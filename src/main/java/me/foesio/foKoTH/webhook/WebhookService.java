package me.foesio.foKoTH.webhook;

import me.foesio.core.command.CommandPlaceholders;
import me.foesio.core.discord.DiscordWebhookEmbed;
import me.foesio.core.discord.DiscordWebhookMessage;
import me.foesio.core.discord.DiscordWebhookService;
import me.foesio.foKoTH.config.DiscordWebhookSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

public final class WebhookService {

    private final JavaPlugin plugin;

    public WebhookService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void send(DiscordWebhookSettings settings, ConfigurationSection root, String event, Map<String, String> placeholders) {
        String url = resolveUrl(settings, root, event);
        if (url == null || url.isBlank()) {
            return;
        }
        ConfigurationSection section = root == null ? null : root.getConfigurationSection(event);
        me.foesio.core.discord.DiscordWebhookSettings coreSettings = new me.foesio.core.discord.DiscordWebhookSettings(
            true,
            url,
            plugin.getName(),
            "",
            true,
            Duration.ofSeconds(7),
            Map.of(event, true)
        );
        DiscordWebhookService.create(plugin, coreSettings).send(event, message(event, section, placeholders));
    }

    private String resolveUrl(DiscordWebhookSettings settings, ConfigurationSection root, String event) {
        ConfigurationSection section = root == null ? null : root.getConfigurationSection(event);
        if (settings.configured()) {
            if (!settings.enabled() || !settings.shouldNotify(event)) {
                return null;
            }
            if (section != null && !section.getBoolean("enabled", true)) {
                return null;
            }
            return settings.url();
        }
        if (root == null || root.getString("url", "").isBlank()) {
            return null;
        }
        if (section != null && !section.getBoolean("enabled", true)) {
            return null;
        }
        return root.getString("url", "");
    }

    private DiscordWebhookMessage message(String event, ConfigurationSection section, Map<String, String> placeholders) {
        EventDefaults defaults = defaults(event);
        String content = apply(section == null ? defaults.content() : section.getString("content", defaults.content()), placeholders);
        String title = apply(section == null ? defaults.title() : section.getString("title.text", defaults.title()), placeholders);
        String color = (section == null ? defaults.color() : section.getString("color", defaults.color())).replace("#", "");
        int decimalColor = parseColor(color);
        DiscordWebhookEmbed.Builder embed = DiscordWebhookEmbed.builder().color(decimalColor);
        if (!title.isBlank()) {
            embed.title(title);
        }
        if (section != null && section.contains("description")) {
            embed.description(apply(section.getString("description", ""), placeholders));
        }
        ConfigurationSection fields = section == null ? null : section.getConfigurationSection("fields");
        if (fields != null && !fields.getKeys(false).isEmpty()) {
            for (String key : fields.getKeys(false)) {
                ConfigurationSection field = fields.getConfigurationSection(key);
                if (field == null) {
                    continue;
                }
                embed.field(
                    apply(field.getString("name", ""), placeholders),
                    apply(field.getString("value", ""), placeholders),
                    field.getBoolean("inline", false)
                );
            }
        }
        return DiscordWebhookMessage.builder().content(content).embed(embed.build()).build();
    }

    private EventDefaults defaults(String event) {
        return switch (event) {
            case "start" -> new EventDefaults("Get ready!", "#03fc88", "%name% - STARTED");
            case "stop" -> new EventDefaults("", "#ff5d73", "%name% - STOPPED");
            case "capture-start" -> new EventDefaults("", "#3ecf8e", "%name% - CAPTURE STARTED");
            case "capture-end" -> new EventDefaults("", "#ff5d73", "%name% - CAPTURE STOPPED");
            case "captured" -> new EventDefaults(":tada:", "#3ecf8e", "%name% - CAPTURED");
            default -> new EventDefaults("", "#03fc88", "%name%");
        };
    }

    private String apply(String input, Map<String, String> placeholders) {
        return CommandPlaceholders.apply(input, placeholders);
    }

    private int parseColor(String color) {
        String clean = color == null ? "" : color.trim().replace("#", "").toLowerCase(Locale.ROOT);
        if (clean.length() > 6) {
            clean = clean.substring(clean.length() - 6);
        }
        try {
            return Integer.parseInt(clean, 16);
        } catch (NumberFormatException ignored) {
            return 261256;
        }
    }

    private record EventDefaults(String content, String color, String title) {}
}
