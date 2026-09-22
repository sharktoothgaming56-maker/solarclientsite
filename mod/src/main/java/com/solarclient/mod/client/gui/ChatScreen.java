package com.solarclient.mod.client.gui;

import com.solarclient.mod.client.social.FriendsState;
import com.solarclient.mod.client.social.SolarLink;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * FRIENDS, in game. A friends-only social screen — there is no party any
 * more. The left column lists your friends (with online / in game / offline
 * status) and any group chats; clicking one opens the conversation on the
 * right, where you can read the history and type a reply.
 *
 * All of it is driven by the launcher over SolarLink; this screen owns no
 * state. Sending a message, or an invite, is a request to the launcher, and
 * the result comes back through the normal poll.
 */
public class ChatScreen extends SpaceTheme.SpaceScreen {
    private final Screen parent;

    private static final int TEXT = 0xFFEAE6F7;
    private static final int DIM = 0xFF736B96;
    private static final int MINE = 0xFFB98BFF;
    private static final int ACCENT = 0xFFA79FC4;
    private static final int ONLINE = 0xFF5EEB8F;
    private static final int INGAME = 0xFF5EE0E0;

    // Open conversation, kept by kind+id so it survives a state refresh.
    private String selKind, selId, selName;

    private int colW, rightW, leftX, rightX, listTop, listBottom;
    private int messagesBottom;                 // messages never draw below this
    private com.google.gson.JsonObject pendingInvite; // newest incoming invite, for Join
    private TextFieldWidget input;

    public ChatScreen(Screen parent) {
        super(Text.literal("Friends"));
        this.parent = parent;
    }

    @Override
    protected boolean showWordmark() { return false; }

    @Override
    protected void init() {
        solarButtons.clear();

        int total = Math.max(320, Math.min(560, (int) (this.width * 0.82f)));
        colW = (int) ((total - 12) * 0.40f);
        rightW = total - 12 - colW;
        leftX = (this.width - total) / 2;
        rightX = leftX + colW + 12;
        listTop = 58;
        listBottom = this.height - 68;

        FriendsState state = FriendsState.get();
        boolean linked = SolarLink.get().isConnected();

        if (linked) {
            int y = listTop;

            // Friends first — clicking one opens (or starts) a DM with them.
            for (FriendsState.Friend f : state.friends()) {
                if (y + 20 > listBottom - 40) break;
                final String id = f.uuid, nm = f.name;
                boolean active = "dm".equals(selKind) && id.equals(selId);
                SpaceTheme.SolarButton b = new SpaceTheme.SolarButton(leftX, y, colW, 18, f.name,
                        () -> { selKind = "dm"; selId = id; selName = nm; this.clearAndInit(); });
                b.bold = active;
                solarButtons.add(b);
                y += 20;
            }

            // Group chats below, if any.
            for (FriendsState.Conversation c : state.conversations()) {
                if (!c.isGroup()) continue;
                if (y + 20 > listBottom) break;
                final String id = c.id, nm = c.name;
                boolean active = "group".equals(selKind) && id.equals(selId);
                SpaceTheme.SolarButton b = new SpaceTheme.SolarButton(leftX, y, colW, 18,
                        "# " + (c.name == null ? "Group" : c.name),
                        () -> { selKind = "group"; selId = id; selName = nm; this.clearAndInit(); });
                b.bold = active;
                solarButtons.add(b);
                y += 20;
            }
        }

        // ---- right column: action row, then message box + send --------
        messagesBottom = this.height - 62;   // default when there's no action row
        if (linked && selId != null) {
            int inputY = this.height - 44;
            int actionRowY = inputY - 24;

            // Newest invite from the OTHER person in this chat — the thing a
            // Join button would act on.
            FriendsState.Conversation conv = state.conversation(selKind, selId);
            pendingInvite = null;
            if (conv != null) {
                for (int i = conv.messages.size() - 1; i >= 0; i--) {
                    FriendsState.Message mm = conv.messages.get(i);
                    if (mm.invite != null && !mm.mine) { pendingInvite = mm.invite; break; }
                }
            }

            // Only server invites remain — inviting to a single-player world
            // was removed. A world can't be made reachable to friends over the
            // internet without a relay, so the button only ever misled people.
            boolean onServer = this.client.getCurrentServerEntry() != null;
            boolean hasJoin = pendingInvite != null;
            int count = (hasJoin ? 1 : 0) + (onServer ? 1 : 0);

            if (count > 0) {
                int gap = 4;
                int each = (rightW - gap * (count - 1)) / count;
                int ax = rightX;
                if (hasJoin) {
                    solarButtons.add(new SpaceTheme.SolarButton(ax, actionRowY, each, 20, "Join", this::joinPending));
                    ax += each + gap;
                }
                if (onServer) {
                    solarButtons.add(new SpaceTheme.SolarButton(ax, actionRowY, each, 20,
                            "Invite to this server", this::inviteToServer));
                }
                messagesBottom = actionRowY - 6;
            } else {
                messagesBottom = inputY - 6;
            }

            input = new TextFieldWidget(this.textRenderer, rightX, inputY, rightW - 52, 20,
                    Text.literal("Message"));
            input.setMaxLength(500);
            input.setPlaceholder(Text.literal("Message " + (selName == null ? "" : selName) + "…").formatted(Formatting.DARK_GRAY));
            this.addDrawableChild(input);
            this.setInitialFocus(input);

            solarButtons.add(new SpaceTheme.SolarButton(rightX + rightW - 48, inputY, 48, 20, "Send",
                    this::send));
        }

        solarButtons.add(new SpaceTheme.SolarButton((this.width - 120) / 2, this.height - 28, 120, 20, "Done",
                () -> this.client.setScreen(parent)));
    }


    private void inviteToServer() {
        if (selId == null) return;
        var entry = this.client.getCurrentServerEntry();
        if (entry == null) { feedbackChat("You're not on a server."); return; }
        SolarLink.get().invitePlaying(selKind, selId, entry.address, entry.name);
        feedbackChat("Invited " + (selName == null ? "them" : selName) + " to this server.");
    }

    /** Join the newest incoming invite in this chat. */
    private void joinPending() {
        if (pendingInvite == null) return;
        String id = pendingInvite.has("id") && !pendingInvite.get("id").isJsonNull()
                ? pendingInvite.get("id").getAsString() : null;
        if (id == null) { feedbackChat("This invite can't be joined."); return; }
        feedbackChat("Joining…");
        SolarLink.get().joinInvite(id,
                (address, name) -> this.client.execute(() -> connectTo(address, name)),
                err -> this.client.execute(() -> feedbackChat(err)));
    }

    /** Connect to a resolved address from in game, leaving the current world. */
    private void connectTo(String address, String name) {
        try {
            net.minecraft.client.network.ServerAddress addr =
                    net.minecraft.client.network.ServerAddress.parse(address);
            net.minecraft.client.network.ServerInfo info = new net.minecraft.client.network.ServerInfo(
                    name == null ? address : name, address,
                    net.minecraft.client.network.ServerInfo.ServerType.OTHER);
            net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(
                    this, this.client, addr, info, false, null);
        } catch (Exception e) {
            feedbackChat("Couldn't join: " + e.getMessage());
        }
    }

    private void send() {
        if (input == null || selId == null) return;
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        SolarLink.get().sendMessage(selKind, selId, text);
        input.setText("");
    }

    private void feedbackChat(String message) {
        if (this.client.player != null) {
            this.client.player.sendMessage(
                    Text.literal("[SolarClient] ").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
                            .append(Text.literal(message).formatted(Formatting.WHITE)),
                    false);
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
        int key = keyInput.key();
        if ((key == 257 || key == 335) && input != null && input.isFocused()) {
            send();
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        FriendsState state = FriendsState.get();
        boolean linked = SolarLink.get().isConnected();

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("FRIENDS").formatted(Formatting.BOLD), this.width / 2, 26, 0xFFB98BFF);

        if (!linked) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("SolarClient launcher isn't running."),
                    this.width / 2, this.height / 2 - 10, TEXT);
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Start the launcher to see your friends."),
                    this.width / 2, this.height / 2 + 4, DIM);
            return;
        }

        // Left: status dot + name for each friend (the buttons draw the
        // clickable label; this adds the status line beneath faintly).
        int y = listTop;
        for (FriendsState.Friend f : state.friends()) {
            if (y + 20 > listBottom - 40) break;
            int dot = f.inGame() ? INGAME : f.online() ? ONLINE : 0x40FFFFFF;
            ctx.fill(leftX - 5, y + 6, leftX - 2, y + 13, dot);
            y += 20;
        }
        if (state.friends().isEmpty()) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("No friends yet."), leftX, listTop + 2, DIM);
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("Add people in the launcher."), leftX, listTop + 14, DIM);
        }

        // Right: the open conversation.
        if (selId == null) {
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("Pick a friend to open your DMs.").formatted(Formatting.ITALIC),
                    rightX, listTop + 2, DIM);
            return;
        }

        FriendsState.Friend friend = "dm".equals(selKind)
                ? state.friends().stream().filter(fr -> fr.uuid.equals(selId)).findFirst().orElse(null)
                : null;
        String title = selName != null ? selName : "Chat";
        String status = friend == null ? "" : "  ·  " + (friend.inGame() ? "In game" : friend.online() ? "Online" : "Offline");
        header(ctx, rightX, listTop - 12, ("group".equals(selKind) ? "# " : "") + title + status);

        FriendsState.Conversation c = state.conversation(selKind, selId);
        List<FriendsState.Message> msgs = c == null ? List.of() : c.messages;
        if (msgs.isEmpty()) {
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("No messages yet — say hi.").formatted(Formatting.ITALIC),
                    rightX, listTop + 4, DIM);
        }

        int lineH = 10;
        int yy = messagesBottom - lineH;
        for (int i = msgs.size() - 1; i >= 0 && yy > listTop; i--) {
            FriendsState.Message m = msgs.get(i);
            String who = m.mine ? "You" : (m.name == null ? "?" : m.name);
            String body = m.text == null ? "" : m.text;
            if (m.invite != null && body.isEmpty()) body = "[world invite]";
            List<String> wrapped = wrap(who + ": " + body, rightW);
            for (int w = wrapped.size() - 1; w >= 0 && yy > listTop; w--) {
                ctx.drawTextWithShadow(this.textRenderer, Text.literal(wrapped.get(w)), rightX, yy,
                        m.mine ? MINE : TEXT);
                yy -= lineH;
            }
        }
    }

    private List<String> wrap(String s, int maxWidth) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (this.textRenderer.getWidth(test) > maxWidth && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    private void header(DrawContext ctx, int x, int y, String label) {
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal(label).formatted(Formatting.BOLD), x, y, ACCENT);
    }
}
