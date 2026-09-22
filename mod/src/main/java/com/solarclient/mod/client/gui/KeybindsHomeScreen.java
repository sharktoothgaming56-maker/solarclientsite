package com.solarclient.mod.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.text.Text;

/**
 * SolarClient's keybinds landing screen — the Mixin-free hub the brief
 * describes. Three buttons:
 *   - LEFT  "Save Current Keybinds": snapshots your current binds → opens a
 *            name + colour screen → saves the whole set to disk.
 *   - RIGHT "Key Presets": opens the list of every saved set; click one to
 *            load/apply it.
 *   - "Edit Key Binds": opens vanilla's real Key Binds screen so you set
 *            keys the normal way, then come back and save them as a set.
 *
 * Reached from the pause menu's "Keybinds" button. Left/right layout mirrors
 * the wording in the brief ("saved keybinds on the left ... key presets is
 * the button beside it on the right").
 */
public class KeybindsHomeScreen extends SpaceTheme.SpaceScreen {
    private final Screen parent;

    public KeybindsHomeScreen(Screen parent) {
        super(Text.literal("Keybinds"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        solarButtons.clear();
        int y = this.height / 2 - 26;

        // Left: save current binds as a set. Right: browse saved sets.
        solarButtons.add(new SpaceTheme.SolarButton(leftColX(), y, halfWidth(), 22, "Save Current Keybinds",
                () -> this.client.setScreen(new PresetNameScreen(this))));
        solarButtons.add(new SpaceTheme.SolarButton(rightColX(), y, halfWidth(), 22, "Key Presets",
                () -> this.client.setScreen(new KeybindPresetScreen(this))));
        y += 30;

        solarButtons.add(new SpaceTheme.SolarButton(panelX(), y, panelWidth(), 22, "Edit Key Binds",
                () -> this.client.setScreen(new KeybindsScreen(this, this.client.options))));

        SpaceTheme.SolarButton back = new SpaceTheme.SolarButton(panelX(), this.height - 32, panelWidth(), 22, "Back",
                () -> this.client.setScreen(parent));
        back.bold = true;
        solarButtons.add(back);
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Supplied KEY BINDS artwork as the heading, with the helper line
        // beneath it.
        int cx = this.width / 2;
        int logoW = (int) (this.width * 0.28f);
        int maxByHeight = (int) (this.height * 0.14f * SolarLogos.KEYBINDS_W / (float) SolarLogos.KEYBINDS_H);
        logoW = Math.max(40, Math.min(logoW, maxByHeight));
        int logoH = (int) ((long) logoW * SolarLogos.KEYBINDS_H / SolarLogos.KEYBINDS_W);
        int logoTop = this.height / 2 - 58 - logoH;
        if (logoTop < 4) logoTop = 4;
        SolarLogos.drawCentered(ctx, SolarLogos.KEYBINDS, SolarLogos.KEYBINDS_W, SolarLogos.KEYBINDS_H, cx, logoTop, logoW);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                "Set your keys, then save them as a named set", cx, this.height / 2 - 44, 0xFFA79FC4);
    }
}
