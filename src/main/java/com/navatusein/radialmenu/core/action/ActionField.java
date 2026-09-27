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
        PROFILE_REF
    }

    public final String key;

    /** Translation key for the field's label. */
    public final String labelKey;

    public final Kind kind;

    public final String defaultValue;

    /** Allowed values, for {@link Kind#ENUM}. */
    public final List<String> options;

    private ActionField(String key, String labelKey, Kind kind, String defaultValue, List<String> options) {
        this.key = key;
        this.labelKey = labelKey;
        this.kind = kind;
        this.defaultValue = defaultValue;
        this.options = options == null ? Collections.<String>emptyList() : options;
    }

    public static ActionField string(String key, String labelKey, String defaultValue) {
        return new ActionField(key, labelKey, Kind.STRING, defaultValue, null);
    }

    public static ActionField multiline(String key, String labelKey, String defaultValue) {
        return new ActionField(key, labelKey, Kind.MULTILINE_STRING, defaultValue, null);
    }

    public static ActionField integer(String key, String labelKey, int defaultValue) {
        return new ActionField(key, labelKey, Kind.INT, Integer.toString(defaultValue), null);
    }

    public static ActionField bool(String key, String labelKey, boolean defaultValue) {
        return new ActionField(key, labelKey, Kind.BOOLEAN, Boolean.toString(defaultValue), null);
    }

    public static ActionField enumeration(String key, String labelKey, String defaultValue, String... options) {
        return new ActionField(key, labelKey, Kind.ENUM, defaultValue, Arrays.asList(options));
    }

    public static ActionField keybindRef(String key, String labelKey) {
        return new ActionField(key, labelKey, Kind.KEYBIND_REF, "", null);
    }

    public static ActionField profileRef(String key, String labelKey) {
        return new ActionField(key, labelKey, Kind.PROFILE_REF, "", null);
    }
}
