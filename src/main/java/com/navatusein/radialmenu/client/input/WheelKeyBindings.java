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

    /** Next profile in the sorted list. */
    public static KeyBinding nextProfile;

    /** Previous profile, for stepping back without cycling all the way round. */
    public static KeyBinding previousProfile;

    /** Opens the profile editor without going through the wheel or the chat command. */
    public static KeyBinding openEditor;

    private WheelKeyBindings() {}

    public static void register() {
        openWheel = new KeyBinding("key.radialmenu.open", Keyboard.KEY_R, CATEGORY);
        // Unbound by default: the mod exists to stop spending physical keys, so only the wheel claims one.
        nextProfile = new KeyBinding("key.radialmenu.nextProfile", Keyboard.KEY_NONE, CATEGORY);
        previousProfile = new KeyBinding("key.radialmenu.previousProfile", Keyboard.KEY_NONE, CATEGORY);
        openEditor = new KeyBinding("key.radialmenu.openEditor", Keyboard.KEY_NONE, CATEGORY);

        ClientRegistry.registerKeyBinding(openWheel);
        ClientRegistry.registerKeyBinding(nextProfile);
        ClientRegistry.registerKeyBinding(previousProfile);
        ClientRegistry.registerKeyBinding(openEditor);
    }
}
