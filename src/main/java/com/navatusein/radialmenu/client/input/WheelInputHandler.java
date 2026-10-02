package com.navatusein.radialmenu.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import com.navatusein.radialmenu.client.action.ActionExecutors;
import com.navatusein.radialmenu.client.action.SneakDiagnostics;
import com.navatusein.radialmenu.client.action.WithoutScreen;
import com.navatusein.radialmenu.client.gui.GuiRadialWheel;
import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.editor.GuiProfileManager;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.client.script.ScriptHost;

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

    /**
     * Runs at {@code LOWEST} so it comes after {@link WithoutScreen#endTick}, and so the screen is its real self
     * again by the time this opens or closes the wheel.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
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
            // A script waiting for a chat line it will never get now, on a server it is no longer talking to.
            if (ScriptHost.hasRunning()) {
                ScriptHost.cancelAll("left the world");
            }
            return;
        }

        if (!autoBindApplied) {
            autoBindApplied = true;
            applyAutoBind(mc);
        }

        KeybindStateTracker.onClientTick();
        if (ActionExecutors.runPending() && WithoutScreen.wheelIsUp()) {
            // A mod reading its keys from a tick handler would find a screen open and skip the press. NEI reads at
            // phase START, before this listener, so the wheel has to be hidden on the tick after the press - which
            // is fine, because isPressed() is a counter that waits until something takes it.
            WithoutScreen.armNextTick();
        }
        // After runPending, and that order is load-bearing: a choice made last tick is applied by the resume executor
        // in the queue above, so by the time the host looks, a wheel that closed because of it is not read as a wheel
        // the player dismissed.
        ScriptHost.onClientTick();

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
     * Hides the wheel around every other mod's tick handler.
     *
     * <p>
     * Two listeners, on either side of the ordinary ones: HIGH before the mods that read keys there, LOW after them.
     * Phase START is where it matters - NEI's handler returns immediately on END - but both are wrapped, because a
     * mod is free to read in either and the cost of covering both is one boolean.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onTickFirst(TickEvent.ClientTickEvent event) {
        WithoutScreen.beginTick();
    }

    /**
     * Brackets the tick from the outside, for the diagnostic only.
     *
     * <p>
     * Three readings pin down where a press is being lost: at the very start of a tick it is whatever survived the one
     * before, at the very start of the end phase it is whatever the tick body left, and the reading at {@code LOW} is
     * what the other mods' own end-of-tick handlers left behind.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTickProbe(TickEvent.ClientTickEvent event) {
        if (!KeybindStateTracker.hasHeld()) {
            return;
        }
        SneakDiagnostics.log(
            event.phase == TickEvent.Phase.START ? "start first" : "end first",
            Minecraft.getMinecraft().gameSettings.keyBindSneak);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onTickLast(TickEvent.ClientTickEvent event) {
        WithoutScreen.endTick(event.phase == TickEvent.Phase.START);
        if (event.phase == TickEvent.Phase.END && KeybindStateTracker.hasHeld()) {
            // After the world has ticked, so the player's own movement update has already read whatever it reads.
            SneakDiagnostics.log("end last", Minecraft.getMinecraft().gameSettings.keyBindSneak);
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
