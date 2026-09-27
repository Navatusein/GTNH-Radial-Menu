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

    public static final int MIN_SLOTS = 2;
    public static final int MAX_SLOTS = 24;
    public static final int DEFAULT_SLOTS = 8;

    public Mode mode = Mode.DYNAMIC;

    /** Only meaningful for {@link Mode#FIXED}. */
    public int slots = DEFAULT_SLOTS;

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

    /** Fills in anything a hand-edited file left out or out of range. */
    public void normalize() {
        if (mode == null) {
            mode = Mode.DYNAMIC;
        }
        slots = clampSlots(slots);
    }
}
