package com.navatusein.radialmenu.core.model;

import com.google.gson.annotations.SerializedName;

/**
 * How a menu spreads its entries around the ring.
 *
 * <p>
 * {@link Mode#FIXED} keeps every entry at a constant angle no matter how many neighbours it has, which is what makes
 * muscle memory work; empty positions simply render as gaps. {@link Mode#DYNAMIC} divides the circle by the number of
 * entries, which looks tidier but shifts every angle whenever an entry is added.
 */
public class SlotLayout {

    public enum Mode {

        @SerializedName("fixed")
        FIXED,

        @SerializedName("dynamic")
        DYNAMIC
    }

    /**
     * How this menu arrives on screen when it is opened from a parent.
     *
     * <p>
     * {@link #REPLACE} is the wheel this mod has always drawn: the submenu takes the parent's place, and going back
     * brings the parent back. {@link #INLINE} unfolds it as a ring outside the parent, which stays where it is -
     * so the entry it came from is still on screen, and several branches can be open at once.
     *
     * <p>
     * A property of the menu rather than of the mod's config, because it is about the shape of one particular wheel
     * and travels with the profile that describes it.
     */
    public enum Opening {

        @SerializedName("replace")
        REPLACE,

        @SerializedName("inline")
        INLINE
    }

    public static final int MIN_SLOTS = 2;
    public static final int MAX_SLOTS = 24;
    public static final int DEFAULT_SLOTS = 8;

    public Mode mode = Mode.DYNAMIC;

    /** Only meaningful for {@link Mode#FIXED}. */
    public int slots = DEFAULT_SLOTS;

    /**
     * How this menu opens. Only meaningful for a submenu - the root has no parent to open it from, and an inline
     * root would have nothing to unfold out of.
     */
    public Opening opening = Opening.REPLACE;

    public static SlotLayout fixed(int slots) {
        SlotLayout layout = new SlotLayout();
        layout.mode = Mode.FIXED;
        layout.slots = slots;
        return layout;
    }

    public static SlotLayout dynamic() {
        return new SlotLayout();
    }

    /** Number of sectors to draw for a menu holding {@code childCount} entries. */
    public int slotCount(int childCount) {
        return mode == Mode.FIXED ? clampSlots(slots) : childCount;
    }

    public static int clampSlots(int value) {
        return Math.max(MIN_SLOTS, Math.min(MAX_SLOTS, value));
    }

    /** Whether a submenu with this layout unfolds around its parent instead of taking its place. */
    public boolean isInline() {
        return opening == Opening.INLINE;
    }

    /** Fills in anything a hand-edited file left out or out of range. */
    public void normalize() {
        if (mode == null) {
            mode = Mode.DYNAMIC;
        }
        if (opening == null) {
            opening = Opening.REPLACE;
        }
        slots = clampSlots(slots);
    }
}
