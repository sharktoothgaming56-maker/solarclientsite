package com.solarclient.mod.client.gui;

import com.solarclient.mod.client.config.SolarConfig;
import com.solarclient.mod.client.waypoint.Waypoint;
import com.solarclient.mod.client.waypoint.WaypointColors;
import com.solarclient.mod.client.waypoint.WaypointManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class WaypointMenuScreen extends SpaceTheme.SpaceScreen {
    private final Screen parent;

    public WaypointMenuScreen(Screen parent) {
        super(Text.literal("Waypoints"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        solarButtons.clear();
        SolarConfig cfg = SolarConfig.get();

        SpaceTheme.SolarButton deathToggle = new SpaceTheme.SolarButton(
                panelX(), 40, panelWidth(), 22,
                "Death Waypoints: " + (cfg.deathWaypointsEnabled ? "ON" : "OFF"), null);
        deathToggle.setAction(() -> {
            cfg.deathWaypointsEnabled = !cfg.deathWaypointsEnabled;
            cfg.save();
            deathToggle.setLabel("Death Waypoints: " + (cfg.deathWaypointsEnabled ? "ON" : "OFF"));
        });
        solarButtons.add(deathToggle);

        // One delete button per row; the name/coords/color chip are drawn in renderContent.
        List<Waypoint> waypoints = WaypointManager.forCurrentWorld(this.client);
        int y = 76;
        for (Waypoint wp : waypoints) {
            solarButtons.add(new SpaceTheme.SolarButton(this.width / 2 + 128, y, 24, 18, "✕", () -> {
                WaypointManager.delete(wp);
                this.client.setScreen(new WaypointMenuScreen(parent)); // rebuild the list
            }));
            y += 24;
            if (y > this.height - 60) break;
        }

        SpaceTheme.SolarButton done = new SpaceTheme.SolarButton(
                panelX(), this.height - 32, panelWidth(), 22, "Done",
                () -> this.client.setScreen(parent));
        done.bold = true;
        solarButtons.add(done);
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("WAYPOINTS").formatted(Formatting.BOLD), this.width / 2, 18, 0xFFB98BFF);

        List<Waypoint> waypoints = WaypointManager.forCurrentWorld(this.client);
        if (waypoints.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, "No waypoints on this world yet — use Create Waypoint in-game.", this.width / 2, 90, 0xFFA79FC4);
            return;
        }

        int y = 76;
        for (Waypoint wp : waypoints) {
            int color = WaypointColors.argb(wp.color);

            // Color chip — click to cycle through colors.
            int chipX = this.width / 2 - 152, chipY = y + 2, chip = 14;
            boolean hover = mouseX >= chipX && mouseX <= chipX + chip && mouseY >= chipY && mouseY <= chipY + chip;
            if (hover) ctx.fill(chipX - 2, chipY - 2, chipX + chip + 2, chipY + chip + 2, 0x80FFFFFF);
            ctx.fill(chipX, chipY, chipX + chip, chipY + chip, color);
            if (justPressed && hover) {
                wp.color = WaypointColors.next(wp.color);
                SolarConfig.get().save();
            }

            String label = (wp.isDeath ? "☠ " : "") + wp.name;
            String coords = String.format("%.0f, %.0f, %.0f", wp.x, wp.y, wp.z);
            ctx.drawTextWithShadow(this.textRenderer, label, this.width / 2 - 130, y + 5, color);
            ctx.drawTextWithShadow(this.textRenderer, coords, this.width / 2 + 20, y + 5, 0xFFA79FC4);

            y += 24;
            if (y > this.height - 60) break;
        }
    }
}
