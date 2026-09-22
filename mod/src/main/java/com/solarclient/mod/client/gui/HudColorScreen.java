package com.solarclient.mod.client.gui;

import com.solarclient.mod.client.config.SolarConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * A dedicated colour picker for one HUD. Reached from the HUD editor's
 * per-box colour button. Clicking a swatch sets that HUD's colour in config
 * immediately and shows a live preview line rendered in the chosen colour —
 * so what you pick is exactly what the HUD will look like (fixes the old
 * "cycled to a near-white shade and looked wrong" behaviour).
 */
public class HudColorScreen extends SpaceTheme.SpaceScreen {
    private final Screen parent;
    private final String hudId;
    private final String hudLabel;

    public HudColorScreen(Screen parent, String hudId, String hudLabel) {
        super(Text.literal("HUD Colour"));
        this.parent = parent;
        this.hudId = hudId;
        this.hudLabel = hudLabel;
    }

    @Override
    protected void init() {
        solarButtons.clear();
        SpaceTheme.SolarButton back = new SpaceTheme.SolarButton(
                panelX(), this.height - 32, panelWidth(), 22, "Done",
                () -> this.client.setScreen(parent));
        back.bold = true;
        solarButtons.add(back);
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        SolarConfig cfg = SolarConfig.get();
        int current = cfg.getHudColor(hudId, HudColorDefault());
        int accent = 0xFF000000 | (cfg.getStrokeColor() & 0xFFFFFF);

        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(hudLabel + " COLOUR").formatted(Formatting.BOLD),
                this.width / 2, 22, accent);

        // Live preview in the currently-selected colour.
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Preview: " + hudLabel).formatted(Formatting.BOLD),
                this.width / 2, 44, current);

        int chip = 28, gap = 12;
        int perRow = Math.max(4, Math.min(SolarColors.PALETTE.size(), (this.width - 40) / (chip + gap)));
        int gridW = perRow * chip + (perRow - 1) * gap;
        int startX = (this.width - gridW) / 2;
        int startY = 70;

        for (int i = 0; i < SolarColors.PALETTE.size(); i++) {
            SolarColors.Swatch sw = SolarColors.PALETTE.get(i);
            int col = i % perRow, row = i / perRow;
            int cx = startX + col * (chip + gap);
            int cy = startY + row * (chip + gap + 12);

            boolean selected = sw.argb() == current;
            boolean hover = mouseX >= cx && mouseX <= cx + chip && mouseY >= cy && mouseY <= cy + chip;
            if (selected || hover) {
                ctx.fill(cx - 2, cy - 2, cx + chip + 2, cy + chip + 2, selected ? 0xFFFFFFFF : 0x80FFFFFF);
            }
            ctx.fill(cx, cy, cx + chip, cy + chip, sw.argb());
            ctx.drawCenteredTextWithShadow(this.textRenderer, sw.name(), cx + chip / 2, cy + chip + 2, 0xFFCCCCCC);

            if (justPressed && hover) {
                cfg.setHudColor(hudId, sw.argb());
            }
        }
    }

    private int HudColorDefault() {
        return HudStyleTextColor();
    }

    private static int HudStyleTextColor() {
        return com.solarclient.mod.client.hud.HudStyle.TEXT;
    }
}
