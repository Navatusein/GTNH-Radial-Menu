package com.navatusein.radialmenu.core.geometry;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The wheel's hit testing. Worth pinning down precisely, because an off-by-half-a-sector error is the kind of bug that
 * feels like "the menu sometimes picks the wrong thing" rather than something obviously broken.
 */
public class RadialGeometryTest {

    private static final double CENTER = 100.0;

    @Test
    public void angleIsZeroStraightUpAndGrowsClockwise() {
        assertEquals(0.0, RadialGeometry.angleTo(CENTER, CENTER, CENTER, CENTER - 50), 1e-6);
        assertEquals(90.0, RadialGeometry.angleTo(CENTER, CENTER, CENTER + 50, CENTER), 1e-6);
        assertEquals(180.0, RadialGeometry.angleTo(CENTER, CENTER, CENTER, CENTER + 50), 1e-6);
        assertEquals(270.0, RadialGeometry.angleTo(CENTER, CENTER, CENTER - 50, CENTER), 1e-6);
    }

    @Test
    public void firstSlotIsCenteredOnTopNotStartingThere() {
        // Straight up, and a hair either side of it, must all land in slot 0.
        assertEquals(0, RadialGeometry.slotAtAngle(0.0, 8, 0.0));
        assertEquals(0, RadialGeometry.slotAtAngle(22.0, 8, 0.0));
        assertEquals(0, RadialGeometry.slotAtAngle(338.0, 8, 0.0));
    }

    @Test
    public void sectorBoundariesLandOnTheExpectedSlots() {
        // Eight slots, so each spans 45 degrees and slot 1 is centred on 45.
        assertEquals(1, RadialGeometry.slotAtAngle(45.0, 8, 0.0));
        assertEquals(1, RadialGeometry.slotAtAngle(23.0, 8, 0.0));
        assertEquals(2, RadialGeometry.slotAtAngle(68.0, 8, 0.0));
        assertEquals(7, RadialGeometry.slotAtAngle(315.0, 8, 0.0));
    }

    @Test
    public void everyAngleMapsToSomeSlotAndBackToItsOwnSector() {
        for (int slotCount = 2; slotCount <= 24; slotCount++) {
            for (int slot = 0; slot < slotCount; slot++) {
                double center = RadialGeometry.slotCenterAngle(slot, slotCount, 0.0);
                assertEquals(
                    "slotCount=" + slotCount + " slot=" + slot,
                    slot,
                    RadialGeometry.slotAtAngle(center, slotCount, 0.0));
            }
        }
    }

    @Test
    public void deadZoneSelectsNothing() {
        int inside = RadialGeometry.slotAtPoint(CENTER, CENTER, CENTER + 5, CENTER, 8, 0.0, 32.0);
        assertEquals(RadialGeometry.NO_SLOT, inside);

        int outside = RadialGeometry.slotAtPoint(CENTER, CENTER, CENTER + 50, CENTER, 8, 0.0, 32.0);
        assertEquals(2, outside);
    }

    @Test
    public void deadZoneBoundaryIsInclusiveOfTheRing() {
        // Exactly on the dead zone radius counts as pointing at the ring, not at nothing.
        int onEdge = RadialGeometry.slotAtPoint(CENTER, CENTER, CENTER, CENTER - 32, 8, 0.0, 32.0);
        assertEquals(0, onEdge);
    }

    @Test
    public void rotationOffsetShiftsWhichSlotAnAngleHits() {
        assertEquals(0, RadialGeometry.slotAtAngle(45.0, 8, 45.0));
        assertEquals(7, RadialGeometry.slotAtAngle(0.0, 8, 45.0));
    }

    @Test
    public void offsetsAgreeWithTheAngleConvention() {
        assertEquals(0.0, RadialGeometry.offsetX(0.0, 10.0), 1e-6);
        assertEquals(-10.0, RadialGeometry.offsetY(0.0, 10.0), 1e-6);
        assertEquals(10.0, RadialGeometry.offsetX(90.0, 10.0), 1e-6);
        assertEquals(0.0, RadialGeometry.offsetY(90.0, 10.0), 1e-6);
    }

    @Test
    public void degenerateSlotCountsDoNotThrow() {
        assertEquals(RadialGeometry.NO_SLOT, RadialGeometry.slotAtAngle(12.0, 0, 0.0));
        assertEquals(0.0, RadialGeometry.sectorSpan(0), 1e-6);
    }
}
