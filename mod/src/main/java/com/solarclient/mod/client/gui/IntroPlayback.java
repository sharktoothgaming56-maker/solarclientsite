package com.solarclient.mod.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Plays the bundled SolarClient loading intro (frame sequence from the
 * provided MP4) and exposes the end-card logo / menu background for the
 * title-screen transition. One play per game session.
 */
public final class IntroPlayback {
    public static final int FRAME_COUNT = 106;
    public static final float FPS = 12f;
    public static final Identifier MENU_BG = Identifier.of("solarclient", "textures/intro/menu_bg.png");
    public static final Identifier LOGO = Identifier.of("solarclient", "textures/intro/logo.png");
    /** Native pixel size of textures/intro/logo.png (alpha-trimmed lettering). */
    public static final int LOGO_W = 942, LOGO_H = 258;
    /** Native size of each intro frame / menu_bg. */
    public static final int FRAME_W = 960, FRAME_H = 540;

    /**
     * Bounding box of the end-card lettering inside a FRAME_W×FRAME_H frame
     * (from meta.txt). Used so the title transition starts at the same spot
     * the video left the logo.
     */
    public static final int ENDCARD_X0 = 227, ENDCARD_Y0 = 141;
    public static final int ENDCARD_X1 = 765, ENDCARD_Y1 = 345;

    private static final Identifier[] FRAMES = new Identifier[FRAME_COUNT];
    static {
        for (int i = 0; i < FRAME_COUNT; i++) {
            FRAMES[i] = Identifier.of("solarclient", String.format("textures/intro/frame_%03d.png", i));
        }
    }

    private static boolean videoFinished;
    private static boolean sessionIntroHandled;
    private static boolean preloaded;
    private static long videoStartNs = -1L;
    private static int lastFrameIndex = 0;

    private IntroPlayback() {}

    public static boolean hasPlayedThisSession() {
        return sessionIntroHandled;
    }

    public static void markSessionHandled() {
        sessionIntroHandled = true;
    }

    public static boolean isVideoFinished() {
        return videoFinished;
    }

    public static void resetVideoClock() {
        videoStartNs = -1L;
        videoFinished = false;
        lastFrameIndex = 0;
    }

    public static int currentFrameIndex() {
        return lastFrameIndex;
    }

    /**
     * Force-load every intro frame (+ logo / menu bg) into the texture
     * manager so playback does not hitch on first bind of each PNG.
     */
    public static void preload(MinecraftClient client) {
        if (preloaded || client == null) return;
        var tm = client.getTextureManager();
        for (Identifier id : FRAMES) {
            tm.getTexture(id);
        }
        tm.getTexture(MENU_BG);
        tm.getTexture(LOGO);
        preloaded = true;
    }

    /** Cover the screen with the current intro frame (letterboxed fill). */
    public static void renderVideoFrame(DrawContext ctx, int screenW, int screenH) {
        long now = System.nanoTime();
        if (videoStartNs < 0L) videoStartNs = now;
        float elapsed = (now - videoStartNs) / 1_000_000_000f;
        int frame = Math.min(FRAME_COUNT - 1, Math.max(0, (int) (elapsed * FPS)));
        lastFrameIndex = frame;
        if (frame >= FRAME_COUNT - 1 && elapsed >= (FRAME_COUNT - 1) / FPS) {
            videoFinished = true;
        }
        drawCover(ctx, FRAMES[frame], FRAME_W, FRAME_H, screenW, screenH);
    }

    /** Draw the frozen end-card background (logo removed / cleaned). */
    public static void renderMenuBackground(DrawContext ctx, int screenW, int screenH) {
        drawCover(ctx, MENU_BG, FRAME_W, FRAME_H, screenW, screenH);
    }

    public static void drawLogo(DrawContext ctx, int cx, int top, int targetWidth) {
        SolarLogos.drawCentered(ctx, LOGO, LOGO_W, LOGO_H, cx, top, targetWidth);
    }

    /** How the FRAME_W×FRAME_H image is mapped onto the screen (cover). */
    public static CoverMapping coverMapping(int screenW, int screenH) {
        float scale = Math.max(screenW / (float) FRAME_W, screenH / (float) FRAME_H);
        int dw = Math.max(1, Math.round(FRAME_W * scale));
        int dh = Math.max(1, Math.round(FRAME_H * scale));
        int x = (screenW - dw) / 2;
        int y = (screenH - dh) / 2;
        return new CoverMapping(scale, x, y, dw, dh);
    }

    /**
     * On-screen rect matching the video end-card lettering, for the logo
     * transition start (same spot the MP4 ends on).
     */
    public static EndcardLogoPlacement endcardLogoOnScreen(int screenW, int screenH) {
        CoverMapping m = coverMapping(screenW, screenH);
        int boxW = Math.max(1, Math.round((ENDCARD_X1 - ENDCARD_X0) * m.scale));
        int boxH = Math.max(1, Math.round((ENDCARD_Y1 - ENDCARD_Y0) * m.scale));
        int boxX = m.x + Math.round(ENDCARD_X0 * m.scale);
        int boxY = m.y + Math.round(ENDCARD_Y0 * m.scale);
        // Fit our clean logo PNG inside that box (contain), keep centered.
        float logoAspect = LOGO_W / (float) LOGO_H;
        float boxAspect = boxW / (float) boxH;
        int logoW;
        int logoH;
        if (logoAspect >= boxAspect) {
            logoW = boxW;
            logoH = Math.max(1, Math.round(boxW / logoAspect));
        } else {
            logoH = boxH;
            logoW = Math.max(1, Math.round(boxH * logoAspect));
        }
        int logoTop = boxY + (boxH - logoH) / 2;
        int logoCx = boxX + boxW / 2;
        return new EndcardLogoPlacement(logoCx, logoTop, logoW, logoH);
    }

    public record CoverMapping(float scale, int x, int y, int dw, int dh) {}

    public record EndcardLogoPlacement(int cx, int top, int width, int height) {}

    /** Aspect-fill: cover the whole screen, crop overflow. */
    public static void drawCover(DrawContext ctx, Identifier tex, int srcW, int srcH, int screenW, int screenH) {
        CoverMapping m = coverMapping(screenW, screenH);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex,
                m.x, m.y, 0f, 0f,
                m.dw, m.dh,
                srcW, srcH,
                srcW, srcH,
                0xFFFFFFFF);
    }

    /** Ease in-out cubic, t in 0..1. */
    public static float easeInOut(float t) {
        t = clamp01(t);
        return t < 0.5f
                ? 4f * t * t * t
                : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    /** Ease-out quint — soft settle at the end of a move. */
    public static float easeOut(float t) {
        t = clamp01(t);
        float u = 1f - t;
        return 1f - u * u * u * u * u;
    }

    public static float clamp01(float t) {
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
