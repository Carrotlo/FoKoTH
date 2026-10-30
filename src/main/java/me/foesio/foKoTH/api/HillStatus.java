package me.foesio.foKoTH.api;

import java.util.Map;
import java.util.UUID;

/** Point-in-time state. Win threshold is seconds in CAPTURE mode and points in SCORE mode. */
public record HillStatus(String id, String displayName, String mode, boolean enabled, boolean active,
                         String worldName, int centerX, int centerY, int centerZ,
                         UUID capturer, String capturerTeam, int captureProgressSeconds,
                         int winThreshold, int eventSecondsRemaining, Map<String, Integer> scores) {
    public HillStatus {
        scores = Map.copyOf(scores);
    }
}
