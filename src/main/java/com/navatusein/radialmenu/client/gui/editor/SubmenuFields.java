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
        spec.set(
            ActionTypes.PARAM_SLOT_MODE,
            layout.mode.name()
                .toLowerCase());
        spec.set(ActionTypes.PARAM_SLOT_COUNT, Integer.toString(layout.slots));
        spec.set(ActionTypes.PARAM_RING_COLOR, style == null || style.ringColor == null ? "" : style.ringColor);
        spec.set(
            ActionTypes.PARAM_HIGHLIGHT_COLOR,
            style == null || style.highlightColor == null ? "" : style.highlightColor);
        return spec;
    }

    /** Writes the edited spec back onto the node, turning it into a category if it was not one already. */
    static void applyToNode(MenuNode node) {
        ActionSpec spec = node.action;

        SlotLayout layout = new SlotLayout();
        layout.mode = spec.getEnum(ActionTypes.PARAM_SLOT_MODE, SlotLayout.Mode.class, SlotLayout.Mode.FIXED);
        layout.slots = SlotLayout.clampSlots(spec.getInt(ActionTypes.PARAM_SLOT_COUNT, SlotLayout.DEFAULT_SLOTS));

        node.layout = layout;
        node.style = MenuStyle.of(
            spec.getString(ActionTypes.PARAM_RING_COLOR, ""),
            spec.getString(ActionTypes.PARAM_HIGHLIGHT_COLOR, ""));

        if (node.children == null) {
            node.children = new ArrayList<>();
        }
        // The spec was only ever a view onto the node; a category carries no action.
        node.action = null;
        node.ensureSlotCapacity();
    }
}
