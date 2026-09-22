package com.solarclient.mod.client.hud;

import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.function.BiFunction;

/** One entry per HUD, so the editor/toggle screens don't need to hardcode each one by hand. */
public class HudInfo {
    public final String id;
    public final String label;
    public final int width;
    public final int height;
    public final BiFunction<MinecraftClient, int[], int[]> defaultPos; // (client, [w,h]) -> [x, y]

    public HudInfo(String id, String label, int width, int height, BiFunction<MinecraftClient, int[], int[]> defaultPos) {
        this.id = id;
        this.label = label;
        this.width = width;
        this.height = height;
        this.defaultPos = defaultPos;
    }

    public static final List<HudInfo> ALL = List.of(
            new HudInfo(FpsHud.ID, "FPS", 60, 12, (c, wh) -> new int[]{6, 6}),
            new HudInfo(CoordsHud.ID, "Coordinates", 130, 12, (c, wh) -> new int[]{6, 20}),
            new HudInfo(DirectionHud.ID, "Direction", 80, 12, (c, wh) -> new int[]{6, 34}),
            new HudInfo(CpsHud.ID, "CPS", 80, 12, (c, wh) -> new int[]{6, 48}),
            new HudInfo(StatusHud.ID, "Status", 120, 34, (c, wh) -> new int[]{c.getWindow().getScaledWidth() - wh[0] - 6, 6}),
            new HudInfo(ArmorHud.ID, "Armor & Held Items", 60, 120, (c, wh) -> new int[]{c.getWindow().getScaledWidth() - wh[0] - 6, c.getWindow().getScaledHeight() / 2 - wh[1] / 2}),
            new HudInfo(EffectsHud.ID, "Potion Effects", 150, 46, (c, wh) -> new int[]{6, c.getWindow().getScaledHeight() / 2 - wh[1] / 2}),
            // Only ever draws when you are actually in a party, so it can
            // sit under the Status HUD without crowding a solo player.
            new HudInfo(PartyHud.ID, "Party", 116, 60, (c, wh) -> new int[]{c.getWindow().getScaledWidth() - wh[0] - 6, 60}),
            // The full-height rail. Edge-anchored rather than freely
            // positioned, so the editor entry exists to TOGGLE it — the
            // position it reports is where it always draws.
            new HudInfo(PartySidePanel.ID, "Party Side Panel", 132, 200,
                    (c, wh) -> new int[]{c.getWindow().getScaledWidth() - wh[0] - 4, 4}),
            // Annotates the vanilla hunger bar in place, so it is pinned to
            // wherever that bar is. The editor entry exists to TOGGLE it and
            // to pick the highlight colour; the reported position is simply
            // where the vanilla bar lives.
            new HudInfo(FoodOverlayHud.ID, "Food & Saturation", 81, 9,
                    (c, wh) -> new int[]{c.getWindow().getScaledWidth() / 2 + 91 - 81,
                            c.getWindow().getScaledHeight() - 39})
    );
}
