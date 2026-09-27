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
        INT,
        BOOLEAN,
        ENUM,
        /** Picked from the list of registered keybindings rather than typed. */
        KEYBIND_REF,
        /** Picked from the profile list. */
        PROFILE_REF,
        /** Picked with the colour picker; stored as #RRGGBB, blank meaning "inherit". */
        COLOR
    }

    public final String key;

    /** Translation key for the field's label. */
    public final String labelKey;

    public final Kind kind;

    public final String defaultValue;

    /** Allowed values, for {@link Kind#ENUM}. */
    public final List<String> options;

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
        boolean required) {
        this.key = key;
        this.labelKey = labelKey;
        this.kind = kind;
        this.defaultValue = defaultValue;
        this.options = options == null ? Collections.<String>emptyList() : options;
        this.required = required;
    }

    /** The same field, marked as one the action cannot run without. */
    public ActionField required() {
        return new ActionField(key, labelKey, kind, defaultValue, options, true);
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
        return new ActionField(key, labelKey, Kind.STRING, defaultValue, null, false);
    }

    public static ActionField multiline(String key, String labelKey, String defaultValue) {
        return new ActionField(key, labelKey, Kind.MULTILINE_STRING, defaultValue, null, false);
    }

    public static ActionField integer(String key, String labelKey, int defaultValue) {
        return new ActionField(key, labelKey, Kind.INT, Integer.toString(defaultValue), null, false);
    }

    public static ActionField bool(String key, String labelKey, boolean defaultValue) {
        return new ActionField(key, labelKey, Kind.BOOLEAN, Boolean.toString(defaultValue), null, false);
    }

    public static ActionField enumeration(String key, String labelKey, String defaultValue, String... options) {
        return new ActionField(key, labelKey, Kind.ENUM, defaultValue, Arrays.asList(options), false);
    }

    public static ActionField keybindRef(String key, String labelKey) {
        return new ActionField(key, labelKey, Kind.KEYBIND_REF, "", null, false);
    }

    public static ActionField profileRef(String key, String labelKey) {
        return new ActionField(key, labelKey, Kind.PROFILE_REF, "", null, false);
    }

    public static ActionField color(String key, String labelKey) {
        return new ActionField(key, labelKey, Kind.COLOR, "", null, false);
    }
}
