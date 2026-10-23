package com.navatusein.radialmenu.client.input;

import org.lwjgl.input.Keyboard;

/**
 * Keeps a key that was already down when a screen opened from typing into it.
 *
 * <p>
 * The case that needed it: a script's prompt opens while the wheel key is still held, and the key auto-repeats into the
 * text box until it is let go. Screens enable repeat events, so LWJGL hands the held key over again and again - and
 * under lwjgl3ify the character does not even ride on the key event: SDL sends the key, then a text event of its own
 * with key code {@code KEY_NONE}, which no check on the key code can recognise. That text event always directly follows
 * the key event it came from, so the one after a swallowed key is swallowed with it.
 *
 * <p>
 * Only keys that produce events are affected. A key code held down by {@link KeyBuffer} for an injected press never
 * produces one, so it cannot block the player from typing while that press lasts.
 */
public final class KeysHeldOnOpen {

    private final boolean[] held = new boolean[256];

    private boolean swallowNextText;

    /** Notes every key down at this moment. Call when the screen is created, before it receives any input. */
    public KeysHeldOnOpen() {
        for (int key = 1; key < held.length; key++) {
            held[key] = Keyboard.isKeyDown(key);
        }
    }

    /**
     * Whether the keyboard event being handled belongs to a key held since the screen opened, and should be ignored.
     *
     * <p>
     * A key stops counting once it is seen up - by its own release event, or by being up when any other event
     * arrives, which covers a release this screen never received. A press is not taken as proof of a release: the
     * press that opened the screen can still be in the queue the first time the screen reads it.
     */
    public boolean swallow() {
        int key = Keyboard.getEventKey();
        if (key == Keyboard.KEY_NONE) {
            boolean text = swallowNextText;
            swallowNextText = false;
            return text;
        }
        swallowNextText = false;

        for (int other = 1; other < held.length; other++) {
            if (held[other] && other != key && !Keyboard.isKeyDown(other)) {
                held[other] = false;
            }
        }
        if (key < 0 || key >= held.length || !held[key]) {
            return false;
        }
        if (!Keyboard.getEventKeyState()) {
            held[key] = false;
            return true;
        }
        swallowNextText = true;
        return true;
    }
}
