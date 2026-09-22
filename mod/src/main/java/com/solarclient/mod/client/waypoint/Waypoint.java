package com.solarclient.mod.client.waypoint;

public class Waypoint {
    public String name;
    public double x, y, z;
    public String dimension;
    public boolean isDeath;
    public String color = "purple"; // one of WaypointColors.NAMES
    public String worldKey; // server address, or "singleplayer" — null on old waypoints (shown everywhere, grandfathered)

    public Waypoint() {} // for Gson

    public Waypoint(String name, double x, double y, double z, String dimension, boolean isDeath) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.isDeath = isDeath;
        if (isDeath) this.color = "red";
    }
}
