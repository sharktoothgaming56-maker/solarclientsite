package com.solarclient.mod.client.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The in-game mirror of the launcher's social state.
 *
 * Deliberately a dumb snapshot holder. Every field is replaced wholesale
 * when a new payload arrives rather than patched, because the launcher
 * already resolved everything — who is a friend, who is allowed to see
 * whose status, who leads the party. Recomputing any of that here would
 * be a second source of truth, and the two would drift.
 *
 * Reads happen on the render thread, writes on the SolarLink poll
 * thread, so the collections handed out are immutable copies and the
 * fields are volatile. That is enough: nothing here does read-modify-write.
 */
public final class PartyState {
    private static final PartyState INSTANCE = new PartyState();

    public static PartyState get() { return INSTANCE; }

    private PartyState() {}

    // ---- member ------------------------------------------------------
    public static final class Member {
        public final String id;
        public final String name;
        public final String uuid;
        public final boolean leader;
        public final boolean self;
        public final boolean ready;
        public final boolean sittingOut;
        public final String activity;   // already-formatted "where they are"

        Member(String id, String name, String uuid, boolean leader, boolean self,
               boolean ready, boolean sittingOut, String activity) {
            this.id = id; this.name = name; this.uuid = uuid;
            this.leader = leader; this.self = self;
            this.ready = ready; this.sittingOut = sittingOut;
            this.activity = activity;
        }
    }

    public static final class Friend {
        public final String id;
        public final String name;
        public final String uuid;
        public final boolean online;
        public final String activity;
        public final String joinable;   // server address, or null

        Friend(String id, String name, String uuid, boolean online, String activity, String joinable) {
            this.id = id; this.name = name; this.uuid = uuid;
            this.online = online; this.activity = activity; this.joinable = joinable;
        }
    }

    public static final class Invite {
        public final String id;
        public final String fromName;
        public final boolean instantLaunch;
        public final String target;

        Invite(String id, String fromName, boolean instantLaunch, String target) {
            this.id = id; this.fromName = fromName;
            this.instantLaunch = instantLaunch; this.target = target;
        }
    }

    // ---- state -------------------------------------------------------
    private volatile List<Member> members = Collections.emptyList();
    private volatile List<Friend> friends = Collections.emptyList();
    private volatile List<Invite> invites = Collections.emptyList();
    private volatile boolean inParty = false;
    private volatile boolean leader = false;
    private volatile boolean instantLaunch = false;
    private volatile boolean anyoneCanInvite = true;
    private volatile String selfName = null;

    /** Set when a notice arrives, cleared once the toast has been shown. */
    private volatile String pendingToastTitle = null;
    private volatile String pendingToastBody = null;

    public List<Member> members() { return members; }
    public List<Friend> friends() { return friends; }
    public List<Invite> invites() { return invites; }
    public boolean inParty() { return inParty; }
    public boolean isLeader() { return leader; }
    public boolean instantLaunch() { return instantLaunch; }
    public boolean anyoneCanInvite() { return anyoneCanInvite; }
    public String selfName() { return selfName; }

    /** True when I am in a party with at least one other person. */
    public boolean hasCompany() { return inParty && members.size() > 1; }

    public void clear() {
        members = Collections.emptyList();
        friends = Collections.emptyList();
        invites = Collections.emptyList();
        inParty = false;
        leader = false;
    }

    // ---- ingest ------------------------------------------------------
    public void apply(JsonObject state) {
        try {
            JsonObject self = obj(state, "self");
            selfName = self != null ? str(self, "name") : null;

            JsonObject party = obj(state, "party");
            if (party == null) {
                members = Collections.emptyList();
                inParty = false;
                leader = false;
            } else {
                inParty = true;
                leader = bool(party, "isLeader");
                instantLaunch = "instant".equals(str(party, "readyMode"));
                anyoneCanInvite = bool(party, "allowAnyoneInvite");
                List<Member> next = new ArrayList<>();
                JsonArray arr = arr(party, "members");
                for (JsonElement e : arr) {
                    JsonObject m = e.getAsJsonObject();
                    next.add(new Member(
                            str(m, "id"), str(m, "name"), str(m, "uuid"),
                            bool(m, "isLeader"), bool(m, "isSelf"),
                            bool(m, "ready"), bool(m, "sittingOut"),
                            activityText(obj(m, "activity"))));
                }
                // Leader first, then me, then everyone else. That is the
                // order the panel reads best in — the person who controls
                // the launch, then yourself, then the rest.
                next.sort((a, b) -> {
                    if (a.leader != b.leader) return a.leader ? -1 : 1;
                    if (a.self != b.self) return a.self ? -1 : 1;
                    return 0;
                });
                members = Collections.unmodifiableList(next);
            }

            List<Friend> f = new ArrayList<>();
            for (JsonElement e : arr(state, "friends")) {
                JsonObject p = e.getAsJsonObject();
                f.add(new Friend(
                        str(p, "id"), str(p, "name"), str(p, "uuid"),
                        bool(p, "online"),
                        activityText(obj(p, "activity")),
                        str(p, "joinable")));
            }
            // Online friends first, then alphabetical — the same ordering
            // the launcher's drawer uses, so the two never disagree.
            f.sort((a, b) -> {
                if (a.online != b.online) return a.online ? -1 : 1;
                return a.name.compareToIgnoreCase(b.name);
            });
            friends = Collections.unmodifiableList(f);

            List<Invite> inv = new ArrayList<>();
            for (JsonElement e : arr(state, "invites")) {
                JsonObject i = e.getAsJsonObject();
                JsonObject from = obj(i, "from");
                JsonObject target = obj(i, "target");
                inv.add(new Invite(
                        str(i, "id"),
                        from != null ? str(from, "name") : "Someone",
                        "instant".equals(str(i, "readyMode")),
                        target != null ? firstNonNull(str(target, "name"), str(target, "address")) : null));
            }
            invites = Collections.unmodifiableList(inv);
        } catch (Exception ignored) {
            // A malformed payload must not take the game down. Keeping the
            // previous snapshot is strictly better than crashing or
            // blanking the panel mid-session.
        }
    }

    public void onNotice(JsonObject notice) {
        String kind = str(notice, "kind");
        if (kind == null) return;
        switch (kind) {
            case "invite" -> setToast("Party invite", "Someone invited you — open the party menu to answer.");
            case "joined" -> setToast("Party", "A friend joined your party.");
            case "declined" -> setToast("Party", "Your invite was declined.");
            case "followback" -> setToast("New friend", "Someone followed you back.");
            case "launching" -> setToast("Party", "Launching together.");
            default -> { /* nothing worth interrupting the game for */ }
        }
    }

    private void setToast(String title, String body) {
        pendingToastTitle = title;
        pendingToastBody = body;
    }

    /** Consumes the pending toast, or returns null. Called from the tick. */
    public String[] takeToast() {
        String t = pendingToastTitle;
        if (t == null) return null;
        String b = pendingToastBody;
        pendingToastTitle = null;
        pendingToastBody = null;
        return new String[]{ t, b };
    }

    // ---- json helpers, all null-safe ---------------------------------
    private static String activityText(JsonObject a) {
        if (a == null) return null;
        String label = str(a, "label");
        String detail = str(a, "detail");
        if (label == null && detail == null) return null;
        if (label == null) return detail;
        if (detail == null) return label;
        return label + " · " + detail;
    }

    private static JsonObject obj(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull() || !o.get(k).isJsonObject()) return null;
        return o.getAsJsonObject(k);
    }

    private static JsonArray arr(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull() || !o.get(k).isJsonArray()) return new JsonArray();
        return o.getAsJsonArray(k);
    }

    private static String str(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) return null;
        try { return o.get(k).getAsString(); } catch (Exception e) { return null; }
    }

    private static boolean bool(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) return false;
        try { return o.get(k).getAsBoolean(); } catch (Exception e) { return false; }
    }

    private static String firstNonNull(String a, String b) { return a != null ? a : b; }
}
