package com.navatusein.radialmenu.core.action;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Describes one editable parameter of an action type.
 *
 * <p>
 * The editor builds its widgets from these descriptors instead of hand-writing a screen per action type, so phase 2's
 * command action needs no new GUI class.
 */
public class ActionField {

    public enum Kind {
        STRING,
        MULTILINE_STRING,
        /**
         * Source code, edited in the code editor rather than as a column of one-line fields.
         *
         * <p>
         * Stored exactly like {@link #MULTILINE_STRING} - one string with newlines - and separate from it because the
         * two want different screens. Lines of commands are a numbered list, where the number is the order they are
         * sent in; thirty lines of Lua want indentation, a caret that moves between lines, and colour.
         */
        CODE,
        INT,
        BOOLEAN,
        ENUM,
        /** Picked from the list of registered keybindings rather than typed. */
        KEYBIND_REF,
        /** Picked from the profile list. */
        PROFILE_REF,
        /** Picked with the colour picker; stored as #RRGGBB, blank meaning "inherit". */
        COLOR,
        /**
         * A colour that fills in other fields, and is not stored itself.
         *
         * <p>
         * Picked without the opacity strip, because an accent is a hue and nothing else - every opacity comes from
         * the coefficients in the mod's config. Choosing one writes the colours it produces into the keys in
         * {@link ActionField#options} there and then, which is what lets the editor offer it without knowing what a
         * wheel is, and what keeps the accent out of the saved file: it is a way of choosing colours, not one of
         * them.
         */
        ACCENT
    }

    public final String key;

    /** Translation key for the field's label. */
    public final String labelKey;

    public final Kind kind;

    public final String defaultValue;

    /** Allowed values for {@link Kind#ENUM}, or the keys an {@link Kind#ACCENT} derives. */
    public final List<String> options;

    /**
     * Range of an {@link Kind#INT}, drawn as a slider when there is one.
     *
     * <p>
     * A number with ends worth knowing is easier to choose by dragging than to type, and a slider cannot be left
     * holding something the action will refuse. {@code min == max} means no range, and the field stays a text box.
     */
    public final int min;

    public final int max;

    /**
     * Whether the action is meaningless without this parameter.
     *
     * <p>
     * A keybinding action with no keybinding cannot do anything, and as a step of a chain it would only log a
     * warning every time the chain ran. Marked here rather than guessed from the kind, because a blank is a real
     * choice for some fields - an empty profile name means "the next one".
     */
    public final boolean required;

    /**
     * Translation key of the localized name of one enum value: {@code <labelKey>.<value>}.
     *
     * <p>
     * Raw values like {@code dynamic} read as leftover data next to properly cased labels, so they are translated
     * for display while the stored value stays lower case.
     */
    public String valueLabelKey(String value) {
        return labelKey + "." + value;
    }

    /** Translation key of this field's tooltip. */
    public String tooltipKey() {
        return labelKey + ".tip";
    }

    private ActionField(String key, String labelKey, Kind kind, String defaultValue, List<String> options,
        boolean required, int min, int max) {
        this.key = key;
        this.labelKey = labelKey;
        this.kind = kind;
        this.defaultValue = defaultValue;
        this.options = options == null ? Collections.<String>emptyList() : options;
        this.required = required;
        this.min = min;
        this.max = max;
    }

    /** The same field, marked as one the action cannot run without. */
    public ActionField required() {
        return new ActionField(key, labelKey, kind, defaultValue, options, true, min, max);
    }

    /** Whether this number is worth dragging rather than typing. */
    public boolean hasRange() {
        return kind == Kind.INT && max > min;
    }

    /** Whether this parameter is missing from a spec that is supposed to carry it. */
    public boolean isMissingFrom(ActionSpec spec) {
        if (!required) {
            return false;
        }
        String value = spec.getString(key, null);
        return value == null || value.trim()
            .isEmpty();
    }

    public static ActionField string(String key, String labelKey, String defaultValue) {
        return new ActionField(key, labelKey, Kind.STRING, defaultValue, null, false, 0, 0);
    }

    public static ActionField multiline(String key, String labelKey, String defaultValue) {
        return new ActionField(key, labelKey, Kind.MULTILINE_STRING, defaultValue, null, false, 0, 0);
    }

    public static ActionField code(String key, String labelKey, String defaultValue) {
        return new ActionField(key, labelKey, Kind.CODE, defaultValue, null, false, 0, 0);
    }

    public static ActionField integer(String key, String labelKey, int defaultValue) {
        return new ActionField(key, labelKey, Kind.INT, Integer.toString(defaultValue), null, false, 0, 0);
    }

    /** A number with ends, drawn as a slider. */
    public static ActionField range(String key, String labelKey, int defaultValue, int min, int max) {
        return new ActionField(key, labelKey, Kind.INT, Integer.toString(defaultValue), null, false, min, max);
    }

    public static ActionField bool(String key, String labelKey, boolean defaultValue) {
        return new ActionField(key, labelKey, Kind.BOOLEAN, Boolean.toString(defaultValue), null, false, 0, 0);
    }

    public static ActionField enumeration(String key, String labelKey, String defaultValue, String... options) {
        return new ActionField(key, labelKey, Kind.ENUM, defaultValue, Arrays.asList(options), false, 0, 0);
    }

    public static ActionField keybindRef(String key, String labelKey) {
        return new ActionField(key, labelKey, Kind.KEYBIND_REF, "", null, false, 0, 0);
    }

    public static ActionField profileRef(String key, String labelKey) {
        return new ActionField(key, labelKey, Kind.PROFILE_REF, "", null, false, 0, 0);
    }

    public static ActionField color(String key, String labelKey) {
        return new ActionField(key, labelKey, Kind.COLOR, "", null, false, 0, 0);
    }

    /**
     * A colour the given keys are derived from.
     *
     * @param derives the keys this accent fills in, so the editor can offer to turn it into them
     */
    public static ActionField accent(String key, String labelKey, String... derives) {
        return new ActionField(key, labelKey, Kind.ACCENT, "", Arrays.asList(derives), false, 0, 0);
    }
}
