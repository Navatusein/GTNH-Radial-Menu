package com.navatusein.radialmenu.client.action;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.navatusein.radialmenu.client.gui.GuiRadialWheel;
import com.navatusein.radialmenu.core.action.ActionSpec;

/**
 * Runs an action as though no screen were open, for an entry that keeps the wheel up.
 *
 * <p>
 * Everything the wheel fires normally runs with the wheel already closed, because a mod on the receiving end may well
 * refuse to act otherwise: NEI only handles its overlay keys when no screen is open, and AdventureBackpack2 checks
 * {@code inGameHasFocus} first. An entry marked {@code keepOpen} has no such moment - the whole point is that the
 * wheel stays - so those entries silently did nothing, with nothing in the log to say why. The injection had
 * genuinely succeeded; the receiver declined.
 *
 * <p>
 * So the two flags are set aside for the length of the call and put back afterwards. The fields are written directly
 * rather than through {@code displayGuiScreen}: that would run {@code onGuiClosed}, re-run {@code initGui} on the way
 * back, grab and release the mouse - moving the cursor off the sector the player is aiming at - and unpress every
 * binding. None of that is wanted for a screen that is not actually going anywhere. Nothing renders during a client
 * tick, so no frame ever observes the substitution.
 *
 * <p>
 * <b>The restore is conditional, and that is the whole trick.</b> A handler may open a GUI of its own - AB2 opens the
 * backpack - and putting the wheel back unconditionally would clobber it, which is exactly why the wheel closes
 * before injecting everywhere else. Restoring only when the screen is still clear leaves that case alone: the mod's
 * GUI stays up and the wheel is gone, which is what the player asked for. A handler that opened something also
 * cleared {@code inGameHasFocus} itself on the way in, so that flag needs no repair either.
 */
public final class WithoutScreen {

    private WithoutScreen() {}

    /**
     * Whether the screen in the way is the wheel's own.
     *
     * <p>
     * Deliberately only the wheel. Any other screen - a chest, the chat box - is the player's doing and none of this
     * mod's business to lie about; an action that fires while one of those is open should see the world as it is.
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
}
