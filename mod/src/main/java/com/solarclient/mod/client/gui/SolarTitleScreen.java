package com.solarclient.mod.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Custom main menu. On the first open of a session (right after the intro
 * video), the SOLAR CLIENT lettering from the end card eases up to the
 * usual title position, scales down, then the menu buttons fade/slide in
 * under it — keeping the intro's space background the whole time.
 */
public class SolarTitleScreen extends SpaceTheme.SpaceScreen {
    private enum Phase { LOGO_MOVE, MENU_IN, DONE }

    private final java.util.List<ModCompat.ModButton> modExtras;

    private Phase phase = Phase.DONE;
    private long phaseStartMs;
    private float menuAlpha = 1f;
    private boolean titleWasMouseDown = true;

    private static final float LOGO_MOVE_MS = 1100f;
    private static final float MENU_IN_MS = 700f;

    public SolarTitleScreen() {
        this(java.util.List.of());
    }

    public SolarTitleScreen(java.util.List<ModCompat.ModButton> modExtras) {
        super(Text.literal("SolarClient"));
        this.modExtras = modExtras;
        if (!IntroPlayback.hasPlayedThisSession()) {
            this.phase = Phase.LOGO_MOVE;
            this.phaseStartMs = System.currentTimeMillis();
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

    @Override
    protected void init() {
        solarButtons.clear();
        int w = panelWidth();

        this.sideRail = SolarSideRail.standard(this, s -> this.client.setScreen(s));

        java.util.List<ModCompat.ModButton> column = ModCompat.columnButtons(modExtras);
        int y = this.height / 2 - 25 - (column.size() * 26) / 2;

        SpaceTheme.SolarButton single = new SpaceTheme.SolarButton(panelX(), y, w, 22, "Singleplayer",
                () -> this.client.setScreen(new SelectWorldScreen(this)));
        single.bold = true;
        solarButtons.add(single);
        y += 26;
        SpaceTheme.SolarButton multi = new SpaceTheme.SolarButton(panelX(), y, w, 22, "Multiplayer",
                () -> this.client.setScreen(new MultiplayerScreen(this)));
        multi.bold = true;
        solarButtons.add(multi);
        y += 26;

        for (ModCompat.ModButton mb : column) {
            solarButtons.add(ModCompat.fold(mb, panelX(), y, w, 22));
            y += 26;
        }

        solarButtons.add(new SpaceTheme.SolarButton(leftColX(), y, halfWidth(), 22, "Options...",
                () -> this.client.setScreen(new OptionsScreen(this, this.client.options))));
        solarButtons.add(new SpaceTheme.SolarButton(rightColX(), y, halfWidth(), 22, "Solar Menu",
                () -> this.client.setScreen(new SolarMenuScreen(this))));
        y += 26;
        solarButtons.add(new SpaceTheme.SolarButton(panelX(), y, w, 22, "Quit",
                () -> this.client.scheduleStop()));

        ModCompat.placeFree(modExtras, this.width, this.height);
        for (ModCompat.ModButton mb : modExtras) {
            this.addDrawableChild(mb.widget());
        }
    }

    private float phaseT(float durationMs) {
        return IntroPlayback.easeInOut((System.currentTimeMillis() - phaseStartMs) / durationMs);
    }

    private void advancePhases() {
        if (phase == Phase.LOGO_MOVE && System.currentTimeMillis() - phaseStartMs >= LOGO_MOVE_MS) {
            phase = Phase.MENU_IN;
            phaseStartMs = System.currentTimeMillis();
        } else if (phase == Phase.MENU_IN && System.currentTimeMillis() - phaseStartMs >= MENU_IN_MS) {
            phase = Phase.DONE;
            menuAlpha = 1f;
        }
        if (phase == Phase.MENU_IN) {
            menuAlpha = phaseT(MENU_IN_MS);
        }
    }

    private int restingLogoWidth() {
        int logoW = (int) (this.width * 0.42f);
        int maxByHeight = (int) (this.height * 0.24f * IntroPlayback.LOGO_W / (float) IntroPlayback.LOGO_H);
        return Math.max(40, Math.min(logoW, maxByHeight));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        advancePhases();

        IntroPlayback.renderMenuBackground(ctx, this.width, this.height);

        int cx = this.width / 2;
        int restW = restingLogoWidth();
        int restH = Math.max(1, (int) ((long) restW * IntroPlayback.LOGO_H / IntroPlayback.LOGO_W));
        int restTop = this.height / 4 - restH / 2;

        int startW = Math.min(this.width - 40, Math.max(restW + 40, (int) (this.width * 0.72f)));
        int startH = Math.max(1, (int) ((long) startW * IntroPlayback.LOGO_H / IntroPlayback.LOGO_W));
        int startTop = this.height / 2 - startH / 2;

        float logoT = phase == Phase.LOGO_MOVE ? phaseT(LOGO_MOVE_MS) : 1f;
        int logoW = Math.round(startW + (restW - startW) * logoT);
        IntroPlayback.drawLogo(ctx, cx,
                Math.round(startTop + (restTop - startTop) * logoT),
                logoW);

        if (phase == Phase.LOGO_MOVE) {
            return; // logo only — menus come in after
        }

        int slide = phase == Phase.MENU_IN ? Math.round((1f - menuAlpha) * 22f) : 0;
        int my = mouseY - slide;

        boolean mouseDown = GLFW.glfwGetMouseButton(this.client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        justPressed = mouseDown && !titleWasMouseDown;
        titleWasMouseDown = mouseDown;

        java.util.List<SpaceTheme.SolarButton> snapshot = new java.util.ArrayList<>(solarButtons);
        for (SpaceTheme.SolarButton b : snapshot) {
            int ox = b.x, oy = b.y;
            b.y = oy + slide;
            b.render(ctx, this.client, mouseX, my);
            b.y = oy;
            b.x = ox;
        }
        if (justPressed && menuAlpha > 0.5f) {
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
            if (justPressed && menuAlpha > 0.5f) sideRail.mouseClicked(mouseX, my, 0);
        }

        // Mod Menu / Iris extras are already folded into solarButtons via ModCompat.
        // Screen.drawables is private in 1.21.11 — do not walk it here.
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
            phase = Phase.DONE;
            menuAlpha = 1f;
            return true;
        }
        return super.keyPressed(keyInput);
    }
}
