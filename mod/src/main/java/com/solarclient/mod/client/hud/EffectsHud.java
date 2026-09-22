package com.solarclient.mod.client.hud;

import com.solarclient.mod.client.config.SolarConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Active potion effects in a panel that resizes itself to fit however
 * many effects are active: each row shows the effect's actual icon
 * ("pfp"), then a bold name + amplifier + time remaining. Vanilla's own
 * top-right effect icons are removed at startup so this is the only list.
 */
public class EffectsHud {
    public static final String ID = "effects";
    private static final int ROW_H = 20;
    private static final int ICON = 18;

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!HudStyle.shouldRenderHuds() || !SolarConfig.get().isHudEnabled(ID)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;

        List<StatusEffectInstance> effects = player.getStatusEffects().stream().toList();
        if (effects.isEmpty()) return;

        // Measure first so the panel fits its content exactly.
        int maxTextW = 0;
        Text[] labels = new Text[effects.size()];
        for (int i = 0; i < effects.size(); i++) {
            StatusEffectInstance effect = effects.get(i);
            String name = effect.getEffectType().value().getName().getString();
            String amplifier = effect.getAmplifier() > 0 ? " " + toRoman(effect.getAmplifier() + 1) : "";
            labels[i] = Text.literal(name + amplifier + "  " + formatDuration(effect.getDuration())).formatted(Formatting.BOLD);
            maxTextW = Math.max(maxTextW, client.textRenderer.getWidth(labels[i]));
        }
        int panelW = 8 + ICON + 6 + maxTextW + 8;
        int panelH = 6 + effects.size() * ROW_H + 2;

        float scale = SolarConfig.get().getHudScale(ID);
        int[] pos = SolarConfig.get().getHudPosition(ID, 6, client.getWindow().getScaledHeight() / 2 - (int) (panelH * scale) / 2);

        HudStyle.beginScale(ctx, pos[0], pos[1], scale);
        HudStyle.panel(ctx, 0, 0, panelW, panelH);
        int y = 5;
        for (int i = 0; i < effects.size(); i++) {
            StatusEffectInstance effect = effects.get(i);
            var key = effect.getEffectType().getKey();
            if (key.isPresent()) {
                Identifier effectId = key.get().getValue();
                Identifier sprite = Identifier.of(effectId.getNamespace(), "mob_effect/" + effectId.getPath());
                // NOTE: this is the one flagged-risky line here — the
                // drawGuiTexture signature changed in the 1.21.6 render
                // rework to take a RenderPipeline first. If it doesn't
                // compile, the older form was:
                //   ctx.drawGuiTexture(sprite, 8, y, ICON, ICON);
                ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, 8, y, ICON, ICON);
            }
            ctx.drawTextWithShadow(client.textRenderer, labels[i], 8 + ICON + 6, y + 5, SolarConfig.get().getHudColor(ID, HudStyle.TEXT));
            y += ROW_H;
        }
        HudStyle.endScale(ctx);
    }

    private static String formatDuration(int ticks) {
        int totalSeconds = ticks / 20;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private static String toRoman(int n) {
        String[] romans = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n <= 10 ? romans[n - 1] : String.valueOf(n);
    }
}
