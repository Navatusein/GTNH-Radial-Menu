package com.navatusein.radialmenu.client.gui.ui;

import net.minecraft.client.gui.Gui;

/**
 * Dragging a row of a list into a different place.
 *
 * <p>
 * The gesture only, with no idea what a row contains: the screen says where its rows are and what to do with the
 * result, which is what lets the menu entries and a chain's steps share one implementation of the fiddly half.
 *
 * <p>
 * <b>Why a grip rather than the whole row.</b> {@code GuiScreen.mouseClicked} fires {@code actionPerformed} on the
 * press, not on the release, so a row that is a button cannot be dragged - it opens its editor the moment you take hold
 * of it. The grip is not a button, so pressing it does nothing vanilla notices, and the row stays clickable for what it
 * was always for.
 */
public final class UiDragReorder {

    /** Width of the grip column. Narrow enough not to crowd the row, wide enough to aim at. */
    public static final int GRIP = 8;

    /** How far the pointer moves before a press becomes a drag, so a shaky click stays a click. */
    private static final int THRESHOLD = 3;

    /** Where a screen's rows are. Asked rather than worked out here, so the layout has one owner. */
    public interface Rows {

        int count();

        /** Screen y of a row's top edge. */
        int top(int row);

        int height();
    }

    private int grabbed = -1;
    private int startY;
    private int pointerY;
    private boolean dragging;

    public boolean isDragging() {
        return dragging;
    }

    public int grabbedRow() {
        return grabbed;
    }

    /**
     * Takes the press if it landed on a grip.
     *
     * @return true when the gesture has it, and the screen should not pass the click on
     */
    public boolean press(int mouseX, int mouseY, int gripLeft, Rows rows) {
        if (mouseX < gripLeft || mouseX >= gripLeft + GRIP) {
            return false;
        }
        for (int row = 0; row < rows.count(); row++) {
            int top = rows.top(row);
            if (mouseY >= top && mouseY < top + rows.height()) {
                grabbed = row;
                startY = mouseY;
                pointerY = mouseY;
                dragging = false;
                return true;
            }
        }
        return false;
    }

    public void moveTo(int mouseY) {
        if (grabbed < 0) {
            return;
        }
        pointerY = mouseY;
        if (Math.abs(mouseY - startY) >= THRESHOLD) {
            dragging = true;
        }
    }

    /** How far the pointer is above or below the list, for a screen that wants to scroll towards it. */
    public int overshoot(Rows rows) {
        if (!dragging || rows.count() == 0) {
            return 0;
        }
        int top = rows.top(0);
        int bottom = rows.top(rows.count() - 1) + rows.height();
        if (pointerY < top) {
            return pointerY - top;
        }
        return pointerY > bottom ? pointerY - bottom : 0;
    }

    /**
     * Ends the gesture.
     *
     * @return the row the dragged one should become, or -1 if this was a click rather than a drag
     */
    public int release(Rows rows) {
        int target = -1;
        if (dragging && grabbed >= 0) {
            int insertion = insertionIndex(rows);
            // Removing the row first shifts everything after it up, so an insertion below its old place lands one
            // short. This is the one place that correction belongs; the screens just move a row to an index.
            target = insertion > grabbed ? insertion - 1 : insertion;
        }
        grabbed = -1;
        dragging = false;
        return target;
    }

    public void cancel() {
        grabbed = -1;
        dragging = false;
    }

    /** Where the row would be dropped, counted in gaps between rows: 0 is above the first, count is below the last. */
    public int insertionIndex(Rows rows) {
        int index = 0;
        while (index < rows.count() && pointerY > rows.top(index) + rows.height() / 2) {
            index++;
        }
        return index;
    }

    /** The line showing where the row will land. Drawn with the content, so a scrolled list clips it like a row. */
    public void drawInsertion(Rows rows, int left, int right) {
        if (!dragging || rows.count() == 0) {
            return;
        }
        int index = insertionIndex(rows);
        int y = index < rows.count() ? rows.top(index) - 2 : rows.top(rows.count() - 1) + rows.height();
        Gui.drawRect(left, y, right, y + 1, Ui.TEXT_HEADER);
    }

    /**
     * Six dots in two columns: the shape that reads as "take hold of this" without a word of explanation.
     *
     * <p>
     * Drawn from the row's middle outwards, like the other marks, so it sits centred whatever the row height is.
     */
    public static void drawGrip(int left, int top, int height, boolean hot) {
        int colour = hot ? Ui.TEXT : Ui.CODE_GUTTER;
        for (int row = 0; row < 3; row++) {
            int y = top + height / 2 - 4 + row * 3;
            Gui.drawRect(left + 1, y, left + 3, y + 2, colour);
            Gui.drawRect(left + 5, y, left + 7, y + 2, colour);
        }
    }
}
