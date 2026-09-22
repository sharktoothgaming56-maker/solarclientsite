package com.solarclient.mod.client.gui;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Plays the bundled SolarClient loading intro (frame sequence from the
 * provided MP4) and exposes the end-card logo / menu background for the
 * title-screen transition. One play per game session.
 */
public final class IntroPlayback {
    public static final int FRAME_COUNT = 84;
    public static final float FPS = 12f;
    public static final Identifier MENU_BG = Identifier.of("solarclient", "textures/intro/menu_bg.png");
    public static final Identifier LOGO = Identifier.of("solarclient", "textures/intro/logo.png");
    /** Native pixel size of textures/intro/logo.png (alpha-trimmed lettering). */
    public static final int LOGO_W = 595, LOGO_H = 205;
    /** Native size of each intro frame / menu_bg. */
    public static final int FRAME_W = 960, FRAME_H = 540;

    private static boolean videoFinished;
    private static boolean sessionIntroHandled;
    private static long videoStartMs = -1L;
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
        videoStartMs = -1L;
        videoFinished = false;
        lastFrameIndex = 0;
    }

    public static int currentFrameIndex() {
        return lastFrameIndex;
    }

    /** Cover the screen with the current intro frame (letterboxed fill). */
    public static void renderVideoFrame(DrawContext ctx, int screenW, int screenH) {
        long now = System.currentTimeMillis();
        if (videoStartMs < 0L) videoStartMs = now;
        float elapsed = (now - videoStartMs) / 1000f;
        int frame = Math.min(FRAME_COUNT - 1, Math.max(0, (int) (elapsed * FPS)));
        lastFrameIndex = frame;
        if (frame >= FRAME_COUNT - 1 && elapsed >= (FRAME_COUNT - 1) / FPS) {
            videoFinished = true;
        }
        Identifier tex = Identifier.of("solarclient", String.format("textures/intro/frame_%03d.png", frame));
        drawCover(ctx, tex, FRAME_W, FRAME_H, screenW, screenH);
    }

    /** Draw the frozen end-card background (logo removed / cleaned). */
    public static void renderMenuBackground(DrawContext ctx, int screenW, int screenH) {
        drawCover(ctx, MENU_BG, FRAME_W, FRAME_H, screenW, screenH);
    }

    public static void drawLogo(DrawContext ctx, int cx, int top, int targetWidth) {
        SolarLogos.drawCentered(ctx, LOGO, LOGO_W, LOGO_H, cx, top, targetWidth);
    }

    /** Aspect-fill: cover the whole screen, crop overflow. */
    public static void drawCover(DrawContext ctx, Identifier tex, int srcW, int srcH, int screenW, int screenH) {
        float scale = Math.max(screenW / (float) srcW, screenH / (float) srcH);
        int dw = Math.max(1, Math.round(srcW * scale));
        int dh = Math.max(1, Math.round(srcH * scale));
        int x = (screenW - dw) / 2;
        int y = (screenH - dh) / 2;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex,
                x, y, 0f, 0f,
                dw, dh,
                srcW, srcH,
                srcW, srcH,
                0xFFFFFFFF);
    }

    /** Ease in-out cubic, t in 0..1. */
    public static float easeInOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t < 0.5f
                ? 4f * t * t * t
                : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }
}
