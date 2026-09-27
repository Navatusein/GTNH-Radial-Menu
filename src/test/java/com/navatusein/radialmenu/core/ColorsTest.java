package com.navatusein.radialmenu.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Colour strings.
 *
 * <p>
 * The mod stores colours in two shapes - the config writes {@code 0xAARRGGBB}, the colour picker can write
 * {@code #RRGGBB} - and reading one as the other is how a ring colour once became invisible.
 */
public class ColorsTest {

    @Test
    public void readsBothWrittenForms() {
        assertEquals(0x99101010, Colors.parseArgb("0x99101010", 0));
        assertEquals(0x00920A0A, Colors.parseArgb("#920A0A", 0));
        assertEquals(0x00920A0A, Colors.parseArgb("920A0A", 0));
    }

    @Test
    public void badInputFallsBack() {
        assertEquals(42, Colors.parseArgb(null, 42));
        assertEquals(42, Colors.parseArgb("", 42));
        assertEquals(42, Colors.parseArgb("not a colour", 42));
        assertEquals(42, Colors.parseArgb("#12345", 42));
    }

    @Test
    public void alphaIsDetectedByLength() {
        assertTrue(Colors.hasAlpha("0x99101010"));
        assertTrue(Colors.hasAlpha("#99101010"));
        assertFalse(Colors.hasAlpha("#920A0A"));
        assertFalse(Colors.hasAlpha(null));
    }

    @Test
    public void aBlankOverrideInheritsEntirely() {
        assertEquals(0x99101010, Colors.resolve(null, "0x99101010", 0));
        assertEquals(0x99101010, Colors.resolve("   ", "0x99101010", 0));
    }

    @Test
    public void anOverrideWithoutAlphaKeepsTheOpacityItReplaces() {
        // Regression: read as an eight-digit colour, #920A0A is alpha zero - the ring simply vanished.
        assertEquals(0x99920A0A, Colors.resolve("#920A0A", "0x99101010", 0));
    }

    @Test
    public void anOverrideWithAlphaIsTakenAsWritten() {
        assertEquals(0x40920A0A, Colors.resolve("#40920A0A", "0x99101010", 0));
        assertEquals(0x00920A0A, Colors.resolve("#00920A0A", "0x99101010", 0));
    }

    @Test
    public void aBrokenOverrideLeavesTheInheritedColourAlone() {
        assertEquals(0x99101010, Colors.resolve("nonsense", "0x99101010", 0));
    }

    @Test
    public void aBrokenInheritedValueFallsBack() {
        assertEquals(0xCC4A90D9, Colors.resolve(null, "nonsense", 0xCC4A90D9));
        // The override still applies over the fallback's opacity.
        assertEquals(0xCC920A0A, Colors.resolve("#920A0A", "nonsense", 0xCC4A90D9));
    }
}
