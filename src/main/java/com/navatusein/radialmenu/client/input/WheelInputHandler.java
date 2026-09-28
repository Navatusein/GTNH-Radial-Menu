package com.navatusein.radialmenu.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import com.navatusein.radialmenu.client.action.ActionExecutors;
import com.navatusein.radialmenu.client.action.WithoutScreen;
import com.navatusein.radialmenu.client.gui.GuiRadialWheel;
import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.editor.GuiProfileManager;
import com.navatusein.radialmenu.client.profile.ProfileManager;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Opens and closes the wheel, and drives the per-tick bookkeeping.
 *
 * <p>
 * The open key is polled from hardware rather than read through {@code isPressed()}, because hold-to-open needs to
 * know the key is still down, and {@code isPressed()} consumes the press instead of reporting a state. Polling also
 * keeps working while the wheel screen is up.
 */
public class WheelInputHandler {

    private boolean wheelKeyDown;

    /** Auto-binding runs once per world, on the first tick where a player exists. */
    private boolean autoBindApplied;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        if (mc.theWorld == null || mc.thePlayer == null) {
            // Leaving a world strands anything an entry was holding down, since no physical key will release it.
            if (wheelKeyDown) {
                wheelKeyDown = false;
            }
            autoBindApplied = false;
            KeybindStateTracker.releaseAll();
            return;
        }

        if (!autoBindApplied) {
            autoBindApplied = true;
            applyAutoBind(mc);
        }

        KeybindStateTracker.onClientTick();
        if (ActionExecutors.runPending() && WithoutScreen.wheelIsUp()) {
            // A mod that reads its keys from a tick handler - NEI reads at END - would find a screen open and skip
            // the press. isPressed() is a one-shot counter, so this tick is the only chance it has.
            WithoutScreen.armTickEnd();
        }
        GuiStack.openRequested();

        if (WheelKeyBindings.nextProfile.isPressed()) {
            ProfileManager.cycle(1);
        }
        if (WheelKeyBindings.previousProfile.isPressed()) {
            ProfileManager.cycle(-1);
        }
        if (WheelKeyBindings.openEditor.isPressed()) {
            // Deferred by a tick, like the chat command: opening a screen from here would be undone by whatever
            // closes right after the key is handled.
            GuiStack.requestOpen(new GuiProfileManager());
        }

        boolean down = KeyInjector.isPhysicallyDown(WheelKeyBindings.openWheel);
        if (down != wheelKeyDown) {
            wheelKeyDown = down;
            if (down) {
                openWheel(mc);
            } else {
                releaseWheel(mc);
            }
        }
    }

    /**
     * Hides the wheel for the END phase, around every other mod's handler.
     *
     * <p>
     * Two listeners rather than one, because the whole point is to be on both sides of everyone else: HIGHEST runs
     * before the mods that read keys there, LOWEST after them. Nothing renders between the phases of one tick, so
     * the substitution is never on screen.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTickEndFirst(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            WithoutScreen.beginTickEnd();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTickEndLast(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            WithoutScreen.endTickEnd();
        }
    }

    /**
     * Picks the profile bound to the world that was just joined.
     *
     * <p>
     * Read on the first tick rather than from a connection event, because the server data and the integrated server's
     * folder name are not both reliably in place until the client is actually in the world.
     */
    private void applyAutoBind(Minecraft mc) {
        String serverAddress = null;
        String worldName = null;

        if (mc.isSingleplayer()) {
            if (mc.getIntegratedServer() != null) {
                worldName = mc.getIntegratedServer()
                    .getFolderName();
            }
        } else {
            ServerData data = mc.func_147104_D();
            if (data != null) {
                serverAddress = data.serverIP;
            }
        }

        ProfileManager.applyAutoBind(serverAddress, worldName);
    }

    private void openWheel(Minecraft mc) {
        // Refuse to open on top of someone else's GUI - the wheel would steal a screen the player is using.
        if (mc.currentScreen != null) {
            return;
        }
        mc.displayGuiScreen(new GuiRadialWheel());
    }

    private void releaseWheel(Minecraft mc) {
        if (mc.currentScreen instanceof GuiRadialWheel) {
            ((GuiRadialWheel) mc.currentScreen).onWheelKeyReleased();
        }
    }
}
