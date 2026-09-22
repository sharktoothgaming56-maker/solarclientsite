package com.solarclient.mod.client.hud;

import com.solarclient.mod.client.config.SolarConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Vertical armor stack, matching the Lunar reference screenshot: bold
 * durability number on the left, item icon on the right, one row per
 * piece — helmet/chest/legs/boots, then mainhand and offhand (with a
 * stack count when more than 1). No panel box. Resizable like every HUD.
 */
public class ArmorHud {
    public static final String ID = "armor";
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final int ROW_H = 20;
    private static final int NUM_COL_W = 34; // right-aligned number column, icon sits after it

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!HudStyle.shouldRenderHuds() || !SolarConfig.get().isHudEnabled(ID)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;
        float scale = SolarConfig.get().getHudScale(ID);

        int[] pos = SolarConfig.get().getHudPosition(
                ID,
                client.getWindow().getScaledWidth() - (int) (60 * scale) - 6,
                client.getWindow().getScaledHeight() / 2 - (int) (60 * scale)
        );

        HudStyle.beginScale(ctx, pos[0], pos[1], scale);
        int y = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            drawRow(ctx, client, player.getEquippedStack(slot), y);
            y += ROW_H;
        }
        drawRow(ctx, client, player.getMainHandStack(), y);
        y += ROW_H;
        drawRow(ctx, client, player.getOffHandStack(), y);
        HudStyle.endScale(ctx);
    }

    private static void drawRow(DrawContext ctx, MinecraftClient client, ItemStack stack, int y) {
        if (stack.isEmpty()) return;
        int iconX = NUM_COL_W + 4;

        // Enchanted pieces used to get a purple box drawn round them. It was
        // redundant — drawItem already renders the enchantment glint on the
        // icon itself — and the boxes made the whole column look cluttered.
        ctx.drawItem(stack, iconX, y);

        if (stack.isDamageable()) {
            int remaining = stack.getMaxDamage() - stack.getDamage();
            float frac = (float) remaining / stack.getMaxDamage();
            int color = frac > 0.5f ? 0xFF5EEB8F : (frac > 0.2f ? 0xFFE3AC45 : 0xFFE0625A);
            Text num = Text.literal(String.valueOf(remaining)).formatted(Formatting.BOLD);
            int tx = NUM_COL_W - client.textRenderer.getWidth(num); // right-aligned in the number column
            ctx.drawTextWithShadow(client.textRenderer, num, tx, y + 4, color);
        } else if (stack.getCount() > 1) {
            Text num = Text.literal("x" + stack.getCount()).formatted(Formatting.BOLD);
            int tx = NUM_COL_W - client.textRenderer.getWidth(num);
            ctx.drawTextWithShadow(client.textRenderer, num, tx, y + 4, HudStyle.TEXT);
        }
    }
}
