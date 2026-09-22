package com.solarclient.mod.client.gui;

import com.solarclient.mod.client.config.SolarConfig;
import com.solarclient.mod.client.hud.HudInfo;
import com.solarclient.mod.client.hud.Waypoint3dHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * The Solar Menu: HUD on/off toggles, HUD editor, waypoints, button
 * colour, and keybind presets.
 *
 * WHY THIS IS A SCROLLING LIST NOW (the "mushed up together" fix): the old
 * version laid every row out at a fixed 26px pitch starting at y=46 and
 * anchored Back to the window bottom. On a short window the rows simply
 * ran past the bottom and overlapped Back — exactly the squished look in
 * the report. Here the toggle rows live inside a fixed content viewport
 * between a header and a pinned footer; if they don't all fit, the
 * viewport scrolls (wheel or drag). The header title and the Back/footer
 * buttons stay put, so nothing overlaps at any window height.
 */
public class SolarMenuScreen extends SpaceTheme.SpaceScreen {
    private final Screen parent; // null when opened via keybind rather than the pause menu

    // Scroll viewport bounds, recomputed in init() so they track window size.
    private int listTop, listBottom;
    private int scroll = 0;      // pixels scrolled down
    private int contentHeight = 0; // total height of all rows

    // Rows are (re)built each frame into this list so their y reflects the
    // current scroll offset; buttons in solarButtons that fall outside the
    // viewport are skipped for both drawing and clicking.
    private final List<Row> rows = new ArrayList<>();

    private record Row(SpaceTheme.SolarButton button, int baseY) {}

    public SolarMenuScreen(Screen parent) {
        super(Text.literal("Solar Menu"));
        this.parent = parent;
    }

    @Override
    protected boolean showWordmark() {
        return false; // requested: no corner SOLARCLIENT text on this menu
    }

    @Override
    protected void init() {
        solarButtons.clear();
        rows.clear();
        SolarConfig cfg = SolarConfig.get();

        listTop = 40;
        listBottom = this.height - 40; // leave room for the pinned footer

        int rowPitch = 26;
        int baseY = 0;

        // HUD on/off toggles, one per HUD, plus the 3D waypoints toggle.
        for (HudInfo info : HudInfo.ALL) {
            baseY = addToggle(cfg, info.id, info.label, baseY, rowPitch);
        }
        baseY = addToggle(cfg, Waypoint3dHud.ID, "3D Waypoints", baseY, rowPitch);

        baseY += 8;
        baseY = addAction("Edit HUD Positions", baseY, rowPitch,
                () -> this.client.setScreen(new HudEditorScreen()));
        baseY = addAction("Waypoints", baseY, rowPitch,
                () -> this.client.setScreen(new WaypointMenuScreen(this)));
        baseY = addAction("UI Buttons Colors", baseY, rowPitch,
                () -> this.client.setScreen(new ButtonColorScreen(this)));

        // Compatibility escape hatch for mods that draw their own menu UI
        // over vanilla's screens (Essential). Takes effect next time a menu
        // opens — no restart needed.
        SpaceTheme.SolarButton menus = new SpaceTheme.SolarButton(
                panelX(), 0, panelWidth(), 22,
                "Custom Menus: " + (cfg.customMenus ? "ON" : "OFF"), null);
        menus.setAction(() -> {
            cfg.customMenus = !cfg.customMenus;
            cfg.save();
            menus.setLabel("Custom Menus: " + (cfg.customMenus ? "ON" : "OFF"));
        });
        rows.add(new Row(menus, baseY));
        baseY += rowPitch;
        // Bottom-left wordmark: settings toggle only (it's pinned, not a
        // movable HUD).
        baseY = addToggle(cfg, com.solarclient.mod.client.hud.LogoHud.ID, "SolarClient Logo", baseY, rowPitch);

        contentHeight = baseY;

        // Clamp scroll in case the window grew since last time.
        int maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        // Pinned footer: Back/Close, always at the bottom, never scrolls.
        SpaceTheme.SolarButton back = new SpaceTheme.SolarButton(
                panelX(), this.height - 32, panelWidth(), 22,
                parent != null ? "Back" : "Close",
                () -> this.client.setScreen(parent));
        back.bold = true;
        solarButtons.add(back); // footer button lives directly in solarButtons (always visible)
    }

    private int addToggle(SolarConfig cfg, String hudId, String label, int baseY, int pitch) {
        SpaceTheme.SolarButton btn = new SpaceTheme.SolarButton(
                panelX(), 0, panelWidth(), 22,
                label + ": " + (cfg.isHudEnabled(hudId) ? "ON" : "OFF"), null);
        btn.setAction(() -> {
            boolean now = !cfg.isHudEnabled(hudId);
            cfg.setHudEnabled(hudId, now);
            btn.setLabel(label + ": " + (now ? "ON" : "OFF"));
        });
        rows.add(new Row(btn, baseY));
        return baseY + pitch;
    }

    private int addAction(String label, int baseY, int pitch, Runnable action) {
        SpaceTheme.SolarButton btn = new SpaceTheme.SolarButton(panelX(), 0, panelWidth(), 22, label, action);
        rows.add(new Row(btn, baseY));
        return baseY + pitch;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Position the scrollable rows for this frame, then let the base
        // class draw background + footer button + wordmark. We draw the
        // rows ourselves (clipped to the viewport) before calling super so
        // click handling still flows through the base justPressed logic.
        for (Row row : rows) {
            row.button().y = listTop + row.baseY() - scroll;
        }

        super.render(ctx, mouseX, mouseY, delta);

        // Clip to the viewport and draw + handle the scrolling rows.
        ctx.enableScissor(0, listTop, this.width, listBottom);
        for (Row row : rows) {
            SpaceTheme.SolarButton b = row.button();
            if (b.y + b.height < listTop || b.y > listBottom) continue; // fully offscreen
            b.render(ctx, this.client, mouseX, mouseY);
            if (justPressed && b.isHovered(mouseX, mouseY)
                    && mouseY >= listTop && mouseY <= listBottom) {
                b.click();
            }
        }
        ctx.disableScissor();

        // Scrollbar hint when the content overflows.
        int viewport = listBottom - listTop;
        if (contentHeight > viewport) {
            int trackX = panelX() + panelWidth() + 6;
            int barH = Math.max(20, viewport * viewport / contentHeight);
            int maxScroll = contentHeight - viewport;
            int barY = listTop + (maxScroll == 0 ? 0 : (viewport - barH) * scroll / maxScroll);
            ctx.fill(trackX, listTop, trackX + 3, listBottom, 0x30FFFFFF);
            ctx.fill(trackX, barY, trackX + 3, barY + barH, 0xFF000000 | (SolarConfig.get().getButtonColor() & 0xFFFFFF));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int viewport = listBottom - listTop;
        int maxScroll = Math.max(0, contentHeight - viewport);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (verticalAmount * 18)));
        return true;
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("SOLAR MENU").formatted(Formatting.BOLD),
                this.width / 2, 20, 0xFF000000 | (SolarConfig.get().getButtonColor() & 0xFFFFFF));
    }

    @Override
    public boolean shouldPause() {
        return parent == null;
    }
}
