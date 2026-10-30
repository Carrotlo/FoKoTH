package me.foesio.foKoTH.koth;

import me.foesio.core.inventory.InventoryDepositService;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.sound.FoSoundService;
import me.foesio.foKoTH.FoKoTH;
import me.foesio.foKoTH.api.HillStatus;
import me.foesio.foKoTH.api.KothActionResult;
import me.foesio.foKoTH.api.ParticipationRule;
import me.foesio.foKoTH.api.event.FoKothFinishReason;
import me.foesio.foKoTH.api.event.FoKothPreStartEvent;
import me.foesio.foKoTH.config.ConfigService;
import me.foesio.foKoTH.config.KothConfig;
import me.foesio.foKoTH.hook.TeamService;
import me.foesio.foKoTH.storage.JdbcUserDataStore;
import me.foesio.foKoTH.util.LocationUtil;
import me.foesio.foKoTH.webhook.WebhookService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class KothManager {

    public enum StartReason {
        COMMAND,
        STARTER,
        SCHEDULE,
        API
    }

    private final FoKoTH plugin;
    private final FoMessageService messages;
    private final ConfigService config;
    private final JdbcUserDataStore userData;
    private final TeamService teamService;
    private final WebhookService webhookService;
    private final FoSoundService sounds;
    private final InventoryDepositService inventoryDeposit = InventoryDepositService.create();
    private final NamespacedKey wandKey;
    private final NamespacedKey starterKey;
    private final Map<String, ActiveKoth> active = new HashMap<>();
    private final Set<String> pendingStarts = new HashSet<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<Plugin, ParticipationRule> participationRules = new LinkedHashMap<>();
    private List<RuleBinding> participationRuleSnapshot = List.of();
    private final Set<Plugin> failedRules = new HashSet<>();
    private record RuleBinding(Plugin owner, ParticipationRule rule) {}
    private boolean ticking;
    private boolean drawingParticles;
    private long tickGeneration;
    private long particleGeneration;

    public KothManager(FoKoTH plugin, FoMessageService messages, ConfigService config, JdbcUserDataStore userData, TeamService teamService, WebhookService webhookService, FoSoundService sounds) {
        this.plugin = plugin;
        this.messages = messages;
        this.config = config;
        this.userData = userData;
        this.teamService = teamService;
        this.webhookService = webhookService;
        this.sounds = sounds;
        this.wandKey = new NamespacedKey(plugin, "wand");
        this.starterKey = new NamespacedKey(plugin, "starter");
    }

    public void start() {
        if (!ticking) {
            ticking = true;
            scheduleTick(20L, ++tickGeneration);
        }
        if (!drawingParticles) {
            drawingParticles = true;
            scheduleParticles(20L, ++particleGeneration);
        }
    }

    public void reload() {
        for (String id : active.keySet().toArray(String[]::new)) {
            ActiveKoth event = active.get(id);
            if (event == null) {
                continue;
            }
            KothConfig updated = config.koth(id);
            if (updated == null || !updated.enabled() || !updated.zone().valid()) {
                stopDetailed(id, FoKothFinishReason.RELOAD, true);
            } else {
                event.updateConfig(updated);
            }
        }
    }

    public void shutdownTasks() {
        ticking = false;
        drawingParticles = false;
        tickGeneration++;
        particleGeneration++;
    }

    public boolean start(String id, Player starter, StartReason reason) {
        return startDetailed(id, starter, reason) == KothActionResult.STARTED;
    }

    public KothActionResult startDetailed(String id, Player starter, StartReason reason) {
        if (id == null || id.isBlank()) {
            return KothActionResult.NOT_FOUND;
        }
        StartReason effectiveReason = reason == null ? StartReason.COMMAND : reason;
        String key = id.toLowerCase(Locale.ROOT);
        KothConfig koth = config.koth(key);
        if (koth == null) {
            if (starter != null) {
                messages.send(starter, "general.invalid-koth", Map.of("koth", id));
            }
            return KothActionResult.NOT_FOUND;
        }
        if (!koth.enabled() || !koth.zone().valid()) {
            if (starter != null) {
                messages.send(starter, "general.invalid-koth", Map.of("koth", id));
            }
            return !koth.enabled() ? KothActionResult.DISABLED : KothActionResult.INVALID_ZONE;
        }
        ActiveKoth existing = active.get(key);
        if (existing != null && existing.stopped()) {
            active.remove(key, existing);
            existing = null;
        }
        if (existing != null || pendingStarts.contains(key)) {
            if (starter != null) {
                messages.send(starter, "koth.already-running", Map.of("koth", id));
            }
            return KothActionResult.ALREADY_ACTIVE;
        }
        if ((effectiveReason == StartReason.STARTER || effectiveReason == StartReason.SCHEDULE) && koth.minimumPlayers() > Bukkit.getOnlinePlayers().size()) {
            if (starter != null) {
                messages.send(starter, "koth.minimum-players", Map.of("minimum", String.valueOf(koth.minimumPlayers())));
            }
            return KothActionResult.INSUFFICIENT_PLAYERS;
        }
        FoKothPreStartEvent preStart = new FoKothPreStartEvent(koth.id(), FoKothPreStartEvent.Cause.valueOf(effectiveReason.name()), starter);
        pendingStarts.add(key);
        try {
            Bukkit.getPluginManager().callEvent(preStart);
        } finally {
            pendingStarts.remove(key);
        }
        if (preStart.isCancelled()) {
            return KothActionResult.CANCELLED;
        }
        existing = active.get(key);
        if (existing != null && existing.stopped()) {
            active.remove(key, existing);
            existing = null;
        }
        if (existing != null) {
            return KothActionResult.ALREADY_ACTIVE;
        }
        koth = config.koth(key);
        if (koth == null) {
            return KothActionResult.NOT_FOUND;
        }
        if (!koth.enabled()) {
            return KothActionResult.DISABLED;
        }
        if (!koth.zone().valid()) {
            return KothActionResult.INVALID_ZONE;
        }
        if ((effectiveReason == StartReason.STARTER || effectiveReason == StartReason.SCHEDULE) && koth.minimumPlayers() > Bukkit.getOnlinePlayers().size()) {
            return KothActionResult.INSUFFICIENT_PLAYERS;
        }
        ActiveKoth event = new ActiveKoth(plugin, messages, config, userData, teamService, webhookService, sounds, koth, starter, this::mayContest);
        active.put(key, event);
        event.start();
        return KothActionResult.STARTED;
    }

    public boolean stop(String id, boolean sendBroadcast) {
        return stopDetailed(id, FoKothFinishReason.MANUAL, sendBroadcast) == KothActionResult.STOPPED;
    }

    public KothActionResult stopDetailed(String id, FoKothFinishReason reason, boolean sendBroadcast) {
        java.util.Objects.requireNonNull(reason, "reason");
        if (id == null || id.isBlank()) {
            return KothActionResult.NOT_FOUND;
        }
        String key = id.toLowerCase(Locale.ROOT);
        ActiveKoth event = active.get(key);
        if (event == null) {
            return config.koth(id) == null ? KothActionResult.NOT_FOUND : KothActionResult.NOT_ACTIVE;
        }
        if (event.stopped()) {
            return KothActionResult.NOT_ACTIVE;
        }
        active.remove(key);
        event.stop(reason, sendBroadcast);
        return KothActionResult.STOPPED;
    }

    public void stopAll(boolean sendBroadcast) {
        stopAll(FoKothFinishReason.MANUAL, sendBroadcast);
    }

    public void stopAll(FoKothFinishReason reason, boolean sendBroadcast) {
        for (String id : active.keySet().toArray(String[]::new)) {
            stopDetailed(id, reason, sendBroadcast);
        }
    }

    public void handleDeath(Player player) {
        if (player == null) {
            return;
        }
        for (ActiveKoth event : List.copyOf(active.values())) {
            event.handleDeath(player);
        }
    }

    public void create(String id, KothMode mode, KothZone zone) {
        config.createKoth(id, mode, zone);
    }

    public boolean delete(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String key = id.toLowerCase(Locale.ROOT);
        if (active.containsKey(key)) {
            stopDetailed(key, FoKothFinishReason.DELETED, true);
        }
        if (config.koth(key) == null) {
            return false;
        }
        config.deleteKoth(key);
        return true;
    }

    public KothConfig koth(String id) {
        return config.koth(id);
    }

    public ActiveKoth active(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return active.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean isActive(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        ActiveKoth event = active.get(id.toLowerCase(Locale.ROOT));
        return event != null && !event.stopped();
    }

    public Collection<KothConfig> allKoths() {
        return config.koths();
    }

    public HillStatus status(String id) {
        KothConfig definition = config.koth(id);
        if (definition == null) {
            return null;
        }
        ActiveKoth running = active(definition.id());
        if (running != null && !running.stopped()) {
            return running.status();
        }
        Location center = definition.zone().center();
        return new HillStatus(definition.id(), definition.displayName(), definition.mode().name(), definition.enabled(), false,
            center == null ? "" : center.getWorld().getName(), center == null ? 0 : center.getBlockX(),
            center == null ? 0 : center.getBlockY(), center == null ? 0 : center.getBlockZ(),
            null, "", 0, definition.captureTimeSeconds(), -1, Map.of());
    }

    public Collection<HillStatus> statuses() {
        List<HillStatus> snapshots = new ArrayList<>();
        for (KothConfig definition : List.copyOf(config.koths())) {
            HillStatus current = status(definition.id());
            if (current != null) {
                snapshots.add(current);
            }
        }
        return List.copyOf(snapshots);
    }

    public void registerParticipationRule(Plugin owner, ParticipationRule rule) {
        java.util.Objects.requireNonNull(owner, "owner");
        java.util.Objects.requireNonNull(rule, "rule");
        if (!owner.isEnabled()) {
            throw new IllegalArgumentException("Rule owner must be enabled");
        }
        participationRules.put(owner, rule);
        refreshParticipationRules();
    }

    public void unregisterParticipationRule(Plugin owner) {
        participationRules.remove(owner);
        failedRules.remove(owner);
        refreshParticipationRules();
    }

    public boolean mayContest(String kothId, Player player) {
        for (RuleBinding binding : participationRuleSnapshot) {
            if (!binding.owner().isEnabled()) {
                continue;
            }
            try {
                if (!binding.rule().mayContest(kothId, player)) {
                    return false;
                }
            } catch (Throwable error) {
                if (failedRules.add(binding.owner())) {
                    plugin.getLogger().warning("Participation rule from " + binding.owner().getName() + " failed: " + error);
                }
            }
        }
        return true;
    }

    private void refreshParticipationRules() {
        participationRuleSnapshot = participationRules.entrySet().stream()
            .map(entry -> new RuleBinding(entry.getKey(), entry.getValue())).toList();
    }

    public Selection selection(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(), uuid -> new Selection());
    }

    public ItemStack wand() {
        ItemStack item = new ItemStack(Material.DIAMOND_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(messages.renderTemplate("&#03fc88FoKoTH Wand", Map.of()));
        meta.setLore(java.util.List.of(messages.renderTemplate("&#ffffffLeft/right click corners.", Map.of())));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.STRING, "true");
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.STRING);
    }

    public ItemStack starter(KothConfig koth) {
        return starter(koth, null);
    }

    public ItemStack starter(KothConfig koth, Player viewer) {
        Map<String, String> replacements = new HashMap<>();
        Location center = koth.zone().center();
        replacements.put("name", koth.id());
        replacements.put("displayName", koth.displayName());
        replacements.put("display_name", koth.displayName());
        replacements.put("x", center == null ? "0" : String.valueOf(center.getBlockX()));
        replacements.put("y", center == null ? "0" : String.valueOf(center.getBlockY()));
        replacements.put("z", center == null ? "0" : String.valueOf(center.getBlockZ()));
        replacements.put("time_formatted", me.foesio.foKoTH.util.TimeUtil.format(koth.captureTimeSeconds(), config.timerFormat()));
        replacements.put("maxTime_formatted", me.foesio.foKoTH.util.TimeUtil.format(koth.maxTimeSeconds(), config.timerFormat()));
        return koth.starterItem().buildStarter(plugin, messages, viewer, koth.id(), replacements);
    }

    public boolean starterItemsEnabled() {
        return config.features().starterItems();
    }

    public String starterId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(starterKey, PersistentDataType.STRING);
    }

    public void giveWand(Player player) {
        inventoryDeposit.deposit(player, wand(), player.getLocation(), OverflowPolicy.DROP_OVERFLOW);
        messages.send(player, "koth.wand-given");
        sounds.play(player, "koth.wand-given");
    }

    public void setSelection(Player player, boolean first, Location location) {
        Selection selection = selection(player);
        if (first) {
            selection.first(location);
            messages.send(player, "koth.wand-pos1", Map.of("location", LocationUtil.shortString(location)));
            sounds.play(player, "koth.selection-first");
        } else {
            selection.second(location);
            messages.send(player, "koth.wand-pos2", Map.of("location", LocationUtil.shortString(location)));
            sounds.play(player, "koth.selection-second");
        }
    }

    private void tick() {
        for (String id : active.keySet().toArray(String[]::new)) {
            ActiveKoth event = active.get(id);
            if (event == null) {
                continue;
            }
            event.tick();
            if (event.stopped()) {
                active.remove(id, event);
            }
        }
    }

    private void scheduleTick(long delayTicks, long generation) {
        plugin.core().scheduler().runGlobalLater(() -> {
            if (!ticking || generation != tickGeneration) {
                return;
            }
            try {
                tick();
            } finally {
                if (ticking && generation == tickGeneration) {
                    scheduleTick(20L, generation);
                }
            }
        }, delayTicks);
    }

    private void particles() {
        for (var iterator = selections.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, Selection> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            Selection selection = entry.getValue();
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (!selection.complete()) {
                continue;
            }
            draw(player, selection.zone());
        }
    }

    private void scheduleParticles(long delayTicks, long generation) {
        plugin.core().scheduler().runGlobalLater(() -> {
            if (!drawingParticles || generation != particleGeneration) {
                return;
            }
            try {
                particles();
            } finally {
                if (drawingParticles && generation == particleGeneration) {
                    scheduleParticles(20L, generation);
                }
            }
        }, delayTicks);
    }

    private void draw(Player player, KothZone zone) {
        if (!zone.valid() || !player.getWorld().equals(zone.world())) {
            return;
        }
        Location a = zone.first();
        Location b = zone.second();
        int minX = Math.min(a.getBlockX(), b.getBlockX());
        int maxX = Math.max(a.getBlockX(), b.getBlockX()) + 1;
        int minY = Math.min(a.getBlockY(), b.getBlockY());
        int maxY = Math.max(a.getBlockY(), b.getBlockY()) + 1;
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        int maxZ = Math.max(a.getBlockZ(), b.getBlockZ()) + 1;
        for (int x = minX; x <= maxX; x++) {
            particle(player, x, minY, minZ);
            particle(player, x, minY, maxZ);
            particle(player, x, maxY, minZ);
            particle(player, x, maxY, maxZ);
        }
        for (int y = minY; y <= maxY; y++) {
            particle(player, minX, y, minZ);
            particle(player, minX, y, maxZ);
            particle(player, maxX, y, minZ);
            particle(player, maxX, y, maxZ);
        }
        for (int z = minZ; z <= maxZ; z++) {
            particle(player, minX, minY, z);
            particle(player, minX, maxY, z);
            particle(player, maxX, minY, z);
            particle(player, maxX, maxY, z);
        }
    }

    private void particle(Player player, double x, double y, double z) {
        player.spawnParticle(Particle.END_ROD, new Location(player.getWorld(), x, y, z), 1, 0, 0, 0, 0);
    }
}
