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
 *  - SolarButton: clear 3D liquid-glass capsule (rounded ends) matching
 *    the launcher glass recipe — frosted clear fill, specular, iridescent rim
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
    // SolarButton — clear 3D liquid-glass, capsule (rounded) ends
    // Matches the launcher liquid-glass recipe as closely as DrawContext
    // fill/fillGradient allows. Shape is a stadium/pill: fully circled
    // left and right ends (radius = height/2).
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

        /** Pill hit-test: rectangle middle + circular caps on each end. */
        public boolean isHovered(int mouseX, int mouseY) {
            if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
            int r = Math.max(1, height / 2);
            int leftCap = x + r;
            int rightCap = x + width - r;
            if (mouseX >= leftCap && mouseX < rightCap) return true;
            float cy = y + height * 0.5f;
            float dy = mouseY + 0.5f - cy;
            if (mouseX < leftCap) {
                float dx = mouseX + 0.5f - leftCap;
                return dx * dx + dy * dy <= (float) r * r;
            }
            float dx = mouseX + 0.5f - rightCap;
            return dx * dx + dy * dy <= (float) r * r;
        }

        public void render(DrawContext ctx, MinecraftClient client, int mouseX, int mouseY) {
            boolean hover = isHovered(mouseX, mouseY);
            // Stroke accent still follows Solar Menu > Colours (rim only).
            // Body stays clear glass like the launcher — no heavy fill tint.
            SolarConfig cfg = com.solarclient.mod.client.config.SolarConfig.get();
            int strokeRgb = cfg.getStrokeColor() & 0xFFFFFF;

            paintLiquidGlass(ctx, x, y, width, height, strokeRgb, hover);

            Text text = bold ? Text.literal(label).formatted(Formatting.BOLD) : Text.literal(label);
            int tx = x + (width - client.textRenderer.getWidth(text)) / 2;
            int ty = y + (height - 8) / 2;
            ctx.drawTextWithShadow(client.textRenderer, text, tx, ty, hover ? 0xFFFFFFFF : 0xFFF2F0FA);
        }

        public void click() { if (action != null) action.run(); }

        /**
         * Clear liquid-glass capsule matching the launcher CSS recipe:
         *   fill 155°: white 26% → 8% → 3% → dark 28%
         *   specular top-left + caustic bottom-right + sheen band
         *   inset bevel + iridescent rim
         * Ends are fully rounded (radius = h/2).
         */
        private static void paintLiquidGlass(DrawContext ctx, int x, int y, int w, int h,
                                             int strokeRgb, boolean hover) {
            int r = Math.max(1, Math.min(h / 2, w / 2));

            // Soft drop shadow under the pill (depth).
            int shadowA = hover ? 0x48 : 0x32;
            fillPill(ctx, x + 1, y + 2, w, h, (shadowA << 24));

            // ---- clear glass body (launcher gradient stops) ----
            // top ~26%/32% white, then 8%/10%, 3%/4%, bottom dark ~28%/30%
            if (hover) {
                fillPillVGradient(ctx, x, y, w, h, r,
                        argb(0.32f, 255, 255, 255),
                        argb(0.10f, 255, 255, 255),
                        argb(0.04f, 255, 255, 255),
                        argb(0.30f, 22, 16, 42));
            } else {
                fillPillVGradient(ctx, x, y, w, h, r,
                        argb(0.26f, 255, 255, 255),
                        argb(0.08f, 255, 255, 255),
                        argb(0.03f, 255, 255, 255),
                        argb(0.28f, 18, 14, 36));
            }

            // Specular sheet — bright radial wash top-left (launcher ::before).
            float specA = hover ? 0.55f : 0.42f;
            fillPillRadial(ctx, x, y, w, h, r,
                    x + w * 0.16f, y - h * 0.10f, w * 0.85f,
                    argb(specA, 255, 255, 255), 0x00FFFFFF);

            // Lower-right caustic (aqua/violet wash).
            float cauA = hover ? 0.18f : 0.12f;
            int cau = blendRgb(0xAA96FF, strokeRgb, 0.25f);
            fillPillRadial(ctx, x, y, w, h, r,
                    x + w * 0.92f, y + h * 1.15f, w * 0.70f,
                    0x00000000, withAlpha(cau, cauA));

            // Diagonal sheen band (soft-light highlight stripe).
            float sheenA = hover ? 0.30f : 0.22f;
            fillPillSheen(ctx, x, y, w, h, r, sheenA);

            // Inset bevel: bright top edge, dark bottom edge (clipped to pill).
            int hi = hover ? argb(0.70f, 255, 255, 255) : argb(0.55f, 255, 255, 255);
            int lo = hover ? argb(0.38f, 0, 0, 0) : argb(0.32f, 0, 0, 0);
            strokePillEdge(ctx, x, y, w, h, r, hi, true);   // top half brighter
            strokePillEdge(ctx, x, y, w, h, r, lo, false);  // bottom half darker

            // Iridescent 1px rim (launcher ::after chromatic edge).
            drawIridescentPillRim(ctx, x, y, w, h, r, strokeRgb, hover);
        }

        // ----- pill geometry helpers -----

        /** Horizontal inset from each side at row {@code row} (0..h-1) for a stadium. */
        private static int pillInset(int row, int h, int radius) {
            float cy = (h - 1) * 0.5f;
            float dy = row - cy;
            float rr = radius;
            float inside = rr * rr - dy * dy;
            if (inside <= 0f) return radius; // degenerate tip
            float half = (float) Math.sqrt(inside);
            // Distance from rect side to the arc: radius - chord half-width.
            return Math.max(0, Math.round(rr - half));
        }

        private static void fillPill(DrawContext ctx, int x, int y, int w, int h, int argb) {
            int r = Math.max(1, Math.min(h / 2, w / 2));
            for (int row = 0; row < h; row++) {
                int inset = pillInset(row, h, r);
                int x0 = x + inset;
                int x1 = x + w - inset;
                if (x1 > x0) ctx.fill(x0, y + row, x1, y + row + 1, argb);
            }
        }

        /** 4-stop vertical gradient clipped to the pill. */
        private static void fillPillVGradient(DrawContext ctx, int x, int y, int w, int h, int r,
                                              int c0, int c1, int c2, int c3) {
            for (int row = 0; row < h; row++) {
                float t = h <= 1 ? 0f : (float) row / (h - 1);
                int color;
                if (t < 0.26f) color = lerpArgb(c0, c1, t / 0.26f);
                else if (t < 0.52f) color = lerpArgb(c1, c2, (t - 0.26f) / 0.26f);
                else color = lerpArgb(c2, c3, (t - 0.52f) / 0.48f);
                int inset = pillInset(row, h, r);
                int x0 = x + inset;
                int x1 = x + w - inset;
                if (x1 > x0) ctx.fill(x0, y + row, x1, y + row + 1, color);
            }
        }

        /** Soft radial wash clipped to the pill (centre → edge fades). */
        private static void fillPillRadial(DrawContext ctx, int x, int y, int w, int h, int r,
                                           float cx, float cy, float radius,
                                           int centre, int edge) {
            float invR = radius <= 1f ? 1f : 1f / radius;
            for (int row = 0; row < h; row++) {
                int inset = pillInset(row, h, r);
                int x0 = x + inset;
                int x1 = x + w - inset;
                float py = y + row + 0.5f;
                for (int px = x0; px < x1; px++) {
                    float dx = px + 0.5f - cx;
                    float dy = py - cy;
                    float d = (float) Math.sqrt(dx * dx + dy * dy) * invR;
                    if (d >= 1f) continue;
                    // Smoothstep falloff
                    float t = d * d * (3f - 2f * d);
                    int col = lerpArgb(centre, edge, t);
                    if (((col >>> 24) & 0xFF) < 3) continue;
                    ctx.fill(px, y + row, px + 1, y + row + 1, col);
                }
            }
        }

        /** Diagonal sheen stripe across the pill (launcher liquid-sheen band). */
        private static void fillPillSheen(DrawContext ctx, int x, int y, int w, int h, int r, float peakA) {
            // Band centre runs ~diagonal; animate gently by time so it feels liquid.
            float phase = (float) ((System.currentTimeMillis() % 5800L) / 5800.0);
            // Sweep from right→left like the CSS keyframes.
            float bandX = x + w * (1.2f - phase * 1.6f);
            float bandW = Math.max(6f, w * 0.18f);
            for (int row = 0; row < h; row++) {
                int inset = pillInset(row, h, r);
                int x0 = x + inset;
                int x1 = x + w - inset;
                float py = y + row + 0.5f;
                // Slight diagonal skew
                float skew = (py - y) * 0.35f;
                for (int px = x0; px < x1; px++) {
                    float d = Math.abs(px + 0.5f - (bandX + skew)) / bandW;
                    if (d >= 1f) continue;
                    float a = peakA * (1f - d) * (1f - d);
                    int col = argb(a, 255, 255, 255);
                    ctx.fill(px, y + row, px + 1, y + row + 1, col);
                }
            }
        }

        /** Top or bottom half outline stroke along the pill perimeter. */
        private static void strokePillEdge(DrawContext ctx, int x, int y, int w, int h, int r,
                                           int color, boolean topHalf) {
            int mid = h / 2;
            for (int row = 0; row < h; row++) {
                boolean inHalf = topHalf ? row <= mid : row >= mid;
                if (!inHalf) continue;
                int inset = pillInset(row, h, r);
                // Only the outermost pixel of the pill on this row.
                int ly = y + row;
                ctx.fill(x + inset, ly, x + inset + 1, ly + 1, color);
                ctx.fill(x + w - inset - 1, ly, x + w - inset, ly + 1, color);
                if (row == 0 || row == h - 1) {
                    // Flat top/bottom span between caps.
                    int x0 = x + inset;
                    int x1 = x + w - inset;
                    if (x1 > x0) ctx.fill(x0, ly, x1, ly + 1, color);
                }
            }
        }

        /** Chromatic rim around the capsule edge. */
        private static void drawIridescentPillRim(DrawContext ctx, int x, int y, int w, int h, int r,
                                                  int strokeRgb, boolean hover) {
            float a = hover ? 0.95f : 0.82f;
            int cTL = withAlpha(blendRgb(0xFFFFFF, strokeRgb, 0.12f), a);
            int cTR = withAlpha(blendRgb(0xAAF0DC, strokeRgb, 0.22f), a * 0.92f);
            int cBR = withAlpha(blendRgb(0xD2AFFF, strokeRgb, 0.28f), a * 0.88f);
            int cBL = withAlpha(blendRgb(0x96C8FF, strokeRgb, 0.22f), a * 0.92f);

            for (int row = 0; row < h; row++) {
                float ty = h <= 1 ? 0f : (float) row / (h - 1);
                int leftCol = lerpArgb(cTL, cBL, ty);
                int rightCol = lerpArgb(cTR, cBR, ty);
                int inset = pillInset(row, h, r);
                int ly = y + row;
                ctx.fill(x + inset, ly, x + inset + 1, ly + 1, leftCol);
                ctx.fill(x + w - inset - 1, ly, x + w - inset, ly + 1, rightCol);
            }
            // Top & bottom spans between the caps.
            for (int i = r; i < w - r; i++) {
                float tx = w <= 1 ? 0f : (float) i / (w - 1);
                ctx.fill(x + i, y, x + i + 1, y + 1, lerpArgb(cTL, cTR, tx));
                ctx.fill(x + i, y + h - 1, x + i + 1, y + h, lerpArgb(cBL, cBR, tx));
            }
        }

        private static int argb(float alpha, int r, int g, int b) {
            return (clamp255((int) (alpha * 255)) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
        }

        private static int blendRgb(int a, int b, float bWeight) {
            float aw = 1f - bWeight;
            int r = clamp255((int) (((a >> 16) & 0xFF) * aw + ((b >> 16) & 0xFF) * bWeight));
            int g = clamp255((int) (((a >> 8) & 0xFF) * aw + ((b >> 8) & 0xFF) * bWeight));
            int bl = clamp255((int) ((a & 0xFF) * aw + (b & 0xFF) * bWeight));
            return (r << 16) | (g << 8) | bl;
        }

        private static int withAlpha(int rgb, float alpha) {
            return (clamp255((int) (alpha * 255)) << 24) | (rgb & 0xFFFFFF);
        }

        private static int lerpArgb(int a, int b, float t) {
            if (t <= 0f) return a;
            if (t >= 1f) return b;
            int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
            int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
            return (clamp255((int) (aa + (ba - aa) * t)) << 24)
                    | (clamp255((int) (ar + (br - ar) * t)) << 16)
                    | (clamp255((int) (ag + (bg - ag) * t)) << 8)
                    | clamp255((int) (ab + (bb - ab) * t));
        }

        private static int clamp255(int v) {
            return v < 0 ? 0 : (v > 255 ? 255 : v);
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

        /** Full-width button width: ~44% of the window, clamped to a sane range. Even so left/right margins match. */
        protected int panelWidth() {
            int w = Math.max(160, Math.min(320, (int) (this.width * 0.44f)));
            return w & ~1;
        }

        /** Left x for a centred full-width button (identical left/right inset). */
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
