package com.navatusein.radialmenu.core.geometry;

/**
 * Angle/slot math for the wheel. Pure functions, no Minecraft, no state - so it is unit testable and survives a port to
 * another Minecraft version untouched.
 *
 * <p>
 * Convention: angles are degrees in [0, 360), zero points straight up and grows clockwise. That matches screen
 * coordinates directly (x grows right, y grows down), which is why none of the "magic" offsets MineMenu needed are
 * present here.
 */
public final class RadialGeometry {

    /** Nothing is selected while the cursor sits in the middle of the wheel. */
    public static final int NO_SLOT = -1;

    private RadialGeometry() {}

    public static double normalizeDegrees(double degrees) {
        double d = degrees % 360.0;
        return d < 0.0 ? d + 360.0 : d;
    }

    /** Angle from the wheel centre to a point, in the convention described above. */
    public static double angleTo(double centerX, double centerY, double x, double y) {
        return normalizeDegrees(Math.toDegrees(Math.atan2(x - centerX, -(y - centerY))));
    }

    /**
     * Slot index whose sector contains the given angle. Sector {@code i} is centred on
     * {@link #slotCenterAngle}, so the first slot straddles the top of the wheel rather than starting there.
     */
    public static int slotAtAngle(double angleDegrees, int slotCount, double rotationDegrees) {
        if (slotCount <= 0) {
            return NO_SLOT;
        }
        double step = 360.0 / slotCount;
        double shifted = normalizeDegrees(angleDegrees - rotationDegrees + step / 2.0);
        return (int) (shifted / step) % slotCount;
    }

    /**
     * Slot index under a cursor position, or {@link #NO_SLOT} inside the dead zone.
     *
     * @param deadZoneRadius radius below which the cursor counts as "not pointing at anything"
     */
    public static int slotAtPoint(double centerX, double centerY, double x, double y, int slotCount,
        double rotationDegrees, double deadZoneRadius) {
        double dx = x - centerX;
        double dy = y - centerY;
        if (dx * dx + dy * dy < deadZoneRadius * deadZoneRadius) {
            return NO_SLOT;
        }
        return slotAtAngle(angleTo(centerX, centerY, x, y), slotCount, rotationDegrees);
    }

    /** Angle of the middle of a slot's sector - where its icon and label go. */
    public static double slotCenterAngle(int slotIndex, int slotCount, double rotationDegrees) {
        if (slotCount <= 0) {
            return 0.0;
        }
        return normalizeDegrees(rotationDegrees + slotIndex * (360.0 / slotCount));
    }

    /** Angular width of one sector, in degrees. */
    public static double sectorSpan(int slotCount) {
        return slotCount <= 0 ? 0.0 : 360.0 / slotCount;
    }

    /**
     * Angular width of one slot of a ring that covers an arc rather than the whole circle.
     *
     * <p>
     * An inline submenu is a ring with a beginning and an end, so everything below takes the arc it is drawn in
     * instead of assuming 360 degrees. A full ring is the same arithmetic with {@code arcSpan == 360}, which is why
     * there is one set of functions and not two.
     */
    public static double arcSlotSpan(double arcSpan, int slotCount) {
        return slotCount <= 0 ? 0.0 : arcSpan / slotCount;
    }

    /** Angle the given slot of an arc starts at. */
    public static double arcSlotStart(int slot, int slotCount, double startAngle, double arcSpan) {
        return startAngle + slot * arcSlotSpan(arcSpan, slotCount);
    }

    /** Middle of a slot's sector within an arc - where its icon and label go. */
    public static double arcSlotCenter(int slot, int slotCount, double startAngle, double arcSpan) {
        return startAngle + (slot + 0.5) * arcSlotSpan(arcSpan, slotCount);
    }

    /**
     * Slot of an arc an angle falls in, or {@link #NO_SLOT} when the angle is outside the arc altogether.
     *
     * <p>
     * The distance from the start is measured round the circle, so an arc that crosses the top of the wheel needs no
     * special case. An arc of a full 360 degrees therefore never answers {@link #NO_SLOT}, which is what keeps the
     * main ring behaving exactly as {@link #slotAtAngle} always has.
     */
    public static int slotInArc(double angleDegrees, double startAngle, double arcSpan, int slotCount) {
        if (slotCount <= 0 || arcSpan <= 0.0) {
            return NO_SLOT;
        }
        double fromStart = normalizeDegrees(angleDegrees - startAngle);
        if (arcSpan < 360.0 && fromStart > arcSpan) {
            return NO_SLOT;
        }
        int slot = (int) (fromStart / arcSlotSpan(arcSpan, slotCount));
        // The division lands exactly on slotCount at the far end of a full ring, and a hair past it on an arc.
        return Math.min(slot, slotCount - 1);
    }

    /** X offset of a point at {@code radius} along {@code angleDegrees}, in screen coordinates. */
    public static double offsetX(double angleDegrees, double radius) {
        return Math.sin(Math.toRadians(angleDegrees)) * radius;
    }

    /** Y offset of a point at {@code radius} along {@code angleDegrees}, in screen coordinates. */
    public static double offsetY(double angleDegrees, double radius) {
        return -Math.cos(Math.toRadians(angleDegrees)) * radius;
    }

    /**
     * Half the angle a gap of {@code gapPixels} takes up at {@code radius}, in degrees.
     *
     * <p>
     * A gap between sectors is a distance, not an angle. Inset by a fixed angle instead and the gap fans out - wide
     * at the rim, pinched at the hole - which is what a wheel drawn with narrow sectors used to look like. Insetting
     * each radius by {@code asin(halfGap / radius)} keeps the two sectors a constant distance apart all the way
     * along, so the edges between them are straight lines.
     *
     * <p>
     * Purely how the ring is drawn: which sector a click lands in is still worked out from the full, undivided
     * sector, because the gap is a look and not a target the player has to avoid.
     */
    public static double gapInsetDegrees(double radius, double gapPixels) {
        if (gapPixels <= 0.0 || radius <= 0.0) {
            return 0.0;
        }
        double half = gapPixels / 2.0;
        if (half >= radius) {
            return 90.0;
        }
        return Math.toDegrees(Math.asin(half / radius));
    }

    /** How many line segments to use when tessellating one sector's arc, so big sectors stay round. */
    public static int arcSegments(double spanDegrees) {
        return Math.max(8, (int) Math.ceil(spanDegrees / 4.0));
    }
}
