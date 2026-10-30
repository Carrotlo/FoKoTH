package me.foesio.foKoTH.api;

/** Outcome of an API start or stop request. */
public enum KothActionResult {
    STARTED, STOPPED, NOT_FOUND, DISABLED, INVALID_ZONE, ALREADY_ACTIVE,
    NOT_ACTIVE, INSUFFICIENT_PLAYERS, CANCELLED, PLUGIN_DISABLED
}
