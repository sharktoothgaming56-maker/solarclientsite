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
 * Frames are decoded on a worker thread (never on the render thread), then
 * uploaded one-at-a-time into a single dynamic GPU texture. The play clock
 * only starts after the first frame is ready, and only advances when the
 * next frame is available — so Windows never sees a long main-thread stall
 * ("Not Responding"), and the full animation is actually visible.
 */
public final class IntroPlayback {
    public static final int FRAME_COUNT = 106;
    public static final float FPS = 12f;
    public static final Identifier MENU_BG = Identifier.of("solarclient", "textures/intro/menu_bg.png");
    public static final Identifier LOGO = Identifier.of("solarclient", "textures/intro/logo.png");
    public static final Identifier VIDEO_ID = Identifier.of("solarclient", "intro_video_dynamic");

    public static final int LOGO_W = 942, LOGO_H = 258;
    public static final int FRAME_W = 960, FRAME_H = 540;

    /** End-card lettering box inside FRAME_W×FRAME_H (meta.txt). */
    public static final int ENDCARD_X0 = 229, ENDCARD_Y0 = 143;
    public static final int ENDCARD_X1 = 763, ENDCARD_Y1 = 343;

    private static final BlockingQueue<NativeImage> QUEUE = new ArrayBlockingQueue<>(8);
    private static final AtomicBoolean producerStarted = new AtomicBoolean(false);
    private static volatile boolean producerDone;
    private static volatile boolean producerFailed;

    private static NativeImageBackedTexture videoTex;
    private static boolean videoFinished;
    private static boolean sessionIntroHandled;
    private static long videoStartNs = -1L;
    private static int displayedFrame = -1;
    private static int producedCount = 0;

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
            // Only two static GUI textures — cheap.
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
        // Drain any leftover images from a previous session.
        NativeImage leftover;
        while ((leftover = QUEUE.poll()) != null) {
            leftover.close();
        }
        producerDone = false;
        producerFailed = false;
        producedCount = 0;
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
                        NativeImage img = NativeImage.read(in);
                        QUEUE.put(img); // blocks if consumer is behind — fine off-thread
                        producedCount = i + 1;
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

    /**
     * Pull at most one decoded frame from the queue into the GPU texture
     * when the playhead says it's time. Returns the frame index to draw.
     */
    private static int pumpFrame(MinecraftClient client) {
        ensureVideoTexture(client);
        long now = System.nanoTime();

        if (displayedFrame < 0) {
            NativeImage first = QUEUE.poll();
            if (first == null) return -1; // still decoding — keep UI alive
            upload(first);
            displayedFrame = 0;
            videoStartNs = now;
            return 0;
        }

        if (displayedFrame >= FRAME_COUNT - 1) {
            videoFinished = true;
            return displayedFrame;
        }

        float elapsed = (now - videoStartNs) / 1_000_000_000f;
        int target = Math.min(FRAME_COUNT - 1, (int) (elapsed * FPS));
        // Catch up at most one frame per render tick so we never hitch, and
        // never skip ahead of decoded frames (that was the "instant end").
        if (target > displayedFrame) {
            NativeImage next = QUEUE.poll();
            if (next != null) {
                upload(next);
                displayedFrame++;
                // If decode lagged, re-anchor the clock so playback stays smooth
                // instead of jumping to the end once frames arrive.
                videoStartNs = now - (long) (displayedFrame / FPS * 1_000_000_000L);
            }
        }

        if (displayedFrame >= FRAME_COUNT - 1 && (producerDone || QUEUE.isEmpty())) {
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
                // Size mismatch fallback: re-register with this image (takes ownership).
                MinecraftClient client = MinecraftClient.getInstance();
                NativeImage owned = new NativeImage(src.getWidth(), src.getHeight(), false);
                owned.copyFrom(src);
                if (client != null) {
                    client.getTextureManager().destroyTexture(VIDEO_ID);
                }
                videoTex = new NativeImageBackedTexture(() -> "solarclient/intro_video", owned);
                if (client != null) {
                    client.getTextureManager().registerTexture(VIDEO_ID, videoTex);
                }
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
            // Decoding — solid dark fill keeps the window painting / responsive.
            ctx.fill(0, 0, screenW, screenH, 0xFF05040C);
            return;
        }
        drawCover(ctx, VIDEO_ID, FRAME_W, FRAME_H, screenW, screenH);
        if (producerFailed && displayedFrame >= 0) {
            // Stay on last good frame; still allow reload to finish.
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

    /** End-card size/top; always horizontally centred on the window. */
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
