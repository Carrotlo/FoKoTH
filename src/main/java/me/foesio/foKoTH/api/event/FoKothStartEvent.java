package me.foesio.foKoTH.api.event;

import org.bukkit.event.HandlerList;

public final class FoKothStartEvent extends FoKothEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public FoKothStartEvent(String kothName) {
        super(kothName);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
