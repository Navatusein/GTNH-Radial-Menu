package com.navatusein.radialmenu.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import com.navatusein.radialmenu.client.action.ActionExecutors;
import com.navatusein.radialmenu.client.gui.GuiRadialWheel;
import com.navatusein.radialmenu.client.profile.ProfileManager;

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
        ActionExecutors.runPending();

        if (WheelKeyBindings.cycleProfile.isPressed()) {
            ProfileManager.cycle(1);
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
