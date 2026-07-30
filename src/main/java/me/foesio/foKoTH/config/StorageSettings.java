package me.foesio.foKoTH.config;

public record StorageSettings(
    String type,
    String host,
    int port,
    String database,
    String username,
    String password,
    boolean useSsl
) {
}
