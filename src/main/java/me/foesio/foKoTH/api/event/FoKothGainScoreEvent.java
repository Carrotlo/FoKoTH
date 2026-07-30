package me.foesio.foKoTH.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public final class FoKothGainScoreEvent extends FoKothEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int score;

    public FoKothGainScoreEvent(String kothName, Player player, int score) {
        super(kothName);
        this.player = player;
        this.score = score;
    }

    public Player getPlayer() {
        return player;
    }

    public int getScore() {
        return score;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
