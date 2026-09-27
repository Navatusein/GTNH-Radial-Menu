package com.navatusein.radialmenu.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

/**
 * Handles the two vanilla keybindings that injecting press state cannot reach.
 *
 * <p>
 * Most keybinding checks in {@code Minecraft.runTick} sit outside the keyboard loop and run every tick, so a
 * synthetic press works on them - {@code keyBindInventory}, {@code keyBindDrop}, {@code keyBindChat} and the hotbar
 * slots all do. But {@code keyBindTogglePerspective} and {@code keyBindSmoothCamera} are checked <em>inside</em>
 * {@code while (Keyboard.next())}, so vanilla only asks about them when a real key event arrives. No amount of field
 * writing produces one of those, and LWJGL's event queue cannot be posted to.
 *
 * <p>
 * Rather than let those two silently do nothing, their effect is applied directly. Injection is then skipped, so no
 * uncounted press is left behind for a later real keypress to consume.
 */
public final class VanillaKeyEffects {

    private VanillaKeyEffects() {}

    /**
     * Applies the effect if this is one of the unreachable bindings.
     *
     * @return true if handled, meaning the caller must not inject a press
     */
    public static boolean tryRun(KeyBinding binding) {
        Minecraft mc = Minecraft.getMinecraft();
        GameSettings settings = mc.gameSettings;
        if (settings == null || binding == null) {
            return false;
        }

        if (binding == settings.keyBindTogglePerspective) {
            settings.thirdPersonView++;
            if (settings.thirdPersonView > 2) {
                settings.thirdPersonView = 0;
            }
            return true;
        }

        if (binding == settings.keyBindSmoothCamera) {
            settings.smoothCamera = !settings.smoothCamera;
            return true;
        }

        return false;
    }
}
