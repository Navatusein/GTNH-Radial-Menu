package com.navatusein.radialmenu.client.gui.editor;

import net.minecraft.client.resources.I18n;

import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionType;
import com.navatusein.radialmenu.core.action.ActionTypes;
import com.navatusein.radialmenu.core.action.Placeholders;

/**
 * One line describing an action, for a row in a chain.
 *
 * <p>
 * A chain is only readable if each row says what it actually does, so the type's name is followed by the parameter
 * that identifies this particular use of it - a bare list of "Keybind, Keybind, Command" would be no list at all.
 * A type this does not know still shows its name, which is why adding an action type needs no change here.
 */
final class ActionSummary {

    private ActionSummary() {}

    static String of(ActionSpec spec) {
        if (spec == null || spec.type == null) {
            return I18n.format("radialmenu.steps.unset");
        }
        ActionType type = ActionTypes.get(spec.type);
        String name = type == null ? spec.type : I18n.format(type.labelKey);
        String detail = detailOf(spec);
        return detail.isEmpty() ? name : name + ": " + detail;
    }

    private static String detailOf(ActionSpec spec) {
        if (ActionTypes.KEYBIND.equals(spec.type)) {
            String binding = spec.getString(ActionTypes.PARAM_BINDING, "");
            return binding.isEmpty() ? I18n.format("radialmenu.steps.unset") : I18n.format(binding);
        }
        if (ActionTypes.COMMAND.equals(spec.type)) {
            String[] lines = Placeholders.splitLines(spec.getString(ActionTypes.PARAM_COMMAND, ""));
            if (lines.length == 0) {
                return I18n.format("radialmenu.steps.unset");
            }
            return lines.length == 1 ? lines[0] : I18n.format("radialmenu.steps.andMore", lines[0], lines.length - 1);
        }
        if (ActionTypes.PROFILE_SWITCH.equals(spec.type)) {
            String profile = spec.getString(ActionTypes.PARAM_PROFILE, "");
            // A blank profile cycles to the next one, which is a choice rather than an omission.
            return profile.isEmpty() ? I18n.format("radialmenu.steps.nextProfile") : profile;
        }
        if (ActionTypes.SEQUENCE.equals(spec.type)) {
            return I18n.format("radialmenu.steps.count", Integer.valueOf(spec.steps == null ? 0 : spec.steps.size()));
        }
        return "";
    }
}
