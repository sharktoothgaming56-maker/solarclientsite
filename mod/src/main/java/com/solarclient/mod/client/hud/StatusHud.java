package com.solarclient.mod.client.hud;

import com.solarclient.mod.client.config.SolarConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows what the player is currently doing — flying, sneaking, sprinting.
 * Plain floating text with a shadow, no background panel.
 *
 * ROOT CAUSE of the earlier backwards/broken labels: KeyBinding.isPressed()
 * returns true for a toggled-ON key even when nothing is physically held —
 * that's literally how toggle mode works internally — so it can never
 * distinguish held from toggled. The real differentiator is the player's
 * accessibility SETTING: if sneak/sprint is set to toggle mode, an active
 * state is "(toggled)"; in hold mode it's "(held)".
 */
public class StatusHud {
    public static final String ID = "status";

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!HudStyle.shouldRenderHuds() || !SolarConfig.get().isHudEnabled(ID)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;

        // NOTE: if these two getters don't compile, search GameOptions for
        // "Toggled" — the sneak/sprint toggle-mode options exist, only the
        // getter naming has shifted between mappings versions.
        boolean sneakToggleMode = client.options.getSneakToggled().getValue();
        boolean sprintToggleMode = client.options.getSprintToggled().getValue();

        List<String> lines = new ArrayList<>();
        if (player.getAbilities().flying) lines.add("Flying (toggled)");
        if (player.isSneaking()) lines.add("Sneaking" + (sneakToggleMode ? " (toggled)" : " (held)"));
        if (player.isSprinting()) lines.add("Sprinting" + (sprintToggleMode ? " (toggled)" : " (held)"));
        if (lines.isEmpty()) return;

        int width = 120;
        float scale = SolarConfig.get().getHudScale(ID);
        int[] pos = SolarConfig.get().getHudPosition(ID, client.getWindow().getScaledWidth() - (int) (width * scale) - 6, 6);

        int statusColor = SolarConfig.get().getHudColor(ID, HudStyle.TEXT);
        HudStyle.beginScale(ctx, pos[0], pos[1], scale);
        for (int i = 0; i < lines.size(); i++) {
            var text = net.minecraft.text.Text.literal(lines.get(i)).formatted(net.minecraft.util.Formatting.BOLD);
            ctx.drawTextWithShadow(client.textRenderer, text, width - client.textRenderer.getWidth(text), i * 11, statusColor);
        }
        HudStyle.endScale(ctx);
    }
}
