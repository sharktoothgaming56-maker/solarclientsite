package com.solarclient.mod.client.waypoint;

import java.util.List;
import java.util.Map;

/** Named color choices for waypoints — used by the compass HUD now, and by 3D rendering later. */
public final class WaypointColors {
    public static final List<String> NAMES = List.of("purple", "cyan", "red", "green", "orange", "black", "white");

    private static final Map<String, Integer> ARGB = Map.of(
            "purple", 0xFF9D6BFF,
            "cyan", 0xFF5EE6D9,
            "red", 0xFFE0625A,
            "green", 0xFF5EEB8F,
            "orange", 0xFFFFA15E,
            "black", 0xFF1A1530,
            "white", 0xFFEAE6F7
    );

    private WaypointColors() {}

    public static int argb(String name) {
        return ARGB.getOrDefault(name, ARGB.get("purple"));
    }

    public static String next(String current) {
        int i = NAMES.indexOf(current);
        return NAMES.get((i + 1) % NAMES.size());
    }
}
