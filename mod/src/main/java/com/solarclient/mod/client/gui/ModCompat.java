package com.solarclient.mod.client.gui;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Carries other mods' buttons onto our custom Title and Game Menu screens,
 * and puts them WHERE THE MOD MEANT THEM TO GO.
 *
 * Researched behaviour this is built around:
 *
 *  - MOD MENU adds its "Mods" button straight into the centre button
 *    column, immediately after Realms, at 200x20 — and shifts vanilla's
 *    own buttons by half a row each way to open the gap. Its other styles
 *    put it beside the column (a 98-wide half button) or as a 20x20 icon
 *    off to the right of it. So the column is where a menu button belongs,
 *    and the rest of the menu is expected to move to accommodate it.
 *
 *  - ESSENTIAL does NOT add buttons to vanilla's menu. It replaces the
 *    title and pause screens with its own redesigned ones, drawn by its own
 *    UI toolkit. The `<essential_social>`-style entries that used to end up
 *    in our menu were internal anchors for that UI, never buttons. They are
 *    filtered out (see {@link #looksLikePlaceholder}). Because Essential
 *    swaps the same screens we do, only one of the two can win — that's
 *    what the "Custom Menus" toggle in the Solar Menu is for.
 *
 * So mod buttons are sorted into two kinds:
 *
 *  COLUMN — a normal menu button in the centre stack. It gets folded into
 *  our own button list at the matching slot, drawn in the Solar style, and
 *  our buttons move to make room, exactly like vanilla's do for Mod Menu.
 *
 *  FREE — icon buttons and anything parked off to the side. These are left
 *  at the coordinates their mod chose, untouched, because that position is
 *  usually deliberate.
 */
public final class ModCompat {
    private ModCompat() {}

    /**
     * A harvested widget plus the geometry it had on the vanilla screen.
     * The original numbers must be captured BEFORE we move anything —
     * otherwise a window resize (which re-runs init on already-moved
     * widgets) would read back our own layout instead of the mod's intent.
     */
    public record ModButton(ClickableWidget widget, int originX, int originY,
                            int originWidth, int originHeight, boolean column) {}

    /**
     * Vanilla title screen button labels.
     *
     * The Language and Accessibility icon buttons changed their message key
     * from "narrator.button.*" to "options.*" at some point upstream (they
     * now build via AccessibilityOnboardingButtons.createLanguageButton /
     * createAccessibilityButton, which label with "options.language" and
     * either "options.accessibility" or the onboarding variant). Both old
     * and new keys are listed so this keeps working across that drift —
     * without a match here these two buttons fall through the filter as
     * "extras" and get free-floated onto our screen at their harvested
     * vanilla coordinates, landing in the middle of our button column
     * looking exactly like stray, broken vanilla widgets.
     */
    private static final String[] TITLE_KEYS = {
            "menu.singleplayer", "menu.multiplayer", "menu.online", "menu.options", "menu.quit",
            "menu.playdemo", "menu.resetdemo",
            "narrator.button.language", "narrator.button.accessibility",
            "options.language", "options.accessibility", "accessibility.onboarding.accessibility.button",
            "title.credits",
    };

    /** Vanilla pause screen button labels. */
    private static final String[] PAUSE_KEYS = {
            "menu.returnToGame", "gui.advancements", "gui.stats", "menu.sendFeedback", "menu.reportBugs",
            "menu.options", "menu.shareToLan", "menu.playerReporting", "menu.returnToMenu",
            "menu.disconnect", "menu.server_links", "menu.quit", "menu.paused", "menu.game",
    };

    /**
     * True for labels that are internal identifiers rather than player-facing
     * text: "&lt;essential_social&gt;", "somemod.button.thing". Mods that draw
     * their own UI register hidden markers like these as anchors; showing
     * them produced a column of nonsense entries that did nothing.
     */
    private static boolean looksLikePlaceholder(String label) {
        if (label.length() < 2) return true;
        if (label.startsWith("<") && label.endsWith(">")) return true;
        return label.matches("[a-z0-9]+([._-][a-z0-9]+)+");
    }

    private static Set<String> labelsOf(String[] keys) {
        Set<String> out = new HashSet<>();
        for (String key : keys) out.add(Text.translatable(key).getString().trim());
        return out;
    }

    /**
     * Widgets on {@code screen} that vanilla didn't put there, each tagged
     * with where it sat and whether it belongs in the button column.
     */
    public static List<ModButton> harvest(Screen screen, boolean pause) {
        LinkedHashSet<ClickableWidget> candidates = new LinkedHashSet<>();
        try {
            candidates.addAll(Screens.getButtons(screen));
        } catch (RuntimeException ignored) {
            // A compatibility helper must never take the menu down with it.
        }
        for (Element element : screen.children()) {
            if (element instanceof ClickableWidget widget) candidates.add(widget);
        }

        Set<String> vanillaLabels = labelsOf(pause ? PAUSE_KEYS : TITLE_KEYS);
        int screenCenter = screen.width / 2;
        List<ModButton> extras = new ArrayList<>();

        for (ClickableWidget widget : candidates) {
            if (widget instanceof TextWidget) continue; // splash/version text, not a control
            if (!widget.visible || !widget.active) continue; // hidden = scenery or an overlay anchor

            boolean vanillaClass = widget.getClass().getName().startsWith("net.minecraft.");
            String label = widget.getMessage() == null ? "" : widget.getMessage().getString().trim();

            if (!label.isEmpty() && looksLikePlaceholder(label)) continue;

            if (vanillaClass) {
                if (label.isEmpty() || vanillaLabels.contains(label)) continue;
                if (label.contains("Mojang")) continue; // the copyright line is a button too
            }

            // Column test: a proper menu button is wide and centred, like Mod
            // Menu's 200x20 entry. Icons (20x20) and side panels fail both
            // halves and keep their own spot.
            int widgetCenter = widget.getX() + widget.getWidth() / 2;
            boolean column = widget.getWidth() >= 90 && Math.abs(widgetCenter - screenCenter) <= 60
                    && !label.isEmpty();

            extras.add(new ModButton(widget, widget.getX(), widget.getY(),
                    widget.getWidth(), widget.getHeight(), column));
        }

        // Top-to-bottom, so several mods' buttons keep their relative order.
        extras.sort(Comparator.comparingInt(ModButton::originY));
        return extras;
    }

    /** Just the ones that belong in our centre stack. */
    public static List<ModButton> columnButtons(List<ModButton> all) {
        List<ModButton> out = new ArrayList<>();
        for (ModButton mb : all) if (mb.column()) out.add(mb);
        return out;
    }

    /**
     * Fold one mod button into our stack: park the real widget exactly under
     * the slot and make it invisible-but-clickable, then hand back a Solar
     * button to draw in its place.
     *
     * Zero alpha rather than {@code visible = false} is deliberate — an
     * invisible widget receives no input, whereas a transparent one still
     * gets the click through vanilla's normal dispatch. So the mod's own
     * action runs untouched: no reflection, no click forwarding. Anything
     * the widget paints with custom code (Mod Menu's update dot) still shows
     * on top, which reads as a badge on our button.
     */
    public static SpaceTheme.SolarButton fold(ModButton mb, int x, int y, int width, int height) {
        mb.widget().setDimensionsAndPosition(width, height, x, y);
        mb.widget().setAlpha(0f);
        String label = mb.widget().getMessage() == null ? "" : mb.widget().getMessage().getString();
        // No-op action: the transparent widget underneath does the work.
        return new SpaceTheme.SolarButton(x, y, width, height, label, () -> {});
    }

    /**
     * Put non-column widgets back exactly where their mod placed them,
     * nudged inside the screen only if they'd otherwise be unreachable.
     */
    public static void placeFree(List<ModButton> all, int screenWidth, int screenHeight) {
        for (ModButton mb : all) {
            if (mb.column()) continue;
            int x = Math.max(0, Math.min(mb.originX(), screenWidth - mb.originWidth()));
            int y = Math.max(0, Math.min(mb.originY(), screenHeight - mb.originHeight()));
            mb.widget().setDimensionsAndPosition(mb.originWidth(), mb.originHeight(), x, y);
        }
    }
}
