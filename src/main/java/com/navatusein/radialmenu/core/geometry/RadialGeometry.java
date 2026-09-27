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

    /** X offset of a point at {@code radius} along {@code angleDegrees}, in screen coordinates. */
    public static double offsetX(double angleDegrees, double radius) {
        return Math.sin(Math.toRadians(angleDegrees)) * radius;
    }

    /** Y offset of a point at {@code radius} along {@code angleDegrees}, in screen coordinates. */
    public static double offsetY(double angleDegrees, double radius) {
        return -Math.cos(Math.toRadians(angleDegrees)) * radius;
    }

    /** How many line segments to use when tessellating one sector's arc, so big sectors stay round. */
    public static int arcSegments(double spanDegrees) {
        return Math.max(8, (int) Math.ceil(spanDegrees / 4.0));
    }
}
