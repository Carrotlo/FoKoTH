package me.foesio.foKoTH.koth;

import me.foesio.core.command.CommandPlaceholders;
import me.foesio.core.inventory.InventoryDepositService;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.core.message.FoMessageService;
import me.foesio.foKoTH.api.event.FoKothCapturedEvent;
import me.foesio.foKoTH.api.event.FoKothCaptureEndEvent;
import me.foesio.foKoTH.api.event.FoKothCaptureStartEvent;
import me.foesio.foKoTH.api.event.FoKothGainScoreEvent;
import me.foesio.foKoTH.api.event.FoKothStartEvent;
import me.foesio.foKoTH.api.event.FoKothStopEvent;
import me.foesio.foKoTH.config.ConfigService;
import me.foesio.foKoTH.config.ItemDefinition;
import me.foesio.foKoTH.config.KothConfig;
import me.foesio.foKoTH.hook.TeamService;
import me.foesio.foKoTH.storage.JdbcUserDataStore;
import me.foesio.foKoTH.util.TimeUtil;
import me.foesio.foKoTH.webhook.WebhookService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ActiveKoth {

    private final JavaPlugin plugin;
    private final FoMessageService messages;
    private final ConfigService configService;
    private final JdbcUserDataStore userData;
    private final TeamService teamService;
    private final WebhookService webhookService;
    private final InventoryDepositService inventoryDeposit = InventoryDepositService.create();
    private KothConfig config;
    private final Player starter;
    private final long startedAtMillis = System.currentTimeMillis();
    private UUID capturer;
    private int captureProgress;
    private int elapsed;
    private int lastStatusBroadcast;
    private boolean stopped;
    private final Map<String, Integer> scores = new HashMap<>();
    private final Map<String, UUID> scoreRepresentatives = new HashMap<>();
    private List<Map.Entry<String, Integer>> rankedScores = List.of();
    private boolean rankingsDirty = true;
    private final BossBar bossBar;
    private final List<TextDisplay> holograms = new ArrayList<>();
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
    private final Set<UUID> scoreboardViewers = new HashSet<>();

    public ActiveKoth(JavaPlugin plugin, FoMessageService messages, ConfigService configService, JdbcUserDataStore userData, TeamService teamService, WebhookService webhookService, KothConfig config, Player starter) {
        this.plugin = plugin;
        this.messages = messages;
        this.configService = configService;
        this.userData = userData;
        this.teamService = teamService;
        this.webhookService = webhookService;
        this.config = config;
        this.starter = starter;
        this.bossBar = Bukkit.createBossBar("", config.bossbar().color(), config.bossbar().style());
    }

    public void start() {
        Bukkit.getPluginManager().callEvent(new FoKothStartEvent(config.id()));
        runCommands(config.startCommands(), null);
        broadcast(starter == null ? "start" : "started-with-starter");
        webhook("start");
        spawnHologram();
    }

    public void tick() {
        if (stopped) {
            return;
        }
        elapsed++;
        Player contender = contender();
        if (config.mode() == KothMode.CAPTURE) {
            tickCapture(contender);
        } else {
            tickScore(contender);
        }
        if (stopped) {
            return;
        }
        updateDisplays();
        if (config.maxTimeSeconds() >= 0 && elapsed >= config.maxTimeSeconds()) {
            if (config.mode() == KothMode.CAPTURE && config.endRewardCapturer() && currentCapturer() != null) {
                completeWin(currentCapturer());
            } else if (config.mode() == KothMode.SCORE) {
                Player winner = topPlayer(1);
                if (winner != null) {
                    completeWin(winner);
                } else {
                    timeout();
                }
            } else {
                timeout();
            }
        }
    }

    public void updateConfig(KothConfig config) {
        this.config = config;
    }

    public void stop(boolean naturalEnd, boolean sendBroadcast) {
        if (stopped) {
            return;
        }
        stopped = true;
        if (sendBroadcast) {
            broadcast(naturalEnd ? "out-of-time" : "stop");
        }
        runCommands(config.endCommands(), currentCapturer());
        Bukkit.getPluginManager().callEvent(new FoKothStopEvent(config.id(), naturalEnd));
        webhook("stop");
        cleanupDisplays();
    }

    public boolean stopped() {
        return stopped;
    }

    public KothConfig config() {
        return config;
    }

    public Player currentCapturer() {
        return capturer == null ? null : Bukkit.getPlayer(capturer);
    }

    public void handleDeath(Player player) {
        if (player == null || capturer == null || !capturer.equals(player.getUniqueId())) {
            return;
        }
        endCapture(player);
        if (config.captureEndResetTime()) {
            captureProgress = 0;
        }
        updateDisplays();
    }

    public int score(Player player) {
        return scores.getOrDefault(scoreKey(player), 0);
    }

    public Map<String, String> placeholders(Player viewer) {
        Location center = config.zone().center();
        Player current = currentCapturer();
        String team = current == null ? "" : teamService.teamName(current);
        int maxTimeLeft = maxTimeLeft();
        int timeLeft = timeLeft();
        Map<String, String> map = new LinkedHashMap<>();
        map.put("name", config.id());
        map.put("displayName", config.displayName());
        map.put("displayname", config.displayName());
        map.put("display_name", config.displayName());
        map.put("active", "true");
        map.put("world", center == null ? "" : center.getWorld().getName());
        map.put("x", center == null ? "0" : String.valueOf(center.getBlockX()));
        map.put("y", center == null ? "0" : String.valueOf(center.getBlockY()));
        map.put("z", center == null ? "0" : String.valueOf(center.getBlockZ()));
        map.put("capturer", current == null ? messages.render("placeholders.none", "None") : teamService.display(current));
        map.put("capturer_raw", current == null ? messages.render("placeholders.none", "None") : current.getName());
        map.put("capturer_name", current == null ? "" : current.getName());
        map.put("capturer_team", team);
        map.put("capturer_team_name", team);
        map.put("maxTime", String.valueOf(config.maxTimeSeconds()));
        map.put("maxtime", String.valueOf(config.maxTimeSeconds()));
        map.put("maxTime_formatted", TimeUtil.format(config.maxTimeSeconds(), configService.timerFormat()));
        map.put("maxtime_formatted", TimeUtil.format(config.maxTimeSeconds(), configService.timerFormat()));
        map.put("maxTimeLeft", String.valueOf(maxTimeLeft));
        map.put("maxtimeleft", String.valueOf(maxTimeLeft));
        map.put("maxTimeLeft_formatted", TimeUtil.format(maxTimeLeft, configService.timerFormat()));
        map.put("maxtimeleft_formatted", TimeUtil.format(maxTimeLeft, configService.timerFormat()));
        map.put("timeTaken", String.valueOf(elapsed));
        map.put("timetaken", String.valueOf(elapsed));
        map.put("timeTaken_formatted", TimeUtil.format(elapsed, configService.timerFormat()));
        map.put("timetaken_formatted", TimeUtil.format(elapsed, configService.timerFormat()));
        map.put("time", String.valueOf(config.captureTimeSeconds()));
        map.put("time_formatted", TimeUtil.format(config.captureTimeSeconds(), configService.timerFormat()));
        map.put("timeLeft", String.valueOf(timeLeft));
        map.put("timeleft", String.valueOf(timeLeft));
        map.put("timeLeft_formatted", TimeUtil.format(timeLeft, configService.timerFormat()));
        map.put("timeleft_formatted", TimeUtil.format(timeLeft, configService.timerFormat()));
        map.put("score", viewer == null ? "0" : String.valueOf(score(viewer)));
        map.put("starter", starter == null ? "" : starter.getName());
        List<Map.Entry<String, Integer>> rankings = rankingEntries();
        for (int i = 1; i <= 10; i++) {
            Map.Entry<String, Integer> entry = rankings.size() < i ? null : rankings.get(i - 1);
            Player player = entry == null ? null : playerForScoreKey(entry.getKey());
            map.put("score_" + i + "_player", player == null ? "-" : player.getName());
            map.put("score_" + i + "_points", entry == null ? "0" : String.valueOf(entry.getValue()));
        }
        return map;
    }

    private void tickCapture(Player contender) {
        Player current = currentCapturer();
        if (current != null && canContest(current)) {
            contender = current;
        } else if (current != null) {
            endCapture(current);
            if (config.captureEndResetTime()) {
                captureProgress = 0;
            }
            current = null;
        }
        if (contender == null) {
            return;
        }
        if (current == null || !current.getUniqueId().equals(contender.getUniqueId())) {
            if (current != null) {
                endCapture(current);
                if (config.captureEndResetTime()) {
                    captureProgress = 0;
                }
            }
            startCapture(contender);
        }
        captureProgress++;
        if (elapsed - lastStatusBroadcast >= configService.captureBroadcastEverySeconds()) {
            lastStatusBroadcast = elapsed;
            broadcast("capture-status");
        }
        if (captureProgress >= config.captureTimeSeconds()) {
            completeWin(contender);
        }
    }

    private void tickScore(Player contender) {
        Player current = currentCapturer();
        if (contender == null) {
            if (current != null) {
                endCapture(current);
            }
            return;
        }
        if (current == null || !current.getUniqueId().equals(contender.getUniqueId())) {
            if (current != null) {
                endCapture(current);
            }
            startCapture(contender);
        }
        String key = scoreKey(contender);
        int score = scores.merge(key, 1, Integer::sum);
        scoreRepresentatives.put(key, contender.getUniqueId());
        rankingsDirty = true;
        Bukkit.getPluginManager().callEvent(new FoKothGainScoreEvent(config.id(), contender, score));
        if (config.captureTimeSeconds() > 0 && score >= config.captureTimeSeconds()) {
            completeWin(contender);
        }
    }

    private Player contender() {
        Player best = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canContest(player)) {
                continue;
            }
            if (best == null || player.getName().compareToIgnoreCase(best.getName()) < 0) {
                best = player;
            }
        }
        return best;
    }

    private boolean canContest(Player player) {
        return player != null
            && player.isOnline()
            && player.isValid()
            && !player.isDead()
            && player.getGameMode() != GameMode.SPECTATOR
            && config.zone().contains(player);
    }

    private void startCapture(Player player) {
        capturer = player.getUniqueId();
        Bukkit.getPluginManager().callEvent(new FoKothCaptureStartEvent(config.id(), player));
        runCommands(config.captureStartCommands(), player);
        broadcast("capture-start");
        webhook("capture-start");
    }

    private void endCapture(Player player) {
        Bukkit.getPluginManager().callEvent(new FoKothCaptureEndEvent(config.id(), player));
        runCommands(config.captureEndCommands(), player);
        broadcast("capture-lost");
        webhook("capture-end");
        capturer = null;
    }

    private void completeWin(Player player) {
        String team = teamService.teamName(player);
        Bukkit.getPluginManager().callEvent(new FoKothCapturedEvent(config.id(), player, team));
        userData.recordWin(player, team, config.id(), config.mode().name());
        reward(player);
        broadcast("captured");
        webhook("captured");
        stop(true, false);
    }

    private void timeout() {
        stop(true, true);
    }

    private void reward(Player winner) {
        if (config.mode() == KothMode.SCORE) {
            rewardScoreCommands();
            rewardScoreItems();
        } else {
            rewardCommands(winner, config.rewardCommands());
            rewardCaptureItems(winner);
        }
    }

    private void rewardScoreCommands() {
        for (String command : config.rewardCommands()) {
            if (!command.toUpperCase(Locale.ROOT).startsWith("TOP:")) {
                Player first = topPlayer(1);
                if (first != null) {
                    executeCommand(command, first);
                }
                continue;
            }
            String[] split = command.split(":", 3);
            if (split.length != 3) {
                continue;
            }
            try {
                Player target = topPlayer(Integer.parseInt(split[1]));
                if (target != null) {
                    executeCommand(split[2], target);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void rewardCommands(Player player, List<String> commands) {
        for (String command : commands) {
            executeCommand(command, player);
        }
    }

    private void executeCommand(String command, Player player) {
        if (command == null || command.isBlank()) {
            return;
        }
        String parsed = apply(command, player);
        if (parsed.isBlank()) {
            return;
        }
        if (player != null) {
            parsed = CommandPlaceholders.applyPlayer(parsed, player);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed.replaceFirst("^/", ""));
    }

    private void rewardCaptureItems(Player player) {
        List<Map<?, ?>> items = config.section().getMapList("reward-items");
        for (Map<?, ?> map : items) {
            org.bukkit.configuration.MemoryConfiguration memory = new org.bukkit.configuration.MemoryConfiguration();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                memory.set(String.valueOf(entry.getKey()), entry.getValue());
            }
            ItemStack item = ItemDefinition.from(memory, Material.DIAMOND).build(plugin, messages, placeholders(player));
            inventoryDeposit.deposit(player, item, player.getLocation(), OverflowPolicy.DROP_OVERFLOW);
        }
    }

    private void rewardScoreItems() {
        ConfigurationSection section = config.section().getConfigurationSection("reward-items");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                Player player = topPlayer(Integer.parseInt(key));
                if (player == null) {
                    continue;
                }
                for (Map<?, ?> map : section.getMapList(key)) {
                    org.bukkit.configuration.MemoryConfiguration memory = new org.bukkit.configuration.MemoryConfiguration();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        memory.set(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    ItemStack item = ItemDefinition.from(memory, Material.DIAMOND).build(plugin, messages, placeholders(player));
                    inventoryDeposit.deposit(player, item, player.getLocation(), OverflowPolicy.DROP_OVERFLOW);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void runCommands(List<String> commands, Player player) {
        for (String command : commands) {
            executeCommand(command, player);
        }
    }

    private void broadcast(String key) {
        String override = config.broadcastOverrides().get(key);
        String template = override == null ? messages.config().getString("broadcasts." + key, "") : override;
        if (template.isBlank()) {
            return;
        }
        String message = renderTemplate(apply(template, placeholders(currentCapturer())));
        Location center = config.zone().center();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canView(player, config.broadcastDistance(), center)) {
                player.sendMessage(message);
            }
        }
        plugin.getLogger().info(ChatColor.stripColor(message));
    }

    private void webhook(String key) {
        if (configService.features().webhooks()) {
            webhookService.send(configService.discordWebhook(), config.section().getConfigurationSection("webhook"), key, placeholders(currentCapturer()));
        }
    }

    private void spawnHologram() {
        if (!configService.features().holograms() || !config.hologram().enabled() || !config.zone().valid()) {
            return;
        }
        Location base = config.zone().center().add(config.hologram().offsetX(), config.hologram().offsetY(), config.hologram().offsetZ());
        for (int i = 0; i < config.hologram().lines().size(); i++) {
            Location loc = base.clone().subtract(0, i * config.hologram().lineHeight(), 0);
            try {
                TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class);
                display.setBillboard(Display.Billboard.CENTER);
                display.setPersistent(false);
                display.setSeeThrough(true);
                holograms.add(display);
            } catch (Throwable ignored) {
                break;
            }
        }
    }

    private void updateDisplays() {
        updateBossBar();
        updateActionbar();
        updateHologram();
        updateScoreboards();
    }

    private void updateActionbar() {
        if (!config.actionbar().enabled() || currentCapturer() == null) {
            return;
        }
        String message = renderTemplate(apply(config.actionbar().message(), placeholders(currentCapturer())));
        if (message.isBlank()) {
            return;
        }
        Location center = config.zone().center();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canView(player, config.actionbar().viewDistance(), center)) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
            }
        }
    }

    private void updateBossBar() {
        if (!configService.features().bossbars() || !config.bossbar().enabled()) {
            bossBar.removeAll();
            return;
        }
        if (config.bossbar().onlyShowWhenCapturing() && currentCapturer() == null) {
            bossBar.removeAll();
            return;
        }
        bossBar.setTitle(renderTemplate(apply(config.bossbar().name(), placeholders(currentCapturer()))));
        bossBar.setProgress(progress());
        Location center = config.zone().center();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canView(player, config.bossbar().viewDistance(), center)) {
                bossBar.addPlayer(player);
            } else {
                bossBar.removePlayer(player);
            }
        }
    }

    private void updateHologram() {
        if (holograms.isEmpty()) {
            return;
        }
        List<String> lines = config.hologram().lines();
        Map<String, String> placeholders = placeholders(currentCapturer());
        for (int i = 0; i < holograms.size() && i < lines.size(); i++) {
            holograms.get(i).setText(renderTemplate(apply(lines.get(i), placeholders)));
        }
    }

    private void updateScoreboards() {
        if (!configService.features().scoreboards() || !config.scoreboard().enabled()) {
            clearScoreboards();
            return;
        }
        if (config.scoreboard().onlyShowWhenCapturing() && currentCapturer() == null) {
            clearScoreboards();
            return;
        }
        Set<UUID> visibleNow = new HashSet<>();
        Location center = config.zone().center();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canView(player, config.scoreboard().viewDistance(), center)) {
                continue;
            }
            visibleNow.add(player.getUniqueId());
            previousScoreboards.putIfAbsent(player.getUniqueId(), player.getScoreboard());
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Map<String, String> placeholders = placeholders(player);
            Objective objective = board.registerNewObjective("fokoth", "dummy", renderTemplate(apply(config.scoreboard().title(), placeholders)));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            List<String> lines = config.scoreboard().lines();
            int score = lines.size();
            Set<String> used = new HashSet<>();
            for (String line : lines) {
                String entry = renderTemplate(apply(line, placeholders));
                while (!used.add(entry)) {
                    entry += ChatColor.RESET;
                }
                objective.getScore(entry.length() > 40 ? entry.substring(0, 40) : entry).setScore(score--);
            }
            player.setScoreboard(board);
        }
        for (UUID uuid : new HashSet<>(scoreboardViewers)) {
            if (!visibleNow.contains(uuid)) {
                restoreScoreboard(uuid);
            }
        }
        scoreboardViewers.clear();
        scoreboardViewers.addAll(visibleNow);
    }

    private void clearScoreboards() {
        for (UUID uuid : new HashSet<>(scoreboardViewers)) {
            restoreScoreboard(uuid);
        }
        scoreboardViewers.clear();
    }

    private void restoreScoreboard(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        Scoreboard previous = previousScoreboards.remove(uuid);
        if (player != null && previous != null) {
            player.setScoreboard(previous);
        }
    }

    private void cleanupDisplays() {
        bossBar.removeAll();
        for (TextDisplay display : holograms) {
            display.remove();
        }
        holograms.clear();
        clearScoreboards();
    }

    private boolean canView(Player player, int distance, Location center) {
        if (center == null) {
            return false;
        }
        if (distance == 0) {
            return true;
        }
        if (!player.getWorld().equals(center.getWorld())) {
            return false;
        }
        if (distance < 0) {
            return true;
        }
        double range = distance;
        return player.getLocation().distanceSquared(center) <= range * range;
    }

    private String apply(String template, Player viewer) {
        return apply(template, placeholders(viewer));
    }

    private String renderTemplate(String template) {
        return messages.renderTemplate(template, Map.of());
    }

    private String apply(String template, Map<String, String> map) {
        String out = template == null ? "" : template;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            out = out.replace("%" + entry.getKey() + "%", entry.getValue());
            out = out.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return out;
    }

    private int timeLeft() {
        if (config.mode() == KothMode.CAPTURE) {
            return Math.max(0, config.captureTimeSeconds() - captureProgress);
        }
        if (config.captureTimeSeconds() > 0) {
            return Math.max(0, config.captureTimeSeconds() - bestScore());
        }
        return maxTimeLeft();
    }

    private int maxTimeLeft() {
        return config.maxTimeSeconds() < 0 ? -1 : Math.max(0, config.maxTimeSeconds() - elapsed);
    }

    private double progress() {
        double value;
        if (config.mode() == KothMode.CAPTURE) {
            value = config.captureTimeSeconds() <= 0 ? 0 : (double) captureProgress / config.captureTimeSeconds();
        } else if (config.captureTimeSeconds() > 0) {
            value = (double) bestScore() / config.captureTimeSeconds();
        } else {
            value = config.maxTimeSeconds() <= 0 ? 0 : (double) elapsed / config.maxTimeSeconds();
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private String scoreKey(Player player) {
        String team = config.teamMode() ? teamService.teamName(player) : "";
        if (!team.isBlank()) {
            return "TEAM:" + team.toLowerCase(Locale.ROOT);
        }
        return player.getUniqueId().toString();
    }

    private Player topPlayer(int placement) {
        if (placement <= 0) {
            return null;
        }
        List<Map.Entry<String, Integer>> entries = rankingEntries();
        if (entries.size() < placement) {
            return null;
        }
        return playerForScoreKey(entries.get(placement - 1).getKey());
    }

    private int topScore(int placement) {
        if (placement <= 0) {
            return 0;
        }
        List<Map.Entry<String, Integer>> entries = rankingEntries();
        return entries.size() < placement ? 0 : entries.get(placement - 1).getValue();
    }

    private int bestScore() {
        List<Map.Entry<String, Integer>> entries = rankingEntries();
        return entries.isEmpty() ? 0 : entries.get(0).getValue();
    }

    private Player playerForScoreKey(String key) {
        UUID uuid = scoreRepresentatives.get(key);
        return uuid == null ? null : Bukkit.getPlayer(uuid);
    }

    private List<Map.Entry<String, Integer>> rankingEntries() {
        if (rankingsDirty) {
            rankedScores = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();
            rankingsDirty = false;
        }
        return rankedScores;
    }
}
