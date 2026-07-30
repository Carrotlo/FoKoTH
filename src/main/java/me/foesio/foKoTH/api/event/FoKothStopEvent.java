package me.foesio.foKoTH.api.event;

import org.bukkit.event.HandlerList;

public final class FoKothStopEvent extends FoKothEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final boolean naturalEnd;

    public FoKothStopEvent(String kothName, boolean naturalEnd) {
        super(kothName);
        this.naturalEnd = naturalEnd;
    }

    public boolean isNaturalEnd() {
        return naturalEnd;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
