package com.solarclient.mod.client.waypoint;

import com.solarclient.mod.client.config.SolarConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;

import java.util.List;

public class WaypointManager {
    private static boolean pendingDeath = false;
    private static double lastX, lastY, lastZ;

    public static List<Waypoint> all() {
        return SolarConfig.get().waypoints;
    }

    /** Which "world bucket" we're in: the server's address, or one shared singleplayer bucket. */
    public static String currentWorldKey(MinecraftClient client) {
        var server = client.getCurrentServerEntry();
        return server != null ? server.address : "singleplayer";
    }

    /** Waypoints for the server/world we're currently on (null-keyed legacy ones show everywhere). */
    public static List<Waypoint> forCurrentWorld(MinecraftClient client) {
        String key = currentWorldKey(client);
        return SolarConfig.get().waypoints.stream()
                .filter(w -> w.worldKey == null || w.worldKey.equals(key))
                .toList();
    }

    public static void create(String name, MinecraftClient client) {
        create(name, "purple", client);
    }

    public static void create(String name, String color, MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        Waypoint wp = new Waypoint(
                name,
                client.player.getX(), client.player.getY(), client.player.getZ(),
                client.world.getRegistryKey().getValue().toString(),
                false
        );
        wp.color = color;
        wp.worldKey = currentWorldKey(client);
        SolarConfig cfg = SolarConfig.get();
        cfg.waypoints.add(wp);
        cfg.save();
    }

    public static void delete(Waypoint wp) {
        SolarConfig cfg = SolarConfig.get();
        cfg.waypoints.remove(wp);
        cfg.save();
    }

    /**
     * Call every client tick. Tracks the player's last-known position (so
     * we still have real death coordinates after they've respawned) and
     * watches for the vanilla DeathScreen opening — that's a safe, stable
     * signal for "the player just died" without needing a Mixin into
     * combat/damage code.
     */
    public static void tick(MinecraftClient client) {
        if (client.player != null) {
            lastX = client.player.getX();
            lastY = client.player.getY();
            lastZ = client.player.getZ();
        }

        boolean onDeathScreen = client.currentScreen instanceof DeathScreen;
        if (onDeathScreen && !pendingDeath) {
            pendingDeath = true;
            if (SolarConfig.get().deathWaypointsEnabled && client.world != null) {
                SolarConfig cfg = SolarConfig.get();
                Waypoint deathWp = new Waypoint(
                        "Death " + (countDeathWaypoints(cfg) + 1),
                        lastX, lastY, lastZ,
                        client.world.getRegistryKey().getValue().toString(),
                        true
                );
                deathWp.worldKey = currentWorldKey(client);
                cfg.waypoints.add(deathWp);
                cfg.save();
            }
        }
        if (!onDeathScreen) pendingDeath = false;
    }

    private static int countDeathWaypoints(SolarConfig cfg) {
        int n = 0;
        for (Waypoint w : cfg.waypoints) if (w.isDeath) n++;
        return n;
    }

    /** Distance from the player to a waypoint, ignoring dimension (caller should check that separately). */
    public static double distanceTo(MinecraftClient client, Waypoint wp) {
        if (client.player == null) return -1;
        double dx = wp.x - client.player.getX();
        double dy = wp.y - client.player.getY();
        double dz = wp.z - client.player.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Compass-style bearing from the player to a waypoint, in degrees (0 = matches player's current yaw). */
    public static float bearingTo(MinecraftClient client, Waypoint wp) {
        if (client.player == null) return 0;
        double dx = wp.x - client.player.getX();
        double dz = wp.z - client.player.getZ();
        double angleToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float relative = (float) (angleToTarget - client.player.getYaw());
        relative = relative % 360;
        if (relative < -180) relative += 360;
        if (relative > 180) relative -= 360;
        return relative;
    }
}
