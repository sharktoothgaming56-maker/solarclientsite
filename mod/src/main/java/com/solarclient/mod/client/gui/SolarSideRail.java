package com.solarclient.mod.client.gui;

import com.solarclient.mod.client.social.SolarLink;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * THE SIDE RAIL — a vertical column of icon buttons flush to the far
 * right edge of a menu screen.
 *
 * This is the pattern Essential established and that people now expect:
 * social features live in a compact rail at the edge of the menu, not
 * as another entry shoved into the middle button stack. Putting them in
 * the stack means competing with Singleplayer and Multiplayer for the
 * player's attention, which social buttons should never do — they are
 * something you reach for deliberately, not something in your way.
 *
 * Deliberately NOT a SolarButton: those are wide labelled rows built
 * for the centre stack. A rail button is a square, an icon, and a
 * tooltip on hover, so it needs its own tiny implementation rather than
 * a stretched version of the wrong thing.
 *
 * Badges: a rail button can carry a count (pending invites, followers
 * you have not answered). The badge is the entire reason the rail earns
 * permanent screen space — it is how you learn something is waiting
 * without opening anything.
 */
public class SolarSideRail {

    public static final int SIZE = 26;      // button edge, in GUI pixels
    private static final int GAP = 5;
    private static final int MARGIN = 8;

    private static final int BG = 0xB0140E28;
    private static final int BG_HOVER = 0xE02A1F52;
    private static final int LINE = 0x40FFFFFF;
    private static final int ACCENT = 0xFFB98BFF;
    private static final int TEXT = 0xFFEAE6F7;
    private static final int BADGE = 0xFFE0484F;

    /** One rail entry. */
    public static class RailButton {
        public final String glyph;      // drawn centred; a short symbol
        public final String tooltip;
        public final Runnable action;
        public int badge;               // 0 = none
        public boolean accented;        // draw the glyph in the accent colour

        public int x, y;

        public RailButton(String glyph, String tooltip, Runnable action) {
            this.glyph = glyph;
            this.tooltip = tooltip;
            this.action = action;
        }

        public boolean isOver(double mx, double my) {
            return mx >= x && mx < x + SIZE && my >= y && my < y + SIZE;
        }
    }

    private final List<RailButton> buttons = new ArrayList<>();

    public List<RailButton> buttons() { return buttons; }

    /**
     * Build the standard rail for a screen. `open` receives the screen to
     * switch to, so the caller keeps control of parent/back behaviour
     * rather than this class guessing it.
     */
    public static SolarSideRail standard(Screen parent, Consumer<Screen> open) {
        SolarSideRail rail = new SolarSideRail();
        com.solarclient.mod.client.social.FriendsState state =
                com.solarclient.mod.client.social.FriendsState.get();

        RailButton friends = new RailButton("\u263A", "Friends",
                () -> open.accept(new ChatScreen(parent)));
        // Badge = invites waiting on an answer.
        int invites = state.invites().size();
        friends.badge = invites > 0 ? invites : 0;
        friends.accented = invites > 0;
        rail.buttons.add(friends);

        rail.buttons.add(new RailButton("\u2699", "SolarClient Settings",
                () -> open.accept(new SolarMenuScreen(parent))));

        rail.buttons.add(new RailButton("\u25C9", "Waypoints",
                () -> open.accept(new WaypointMenuScreen(parent))));

        return rail;
    }

    /** Lay the rail out against the right edge, vertically centred. */
    public void layout(int screenWidth, int screenHeight) {
        int total = buttons.size() * SIZE + Math.max(0, buttons.size() - 1) * GAP;
        int x = screenWidth - SIZE - MARGIN;
        int y = Math.max(MARGIN, (screenHeight - total) / 2);
        for (RailButton b : buttons) {
            b.x = x;
            b.y = y;
            y += SIZE + GAP;
        }
    }

    public void render(DrawContext ctx, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean linked = SolarLink.get().isConnected();

        for (RailButton b : buttons) {
            boolean hover = b.isOver(mouseX, mouseY);
            ctx.fill(b.x, b.y, b.x + SIZE, b.y + SIZE, hover ? BG_HOVER : BG);

            // Hairline border, brighter on the hovered one.
            int line = hover ? ACCENT : LINE;
            ctx.fill(b.x, b.y, b.x + SIZE, b.y + 1, line);
            ctx.fill(b.x, b.y + SIZE - 1, b.x + SIZE, b.y + SIZE, line);
            ctx.fill(b.x, b.y, b.x + 1, b.y + SIZE, line);
            ctx.fill(b.x + SIZE - 1, b.y, b.x + SIZE, b.y + SIZE, line);

            int glyphColor = b.accented ? ACCENT : TEXT;
            int gw = client.textRenderer.getWidth(b.glyph);
            ctx.drawTextWithShadow(client.textRenderer, Text.literal(b.glyph),
                    b.x + (SIZE - gw) / 2, b.y + (SIZE - 8) / 2, glyphColor);

            if (b.badge > 0) {
                String n = b.badge > 9 ? "9+" : String.valueOf(b.badge);
                int bw = client.textRenderer.getWidth(n) + 4;
                int bx = b.x + SIZE - bw + 2;
                int by = b.y - 2;
                ctx.fill(bx, by, bx + bw, by + 9, BADGE);
                ctx.drawTextWithShadow(client.textRenderer, Text.literal(n),
                        bx + 2, by + 1, 0xFFFFFFFF);
            }
        }

        // Tooltip last, so it draws over every button rather than under
        // whichever one happens to come after it in the list.
        for (RailButton b : buttons) {
            if (!b.isOver(mouseX, mouseY)) continue;
            String label = b.tooltip;
            if (!linked && b.tooltip.startsWith("Friends")) label += " (launcher offline)";
            int tw = client.textRenderer.getWidth(label);
            int tx = b.x - tw - 10;
            int ty = b.y + (SIZE - 10) / 2;
            ctx.fill(tx - 4, ty - 3, tx + tw + 4, ty + 12, 0xE0140E28);
            ctx.fill(tx - 4, ty - 3, tx + tw + 4, ty - 2, LINE);
            ctx.fill(tx - 4, ty + 11, tx + tw + 4, ty + 12, LINE);
            ctx.drawTextWithShadow(client.textRenderer, Text.literal(label), tx, ty, TEXT);
            break;
        }
    }

    /** @return true if a button consumed the click. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        for (RailButton b : buttons) {
            if (b.isOver(mouseX, mouseY)) {
                // No click sound here.
                //
                // PositionedSoundInstance.master() rejected both a
                // RegistryEntry<SoundEvent> and a raw SoundEvent on these
                // mappings, so the signature is something else again. Rather
                // than keep guessing at an API I can't inspect and blocking
                // every build over one cosmetic click, it's gone.
                //
                // To add it back: in your IDE, Ctrl/Cmd-click PositionedSoundInstance
                // and read master()'s real parameters, then call it here. The
                // rail works exactly the same without it.
                b.action.run();
                return true;
            }
        }
        return false;
    }
}
