package me.foesio.foKoTH.api;

import org.bukkit.entity.Player;

/** Return a stable team name, or null/blank when this player has no team. */
@FunctionalInterface
public interface TeamResolver {
    String teamName(Player player);
}
