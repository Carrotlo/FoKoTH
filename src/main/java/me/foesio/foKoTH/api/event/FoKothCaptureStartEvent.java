package me.foesio.foKoTH.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public final class FoKothCaptureStartEvent extends FoKothEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;

    public FoKothCaptureStartEvent(String kothName, Player player) {
        super(kothName);
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
