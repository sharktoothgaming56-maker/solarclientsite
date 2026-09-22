package com.solarclient.mod.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.StatsScreen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen; // NOTE: verify this exact package — try net.minecraft.client.gui.screen.OptionsScreen if it doesn't compile.
import net.minecraft.text.Text;

/**
 * Custom pause menu. Buttons size off the responsive panel helpers so they
 * grow/shrink with the window instead of a fixed 200px, matching the main
 * menu's look. Adds a Keybinds button that jumps straight to the vanilla
 * Key Binds screen (returning here on Done), next to the Solar Menu.
 */
public class SolarGameMenuScreen extends SpaceTheme.SpaceScreen {
    /** Other mods' pause-menu buttons, re-hosted here (see ModCompat). */
    private final java.util.List<ModCompat.ModButton> modExtras;

    /** Top of the button stack, so the logo can sit above it as it grows. */
    private int stackTop;

    /** "Friends" normally; carries a pending-invite count when you have one. */
    private static String partyButtonLabel() {
        com.solarclient.mod.client.social.FriendsState f =
                com.solarclient.mod.client.social.FriendsState.get();
        int invites = f.invites().size();
        if (invites > 0) return "Friends (" + invites + " invite" + (invites > 1 ? "s" : "") + ")";
        return "Friends";
    }

    public SolarGameMenuScreen() {
        this(java.util.List.of());
    }

    public SolarGameMenuScreen(java.util.List<ModCompat.ModButton> modExtras) {
        super(Text.literal("Game Menu"));
        this.modExtras = modExtras;
    }

    @Override
    protected void init() {
        solarButtons.clear();
        this.sideRail = SolarSideRail.standard(this, s -> this.client.setScreen(s));
        int full = panelWidth();
        // Same treatment as the title screen: mod buttons take a row in the
        // stack and the stack grows both ways so it stays centred.
        java.util.List<ModCompat.ModButton> column = ModCompat.columnButtons(modExtras);
        int y = this.height / 2 - 76 - (column.size() * 26) / 2;
        stackTop = y;

        SpaceTheme.SolarButton back = new SpaceTheme.SolarButton(panelX(), y, full, 22, "Back to Game", () -> this.client.setScreen(null));
        back.bold = true;
        solarButtons.add(back);
        y += 26;

        solarButtons.add(new SpaceTheme.SolarButton(leftColX(), y, halfWidth(), 22, "Advancements",
                () -> this.client.setScreen(new AdvancementsScreen(this.client.player.networkHandler.getAdvancementHandler(), this))));
        solarButtons.add(new SpaceTheme.SolarButton(rightColX(), y, halfWidth(), 22, "Statistics",
                () -> this.client.setScreen(new StatsScreen(this, this.client.player.getStatHandler()))));
        y += 26;

        solarButtons.add(new SpaceTheme.SolarButton(leftColX(), y, halfWidth(), 22, "Multiplayer",
                () -> this.client.setScreen(new MultiplayerScreen(this))));
        solarButtons.add(new SpaceTheme.SolarButton(rightColX(), y, halfWidth(), 22, partyButtonLabel(),
                () -> this.client.setScreen(new ChatScreen(this))));
        y += 26;

        solarButtons.add(new SpaceTheme.SolarButton(leftColX(), y, halfWidth(), 22, "Options...",
                () -> this.client.setScreen(new OptionsScreen(this, this.client.options))));
        solarButtons.add(new SpaceTheme.SolarButton(rightColX(), y, halfWidth(), 22, "Solar Menu",
                () -> this.client.setScreen(new SolarMenuScreen(this))));
        y += 26;

        // Keybinds — straight to the vanilla Key Binds page (which now has
        // the Save Preset / Presets buttons in its header).
        solarButtons.add(new SpaceTheme.SolarButton(panelX(), y, full, 22, "Keybinds",
                () -> this.client.setScreen(new net.minecraft.client.gui.screen.option.KeybindsScreen(this, this.client.options))));
        y += 26;

        for (ModCompat.ModButton mb : column) {
            solarButtons.add(ModCompat.fold(mb, panelX(), y, full, 22));
            y += 26;
        }

        solarButtons.add(new SpaceTheme.SolarButton(panelX(), y, full, 22, "Disconnect",
                () -> {
                    // This mirrors what vanilla's own Disconnect button does,
                    // in vanilla's order. The step we were missing is the
                    // FIRST one: closing the world's connection. Without it
                    // the teardown stalls, which is why the progress box sat
                    // there with the world still rendering behind it.
                    //
                    // The argument those disconnect* methods take is named
                    // "disconnectionScreen" in the mappings — it's what shows
                    // WHILE tearing down, not where you end up. Choosing the
                    // destination has always been the caller's job, so we set
                    // it right after, exactly like vanilla.
                    boolean singleplayer = this.client.isInSingleplayer();
                    if (this.client.world != null) {
                        this.client.world.disconnect(Text.translatable("menu.returnToMenu"));
                    }
                    com.solarclient.mod.client.SolarClientModClient.beginDisconnect(singleplayer);
                    if (singleplayer) {
                        this.client.disconnectWithSavingScreen();
                    } else {
                        this.client.disconnectWithProgressScreen();
                    }
                    this.client.setScreen(
                            com.solarclient.mod.client.SolarClientModClient.disconnectDestination(singleplayer));
                }));

        // Icon buttons and side panels keep the spot their mod chose.
        ModCompat.placeFree(modExtras, this.width, this.height);
        for (ModCompat.ModButton mb : modExtras) {
            this.addDrawableChild(mb.widget());
        }
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Supplied GAME MENU logo, centred and placed nicely above the
        // buttons (roughly the same distance the text title used to sit at).
        int cx = this.width / 2;
        int logoW = (int) (this.width * 0.34f);
        int maxByHeight = (int) (this.height * 0.16f * SolarLogos.GAME_MENU_W / (float) SolarLogos.GAME_MENU_H);
        logoW = Math.max(40, Math.min(logoW, maxByHeight));
        int logoH = (int) ((long) logoW * SolarLogos.GAME_MENU_H / SolarLogos.GAME_MENU_W);
        int logoTop = stackTop - logoH - 6; // sit just above the "Back to Game" button, following the stack
        if (logoTop < 4) logoTop = 4;
        SolarLogos.drawCentered(ctx, SolarLogos.GAME_MENU, SolarLogos.GAME_MENU_W, SolarLogos.GAME_MENU_H, cx, logoTop, logoW);
    }
}
