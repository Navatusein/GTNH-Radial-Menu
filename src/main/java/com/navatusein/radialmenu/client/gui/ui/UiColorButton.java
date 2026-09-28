package com.navatusein.radialmenu.client.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import com.navatusein.radialmenu.core.Colors;

/**
 * A button that shows a colour: the value beside a square of it.
 *
 * <p>
 * The swatch is the point. A row of hex codes is a row of hex codes - the difference between {@code #CC4A90D9} and
 * {@code #CC4AD990} is one character and a completely different wheel, and nobody reads it off the text.
 *
 * <p>
 * What it shows is read from the model as it draws, not copied in when the button is built. Those are the same thing
 * right up until something changes several colours at once - picking an accent does - and then a button built before
 * the change goes on showing what it was told. Reading live costs nothing and cannot be wrong.
 */
public class UiColorButton extends GuiButton {

    /** Where the value comes from, read afresh on every frame. */
    public interface Value {

        /** The stored colour, or blank when this one inherits. */
        String get();
    }

    private static final int SWATCH = 12;

    private final Value value;

    /** What the colour actually is when nothing is stored here, so the swatch shows what the player sees. */
    private final int inherited;

    /** What to write in place of a value when nothing is stored - "Inherit", or wherever it comes from instead. */
    private final String blankLabel;

    public UiColorButton(int id, int x, int y, int width, Value value, int inherited, String blankLabel) {
        // No display string: the label is drawn here, left of centre and beside the swatch, rather than centred
        // underneath it.
        super(id, x, y, width, Ui.ROW, "");
        this.value = value;
        this.inherited = inherited;
        this.blankLabel = blankLabel;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        super.drawButton(mc, mouseX, mouseY);
        if (!this.visible) {
            return;
        }

        String current = value.get();
        boolean blank = current == null || current.trim()
            .isEmpty();
        int shown = blank ? inherited : Colors.over(current, inherited);

        int top = this.yPosition + (this.height - SWATCH) / 2;
        Ui.frame(
            this.xPosition + 4,
            top,
            this.xPosition + 4 + SWATCH,
            top + SWATCH,
            0xFF000000 | (shown & 0x00FFFFFF),
            0xFF000000);

        String text = blank ? blankLabel : current;
        mc.fontRenderer.drawString(
            Ui.fit(text, this.width - SWATCH - 12),
            this.xPosition + SWATCH + 8,
            this.yPosition + (this.height - 8) / 2,
            this.enabled ? Ui.TEXT : Ui.TEXT_MUTED);
    }
}
