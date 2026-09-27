package com.navatusein.radialmenu.core.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.navatusein.radialmenu.core.action.ActionSpec;

/**
 * Switching a menu between the two slot layouts.
 *
 * <p>
 * The point of these: a dynamic layout is a way of drawing the menu, not a change to it. The empty positions stay in
 * the list so that switching to dynamic and back leaves every entry exactly where the player put it.
 */
public class SlotLayoutSwitchTest {

    private static MenuNode leaf(String title) {
        return MenuNode.leaf(title, null, new ActionSpec("keybind"));
    }

    /** Eight fixed sectors with entries on 0, 2 and 4. */
    private static MenuNode fixedWithGaps() {
        MenuNode menu = MenuNode.category("root", null, SlotLayout.fixed(8));
        menu.setChildAt(0, leaf("a"));
        menu.setChildAt(2, leaf("b"));
        menu.setChildAt(4, leaf("c"));
        menu.ensureSlotCapacity();
        return menu;
    }

    @Test
    public void fixedDrawsEveryPositionIncludingTheGaps() {
        MenuNode menu = fixedWithGaps();
        assertEquals(8, menu.slotCount());
        assertEquals("a", menu.childAt(0).title);
        assertNull(menu.childAt(1));
        assertEquals("b", menu.childAt(2).title);
    }

    @Test
    public void dynamicDrawsOnlyTheEntries() {
        MenuNode menu = fixedWithGaps();
        menu.layout = SlotLayout.dynamic();

        assertEquals(3, menu.slotCount());
        assertEquals("a", menu.childAt(0).title);
        assertEquals("b", menu.childAt(1).title);
        assertEquals("c", menu.childAt(2).title);
    }

    @Test
    public void dynamicLeavesTheListAlone() {
        MenuNode menu = fixedWithGaps();
        menu.layout = SlotLayout.dynamic();
        menu.ensureSlotCapacity();
        menu.normalize();

        assertEquals("the gaps are what positions survive on", 8, menu.children.size());
        assertNull(menu.children.get(1));
    }

    @Test
    public void switchingToDynamicAndBackKeepsEveryEntryOnItsOwnSector() {
        // Regression: compacting the list on the way to dynamic moved a, b and c onto sectors 0, 1 and 2, and
        // switching back could not put them right again.
        MenuNode menu = fixedWithGaps();

        menu.layout = SlotLayout.dynamic();
        menu.ensureSlotCapacity();
        menu.layout = SlotLayout.fixed(8);
        menu.ensureSlotCapacity();

        assertEquals(8, menu.slotCount());
        assertEquals("a", menu.childAt(0).title);
        assertNull(menu.childAt(1));
        assertEquals("b", menu.childAt(2).title);
        assertNull(menu.childAt(3));
        assertEquals("c", menu.childAt(4).title);
    }

    @Test
    public void sectorsMapBackToListPositionsUnderEachLayout() {
        MenuNode menu = fixedWithGaps();

        assertEquals(2, menu.childIndexForSlot(2));
        assertEquals("an empty fixed sector has no entry", -1, menu.childIndexForSlot(1));

        menu.layout = SlotLayout.dynamic();
        assertEquals("the second drawn sector is the entry at list position 2", 2, menu.childIndexForSlot(1));
        assertEquals(4, menu.childIndexForSlot(2));
        assertEquals(-1, menu.childIndexForSlot(3));
    }

    @Test
    public void aNewEntryOnADynamicWheelGoesIntoTheFirstGap() {
        MenuNode menu = fixedWithGaps();
        menu.layout = SlotLayout.dynamic();

        int index = menu.firstFreeIndex();
        assertEquals(1, index);

        menu.setChildAt(index, leaf("d"));
        assertEquals(4, menu.slotCount());
        assertEquals("d", menu.childAt(1).title);
    }

    @Test
    public void aFullListAppendsInsteadOfOverwriting() {
        MenuNode menu = MenuNode.category("root", null, SlotLayout.dynamic());
        menu.setChildAt(0, leaf("a"));
        menu.setChildAt(1, leaf("b"));

        assertEquals(2, menu.firstFreeIndex());
        menu.setChildAt(menu.firstFreeIndex(), leaf("c"));

        assertEquals(3, menu.slotCount());
        assertEquals("c", menu.childAt(2).title);
    }

    @Test
    public void filledCountIgnoresGaps() {
        MenuNode menu = fixedWithGaps();
        assertEquals(3, menu.filledCount());
        assertNotNull(menu.childAt(0));
    }

    @Test
    public void anEmptyDynamicMenuDrawsNothing() {
        MenuNode menu = MenuNode.category("root", null, SlotLayout.dynamic());
        menu.ensureSlotCapacity();
        assertEquals(0, menu.slotCount());
        assertEquals(-1, menu.childIndexForSlot(0));
    }
}
