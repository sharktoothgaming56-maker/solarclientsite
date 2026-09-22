package com.solarclient.mod.client.gui;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Draws the two supplied PNG logos (bundled under
 * assets/solarclient/textures/gui) centred and scaled: the SOLARCLIENT
 * wordmark on the title screen, and the GAME MENU wordmark on the pause
 * menu. Uses the same GUI_TEXTURED draw path the effects HUD already uses,
 * with the 13-arg scaled drawTexture so the source PNG can be drawn at any
 * on-screen size while keeping its aspect ratio.
 */
public final class SolarLogos {
    public static final Identifier TITLE = Identifier.of("solarclient", "textures/gui/title_logo.png");
    public static final Identifier GAME_MENU = Identifier.of("solarclient", "textures/gui/game_menu_logo.png");
    public static final Identifier KEYBINDS = Identifier.of("solarclient", "textures/gui/keybinds_logo.png");
    public static final Identifier MULTIPLAYER = Identifier.of("solarclient", "textures/gui/multiplayer_logo.png");

    // Native pixel sizes of the supplied PNGs (used for aspect ratio).
    // These are the alpha-trimmed sizes of the latest artwork drop, so the
    // drawn logo centres exactly with no invisible padding throwing it off.
    public static final int TITLE_W = 604, TITLE_H = 126;
    public static final int GAME_MENU_W = 498, GAME_MENU_H = 113;
    public static final int KEYBINDS_W = 578, KEYBINDS_H = 94;
    public static final int MULTIPLAYER_W = 587, MULTIPLAYER_H = 72;

    private SolarLogos() {}

    /**
     * Draw a logo centred horizontally on cx, with its TOP at y, scaled so
     * its width is targetWidth (height follows to keep aspect ratio).
     */
    public static void drawCentered(DrawContext ctx, Identifier tex, int srcW, int srcH, int cx, int y, int targetWidth) {
        int w = targetWidth;
        int h = Math.max(1, (int) ((long) targetWidth * srcH / srcW));
        int x = cx - w / 2;
        // width/height = on-screen size; u,v = 0; regionWidth/Height sample
        // the FULL native texture (srcW×srcH), and textureWidth/Height are
        // the native texture size — so the whole PNG maps across the drawn
        // rectangle at any target size.
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex,
                x, y, 0f, 0f,
                w, h,          // on-screen size
                srcW, srcH,    // region sampled (full texture)
                srcW, srcH,    // full texture size
                0xFFFFFFFF);
    }
}
