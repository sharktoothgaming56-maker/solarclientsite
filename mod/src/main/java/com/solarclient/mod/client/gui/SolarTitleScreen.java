package com.solarclient.mod.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Custom main menu. On the first open of a session (right after the intro
 * video): the end-card lettering rises to the title position (still large),
 * then scales down to the resting size, then menu buttons fade/slide in
 * under it — keeping the intro's space background the whole time.
 *
 * Logo width == button panel width so the sides line up; both scale with
 * the window on resize (init() re-runs).
 */
public class SolarTitleScreen extends SpaceTheme.SpaceScreen {
    private enum Phase { LOGO_RISE, LOGO_SHRINK, MENU_IN, DONE }

    private final java.util.List<ModCompat.ModButton> modExtras;

    private Phase phase = Phase.DONE;
    private long phaseStartNs;
    private float menuAlpha = 1f;
    private boolean titleWasMouseDown = true;

    /** Rise to title slot while staying large. */
    private static final float LOGO_RISE_MS = 950f;
    /** Then shrink into resting size. */
    private static final float LOGO_SHRINK_MS = 750f;
    /** Then reveal the menu under it. */
    private static final float MENU_IN_MS = 650f;

    public SolarTitleScreen() {
        this(java.util.List.of());
    }

    public SolarTitleScreen(java.util.List<ModCompat.ModButton> modExtras) {
        super(Text.literal("SolarClient"));
        this.modExtras = modExtras;
        if (!IntroPlayback.hasPlayedThisSession()) {
            this.phase = Phase.LOGO_RISE;
            this.phaseStartNs = System.nanoTime();
            this.menuAlpha = 0f;
            IntroPlayback.markSessionHandled();
        }
    }

    @Override
    protected boolean opaqueBackground() {
        return true;
    }

    @Override
    protected boolean showWordmark() {
        return false;
    }

    /**
     * Button column matches resting logo width so left/right edges align,
     * and grows/shrinks with the window (fullscreen vs windowed).
     */
    @Override
    protected int panelWidth() {
        return restingLogoWidth();
    }

    /** Resting logo / panel width — scales with window; keeps aspect of new mark. */
    private int restingLogoWidth() {
        float byW = this.width * 0.52f;
        float byH = this.height * 0.22f * IntroPlayback.LOGO_W / (float) IntroPlayback.LOGO_H;
        int w = Math.round(Math.min(byW, byH));
        int max = Math.min(this.width - 48, 620);
        return Math.max(220, Math.min(w, max));
    }

    private int logoHeightFor(int logoW) {
        return Math.max(1, (int) ((long) logoW * IntroPlayback.LOGO_H / IntroPlayback.LOGO_W));
    }

    private int restingLogoTop(int restH) {
        return Math.max(10, this.height / 5 - restH / 2);
    }

    private int buttonHeight() {
        return Math.max(20, Math.min(28, this.height / 28));
    }

    private int rowStride() {
        int h = buttonHeight();
        return h + Math.max(4, this.height / 90);
    }

    private int logoMenuGap(int restH) {
        return Math.max(16, Math.min(36, this.height / 28));
    }

    @Override
    protected void init() {
        solarButtons.clear();
        int w = panelWidth();
        int btnH = buttonHeight();
        int stride = rowStride();

        this.sideRail = SolarSideRail.standard(this, s -> this.client.setScreen(s));

        int restW = restingLogoWidth();
        int restH = logoHeightFor(restW);
        int restTop = restingLogoTop(restH);
        int y = restTop + restH + logoMenuGap(restH);

        java.util.List<ModCompat.ModButton> column = ModCompat.columnButtons(modExtras);

        SpaceTheme.SolarButton single = new SpaceTheme.SolarButton(panelX(), y, w, btnH, "Singleplayer",
                () -> this.client.setScreen(new SelectWorldScreen(this)));
        single.bold = true;
        solarButtons.add(single);
        y += stride;
        SpaceTheme.SolarButton multi = new SpaceTheme.SolarButton(panelX(), y, w, btnH, "Multiplayer",
                () -> this.client.setScreen(new MultiplayerScreen(this)));
        multi.bold = true;
        solarButtons.add(multi);
        y += stride;

        for (ModCompat.ModButton mb : column) {
            solarButtons.add(ModCompat.fold(mb, panelX(), y, w, btnH));
            y += stride;
        }

        solarButtons.add(new SpaceTheme.SolarButton(leftColX(), y, halfWidth(), btnH, "Options...",
                () -> this.client.setScreen(new OptionsScreen(this, this.client.options))));
        solarButtons.add(new SpaceTheme.SolarButton(rightColX(), y, halfWidth(), btnH, "Solar Menu",
                () -> this.client.setScreen(new SolarMenuScreen(this))));
        y += stride;
        solarButtons.add(new SpaceTheme.SolarButton(panelX(), y, w, btnH, "Quit",
                () -> this.client.scheduleStop()));

        ModCompat.placeFree(modExtras, this.width, this.height);
        for (ModCompat.ModButton mb : modExtras) {
            this.addDrawableChild(mb.widget());
        }
    }

    private float phaseRaw(float durationMs) {
        float ms = (System.nanoTime() - phaseStartNs) / 1_000_000f;
        return IntroPlayback.clamp01(ms / durationMs);
    }

    private void advancePhases() {
        if (phase == Phase.LOGO_RISE && phaseRaw(LOGO_RISE_MS) >= 1f) {
            phase = Phase.LOGO_SHRINK;
            phaseStartNs = System.nanoTime();
        } else if (phase == Phase.LOGO_SHRINK && phaseRaw(LOGO_SHRINK_MS) >= 1f) {
            phase = Phase.MENU_IN;
            phaseStartNs = System.nanoTime();
            menuAlpha = 0f;
        } else if (phase == Phase.MENU_IN && phaseRaw(MENU_IN_MS) >= 1f) {
            phase = Phase.DONE;
            menuAlpha = 1f;
        }
        if (phase == Phase.MENU_IN) {
            menuAlpha = IntroPlayback.easeOut(phaseRaw(MENU_IN_MS));
        }
    }

    private void skipIntro() {
        phase = Phase.DONE;
        menuAlpha = 1f;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        advancePhases();

        IntroPlayback.renderMenuBackground(ctx, this.width, this.height);

        // Start exactly where the MP4 end-card lettering sat (cover-mapped).
        IntroPlayback.EndcardLogoPlacement endcard =
                IntroPlayback.endcardLogoOnScreen(this.width, this.height);
        int startW = endcard.width();
        int startTop = endcard.top();
        int startCx = endcard.cx();

        int restW = restingLogoWidth();
        int restH = logoHeightFor(restW);
        int restTop = restingLogoTop(restH);
        int restCx = this.width / 2;

        int logoW;
        int logoTop;
        int logoCx;
        if (phase == Phase.LOGO_RISE) {
            float t = IntroPlayback.easeInOut(phaseRaw(LOGO_RISE_MS));
            logoW = startW;
            logoTop = Math.round(IntroPlayback.lerp(startTop, restTop, t));
            logoCx = Math.round(IntroPlayback.lerp(startCx, restCx, t));
        } else if (phase == Phase.LOGO_SHRINK) {
            float t = IntroPlayback.easeInOut(phaseRaw(LOGO_SHRINK_MS));
            logoW = Math.round(IntroPlayback.lerp(startW, restW, t));
            logoTop = restTop;
            logoCx = restCx;
        } else {
            logoW = restW;
            logoTop = restTop;
            logoCx = restCx;
        }

        IntroPlayback.drawLogo(ctx, logoCx, logoTop, logoW);

        // Menus stay completely hidden until rise + shrink finish.
        if (phase == Phase.LOGO_RISE || phase == Phase.LOGO_SHRINK) {
            return;
        }

        int slide = phase == Phase.MENU_IN ? Math.round((1f - menuAlpha) * 28f) : 0;
        int my = mouseY - slide;

        boolean mouseDown = GLFW.glfwGetMouseButton(this.client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        justPressed = mouseDown && !titleWasMouseDown;
        titleWasMouseDown = mouseDown;

        // Soft fade via partial transparency isn't available on SolarButton;
        // slide + delayed click gate still reads as a clean entrance.
        java.util.List<SpaceTheme.SolarButton> snapshot = new java.util.ArrayList<>(solarButtons);
        for (SpaceTheme.SolarButton b : snapshot) {
            int ox = b.x, oy = b.y;
            b.y = oy + slide;
            b.render(ctx, this.client, mouseX, my);
            b.y = oy;
            b.x = ox;
        }
        if (justPressed && menuAlpha > 0.55f) {
            for (SpaceTheme.SolarButton b : snapshot) {
                int oy = b.y;
                b.y = oy + slide;
                boolean hit = b.isHovered(mouseX, my);
                b.y = oy;
                if (hit) { b.click(); break; }
            }
        }

        if (sideRail != null) {
            sideRail.layout(this.width, this.height);
            sideRail.render(ctx, mouseX, my);
            if (justPressed && menuAlpha > 0.55f) sideRail.mouseClicked(mouseX, my, 0);
        }
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Handled in render().
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
        int key = keyInput.key();
        if (phase != Phase.DONE && (key == GLFW.GLFW_KEY_ENTER
                || key == GLFW.GLFW_KEY_SPACE
                || key == GLFW.GLFW_KEY_ESCAPE)) {
            skipIntro();
            return true;
        }
        return super.keyPressed(keyInput);
    }
}
