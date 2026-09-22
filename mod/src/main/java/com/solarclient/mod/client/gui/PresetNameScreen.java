package com.solarclient.mod.client.gui;

import com.solarclient.mod.client.keybind.KeybindManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Name + colour picker for a new keybind set. Mirrors WaypointNameScreen
 * (a known-good pattern in this project): a vanilla TextFieldWidget added
 * via addDrawableChild so vanilla routes typing/clicks to it, plus a swatch
 * grid for the colour tag. On save it snapshots the CURRENT keybinds into
 * the new set and persists everything to disk, then shows the list of saved
 * sets — matching the brief's "save the whole keybind set on their PC".
 */
public class PresetNameScreen extends SpaceTheme.SpaceScreen {
    private final Screen parent;
    private TextFieldWidget nameField;
    private int selectedColor = com.solarclient.mod.client.config.SolarConfig.DEFAULT_ACCENT;

    public PresetNameScreen(Screen parent) {
        super(Text.literal("Name Keybind Set"));
        this.parent = parent;
    }

    private String defaultName() {
        return "Set " + (com.solarclient.mod.client.config.SolarConfig.get().keybindPresets.size() + 1);
    }

    @Override
    protected void init() {
        solarButtons.clear();

        nameField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 70, 200, 20, Text.literal("Set name"));
        nameField.setText(defaultName());
        this.addDrawableChild(nameField);
        this.setInitialFocus(nameField);

        SpaceTheme.SolarButton save = new SpaceTheme.SolarButton(this.width / 2 - 100, 172, 98, 22, "Save", null);
        save.bold = true;
        save.setAction(() -> {
            String name = nameField.getText().isBlank() ? defaultName() : nameField.getText().trim();
            KeybindManager.createFromCurrent(name, selectedColor); // snapshots ALL current binds + persists
            this.client.setScreen(new KeybindPresetScreen(parent));
        });
        solarButtons.add(save);
        solarButtons.add(new SpaceTheme.SolarButton(this.width / 2 + 2, 172, 98, 22, "Cancel",
                () -> this.client.setScreen(parent)));
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int accent = 0xFF000000 | (com.solarclient.mod.client.config.SolarConfig.get().getStrokeColor() & 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("NAME KEYBIND SET").formatted(Formatting.BOLD),
                this.width / 2, 40, accent);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Saves ALL your current key binds under this name", this.width / 2, 100, 0xFFA79FC4);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Colour", this.width / 2, 118, 0xFFA79FC4);

        // Swatch row (wraps if the window is narrow).
        int chip = 16, gap = 8;
        int perRow = Math.max(4, Math.min(SolarColors.PALETTE.size(), (this.width - 40) / (chip + gap)));
        int gridW = perRow * chip + (perRow - 1) * gap;
        int startX = (this.width - gridW) / 2;
        int startY = 132;
        for (int i = 0; i < SolarColors.PALETTE.size(); i++) {
            SolarColors.Swatch sw = SolarColors.PALETTE.get(i);
            int col = i % perRow, row = i / perRow;
            int cx = startX + col * (chip + gap);
            int cy = startY + row * (chip + gap);
            boolean selected = sw.argb() == selectedColor;
            boolean hover = mouseX >= cx && mouseX <= cx + chip && mouseY >= cy && mouseY <= cy + chip;
            if (selected || hover) {
                ctx.fill(cx - 2, cy - 2, cx + chip + 2, cy + chip + 2, selected ? 0xFFFFFFFF : 0x80FFFFFF);
            }
            ctx.fill(cx, cy, cx + chip, cy + chip, sw.argb());
            if (justPressed && hover) selectedColor = sw.argb();
        }
    }
}
