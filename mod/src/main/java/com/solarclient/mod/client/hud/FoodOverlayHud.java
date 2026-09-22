package com.solarclient.mod.client.hud;

import com.solarclient.mod.client.config.SolarConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Draws on top of the vanilla hunger bar to answer two questions at a
 * glance: how much of your hunger is still protected by saturation, and
 * what the food in your hand would actually do for you.
 *
 * Three states, all on the same ten icons:
 *
 *   1. SATURATION YOU HAVE — a steady yellow outline on every hunger icon
 *      currently covered by saturation, drawn individually per icon.
 *      Steady, because it is a fact about right now, not a preview.
 *
 *   2. HUNGER YOU WOULD GAIN — while holding food, the icons that eating it
 *      would fill fade in and out as full drumsticks in the empty slots.
 *      Nothing is drawn over hunger you already have, so the preview only
 *      ever shows the difference.
 *
 *   3. SATURATION YOU WOULD GAIN — those same new icons get an outline that
 *      fades in sync with the drumstick preview rather than hard blinking.
 *
 * Positioning mirrors InGameHud.renderStatusBars exactly: the row sits at
 * scaledHeight - 39, the right edge at scaledWidth / 2 + 91, and icon m
 * (counting right to left, the direction the bar fills) is at
 * right - m * 8 - 9 with a 9x9 footprint.
 */
public class FoodOverlayHud {
    public static final String ID = "food_overlay";

    private static final int ICON = 9;
    private static final int ICON_STEP = 8;   // icons overlap by a pixel, same as vanilla
    private static final int ICONS = 10;

    /** One full fade cycle. Deliberately slow — this sits next to the hotbar. */
    private static final long FADE_MS = 1150L;
    /** Alpha range the preview breathes across, out of 255. */
    private static final int FADE_ALPHA_MIN = 70;
    private static final int FADE_ALPHA_MAX = 235;

    private static final int DEFAULT_YELLOW = 0xFFFFD84A;

    private static final Identifier FOOD_FULL = Identifier.of("minecraft", "hud/food_full");
    private static final Identifier FOOD_HALF = Identifier.of("minecraft", "hud/food_half");
    private static final Identifier FOOD_FULL_HUNGER = Identifier.of("minecraft", "hud/food_full_hunger");
    private static final Identifier FOOD_HALF_HUNGER = Identifier.of("minecraft", "hud/food_half_hunger");

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!HudStyle.shouldRenderHuds() || !SolarConfig.get().isHudEnabled(ID)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // Only draw where vanilla draws a hunger bar in the first place:
        // creative and spectator have no status bars, and riding a living
        // mount replaces the food row with the mount's health.
        if (player.isCreative() || player.isSpectator()) return;
        if (player.getVehicle() instanceof LivingEntity) return;

        int foodNow = player.getHungerManager().getFoodLevel();
        float satNow = player.getHungerManager().getSaturationLevel();

        // What the held food would do. Main hand wins, then off hand — the
        // same order the player would actually eat in.
        FoodComponent food = foodIn(player.getMainHandStack());
        if (food == null) food = foodIn(player.getOffHandStack());
        // Mid-bite, the item is the active one; keep showing its preview
        // even if the hand somehow reports empty.
        if (food == null && player.isUsingItem()) food = foodIn(player.getActiveItem());

        int foodNew = foodNow;
        float satNew = satNow;
        if (food != null) {
            // Mirrors HungerManager.add(): hunger caps at 20, and saturation
            // can never exceed the hunger level it is sitting on top of.
            foodNew = Math.min(20, foodNow + food.nutrition());
            satNew = Math.min(satNow + food.saturation(), (float) foodNew);
        }

        long now = System.currentTimeMillis();
        double phase = (now % FADE_MS) / (double) FADE_MS;
        // Smooth 0..1 ramp so the preview breathes in and out rather than
        // snapping on and off.
        float pulse = (float) (0.5 - 0.5 * Math.cos(phase * Math.PI * 2.0));
        int fadeAlpha = FADE_ALPHA_MIN + Math.round((FADE_ALPHA_MAX - FADE_ALPHA_MIN) * pulse);

        int right = client.getWindow().getScaledWidth() / 2 + 91;
        int rowY = client.getWindow().getScaledHeight() - 39;

        int yellow = SolarConfig.get().getHudColor(ID, DEFAULT_YELLOW);
        boolean hungerEffect = player.hasStatusEffect(StatusEffects.HUNGER);

        for (int m = 0; m < ICONS; m++) {
            int x = right - m * ICON_STEP - ICON;
            // Icon m covers hunger points (2m, 2m+2].
            int lo = m * 2;

            // ---- 2. hunger this food would add -------------------------
            //
            // Compare the icon's DRAWN STATE before and after rather than
            // comparing raw hunger numbers to the icon's start point. An
            // earlier version did the latter and silently showed nothing
            // whenever hunger was an odd number, because a half-full icon
            // completing to full never crosses its own start point — and
            // odd hunger is half the time.
            int stateNow = iconState(foodNow, m);
            int stateAfter = iconState(foodNew, m);
            if (food != null && stateAfter > stateNow) {
                Identifier sprite = stateAfter == 1
                        ? (hungerEffect ? FOOD_HALF_HUNGER : FOOD_HALF)
                        : (hungerEffect ? FOOD_FULL_HUNGER : FOOD_FULL);
                // NOTE: same signature the Effects HUD uses. If the render
                // pipeline argument ever disappears again the older form was
                //   ctx.drawGuiTexture(sprite, x, rowY, ICON, ICON);
                ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x, rowY, ICON, ICON,
                        withAlpha(0xFFFFFFFF, fadeAlpha));
            }

            // ---- 1 & 3. saturation outline, hugging the drumstick shape --
            // The outline traces the food icon itself, not a box around it:
            // we stamp the icon's own sprite in yellow at four 1px offsets so
            // a thin yellow edge follows the drumstick silhouette, then let
            // vanilla's icon sit on top in the middle. Each icon is outlined
            // separately, so a partly-saturated bar shows exactly which
            // drumsticks are covered.
            boolean saturatedNow = satNow > lo;
            boolean saturatedAfter = satNew > lo;

            int stateForShape = iconState(foodNow, m);
            Identifier shape = stateForShape == 1
                    ? (hungerEffect ? FOOD_HALF_HUNGER : FOOD_HALF)
                    : (hungerEffect ? FOOD_FULL_HUNGER : FOOD_FULL);
            if (saturatedNow) {
                spriteOutline(ctx, shape, x, rowY, withAlpha(yellow, FADE_ALPHA_MAX));
            } else if (food != null && saturatedAfter) {
                // Preview: outline the sprite the icon WOULD show once eaten.
                Identifier gainShape = iconState(foodNew, m) == 1
                        ? (hungerEffect ? FOOD_HALF_HUNGER : FOOD_HALF)
                        : (hungerEffect ? FOOD_FULL_HUNGER : FOOD_FULL);
                spriteOutline(ctx, gainShape, x, rowY, withAlpha(yellow, fadeAlpha));
            }
        }
    }

    /**
     * Draw {@code sprite} tinted {@code color} at four 1px offsets around
     * (x,y), producing a 1px outline that follows the sprite's own shape.
     * Vanilla's real icon is drawn separately (underneath this overlay), so
     * only the yellow fringe pokes out around the edges.
     */
    private static void spriteOutline(DrawContext ctx, Identifier sprite, int x, int y, int color) {
        ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x - 1, y, ICON, ICON, color);
        ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x + 1, y, ICON, ICON, color);
        ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x, y - 1, ICON, ICON, color);
        ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x, y + 1, ICON, ICON, color);
    }

    /** 0 empty, 1 half, 2 full — the same rule InGameHud uses to fill a slot. */
    private static int iconState(int foodLevel, int m) {
        if (foodLevel >= m * 2 + 2) return 2;
        if (foodLevel == m * 2 + 1) return 1;
        return 0;
    }

    private static FoodComponent foodIn(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.get(DataComponentTypes.FOOD);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }
}
