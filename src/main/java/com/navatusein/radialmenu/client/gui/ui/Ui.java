package com.navatusein.radialmenu.client.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * One place for every measurement and colour the mod's screens use.
 *
 * <p>
 * Each screen used to carry its own hardcoded offsets, which is why the bottom buttons jumped as you moved between
 * them and the gaps never matched. Anything positional belongs here instead, so "consistent" is the default rather
 * than something to remember.
 */
public final class Ui {

    /** Height of a button or a single-line control. */
    public static final int ROW = 20;

    /** Vertical gap between rows. Add to {@link #ROW} for the step between two stacked controls. */
    public static final int GAP = 4;

    public static final int STEP = ROW + GAP;

    /** Padding inside a framed panel. */
    public static final int PAD = 8;

    /** Distance from the bottom of the screen to the bottom of the button bar. Fixed for every screen. */
    public static final int BOTTOM_MARGIN = 8;

    /** Width of the label column in a label-and-control row. */
    public static final int LABEL_WIDTH = 104;

    public static final int PANEL_BG = 0xE8101010;
    public static final int PANEL_BORDER = 0xFF4A4A4A;
    public static final int LIST_BG = 0xC0000000;
    public static final int ROW_HOVER = 0x30FFFFFF;
    public static final int ROW_SELECTED = 0x50FFFFFF;

    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_MUTED = 0xFF9A9A9A;
    public static final int TEXT_HEADER = 0xFFFFAA00;
    public static final int TEXT_ACTIVE = 0xFF55FF55;
    public static final int TEXT_ERROR = 0xFFFF5555;

    private Ui() {}

    public static FontRenderer font() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    /** y of the button bar, measured from the bottom so every screen agrees. */
    public static int bottomBarY(int screenHeight) {
        return screenHeight - BOTTOM_MARGIN - ROW;
    }

    /** A filled rectangle with a one-pixel border, the frame every panel and list sits in. */
    public static void frame(int left, int top, int right, int bottom, int fill, int border) {
        Gui.drawRect(left, top, right, top + 1, border);
        Gui.drawRect(left, bottom - 1, right, bottom, border);
        Gui.drawRect(left, top + 1, left + 1, bottom - 1, border);
        Gui.drawRect(right - 1, top + 1, right, bottom - 1, border);
        Gui.drawRect(left + 1, top + 1, right - 1, bottom - 1, fill);
    }

    public static void panel(int left, int top, int right, int bottom) {
        frame(left, top, right, bottom, PANEL_BG, PANEL_BORDER);
    }

    public static void list(int left, int top, int right, int bottom) {
        frame(left, top, right, bottom, LIST_BG, PANEL_BORDER);
    }

    /** A section heading, in the accent colour, with a rule under it. */
    public static void sectionHeader(String text, int left, int top, int right) {
        font().drawString(text, left, top, TEXT_HEADER);
        Gui.drawRect(left, top + 10, right, top + 11, 0x40FFFFFF);
    }

    /** Right-aligned label for the control that sits beside it, vertically centred against a {@link #ROW}. */
    public static void rowLabel(String text, int left, int rowTop) {
        FontRenderer font = font();
        font.drawString(text, left + LABEL_WIDTH - font.getStringWidth(text), rowTop + (ROW - 8) / 2, TEXT_MUTED);
    }

    /** Shortens text with an ellipsis so it cannot run past the width it was given. */
    public static String fit(String text, int width) {
        FontRenderer font = font();
        if (text == null || font.getStringWidth(text) <= width) {
            return text;
        }
        String trimmed = font.trimStringToWidth(text, width - font.getStringWidth("..."));
        return trimmed + "...";
    }
}
