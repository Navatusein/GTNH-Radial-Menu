package com.navatusein.radialmenu.client.input;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.navatusein.radialmenu.RadialMenuMod;

import cpw.mods.fml.client.registry.ClientRegistry;

/**
 * The mod's own keybindings.
 *
 * <p>
 * Two physical keys is the whole budget - the point of the mod is to stop spending them, so everything else is reached
 * through the wheel or the {@code /radialmenu} command.
 */
public final class WheelKeyBindings {

    public static final String CATEGORY = RadialMenuMod.MODNAME;

    public static KeyBinding openWheel;

    public static KeyBinding cycleProfile;

    private WheelKeyBindings() {}

    public static void register() {
        openWheel = new KeyBinding("key.radialmenu.open", Keyboard.KEY_R, CATEGORY);
        cycleProfile = new KeyBinding("key.radialmenu.cycleProfile", Keyboard.KEY_NONE, CATEGORY);

        ClientRegistry.registerKeyBinding(openWheel);
        ClientRegistry.registerKeyBinding(cycleProfile);
    }
}
