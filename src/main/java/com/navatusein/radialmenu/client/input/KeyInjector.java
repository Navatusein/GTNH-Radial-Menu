package com.navatusein.radialmenu.client.input;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.navatusein.radialmenu.config.RadialMenuConfig;
import com.navatusein.radialmenu.mixins.early.KeyBindingAccessor;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.gameevent.InputEvent;

/**
 * Makes a {@link KeyBinding} look pressed even when no physical key is bound to it.
 *
 * <p>
 * Why the fields have to be written directly, confirmed against the 1.7.10 sources: both
 * {@code KeyBinding.setKeyBindState} and {@code KeyBinding.onTick} start with {@code if (keyCode != 0)} and then look
 * the binding up in a static map keyed by key code. An unbound binding has key code 0, so neither entry point can ever
 * reach it.
 *
 * <p>
 * Two fields matter, because mods read key state in two different ways:
 * <ul>
 * <li>{@code pressed} backs {@code getIsKeyPressed()} - "is it held right now", used for movement and hold-style
 * actions.</li>
 * <li>{@code pressTime} backs {@code isPressed()}, which returns true once per counted press and decrements. This is
 * what nearly every GTNH mod uses.</li>
 * </ul>
 *
 * <p>
 * Those mods read it from an {@link InputEvent.KeyInputEvent} handler on the FML bus, so bumping the counter is not
 * enough on its own - the event has to be posted too, or the handler never runs.
 */
public final class KeyInjector {

    /**
     * Key code lent to an unbound binding while an action holds it down.
     *
     * <p>
     * F15 because LWJGL defines it and no keyboard in use has one, so nothing can press it by accident and nothing
     * the player has bound can collide with it.
     */
    private static final int LENT_KEY_CODE = Keyboard.KEY_F15;

    /** Bindings currently carrying the lent code, by identity - two bindings can compare equal and must not share. */
    private static final Set<KeyBinding> LENT = Collections.newSetFromMap(new IdentityHashMap<KeyBinding, Boolean>());

    private KeyInjector() {}

    /**
     * Lends a key code to a binding that has none, for as long as the press lasts.
     *
     * <p>
     * Some mods never look past the key code. JourneyMap's {@code Constants.isPressed} returns false outright when
     * {@code getKeyCode() == 0} and never reads the press counter at all, so its zoom and minimap keys could not be
     * driven from the menu however correctly they were pressed - while its map toggle, which calls
     * {@code isPressed()} directly, worked fine. A code that exists but cannot be typed satisfies the check without
     * giving the binding a key anyone could hit.
     *
     * <p>
     * The static {@code KeyBinding.hash} map is deliberately left alone: it is keyed by the code registered at
     * startup, and rebuilding it would be a far bigger intrusion than borrowing a field. Nothing needs it here,
     * because the press is written to the binding directly rather than dispatched through the map.
     */
    private static void lendKeyCode(KeyBinding binding) {
        if (RadialMenuConfig.lendKeyCodeToUnbound && binding.getKeyCode() == 0 && LENT.add(binding)) {
            binding.setKeyCode(LENT_KEY_CODE);
        }
    }

    /** Gives the code back, so the binding reads as unbound again everywhere the player might look at it. */
    private static void returnKeyCode(KeyBinding binding) {
        if (LENT.remove(binding)) {
            binding.setKeyCode(0);
        }
    }

    private static KeyBindingAccessor access(KeyBinding binding) {
        return (KeyBindingAccessor) (Object) binding;
    }

    /**
     * Marks the binding as held and counts one press.
     *
     * <p>
     * The press counter is bumped exactly once, matching what a real key does: vanilla increments it from the keyboard
     * event, not every tick. Bumping it per tick would make {@code isPressed()} fire repeatedly and, for example, leave
     * a mod's flight toggle flickering on and off.
     */
    public static void press(KeyBinding binding) {
        lendKeyCode(binding);
        KeyBindingAccessor accessor = access(binding);
        accessor.radialmenu$setPressed(true);
        accessor.radialmenu$setPressTime(accessor.radialmenu$getPressTime() + 1);
    }

    /** Marks the binding as held without counting a press - for keeping a toggle or hold down. */
    public static void hold(KeyBinding binding) {
        access(binding).radialmenu$setPressed(true);
    }

    /** Clears both fields, exactly as vanilla's private {@code unpressKey()} does. */
    public static void release(KeyBinding binding) {
        KeyBindingAccessor accessor = access(binding);
        accessor.radialmenu$setPressed(false);
        accessor.radialmenu$setPressTime(0);
        returnKeyCode(binding);
    }

    public static boolean isHeld(KeyBinding binding) {
        return access(binding).radialmenu$isPressed();
    }

    /**
     * Posts a synthetic key input event so handlers that only run on input actually see the press.
     *
     * <p>
     * Must be called with the radial screen already closed: the event is delivered synchronously, and handlers both
     * check {@code inGameHasFocus} (AdventureBackpack2 does) and open GUIs of their own, which a still-open wheel would
     * fight with.
     */
    public static void fireInputEvent() {
        FMLCommonHandler.instance()
            .bus()
            .post(new InputEvent.KeyInputEvent());
    }

    /**
     * Whether the physical key or mouse button behind a binding is down right now.
     *
     * <p>
     * In 1.7.10 mouse buttons are stored as key codes below zero, offset by -100.
     */
    public static boolean isPhysicallyDown(int keyCode) {
        if (keyCode == 0) {
            return false;
        }
        if (keyCode > 0) {
            return keyCode < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(keyCode);
        }
        int button = keyCode + 100;
        return button >= 0 && button < Mouse.getButtonCount() && Mouse.isButtonDown(button);
    }

    public static boolean isPhysicallyDown(KeyBinding binding) {
        return isPhysicallyDown(binding.getKeyCode());
    }
}
