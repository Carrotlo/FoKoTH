package me.foesio.foKoTH.util;

import me.foesio.core.number.DurationParser;
import me.foesio.core.number.TickDuration;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {

    private static final Pattern PART_PATTERN = Pattern.compile("(\\d+)([dhms])");

    private TimeUtil() {
    }

    public static int parseSeconds(String input) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("empty time");
        }
        if (normalized.matches("-?\\d+")) {
            return Integer.parseInt(normalized);
        }
        Optional<TickDuration> coreDuration = DurationParser.parse(normalized);
        if (coreDuration.isPresent()) {
            long seconds = coreDuration.get().secondsFloor();
            if (seconds > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("time too large");
            }
            return (int) seconds;
        }
        Matcher matcher = PART_PATTERN.matcher(normalized);
        long seconds = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                throw new IllegalArgumentException("invalid time");
            }
            long value = Long.parseLong(matcher.group(1));
            seconds += switch (matcher.group(2)) {
                case "d" -> value * 86400;
                case "h" -> value * 3600;
                case "m" -> value * 60;
                default -> value;
            };
            if (seconds > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("time too large");
            }
            end = matcher.end();
        }
        if (end != normalized.length()) {
            throw new IllegalArgumentException("invalid time");
        }
        return (int) seconds;
    }

    public static String format(int seconds, int mode) {
        if (seconds < 0) {
            return "Never";
        }
        int safe = Math.max(0, seconds);
        int h = safe / 3600;
        int m = safe % 3600 / 60;
        int s = safe % 60;
        return switch (mode) {
            case 2 -> shortFormat(safe);
            case 3 -> String.format("%02dh %02dm %02ds", h, m, s);
            default -> String.format("%02d:%02d:%02d", h, m, s);
        };
    }

    private static String shortFormat(int seconds) {
        if (seconds >= 86400) {
            return seconds / 86400 + "d";
        }
        if (seconds >= 3600) {
            return seconds / 3600 + "h";
        }
        if (seconds >= 60) {
            return seconds / 60 + "m";
        }
        return seconds + "s";
    }
}
