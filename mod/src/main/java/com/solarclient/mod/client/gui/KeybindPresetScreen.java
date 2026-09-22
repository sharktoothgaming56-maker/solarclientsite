package com.solarclient.mod.client.gui;

import com.solarclient.mod.client.config.SolarConfig;
import com.solarclient.mod.client.keybind.KeybindManager;
import com.solarclient.mod.client.keybind.KeybindPreset;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The saved keybind sets ("Presets"). Reached from the Presets button on the
 * Key Binds screen (top-right) or the Keybinds hub.
 *
 * SELECT = APPLY: clicking a preset row immediately swaps every keybinding
 * (vanilla + this mod's) to that set and marks it active — no separate
 * Apply button, exactly per the brief. Each row also has a small ✕ to
 * delete. Everything is stored locally in config/solarclient.json.
 */
public class KeybindPresetScreen extends SpaceTheme.SpaceScreen {
    /** One-shot "Applied X" note handed from the click to the rebuilt screen. */
    private static String appliedFlash;

    private final Screen parent;
    private String appliedNote;
    private int scroll = 0;

    private static final int ROW_TOP = 80;
    private static final int ROW_H = 26;

    public KeybindPresetScreen(Screen parent) {
        super(Text.literal("Key Presets"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        solarButtons.clear();
        if (appliedFlash != null) {
            appliedNote = appliedFlash;
            appliedFlash = null;
        }

        solarButtons.add(new SpaceTheme.SolarButton(panelX(), 46, panelWidth(), 22, "Save Current as New Set",
                () -> this.client.setScreen(new PresetNameScreen(this))));

        SpaceTheme.SolarButton back = new SpaceTheme.SolarButton(
                panelX(), this.height - 32, panelWidth(), 22, "Back",
                () -> this.client.setScreen(parent));
        back.bold = true;
        solarButtons.add(back);

        rebuildRows();
    }

    /** Rebuilds the per-preset row buttons (apply-on-click + delete). */
    private void rebuildRows() {
        while (solarButtons.size() > 2) solarButtons.remove(solarButtons.size() - 1); // keep Save + Back

        SolarConfig cfg = SolarConfig.get();
        List<KeybindPreset> presets = cfg.keybindPresets;
        int listBottom = this.height - 40;

        int y = ROW_TOP - scroll;
        for (KeybindPreset preset : presets) {
            if (y + 22 >= ROW_TOP && y <= listBottom) {
                boolean active = preset.name != null && preset.name.equals(cfg.activePreset);
                // Main row button: chip-width inset on the left, ✕ on the right.
                int rowX = panelX() + 20;
                int rowW = panelWidth() - 20 - 28;
                SpaceTheme.SolarButton apply = new SpaceTheme.SolarButton(rowX, y, rowW, 22,
                        preset.name, () -> {
                    KeybindManager.apply(preset); // select = apply, instantly
                    refreshParentKeybindList();
                    appliedFlash = preset.name;
                    this.client.setScreen(new KeybindPresetScreen(parent));
                });
                apply.bold = active;
                SpaceTheme.SolarButton del = new SpaceTheme.SolarButton(panelX() + panelWidth() - 24, y, 24, 22, "✕", () -> {
                    KeybindManager.delete(preset);
                    this.client.setScreen(new KeybindPresetScreen(parent));
                });
                solarButtons.add(apply);
                solarButtons.add(del);
            }
            y += ROW_H;
        }
    }

    /**
     * The vanilla Key Binds screen keeps its list widget alive across
     * re-inits (that's how it preserves scroll on resize) — so after we
     * swap every bind underneath it, its rows would keep showing the OLD
     * keys and the preset would look like it did nothing. Poking the live
     * ControlsListWidget makes every row re-read its binding right away.
     */
    private void refreshParentKeybindList() {
        if (parent instanceof net.minecraft.client.gui.screen.option.KeybindsScreen ks) {
            for (var child : ks.children()) {
                if (child instanceof net.minecraft.client.gui.screen.option.ControlsListWidget list) {
                    list.update();
                }
            }
        }
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        SolarConfig cfg = SolarConfig.get();
        int accent = 0xFF000000 | (cfg.getStrokeColor() & 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("KEY PRESETS").formatted(Formatting.BOLD),
                this.width / 2, 22, accent);

        List<KeybindPreset> presets = cfg.keybindPresets;
        int listBottom = this.height - 40;

        if (presets.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    "No saved sets yet — set your keys, then \"Save Preset\".", this.width / 2, ROW_TOP + 10, 0xFFA79FC4);
            return;
        }

        if (appliedNote != null) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, "Applied \"" + appliedNote + "\" \u2713", this.width / 2, 68, 0xFF9FD9A8);
        } else {
            ctx.drawCenteredTextWithShadow(this.textRenderer, "Click a set to apply it", this.width / 2, 68, 0xFFA79FC4);
        }

        // Colour chips beside each visible row (chips only; the row itself
        // is a real button drawn by the base class).
        ctx.enableScissor(0, ROW_TOP, this.width, listBottom);
        int y = ROW_TOP - scroll;
        for (KeybindPreset preset : presets) {
            if (y + 22 >= ROW_TOP && y <= listBottom) {
                int chip = 14;
                ctx.fill(panelX(), y + 4, panelX() + chip, y + 4 + chip, 0xFF000000 | (preset.color & 0xFFFFFF));
            }
            y += ROW_H;
        }
        ctx.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int viewport = (this.height - 40) - ROW_TOP;
        int contentHeight = SolarConfig.get().keybindPresets.size() * ROW_H;
        int maxScroll = Math.max(0, contentHeight - viewport);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (verticalAmount * 18)));
        rebuildRows();
        return true;
    }
}
