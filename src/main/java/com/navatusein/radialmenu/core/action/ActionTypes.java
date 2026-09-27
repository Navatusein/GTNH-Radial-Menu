package com.navatusein.radialmenu.core.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of known action types.
 *
 * <p>
 * Open by design: MineMenu's equivalent is a closed {@code enum}, which is why adding an action type there means
 * editing the core of the mod. Here a type is registered at startup and the editor picks it up automatically.
 */
public final class ActionTypes {

    /** Press a keybinding, whether or not it is bound to a physical key. */
    public static final String KEYBIND = "keybind";

    /** Switch the active profile. */
    public static final String PROFILE_SWITCH = "profileSwitch";

    /** Run the nested steps in order. */
    public static final String SEQUENCE = "sequence";

    /** Send one or more chat lines or slash commands. */
    public static final String COMMAND = "command";

    public static final String PARAM_BINDING = "binding";
    public static final String PARAM_CATEGORY = "category";
    public static final String PARAM_MODE = "mode";
    public static final String PARAM_HOLD_TICKS = "holdTicks";
    public static final String PARAM_PROFILE = "profile";
    public static final String PARAM_COMMAND = "command";
    public static final String PARAM_DELAY_TICKS = "delayTicks";
    public static final String PARAM_CYCLE = "cycle";

    private static final Map<String, ActionType> TYPES = new LinkedHashMap<>();

    private ActionTypes() {}

    public static void register(ActionType type) {
        TYPES.put(type.id, type);
    }

    public static ActionType get(String id) {
        return id == null ? null : TYPES.get(id);
    }

    public static boolean isRegistered(String id) {
        return id != null && TYPES.containsKey(id);
    }

    public static List<ActionType> all() {
        return Collections.unmodifiableList(new ArrayList<>(TYPES.values()));
    }

    public static void clear() {
        TYPES.clear();
    }

    /** Everything the editor offers today. Inventory and backpack actions arrive in later phases. */
    public static void registerDefaults() {
        register(
            new ActionType(
                KEYBIND,
                "radialmenu.action.keybind",
                false,
                ActionField.keybindRef(PARAM_BINDING, "radialmenu.action.keybind.binding"),
                ActionField.string(PARAM_CATEGORY, "radialmenu.action.keybind.category", ""),
                ActionField.enumeration(PARAM_MODE, "radialmenu.action.keybind.mode", "tap", "tap", "toggle", "hold"),
                ActionField.integer(PARAM_HOLD_TICKS, "radialmenu.action.keybind.holdTicks", 20)));

        register(
            new ActionType(
                PROFILE_SWITCH,
                "radialmenu.action.profileSwitch",
                false,
                ActionField.profileRef(PARAM_PROFILE, "radialmenu.action.profileSwitch.profile")));

        register(
            new ActionType(
                COMMAND,
                "radialmenu.action.command",
                false,
                ActionField.multiline(PARAM_COMMAND, "radialmenu.action.command.text", ""),
                ActionField.integer(PARAM_DELAY_TICKS, "radialmenu.action.command.delayTicks", 0),
                ActionField.bool(PARAM_CYCLE, "radialmenu.action.command.cycle", false)));

        register(
            new ActionType(
                SEQUENCE,
                "radialmenu.action.sequence",
                true,
                ActionField.integer(PARAM_DELAY_TICKS, "radialmenu.action.sequence.delayTicks", 0)));
    }
}
