package me.foesio.foKoTH.api.event;

import org.bukkit.event.Event;

public abstract class FoKothEvent extends Event {

    private final String kothName;

    protected FoKothEvent(String kothName) {
        this.kothName = kothName;
    }

    public String getKothName() {
        return kothName;
    }
}
