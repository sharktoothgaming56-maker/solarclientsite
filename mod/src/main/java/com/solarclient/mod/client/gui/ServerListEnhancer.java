package com.solarclient.mod.client.gui;

import com.solarclient.mod.client.config.SolarConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds two things to the vanilla Multiplayer screen, purely additively (no
 * Mixins, nothing vanilla is removed or re-routed):
 *
 *  1. FAVOURITES. Every server row gets a star in its bottom-right corner;
 *     click it to star/unstar (stored in solarclient.json). A button in
 *     the top-left switches between the full list and a Favorites view.
 *     The Favorites view IS the vanilla list — we hand the widget a
 *     filtered ServerList through the same public setServers(...) call
 *     vanilla itself uses, so favourites keep their icons, MOTD, ping
 *     bars, join arrow, double-click-to-join... identical look, just
 *     with the non-favourites removed. The screen's REAL ServerList is
 *     never modified, so servers.dat is untouched and toggling back
 *     restores everything. A tiny per-frame reconciler re-applies the
 *     filter whenever vanilla rebuilds the rows (refresh, add, delete,
 *     LAN discovery ticks).
 *
 *  2. DRAG TO REORDER (All Servers view). Press a row and drag vertically
 *     to move that server up/down the list; the order is written through
 *     ServerList.swapEntries — the same call the vanilla move arrows use —
 *     so it persists to servers.dat. The click zones vanilla already owns
 *     (the join arrow over the icon and the tiny move-up/move-down arrows)
 *     are left completely alone. Reordering is disabled in the Favorites
 *     view, where row numbers don't line up with the real list: the tiny
 *     move arrows are swallowed and shift+arrow reordering is blocked, so
 *     the real order can't be corrupted from a filtered view.
 */
public final class ServerListEnhancer {
    /** Which category is showing. Session-wide so it survives screen re-inits. */
    private static boolean favoritesView = false;

    // --- geometry constants ---
    private static final int STAR_BOX = 15;    // clickable star square, bottom-right of each row
    private static final int JOIN_ZONE = 32;   // vanilla's join + move-arrow strip (relative x), never touched
    private static final int MOVE_ARROWS = 16; // just the move-up/move-down arrows (relative x)
    private static final int DRAG_THRESHOLD = 4;
    private static final int ROW_STRIDE = 36;  // vanilla server list item height

    private final MinecraftClient client;
    private final MultiplayerScreen screen;
    private MultiplayerServerListWidget widget;

    // Frame-tracked mouse (screen coords) so click events can hit-test
    // without depending on unmapped accessors of the new Click object.
    private int lastMouseX = -1, lastMouseY = -1;

    // GLFW edge tracking (starts true so a held-over click can't insta-fire).
    private boolean wasDown = true;

    // Drag state (All Servers view only).
    private MultiplayerServerListWidget.ServerEntry pressedEntry;
    private int pressY;
    private boolean dragging;
    private boolean movedAny;

    // Which view the widget last had applied; lets the reconciler restore
    // the full list exactly once after leaving the Favorites view.
    private boolean appliedFav = false;

    private ServerListEnhancer(MinecraftClient client, MultiplayerScreen screen) {
        this.client = client;
        this.screen = screen;
    }

    /**
     * The category button's width for a given screen width — public so the
     * header logo can be sized to clear it symmetrically.
     */
    public static int toggleWidth(int screenWidth) {
        return Math.min(115, Math.max(70, screenWidth / 2 - 70));
    }

    /** Hook everything onto a freshly-initialised Multiplayer screen. */
    public static void attach(MinecraftClient client, MultiplayerScreen screen) {
        ServerListEnhancer e = new ServerListEnhancer(client, screen);
        e.widget = e.findWidget();

        // Category button, top-left. Its label names the view it switches
        // TO (like a link), which reads naturally next to the gold
        // "★ Favorites" marker shown while the filter is on.
        ButtonWidget toggle = ButtonWidget.builder(e.toggleLabel(), b -> {
            favoritesView = !favoritesView;
            b.setMessage(e.toggleLabel());
            e.cancelDrag();
            e.applyView();
        }).dimensions(6, 6, toggleWidth(screen.width), 20).build();
        Screens.getButtons(screen).add(toggle);

        ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, delta) -> e.onFrame(ctx, mouseX, mouseY));

        ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> e.onAllowClick(click.button()));

        // In the Favorites view, block shift+arrow reordering: vanilla
        // would swap the REAL list at filtered row numbers. Screen has no
        // modifier-check helper in 1.21.11 (and the new KeyEvent context
        // object is deliberately left untouched — its accessors aren't
        // verified), so the live GLFW key state is read directly instead,
        // the same edge-polling approach the drag machine below already
        // uses for the mouse button.
        ScreenKeyboardEvents.allowKeyPress(screen).register((s, key) -> {
            if (!favoritesView) return true;
            long handle = client.getWindow().getHandle();
            boolean shift = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            return !shift;
        });
    }

    private Text toggleLabel() {
        return favoritesView
                ? Text.literal("All Servers")
                : Text.literal("\u2605 Favorites");
    }

    private MultiplayerServerListWidget findWidget() {
        for (var el : screen.children()) {
            if (el instanceof MultiplayerServerListWidget w) return w;
        }
        return null;
    }

    /** Server entries in list order (they sit at the head of children()). */
    private List<MultiplayerServerListWidget.ServerEntry> serverRows() {
        List<MultiplayerServerListWidget.ServerEntry> rows = new ArrayList<>();
        if (widget == null) return rows;
        for (var entry : widget.children()) {
            if (entry instanceof MultiplayerServerListWidget.ServerEntry se) {
                rows.add(se);
            } else {
                break; // scanning/LAN entries follow the servers; stop at the first
            }
        }
        return rows;
    }

    private static String keyOf(ServerInfo info) {
        return SolarConfig.serverFavoriteKey(info.name, info.address);
    }

    /** The favourite ServerInfos, in real-list order (same instances). */
    private List<ServerInfo> favoriteInfos() {
        List<ServerInfo> favs = new ArrayList<>();
        SolarConfig cfg = SolarConfig.get();
        ServerList real = screen.getServerList();
        for (int i = 0; i < real.size(); i++) {
            ServerInfo info = real.get(i);
            if (cfg.isFavoriteServer(keyOf(info))) favs.add(info);
        }
        return favs;
    }

    // =====================================================================
    // View switching: hand the widget either the real list or a filtered
    // stand-in built from the SAME ServerInfo instances.
    // =====================================================================
    private void applyView() {
        if (widget == null) widget = findWidget();
        if (widget == null) return;

        if (favoritesView) {
            // A throwaway ServerList that exists only to feed setServers.
            // It is never loaded from or saved to disk by us, and vanilla's
            // own save path (delete, edit, favicon updates) goes through
            // screen.getServerList() — the real list — so servers.dat stays
            // safe. The drain loop is belt-and-braces against any future
            // constructor deciding to pre-load entries.
            ServerList favs = new ServerList(client);
            while (favs.size() > 0) favs.remove(favs.get(0));
            for (ServerInfo info : favoriteInfos()) {
                favs.add(info, false);
            }
            widget.setServers(favs);
        } else {
            widget.setServers(screen.getServerList());
        }
        widget.setSelected(null);
        appliedFav = favoritesView;
    }

    /**
     * Vanilla rebuilds the rows from the REAL list after refresh, add,
     * delete and on LAN ticks. Detect that (or a favourite being starred /
     * unstarred) and re-apply the filter. Comparison is by address key, not
     * identity, so a ping mutating an info doesn't cause rebuild loops.
     */
    private void reconcileView() {
        if (favoritesView) {
            List<ServerInfo> expected = favoriteInfos();
            List<MultiplayerServerListWidget.ServerEntry> rows = serverRows();
            boolean match = rows.size() == expected.size();
            if (match) {
                for (int i = 0; i < rows.size(); i++) {
                    if (!keyOf(rows.get(i).getServer()).equals(keyOf(expected.get(i)))) {
                        match = false;
                        break;
                    }
                }
            }
            if (!match) applyView();
        } else if (appliedFav) {
            applyView(); // restore the full list once after leaving Favorites
        }
    }

    // =====================================================================
    // Per-frame: reconcile the view, draw stars, run the drag machine.
    // =====================================================================
    private void onFrame(DrawContext ctx, int mouseX, int mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (widget == null) widget = findWidget();
        if (widget == null) return;

        boolean down = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean pressedEdge = down && !wasDown;
        boolean releasedEdge = !down && wasDown;
        wasDown = down;

        reconcileView();

        int wx = widget.getX(), wy = widget.getY(), ww = widget.getWidth(), wh = widget.getHeight();
        List<MultiplayerServerListWidget.ServerEntry> rows = serverRows();
        SolarConfig cfg = SolarConfig.get();

        // ---------- stars on every row, in both views ----------
        ctx.enableScissor(wx, wy, wx + ww, wy + wh);
        for (MultiplayerServerListWidget.ServerEntry row : rows) {
            int y = row.getContentY(), h = row.getContentHeight();
            if (y + h < wy || y > wy + wh) continue; // fully scrolled out

            boolean fav = cfg.isFavoriteServer(keyOf(row.getServer()));
            int sx = starX(row), sy = starY(row);
            boolean hover = mouseX >= sx && mouseX < sx + STAR_BOX && mouseY >= sy && mouseY < sy + STAR_BOX;
            if (hover) ctx.fill(sx, sy, sx + STAR_BOX, sy + STAR_BOX, 0x28FFFFFF);
            Text glyph = Text.literal(fav ? "\u2605" : "\u2606");
            int color = fav ? 0xFFFFD75E : (hover ? 0xFFEAE6F7 : 0x90C9C2DE);
            ctx.drawTextWithShadow(client.textRenderer, glyph,
                    sx + (STAR_BOX - client.textRenderer.getWidth(glyph)) / 2, sy + 3, color);
        }

        // Drag highlight, drawn over the row being carried (All view only).
        if (!favoritesView && dragging && pressedEntry != null) {
            int x = pressedEntry.getContentX(), y = pressedEntry.getContentY();
            int w = pressedEntry.getContentWidth(), h = pressedEntry.getContentHeight();
            int accent = 0xFF000000 | (cfg.getStrokeColor() & 0xFFFFFF);
            ctx.fill(x - 2, y - 2, x + w + 2, y - 1, accent);
            ctx.fill(x - 2, y + h + 1, x + w + 2, y + h + 2, accent);
            ctx.fill(x - 2, y - 2, x - 1, y + h + 2, accent);
            ctx.fill(x + w + 1, y - 2, x + w + 2, y + h + 2, accent);
            ctx.fill(x, y, x + w, y + h, 0x14FFFFFF);
        }
        ctx.disableScissor();

        if (favoritesView) {
            // Gold marker top-right so the filtered view announces itself
            // without disturbing the vanilla header layout.
            Text marker = Text.literal("\u2605 Favorites").formatted(Formatting.BOLD);
            ctx.drawTextWithShadow(client.textRenderer, marker,
                    screen.width - client.textRenderer.getWidth(marker) - 8, 11, 0xFFFFD75E);

            if (rows.isEmpty()) {
                int cx = wx + ww / 2;
                ctx.drawCenteredTextWithShadow(client.textRenderer,
                        "No favorite servers yet", cx, wy + wh / 2 - 10, 0xFFEAE6F7);
                ctx.drawCenteredTextWithShadow(client.textRenderer,
                        "Switch to All Servers and click the \u2606 on a server", cx, wy + wh / 2 + 4, 0xFFA79FC4);
            }
            cancelDrag();
            return; // no drag machine in the filtered view
        }

        // ---------- drag state machine (GLFW edge polling) ----------
        boolean mouseInList = mouseX >= wx && mouseX < wx + ww && mouseY >= wy && mouseY < wy + wh;

        if (pressedEdge && mouseInList) {
            for (MultiplayerServerListWidget.ServerEntry row : rows) {
                int x = row.getContentX(), y = row.getContentY();
                int w = row.getContentWidth(), h = row.getContentHeight();
                boolean inRow = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
                if (!inRow) continue;
                int relX = mouseX - x;
                boolean inStar = mouseX >= starX(row) && mouseX < starX(row) + STAR_BOX
                        && mouseY >= starY(row) && mouseY < starY(row) + STAR_BOX;
                // Leave vanilla's join + move-arrow strip and the star alone.
                if (relX >= JOIN_ZONE && !inStar) {
                    pressedEntry = row;
                    pressY = mouseY;
                    dragging = false;
                    movedAny = false;
                }
                break;
            }
        }

        if (down && pressedEntry != null && !dragging && Math.abs(mouseY - pressY) > DRAG_THRESHOLD) {
            dragging = true;
            widget.setSelected(pressedEntry); // vanilla highlight follows the carried row
        }

        if (dragging && pressedEntry != null) {
            List<MultiplayerServerListWidget.ServerEntry> live = serverRows();
            int cur = live.indexOf(pressedEntry);
            if (cur < 0) {
                cancelDrag(); // list was rebuilt (refresh/LAN tick) mid-drag
            } else {
                int target = slotUnderMouse(live, mouseY);
                ServerList list = screen.getServerList();
                // Walk one swap at a time so the saved list moves in
                // lockstep; guard against any size mismatch just in case.
                boolean swappedNow = false;
                while (cur != target && target >= 0 && target < live.size()
                        && cur < list.size() && target < list.size()) {
                    int next = cur + (target > cur ? 1 : -1);
                    list.swapEntries(cur, next); // persists order (same call the arrows use)
                    cur = next;
                    swappedNow = true;
                    movedAny = true;
                }
                if (swappedNow) {
                    // Rebuild the rows from the reordered list — the same
                    // public call vanilla's own move arrows use. This
                    // replaces the ServerEntry objects, so re-acquire the
                    // one we're carrying by its index and keep it selected.
                    widget.setServers(list);
                    List<MultiplayerServerListWidget.ServerEntry> rebuilt = serverRows();
                    if (cur >= 0 && cur < rebuilt.size()) {
                        pressedEntry = rebuilt.get(cur);
                        widget.setSelected(pressedEntry);
                    } else {
                        cancelDrag();
                    }
                }
            }
        }

        if (releasedEdge) {
            if (movedAny) {
                try {
                    screen.getServerList().saveFile(); // belt-and-braces persist
                } catch (Exception ignored) {}
            }
            cancelDrag();
        }
    }

    private int starX(MultiplayerServerListWidget.ServerEntry row) {
        return row.getContentX() + row.getContentWidth() - STAR_BOX - 2;
    }

    private int starY(MultiplayerServerListWidget.ServerEntry row) {
        return row.getContentY() + row.getContentHeight() - STAR_BOX - 1;
    }

    /** Which slot index the mouse is over, clamped to the ends of the list. */
    private int slotUnderMouse(List<MultiplayerServerListWidget.ServerEntry> rows, int mouseY) {
        if (rows.isEmpty()) return -1;
        if (mouseY < rows.get(0).getContentY()) return 0;
        for (int i = 0; i < rows.size(); i++) {
            int top = rows.get(i).getContentY();
            if (mouseY >= top && mouseY < top + ROW_STRIDE) return i;
        }
        return rows.size() - 1;
    }

    private void cancelDrag() {
        pressedEntry = null;
        dragging = false;
        movedAny = false;
    }

    // =====================================================================
    // Click interception
    // =====================================================================
    /** Return false to consume the click before vanilla sees it. */
    private boolean onAllowClick(int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (widget == null || lastMouseX < 0) return true;
        int mx = lastMouseX, my = lastMouseY;
        int wx = widget.getX(), wy = widget.getY(), ww = widget.getWidth(), wh = widget.getHeight();
        if (my < wy || my >= wy + wh) return true;

        for (MultiplayerServerListWidget.ServerEntry row : serverRows()) {
            int x = row.getContentX(), y = row.getContentY();
            int w = row.getContentWidth(), h = row.getContentHeight();
            boolean inRow = mx >= x && mx < x + w && my >= y && my < y + h;
            if (!inRow) continue;

            // Star click: ours in both views, and consumed so it can't
            // count toward vanilla's double-click-to-join.
            int sx = starX(row), sy = starY(row);
            if (mx >= sx && mx < sx + STAR_BOX && my >= sy && my < sy + STAR_BOX) {
                SolarConfig.get().toggleFavoriteServer(keyOf(row.getServer()));
                return false;
            }

            // Favorites view: swallow the tiny move arrows — they'd swap
            // the REAL list at this filtered row's number. Selecting,
            // joining (the arrow at relX 16..32) and double-click-to-join
            // all pass straight through to the real vanilla entry.
            if (favoritesView && mx - x < MOVE_ARROWS) {
                return false;
            }
            break;
        }
        return true;
    }
}
