package com.solarclient.mod.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Every keybind here shows up under its own "SolarClient" heading in
 * Options > Controls > Key Binds, the same way Lunar Client's keys get
 * their own section — Fabric does this automatically based on the
 * category, no custom screen needed.
 *
 * Deliberately left out (per the brief):
 *   - Emote Wheel / Spray Snap / Spray Wheel  (emote + spray features, out of scope)
 *   - Skip Node / Reset Counts                 (pathing/node features, out of scope)
 *   - Sneak / Sprint / Third Person Key        (already exist as vanilla keybinds)
 *
 * Each binding's actual behavior is a stub for now — Phase 1 is the
 * foundation (mod loads, category shows up, keys are rebindable and
 * persist to options.txt like any vanilla keybind). Waypoints, the Mod
 * Menu screen, freelook camera behavior, etc. land in their own phases.
 *
 * As of 1.21.9+, Minecraft's KeyBinding category is a proper
 * KeyBinding.Category object (built from an Identifier), not a plain
 * translation-key string like it used to be — that's what
 * KeyBinding.Category.create(...) below is for.
 */
public final class SolarKeybinds {
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("solarclient", "main"));

    public static final KeyBinding CREATE_WAYPOINT = register("key.solarclient.create_waypoint", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyBinding FORWARD_VIEW = register("key.solarclient.forward_view", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyBinding FREELOOK = register("key.solarclient.freelook", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyBinding MOD_MENU = register("key.solarclient.mod_menu", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyBinding PARTY_MENU = register("key.solarclient.party_menu", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyBinding RESET_COUNTS = register("key.solarclient.reset_counts", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyBinding SEND_COORDINATES = register("key.solarclient.send_coordinates", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyBinding TOGGLE_CHAT_VISIBILITY = register("key.solarclient.toggle_chat_visibility", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyBinding TOGGLE_WAYPOINT_DISPLAY = register("key.solarclient.toggle_waypoint_display", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyBinding WAYPOINT_MENU = register("key.solarclient.waypoint_menu", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyBinding ZOOM = register("key.solarclient.zoom", GLFW.GLFW_KEY_UNKNOWN);

    private SolarKeybinds() {}

    private static KeyBinding register(String translationKey, int defaultGlfwKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                translationKey,
                InputUtil.Type.KEYSYM,
                defaultGlfwKey,
                CATEGORY
        ));
    }

    /** Call once from the client entrypoint so the static initializers above actually run. */
    public static void init() {
        SolarClientModClient.LOGGER.info("Registered {} SolarClient keybinds", 11);
    }
}
