package com.navatusein.radialmenu.client.input;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

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

    private KeyInjector() {}

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
