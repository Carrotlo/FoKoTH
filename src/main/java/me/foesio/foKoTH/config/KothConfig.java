package me.foesio.foKoTH.config;

import me.foesio.foKoTH.koth.KothMode;
import me.foesio.foKoTH.koth.KothZone;
import me.foesio.foKoTH.util.LocationUtil;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KothConfig {

    private static final String DEFAULT_ACTIONBAR_MESSAGE = "&#03fc88%displayName% &#ffffff| &#3ecf8e%capturer% &#ffffffcapturing &#03fc88%timeLeft_formatted%";

    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final KothMode mode;
    private final int captureTimeSeconds;
    private final boolean captureEndResetTime;
    private final int maxTimeSeconds;
    private final boolean endRewardCapturer;
    private final boolean teamMode;
    private final KothZone zone;
    private final List<String> rewardCommands;
    private final int broadcastDistance;
    private final ItemDefinition starterItem;
    private final boolean starterEnabled;
    private final int minimumPlayers;
    private final DisplaySettings hologram;
    private final BossbarSettings bossbar;
    private final ScoreboardSettings scoreboard;
    private final ActionbarSettings actionbar;
    private final List<String> startCommands;
    private final List<String> endCommands;
    private final List<String> captureStartCommands;
    private final List<String> captureEndCommands;
    private final Map<String, String> broadcastOverrides;
    private final ConfigurationSection section;

    public KothConfig(String id, ConfigurationSection section) {
        this.id = id;
        this.section = section;
        this.enabled = section.getBoolean("enabled", true);
        this.displayName = section.getString("display-name", id);
        this.mode = parseMode(section.getString("mode", "CAPTURE"));
        this.captureTimeSeconds = section.getInt("settings.capture-time-seconds", 180);
        this.captureEndResetTime = section.getBoolean("settings.capture-end-reset-time", true);
        this.maxTimeSeconds = section.getInt("settings.max-time-seconds", 7200);
        this.endRewardCapturer = section.getBoolean("settings.end-reward-capturer", false);
        this.teamMode = section.getBoolean("settings.team-mode", false);
        this.zone = new KothZone(LocationUtil.parse(section.getString("zone.location1")), LocationUtil.parse(section.getString("zone.location2")));
        this.rewardCommands = section.getStringList("reward-commands");
        this.broadcastDistance = section.getInt("broadcast-distance", 0);
        this.starterEnabled = section.getBoolean("starter.enabled", true);
        this.starterItem = ItemDefinition.from(section.getConfigurationSection("starter"), Material.BLAZE_POWDER);
        this.minimumPlayers = section.getInt("minimum-players", 0);
        this.hologram = DisplaySettings.from(section.getConfigurationSection("hologram"));
        this.bossbar = BossbarSettings.from(section.getConfigurationSection("bossbar"));
        this.scoreboard = ScoreboardSettings.from(section.getConfigurationSection("scoreboard"));
        this.actionbar = ActionbarSettings.from(section.getConfigurationSection("actionbar"));
        this.startCommands = section.getStringList("start-commands");
        this.endCommands = section.getStringList("end-commands");
        this.captureStartCommands = section.getStringList("capture-start-commands");
        this.captureEndCommands = section.getStringList("capture-end-commands");
        this.broadcastOverrides = new HashMap<>();
        ConfigurationSection broadcasts = section.getConfigurationSection("broadcasts");
        if (broadcasts != null) {
            for (String key : broadcasts.getKeys(false)) {
                broadcastOverrides.put(key, broadcasts.getString(key, ""));
            }
        }
    }

    private KothMode parseMode(String value) {
        try {
            return KothMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return KothMode.CAPTURE;
        }
    }

    public String id() { return id; }
    public boolean enabled() { return enabled; }
    public String displayName() { return displayName; }
    public KothMode mode() { return mode; }
    public int captureTimeSeconds() { return captureTimeSeconds; }
    public boolean captureEndResetTime() { return captureEndResetTime; }
    public int maxTimeSeconds() { return maxTimeSeconds; }
    public boolean endRewardCapturer() { return endRewardCapturer; }
    public boolean teamMode() { return teamMode; }
    public KothZone zone() { return zone; }
    public List<String> rewardCommands() { return rewardCommands; }
    public int broadcastDistance() { return broadcastDistance; }
    public ItemDefinition starterItem() { return starterItem; }
    public boolean starterEnabled() { return starterEnabled; }
    public int minimumPlayers() { return minimumPlayers; }
    public DisplaySettings hologram() { return hologram; }
    public BossbarSettings bossbar() { return bossbar; }
    public ScoreboardSettings scoreboard() { return scoreboard; }
    public ActionbarSettings actionbar() { return actionbar; }
    public List<String> startCommands() { return startCommands; }
    public List<String> endCommands() { return endCommands; }
    public List<String> captureStartCommands() { return captureStartCommands; }
    public List<String> captureEndCommands() { return captureEndCommands; }
    public Map<String, String> broadcastOverrides() { return broadcastOverrides; }
    public ConfigurationSection section() { return section; }

    public record DisplaySettings(boolean enabled, double offsetX, double offsetY, double offsetZ, double lineHeight, List<String> lines) {
        static DisplaySettings from(ConfigurationSection section) {
            if (section == null) {
                return new DisplaySettings(false, 0, 3, 0, 0.3, List.of());
            }
            return new DisplaySettings(
                section.getBoolean("enabled", true),
                section.getDouble("location-offset.x", 0.0),
                section.getDouble("location-offset.y", 3.0),
                section.getDouble("location-offset.z", 0.0),
                section.getDouble("line-height", 0.3),
                section.getStringList("lines")
            );
        }
    }

    public record BossbarSettings(boolean enabled, boolean onlyShowWhenCapturing, int viewDistance, String name, BarColor color, BarStyle style) {
        static BossbarSettings from(ConfigurationSection section) {
            if (section == null) {
                return new BossbarSettings(false, false, 64, "", BarColor.GREEN, BarStyle.SEGMENTED_20);
            }
            return new BossbarSettings(
                section.getBoolean("enabled", true),
                section.getBoolean("only-show-when-capturing", false),
                section.getInt("view-distance", 64),
                section.getString("name", ""),
                parseColor(section.getString("color", "GREEN")),
                parseStyle(section.getString("style", "SEGMENTED_20"))
            );
        }

        private static BarColor parseColor(String input) {
            try {
                return BarColor.valueOf(input.toUpperCase(Locale.ROOT));
            } catch (Exception ex) {
                return BarColor.GREEN;
            }
        }

        private static BarStyle parseStyle(String input) {
            try {
                return BarStyle.valueOf(input.toUpperCase(Locale.ROOT));
            } catch (Exception ex) {
                return BarStyle.SEGMENTED_20;
            }
        }
    }

    public record ScoreboardSettings(boolean enabled, boolean onlyShowWhenCapturing, int viewDistance, String title, List<String> lines) {
        static ScoreboardSettings from(ConfigurationSection section) {
            if (section == null) {
                return new ScoreboardSettings(false, false, 64, "&8ꜰᴏᴋᴏᴛʜ", List.of());
            }
            return new ScoreboardSettings(
                section.getBoolean("enabled", true),
                section.getBoolean("only-show-when-capturing", false),
                section.getInt("view-distance", 64),
                section.getString("title", "&8ꜰᴏᴋᴏᴛʜ"),
                section.getStringList("lines")
            );
        }
    }

    public record ActionbarSettings(boolean enabled, int viewDistance, String message) {
        static ActionbarSettings from(ConfigurationSection section) {
            if (section == null) {
                return new ActionbarSettings(false, 64, DEFAULT_ACTIONBAR_MESSAGE);
            }
            return new ActionbarSettings(
                section.getBoolean("enabled", false),
                section.getInt("view-distance", 64),
                section.getString("message", DEFAULT_ACTIONBAR_MESSAGE)
            );
        }
    }
}
