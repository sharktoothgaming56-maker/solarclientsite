package com.solarclient.mod.client.gui;

import com.solarclient.mod.client.config.SolarConfig;
import com.solarclient.mod.client.hud.HudInfo;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * HUD editor, reworked around the pattern established client editors use
 * (select -> edit in a panel, drag with snapping) instead of tiny per-box
 * corner icons:
 *
 *  - CLICK a HUD box to select it (white outline). Click empty space to
 *    deselect.
 *  - DRAG a box to move it. While dragging, the box SNAPS to the screen's
 *    vertical/horizontal centre and to an 6px margin from each edge; cyan
 *    guide lines show when a snap is active. This makes tidy layouts easy,
 *    which was the main inefficiency before.
 *  - The BOTTOM BAR shows big controls for the selected HUD:
 *    [Shown/Hidden] [-] [1.0x] [+] [Colour] [Reset pos]. Large targets, no
 *    overlap with the box labels.
 *
 * Backdrop: the captured gameplay still (if opened from the pause menu),
 * else the dim theme. Mouse stays on raw GLFW polling — the Click-object
 * Screen API broke the classic overrides in this MC version (known issue
 * in this project).
 */
public class HudEditorScreen extends Screen {
    private static final int SNAP = 6;      // snap distance in px
    private static final int MARGIN = 6;    // edge-margin snap target
    private static final int PANEL_W = 150;
    private static final int PANEL_H = 108;
    private static final int TITLE_H = 14;

    private HudInfo dragging = null;
    private HudInfo selected = null;
    private int dragOffsetX, dragOffsetY;
    private boolean wasMouseDown = true; // true so a held-over click can't instantly grab
    private boolean snappedX = false, snappedY = false;
    private int snapLineX = 0, snapLineY = 0;

    // The floating control panel: a compact box you can drag anywhere, so it
    // never blocks the part of the screen you're arranging. Position is
    // remembered for the session; it only appears once a HUD is selected.
    private int panelX = -1, panelY = -1;
    private boolean draggingPanel = false;
    private int panelDragDX, panelDragDY;

    public HudEditorScreen() {
        super(Text.literal("Edit HUDs"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (GameplayPreview.isAvailable()) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, GameplayPreview.TEXTURE_ID,
                    0, 0, 0f, 0f, this.width, this.height,
                    this.width, this.height, this.width, this.height, 0xFFFFFFFF);
            ctx.fill(0, 0, this.width, this.height, 0x66000000);
        } else {
            SpaceTheme.paintDim(ctx, this.width, this.height);
        }

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                "Click to select · drag to move (snaps to centre/edges) · Esc when done",
                this.width / 2, 8, 0xFFFFFFFF);

        SolarConfig cfg = SolarConfig.get();
        boolean mouseDown = GLFW.glfwGetMouseButton(this.client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean justPressed = mouseDown && !wasMouseDown;
        boolean justReleased = !mouseDown && wasMouseDown;
        boolean clickedSomething = false;

        // ---- Bottom control bar for the selected HUD (drawn/handled first
        // so its clicks never fall through to boxes underneath) ----
        if (panelX < 0) { // first show: park it top-right, out of the way
            panelX = this.width - PANEL_W - 8;
            panelY = 24;
        }
        if (selected != null) {
            clickedSomething |= renderControlPanel(ctx, cfg, mouseX, mouseY, justPressed, mouseDown, justReleased);
        } else {
            ctx.drawCenteredTextWithShadow(this.textRenderer, "Click a HUD to edit it",
                    this.width / 2, this.height - 14, 0xFFA79FC4);
        }

        // ---- HUD boxes ----
        for (HudInfo info : HudInfo.ALL) {
            float scale = cfg.getHudScale(info.id);
            int w = (int) (info.width * scale);
            int h = Math.max((int) (info.height * scale), 24);
            int[] pos = cfg.hudPosition.containsKey(info.id)
                    ? cfg.getHudPosition(info.id, 0, 0)
                    : info.defaultPos.apply(this.client, new int[]{w, h});
            boolean enabled = cfg.isHudEnabled(info.id);
            boolean isSel = selected == info;

            int fill = enabled ? 0x559D6BFF : 0x40666666;
            ctx.fill(pos[0], pos[1], pos[0] + w, pos[1] + h, fill);
            int outline = isSel ? 0xFFFFFFFF : (enabled ? 0xFF9D6BFF : 0xFF666666);
            drawBoxOutline(ctx, pos[0], pos[1], w, h, outline);
            ctx.drawTextWithShadow(this.textRenderer, info.label, pos[0] + 4, pos[1] + 4,
                    enabled ? 0xFFFFFFFF : 0xFF999999);

            boolean overPanel = selected != null
                    && mouseX >= panelX && mouseX <= panelX + PANEL_W
                    && mouseY >= panelY && mouseY <= panelY + PANEL_H;
            boolean inside = mouseX >= pos[0] && mouseX <= pos[0] + w
                    && mouseY >= pos[1] && mouseY <= pos[1] + h && !overPanel;
            if (justPressed && inside && dragging == null && !clickedSomething) {
                selected = info;
                dragging = info;
                dragOffsetX = mouseX - pos[0];
                dragOffsetY = mouseY - pos[1];
                clickedSomething = true;
            }
        }

        // Click on empty space (not bar, not a box) deselects.
        if (justPressed && !clickedSomething && !draggingPanel) {
            selected = null;
        }

        // ---- Dragging with snapping ----
        snappedX = snappedY = false;
        if (dragging != null && mouseDown) {
            float scale = cfg.getHudScale(dragging.id);
            int w = (int) (dragging.width * scale);
            int h = Math.max((int) (dragging.height * scale), 24);
            int nx = mouseX - dragOffsetX;
            int ny = mouseY - dragOffsetY;

            // Snap X: screen centre, left margin, right margin.
            int cx = this.width / 2 - w / 2;
            if (Math.abs(nx - cx) <= SNAP) { nx = cx; snappedX = true; snapLineX = this.width / 2; }
            else if (Math.abs(nx - MARGIN) <= SNAP) { nx = MARGIN; snappedX = true; snapLineX = MARGIN; }
            else if (Math.abs(nx - (this.width - MARGIN - w)) <= SNAP) { nx = this.width - MARGIN - w; snappedX = true; snapLineX = this.width - MARGIN; }

            // Snap Y: screen centre, top margin, bottom margin (above bar).
            int cy = this.height / 2 - h / 2;
            int bottomLimit = this.height;
            if (Math.abs(ny - cy) <= SNAP) { ny = cy; snappedY = true; snapLineY = this.height / 2; }
            else if (Math.abs(ny - MARGIN) <= SNAP) { ny = MARGIN; snappedY = true; snapLineY = MARGIN; }
            else if (Math.abs(ny - (bottomLimit - MARGIN - h)) <= SNAP) { ny = bottomLimit - MARGIN - h; snappedY = true; snapLineY = bottomLimit - MARGIN; }

            cfg.hudPosition.put(dragging.id, new int[]{nx, ny}); // save() on release

            if (snappedX) ctx.fill(snapLineX, 0, snapLineX + 1, this.height, 0xFF35D0E0);
            if (snappedY) ctx.fill(0, snapLineY, this.width, snapLineY + 1, 0xFF35D0E0);
        }
        if (justReleased && dragging != null) {
            cfg.save();
            dragging = null;
        }

        wasMouseDown = mouseDown;
        super.render(ctx, mouseX, mouseY, delta);
    }

    /**
     * Draws + handles the floating control panel for the selected HUD.
     * It's a compact draggable box (grab its title bar) so it can be moved
     * clear of whatever you're arranging — including the bottom of the
     * screen. Returns true if the click landed on the panel.
     */
    private boolean renderControlPanel(DrawContext ctx, SolarConfig cfg, int mouseX, int mouseY,
                                       boolean justPressed, boolean mouseDown, boolean justReleased) {
        // Keep the panel fully on-screen if the window was resized.
        panelX = Math.max(0, Math.min(panelX, this.width - PANEL_W));
        panelY = Math.max(0, Math.min(panelY, this.height - PANEL_H));

        boolean over = mouseX >= panelX && mouseX <= panelX + PANEL_W
                && mouseY >= panelY && mouseY <= panelY + PANEL_H;

        // Body + border + title bar.
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xF0140b22);
        drawBoxOutline(ctx, panelX, panelY, PANEL_W, PANEL_H, 0xFFB98BFF);
        ctx.fill(panelX + 1, panelY + 1, panelX + PANEL_W - 1, panelY + TITLE_H, 0xFF2a1845);

        String title = selected.label;
        if (this.textRenderer.getWidth(title) > PANEL_W - 12) {
            while (title.length() > 3 && this.textRenderer.getWidth(title + "...") > PANEL_W - 12) {
                title = title.substring(0, title.length() - 1);
            }
            title = title + "...";
        }
        ctx.drawTextWithShadow(this.textRenderer, title, panelX + 5, panelY + 3, 0xFFEAE6F7);
        ctx.drawTextWithShadow(this.textRenderer, "\u2725", panelX + PANEL_W - 12, panelY + 3, 0xFFA79FC4); // drag hint

        // Title-bar dragging.
        boolean overTitle = mouseX >= panelX && mouseX <= panelX + PANEL_W
                && mouseY >= panelY && mouseY <= panelY + TITLE_H;
        if (justPressed && overTitle) {
            draggingPanel = true;
            panelDragDX = mouseX - panelX;
            panelDragDY = mouseY - panelY;
        }
        if (draggingPanel && mouseDown) {
            panelX = mouseX - panelDragDX;
            panelY = mouseY - panelDragDY;
        }
        if (justReleased) draggingPanel = false;

        boolean enabled = cfg.isHudEnabled(selected.id);
        float scale = cfg.getHudScale(selected.id);
        int hudColor = cfg.getHudColor(selected.id, 0xFFEAE6F7);
        boolean clicked = over;

        int ix = panelX + 6;
        int iw = PANEL_W - 12;
        int y = panelY + TITLE_H + 5;

        // Row 1: visibility
        barButton(ctx, ix, y, iw, 18, enabled ? "Shown" : "Hidden", mouseX, mouseY, justPressed,
                () -> cfg.setHudEnabled(selected.id, !enabled));
        y += 22;

        // Row 2: scale  [-] 1.00x [+]
        barButton(ctx, ix, y, 18, 18, "-", mouseX, mouseY, justPressed,
                () -> cfg.setHudScale(selected.id, scale - 0.25f));
        ctx.drawCenteredTextWithShadow(this.textRenderer, String.format("%.2fx", scale),
                ix + iw / 2, y + 5, 0xFFEAE6F7);
        barButton(ctx, ix + iw - 18, y, 18, 18, "+", mouseX, mouseY, justPressed,
                () -> cfg.setHudScale(selected.id, scale + 0.25f));
        y += 22;

        // Row 3: colour (chip + label)
        boolean hovC = mouseX >= ix && mouseX <= ix + iw && mouseY >= y && mouseY <= y + 18;
        ctx.fill(ix, y, ix + iw, y + 18, hovC ? 0xF0432a6e : 0xE02a1845);
        drawBoxOutline(ctx, ix, y, iw, 18, hovC ? 0xFFB98BFF : 0x80B98BFF);
        ctx.fill(ix + 4, y + 4, ix + 14, y + 14, hudColor);
        drawBoxOutline(ctx, ix + 4, y + 4, 10, 10, 0x80FFFFFF);
        ctx.drawTextWithShadow(this.textRenderer, "Colour", ix + 18, y + 5, 0xFFEAE6F7);
        if (justPressed && hovC) {
            this.client.setScreen(new HudColorScreen(this, selected.id, selected.label));
        }
        y += 22;

        // Row 4: reset position
        barButton(ctx, ix, y, iw, 18, "Reset position", mouseX, mouseY, justPressed, () -> {
            cfg.hudPosition.remove(selected.id);
            cfg.save();
        });

        return clicked;
    }

    /** Small flat bar button; runs action and returns true when clicked. */
    private boolean barButton(DrawContext ctx, int x, int y, int w, int h, String label,
                              int mouseX, int mouseY, boolean justPressed, Runnable action) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        ctx.fill(x, y, x + w, y + h, hover ? 0xF0432a6e : 0xE02a1845);
        drawBoxOutline(ctx, x, y, w, h, hover ? 0xFFB98BFF : 0x80B98BFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer, label, x + w / 2, y + (h - 8) / 2, 0xFFEAE6F7);
        if (justPressed && hover) {
            action.run();
            return true;
        }
        return false;
    }

    private static void drawBoxOutline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
