package com.navatusein.radialmenu.client.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

/**
 * A small square button carrying a drawn mark rather than a letter.
 *
 * <p>
 * Written because the letter could not be centred, and not for want of trying. Vanilla centres a label by its
 * <em>advance</em> width, which in Minecraft's font is the ink plus a pixel of spacing: {@code getStringWidth("x")} is
 * 6 for a glyph that is 5 pixels across. On a 20-pixel button that puts the ink at 7 pixels from the left and 8 from
 * the right. Vertically it lands the same way, because the glyph sits on a baseline rather than in the middle of its
 * cell.
 *
 * <p>
 * Neither is fixable by nudging: 20 minus 5 is odd, so a five-pixel mark has no centred position on a twenty-pixel
 * button at all. A drawn mark can be given an even width, and then it is centred exactly - on both axes, at any button
 * size, with no font in the way.
 */
public class UiIconButton extends GuiButton {

    public enum Icon {
        /** A diagonal cross, for clearing and removing. */
        CROSS,
        /** A triangle pointing up, for moving an entry earlier. */
        UP,
        /** A triangle pointing down. */
        DOWN,
        /** Two offset squares, for making a copy of something. */
        COPY,
        /** A triangle pointing right, for sending something somewhere else. */
        RIGHT
    }

    /** Across the mark, in pixels. Even on purpose: an odd size cannot sit centred on an even button. */
    private static final int SIZE = 6;

    private final Icon icon;

    public UiIconButton(int id, int x, int y, int width, int height, Icon icon) {
        super(id, x, y, width, height, "");
        this.icon = icon;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        // The vanilla frame and its hover state, with no label: the mark goes on afterwards.
        super.drawButton(mc, mouseX, mouseY);
        if (!this.visible) {
            return;
        }

        int colour = !this.enabled ? 0xFF8A8A8A : this.field_146123_n ? 0xFFFFFFA0 : Ui.TEXT;
        int centerX = this.xPosition + this.width / 2;
        int centerY = this.yPosition + this.height / 2;

        switch (icon) {
            case CROSS:
                drawCross(centerX, centerY, colour);
                break;
            case UP:
                drawTriangle(centerX, centerY, colour, true);
                break;
            case DOWN:
                drawTriangle(centerX, centerY, colour, false);
                break;
            case COPY:
                drawCopy(centerX, centerY, colour);
                break;
            case RIGHT:
                drawRightTriangle(centerX, centerY, colour);
                break;
            default:
                break;
        }
    }

    /**
     * A solid triangle, rather than the caret and the letter v that stood in for one.
     *
     * <p>
     * Those were two different glyphs doing one job: {@code ^} hangs at the top of its cell and {@code v} sits on the
     * baseline, so the pair never looked like a matched set however they were placed. Drawn, they are the same shape
     * mirrored, and both are centred.
     */
    private static void drawTriangle(int centerX, int centerY, int colour, boolean pointingUp) {
        int rows = SIZE / 2;
        for (int row = 0; row < rows; row++) {
            // Two pixels wider per row down, so the base is as wide as the mark and the tip is two pixels across.
            int halfWidth = row + 1;
            // Both directions occupy the same band, so an up and a down button side by side line up exactly.
            int y = pointingUp ? centerY - rows / 2 + row : centerY + rows / 2 - row;
            Gui.drawRect(centerX - halfWidth, y, centerX + halfWidth, y + 1, colour);
        }
    }

    /**
     * The same triangle lying on its side.
     *
     * <p>
     * Written out rather than folded into {@link #drawTriangle}: that one keeps an up and a down button in the same
     * band so a pair lines up, which is an argument about rows and has nothing to say about a sideways mark.
     */
    private static void drawRightTriangle(int centerX, int centerY, int colour) {
        int columns = SIZE / 2;
        for (int column = 0; column < columns; column++) {
            int halfHeight = columns - column;
            int x = centerX - columns / 2 + column;
            Gui.drawRect(x, centerY - halfHeight, x + 1, centerY + halfHeight, colour);
        }
    }

    /**
     * Two squares, one behind the other.
     *
     * <p>
     * Outlines rather than fills, and the back one drawn first: a copy mark is only legible if the two shapes read as
     * two, which they stop doing the moment either is solid at this size.
     */
    private static void drawCopy(int centerX, int centerY, int colour) {
        int side = SIZE - 1;
        outline(centerX - side / 2 - 1, centerY - side / 2 - 1, side, colour);
        outline(centerX - side / 2 + 2, centerY - side / 2 + 2, side, colour);
    }

    private static void outline(int x, int y, int side, int colour) {
        Gui.drawRect(x, y, x + side, y + 1, colour);
        Gui.drawRect(x, y + side - 1, x + side, y + side, colour);
        Gui.drawRect(x, y, x + 1, y + side, colour);
        Gui.drawRect(x + side - 1, y, x + side, y + side, colour);
    }

    /**
     * Two diagonals meeting in the middle.
     *
     * <p>
     * Drawn from the centre outwards rather than from a corner, so the mark is centred by construction instead of by
     * arithmetic that has to be right.
     */
    private static void drawCross(int centerX, int centerY, int colour) {
        int half = SIZE / 2;
        for (int i = 0; i < SIZE; i++) {
            int x = centerX - half + i;
            int down = centerY - half + i;
            int up = centerY + half - 1 - i;
            Gui.drawRect(x, down, x + 1, down + 1, colour);
            Gui.drawRect(x, up, x + 1, up + 1, colour);
        }
    }
}
