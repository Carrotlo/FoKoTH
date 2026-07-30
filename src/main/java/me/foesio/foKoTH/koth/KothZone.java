package me.foesio.foKoTH.koth;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class KothZone {

    private final Location first;
    private final Location second;
    private final World world;
    private final boolean valid;
    private final Location center;
    private final double minX;
    private final double maxX;
    private final double minY;
    private final double maxY;
    private final double minZ;
    private final double maxZ;

    public KothZone(Location first, Location second) {
        this.first = first == null ? null : first.clone();
        this.second = second == null ? null : second.clone();
        this.world = this.first == null ? null : this.first.getWorld();
        this.valid = this.first != null && this.second != null && world != null && world.equals(this.second.getWorld());
        if (valid) {
            this.minX = Math.min(this.first.getX(), this.second.getX());
            this.maxX = Math.max(this.first.getX(), this.second.getX()) + 1.0;
            this.minY = Math.min(this.first.getY(), this.second.getY());
            this.maxY = Math.max(this.first.getY(), this.second.getY()) + 1.0;
            this.minZ = Math.min(this.first.getZ(), this.second.getZ());
            this.maxZ = Math.max(this.first.getZ(), this.second.getZ()) + 1.0;
            this.center = new Location(world,
                (minX + maxX) / 2.0,
                (minY + maxY) / 2.0,
                (minZ + maxZ) / 2.0);
        } else {
            this.minX = 0.0;
            this.maxX = 0.0;
            this.minY = 0.0;
            this.maxY = 0.0;
            this.minZ = 0.0;
            this.maxZ = 0.0;
            this.center = null;
        }
    }

    public Location first() {
        return first == null ? null : first.clone();
    }

    public Location second() {
        return second == null ? null : second.clone();
    }

    public World world() {
        return world;
    }

    public boolean valid() {
        return valid;
    }

    public Location center() {
        return center == null ? null : center.clone();
    }

    public boolean contains(Player player) {
        if (!valid || player == null || !player.getWorld().equals(world)) {
            return false;
        }
        Location loc = player.getLocation();
        return loc.getX() >= minX && loc.getX() < maxX
            && loc.getY() >= minY && loc.getY() < maxY
            && loc.getZ() >= minZ && loc.getZ() < maxZ;
    }
}
