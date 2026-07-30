package me.foesio.foKoTH.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.foesio.foKoTH.FoKoTH;
import me.foesio.foKoTH.config.KothConfig;
import me.foesio.foKoTH.koth.ActiveKoth;
import me.foesio.foKoTH.util.TimeUtil;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public final class PlaceholderHook extends PlaceholderExpansion {

    private final FoKoTH plugin;

    public PlaceholderHook(FoKoTH plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "fokoth";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Carrotio";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        Player player = offlinePlayer == null ? null : offlinePlayer.getPlayer();
        String lower = params.toLowerCase(Locale.ROOT);
        if (lower.equals("player_wins")) {
            return offlinePlayer == null ? "0" : String.valueOf(plugin.userDataStore().playerWins(offlinePlayer.getUniqueId()));
        }
        if (lower.equals("team_wins")) {
            return player == null ? "0" : String.valueOf(plugin.userDataStore().teamWins(plugin.teamService().teamName(player)));
        }
        if (lower.equals("next")) {
            int seconds = plugin.scheduleManager().secondsUntilNext(null, null);
            return String.valueOf(seconds);
        }
        if (lower.equals("next_formatted")) {
            return TimeUtil.format(plugin.scheduleManager().secondsUntilNext(null, null), plugin.configService().timerFormat());
        }
        if (lower.startsWith("next_")) {
            String schedule = params.substring("next_".length());
            boolean formatted = schedule.toLowerCase(Locale.ROOT).endsWith("_formatted");
            if (formatted) {
                schedule = schedule.substring(0, schedule.length() - "_formatted".length());
            }
            int seconds = plugin.scheduleManager().secondsUntilNext(schedule, null);
            return formatted ? TimeUtil.format(seconds, plugin.configService().timerFormat()) : String.valueOf(seconds);
        }
        int split = params.indexOf('_');
        if (split <= 0) {
            return null;
        }
        String kothId = params.substring(0, split);
        String key = params.substring(split + 1);
        ActiveKoth active = plugin.kothManager().active(kothId);
        if (active != null) {
            Map<String, String> placeholders = active.placeholders(player);
            String value = placeholders.get(key);
            return value == null ? placeholders.get(key.toLowerCase(Locale.ROOT)) : value;
        }
        KothConfig koth = plugin.kothManager().koth(kothId);
        if (koth == null) {
            return null;
        }
        return staticPlaceholder(koth, key);
    }

    private String staticPlaceholder(KothConfig koth, String key) {
        Location center = koth.zone().center();
        String normalized = key.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "active" -> "false";
            case "name" -> koth.id();
            case "displayname", "display_name" -> koth.displayName();
            case "world" -> center == null ? "" : center.getWorld().getName();
            case "x" -> center == null ? "0" : String.valueOf(center.getBlockX());
            case "y" -> center == null ? "0" : String.valueOf(center.getBlockY());
            case "z" -> center == null ? "0" : String.valueOf(center.getBlockZ());
            case "next" -> String.valueOf(plugin.scheduleManager().secondsUntilNext(null, koth.id()));
            case "next_formatted" -> TimeUtil.format(plugin.scheduleManager().secondsUntilNext(null, koth.id()), plugin.configService().timerFormat());
            case "capturer", "capturer_name", "capturer_team", "capturer_team_name" -> "";
            case "maxtime", "max_time" -> String.valueOf(koth.maxTimeSeconds());
            case "maxtime_formatted", "max_time_formatted" -> TimeUtil.format(koth.maxTimeSeconds(), plugin.configService().timerFormat());
            case "maxtimeleft", "max_time_left" -> String.valueOf(koth.maxTimeSeconds());
            case "maxtimeleft_formatted", "max_time_left_formatted" -> TimeUtil.format(koth.maxTimeSeconds(), plugin.configService().timerFormat());
            case "time" -> String.valueOf(koth.captureTimeSeconds());
            case "time_formatted" -> TimeUtil.format(koth.captureTimeSeconds(), plugin.configService().timerFormat());
            case "timeleft", "time_left" -> String.valueOf(koth.captureTimeSeconds());
            case "timeleft_formatted", "time_left_formatted" -> TimeUtil.format(koth.captureTimeSeconds(), plugin.configService().timerFormat());
            default -> null;
        };
    }
}
