package com.solarclient.mod.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import com.solarclient.mod.client.config.SolarConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole space look, shared by every SolarClient screen:
 *  - deterministic twinkling starfield (positions seeded per-index so the
 *    sky doesn't reshuffle every frame; only brightness animates)
 *  - two soft glowing planets (layered translucent discs)
 *  - a shooting star every few seconds (time-derived, no state needed)
 *  - SolarButton: a glowing purple gradient button drawn by hand
 *
 * Everything here uses only ctx.fill / fillGradient / text drawing —
 * the APIs already proven working in this project — deliberately
 * avoiding ButtonWidget rendering overrides and the reworked
 * mouseClicked(Click) API that broke earlier. Click handling uses raw
 * GLFW polling, the same technique HudEditorScreen already runs on.
 */
public final class SpaceTheme {
    private SpaceTheme() {}

    /** Fully opaque variant (title screen). */
    public static void paintOpaque(DrawContext ctx, int w, int h) {
        ctx.fillGradient(0, 0, w, h, 0xFF2a1440, 0xFF08050F);
        paintSky(ctx, w, h, 1f);
    }

    /** Translucent variant (in-game menus — world stays faintly visible). */
    public static void paintOverlay(DrawContext ctx, int w, int h) {
        ctx.fillGradient(0, 0, w, h, 0xE0241033, 0xF008050F);
        paintSky(ctx, w, h, 0.8f);
    }

    /** Dim variant (HUD editor — needs to stay out of the way). */
    public static void paintDim(DrawContext ctx, int w, int h) {
        ctx.fillGradient(0, 0, w, h, 0xB01a0e28, 0xC008050F);
        paintSky(ctx, w, h, 0.4f);
    }

    private static void paintSky(DrawContext ctx, int w, int h, float intensity) {
        long time = System.currentTimeMillis();

        // Planets: a big warm one top-right, a small purple one mid-left.
        drawGlowDisc(ctx, (int) (w * 0.86), (int) (h * 0.12), 34, 255, 190, 120, 0.35f * intensity);
        drawGlowDisc(ctx, (int) (w * 0.10), (int) (h * 0.68), 13, 157, 107, 255, 0.45f * intensity);

        // Stars — ~1 per 4000 px², deterministic positions, twinkling alpha.
        int count = Math.max(30, (w * h) / 4000);
        for (int i = 0; i < count; i++) {
            long hash = (i * 2654435761L) ^ 0x9E3779B97F4A7C15L;
            int x = (int) (Math.abs(hash) % w);
            int y = (int) (Math.abs(hash >> 20) % h);
            int size = (Math.abs((int) (hash >> 40)) % 5 == 0) ? 2 : 1;
            float twinkle = (float) (0.55 + 0.45 * Math.sin(time / 400.0 + i * 1.7));
            int alpha = (int) (200 * twinkle * intensity);
            if (alpha <= 8) continue;
            ctx.fill(x, y, x + size, y + size, (alpha << 24) | 0xE6E1F7);
        }

        // Shooting star: one every ~3.5s, streaking for the first 0.7s of
        // its cycle. Derived purely from the clock, so no state to manage.
        long period = 3500;
        long cycle = time / period;
        float progress = (time % period) / 700f;
        if (progress < 1f) {
            long seed = cycle * 6364136223846793005L + 1442695040888963407L;
            int startX = (int) (Math.abs(seed) % (w * 3 / 4));
            int startY = (int) (Math.abs(seed >> 24) % (h / 3));
            float px = startX + progress * (w * 0.35f);
            float py = startY + progress * (h * 0.22f);
            for (int t = 0; t < 10; t++) {
                int alpha = (int) (220 * (1f - progress) * (1f - t / 10f) * intensity);
                if (alpha <= 8) break;
                int sx = (int) (px - t * 3.2f);
                int sy = (int) (py - t * 2.0f);
                ctx.fill(sx, sy, sx + 2, sy + 2, (alpha << 24) | 0xFFFFFF);
            }
        }
    }

    /** A soft glowing disc: concentric translucent filled circles, drawn as row strips. */
    public static void drawGlowDisc(DrawContext ctx, int cx, int cy, int radius, int r, int g, int b, float coreAlpha) {
        for (int layer = 3; layer >= 0; layer--) {
            int lr = radius + layer * (radius / 3 + 2);
            int alpha = (int) (255 * coreAlpha / (1 + layer * 2));
            if (alpha <= 4) continue;
            fillDisc(ctx, cx, cy, lr, (alpha << 24) | (r << 16) | (g << 8) | b);
        }
    }

    /** Filled circle via horizontal strips — no circle primitive needed. */
    public static void fillDisc(DrawContext ctx, int cx, int cy, int radius, int argb) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
            ctx.fill(cx - half, cy + dy, cx + half, cy + dy + 1, argb);
        }
    }

    // =================================================================
    // SolarButton — hand-drawn glowing button
    // =================================================================
    public static class SolarButton {
        public int x, y, width, height;
        private String label;
        private Runnable action;
        public boolean bold;

        public SolarButton(int x, int y, int width, int height, String label, Runnable action) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.label = label; this.action = action;
        }

        public void setLabel(String label) { this.label = label; }
        public void setAction(Runnable action) { this.action = action; }

        public boolean isHovered(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        public void render(DrawContext ctx, MinecraftClient client, int mouseX, int mouseY) {
            boolean hover = isHovered(mouseX, mouseY);
            // Fill/gradient tint follows the button colour; border + glow
            // follow the stroke colour — both picked independently in
            // Solar Menu > Colours.
            SolarConfig cfg = com.solarclient.mod.client.config.SolarConfig.get();
            int fillRgb = cfg.getButtonColor() & 0xFFFFFF;
            int strokeRgb = cfg.getStrokeColor() & 0xFFFFFF;

            // Tint the base gradient toward the fill colour (darkened) so
            // the button body actually reflects the chosen colour rather
            // than a fixed purple.
            int top = hover ? tint(fillRgb, 0.42f, 0.94f) : tint(fillRgb, 0.28f, 0.88f);
            int bottom = hover ? tint(fillRgb, 0.24f, 0.94f) : tint(fillRgb, 0.14f, 0.88f);
            ctx.fillGradient(x, y, x + width, y + height, top, bottom);

            int border = hover ? (0xFF000000 | strokeRgb) : (0x80000000 | strokeRgb);
            ctx.fill(x, y, x + width, y + 1, border);
            ctx.fill(x, y + height - 1, x + width, y + height, border);
            ctx.fill(x, y, x + 1, y + height, border);
            ctx.fill(x + width - 1, y, x + width, y + height, border);
            // (no outer hover glow — the vanilla button texture can't draw
            // outside its bounds, so both button types omit it and render
            // pixel-identically)

            Text text = bold ? Text.literal(label).formatted(Formatting.BOLD) : Text.literal(label);
            int tx = x + (width - client.textRenderer.getWidth(text)) / 2;
            int ty = y + (height - 8) / 2;
            ctx.drawTextWithShadow(client.textRenderer, text, tx, ty, hover ? 0xFFFFFFFF : 0xFFEAE6F7);
        }

        public void click() { if (action != null) action.run(); }

        /**
         * Darken an RGB toward black by {@code factor} (0..1) and apply an
         * alpha (0..1), returning ARGB. Used so the button body takes on a
         * dim shade of the chosen fill colour rather than a fixed purple.
         */
        private static int tint(int rgb, float factor, float alpha) {
            int r = (int) (((rgb >> 16) & 0xFF) * factor);
            int g = (int) (((rgb >> 8) & 0xFF) * factor);
            int b = (int) ((rgb & 0xFF) * factor);
            int a = (int) (alpha * 255) & 0xFF;
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    // =================================================================
    // SpaceScreen — base for all SolarClient screens: paints the theme,
    // owns a SolarButton list, and does GLFW edge-detected clicking.
    // =================================================================
    public abstract static class SpaceScreen extends Screen {
        protected final List<SolarButton> solarButtons = new ArrayList<>();
        // Starts true so a click held over from the previous screen can't
        // instantly trigger a button sitting under the cursor.
        private boolean wasMouseDown = true;
        protected boolean justPressed = false; // exposed for subclasses with extra custom hit areas

        protected SpaceScreen(Text title) { super(title); }

        // ----- Responsive layout helpers ---------------------------------
        // Every SolarClient menu sizes its buttons off these instead of a
        // hardcoded 200px, so buttons grow/shrink and stay centred when the
        // window is resized. init() re-runs on resize, so reading these
        // there keeps the layout correct at any window size.

        /** Full-width button width: ~44% of the window, clamped to a sane range. */
        protected int panelWidth() {
            return Math.max(160, Math.min(320, (int) (this.width * 0.44f)));
        }

        /** Left x for a centred full-width button. */
        protected int panelX() {
            return (this.width - panelWidth()) / 2;
        }

        /** Half-width (for two-column rows), accounting for a 4px gutter. */
        protected int halfWidth() {
            return (panelWidth() - 4) / 2;
        }

        /** x of the left column in a two-column row. */
        protected int leftColX() {
            return panelX();
        }

        /** x of the right column in a two-column row. */
        protected int rightColX() {
            return panelX() + halfWidth() + 4;
        }

        /** true = opaque space background (title screen); false = translucent overlay. */
        protected boolean opaqueBackground() { return false; }

        /**
         * Whether the bottom-right SOLARCLIENT wordmark is drawn on this
         * screen. Defaults on; the title screen and the Solar Menu turn it
         * off because their artwork already carries the branding.
         */
        protected boolean showWordmark() { return true; }

        /**
         * Optional Essential-style icon rail down the far right edge.
         * Screens opt in by assigning this in init(); null means no rail.
         * Lives on the base class so the click plumbing is written once
         * and every screen that wants one behaves identically.
         */
        protected SolarSideRail sideRail;

        protected abstract void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta);

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            if (opaqueBackground()) SpaceTheme.paintOpaque(ctx, this.width, this.height);
            else SpaceTheme.paintOverlay(ctx, this.width, this.height);

            boolean mouseDown = GLFW.glfwGetMouseButton(this.client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            justPressed = mouseDown && !wasMouseDown;
            wasMouseDown = mouseDown;

            // Iterate a SNAPSHOT, not the live list. A button's click can
            // call clearAndInit() (e.g. opening a different conversation),
            // which clears and refills solarButtons — mutating it mid
            // for-each would throw ConcurrentModificationException and crash
            // the game. Render over the snapshot, then fire at most one
            // click and stop, since the list may no longer be valid after.
            List<SolarButton> snapshot = new ArrayList<>(solarButtons);
            for (SolarButton b : snapshot) {
                b.render(ctx, this.client, mouseX, mouseY);
            }
            if (justPressed) {
                for (SolarButton b : snapshot) {
                    if (b.isHovered(mouseX, mouseY)) { b.click(); break; }
                }
            }

            renderContent(ctx, mouseX, mouseY, delta);

            // Rail draws after content so its tooltip floats over
            // everything, and is click-tested BEFORE the centre stack
            // would ever see the same press.
            if (sideRail != null) {
                sideRail.layout(this.width, this.height);
                sideRail.render(ctx, mouseX, mouseY);
                if (justPressed) sideRail.mouseClicked(mouseX, mouseY, 0);
            }

            // Big SOLARCLIENT wordmark, bottom-right (screens can opt out).
            if (showWordmark()) {
            Text mark = Text.literal("SOLARCLIENT").formatted(Formatting.BOLD);
            float markScale = 1.8f;
            int mx = this.width - (int) (this.client.textRenderer.getWidth(mark) * markScale) - 8;
            int my = this.height - (int) (12 * markScale) - 4;
            com.solarclient.mod.client.hud.HudStyle.beginScale(ctx, mx, my, markScale);
            int markColor = 0xFF000000 | (com.solarclient.mod.client.config.SolarConfig.get().getButtonColor() & 0xFFFFFF);
            ctx.drawTextWithShadow(this.client.textRenderer, mark, 0, 0, markColor);
            com.solarclient.mod.client.hud.HudStyle.endScale(ctx);
            }

            super.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldPause() { return false; }
    }
}
