package me.foesio.foKoTH.schedule;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class CronExpression {

    private final Set<Integer> minutes;
    private final Set<Integer> hours;
    private final Set<Integer> days;
    private final Set<Integer> months;
    private final Set<Integer> weekdays;

    public CronExpression(String input) {
        String[] split = input.trim().split("\\s+");
        if (split.length != 5) {
            throw new IllegalArgumentException("cron needs 5 segments");
        }
        this.minutes = parse(split[0], 0, 59, null);
        this.hours = parse(split[1], 0, 23, null);
        this.days = parse(split[2], 1, 31, null);
        this.months = parse(split[3], 1, 12, monthNames());
        this.weekdays = parse(split[4], 0, 6, dayNames());
    }

    public boolean matches(ZonedDateTime time) {
        int day = time.getDayOfWeek().getValue() % 7;
        return minutes.contains(time.getMinute())
            && hours.contains(time.getHour())
            && days.contains(time.getDayOfMonth())
            && months.contains(time.getMonthValue())
            && weekdays.contains(day);
    }

    public int secondsUntilNext(ZonedDateTime from, int maxMinutes) {
        ZonedDateTime cursor = from.plusMinutes(1).withSecond(0).withNano(0);
        for (int i = 1; i <= maxMinutes; i++) {
            if (matches(cursor)) {
                return (int) java.time.Duration.between(from, cursor).getSeconds();
            }
            cursor = cursor.plusMinutes(1);
        }
        return -1;
    }

    private Set<Integer> parse(String input, int min, int max, java.util.Map<String, Integer> names) {
        Set<Integer> out = new HashSet<>();
        for (String part : input.split(",")) {
            addPart(out, part.toUpperCase(Locale.ROOT), min, max, names);
        }
        return out;
    }

    private void addPart(Set<Integer> out, String part, int min, int max, java.util.Map<String, Integer> names) {
        int step = 1;
        String range = part;
        if (part.contains("/")) {
            String[] split = part.split("/", 2);
            range = split[0];
            step = Math.max(1, Integer.parseInt(split[1]));
        }
        int start;
        int end;
        if (range.equals("*")) {
            start = min;
            end = max;
        } else if (range.contains("-")) {
            String[] split = range.split("-", 2);
            start = value(split[0], names);
            end = value(split[1], names);
        } else {
            start = value(range, names);
            end = max;
            if (!part.contains("/")) {
                end = start;
            }
        }
        for (int i = start; i <= end; i += step) {
            if (i >= min && i <= max) {
                out.add(i);
            }
        }
    }

    private int value(String input, java.util.Map<String, Integer> names) {
        if (names != null && names.containsKey(input)) {
            return names.get(input);
        }
        return Integer.parseInt(input);
    }

    private java.util.Map<String, Integer> dayNames() {
        return java.util.Map.of(
            "SUN", 0, "MON", 1, "TUE", 2, "WED", 3, "THU", 4, "FRI", 5, "SAT", 6
        );
    }

    private java.util.Map<String, Integer> monthNames() {
        return java.util.Map.ofEntries(
            java.util.Map.entry("JAN", 1), java.util.Map.entry("FEB", 2), java.util.Map.entry("MAR", 3),
            java.util.Map.entry("APR", 4), java.util.Map.entry("MAY", 5), java.util.Map.entry("JUN", 6),
            java.util.Map.entry("JUL", 7), java.util.Map.entry("AUG", 8), java.util.Map.entry("SEP", 9),
            java.util.Map.entry("OCT", 10), java.util.Map.entry("NOV", 11), java.util.Map.entry("DEC", 12)
        );
    }
}
