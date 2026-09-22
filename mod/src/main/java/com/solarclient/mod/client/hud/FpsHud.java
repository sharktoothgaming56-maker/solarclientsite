package com.solarclient.mod.client.hud;

import com.solarclient.mod.client.config.SolarConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class FpsHud {
    public static final String ID = "fps";

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!HudStyle.shouldRenderHuds() || !SolarConfig.get().isHudEnabled(ID)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        int[] pos = SolarConfig.get().getHudPosition(ID, 6, 6);
        HudStyle.beginScale(ctx, pos[0], pos[1], SolarConfig.get().getHudScale(ID));
        ctx.drawTextWithShadow(client.textRenderer, Text.literal("FPS: " + client.getCurrentFps()).formatted(Formatting.BOLD), 0, 0, SolarConfig.get().getHudColor(ID, HudStyle.TEXT));
        HudStyle.endScale(ctx);
    }
}
