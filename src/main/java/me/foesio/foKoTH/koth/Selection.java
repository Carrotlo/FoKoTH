package me.foesio.foKoTH.koth;

import org.bukkit.Location;

public final class Selection {

    private Location first;
    private Location second;

    public Location first() {
        return first;
    }

    public void first(Location first) {
        this.first = first;
    }

    public Location second() {
        return second;
    }

    public void second(Location second) {
        this.second = second;
    }

    public boolean complete() {
        return first != null && second != null && first.getWorld() != null && first.getWorld().equals(second.getWorld());
    }

    public KothZone zone() {
        return new KothZone(first, second);
    }
}
