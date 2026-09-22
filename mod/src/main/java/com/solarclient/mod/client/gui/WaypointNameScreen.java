package com.solarclient.mod.client.gui;

import com.solarclient.mod.client.waypoint.WaypointColors;
import com.solarclient.mod.client.waypoint.WaypointManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Opens when you press the Create Waypoint key: type a name, pick a
 * color, save. The text field is a vanilla TextFieldWidget added through
 * addDrawableChild so vanilla routes keyboard/click input to it
 * internally — that sidesteps the reworked keyPressed/mouseClicked
 * signatures entirely.
 */
public class WaypointNameScreen extends SpaceTheme.SpaceScreen {
    private final String defaultName;
    private TextFieldWidget nameField;
    private String selectedColor = "purple";

    public WaypointNameScreen(String defaultName) {
        super(Text.literal("New Waypoint"));
        this.defaultName = defaultName;
    }

    @Override
    protected void init() {
        solarButtons.clear();

        // NOTE: TextFieldWidget's (TextRenderer, x, y, w, h, Text) constructor
        // is the current common form — if this line doesn't compile, check the
        // constructor list in your IDE/build error; older builds had an extra
        // "copyFrom" TextFieldWidget parameter variant.
        nameField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 70, 200, 20, Text.literal("Waypoint name"));
        nameField.setText(defaultName);
        this.addDrawableChild(nameField);
        // NOTE: if setInitialFocus doesn't compile, just delete this line —
        // you'll need to click the field before typing, nothing else breaks.
        this.setInitialFocus(nameField);

        SpaceTheme.SolarButton save = new SpaceTheme.SolarButton(this.width / 2 - 100, 150, 98, 22, "Save", null);
        save.bold = true;
        save.setAction(() -> {
            String name = nameField.getText().isBlank() ? defaultName : nameField.getText().trim();
            WaypointManager.create(name, selectedColor, this.client);
            this.client.setScreen(null);
        });
        solarButtons.add(save);
        solarButtons.add(new SpaceTheme.SolarButton(this.width / 2 + 2, 150, 98, 22, "Cancel", () -> this.client.setScreen(null)));
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("NEW WAYPOINT").formatted(Formatting.BOLD), this.width / 2, 40, 0xFFB98BFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Color", this.width / 2, 102, 0xFFA79FC4);

        // Color swatches — small squares, click to select, glow ring on the chosen one.
        int count = WaypointColors.NAMES.size();
        int chip = 16, gap = 8;
        int totalW = count * chip + (count - 1) * gap;
        int cx = this.width / 2 - totalW / 2;
        int cy = 116;
        for (String colorName : WaypointColors.NAMES) {
            int argb = WaypointColors.argb(colorName);
            boolean selected = colorName.equals(selectedColor);
            boolean hover = mouseX >= cx && mouseX <= cx + chip && mouseY >= cy && mouseY <= cy + chip;
            if (selected || hover) {
                ctx.fill(cx - 2, cy - 2, cx + chip + 2, cy + chip + 2, selected ? 0xFFFFFFFF : 0x80FFFFFF);
            }
            ctx.fill(cx, cy, cx + chip, cy + chip, argb);
            if (justPressed && hover) selectedColor = colorName;
            cx += chip + gap;
        }
    }
}
