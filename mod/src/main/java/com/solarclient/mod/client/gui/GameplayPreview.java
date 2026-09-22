package com.solarclient.mod.client.gui;

import com.solarclient.mod.client.SolarClientModClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;

/**
 * Captures a still of the last in-game frame so the HUD editor can show it
 * as a backdrop — that way, if you open the editor from the pause menu you
 * see roughly where things sit over your actual gameplay, instead of over
 * an empty dim screen, and can place HUDs against a real reference.
 *
 * HOW: when the pause menu opens we grab the current framebuffer via
 * ScreenshotRecorder.takeScreenshot (the same vanilla path the F2 key uses)
 * which hands back a NativeImage on the render thread. We wrap that in a
 * NativeImageBackedTexture registered under a fixed Identifier, so the
 * editor can just draw it like any other GUI texture. The previous capture
 * is destroyed first so we don't leak GPU textures across pauses.
 *
 * Deliberately a still snapshot, not a live mirror: capturing once on pause
 * avoids per-frame framebuffer blits (expensive, and far more fragile
 * across render-engine changes). "A base idea where to put your stuff" only
 * needs a snapshot.
 */
public final class GameplayPreview {
    public static final Identifier TEXTURE_ID = Identifier.of("solarclient", "gameplay_preview");

    private static boolean available = false;
    private static int imgWidth = 0;
    private static int imgHeight = 0;

    private GameplayPreview() {}

    public static boolean isAvailable() {
        return available;
    }

    public static int width() { return imgWidth; }
    public static int height() { return imgHeight; }

    /**
     * Capture the current frame into TEXTURE_ID. Safe to call when there's
     * no world (it just no-ops and clears availability). Any failure is
     * swallowed to a warning — the editor falls back to its plain dim
     * background, nothing breaks.
     */
    public static void capture(MinecraftClient client) {
        if (client.world == null || client.getFramebuffer() == null) {
            available = false;
            return;
        }
        try {
            ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), (NativeImage image) -> {
                try {
                    imgWidth = image.getWidth();
                    imgHeight = image.getHeight();
                    // Register a texture backed by this image. The texture
                    // takes ownership of the NativeImage and frees it when
                    // destroyed, so we must NOT close `image` ourselves here.
                    NativeImageBackedTexture tex = new NativeImageBackedTexture(TEXTURE_ID::toString, image);
                    client.getTextureManager().destroyTexture(TEXTURE_ID); // drop the previous one
                    client.getTextureManager().registerTexture(TEXTURE_ID, tex);
                    available = true;
                } catch (Exception e) {
                    available = false;
                    SolarClientModClient.LOGGER.warn("Gameplay preview: register failed: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            available = false;
            SolarClientModClient.LOGGER.warn("Gameplay preview: capture failed: {}", e.getMessage());
        }
    }

    public static void clear(MinecraftClient client) {
        available = false;
        try {
            client.getTextureManager().destroyTexture(TEXTURE_ID);
        } catch (Exception ignored) {
        }
    }
}
