package com.solarclient.mod.client.social;

import com.google.gson.JsonArray;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SolarClient rank badges - the little logo drawn beside a player's name.
 *
 * HOW THE ICON IS DRAWN: not as a texture at a computed position (which
 * would mean re-implementing alignment everywhere a name appears), but as a
 * single glyph in a custom bitmap font. Prepend that glyph to a name's Text
 * and vanilla's own text renderer places, scales and aligns it for free -
 * in the tab list, in chat, anywhere a Text is rendered.
 *
 * WHERE ROLES COME FROM: the backend, reached through the launcher bridge.
 * We ask for the roles of the players currently visible and cache the
 * answer, so drawing a badge never blocks on the network. A player the
 * backend doesn't know (someone not using SolarClient) has no entry and so
 * gets no badge.
 *
 * Only the HIGHEST badge shows - the backend already collapses each player
 * to a single role, so there's nothing to prioritise here.
 *
 * The glyph codepoints below are literal \\uXXXX escapes on purpose (rather
 * than the characters themselves), so the source stays plain ASCII and the
 * meaning can't be lost to an editor or a build encoding. They match the
 * private-use codepoints declared in assets/solarclient/font/badges.json.
 */
public final class SolarBadges {
    private SolarBadges() {}

    public static final Identifier FONT = Identifier.of("solarclient", "badges");

    private static final Map<String, String> GLYPH = Map.of(
            "member",   "\uE000",
            "solar+",   "\uE001",
            "verified", "\uE002",
            "admin",    "\uE003",
            "owner",    "\uE004");

    // uuid (dashless, lowercase) -> role. Filled from launcher replies.
    private static final Map<String, String> ROLES = new ConcurrentHashMap<>();
    private static volatile long lastQuery = 0L;
    private static final long QUERY_INTERVAL_MS = 8_000L;

    /** The badge Text for a role, or null if that role has no badge. */
    public static Text badge(String role) {
        String glyph = role == null ? null : GLYPH.get(role);
        if (glyph == null) return null;
        return Text.literal(glyph).setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(FONT)));
    }

    /** Prepend the badge glyph and a space to a name if the player has a known role. */
    public static Text withBadge(UUID id, Text name) {
        if (id == null || name == null) return name;
        Text badge = badge(ROLES.get(key(id)));
        if (badge == null) return name;
        MutableText out = Text.empty();
        out.append(badge);
        out.append(Text.literal(" "));
        out.append(name);
        return out;
    }

    /**
     * Apply a query result. For every uuid we ASKED about, set its role from
     * the answer — or clear it if the answer has nothing for them. Clearing
     * is what makes a removed rank disappear promptly instead of lingering
     * from an earlier cache; without it, "/solarclient owner remove" would
     * leave the badge showing until the game restarted.
     */
    public static void applyQuery(Iterable<String> queried, Map<String, String> returned) {
        for (String raw : queried) {
            String k = norm(raw);
            String role = returned.get(raw);
            if (role == null) role = returned.get(k);
            if (role != null) ROLES.put(k, role);
            else ROLES.remove(k);
        }
    }

    private static String key(UUID id) { return norm(id.toString()); }
    private static String norm(String s) { return s.replace("-", "").toLowerCase(Locale.ROOT); }

    /** Returns true if the player has the exact role requested. */
    public static boolean hasRole(UUID id, String role) {
        if (id == null || role == null) return false;
        String r = ROLES.get(key(id));
        return role.equals(r);
    }

    public static void forceRefresh() {
        lastQuery = 0L;
        maybeRefresh();
    }

    /**
     * Ask the launcher for the roles of everyone currently on the tab list,
     * at most once every QUERY_INTERVAL_MS. Cheap to call every frame; it
     * self-throttles and does the network work off-thread.
     */
    public static void maybeRefresh() {
        long now = System.currentTimeMillis();
        if (now - lastQuery < QUERY_INTERVAL_MS) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return;
        lastQuery = now;

        JsonArray uuids = new JsonArray();
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            uuids.add(entry.getProfile().id().toString().replace("-", ""));
        }
        if (!uuids.isEmpty()) SolarLink.get().queryRoles(uuids);
    }
}
