package com.solarclient.mod.client.keybind;

import com.solarclient.mod.client.config.SolarConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * Applies and captures {@link KeybindPreset}s against Minecraft's live
 * keybindings.
 *
 * Design notes:
 *  - "All keybinds" means every binding the game knows about —
 *    client.options.allKeys — so a preset can cover vanilla movement,
 *    attack/use, hotbar, AND SolarClient's own keys in one shot. That's
 *    what makes a Builder-vs-PvP preset genuinely useful.
 *  - Applying resets every key to UNKNOWN first, then sets the ones the
 *    preset names. That way switching presets never leaves a stale bind
 *    from the previous preset hanging around.
 *  - After mutating binds we call the vanilla persistence + refresh path
 *    (options.write() + KeyBinding.updateKeysByCode()) so the change
 *    survives to options.txt and takes effect immediately, exactly like
 *    rebinding a key in the vanilla Controls screen does.
 */
public final class KeybindManager {
    private KeybindManager() {}

    /** All keybindings the game knows about (vanilla + every mod's). */
    public static KeyBinding[] allKeys() {
        return MinecraftClient.getInstance().options.allKeys;
    }

    /** Snapshot the current binds of every keybinding into the preset. */
    public static void captureInto(KeybindPreset preset) {
        preset.binds().clear();
        for (KeyBinding kb : allKeys()) {
            preset.binds().put(kb.getId(), kb.getBoundKeyTranslationKey());
        }
    }

    /**
     * Make the preset the live configuration: unbind everything, then set
     * exactly what the preset stored, then persist + refresh.
     */
    public static void apply(KeybindPreset preset) {
        MinecraftClient client = MinecraftClient.getInstance();
        int applied = 0, skipped = 0;
        for (KeyBinding kb : allKeys()) {
            String stored = preset.binds().get(kb.getId());
            try {
                InputUtil.Key key = stored != null
                        ? InputUtil.fromTranslationKey(stored)
                        : InputUtil.UNKNOWN_KEY;
                kb.setBoundKey(key);
                applied++;
            } catch (RuntimeException e) {
                // A bad/foreign entry (say, from a hand-edited file or a
                // removed mod's key) must not abort the rest of the set.
                skipped++;
            }
        }
        // Full vanilla refresh, in order: release anything held under the
        // old binds, rebuild the key -> binding dispatch registry (1.21.9+
        // routes ALL input through that static map, so this is the step
        // that makes the new binds actually fire), then re-sync pressed
        // state from the real keyboard, then persist to options.txt.
        KeyBinding.unpressAll();
        KeyBinding.updateKeysByCode();
        KeyBinding.updatePressedStates();
        client.options.write();

        SolarConfig cfg = SolarConfig.get();
        cfg.activePreset = preset.name;
        cfg.save();
        com.solarclient.mod.client.SolarClientModClient.LOGGER.info(
                "Applied keybind set '{}' ({} binds set, {} skipped)", preset.name, applied, skipped);
    }

    /** Create a preset from the current live binds and store it in config. */
    public static KeybindPreset createFromCurrent(String name, int color) {
        KeybindPreset preset = new KeybindPreset(name, color);
        captureInto(preset);
        SolarConfig cfg = SolarConfig.get();
        cfg.keybindPresets.add(preset);
        cfg.save();
        return preset;
    }

    public static void delete(KeybindPreset preset) {
        SolarConfig cfg = SolarConfig.get();
        cfg.keybindPresets.remove(preset);
        if (preset.name != null && preset.name.equals(cfg.activePreset)) cfg.activePreset = null;
        cfg.save();
    }

    /** Re-capture the current binds into an existing preset (an "update this preset" action). */
    public static void updateFromCurrent(KeybindPreset preset) {
        captureInto(preset);
        SolarConfig.get().save();
    }
}
