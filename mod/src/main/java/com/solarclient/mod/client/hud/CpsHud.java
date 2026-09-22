package com.solarclient.mod.client.hud;

import com.solarclient.mod.client.config.SolarConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

/**
 * Clicks-per-second, Lunar-style "L | R". Counts raw GLFW button-down
 * edges in a rolling one-second window — same GLFW polling technique the
 * HUD editor already uses, so no Minecraft input APIs to get wrong. The
 * render callback runs every frame, which is plenty fast to catch clicks.
 */
public class CpsHud {
    public static final String ID = "cps";
    private static final ArrayDeque<Long> leftClicks = new ArrayDeque<>();
    private static final ArrayDeque<Long> rightClicks = new ArrayDeque<>();
    private static boolean leftWasDown = false;
    private static boolean rightWasDown = false;

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Track clicks every frame regardless of visibility, so the counter
        // is already accurate the moment the HUD is toggled on.
        long now = System.currentTimeMillis();
        long handle = client.getWindow().getHandle();
        boolean leftDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rightDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (leftDown && !leftWasDown) leftClicks.addLast(now);
        if (rightDown && !rightWasDown) rightClicks.addLast(now);
        leftWasDown = leftDown;
        rightWasDown = rightDown;
        while (!leftClicks.isEmpty() && now - leftClicks.peekFirst() > 1000) leftClicks.removeFirst();
        while (!rightClicks.isEmpty() && now - rightClicks.peekFirst() > 1000) rightClicks.removeFirst();

        if (!HudStyle.shouldRenderHuds() || !SolarConfig.get().isHudEnabled(ID)) return;

        int[] pos = SolarConfig.get().getHudPosition(ID, 6, 48);
        HudStyle.beginScale(ctx, pos[0], pos[1], SolarConfig.get().getHudScale(ID));
        ctx.drawTextWithShadow(client.textRenderer,
                Text.literal("CPS: " + leftClicks.size() + " | " + rightClicks.size()).formatted(Formatting.BOLD),
                0, 0, SolarConfig.get().getHudColor(ID, HudStyle.TEXT));
        HudStyle.endScale(ctx);
    }
}
