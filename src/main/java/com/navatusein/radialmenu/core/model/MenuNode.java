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

    /** Number of sectors this node's wheel should draw. */
    public int slotCount() {
        return layoutOrDefault().slotCount(childrenOrEmpty().size());
    }

    /** Child at a sector index, or null for an empty slot or an out-of-range index. */
    public MenuNode childAt(int slotIndex) {
        List<MenuNode> kids = childrenOrEmpty();
        if (slotIndex < 0 || slotIndex >= kids.size()) {
            return null;
        }
        return kids.get(slotIndex);
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
        if (!isCategory() || layoutOrDefault().mode != SlotLayout.Mode.FIXED) {
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

    public MenuNode copy() {
        MenuNode copy = new MenuNode();
        copy.title = title;
        copy.icon = icon == null ? null : icon.copy();
        copy.keepOpen = keepOpen;
        copy.layout = layout;
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
        }
    }
}
