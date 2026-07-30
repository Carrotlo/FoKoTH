package me.foesio.foKoTH.schedule;

import java.util.List;

public record KothSchedule(String id, boolean enabled, boolean random, String cron, List<String> koths) {

    public CronExpression expression() {
        return new CronExpression(cron);
    }
}
