package com.navatusein.radialmenu.client.action;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.navatusein.radialmenu.client.gui.GuiRadialWheel;
import com.navatusein.radialmenu.core.action.ActionSpec;

/**
 * Hides the wheel from mods that refuse to act while a screen is open.
 *
 * <p>
 * Everything the wheel fires normally runs with the wheel already closed, because a receiver may well check: NEI
 * returns immediately from {@code WorldOverlayRenderer.tickKeyStates} while {@code currentScreen != null}, and
 * AdventureBackpack2 checks {@code inGameHasFocus}. An entry marked {@code keepOpen} has no such moment - the whole
 * point is that the wheel stays - so those entries did nothing, with nothing in the log to say why. The injection had
 * genuinely succeeded; the receiver declined.
 *
 * <p>
 * There are two moments to cover, because mods read a key in two places:
 * <ul>
 * <li>Inside the {@code KeyInputEvent} posted during the injection - {@link #runAction}.</li>
 * <li>In their own {@code ClientTickEvent} handler at phase END, which is where NEI reads - {@link #beginTickEnd} and
 * {@link #endTickEnd}, armed for the one tick a press was made.</li>
 * </ul>
 *
 * <p>
 * The fields are written directly rather than through {@code displayGuiScreen}, which would unpress every binding,
 * re-run {@code initGui}, and grab and release the mouse - re-centring the cursor, which on this screen is the
 * player's aim. Nothing renders between the two phases of one tick, so no frame ever sees the substitution.
 *
 * <p>
 * <b>The restore is conditional, and that is the whole trick.</b> A handler may open a GUI of its own -
 * AdventureBackpack2 opens the backpack - and putting the wheel back unconditionally would clobber it, which is
 * exactly the bug that made "close before injecting" the rule everywhere else. Restoring only when the screen is
 * still clear leaves that case alone: the mod's GUI stays up and the wheel is gone, which is what the player asked
 * for. A handler that opened something also cleared {@code inGameHasFocus} itself on the way in.
 */
public final class WithoutScreen {

    private WithoutScreen() {}

    /** Set while the END phase of this tick should run as though no screen were open. */
    private static boolean veilTickEnd;

    private static GuiScreen hiddenScreen;
    private static boolean hiddenFocus;

    /**
     * Whether the screen in the way is the wheel's own.
     *
     * <p>
     * Deliberately only the wheel. Any other screen - a chest, the chat box - is the player's doing and none of this
     * mod's business to lie about; an action firing while one of those is open should see the world as it is.
     */
    public static boolean wheelIsUp() {
        return Minecraft.getMinecraft().currentScreen instanceof GuiRadialWheel;
    }

    public static void runAction(ActionSpec spec) {
        Minecraft mc = Minecraft.getMinecraft();

        GuiScreen screen = mc.currentScreen;
        boolean hadFocus = mc.inGameHasFocus;

        mc.currentScreen = null;
        mc.inGameHasFocus = true;
        try {
            // Straight to the execution: going back through runNow would find the screen already cleared and simply
            // call this again.
            ActionExecutors.execute(spec);
        } finally {
            if (mc.currentScreen == null) {
                mc.currentScreen = screen;
                mc.inGameHasFocus = hadFocus;
            }
        }
    }

    /**
     * Asks for this tick's END phase to run with the wheel hidden.
     *
     * <p>
     * Armed only for the tick a press was actually made. {@code isPressed()} is a one-shot counter, so that is the
     * only tick a tick-phase reader can consume it in, and holding the lie open for longer would show every other
     * mod's END handler a world with no screen in it for no reason.
     */
    public static void armTickEnd() {
        veilTickEnd = true;
    }

    /** Called at END phase before any other mod's handler. */
    public static void beginTickEnd() {
        if (!veilTickEnd) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiRadialWheel)) {
            veilTickEnd = false;
            return;
        }
        hiddenScreen = mc.currentScreen;
        hiddenFocus = mc.inGameHasFocus;
        mc.currentScreen = null;
        mc.inGameHasFocus = true;
    }

    /** Called at END phase after every other mod's handler. */
    public static void endTickEnd() {
        if (!veilTickEnd) {
            return;
        }
        veilTickEnd = false;
        if (hiddenScreen == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null) {
            mc.currentScreen = hiddenScreen;
            mc.inGameHasFocus = hiddenFocus;
        }
        hiddenScreen = null;
    }
}
