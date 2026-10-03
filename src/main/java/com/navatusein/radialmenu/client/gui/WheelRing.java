package com.navatusein.radialmenu.client.gui;

import com.navatusein.radialmenu.config.WheelConfig;
import com.navatusein.radialmenu.core.geometry.RadialGeometry;
import com.navatusein.radialmenu.core.model.MenuNode;

/**
 * One ring on screen: a menu, the arc it covers and the band of radii it is drawn in.
 *
 * <p>
 * The wheel used to be one menu filling one circle, so a sector was described by its index and the slot count alone.
 * An inline submenu unfolds outside its parent, which stays where it is, so there can be several menus drawn at once
 * at different radii and over different stretches of the circle. Everything that draws or hit-tests a sector asks
 * this instead of assuming the whole circle - including the middle ring, which is simply an arc of 360 degrees
 * starting half a sector before the top.
 *
 * <p>
 * Immutable and rebuilt from the tree each frame: the arcs depend on which branches are open and on how many entries
 * each holds, and a cached copy of that is one more thing that can disagree with the menu it came from.
 */
public final class WheelRing {

    /** The menu this ring draws. */
    public final MenuNode menu;

    /** How far out from the middle ring this one is: 0 for the menu at the centre. */
    public final int level;

    public final double startAngle;

    public final double span;

    public final double inner;

    public final double outer;

    public final int slotCount;

    /** Index of the ring this one unfolded out of, or -1 for the one at the centre. */
    public final int parent;

    /** Sector of that parent ring the entry sits in, or -1. */
    public final int parentSlot;

    public WheelRing(MenuNode menu, int level, double startAngle, double span, int slotCount, int parent,
        int parentSlot) {
        this.menu = menu;
        this.level = level;
        this.startAngle = startAngle;
        this.span = span;
        this.slotCount = slotCount;
        this.parent = parent;
        this.parentSlot = parentSlot;
        this.inner = innerRadius(level);
        this.outer = innerRadius(level) + thickness();
    }

    /** The menu at the centre: a full circle, with its first sector straddling the top as it always has. */
    public static WheelRing root(MenuNode menu, int slotCount) {
        return new WheelRing(menu, 0, -RadialGeometry.sectorSpan(slotCount) / 2.0, 360.0, slotCount, -1, -1);
    }

    /** How wide every ring is. Each level is the same thickness, so a wheel four deep is still one shape. */
    public static double thickness() {
        return WheelConfig.outerRadius - WheelConfig.effectiveInnerRadius();
    }

    public static double innerRadius(int level) {
        return WheelConfig.effectiveInnerRadius() + level * (thickness() + WheelConfig.inlineRingSpacing);
    }

    public static double outerRadius(int level) {
        return innerRadius(level) + thickness();
    }

    /** Where an icon sits: midway between the two radii, as it always has. */
    public double midRadius() {
        return (inner + outer) / 2.0;
    }

    public double slotSpan() {
        return RadialGeometry.arcSlotSpan(span, slotCount);
    }

    public double slotStart(int slot) {
        return RadialGeometry.arcSlotStart(slot, slotCount, startAngle, span);
    }

    public double slotCenter(int slot) {
        return RadialGeometry.arcSlotCenter(slot, slotCount, startAngle, span);
    }

    /** Sector of this ring an angle falls in, or {@link RadialGeometry#NO_SLOT} outside its arc. */
    public int slotAt(double angle) {
        return RadialGeometry.slotInArc(angle, startAngle, span, slotCount);
    }

    public MenuNode childAt(int slot) {
        return menu.childAt(slot);
    }
}
