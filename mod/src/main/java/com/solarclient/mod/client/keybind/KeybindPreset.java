package com.solarclient.mod.client.keybind;

import java.util.HashMap;
import java.util.Map;

/**
 * A named, colour-tagged snapshot of keybinds. The player can build a
 * "Builder" preset with all their building keys and a "PvP" preset with
 * their combat keys, then switch between them from the Keybinds screen.
 *
 * Storage is intentionally string-to-string: keybind translation key
 * (e.g. "key.attack", "key.solarclient.zoom") mapped to the bound key's
 * translation key (e.g. "key.keyboard.f", "key.mouse.left"). Storing the
 * key's *translation key* rather than a raw GLFW code means it round-trips
 * cleanly through JSON and is restored with InputUtil.fromTranslationKey,
 * the inverse used by KeybindManager when applying a preset.
 */
public class KeybindPreset {
    public String name;
    /** ARGB colour tag shown on the preset's row/button. */
    public int color;
    /** keybind translation key -> bound key translation key */
    public Map<String, String> binds = new HashMap<>();

    public KeybindPreset() {} // for Gson

    public KeybindPreset(String name, int color) {
        this.name = name;
        this.color = color;
    }

    /** Gson can leave this null on a hand-edited/old file — never hand back null. */
    public Map<String, String> binds() {
        if (binds == null) binds = new HashMap<>();
        return binds;
    }
}
