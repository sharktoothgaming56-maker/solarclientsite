package com.solarclient.mod.client.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The in-game view of the launcher's friends state.
 *
 * This holds no truth of its own — every field is replaced wholesale from
 * whatever the launcher last published over SolarLink. That is deliberate:
 * the launcher and the game must never disagree about who your friends are
 * or what was said, and the only way to guarantee that is for one of them
 * to own it. Actions here are requests sent to the launcher, and the
 * resulting state comes back through the same poll as everything else.
 */
public final class FriendsState {
    private static final FriendsState INSTANCE = new FriendsState();
    public static FriendsState get() { return INSTANCE; }
    private FriendsState() {}

    public static final class Friend {
        public final String uuid, name, status, activity;
        public Friend(String uuid, String name, String status, String activity) {
            this.uuid = uuid; this.name = name; this.status = status; this.activity = activity;
        }
        public boolean online() { return "online".equals(status) || inGame(); }
        public boolean inGame() { return "in_game".equals(status); }
    }

    public static final class Message {
        public final String from, name, text;
        public final long at;
        public final boolean mine;
        public final JsonObject invite;
        public Message(String from, String name, String text, long at, boolean mine, JsonObject invite) {
            this.from = from; this.name = name; this.text = text;
            this.at = at; this.mine = mine; this.invite = invite;
        }
    }

    /** A DM or a group chat — the in-game screen treats them the same. */
    public static final class Conversation {
        public final String kind, id, name;
        public final List<Message> messages = new ArrayList<>();
        public Conversation(String kind, String id, String name) {
            this.kind = kind; this.id = id; this.name = name;
        }
        public boolean isGroup() { return "group".equals(kind); }
    }

    private volatile List<Friend> friends = List.of();
    private volatile List<Conversation> conversations = List.of();
    private volatile List<JsonObject> invites = List.of();
    private volatile String selfName = null;
    private volatile String selfUuid = null;
    private volatile boolean linked = false;

    public List<Friend> friends() { return friends; }
    public List<Conversation> conversations() { return conversations; }
    public List<JsonObject> invites() { return invites; }
    public String selfName() { return selfName; }
    public String selfUuid() { return selfUuid; }
    public boolean isLinked() { return linked; }

    public Conversation conversation(String kind, String id) {
        for (Conversation c : conversations) {
            if (c.kind.equals(kind) && c.id.equals(id)) return c;
        }
        return null;
    }

    public void clear() {
        friends = List.of();
        conversations = List.of();
        invites = List.of();
        linked = false;
    }

    /** Replace everything from a launcher payload. Never merges. */
    public void apply(JsonObject state) {
        if (state == null) { clear(); return; }
        linked = true;

        if (state.has("self") && state.get("self").isJsonObject()) {
            JsonObject self = state.getAsJsonObject("self");
            selfName = str(self, "name");
            selfUuid = str(self, "id");
        }

        List<Friend> f = new ArrayList<>();
        if (state.has("friends") && state.get("friends").isJsonArray()) {
            for (var el : state.getAsJsonArray("friends")) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String act = null;
                if (o.has("activity") && o.get("activity").isJsonObject()) {
                    act = str(o.getAsJsonObject("activity"), "instanceName");
                }
                f.add(new Friend(str(o, "uuid"), str(o, "name"), str(o, "status"), act));
            }
        }
        friends = List.copyOf(f);

        List<Conversation> cs = new ArrayList<>();
        if (state.has("conversations") && state.get("conversations").isJsonArray()) {
            for (var el : state.getAsJsonArray("conversations")) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                Conversation c = new Conversation(str(o, "kind"), str(o, "id"), str(o, "name"));
                if (o.has("messages") && o.get("messages").isJsonArray()) {
                    JsonArray arr = o.getAsJsonArray("messages");
                    for (var mEl : arr) {
                        if (!mEl.isJsonObject()) continue;
                        JsonObject m = mEl.getAsJsonObject();
                        c.messages.add(new Message(
                                str(m, "from"),
                                str(m, "name"),
                                str(m, "text"),
                                m.has("at") && m.get("at").isJsonPrimitive() ? m.get("at").getAsLong() : 0L,
                                m.has("mine") && m.get("mine").getAsBoolean(),
                                m.has("invite") && m.get("invite").isJsonObject() ? m.getAsJsonObject("invite") : null
                        ));
                    }
                }
                cs.add(c);
            }
        }
        conversations = List.copyOf(cs);

        List<JsonObject> inv = new ArrayList<>();
        if (state.has("invites") && state.get("invites").isJsonArray()) {
            for (var el : state.getAsJsonArray("invites")) {
                if (el.isJsonObject()) inv.add(el.getAsJsonObject());
            }
        }
        invites = List.copyOf(inv);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }
}
