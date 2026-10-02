package com.navatusein.radialmenu.client.input;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import org.lwjgl.input.Keyboard;

import com.navatusein.radialmenu.RadialMenuMod;

/**
 * Whatever the game currently believes about which keys are physically down.
 *
 * <p>
 * Some readers never consult a keybinding. {@code GuiScreen.isShiftKeyDown()} is {@code Keyboard.isKeyDown} and
 * nothing else; GTNH's Hodgepodge resynchronises every binding from {@code Keyboard.isKeyDown} whenever a screen
 * closes; and Controlling rewrites {@code getIsKeyPressed()} to read {@code Keyboard.isKeyDown} for any binding whose
 * key <em>is</em> a modifier - which is what {@code key.sneak} is, since it lives on Shift. An injected press that
 * does not reach this store is invisible to all three.
 *
 * <p>
 * <b>There are two stores, and the pack uses the second.</b> Plain LWJGL 2 keeps a private {@code keyDownBuffer}
 * indexed by key code. GTNH runs lwjgl3ify, where {@code org.lwjgl.input.Keyboard} is a forwarder to
 * {@code org.lwjglx.input.Keyboard}, and the state is a public {@code sdlKeyPressedArray} indexed by SDL scancode -
 * so the index has to be translated through {@code KeyCodes.lwjglToSdlScancode} or the byte lands on an unrelated key.
 * Both are found by reflection and the first one that exists wins; a dev client takes the first branch and the pack
 * the second.
 *
 * <p>
 * <b>A write lasts one frame.</b> Whichever store it is, the real keyboard poll refills it, so anything held this way
 * is written again every tick. That is also what makes it safe: nothing can be jammed down longer than a frame, and a
 * key the player is really holding comes back on the next poll whatever was done to it.
 */
public final class KeyBuffer {

    private static boolean resolved;

    /** The field holding the byte array, in whichever of the two shapes this installation has. */
    private static Field store;

    /** Key code to index, for the SDL-backed store. Null when the index is the key code itself. */
    private static Method scancodeOf;

    private KeyBuffer() {}

    /** Whether anything can be written at all. Presses still work without it, only less convincingly. */
    public static boolean isAvailable() {
        resolve();
        return store != null;
    }

    /** True for a key code this can write: a keyboard key, not a mouse button and not "unbound". */
    public static boolean isKeyboardCode(int keyCode) {
        return keyCode > 0 && keyCode < Keyboard.KEYBOARD_SIZE;
    }

    public static void set(int keyCode, boolean down) {
        if (!isKeyboardCode(keyCode)) {
            return;
        }
        resolve();
        if (store == null) {
            return;
        }

        try {
            ByteBuffer keys = (ByteBuffer) store.get(null);
            if (keys == null) {
                return;
            }
            int index = index(keyCode);
            if (index <= 0 || index >= keys.limit()) {
                return;
            }
            keys.put(index, (byte) (down ? 1 : 0));
        } catch (Exception e) {
            // Whatever shape the store turned out to have, it is not one that can be written. Say so once.
            store = null;
            RadialMenuMod.LOG.warn("The key state store cannot be written, so modifier keys will not be held: " + e);
        }
    }

    /**
     * Clears a key unless it is genuinely held.
     *
     * <p>
     * Only useful where the key being checked is not the one being written - letting go of an injected left Shift while
     * the player holds the right one, say. Asking about the key we wrote ourselves would only read our own answer back.
     */
    public static void clearUnlessHeld(int keyCode, boolean physicallyHeld) {
        if (!physicallyHeld) {
            set(keyCode, false);
        }
    }

    private static int index(int keyCode) throws Exception {
        if (scancodeOf == null) {
            return keyCode;
        }
        return ((Integer) scancodeOf.invoke(null, Integer.valueOf(keyCode))).intValue();
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;

        store = field("org.lwjgl.input.Keyboard", "keyDownBuffer");
        if (store != null) {
            RadialMenuMod.LOG.debug("Key states come from LWJGL 2's keyDownBuffer");
            return;
        }

        // lwjgl3ify: the forwarder has no buffer of its own, and the real one is indexed by SDL scancode.
        store = field("org.lwjglx.input.Keyboard", "sdlKeyPressedArray");
        if (store == null) {
            RadialMenuMod.LOG.warn("No key state store found; modifier keys and sneak injection will not be held");
            return;
        }
        try {
            scancodeOf = Class.forName("org.lwjglx.input.KeyCodes")
                .getDeclaredMethod("lwjglToSdlScancode", int.class);
            scancodeOf.setAccessible(true);
            RadialMenuMod.LOG.debug("Key states come from lwjgl3ify's sdlKeyPressedArray");
        } catch (Exception e) {
            // The store without its index is worse than nothing: every write would land on the wrong key.
            store = null;
            RadialMenuMod.LOG.warn("lwjgl3ify's key store was found but its scancode mapping was not: " + e);
        }
    }

    private static Field field(String className, String fieldName) {
        try {
            Field field = Class.forName(className)
                .getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            return null;
        }
    }
}
