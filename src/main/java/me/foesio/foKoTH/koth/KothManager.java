package me.foesio.foKoTH.koth;

import me.foesio.core.inventory.InventoryDepositService;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.core.message.FoMessageService;
import me.foesio.foKoTH.FoKoTH;
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class KothManager {

    public enum StartReason {
        COMMAND,
        STARTER,
        SCHEDULE
    }

    private final FoKoTH plugin;
    private final FoMessageService messages;
    private final ConfigService config;
    private final JdbcUserDataStore userData;
    private final TeamService teamService;
    private final WebhookService webhookService;
    private final InventoryDepositService inventoryDeposit = InventoryDepositService.create();
    private final NamespacedKey wandKey;
    private final NamespacedKey starterKey;
    private final Map<String, ActiveKoth> active = new HashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private boolean ticking;
    private boolean drawingParticles;
    private long tickGeneration;
    private long particleGeneration;

    public KothManager(FoKoTH plugin, FoMessageService messages, ConfigService config, JdbcUserDataStore userData, TeamService teamService, WebhookService webhookService) {
        this.plugin = plugin;
        this.messages = messages;
        this.config = config;
        this.userData = userData;
        this.teamService = teamService;
        this.webhookService = webhookService;
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
                stop(id, true);
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
        if (id == null || id.isBlank()) {
            return false;
        }
        String key = id.toLowerCase(Locale.ROOT);
        KothConfig koth = config.koth(key);
        if (koth == null || !koth.enabled() || !koth.zone().valid()) {
            if (starter != null) {
                messages.send(starter, "general.invalid-koth", Map.of("koth", id));
            }
            return false;
        }
        if (active.containsKey(key)) {
            if (starter != null) {
                messages.send(starter, "koth.already-running", Map.of("koth", id));
            }
            return false;
        }
        if ((reason == StartReason.STARTER || reason == StartReason.SCHEDULE) && koth.minimumPlayers() > Bukkit.getOnlinePlayers().size()) {
            if (starter != null) {
                messages.send(starter, "koth.minimum-players", Map.of("minimum", String.valueOf(koth.minimumPlayers())));
            }
            return false;
        }
        ActiveKoth event = new ActiveKoth(plugin, messages, config, userData, teamService, webhookService, koth, starter);
        active.put(key, event);
        event.start();
        return true;
    }

    public boolean stop(String id, boolean sendBroadcast) {
        if (id == null || id.isBlank()) {
            return false;
        }
        ActiveKoth event = active.remove(id.toLowerCase(Locale.ROOT));
        if (event == null) {
            return false;
        }
        event.stop(false, sendBroadcast);
        return true;
    }

    public void stopAll(boolean sendBroadcast) {
        for (String id : active.keySet().toArray(String[]::new)) {
            stop(id, sendBroadcast);
        }
    }

    public void handleDeath(Player player) {
        if (player == null) {
            return;
        }
        for (ActiveKoth event : active.values()) {
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
            stop(key, true);
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
        return active.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public Collection<KothConfig> allKoths() {
        return config.koths();
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
        return koth.starterItem().buildStarter(plugin, messages, koth.id(), replacements);
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
    }

    public void setSelection(Player player, boolean first, Location location) {
        Selection selection = selection(player);
        if (first) {
            selection.first(location);
            messages.send(player, "koth.wand-pos1", Map.of("location", LocationUtil.shortString(location)));
        } else {
            selection.second(location);
            messages.send(player, "koth.wand-pos2", Map.of("location", LocationUtil.shortString(location)));
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
                active.remove(id);
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
