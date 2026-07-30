package me.foesio.foKoTH.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public final class FoKothCapturedEvent extends FoKothEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String teamName;

    public FoKothCapturedEvent(String kothName, Player player, String teamName) {
        super(kothName);
        this.player = player;
        this.teamName = teamName;
    }

    public Player getPlayer() {
        return player;
    }

    public String getTeamName() {
        return teamName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
