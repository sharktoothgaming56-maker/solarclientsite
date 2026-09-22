package com.solarclient.mod.client.gui;

import java.util.List;

/**
 * The fixed colour palette shared by the button-colour picker and the
 * per-HUD colour picker. Kept deliberately small and named so the picker
 * UIs stay simple swatch grids — matching how the waypoint colours already
 * work in this project rather than introducing RGB sliders.
 */
public final class SolarColors {
    private SolarColors() {}

    /** An ARGB colour plus a short display name, for swatch tooltips/labels. */
    public record Swatch(String name, int argb) {}

    public static final List<Swatch> PALETTE = List.of(
            new Swatch("Solar",  0xFFB98BFF), // default accent
            new Swatch("Violet",  0xFF9D6BFF),
            new Swatch("Cyan",    0xFF5EE6D9),
            new Swatch("Sky",     0xFF6BB8FF),
            new Swatch("Green",   0xFF5EEB8F),
            new Swatch("Lime",    0xFFB6EB5E),
            new Swatch("Yellow",  0xFFF5D96B),
            new Swatch("Orange",  0xFFFFA15E),
            new Swatch("Red",     0xFFE0625A),
            new Swatch("Pink",    0xFFFF8FC7),
            new Swatch("White",   0xFFEAE6F7),
            new Swatch("Slate",   0xFF8A82A8)
    );

    /** Nearest palette index to an ARGB value, so a picker can highlight the current choice. */
    public static int indexOf(int argb) {
        for (int i = 0; i < PALETTE.size(); i++) {
            if (PALETTE.get(i).argb() == argb) return i;
        }
        return -1;
    }
}
