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
 * Two moments are covered, because a mod can read a key in two places:
 * <ul>
 * <li>Inside the {@code KeyInputEvent} posted during the injection - {@link #runAction}.</li>
 * <li>In its own {@code ClientTickEvent} handler, which is where NEI reads - {@link #beginTick} and {@link #endTick},
 * wrapped around the tick after a press was made.</li>
 * </ul>
 *
 * <p>
 * The tick handlers sit at {@code HIGH} and {@code LOW} so an ordinary {@code NORMAL} handler runs between them, and
 * the mod's own listener is at {@code LOWEST} so it runs after the screen is back. NEI does its work at phase
 * <b>START</b> - its handler returns immediately on END - and that detail is the whole fix: wrapping the END phase
 * instead, which is what a first reading suggested, put the screen back long before NEI ever looked.
 *
 * <p>
 * The fields are written directly rather than through {@code displayGuiScreen}, which would unpress every binding,
 * re-run {@code initGui}, and grab and release the mouse - re-centring the cursor, which on this screen is the
 * player's aim. Nothing renders inside a tick phase, so no frame ever sees the substitution.
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

    /**
     * Set when a press was made while the wheel was up, so the next tick runs with the wheel hidden.
     *
     * <p>
     * The next tick, not this one: a press made in the mod's own listener has already missed the readers that ran
     * earlier in the same phase, and {@code isPressed()} is a counter that survives until something takes it.
     */
    private static boolean armed;

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

    /** Asks for the next tick to run with the wheel hidden. */
    public static void armNextTick() {
        armed = true;
    }

    /** Called at the start of a tick phase, before any ordinary handler. */
    public static void beginTick() {
        if (!armed || hiddenScreen != null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiRadialWheel)) {
            armed = false;
            return;
        }
        hiddenScreen = mc.currentScreen;
        hiddenFocus = mc.inGameHasFocus;
        mc.currentScreen = null;
        mc.inGameHasFocus = true;
    }

    /**
     * Called after every ordinary handler has had its turn.
     *
     * @param startPhase whether this was phase START, which is the one that has to happen before the arming is
     *                   spent - the END phase of the same tick comes first, and clearing it there would leave the
     *                   wheel in plain sight for the reader that actually matters
     */
    public static void endTick(boolean startPhase) {
        if (hiddenScreen == null) {
            return;
        }
        if (startPhase) {
            armed = false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null) {
            mc.currentScreen = hiddenScreen;
            mc.inGameHasFocus = hiddenFocus;
        }
        hiddenScreen = null;
    }
}
