package com.solarclient.mod.client.hud;

import com.solarclient.mod.client.config.SolarConfig;
import com.solarclient.mod.client.social.SolarLink;
import com.solarclient.mod.client.social.PartyState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * THE SIDE PANEL — your party and friends down the whole right edge,
 * in game.
 *
 * This is not a button that opens a thing. It IS the thing. It runs the
 * full height of the screen so the information is simply present while
 * you play, the way a party list is present in a modern launcher rather
 * than buried a click deep.
 *
 * Drawn as a HUD element (so it can be toggled and moved like every
 * other one) but laid out against the window edge rather than a free
 * position, because a full-height rail only makes sense flush.
 *
 * WHAT IT SHOWS, in priority order — anything waiting on YOU sits above
 * anything that is merely informational:
 *   1. Invites you have not answered
 *   2. Your party, with ready state and the crown
 *   3. Friends who are online
 *   4. Friends who are offline, dimmed
 */
public class PartySidePanel {
    public static final String ID = "party_panel";

    private static final int WIDTH = 146;
    private static final int PAD = 7;
    private static final int ROW_H = 26;   // tall enough for a 20px head
    private static final int HEAD_H = 15;

    private static final int READY = 0xFF5EEB8F;
    private static final int WAITING = 0xFFE3AC45;
    private static final int DIM = 0xFF736B96;
    private static final int CROWN = 0xFFFFD76A;
    private static final int TEXT = 0xFFEAE6F7;
    private static final int ACCENT = 0xFFB98BFF;

    public static void render(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tick) {
        if (!HudStyle.shouldRenderHuds()) return;
        if (!SolarConfig.get().isHudEnabled(ID)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        PartyState state = PartyState.get();

        // Only worth a rail when there's something to say: an invite, an
        // actual party (not just yourself), or an online friend. Solo with
        // nothing pending means nothing draws at all, rather than a rail
        // whose only content is "You / Not in a party".
        boolean showParty = state.inParty() && !state.members().isEmpty();
        boolean hasInvites = !state.invites().isEmpty();
        boolean hasFriends = !state.friends().isEmpty();
        if (!showParty && !hasInvites && !hasFriends) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        int x = screenW - WIDTH - 4;
        int y = 4;
        int maxBottom = screenH - 4;

        // Size the rail to what's actually going to be drawn instead of
        // always spanning the full window height — a single invite or a
        // two-person party shouldn't paint a panel from top to bottom.
        int bottom = Math.min(maxBottom, y + measureHeight(state, showParty, hasFriends));

        // One translucent rail behind everything, so the sections read as
        // one panel with divisions rather than three stacked boxes.
        HudStyle.panel(ctx, x, y, WIDTH, bottom - y);

        int cy = y + PAD;

        // ---- invites: the only thing here that is waiting on you ------
        for (PartyState.Invite inv : state.invites()) {
            if (cy + 30 > bottom) break;
            ctx.fill(x + 3, cy - 2, x + WIDTH - 3, cy + 28, 0x38B98BFF);
            ctx.fill(x + 3, cy - 2, x + 4, cy + 28, ACCENT); // accent spine
            ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal(clip(client, inv.fromName, WIDTH - 20)).formatted(Formatting.BOLD),
                    x + PAD, cy + 1, TEXT);
            ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal("invited you"), x + PAD, cy + 11, DIM);
            ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal(inv.instantLaunch ? "No ready-up!" : "Open party menu"),
                    x + PAD, cy + 20, inv.instantLaunch ? WAITING : ACCENT);
            cy += 34;
        }

        // ---- party ----------------------------------------------------
        // Skipped entirely when solo — see showParty above. Once you're
        // actually in a party with others, the header and roster show.
        if (showParty) {
            cy = section(ctx, client, x, cy, "PARTY · " + state.members().size());

            for (PartyState.Member m : state.members()) {
                if (cy + ROW_H > bottom) break;
                String sub = m.sittingOut ? "Sitting out"
                        : m.leader ? "Leader"
                        : m.ready ? "Ready" : "Not ready";
                int subColor = m.sittingOut ? DIM
                        : m.leader ? CROWN
                        : m.ready ? READY : WAITING;
                avatarRow(ctx, client, x, cy,
                        m.name == null ? "Player" : m.name, sub, m.uuid,
                        m.self, m.leader, m.sittingOut ? DIM : TEXT, subColor);
                cy += ROW_H;
            }
            if (state.instantLaunch() && cy + 10 < bottom) {
                ctx.drawTextWithShadow(client.textRenderer,
                        Text.literal("Instant launch"), x + PAD, cy, WAITING);
                cy += 12;
            }
            cy += 4;
        }

        // ---- friends ---------------------------------------------------
        if (!state.friends().isEmpty() && cy + HEAD_H < bottom) {
            long online = state.friends().stream().filter(f -> f.online).count();
            cy = section(ctx, client, x, cy, "FRIENDS · " + online + "/" + state.friends().size());

            for (PartyState.Friend f : state.friends()) {
                if (cy + ROW_H > bottom) {
                    // Say how many did not fit rather than silently cutting
                    // the list off at whatever the window height allows.
                    int shown = countShown(state, bottom, cy);
                    if (shown > 0) {
                        ctx.drawTextWithShadow(client.textRenderer,
                                Text.literal("+" + shown + " more"), x + PAD, bottom - 12, DIM);
                    }
                    break;
                }
                String sub = !f.online ? "Offline"
                        : f.activity != null ? f.activity : "Online";
                avatarRow(ctx, client, x, cy, f.name, sub, f.uuid,
                        false, false, f.online ? TEXT : DIM, DIM);
                cy += ROW_H;
            }
        }
    }

    /**
     * One row: the player's head on the left, name and status stacked to
     * the right of it, crown on the far right if they lead.
     *
     * THE HEAD is drawn from the live skin when we can get it. That is
     * only possible for players the client already knows about — someone
     * on your current server. For everyone else there is no skin loaded
     * and no way to fetch one synchronously inside a render call, so the
     * fallback is a tile tinted from a hash of their name with their
     * initial on it. Consistent per player, instantly readable, and it
     * never blocks a frame on a network request.
     */
    private static void avatarRow(DrawContext ctx, MinecraftClient client, int x, int y,
                                  String name, String sub, String uuid,
                                  boolean self, boolean leader,
                                  int nameColor, int subColor) {
        final int head = 20;
        final int hx = x + PAD;
        final int hy = y + 2;

        boolean drew = false;
        if (uuid != null && client.getNetworkHandler() != null) {
            try {
                java.util.UUID id = java.util.UUID.fromString(dashify(uuid));
                var entry = client.getNetworkHandler().getPlayerListEntry(id);
                if (entry != null) {
                    // The compiler settled this one: it reported
                    // "TextureAsset cannot be converted to SkinTextures",
                    // i.e. draw() wants the whole SkinTextures object, not a
                    // texture pulled out of it. Both .texture() and .body()
                    // were me unwrapping something that shouldn't be unwrapped.
                    net.minecraft.client.gui.PlayerSkinDrawer.draw(
                            ctx, entry.getSkinTextures(), hx, hy, head);
                    drew = true;
                }
            } catch (Exception ignored) {
                // Unknown player, malformed uuid, or a mapping change in
                // the skin API — fall through to the tile rather than
                // taking the HUD down mid-frame.
            }
        }

        if (!drew) {
            int tint = tintFor(name);
            ctx.fill(hx, hy, hx + head, hy + head, 0xFF000000 | (tint & 0xFFFFFF));
            ctx.fill(hx, hy, hx + head, hy + 1, 0x33FFFFFF);   // top highlight
            String initial = name == null || name.isEmpty()
                    ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            int iw = client.textRenderer.getWidth(initial);
            ctx.drawTextWithShadow(client.textRenderer, Text.literal(initial),
                    hx + (head - iw) / 2, hy + 6, 0xFFFFFFFF);
        }

        // A thin accent edge on your own row, so you find yourself instantly.
        if (self) ctx.fill(hx - 2, hy, hx - 1, hy + head, ACCENT);

        int tx = hx + head + 5;
        int room = WIDTH - (tx - x) - PAD - (leader ? 8 : 0);
        Text label = self
                ? Text.literal(clip(client, name, room)).formatted(Formatting.BOLD)
                : Text.literal(clip(client, name, room));
        ctx.drawTextWithShadow(client.textRenderer, label, tx, y + 3, nameColor);
        ctx.drawTextWithShadow(client.textRenderer,
                Text.literal(clip(client, sub, room)), tx, y + 13, subColor);

        if (leader) {
            ctx.drawTextWithShadow(client.textRenderer, Text.literal("♛"),
                    x + WIDTH - PAD - 6, y + 3, CROWN);
        }
    }

    /**
     * Mirrors the layout math in {@link #render} to get a total content
     * height up front, before anything is drawn — the background rail
     * needs its final size before the first pixel of content goes down.
     * Friends aren't walked row-by-row here: the render loop itself caps
     * a huge friends list against {@code bottom} and prints "+N more", so
     * for sizing purposes it's enough to ask for as much room as it would
     * take unclipped and let the min() against maxBottom do the clamping.
     */
    private static int measureHeight(PartyState state, boolean showParty, boolean hasFriends) {
        int h = PAD;
        h += state.invites().size() * 34;
        if (showParty) {
            h += HEAD_H;
            h += state.members().size() * ROW_H;
            if (state.instantLaunch()) h += 12;
            h += 4;
        }
        if (hasFriends) {
            h += HEAD_H;
            h += state.friends().size() * ROW_H;
        }
        h += PAD;
        return h;
    }

    /** Insert the dashes a UUID needs if the wire form omitted them. */
    private static String dashify(String raw) {
        String u = raw.replace("-", "");
        if (u.length() != 32) return raw;
        return u.substring(0, 8) + "-" + u.substring(8, 12) + "-" + u.substring(12, 16)
                + "-" + u.substring(16, 20) + "-" + u.substring(20);
    }

    /** Stable pleasant colour per name. Same input, same tile, always. */
    private static int tintFor(String name) {
        int h = (name == null ? "?" : name).hashCode();
        // Bias toward the mid range so no tile comes out black or white.
        int r = 70 + Math.abs(h % 120);
        int g = 70 + Math.abs((h >> 8) % 120);
        int b = 90 + Math.abs((h >> 16) % 120);
        return (r << 16) | (g << 8) | b;
    }

    /** Section header with a hairline under it. Returns the new cursor. */
    private static int section(DrawContext ctx, MinecraftClient client, int x, int y, String label) {
        ctx.drawTextWithShadow(client.textRenderer,
                Text.literal(label).formatted(Formatting.BOLD), x + PAD, y, ACCENT);
        ctx.fill(x + PAD, y + 10, x + WIDTH - PAD, y + 11, 0x22FFFFFF);
        return y + HEAD_H;
    }

    private static int countShown(PartyState state, int bottom, int cy) {
        int fits = Math.max(0, (bottom - cy) / ROW_H);
        return Math.max(0, state.friends().size() - fits);
    }

    /** Truncate to fit the rail rather than letting text run off it. */
    private static String clip(MinecraftClient client, String text, int maxWidth) {
        if (text == null) return "";
        String out = text;
        while (client.textRenderer.getWidth(out) > maxWidth && out.length() > 2) {
            out = out.substring(0, out.length() - 2) + "…";
        }
        return out;
    }
}
