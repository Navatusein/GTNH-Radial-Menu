package com.navatusein.radialmenu.client.gui.editor;

import net.minecraft.client.resources.I18n;

import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.config.AccentConfig;
import com.navatusein.radialmenu.config.ColorConfig;
import com.navatusein.radialmenu.config.WheelConfig;
import com.navatusein.radialmenu.core.Colors;
import com.navatusein.radialmenu.core.action.ActionField;
import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionTypes;
import com.navatusein.radialmenu.core.action.Placeholders;
import com.navatusein.radialmenu.core.model.StyleResolver;
import com.navatusein.radialmenu.core.model.WheelColors;

import cpw.mods.fml.client.config.GuiSlider;

/**
 * What kind of control a field descriptor wants, and what its button says.
 *
 * <p>
 * Two screens generate their widgets from the same descriptors - the slot editor and the menu settings - and until
 * this existed they each decided for themselves. They disagreed the moment a new kind of field appeared: an accent
 * came out as a colour button in one and as a text box in the other. The decisions live here so a new field kind is
 * added once.
 */
final class FieldControls {

    /** Width of the button that clears a colour. The same one the profile's colours use. */
    static final int CLEAR_WIDTH = 20;

    private FieldControls() {}

    /**
     * Whether the field carries a button that empties it.
     *
     * <p>
     * Only a colour: blank means "inherit" there, which is a value a player chooses as deliberately as any other and
     * cannot type into a button. A blank keybinding or profile name is reached by picking from the list.
     */
    static boolean hasClear(ActionField field) {
        return field.kind == ActionField.Kind.COLOR;
    }

    /**
     * Whether the field stands apart from its neighbours.
     *
     * <p>
     * An accent writes into the rows below it rather than holding a value of its own, and read as one more row in
     * the column it looks like a colour that has gone blank.
     */
    static boolean standsApart(ActionField field) {
        return field.kind == ActionField.Kind.ACCENT;
    }

    /** Whether the field is shown as a button rather than something typed into. */
    static boolean isButton(ActionField field) {
        switch (field.kind) {
            case KEYBIND_REF:
            case MULTILINE_STRING:
            case ENUM:
            case PROFILE_REF:
            case COLOR:
            case ACCENT:
                return true;
            default:
                return false;
        }
    }

    /** Whether the colour picker should offer opacity. An accent is a hue and nothing else. */
    static boolean pickerHasAlpha(ActionField field) {
        return field.kind != ActionField.Kind.ACCENT;
    }

    static String buttonLabel(ActionField field, String current) {
        switch (field.kind) {
            case KEYBIND_REF:
                return current == null || current.isEmpty() ? I18n.format("radialmenu.editor.pickKeybind")
                    : I18n.format(current);
            case MULTILINE_STRING:
                return I18n.format("radialmenu.editor.editLines", Placeholders.splitLines(current).length);
            case COLOR:
                return isBlank(current) ? I18n.format("radialmenu.editor.inherit") : current;
            case ACCENT:
                // Nothing is stored, so there is no value to show - only the offer.
                return I18n.format("radialmenu.editor.pickAccent");
            case ENUM:
                return localizedValue(field, current);
            default:
                return current == null ? "" : current;
        }
    }

    /**
     * Shows an enum value the way the rest of the interface is written.
     *
     * <p>
     * The stored value stays lower case; only the display changes. A value with no translation falls back to itself
     * rather than showing a raw key, so an action type that forgot a string still reads as something.
     */
    static String localizedValue(ActionField field, String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String key = field.valueLabelKey(value);
        String translated = I18n.format(key);
        return translated.equals(key) ? value : translated;
    }

    static GuiSlider slider(int id, int x, int y, int width, int height, ActionField field, String current) {
        int value = parseInt(current, parseInt(field.defaultValue, field.min));
        return new GuiSlider(
            id,
            x,
            y,
            width,
            height,
            "",
            "",
            field.min,
            field.max,
            Math.max(field.min, Math.min(field.max, value)),
            false,
            true);
    }

    /**
     * Writes the colours an accent produces into the fields it derives.
     *
     * <p>
     * Every one of them, including those already set. Picking an accent is asking for this whole set of colours; a
     * field left alone because it had been chosen earlier would be the one that no longer matches, and finding out
     * which one that is means comparing five hex values by eye.
     *
     * <p>
     * The accent itself is not kept anywhere. It is how the colours were chosen, and once they are written there is
     * nothing left for it to say.
     */
    static void applyAccent(ActionSpec spec, ActionField field, String accent) {
        if (isBlank(accent)) {
            return;
        }
        WheelColors derived = derive(accent);
        for (String key : field.options) {
            spec.set(key, String.format("#%08X", Integer.valueOf(colorFor(key, derived))));
        }
        spec.set(field.key, "");
    }

    /** The set an accent produces, with the mod's coefficients deciding how far each sits from it. */
    static WheelColors derive(String accent) {
        return ColorConfig.defaultColors()
            .fromAccent(Colors.parseArgb(accent, 0xFFFFFF) & 0x00FFFFFF, AccentConfig.coefficients());
    }

    static int colorFor(String key, WheelColors colors) {
        if (ActionTypes.PARAM_HIGHLIGHT_COLOR.equals(key)) {
            return colors.highlight;
        }
        if (ActionTypes.PARAM_BORDER_COLOR.equals(key)) {
            return colors.border;
        }
        if (ActionTypes.PARAM_HIGHLIGHT_BORDER_COLOR.equals(key)) {
            return colors.highlightBorder;
        }
        if (ActionTypes.PARAM_BACKGROUND_COLOR.equals(key)) {
            return colors.background;
        }
        return colors.ring;
    }

    /**
     * Whether a colour is drawn at all, and so whether it can be edited.
     *
     * <p>
     * A colour whose part of the wheel is switched off in the config is shown but not editable - the value is kept,
     * and an accent still writes into it, so turning the feature back on brings back the colour that was chosen for
     * it. Hiding the row instead would make the wheel's colours move about as switches are flipped, and clearing the
     * value would quietly throw away a choice.
     */
    static boolean colorEnabled(String key) {
        return switchFor(key) == null;
    }

    /**
     * Why a colour cannot be edited: the name of the setting that switched its part of the wheel off.
     *
     * <p>
     * Named rather than described. "Switched off in the config" leaves the player looking for which of thirty
     * settings it was, and the answer is a word the config screen and the file both use.
     */
    static String colorOffTip(String key) {
        String setting = switchFor(key);
        return setting == null ? null : I18n.format("radialmenu.editor.colorOff.tip", setting);
    }

    /** The setting that has to be on for this colour to be drawn, or null when it is always drawn. */
    private static String switchFor(String key) {
        if (ActionTypes.PARAM_BORDER_COLOR.equals(key)) {
            return WheelConfig.drawOutline ? null : "drawOutline";
        }
        if (ActionTypes.PARAM_HIGHLIGHT_BORDER_COLOR.equals(key)) {
            return WheelConfig.drawHighlightOutline ? null : "drawHighlightOutline";
        }
        if (ActionTypes.PARAM_BACKGROUND_COLOR.equals(key)) {
            return WheelConfig.dimBackground ? null : "dimBackground";
        }
        return null;
    }

    /** What a colour field is actually drawn with when it is left blank: the profile's, or the mod's. */
    static int inheritedColor(String key) {
        return colorFor(key, StyleResolver.resolve(ColorConfig.defaultColors(), ProfileManager.active().style, null));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException broken) {
            return fallback;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim()
            .isEmpty();
    }
}
