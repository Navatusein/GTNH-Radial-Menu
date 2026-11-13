package com.navatusein.radialmenu.client.input;

import java.lang.reflect.Field;

import com.navatusein.radialmenu.RadialMenuMod;

/**
 * lwjgl3ify's own copy of the text the player typed, which a text field reads instead of the key event's character.
 *
 * <p>
 * SDL reports text separately from keys, and lwjgl3ify appends it to {@code TextFieldHandler.textBuffer} the moment
 * the window is polled - before any screen sees an event. {@code GuiTextField.textboxKeyTyped} then writes the whole
 * buffer when the next key carrying a printable character arrives, so text from an event a screen chose to ignore is
 * still typed, only later and in front of something else. Taking a character out of here is how an ignored event
 * stays ignored.
 *
 * <p>
 * Found by reflection, because lwjgl3ify is not a dependency; on plain LWJGL 2 there is no such buffer, the character
 * rides on the key event, and there is nothing to do.
 */
final class TypedTextBuffer {

    private static boolean resolved;

    private static StringBuilder buffer;

    private TypedTextBuffer() {}

    /** Removes the earliest occurrence of a character, the one an event being ignored put there. */
    static void remove(char character) {
        resolve();
        if (buffer == null) {
            return;
        }
        int index = buffer.indexOf(String.valueOf(character));
        if (index >= 0) {
            buffer.deleteCharAt(index);
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Field field = Class.forName("me.eigenraven.lwjgl3ify.client.TextFieldHandler")
                .getDeclaredField("textBuffer");
            field.setAccessible(true);
            buffer = (StringBuilder) field.get(null);
            RadialMenuMod.LOG.debug("Typed text is buffered by lwjgl3ify");
        } catch (ClassNotFoundException e) {
            // Plain LWJGL 2: the character comes with the key event, which is already being swallowed.
        } catch (Exception e) {
            RadialMenuMod.LOG.warn("lwjgl3ify's text buffer could not be reached, so a held key may still type: " + e);
        }
    }
}
