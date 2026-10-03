package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;

import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionTypes;
import com.navatusein.radialmenu.core.model.MenuNode;
import com.navatusein.radialmenu.core.model.MenuStyle;
import com.navatusein.radialmenu.core.model.SlotLayout;

/**
 * Bridges a submenu's settings, which live on the node, to the generic field editor, which drives an
 * {@link ActionSpec}.
 *
 * <p>
 * Keeping submenu configuration on the node is right for the file format - a submenu is a shape, not an action - but
 * presenting it as one more action type is right for the player. This is the only place the two views meet, so the
 * editor needs no special cases for it.
 */
final class SubmenuFields {

    private SubmenuFields() {}

    /** Reads the node's layout and colours into a spec the field editor can show. */
    static ActionSpec toSpec(MenuNode node) {
        SlotLayout layout = node.layoutOrDefault();
        MenuStyle style = node.style;

        ActionSpec spec = new ActionSpec(ActionTypes.SUBMENU);
        // Defaulted here rather than trusted: a node built in code, or one loaded before this field existed, carries
        // no opening at all and the editor would rather show "replace" than refuse to open.
        SlotLayout.Opening opening = layout.opening == null ? SlotLayout.Opening.REPLACE : layout.opening;
        spec.set(
            ActionTypes.PARAM_OPENING,
            opening.name()
                .toLowerCase());
        spec.set(
            ActionTypes.PARAM_SLOT_MODE,
            layout.mode.name()
                .toLowerCase());
        spec.set(ActionTypes.PARAM_SLOT_COUNT, Integer.toString(layout.slots));
        spec.set(ActionTypes.PARAM_RING_COLOR, orBlank(style == null ? null : style.ringColor));
        spec.set(ActionTypes.PARAM_HIGHLIGHT_COLOR, orBlank(style == null ? null : style.highlightColor));
        spec.set(ActionTypes.PARAM_BORDER_COLOR, orBlank(style == null ? null : style.borderColor));
        spec.set(ActionTypes.PARAM_HIGHLIGHT_BORDER_COLOR, orBlank(style == null ? null : style.highlightBorderColor));
        spec.set(ActionTypes.PARAM_BACKGROUND_COLOR, orBlank(style == null ? null : style.backgroundColor));
        return spec;
    }

    private static String orBlank(String value) {
        return value == null ? "" : value;
    }

    /** Writes the edited spec back onto the node, turning it into a category if it was not one already. */
    static void applyToNode(MenuNode node) {
        ActionSpec spec = node.action;

        SlotLayout layout = new SlotLayout();
        layout.opening = spec.getEnum(ActionTypes.PARAM_OPENING, SlotLayout.Opening.class, SlotLayout.Opening.REPLACE);
        layout.mode = spec.getEnum(ActionTypes.PARAM_SLOT_MODE, SlotLayout.Mode.class, SlotLayout.Mode.FIXED);
        layout.slots = SlotLayout.clampSlots(spec.getInt(ActionTypes.PARAM_SLOT_COUNT, SlotLayout.DEFAULT_SLOTS));

        node.layout = layout;
        // A submenu has no icon tint of its own - that belongs to the entry, not to the wheel it opens.
        node.style = MenuStyle.of(
            spec.getString(ActionTypes.PARAM_RING_COLOR, ""),
            spec.getString(ActionTypes.PARAM_HIGHLIGHT_COLOR, ""),
            null,
            spec.getString(ActionTypes.PARAM_BORDER_COLOR, ""),
            spec.getString(ActionTypes.PARAM_HIGHLIGHT_BORDER_COLOR, ""),
            spec.getString(ActionTypes.PARAM_BACKGROUND_COLOR, ""));

        if (node.children == null) {
            node.children = new ArrayList<>();
        }
        // The spec was only ever a view onto the node; a category carries no action.
        node.action = null;
        node.ensureSlotCapacity();
    }
}
