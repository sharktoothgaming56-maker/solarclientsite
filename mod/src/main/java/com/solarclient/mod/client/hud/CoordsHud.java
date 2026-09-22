package com.solarclient.mod.client.hud;

import com.solarclient.mod.client.config.SolarConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class CoordsHud {
    public static final String ID = "coords";

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!HudStyle.shouldRenderHuds() || !SolarConfig.get().isHudEnabled(ID)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;
        int[] pos = SolarConfig.get().getHudPosition(ID, 6, 20);
        String coords = String.format("XYZ: %.0f, %.0f, %.0f", player.getX(), player.getY(), player.getZ());
        HudStyle.beginScale(ctx, pos[0], pos[1], SolarConfig.get().getHudScale(ID));
        ctx.drawTextWithShadow(client.textRenderer, Text.literal(coords).formatted(Formatting.BOLD), 0, 0, SolarConfig.get().getHudColor(ID, HudStyle.TEXT));
        HudStyle.endScale(ctx);
    }
}
