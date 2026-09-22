package com.solarclient.mod.client.hud;

import com.solarclient.mod.client.config.SolarConfig;
import com.solarclient.mod.client.social.PartyState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The party, on screen, while you play.
 *
 * Shows up only when there is actually a party — a HUD that renders an
 * empty panel for every solo player is just clutter, and the player
 * shouldn't have to turn it off to get rid of it.
 *
 * Kept deliberately narrow and quiet: one row per member, a crown on the
 * leader, a coloured dot for ready state. Anything richer belongs in the
 * party screen, not layered over the game.
 */
public class PartyHud {
    public static final String ID = "party";

    private static final int ROW_H = 11;
    private static final int PAD = 5;
    private static final int WIDTH = 116;

    private static final int READY = 0xFF5EEB8F;
    private static final int WAITING = 0xFFE3AC45;
    private static final int SITTING_OUT = 0xFF736B96;
    private static final int CROWN = 0xFFFFD76A;

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!HudStyle.shouldRenderHuds() || !SolarConfig.get().isHudEnabled(ID)) return;

        PartyState party = PartyState.get();
        // No party, or a "party" that is only you: nothing worth drawing.
        if (!party.hasCompany()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        var members = party.members();

        int height = PAD * 2 + 10 + members.size() * ROW_H;
        int[] pos = SolarConfig.get().getHudPosition(ID,
                client.getWindow().getScaledWidth() - WIDTH - 6, 60);

        HudStyle.beginScale(ctx, pos[0], pos[1], SolarConfig.get().getHudScale(ID));
        HudStyle.panel(ctx, 0, 0, WIDTH, height);

        int accent = SolarConfig.get().getHudColor(ID, HudStyle.ACCENT);
        ctx.drawTextWithShadow(client.textRenderer,
                Text.literal("PARTY · " + members.size()).formatted(Formatting.BOLD),
                PAD, PAD, accent);

        int y = PAD + 11;
        for (PartyState.Member m : members) {
            int dot = m.sittingOut ? SITTING_OUT : m.ready ? READY : WAITING;
            // The leader never "readies" — they are the one everybody is
            // waiting on — so their dot shows leadership instead.
            if (m.leader) dot = CROWN;
            ctx.fill(PAD, y + 2, PAD + 3, y + 5, dot);

            String name = m.name == null ? "Player" : m.name;
            if (name.length() > 14) name = name.substring(0, 13) + "…";

            Text label = m.self
                    ? Text.literal(name).formatted(Formatting.BOLD)
                    : Text.literal(name);
            ctx.drawTextWithShadow(client.textRenderer, label, PAD + 7, y,
                    m.sittingOut ? HudStyle.TEXT_DIM : HudStyle.TEXT);

            if (m.leader) {
                int nameW = client.textRenderer.getWidth(label);
                ctx.drawTextWithShadow(client.textRenderer, Text.literal("♛"),
                        PAD + 9 + nameW, y, CROWN);
            }
            y += ROW_H;
        }

        HudStyle.endScale(ctx);
    }
}
