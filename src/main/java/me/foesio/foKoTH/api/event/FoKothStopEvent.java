package me.foesio.foKoTH.api.event;

import org.bukkit.event.HandlerList;

public final class FoKothStopEvent extends FoKothEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final boolean naturalEnd;
    private final FoKothFinishReason reason;

    /** Kept for binary compatibility with existing consumers. */
    public FoKothStopEvent(String kothName, boolean naturalEnd) {
        super(kothName);
        this.naturalEnd = naturalEnd;
        this.reason = naturalEnd ? FoKothFinishReason.TIMEOUT : FoKothFinishReason.MANUAL;
    }

    public FoKothStopEvent(String kothName, FoKothFinishReason reason) {
        super(kothName);
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
        this.naturalEnd = reason == FoKothFinishReason.WIN || reason == FoKothFinishReason.TIMEOUT;
    }

    public boolean isNaturalEnd() {
        return naturalEnd;
    }

    public FoKothFinishReason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
