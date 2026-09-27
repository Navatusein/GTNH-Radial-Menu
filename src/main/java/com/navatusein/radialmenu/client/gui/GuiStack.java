package com.navatusein.radialmenu.client.gui;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * Back-navigation for the editor screens.
 *
 * <p>
 * Vanilla's {@code GuiScreen} has no notion of a parent, so without this every editor screen would have to know which
 * screen opened it. Pushing instead means a picker can be opened from anywhere and still return to the right place.
 */
public final class GuiStack {

    private static final Deque<GuiScreen> STACK = new ArrayDeque<>();

    private GuiStack() {}

    /** Opens a screen, remembering the current one to come back to. */
    public static void push(GuiScreen screen) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) {
            STACK.push(mc.currentScreen);
        }
        mc.displayGuiScreen(screen);
    }

    /** Returns to the screen that opened the current one, or closes if there is none. */
    public static void pop() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.displayGuiScreen(STACK.isEmpty() ? null : STACK.pop());
    }

    /** Closes the whole editor, discarding the trail. */
    public static void closeAll() {
        STACK.clear();
        Minecraft.getMinecraft()
            .displayGuiScreen(null);
    }

    public static boolean hasParent() {
        return !STACK.isEmpty();
    }
}
