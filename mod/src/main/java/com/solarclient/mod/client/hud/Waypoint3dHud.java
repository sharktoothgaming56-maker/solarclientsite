package com.solarclient.mod.client.hud;

import com.solarclient.mod.client.SolarClientModClient;
import com.solarclient.mod.client.config.SolarConfig;
import com.solarclient.mod.client.gui.SpaceTheme;
import com.solarclient.mod.client.waypoint.Waypoint;
import com.solarclient.mod.client.waypoint.WaypointColors;
import com.solarclient.mod.client.waypoint.WaypointManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

/**
 * In-world waypoint markers: a glowing circle at the waypoint with a beam
 * rising out of it, plus a name + distance box when you look at it.
 *
 * HOW THIS WORKS (and why): instead of pushing vertices into Minecraft's
 * world renderer (vertex formats / render layers — APIs that have churned
 * heavily and that I can't verify well enough to trust), this projects
 * each waypoint's world position into screen coordinates manually using
 * the camera's position/yaw/pitch + the FOV option, then draws with the
 * same DrawContext calls every other HUD here already uses. Trade-offs:
 *  - markers render through walls (ESP-style — which matches the ask)
 *  - marker position can drift a few pixels during sprint/speed FOV
 *    changes, since this reads the base FOV setting, not the live
 *    animated FOV. Cosmetic, not functional.
 */
public class Waypoint3dHud {
    public static final String ID = "waypoints_3d";
    private static final int MAX_SHOWN = 8;
    private static final double BEAM_HEIGHT = 48; // blocks

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!HudStyle.shouldRenderHuds() || !SolarConfig.get().isHudEnabled(ID)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!SolarClientModClient.waypointsVisible || client.world == null) return;

        String currentDim = client.world.getRegistryKey().getValue().toString();
        List<Waypoint> visible = WaypointManager.forCurrentWorld(client).stream()
                .filter(w -> w.dimension.equals(currentDim))
                .sorted(Comparator.comparingDouble(w -> WaypointManager.distanceTo(client, w)))
                .limit(MAX_SHOWN)
                .toList();
        if (visible.isEmpty()) return;

        Camera cam = client.gameRenderer.getCamera();
        Vec3d camPos = cam.getCameraPos(); // renamed from getPos() in this version — verified against the 1.21.11 Yarn javadoc
        double yawR = Math.toRadians(cam.getYaw());
        double pitchR = Math.toRadians(cam.getPitch());

        // Camera basis vectors (Minecraft convention: yaw 0 = facing +Z/south).
        double fx = -Math.sin(yawR) * Math.cos(pitchR);
        double fy = -Math.sin(pitchR);
        double fz = Math.cos(yawR) * Math.cos(pitchR);
        double horiz = Math.sqrt(fx * fx + fz * fz);
        if (horiz < 0.01) return; // looking almost straight up/down — skip this frame
        double rx = -fz / horiz, rz = fx / horiz; // right = normalize(cross(forward, worldUp))
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;

        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();
        double fov = client.options.getFov().getValue();
        double halfH = Math.tan(Math.toRadians(fov) / 2.0);
        double aspect = (double) w / h;

        for (Waypoint wp : visible) {
            double[] base = project(wp.x - camPos.x, wp.y - camPos.y, wp.z - camPos.z,
                    rx, rz, ux, uy, uz, fx, fy, fz, halfH, aspect, w, h);
            if (base == null) continue; // behind the camera
            double[] top = project(wp.x - camPos.x, wp.y + BEAM_HEIGHT - camPos.y, wp.z - camPos.z,
                    rx, rz, ux, uy, uz, fx, fy, fz, halfH, aspect, w, h);

            int color = WaypointColors.argb(wp.color);
            double dist = WaypointManager.distanceTo(client, wp);
            int bx = (int) base[0], by = (int) base[1];

            // Beam: stepped line from the circle up to the projected top point.
            if (top != null) {
                drawSteppedLine(ctx, bx, by, (int) top[0], (int) top[1], (0xAA << 24) | (color & 0xFFFFFF));
            }

            // Circle at the waypoint, shrinking with distance, with a soft halo.
            int radius = (int) Math.max(2, Math.min(7, 140.0 / Math.max(dist, 1)));
            SpaceTheme.fillDisc(ctx, bx, by, radius + 3, (0x40 << 24) | (color & 0xFFFFFF));
            SpaceTheme.fillDisc(ctx, bx, by, radius, color);

            // Look-at box: name + distance when the crosshair is near it.
            boolean lookedAt = Math.abs(bx - w / 2) < 35 && Math.abs(by - h / 2) < 35;
            if (lookedAt) {
                String distText = Math.round(dist) + "m";
                int nameW = client.textRenderer.getWidth(wp.name);
                int distW = client.textRenderer.getWidth(distText);
                int boxW = Math.max(nameW, distW) + 12;
                int boxH = 26;
                int boxX = bx - boxW / 2;
                int boxY = by - radius - boxH - 8;
                ctx.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xE0140B22);
                ctx.fill(boxX, boxY, boxX + boxW, boxY + 1, color);
                ctx.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, color);
                ctx.fill(boxX, boxY, boxX + 1, boxY + boxH, color);
                ctx.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, color);
                ctx.drawTextWithShadow(client.textRenderer, wp.name, boxX + 6, boxY + 4, color);
                ctx.drawTextWithShadow(client.textRenderer, distText, boxX + 6, boxY + 15, 0xFFA79FC4);
            }
        }
    }

    /** World-relative offset -> screen [x, y], or null if behind the camera. */
    private static double[] project(double dx, double dy, double dz,
                                    double rx, double rz,
                                    double ux, double uy, double uz,
                                    double fx, double fy, double fz,
                                    double halfH, double aspect, int w, int h) {
        double xc = dx * rx + dz * rz;
        double yc = dx * ux + dy * uy + dz * uz;
        double zc = dx * fx + dy * fy + dz * fz;
        if (zc < 0.1) return null;
        double sx = w / 2.0 + (xc / zc) / (halfH * aspect) * (w / 2.0);
        double sy = h / 2.0 - (yc / zc) / halfH * (h / 2.0);
        return new double[]{sx, sy};
    }

    /** Line between two screen points as small stepped fills (no line primitive needed). */
    private static void drawSteppedLine(DrawContext ctx, int x1, int y1, int x2, int y2, int argb) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) / 2;
        steps = Math.max(1, Math.min(steps, 200));
        for (int i = 0; i <= steps; i++) {
            int px = x1 + (x2 - x1) * i / steps;
            int py = y1 + (y2 - y1) * i / steps;
            ctx.fill(px, py, px + 2, py + 2, argb);
        }
    }
}
