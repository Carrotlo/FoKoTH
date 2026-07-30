package me.foesio.foKoTH.config;

public record FeatureSettings(
    boolean schedules,
    boolean starterItems,
    boolean scoreboards,
    boolean bossbars,
    boolean holograms,
    boolean webhooks,
    boolean placeholderApi,
    boolean teamHooks
) {
}
