package com.navatusein.radialmenu.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Hides the crosshair while the wheel is open.
 *
 * <p>
 * 1.7.10 draws the crosshair from the HUD overlay without checking whether a screen is showing, so it sits in the
 * middle of the ring - right where the hovered entry's name goes. The rest of the HUD is deliberately left alone: a
 * player checking health or hunger before picking an action is the normal case.
 */
public class WheelOverlayHandler {

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            return;
        }
        if (Minecraft.getMinecraft().currentScreen instanceof GuiRadialWheel) {
            event.setCanceled(true);
        }
    }
}
