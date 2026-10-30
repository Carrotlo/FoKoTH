package me.foesio.foKoTH.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** Fired after validation, before any start side effects. */
public final class FoKothPreStartEvent extends FoKothEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    public enum Cause { COMMAND, STARTER, SCHEDULE, API }

    private final Cause cause;
    private final Player starter;
    private boolean cancelled;

    public FoKothPreStartEvent(String kothName, Cause cause, Player starter) {
        super(kothName);
        this.cause = cause;
        this.starter = starter;
    }

    public Cause getCause() { return cause; }

    /** Non-null only for a starter-item start. */
    public Player getStarter() { return starter; }

    @Override public boolean isCancelled() { return cancelled; }

    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
