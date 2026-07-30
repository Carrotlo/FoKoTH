package me.foesio.foKoTH.config;

import me.foesio.core.config.ResourceFiles;
import me.foesio.foKoTH.koth.KothMode;
import me.foesio.foKoTH.koth.KothZone;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.text.FoText;
import me.foesio.foKoTH.schedule.KothSchedule;
import me.foesio.foKoTH.util.LocationUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConfigService {

    private record MigrationResult(boolean hadLegacy, boolean failed) {}

    private final JavaPlugin plugin;
    private final FoMessageService messages;
    private FeatureSettings features;
    private DiscordWebhookSettings discordWebhook;
    private StorageSettings storage;
    private String teamHook;
    private int timerFormat;
    private int captureBroadcastEverySeconds;
    private Map<String, KothConfig> koths = Map.of();
    private Map<String, KothSchedule> schedules = Map.of();

    public ConfigService(JavaPlugin plugin, FoMessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        this.features = new FeatureSettings(
            config.getBoolean("features.schedules", true),
            config.getBoolean("features.starter-items", true),
            config.getBoolean("features.scoreboards", true),
            config.getBoolean("features.bossbars", true),
            config.getBoolean("features.holograms", true),
            config.getBoolean("features.webhooks", true),
            config.getBoolean("features.placeholderapi", true),
            config.getBoolean("features.team-hooks", true)
        );
        this.discordWebhook = new DiscordWebhookSettings(
            config.isSet("discord-webhook")
                || config.isSet("discord-webhook.enabled")
                || config.isSet("discord-webhook.url")
                || config.isSet("discord-webhook.notify"),
            config.getBoolean("discord-webhook.enabled", false),
            config.getString("discord-webhook.url", ""),
            Map.of(
                "start", config.getBoolean("discord-webhook.notify.start", true),
                "stop", config.getBoolean("discord-webhook.notify.stop", true),
                "capture-start", config.getBoolean("discord-webhook.notify.capture-start", true),
                "capture-end", config.getBoolean("discord-webhook.notify.capture-end", true),
                "captured", config.getBoolean("discord-webhook.notify.captured", true)
            )
        );
        this.storage = new StorageSettings(
            config.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT),
            config.getString("storage.host", "localhost"),
            config.getInt("storage.port", 3306),
            config.getString("storage.database", "fokoth"),
            config.getString("storage.username", "root"),
            config.getString("storage.password", ""),
            config.getBoolean("storage.use-ssl", false)
        );
        this.teamHook = config.getString("hooks.team-plugin", "none");
        this.timerFormat = config.getInt("timer-format", 1);
        this.captureBroadcastEverySeconds = Math.max(1, config.getInt("capture-broadcast-every-seconds", 5));
        File kothFolder = kothFolder();
        boolean newKothFolder = !kothFolder.exists();
        if (newKothFolder && !kothFolder.mkdirs()) {
            warnConfig("Could not create koths folder.");
        }
        MigrationResult kothMigration = migrateLegacyKoths(config);
        if (newKothFolder && !kothMigration.hadLegacy()) {
            saveDefaultKothFile();
        }
        this.koths = loadKoths(kothFolder);
        if (kothMigration.failed()) {
            this.koths = mergeLegacyKoths(config.getConfigurationSection("koths"), this.koths);
        }
        MigrationResult scheduleMigration = migrateLegacySchedules(config);
        if (!scheduleFile().exists() && !scheduleMigration.hadLegacy()) {
            saveDefaultScheduleFile();
        }
        this.schedules = loadSchedules(loadScheduleFile());
        if (scheduleMigration.failed()) {
            this.schedules = mergeLegacySchedules(config.getConfigurationSection("schedules"), this.schedules);
        }
    }

    private Map<String, KothConfig> loadKoths(File folder) {
        Map<String, KothConfig> loaded = new LinkedHashMap<>();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return loaded;
        }
        for (File file : Arrays.stream(files).sorted(Comparator.comparing(File::getName)).toList()) {
            String id = file.getName().substring(0, file.getName().length() - ".yml".length()).toLowerCase(Locale.ROOT);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            loaded.put(id, new KothConfig(id, config));
        }
        return loaded;
    }

    private MigrationResult migrateLegacyKoths(FileConfiguration config) {
        ConfigurationSection legacy = config.getConfigurationSection("koths");
        if (legacy == null) {
            return new MigrationResult(false, false);
        }
        boolean failed = false;
        for (String id : legacy.getKeys(false)) {
            ConfigurationSection section = legacy.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            File file = kothFile(id);
            if (file.exists()) {
                warnConfig("Could not migrate KOTH " + id + " because koths/" + file.getName() + " already exists.");
                failed = true;
                continue;
            }
            YamlConfiguration target = new YamlConfiguration();
            copySection(section, target);
            if (!saveKothFile(id, target)) {
                failed = true;
            }
        }
        if (!failed) {
            config.set("koths", null);
            plugin.saveConfig();
        }
        return new MigrationResult(true, failed);
    }

    private MigrationResult migrateLegacySchedules(FileConfiguration config) {
        ConfigurationSection legacy = config.getConfigurationSection("schedules");
        if (legacy == null) {
            return new MigrationResult(false, false);
        }
        YamlConfiguration target = loadScheduleFile();
        boolean failed = false;
        for (String id : legacy.getKeys(false)) {
            ConfigurationSection section = legacy.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            if (target.isConfigurationSection(id)) {
                warnConfig("Could not migrate schedule " + id + " because schedules.yml already contains it.");
                failed = true;
                continue;
            }
            copySection(section, target, id + ".");
        }
        if (!failed && saveScheduleFile(target)) {
            config.set("schedules", null);
            plugin.saveConfig();
        }
        return new MigrationResult(true, failed);
    }

    private Map<String, KothConfig> mergeLegacyKoths(ConfigurationSection legacy, Map<String, KothConfig> loaded) {
        Map<String, KothConfig> merged = new LinkedHashMap<>(loaded);
        if (legacy == null) {
            return merged;
        }
        for (String id : legacy.getKeys(false)) {
            ConfigurationSection section = legacy.getConfigurationSection(id);
            if (section != null) {
                String key = id.toLowerCase(Locale.ROOT);
                merged.putIfAbsent(key, new KothConfig(key, section));
            }
        }
        return merged;
    }

    private Map<String, KothSchedule> mergeLegacySchedules(ConfigurationSection legacy, Map<String, KothSchedule> loaded) {
        Map<String, KothSchedule> merged = new LinkedHashMap<>(loaded);
        loadSchedules(legacy).forEach(merged::putIfAbsent);
        return merged;
    }

    private void copySection(ConfigurationSection source, YamlConfiguration target) {
        copySection(source, target, "");
    }

    private void copySection(ConfigurationSection source, YamlConfiguration target, String prefix) {
        for (Map.Entry<String, Object> entry : source.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof ConfigurationSection)) {
                target.set(prefix + entry.getKey(), entry.getValue());
            }
        }
    }

    private void saveDefaultKothFile() {
        saveDefaultResourceIfPresent("koths/spawn.yml");
    }

    private void saveDefaultScheduleFile() {
        saveDefaultResourceIfPresent("schedules.yml");
    }

    private void saveDefaultResourceIfPresent(String relativePath) {
        if (plugin.getResource(relativePath) == null) {
            return;
        }
        ResourceFiles.saveDefault(plugin, relativePath);
    }

    private File kothFolder() {
        return new File(plugin.getDataFolder(), "koths");
    }

    private File kothFile(String id) {
        return new File(kothFolder(), id.toLowerCase(Locale.ROOT) + ".yml");
    }

    private File scheduleFile() {
        return new File(plugin.getDataFolder(), "schedules.yml");
    }

    private YamlConfiguration loadKothFile(String id) {
        File file = kothFile(id);
        return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private YamlConfiguration loadScheduleFile() {
        File file = scheduleFile();
        return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private boolean saveKothFile(String id, YamlConfiguration config) {
        File folder = kothFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            warnConfig("Could not create koths folder.");
            return false;
        }
        try {
            config.save(kothFile(id));
            return true;
        } catch (IOException ex) {
            warnConfig("Could not save KOTH " + id + ": " + ex.getMessage());
            return false;
        }
    }

    private boolean saveScheduleFile(YamlConfiguration config) {
        try {
            config.save(scheduleFile());
            return true;
        } catch (IOException ex) {
            warnConfig("Could not save schedules.yml: " + ex.getMessage());
            return false;
        }
    }

    private boolean setKothValue(String path, Object value) {
        String remaining = path.substring("koths.".length());
        int split = remaining.indexOf('.');
        if (split <= 0 || split >= remaining.length() - 1) {
            return false;
        }
        String id = remaining.substring(0, split).toLowerCase(Locale.ROOT);
        String key = remaining.substring(split + 1);
        YamlConfiguration config = loadKothFile(id);
        config.set(key, value);
        if (!saveKothFile(id, config)) {
            return false;
        }
        reload();
        return true;
    }

    public boolean setKothValues(String id, Map<String, Object> values) {
        if (id == null || id.isBlank() || values.isEmpty()) {
            return false;
        }
        String key = id.toLowerCase(Locale.ROOT);
        YamlConfiguration config = loadKothFile(key);
        values.forEach(config::set);
        if (!saveKothFile(key, config)) {
            return false;
        }
        reload();
        return true;
    }

    private boolean setScheduleValue(String path, Object value) {
        String remaining = path.substring("schedules.".length());
        int split = remaining.indexOf('.');
        if (split <= 0 || split >= remaining.length() - 1) {
            return false;
        }
        String id = remaining.substring(0, split).toLowerCase(Locale.ROOT);
        String key = remaining.substring(split + 1);
        YamlConfiguration config = loadScheduleFile();
        config.set(id + "." + key, value);
        if (!saveScheduleFile(config)) {
            return false;
        }
        reload();
        return true;
    }

    public boolean setScheduleValues(String id, Map<String, Object> values) {
        if (id == null || id.isBlank() || values.isEmpty()) {
            return false;
        }
        String key = id.toLowerCase(Locale.ROOT);
        YamlConfiguration config = loadScheduleFile();
        values.forEach((path, value) -> config.set(key + "." + path, value));
        if (!saveScheduleFile(config)) {
            return false;
        }
        reload();
        return true;
    }

    private Map<String, KothSchedule> loadSchedules(ConfigurationSection root) {
        Map<String, KothSchedule> loaded = new LinkedHashMap<>();
        if (root == null) {
            return loaded;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section != null) {
                loaded.put(id.toLowerCase(Locale.ROOT), new KothSchedule(
                    id.toLowerCase(Locale.ROOT),
                    section.getBoolean("enabled", true),
                    section.getBoolean("random", false),
                    section.getString("cron", "0 18 * * *"),
                    section.getStringList("koths")
                ));
            }
        }
        return loaded;
    }

    public void createKoth(String id, KothMode mode, KothZone zone) {
        String key = id.toLowerCase(Locale.ROOT);
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", true);
        config.set("display-name", "&#03fc88" + id);
        config.set("mode", mode.name());
        config.set("settings.capture-time-seconds", mode == KothMode.CAPTURE ? 180 : -1);
        config.set("settings.capture-end-reset-time", true);
        config.set("settings.max-time-seconds", mode == KothMode.CAPTURE ? 7200 : 1200);
        config.set("settings.end-reward-capturer", false);
        config.set("settings.team-mode", false);
        config.set("zone.location1", LocationUtil.serialize(zone.first()));
        config.set("zone.location2", LocationUtil.serialize(zone.second()));
        config.set("reward-commands", mode == KothMode.SCORE
            ? List.of("TOP:1:give %player% diamond 3", "TOP:2:give %player% diamond 2", "TOP:3:give %player% diamond 1")
            : List.of("give %player% diamond 1"));
        config.set("reward-items", List.of());
        config.set("broadcast-distance", 0);
        config.set("starter.enabled", true);
        config.set("starter.material", "BLAZE_POWDER");
        config.set("starter.glow", true);
        config.set("starter.name", "&#03fc88%displayName% Starter");
        config.set("starter.lore", List.of(" ", "&#ffffffLocation: &#03fc88%x%, %y%, %z%", "&#ffffffClick to start"));
        config.set("minimum-players", 0);
        config.set("hologram.enabled", true);
        config.set("hologram.location-offset.x", 0.0);
        config.set("hologram.location-offset.y", 3.0);
        config.set("hologram.location-offset.z", 0.0);
        config.set("hologram.line-height", 0.3);
        config.set("hologram.lines", List.of(
            "&#03fc88%displayName%",
            "&#3ecf8eCapturer: &#ffffff%capturer%",
            "&#3ecf8eTime Left: &#ffffff%timeLeft_formatted%"
        ));
        config.set("bossbar.enabled", true);
        config.set("bossbar.only-show-when-capturing", false);
        config.set("bossbar.view-distance", 64);
        config.set("bossbar.name", "&#03fc88%displayName% &#a7b8b0| &#ffffff%capturer% (%timeLeft_formatted%)");
        config.set("bossbar.color", "GREEN");
        config.set("bossbar.style", "SEGMENTED_20");
        config.set("scoreboard.enabled", true);
        config.set("scoreboard.only-show-when-capturing", false);
        config.set("scoreboard.view-distance", 64);
        config.set("scoreboard.title", "&#03fc88ꜰᴏᴋᴏᴛʜ");
        config.set("scoreboard.lines", defaultScoreboardLines(mode));
        config.set("actionbar.enabled", false);
        config.set("actionbar.view-distance", 64);
        config.set("actionbar.message", "&#03fc88%displayName% &#ffffff| &#3ecf8e%capturer% &#ffffffcapturing &#03fc88%timeLeft_formatted%");
        config.set("start-commands", List.of());
        config.set("end-commands", List.of());
        config.set("capture-start-commands", List.of());
        config.set("capture-end-commands", List.of());
        setDefaultWebhookPayloads(config);
        if (saveKothFile(key, config)) {
            reload();
        }
    }

    private void setDefaultWebhookPayloads(YamlConfiguration config) {
        setWebhookPayload(config, "start", true, "Get ready!", "#03fc88", "%name% - STARTED");
        setWebhookPayload(config, "stop", true, "", "#ff5d73", "%name% - STOPPED");
        setWebhookPayload(config, "capture-start", true, "", "#3ecf8e", "%name% - CAPTURE STARTED");
        setWebhookPayload(config, "capture-end", true, "", "#ff5d73", "%name% - CAPTURE STOPPED");
        setWebhookPayload(config, "captured", true, ":tada:", "#3ecf8e", "%name% - CAPTURED");
    }

    private void setWebhookPayload(YamlConfiguration config, String event, boolean enabled, String content, String color, String title) {
        config.set("webhook." + event + ".enabled", enabled);
        if (!content.isBlank()) {
            config.set("webhook." + event + ".content", content);
        }
        config.set("webhook." + event + ".color", color);
        config.set("webhook." + event + ".title.text", title);
    }

    private List<String> defaultScoreboardLines(KothMode mode) {
        if (mode == KothMode.SCORE) {
            return List.of(
                " ",
                "&#03fc88Top Scores",
                "&#ffffff1. &#3ecf8e%score_1_player% &#ffffff- &#03fc88%score_1_points%",
                "&#ffffff2. &#3ecf8e%score_2_player% &#ffffff- &#03fc88%score_2_points%",
                "&#ffffff3. &#3ecf8e%score_3_player% &#ffffff- &#03fc88%score_3_points%",
                " ",
                "&#03fc88Your Score",
                "&#ffffff%score%",
                " ",
                "&#3ecf8eEnds In",
                "&#ffffff%maxTimeLeft_formatted%"
            );
        }
        return List.of(
            " ",
            "&#03fc88Capturer",
            "&#ffffff%capturer%",
            " ",
            "&#3ecf8eTime Left",
            "&#ffffff%timeLeft_formatted%",
            " ",
            "&#03fc88Stay in the zone"
        );
    }

    public void deleteKoth(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        File file = kothFile(key);
        if (file.exists() && !file.delete()) {
            warnConfig("Could not delete KOTH " + key + ".");
        }
        if (plugin.getConfig().contains("koths." + key)) {
            plugin.getConfig().set("koths." + key, null);
            if (plugin.getConfig().isConfigurationSection("koths") && plugin.getConfig().getConfigurationSection("koths").getKeys(false).isEmpty()) {
                plugin.getConfig().set("koths", null);
            }
            plugin.saveConfig();
        }
        reload();
    }

    public void set(String path, Object value) {
        if (path.startsWith("koths.")) {
            setKothValue(path, value);
            return;
        }
        if (path.startsWith("schedules.")) {
            setScheduleValue(path, value);
            return;
        }
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
        reload();
    }

    public FeatureSettings features() { return features; }
    public DiscordWebhookSettings discordWebhook() { return discordWebhook; }
    public StorageSettings storage() { return storage; }
    public String teamHook() { return teamHook; }
    public int timerFormat() { return timerFormat; }
    public int captureBroadcastEverySeconds() { return captureBroadcastEverySeconds; }
    public Collection<KothConfig> koths() { return koths.values(); }
    public KothConfig koth(String id) { return id == null ? null : koths.get(id.toLowerCase(Locale.ROOT)); }
    public Map<String, KothSchedule> schedules() { return schedules; }
    public FoMessageService messages() { return messages; }

    private void warnConfig(String message) {
        plugin.getLogger().warning(FoText.plain(messages.render("console.config-warning", "{prefix}{bad}{message}", Map.of("message", message))));
    }
}
