package com.navatusein.radialmenu.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.navatusein.radialmenu.core.action.ActionSpec;

/**
 * One entry of the wheel, and - when it has children - the wheel those children live on.
 *
 * <p>
 * A single node type covers both cases: {@link #children} being non-null makes it a category, {@link #action} being
 * non-null makes it a leaf. That is what gives a real tree with automatic back-navigation, instead of a flat map of
 * string-keyed categories where going back is an entry the player has to add by hand.
 */
public class MenuNode {

    public String title;

    public IconSpec icon;

    /** When true the wheel stays open after this entry fires, so it can be triggered repeatedly. */
    public boolean keepOpen;

    /** Layout of this node's own children wheel. Only meaningful for categories. */
    public SlotLayout layout;

    /** Colour overrides for this node's own children wheel. Null inherits the global settings. */
    public MenuStyle style;

    /**
     * Child entries. Non-null marks this node as a category. A null element is an empty slot, which only happens under
     * {@link SlotLayout.Mode#FIXED}.
     */
    public List<MenuNode> children;

    public ActionSpec action;

    public static MenuNode leaf(String title, IconSpec icon, ActionSpec action) {
        MenuNode node = new MenuNode();
        node.title = title;
        node.icon = icon;
        node.action = action;
        return node;
    }

    public static MenuNode category(String title, IconSpec icon, SlotLayout layout) {
        MenuNode node = new MenuNode();
        node.title = title;
        node.icon = icon;
        node.layout = layout;
        node.children = new ArrayList<>();
        return node;
    }

    public boolean isCategory() {
        return children != null;
    }

    public List<MenuNode> childrenOrEmpty() {
        return children == null ? Collections.<MenuNode>emptyList() : children;
    }

    public SlotLayout layoutOrDefault() {
        return layout == null ? SlotLayout.dynamic() : layout;
    }

    /**
     * Number of sectors this node's wheel draws.
     *
     * <p>
     * A fixed wheel always has its configured number, gaps included. A dynamic one has as many as it has entries -
     * the empty positions still sit in the list, they are simply not drawn.
     */
    public int slotCount() {
        if (layoutOrDefault().mode == SlotLayout.Mode.FIXED) {
            return layoutOrDefault().slotCount(childrenOrEmpty().size());
        }
        return filledCount();
    }

    /** How many entries this menu actually holds, ignoring empty positions. */
    public int filledCount() {
        int count = 0;
        for (MenuNode child : childrenOrEmpty()) {
            if (child != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Every entry anywhere under this node: its own, those of its submenus, and so on down.
     *
     * <p>
     * What {@link #filledCount()} counts is one wheel; this counts a profile. The difference is the whole point of
     * having both - a submenu that loses everything inside it leaves the wheel above it exactly as long as it was,
     * so a number that only looked at one level could not tell the two apart.
     */
    public int deepCount() {
        int count = 0;
        for (MenuNode child : childrenOrEmpty()) {
            if (child == null) {
                continue;
            }
            count++;
            count += child.deepCount();
        }
        return count;
    }

    /**
     * Entry drawn in a sector, or null when that sector is empty.
     *
     * <p>
     * Under a dynamic layout the sectors are the entries in order, skipping the gaps; under a fixed one a sector is
     * a position in the list. Keeping the gaps in the list either way is what lets a menu be switched to dynamic and
     * back without the entries losing the positions the player gave them.
     */
    public MenuNode childAt(int slotIndex) {
        int index = childIndexForSlot(slotIndex);
        return index < 0 ? null : children.get(index);
    }

    /**
     * Entry at a position in {@link #children}, or null for an empty position or one past the end.
     *
     * <p>
     * Deliberately separate from {@link #childAt(int)}: one takes a sector, the other a list position, and on a
     * dynamic wheel with gaps in the list they are different numbers. Passing a list position to the sector version
     * translates it a second time, which is how the editor for a free position came to open the entry six sectors
     * along.
     */
    public MenuNode childAtIndex(int index) {
        List<MenuNode> kids = childrenOrEmpty();
        return index >= 0 && index < kids.size() ? kids.get(index) : null;
    }

    /**
     * Index in {@link #children} of the entry drawn in a sector, or -1 if there is none.
     *
     * <p>
     * The editor works in list indices, so anything that edits what a sector shows has to translate through here.
     */
    public int childIndexForSlot(int slotIndex) {
        List<MenuNode> kids = childrenOrEmpty();
        if (slotIndex < 0) {
            return -1;
        }
        if (layoutOrDefault().mode == SlotLayout.Mode.FIXED) {
            return slotIndex < kids.size() && kids.get(slotIndex) != null ? slotIndex : -1;
        }
        int seen = 0;
        for (int i = 0; i < kids.size(); i++) {
            if (kids.get(i) == null) {
                continue;
            }
            if (seen == slotIndex) {
                return i;
            }
            seen++;
        }
        return -1;
    }

    /**
     * Sector a list position is drawn in - the inverse of {@link #childIndexForSlot(int)}.
     *
     * <p>
     * On a fixed wheel the two are the same number. On a dynamic one the gaps are not drawn, so a position sits at
     * the sector its filled predecessors leave it: list position 6 with two gaps before it is sector 4. Only the
     * sector means anything to the player, who is looking at the ring and not at the file.
     */
    public int slotForChildIndex(int index) {
        if (index < 0) {
            return -1;
        }
        if (layoutOrDefault().mode == SlotLayout.Mode.FIXED) {
            return index;
        }
        List<MenuNode> kids = childrenOrEmpty();
        int seen = 0;
        for (int i = 0; i < index && i < kids.size(); i++) {
            if (kids.get(i) != null) {
                seen++;
            }
        }
        return seen;
    }

    /**
     * Position a new entry takes on a dynamic wheel: just past the last entry in the list.
     *
     * <p>
     * The end, because that is where the player clicked - the extra sector a dynamic wheel grows for adding sits at
     * the end of the ring. Filling the first gap instead put the new entry in the middle of any menu that had once
     * been fixed, which is nowhere near the sector that was pressed.
     *
     * <p>
     * Empty positions in the tail are reused rather than skipped past, so adding to a menu padded out by a fixed
     * layout does not widen that layout by however many gaps its tail happened to carry.
     */
    public int appendIndex() {
        List<MenuNode> kids = childrenOrEmpty();
        int last = -1;
        for (int i = 0; i < kids.size(); i++) {
            if (kids.get(i) != null) {
                last = i;
            }
        }
        return last + 1;
    }

    /**
     * Grows the child list so every sector of a fixed-size wheel has a position, padding with nulls. Dynamic wheels
     * need no padding, since their sector count follows the list length.
     *
     * <p>
     * If the list is longer than the wheel has sectors, the wheel is widened instead of leaving the overflow
     * invisible. Silently hiding entries a player configured is worse than a ring that grew a step.
     */
    public void ensureSlotCapacity() {
        if (!isCategory()) {
            return;
        }
        if (layoutOrDefault().mode != SlotLayout.Mode.FIXED) {
            // Deliberately keeps the empty positions: a dynamic wheel just does not draw them, so switching to
            // dynamic and back leaves every entry on the sector the player put it on.
            return;
        }
        if (children.size() > layout.slots) {
            layout.slots = SlotLayout.clampSlots(children.size());
        }
        int required = layoutOrDefault().slotCount(children.size());
        while (children.size() < required) {
            children.add(null);
        }
    }

    /**
     * Places a child at a specific sector, padding with empty slots as needed.
     *
     * <p>
     * Assigning by index rather than appending is what keeps a fixed wheel's angles stable - appending into a list
     * that was already padded with nulls would push every entry past the last visible sector.
     */
    public void setChildAt(int slotIndex, MenuNode child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        while (children.size() <= slotIndex) {
            children.add(null);
        }
        children.set(slotIndex, child);
    }

    /**
     * Moves the entry at a list position one place towards the start or end.
     *
     * <p>
     * What "one place" means follows the layout, because that is what the player sees. On a fixed wheel the sectors
     * are list positions, so an entry swaps with its neighbour whether or not that neighbour is empty - which is how
     * an entry is walked into a gap. On a dynamic wheel empty positions are not drawn at all, so swapping with one
     * would look like nothing happened; there the entry swaps with the next entry that is actually on screen.
     *
     * @param direction -1 towards the start, 1 towards the end
     * @return true if anything moved
     */
    public boolean moveChild(int index, int direction) {
        if (!isCategory()) {
            return false;
        }
        return moveInList(children, index, direction, layoutOrDefault().mode == SlotLayout.Mode.FIXED);
    }

    /**
     * The same move against a caller's own list.
     *
     * <p>
     * The editor reorders a working copy so that cancelling really cancels, and it has to honour the layout the
     * player has just chosen on screen rather than the one currently saved.
     */
    public static boolean moveInList(List<MenuNode> kids, int index, int direction, boolean fixedLayout) {
        if (kids == null || direction == 0) {
            return false;
        }
        if (index < 0 || index >= kids.size() || kids.get(index) == null) {
            return false;
        }

        int target = -1;
        if (fixedLayout) {
            int candidate = index + direction;
            if (candidate >= 0 && candidate < kids.size()) {
                target = candidate;
            }
        } else {
            for (int i = index + direction; i >= 0 && i < kids.size(); i += direction) {
                if (kids.get(i) != null) {
                    target = i;
                    break;
                }
            }
        }

        if (target < 0) {
            return false;
        }
        MenuNode moved = kids.get(index);
        kids.set(index, kids.get(target));
        kids.set(target, moved);
        return true;
    }

    public MenuNode copy() {
        MenuNode copy = new MenuNode();
        copy.title = title;
        copy.icon = icon == null ? null : icon.copy();
        copy.keepOpen = keepOpen;
        copy.layout = layout;
        copy.style = style;
        copy.action = action == null ? null : action.copy();
        if (children != null) {
            copy.children = new ArrayList<>(children.size());
            for (MenuNode child : children) {
                copy.children.add(child == null ? null : child.copy());
            }
        }
        return copy;
    }

    /**
     * Repairs anything a hand-edited file got wrong. A node carrying both an action and children is treated as a
     * category, because dropping the children would silently destroy more of the player's work than dropping the
     * action does.
     */
    public void normalize() {
        if (icon != null) {
            icon.normalize();
        }
        if (isCategory()) {
            action = null;
            if (layout == null) {
                layout = SlotLayout.dynamic();
            }
            layout.normalize();
            for (MenuNode child : children) {
                if (child != null) {
                    child.normalize();
                }
            }
            ensureSlotCapacity();
        } else {
            layout = null;
            style = null;
            if (action != null) {
                action.normalize();
            }
        }
    }
}
