package com.navatusein.radialmenu.core.action;

/**
 * Which modifier keys an action wants held while it fires.
 *
 * <p>
 * Three booleans rather than a combined value, because they combine: a binding can want Ctrl and Shift at once, and a
 * single setting cycling through eight combinations is unreadable. Read as flags here so the client has one place to
 * ask and a test has something to ask it.
 *
 * <p>
 * What they are for: some mods read a modifier straight from the keyboard - {@code GuiScreen.isShiftKeyDown} is
 * {@code Keyboard.isKeyDown} and nothing else - so no keybinding, injected or otherwise, can tell them it is held.
 */
public final class Modifiers {

    public final boolean shift;
    public final boolean ctrl;
    public final boolean alt;

    public static final Modifiers NONE = new Modifiers(false, false, false);

    private Modifiers(boolean shift, boolean ctrl, boolean alt) {
        this.shift = shift;
        this.ctrl = ctrl;
        this.alt = alt;
    }

    public static Modifiers of(ActionSpec spec) {
        if (spec == null) {
            return NONE;
        }
        return new Modifiers(
            spec.getBoolean(ActionTypes.PARAM_SHIFT, false),
            spec.getBoolean(ActionTypes.PARAM_CTRL, false),
            spec.getBoolean(ActionTypes.PARAM_ALT, false));
    }

    /** Whether anything is asked for at all, so the common case costs nothing. */
    public boolean any() {
        return shift || ctrl || alt;
    }

    @Override
    public String toString() {
        if (!any()) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        if (shift) {
            text.append("shift");
        }
        if (ctrl) {
            text.append(text.length() > 0 ? "+ctrl" : "ctrl");
        }
        if (alt) {
            text.append(text.length() > 0 ? "+alt" : "alt");
        }
        return text.toString();
    }
}
