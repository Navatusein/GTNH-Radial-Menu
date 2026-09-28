package com.navatusein.radialmenu.core.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.navatusein.radialmenu.core.model.SlotLayout;

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

    /**
     * Open a nested wheel.
     *
     * <p>
     * Not executable - there is no executor for it. It exists as a type so the editor can present "what this slot
     * does" as one choice instead of a mode switch plus an action picker that had to agree with each other.
     */
    public static final String SUBMENU = "submenu";

    public static final String PARAM_BINDING = "binding";
    /**
     * Category of the chosen keybinding.
     *
     * <p>
     * Stored but never offered as a field: the picker fills it in, and it exists only to tell apart two mods that
     * registered the same description. Typing it by hand could only make the reference worse.
     */
    public static final String PARAM_CATEGORY = "category";
    public static final String PARAM_MODE = "mode";
    public static final String PARAM_HOLD_TICKS = "holdTicks";
    public static final String PARAM_PROFILE = "profile";
    public static final String PARAM_COMMAND = "command";
    public static final String PARAM_DELAY_TICKS = "delayTicks";
    public static final String PARAM_CYCLE = "cycle";
    public static final String PARAM_SLOT_MODE = "slotMode";
    public static final String PARAM_SLOT_COUNT = "slots";
    public static final String PARAM_ACCENT = "accent";
    public static final String PARAM_RING_COLOR = "ringColor";
    public static final String PARAM_HIGHLIGHT_COLOR = "highlightColor";
    public static final String PARAM_BORDER_COLOR = "borderColor";
    /** Not offered on a submenu - an entry owns its icon tint - but named here with the other colours. */
    public static final String PARAM_ICON_COLOR = "iconColor";
    public static final String PARAM_HIGHLIGHT_BORDER_COLOR = "highlightBorderColor";
    public static final String PARAM_BACKGROUND_COLOR = "backgroundColor";

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

    /**
     * Whether an action has the parameters its type needs.
     *
     * <p>
     * An unregistered type is reported complete: the executor lookup is what should complain about it, and a warning
     * about a missing parameter of a type nobody knows would only be misleading.
     */
    public static boolean isComplete(ActionSpec spec) {
        if (spec == null || spec.type == null) {
            return false;
        }
        ActionType type = get(spec.type);
        return type == null || type.isComplete(spec);
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
                ActionField.keybindRef(PARAM_BINDING, "radialmenu.action.keybind.binding")
                    .required(),
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
                ActionField.multiline(PARAM_COMMAND, "radialmenu.action.command.text", "")
                    .required(),
                ActionField.integer(PARAM_DELAY_TICKS, "radialmenu.action.command.delayTicks", 0),
                ActionField.bool(PARAM_CYCLE, "radialmenu.action.command.cycle", false)));

        register(
            new ActionType(
                SUBMENU,
                "radialmenu.action.submenu",
                false,
                ActionField.enumeration(PARAM_SLOT_MODE, "radialmenu.action.submenu.mode", "fixed", "fixed", "dynamic"),
                ActionField.range(
                    PARAM_SLOT_COUNT,
                    "radialmenu.action.submenu.slots",
                    SlotLayout.DEFAULT_SLOTS,
                    SlotLayout.MIN_SLOTS,
                    SlotLayout.MAX_SLOTS),
                ActionField.accent(
                    PARAM_ACCENT,
                    "radialmenu.action.submenu.accent",
                    PARAM_RING_COLOR,
                    PARAM_HIGHLIGHT_COLOR,
                    PARAM_BORDER_COLOR,
                    PARAM_HIGHLIGHT_BORDER_COLOR,
                    PARAM_BACKGROUND_COLOR),
                // A fill and its outline, twice: the ring, then the sector under the cursor. Changing one of a
                // pair almost always means changing the other, so they are adjacent.
                ActionField.color(PARAM_RING_COLOR, "radialmenu.action.submenu.ringColor"),
                ActionField.color(PARAM_BORDER_COLOR, "radialmenu.action.submenu.borderColor"),
                ActionField.color(PARAM_HIGHLIGHT_COLOR, "radialmenu.action.submenu.highlightColor"),
                ActionField.color(PARAM_HIGHLIGHT_BORDER_COLOR, "radialmenu.action.submenu.highlightBorderColor"),
                ActionField.color(PARAM_BACKGROUND_COLOR, "radialmenu.action.submenu.backgroundColor")));

        register(
            new ActionType(
                SEQUENCE,
                "radialmenu.action.sequence",
                true,
                ActionField.integer(PARAM_DELAY_TICKS, "radialmenu.action.sequence.delayTicks", 0)));
    }
}
