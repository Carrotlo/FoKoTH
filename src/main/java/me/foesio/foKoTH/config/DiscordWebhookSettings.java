package me.foesio.foKoTH.config;

import java.util.Map;

public record DiscordWebhookSettings(boolean configured, boolean enabled, String url, Map<String, Boolean> eventNotifications) {

    public boolean shouldNotify(String event) {
        return eventNotifications.getOrDefault(event, true);
    }
}
