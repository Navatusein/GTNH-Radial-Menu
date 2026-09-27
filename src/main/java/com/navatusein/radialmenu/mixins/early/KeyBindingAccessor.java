package com.navatusein.radialmenu.mixins.early;

import net.minecraft.client.settings.KeyBinding;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Direct access to {@link KeyBinding}'s private press state.
 *
 * <p>
 * This is the one thing the whole mod is built on. A keybinding that is not bound to a physical key has
 * {@code keyCode == 0} and therefore never appears in {@code KeyBinding.hash}, so neither {@code setKeyBindState} nor
 * {@code onTick} can ever reach it - the fields have to be written directly.
 *
 * <p>
 * Names are the MCP ones; the mixin refmap remaps them to SRG at build time. Writing SRG names here would break the
 * development environment.
 */
@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {

    /** Backs {@code KeyBinding.getIsKeyPressed()} - the "is it held right now" flag. */
    @Accessor("pressed")
    void radialmenu$setPressed(boolean pressed);

    @Accessor("pressed")
    boolean radialmenu$isPressed();

    /** Backs {@code KeyBinding.isPressed()}, which returns true once per counted press and decrements. */
    @Accessor("pressTime")
    int radialmenu$getPressTime();

    @Accessor("pressTime")
    void radialmenu$setPressTime(int pressTime);
}
