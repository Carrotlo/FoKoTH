package me.foesio.foKoTH.api;

import org.bukkit.entity.Player;

/** A lightweight check invoked for players in the hill zone each KOTH tick. */
@FunctionalInterface
public interface ParticipationRule {
    boolean mayContest(String kothId, Player player);
}
