package com.solarclient.mod.client.gui;

import com.solarclient.mod.client.config.SolarConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Colour customisation for the menu buttons: two independent pickers —
 *  - BUTTON: the fill/body tint of every SolarButton
 *  - STROKE: the border/rounding colour around every SolarButton
 * Clicking a swatch writes it to config immediately, so the buttons on this
 * very screen recolour live as the preview.
 */
public class ButtonColorScreen extends SpaceTheme.SpaceScreen {
    private final Screen parent;

    public ButtonColorScreen(Screen parent) {
        super(Text.literal("UI Buttons Colors"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        solarButtons.clear();
        SpaceTheme.SolarButton back = new SpaceTheme.SolarButton(
                panelX(), this.height - 32, panelWidth(), 22, "Back",
                () -> this.client.setScreen(parent));
        back.bold = true;
        solarButtons.add(back);
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        SolarConfig cfg = SolarConfig.get();
        int accent = 0xFF000000 | (cfg.getStrokeColor() & 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("UI BUTTONS COLORS").formatted(Formatting.BOLD),
                this.width / 2, 20, accent);

        // Two swatch rows: Button (fill) then Stroke (border).
        int firstY = swatchGrid(ctx, "Button colour (fill)", 42, cfg.getButtonColor(), mouseX, mouseY, true);
        swatchGrid(ctx, "Stroke colour (border)", firstY + 14, cfg.getStrokeColor(), mouseX, mouseY, false);
    }

    /**
     * Draws a labelled swatch grid; returns the y just below it. When a
     * swatch is clicked, sets either the button-fill (isButton) or the
     * stroke colour.
     */
    private int swatchGrid(DrawContext ctx, String label, int labelY, int current, int mouseX, int mouseY, boolean isButton) {
        ctx.drawCenteredTextWithShadow(this.textRenderer, label, this.width / 2, labelY, 0xFFA79FC4);

        int chip = 26, gap = 10;
        int perRow = Math.max(4, Math.min(SolarColors.PALETTE.size(), (this.width - 40) / (chip + gap)));
        int gridW = perRow * chip + (perRow - 1) * gap;
        int startX = (this.width - gridW) / 2;
        int startY = labelY + 14;

        int lastRowBottom = startY;
        for (int i = 0; i < SolarColors.PALETTE.size(); i++) {
            SolarColors.Swatch sw = SolarColors.PALETTE.get(i);
            int col = i % perRow, row = i / perRow;
            int cx = startX + col * (chip + gap);
            int cy = startY + row * (chip + gap);
            lastRowBottom = Math.max(lastRowBottom, cy + chip);

            boolean selected = sw.argb() == current;
            boolean hover = mouseX >= cx && mouseX <= cx + chip && mouseY >= cy && mouseY <= cy + chip;
            if (selected || hover) {
                ctx.fill(cx - 2, cy - 2, cx + chip + 2, cy + chip + 2, selected ? 0xFFFFFFFF : 0x80FFFFFF);
            }
            ctx.fill(cx, cy, cx + chip, cy + chip, sw.argb());

            if (justPressed && hover) {
                if (isButton) cfg().setButtonColor(sw.argb());
                else cfg().setStrokeColor(sw.argb());
            }
        }
        return lastRowBottom;
    }

    private static SolarConfig cfg() { return SolarConfig.get(); }
}
