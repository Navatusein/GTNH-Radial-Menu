package com.navatusein.radialmenu.client.input;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.settings.KeyBinding;

import com.navatusein.radialmenu.core.action.Modifiers;

/**
 * Drives the three ways a menu entry can press a keybinding, and makes sure nothing stays stuck down.
 *
 * <p>
 * Injected presses are not backed by a physical key, so nothing will ever release them on its own. Every mode
 * therefore has a defined end, and {@link #releaseAll()} exists for the cases that bypass one - leaving a world,
 * disconnecting, or the player switching profiles mid-toggle.
 */
public final class KeybindStateTracker {

    public enum Mode {
        /** Press and release again on the next tick: one shot. */
        TAP,
        /** Stay down until the same entry is chosen again. */
        TOGGLE,
        /** Stay down for a fixed number of ticks. */
        HOLD
    }

    private static final class Held {

        Mode mode;
        int remainingTicks;

        /** Written into the key buffer again on every tick this stays held - a poll clears them each frame. */
        Modifiers modifiers = Modifiers.NONE;
    }

    private static final Map<KeyBinding, Held> HELD = new HashMap<>();

    private KeybindStateTracker() {}

    /** Runs the requested mode. Returns false if the binding could not be resolved. */
    public static boolean activate(KeyBinding binding, Mode mode, int holdTicks, Modifiers modifiers) {
        if (binding == null) {
            return false;
        }
        if (mode == Mode.TOGGLE && HELD.containsKey(binding)) {
            stop(binding);
            return true;
        }

        // Before the press, not after: a mod reading the modifier does it from inside the event posted below.
        ModifierKeys.hold(modifiers);
        KeyInjector.press(binding);
        KeyInjector.fireInputEvent();

        Held held = new Held();
        held.mode = mode;
        held.remainingTicks = mode == Mode.TAP ? 1 : Math.max(1, holdTicks);
        held.modifiers = modifiers == null ? Modifiers.NONE : modifiers;
        HELD.put(binding, held);
        return true;
    }

    /** True while an entry is holding this binding down - the editor shows it, and toggles render as active. */
    public static boolean isActive(KeyBinding binding) {
        return HELD.containsKey(binding);
    }

    public static void stop(KeyBinding binding) {
        Held held = HELD.remove(binding);
        if (held != null) {
            KeyInjector.release(binding);
            ModifierKeys.release(held.modifiers);
        }
    }

    public static void releaseAll() {
        for (Map.Entry<KeyBinding, Held> entry : HELD.entrySet()) {
            KeyInjector.release(entry.getKey());
            ModifierKeys.release(entry.getValue().modifiers);
        }
        HELD.clear();
    }

    /**
     * Ages every held binding by one tick. Toggles are refreshed rather than counted down, because vanilla's
     * {@code unPressAllKeys()} runs whenever any GUI opens and would otherwise silently drop them.
     */
    public static void onClientTick() {
        if (HELD.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<KeyBinding, Held>> iterator = HELD.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<KeyBinding, Held> entry = iterator.next();
            KeyBinding binding = entry.getKey();
            Held held = entry.getValue();

            if (held.mode == Mode.TOGGLE) {
                KeyInjector.hold(binding);
                ModifierKeys.hold(held.modifiers);
                continue;
            }

            if (--held.remainingTicks <= 0) {
                KeyInjector.release(binding);
                ModifierKeys.release(held.modifiers);
                iterator.remove();
            } else {
                KeyInjector.hold(binding);
                // Written again every tick, because the real keyboard poll clears the buffer on every frame.
                ModifierKeys.hold(held.modifiers);
            }
        }
    }
}
