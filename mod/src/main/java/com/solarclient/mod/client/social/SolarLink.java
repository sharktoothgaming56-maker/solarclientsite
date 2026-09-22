package com.solarclient.mod.client.social;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solarclient.mod.client.SolarClientModClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SOLAR LINK — the mod's end of the launcher bridge.
 *
 * The launcher runs a tiny HTTP server bound to 127.0.0.1 on an
 * OS-assigned port, and drops a handshake file with the port and a
 * per-session token into its own user-data directory. This class finds
 * that file, then long-polls {@code /events} on a daemon thread so party
 * changes show up in-game within a frame or two instead of on a timer.
 *
 * WHY HTTP AND NOT A SOCKET LIBRARY: {@code java.net.http.HttpClient} has
 * shipped with the JDK since 11, and this mod already requires Java 21.
 * So this whole file adds zero dependencies to build.gradle. Same story
 * on the launcher side, which uses Node's built-in http module.
 *
 * EVERYTHING HERE IS BEST-EFFORT. If the launcher is not running — the
 * player started Minecraft some other way, or closed the launcher after
 * launching — every method quietly no-ops and {@link #isConnected()}
 * stays false. Nothing in the game is allowed to break because a party
 * feature could not reach a launcher that may not exist.
 */
public final class SolarLink {
    private static final Gson GSON = new Gson();
    private static final SolarLink INSTANCE = new SolarLink();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile int port = -1;
    private volatile String token = null;
    private volatile boolean connected = false;
    private volatile long revision = 0;
    private Thread pollThread;

    private SolarLink() {}

    public static SolarLink get() { return INSTANCE; }

    public boolean isConnected() { return connected; }

    // -----------------------------------------------------------------
    // Handshake discovery
    // -----------------------------------------------------------------

    /**
     * Every place the launcher might have dropped its handshake file, tried
     * in order.
     *
     * There are TWO moving parts, both a legacy of the nebula→solar rebrand:
     *   - the FOLDER is Electron's app.getPath('userData'), which the
     *     launcher deliberately pins back to the original "nebulaclient" so
     *     it keeps reading the user's existing accounts and instances; and
     *   - the FILE is currently written as "nebula-link.json".
     * A build that only looked in SolarClient/solar-bridge.json therefore
     * never found a launcher that was running fine — which is exactly the
     * "launcher isn't connected" bug. Listing every candidate makes the mod
     * work whether the launcher writes the old name/folder or a future one,
     * with no coordinated release needed.
     */
    private static java.util.List<Path> handshakeCandidates() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home", ".");
        Path base;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            base = appData != null ? Paths.get(appData) : Paths.get(home, "AppData", "Roaming");
        } else if (os.contains("mac") || os.contains("darwin")) {
            base = Paths.get(home, "Library", "Application Support");
        } else {
            String xdg = System.getenv("XDG_CONFIG_HOME");
            base = xdg != null ? Paths.get(xdg) : Paths.get(home, ".config");
        }
        java.util.List<Path> out = new java.util.ArrayList<>();
        for (String folder : new String[] { "nebulaclient", "SolarClient" }) {
            for (String file : new String[] { "nebula-link.json", "solar-bridge.json" }) {
                out.add(base.resolve(folder).resolve(file));
            }
        }
        return out;
    }

    /** Reads the handshake file. Returns false if the launcher isn't up. */
    private boolean readHandshake() {
        try {
            Path p = null;
            for (Path candidate : handshakeCandidates()) {
                if (Files.exists(candidate)) { p = candidate; break; }
            }
            if (p == null) return false;
            JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            int newPort = o.get("port").getAsInt();
            String newToken = o.get("token").getAsString();
            if (newPort != port || !newToken.equals(token)) {
                // The launcher restarted and picked a new port — reset the
                // revision, or we'd sit waiting on an event number from a
                // server that no longer exists.
                revision = 0;
            }
            port = newPort;
            token = newToken;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // -----------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        pollThread = new Thread(this::pollLoop, "SolarLink");
        pollThread.setDaemon(true); // never hold the game open on shutdown
        pollThread.start();
    }

    public void stop() {
        running.set(false);
        if (pollThread != null) pollThread.interrupt();
    }

    private void pollLoop() {
        int backoffMs = 1000;
        while (running.get()) {
            try {
                if (!readHandshake()) {
                    connected = false;
                    PartyState.get().clear();
                    FriendsState.get().clear();
                    // No launcher. Check again on a slow cadence — this is
                    // the normal state for anyone not using the launcher,
                    // so it must cost effectively nothing.
                    Thread.sleep(5000);
                    continue;
                }

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/events?since=" + revision))
                        .header("authorization", "Bearer " + token)
                        .timeout(Duration.ofSeconds(35)) // server parks for 25s
                        .GET().build();

                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    connected = false;
                    Thread.sleep(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, 15000);
                    continue;
                }

                connected = true;
                backoffMs = 1000;
                JsonObject body = JsonParser.parseString(res.body()).getAsJsonObject();
                if (body.has("revision")) revision = body.get("revision").getAsLong();
                if (body.has("state") && !body.get("state").isJsonNull()) {
                    JsonObject st = body.getAsJsonObject("state");
                    PartyState.get().apply(st);
                    // Same payload feeds the friends screen. The launcher
                    // owns this state; we only ever mirror it.
                    FriendsState.get().apply(st);
                }
                if (body.has("notice") && !body.get("notice").isJsonNull()) {
                    PartyState.get().onNotice(body.getAsJsonObject("notice"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Timeouts are expected and routine — the long poll simply
                // had nothing to report. Only log the interesting ones.
                connected = false;
                try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { return; }
                backoffMs = Math.min(backoffMs * 2, 15000);
            }
        }
    }

    // -----------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------

    /**
     * Fire-and-forget action. Runs off the render thread so a slow or
     * dead launcher can never stall a frame — clicking "Leave party"
     * must feel instant whether or not anything is listening.
     */
    public void action(String action, JsonObject payload) {
        if (!connected || token == null) return;
        Thread.ofVirtual().start(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("action", action);
                body.add("payload", payload == null ? new JsonObject() : payload);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/action"))
                        .header("authorization", "Bearer " + token)
                        .header("content-type", "application/json")
                        .timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();
                http.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                SolarClientModClient.LOGGER.debug("SolarLink action {} failed: {}", action, e.toString());
            }
        });
    }

    /**
     * Like {@link #action}, but waits for the launcher's reply and hands the
     * human-readable result to {@code onResult} (always on a background
     * thread, so callers should hop back to the client thread to touch the
     * game). Used by the /solarclient command, which needs to tell the
     * player whether it worked. Never throws; a dead launcher just yields a
     * "not connected" style message.
     */
    public void actionForResult(String action, JsonObject payload, java.util.function.Consumer<String> onResult) {
        if (!connected || token == null) {
            onResult.accept("SolarClient launcher isn't connected.");
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("action", action);
                body.add("payload", payload == null ? new JsonObject() : payload);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/action"))
                        .header("authorization", "Bearer " + token)
                        .header("content-type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                String message = o.has("message") && !o.get("message").isJsonNull()
                        ? o.get("message").getAsString()
                        : (o.has("ok") && o.get("ok").getAsBoolean() ? "Done." : "Command failed.");
                onResult.accept(message);
            } catch (Exception e) {
                SolarClientModClient.LOGGER.debug("SolarLink actionForResult {} failed: {}", action, e.toString());
                onResult.accept("Couldn't reach the SolarClient launcher.");
            }
            });
    }

    public void actionForList(String action, JsonObject payload, java.util.function.Consumer<java.util.List<String>> onResult) {
        if (!connected || token == null) {
            onResult.accept(java.util.Collections.emptyList());
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("action", action);
                body.add("payload", payload == null ? new JsonObject() : payload);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/action"))
                        .header("authorization", "Bearer " + token)
                        .header("content-type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                java.util.List<String> result = new java.util.ArrayList<>();
                if (o.has("names") && o.get("names").isJsonArray()) {
                    for (com.google.gson.JsonElement e : o.getAsJsonArray("names")) {
                        result.add(e.getAsString());
                    }
                }
                onResult.accept(result);
            } catch (Exception e) {
                SolarClientModClient.LOGGER.debug("SolarLink actionForList {} failed: {}", action, e.toString());
                onResult.accept(java.util.Collections.emptyList());
            }
        });
    }

    /**
     * Ask the launcher for the SolarClient roles of a set of player UUIDs and
     * feed the answer into {@link SolarBadges}. Fire-and-forget from the
     * caller's view; the reply is a JSON map {@code {uuid: role}}.
     */
    public void queryRoles(com.google.gson.JsonArray uuids) {
        if (!connected || token == null) return;
        Thread.ofVirtual().start(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.add("uuids", uuids);
                JsonObject body = new JsonObject();
                body.addProperty("action", "roles:query");
                body.add("payload", payload);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/action"))
                        .header("authorization", "Bearer " + token)
                        .header("content-type", "application/json")
                        .timeout(Duration.ofSeconds(8))
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                java.util.Map<String, String> map = new java.util.HashMap<>();
                if (o.has("roles") && o.get("roles").isJsonObject()) {
                    for (var e : o.getAsJsonObject("roles").entrySet()) {
                        if (!e.getValue().isJsonNull()) map.put(e.getKey(), e.getValue().getAsString());
                    }
                }
                // Pass the uuids we asked about too, so ranks that were
                // removed (absent from the answer) get cleared, not kept.
                java.util.List<String> queried = new java.util.ArrayList<>();
                for (var el : uuids) queried.add(el.getAsString());
                SolarBadges.applyQuery(queried, map);
            } catch (Exception e) {
                SolarClientModClient.LOGGER.debug("SolarLink queryRoles failed: {}", e.toString());
            }
        });
    }

    /**
     * Resolve an invite to a joinable address through the launcher, then hand
     * the address + display name to {@code onOk} (or an error to {@code onErr}).
     * Off-thread; the caller should hop to the client thread to connect.
     */
    public void joinInvite(String inviteId, java.util.function.BiConsumer<String, String> onOk,
                           java.util.function.Consumer<String> onErr) {
        if (!connected || token == null) { onErr.accept("SolarClient launcher isn't connected."); return; }
        Thread.ofVirtual().start(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("inviteId", inviteId);
                JsonObject body = new JsonObject();
                body.addProperty("action", "friends:join");
                body.add("payload", payload);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/action"))
                        .header("authorization", "Bearer " + token)
                        .header("content-type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                boolean ok = o.has("ok") && o.get("ok").getAsBoolean();
                if (ok && o.has("address") && !o.get("address").isJsonNull()) {
                    String addr = o.get("address").getAsString();
                    String nm = o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : addr;
                    onOk.accept(addr, nm);
                } else {
                    onErr.accept(o.has("error") && !o.get("error").isJsonNull()
                            ? o.get("error").getAsString() : "That invite is no longer available.");
                }
            } catch (Exception e) {
                onErr.accept("Couldn't reach the SolarClient launcher.");
            }
        });
    }

    public void leaveParty() { action("party:leave", null); }

    // ---- friends ----------------------------------------------------

    /** Send a chat message from in game; it lands in the launcher too. */
    public void sendMessage(String conversationKind, String conversationId, String text) {
        JsonObject p = new JsonObject();
        p.addProperty("conversationKind", conversationKind);
        p.addProperty("conversationId", conversationId);
        p.addProperty("text", text);
        action("friends:message", p);
    }

    /**
     * Invite to whatever you are currently playing. On a multiplayer
     * server that is the server's address; in single-player it is the
     * world you are in, which only stays joinable while you are in it.
     */
    public void invitePlaying(String conversationKind, String conversationId,
                              String address, String worldName) {
        JsonObject p = new JsonObject();
        p.addProperty("conversationKind", conversationKind);
        p.addProperty("conversationId", conversationId);
        if (address != null) p.addProperty("address", address);
        if (worldName != null) p.addProperty("worldName", worldName);
        action("friends:invite", p);
    }

    public void markRead(String conversationId) {
        JsonObject p = new JsonObject();
        p.addProperty("conversationId", conversationId);
        action("friends:markRead", p);
    }

    public void setReady(boolean ready) {
        JsonObject o = new JsonObject();
        o.addProperty("ready", ready);
        action("party:setReady", o);
    }

    public void setSittingOut(boolean sittingOut) {
        JsonObject o = new JsonObject();
        o.addProperty("sittingOut", sittingOut);
        action("party:setSittingOut", o);
    }

    public void invite(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        action("party:invite", o);
    }

    public void transferLeader(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        action("party:transferLeader", o);
    }

    public void kick(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        action("party:kick", o);
    }

    public void respondToInvite(String inviteId, boolean accept) {
        JsonObject o = new JsonObject();
        o.addProperty("inviteId", inviteId);
        o.addProperty("accept", accept);
        action("party:respondToInvite", o);
    }

    /**
     * Tell the launcher where the player actually is. The mod knows this
     * far better than the launcher does — it can read the live server
     * connection rather than inferring it from launch arguments, so a
     * player who joined a second server mid-session still shows up
     * correctly to their friends.
     */
    public void reportActivity(String kind, String server, String label, String detail) {
        JsonObject activity = new JsonObject();
        activity.addProperty("kind", kind);
        if (server != null) activity.addProperty("server", server);
        if (label != null) activity.addProperty("label", label);
        if (detail != null) activity.addProperty("detail", detail);
        JsonObject o = new JsonObject();
        o.add("activity", activity);
        action("presence:update", o);
    }

    public void reportIdle() {
        JsonObject o = new JsonObject();
        o.add("activity", null);
        action("presence:update", o);
    }
}
