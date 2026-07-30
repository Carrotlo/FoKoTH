package me.foesio.foKoTH.schedule;

import me.foesio.core.message.FoMessageService;
import me.foesio.core.text.FoText;
import me.foesio.foKoTH.FoKoTH;
import me.foesio.foKoTH.config.ConfigService;
import me.foesio.foKoTH.koth.KothManager;
import me.foesio.foKoTH.util.TimeUtil;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public final class ScheduleManager {

    private final FoKoTH plugin;
    private final FoMessageService messages;
    private final ConfigService config;
    private final KothManager kothManager;
    private final Map<String, Long> lastRunMinute = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private boolean running;
    private long generation;

    public ScheduleManager(FoKoTH plugin, FoMessageService messages, ConfigService config, KothManager kothManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.config = config;
        this.kothManager = kothManager;
    }

    public void start() {
        stop();
        running = true;
        scheduleTick(40L, ++generation);
    }

    public void stop() {
        running = false;
        generation++;
    }

    public void reload() {
        lastRunMinute.keySet().removeIf(id -> !config.schedules().containsKey(id));
    }

    public String formattedNext(String scheduleId) {
        int seconds = secondsUntilNext(scheduleId, null);
        return TimeUtil.format(seconds, config.timerFormat());
    }

    public int secondsUntilNext(String scheduleId, String kothId) {
        ZonedDateTime now = ZonedDateTime.now();
        int best = -1;
        for (KothSchedule schedule : config.schedules().values()) {
            if (!schedule.enabled()) {
                continue;
            }
            if (scheduleId != null && !schedule.id().equalsIgnoreCase(scheduleId)) {
                continue;
            }
            if (kothId != null && schedule.koths().stream().noneMatch(k -> k.equalsIgnoreCase(kothId))) {
                continue;
            }
            try {
                int seconds = schedule.expression().secondsUntilNext(now, 60 * 24 * 370);
                if (seconds >= 0 && (best < 0 || seconds < best)) {
                    best = seconds;
                }
            } catch (Exception ignored) {
                warnConfig("Invalid cron for schedule " + schedule.id());
            }
        }
        return best;
    }

    private void tick() {
        if (!config.features().schedules()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now().withSecond(0).withNano(0);
        long minute = now.toEpochSecond() / 60L;
        for (KothSchedule schedule : config.schedules().values()) {
            if (!schedule.enabled() || schedule.koths().isEmpty()) {
                continue;
            }
            try {
                if (!schedule.expression().matches(now)) {
                    continue;
                }
                if (lastRunMinute.getOrDefault(schedule.id(), -1L) == minute) {
                    continue;
                }
                lastRunMinute.put(schedule.id(), minute);
                start(schedule);
            } catch (Exception ex) {
                warnConfig("Invalid cron for schedule " + schedule.id() + ": " + ex.getMessage());
            }
        }
    }

    private void scheduleTick(long delayTicks, long generation) {
        plugin.core().scheduler().runGlobalLater(() -> {
            if (!running || generation != this.generation) {
                return;
            }
            try {
                tick();
            } finally {
                if (running && generation == this.generation) {
                    scheduleTick(20L * 30L, generation);
                }
            }
        }, delayTicks);
    }

    private void warnConfig(String message) {
        plugin.getLogger().warning(FoText.plain(messages.render("console.config-warning", "{prefix}{bad}{message}", Map.of("message", message))));
    }

    private void start(KothSchedule schedule) {
        List<String> ids = new ArrayList<>(schedule.koths());
        if (schedule.random()) {
            String id = ids.get(random.nextInt(ids.size())).toLowerCase(Locale.ROOT);
            kothManager.start(id, null, KothManager.StartReason.SCHEDULE);
            return;
        }
        for (String id : ids) {
            kothManager.start(id.toLowerCase(Locale.ROOT), null, KothManager.StartReason.SCHEDULE);
        }
    }
}
