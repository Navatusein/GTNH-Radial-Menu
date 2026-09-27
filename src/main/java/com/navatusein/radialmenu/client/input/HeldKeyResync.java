package com.navatusein.radialmenu.client.input;

import net.minecraft.client.settings.KeyBinding;

/**
 * Restores the press state of keys the player is physically holding when the wheel opens.
 *
 * <p>
 * Opening any screen runs {@code Minecraft.displayGuiScreen} -> {@code setIngameNotInFocus()} ->
 * {@code KeyBinding.unPressAllKeys()}, which clears every binding. Vanilla then only updates them again from keyboard
 * events, and a key that is already down produces no new event - so a player who opens the wheel while running would
 * simply stop running.
 *
 * <p>
 * Re-applying the physical state once, right after the screen opens, fixes that. It has to happen after the screen is
 * set, not before, or the unpress will undo it. Note this only works for bound keys: an unbound binding has no
 * physical state to read, which is fine because the player cannot be holding it either.
 */
public final class HeldKeyResync {

    private HeldKeyResync() {}

    /** Re-presses every binding whose physical key or mouse button is currently down. */
    public static void resyncHeldKeys() {
        for (KeyBinding binding : KeyBindingLookup.all()) {
            if (KeyInjector.isPhysicallyDown(binding)) {
                KeyInjector.hold(binding);
            }
        }
    }
}
