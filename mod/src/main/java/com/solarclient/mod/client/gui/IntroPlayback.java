package com.solarclient.mod.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SolarClient loading intro.
 *
 * Frames decode on a worker thread into a short queue; the render thread
 * only uploads one frame at a time into a single dynamic GPU texture.
 * Playback waits for a small prefetch buffer, then runs on a stable clock
 * that pauses (instead of skipping) if decode briefly falls behind.
 */
public final class IntroPlayback {
    public static final int FRAME_COUNT = 102;
    public static final float FPS = 15f;
    public static final Identifier MENU_BG = Identifier.of("solarclient", "textures/intro/menu_bg.png");
    public static final Identifier LOGO = Identifier.of("solarclient", "textures/intro/logo.png");
    public static final Identifier VIDEO_ID = Identifier.of("solarclient", "intro_video_dynamic");

    public static final int LOGO_W = 942, LOGO_H = 258;
    public static final int FRAME_W = 1280, FRAME_H = 720;

    /** End-card lettering box inside FRAME_W×FRAME_H (meta.txt). */
    public static final int ENDCARD_X0 = 315, ENDCARD_Y0 = 271;
    public static final int ENDCARD_X1 = 968, ENDCARD_Y1 = 447;

    private static final int PREFETCH = 6;
    private static final long FRAME_DURATION_NS = (long) (1_000_000_000L / FPS);

    private static final BlockingQueue<NativeImage> QUEUE = new ArrayBlockingQueue<>(20);
    private static final AtomicBoolean producerStarted = new AtomicBoolean(false);
    private static volatile boolean producerDone;
    private static volatile boolean producerFailed;

    private static NativeImageBackedTexture videoTex;
    private static boolean videoFinished;
    private static boolean sessionIntroHandled;
    private static long videoStartNs = -1L;
    private static int displayedFrame = -1;

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

    public static void begin(MinecraftClient client) {
        resetPlaybackState();
        if (client != null) {
            client.getTextureManager().getTexture(MENU_BG);
            client.getTextureManager().getTexture(LOGO);
            ensureVideoTexture(client);
        }
        startProducer();
    }

    private static void resetPlaybackState() {
        videoStartNs = -1L;
        videoFinished = false;
        displayedFrame = -1;
        NativeImage leftover;
        while ((leftover = QUEUE.poll()) != null) {
            leftover.close();
        }
        producerDone = false;
        producerFailed = false;
        producerStarted.set(false);
    }

    private static void startProducer() {
        if (!producerStarted.compareAndSet(false, true)) return;
        Util.getMainWorkerExecutor().execute(() -> {
            try {
                ClassLoader cl = IntroPlayback.class.getClassLoader();
                for (int i = 0; i < FRAME_COUNT; i++) {
                    String path = String.format("assets/solarclient/textures/intro/frame_%03d.png", i);
                    try (InputStream in = cl.getResourceAsStream(path)) {
                        if (in == null) throw new IllegalStateException("missing " + path);
                        QUEUE.put(NativeImage.read(in));
                    }
                }
                producerDone = true;
            } catch (Exception e) {
                producerFailed = true;
                producerDone = true;
                e.printStackTrace();
            }
        });
    }

    private static void ensureVideoTexture(MinecraftClient client) {
        if (videoTex != null) return;
        NativeImage placeholder = new NativeImage(FRAME_W, FRAME_H, false);
        videoTex = new NativeImageBackedTexture(() -> "solarclient/intro_video", placeholder);
        client.getTextureManager().registerTexture(VIDEO_ID, videoTex);
    }

    private static int pumpFrame(MinecraftClient client) {
        ensureVideoTexture(client);
        long now = System.nanoTime();

        if (displayedFrame < 0) {
            // Wait for a small buffer so the first seconds don't stutter.
            if (QUEUE.size() < PREFETCH && !producerDone && !producerFailed) {
                return -1;
            }
            NativeImage first = QUEUE.poll();
            if (first == null) return -1;
            upload(first);
            displayedFrame = 0;
            videoStartNs = now;
            return 0;
        }

        if (displayedFrame >= FRAME_COUNT - 1) {
            videoFinished = true;
            return displayedFrame;
        }

        long due = videoStartNs + (long) (displayedFrame + 1) * FRAME_DURATION_NS;
        if (now >= due) {
            NativeImage next = QUEUE.poll();
            if (next != null) {
                upload(next);
                displayedFrame++;
            } else if (!producerDone) {
                // Decode briefly behind — pause the clock instead of skipping.
                videoStartNs = now - (long) displayedFrame * FRAME_DURATION_NS;
            } else {
                // Producer finished but queue empty before last index — clamp.
                videoFinished = true;
            }
        }

        if (displayedFrame >= FRAME_COUNT - 1) {
            videoFinished = true;
            releaseRemaining();
        }
        return displayedFrame;
    }

    private static void upload(NativeImage src) {
        try {
            NativeImage dst = videoTex.getImage();
            if (dst != null && dst.getWidth() == src.getWidth() && dst.getHeight() == src.getHeight()) {
                dst.copyFrom(src);
                videoTex.upload();
            } else {
                MinecraftClient client = MinecraftClient.getInstance();
                NativeImage owned = new NativeImage(src.getWidth(), src.getHeight(), false);
                owned.copyFrom(src);
                if (client != null) client.getTextureManager().destroyTexture(VIDEO_ID);
                videoTex = new NativeImageBackedTexture(() -> "solarclient/intro_video", owned);
                if (client != null) client.getTextureManager().registerTexture(VIDEO_ID, videoTex);
            }
        } finally {
            src.close();
        }
    }

    private static void releaseRemaining() {
        NativeImage img;
        while ((img = QUEUE.poll()) != null) {
            img.close();
        }
    }

    public static void renderVideoFrame(DrawContext ctx, int screenW, int screenH) {
        MinecraftClient client = MinecraftClient.getInstance();
        int frame = pumpFrame(client);
        if (frame < 0) {
            // Prefetching — show the real space BG so it never feels frozen black.
            drawCover(ctx, MENU_BG, FRAME_W, FRAME_H, screenW, screenH);
            return;
        }
        drawCover(ctx, VIDEO_ID, FRAME_W, FRAME_H, screenW, screenH);
        if (producerFailed && displayedFrame >= 0) {
            videoFinished = producerDone;
        }
    }

    public static void renderMenuBackground(DrawContext ctx, int screenW, int screenH) {
        drawCover(ctx, MENU_BG, FRAME_W, FRAME_H, screenW, screenH);
    }

    public static void drawLogo(DrawContext ctx, int cx, int top, int targetWidth) {
        int w = Math.max(2, targetWidth & ~1);
        SolarLogos.drawCentered(ctx, LOGO, LOGO_W, LOGO_H, cx, top, w);
    }

    public static CoverMapping coverMapping(int screenW, int screenH) {
        float scale = Math.max(screenW / (float) FRAME_W, screenH / (float) FRAME_H);
        int dw = Math.max(1, Math.round(FRAME_W * scale));
        int dh = Math.max(1, Math.round(FRAME_H * scale));
        int x = (screenW - dw) / 2;
        int y = (screenH - dh) / 2;
        return new CoverMapping(scale, x, y, dw, dh);
    }

    public static EndcardLogoPlacement endcardLogoOnScreen(int screenW, int screenH) {
        CoverMapping m = coverMapping(screenW, screenH);
        int boxW = Math.max(1, Math.round((ENDCARD_X1 - ENDCARD_X0) * m.scale));
        int boxH = Math.max(1, Math.round((ENDCARD_Y1 - ENDCARD_Y0) * m.scale));
        int boxY = m.y + Math.round(ENDCARD_Y0 * m.scale);
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
        logoW = Math.max(2, logoW & ~1);
        logoH = Math.max(1, (int) ((long) logoW * LOGO_H / LOGO_W));
        int logoTop = boxY + (boxH - logoH) / 2;
        return new EndcardLogoPlacement(screenW / 2, logoTop, logoW, logoH);
    }

    public record CoverMapping(float scale, int x, int y, int dw, int dh) {}

    public record EndcardLogoPlacement(int cx, int top, int width, int height) {}

    public static void drawCover(DrawContext ctx, Identifier tex, int srcW, int srcH, int screenW, int screenH) {
        CoverMapping m = coverMapping(screenW, screenH);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex,
                m.x, m.y, 0f, 0f,
                m.dw, m.dh,
                srcW, srcH,
                srcW, srcH,
                0xFFFFFFFF);
    }

    public static float easeInOut(float t) {
        t = clamp01(t);
        return t < 0.5f
                ? 4f * t * t * t
                : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

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
