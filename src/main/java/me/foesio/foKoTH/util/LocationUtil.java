package me.foesio.foKoTH.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class LocationUtil {

    private LocationUtil() {
    }

    public static Location parse(String input) {
        if (input == null) {
            return null;
        }
        String[] split = input.split(";");
        if (split.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(split[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = parseDouble(split[1]);
            double y = parseDouble(split[2]);
            double z = parseDouble(split[3]);
            float yaw = split.length > 4 ? (float) parseDouble(split[4]) : 0.0f;
            float pitch = split.length > 5 ? (float) parseDouble(split[5]) : 0.0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static String serialize(Location location) {
        return location.getWorld().getName() + ";"
            + round(location.getX()) + ";"
            + round(location.getY()) + ";"
            + round(location.getZ()) + ";"
            + round(location.getYaw()) + ";"
            + round(location.getPitch());
    }

    public static String shortString(Location location) {
        return location.getWorld().getName() + " "
            + location.getBlockX() + ", "
            + location.getBlockY() + ", "
            + location.getBlockZ();
    }

    private static double parseDouble(String input) {
        return Double.parseDouble(input.trim());
    }

    private static String round(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }
}
