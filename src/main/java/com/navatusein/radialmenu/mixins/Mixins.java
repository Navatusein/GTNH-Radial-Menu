package com.navatusein.radialmenu.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

public enum Mixins implements IMixins {

    /**
     * {@code KeyBinding} is loaded together with {@code GameSettings} inside {@code Minecraft.startGame()}, long before
     * preInit, so the accessor has to be applied in the early phase.
     *
     * <p>
     * Class names here are relative to the package declared in {@code mixins.radialmenu.early.json}.
     */
    KEYBINDING_ACCESS(new MixinBuilder("KeyBinding press-state access").addClientMixins("KeyBindingAccessor")
        .setPhase(Phase.EARLY));

    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @Nonnull
    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
