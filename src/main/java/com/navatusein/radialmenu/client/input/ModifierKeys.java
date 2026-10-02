package com.navatusein.radialmenu.client.input;

import org.lwjgl.input.Keyboard;

import com.navatusein.radialmenu.core.action.Modifiers;

/**
 * Holds Shift, Ctrl or Alt down for a mod that reads them from the keyboard rather than from a keybinding.
 *
 * <p>
 * The keybind injection cannot reach that case at all. {@code GuiScreen.isShiftKeyDown()} is
 * {@code Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54)} and nothing else - no binding is consulted, so no binding
 * can
 * be pressed to say yes. {@link KeyBuffer} is where the answer actually lives, and the left-hand key of each pair is
 * the
 * one written: a modifier has two codes and holding either satisfies every check in the game.
 */
public final class ModifierKeys {

    private ModifierKeys() {}

    /** Writes the wanted modifiers into the key buffer. Call once per tick while they should be held. */
    public static void hold(Modifiers modifiers) {
        if (modifiers == null || !modifiers.any()) {
            return;
        }
        if (modifiers.shift) {
            KeyBuffer.set(Keyboard.KEY_LSHIFT, true);
        }
        if (modifiers.ctrl) {
            KeyBuffer.set(Keyboard.KEY_LCONTROL, true);
        }
        if (modifiers.alt) {
            KeyBuffer.set(Keyboard.KEY_LMENU, true);
        }
    }

    /**
     * Clears what {@link #hold} wrote.
     *
     * <p>
     * Not strictly needed - the next poll would clear it anyway - but a press that ends mid-frame should not leave a
     * modifier reading as held for whatever draws next. The right-hand twin is checked first, so letting go of an
     * injected Shift does not contradict a Shift the player is actually holding.
     */
    public static void release(Modifiers modifiers) {
        if (modifiers == null || !modifiers.any()) {
            return;
        }
        if (modifiers.shift) {
            KeyBuffer.clearUnlessHeld(Keyboard.KEY_LSHIFT, Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
        }
        if (modifiers.ctrl) {
            KeyBuffer.clearUnlessHeld(Keyboard.KEY_LCONTROL, Keyboard.isKeyDown(Keyboard.KEY_RCONTROL));
        }
        if (modifiers.alt) {
            KeyBuffer.clearUnlessHeld(Keyboard.KEY_LMENU, Keyboard.isKeyDown(Keyboard.KEY_RMENU));
        }
    }
}
