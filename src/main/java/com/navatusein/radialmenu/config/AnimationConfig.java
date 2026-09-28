package com.navatusein.radialmenu.config;

import static com.navatusein.radialmenu.RadialMenuMod.MODID;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.navatusein.radialmenu.core.animation.RevealAnimation;

/**
 * How the wheel moves.
 *
 * <p>
 * Every duration is in milliseconds and every one of them can be zero, which is how the animation is switched off
 * without losing the choice of which one it was.
 */
@Config(modid = MODID, configSubDirectory = "RadialMenu", filename = "general", category = "animation")
@Config.LangKey("radialmenu.config.animation")
public class AnimationConfig {

    @Config.Comment("How the wheel arrives: NONE, FADE, ZOOM, or one of the staggered orders STAGGER_CW, "
        + "STAGGER_CCW, STAGGER_BOTH.")
    @Config.DefaultEnum("ZOOM")
    @Config.Order(1)
    public static RevealAnimation revealAnimation;

    @Config.Comment("How long that takes, in milliseconds. A staggered wheel still finishes within it - the stagger "
        + "spends part of the time queueing rather than adding to it.")
    @Config.DefaultInt(180)
    @Config.RangeInt(min = 0, max = 2000)
    @Config.Order(2)
    public static int revealDurationMs;

    @Config.Comment("Push the sector under the cursor outwards, in GUI pixels. Zero leaves it where it is.")
    @Config.DefaultInt(4)
    @Config.RangeInt(min = 0, max = 20)
    @Config.Order(3)
    public static int hoverPush;

    @Config.Comment("How long that push takes, in milliseconds. Zero moves it at once.")
    @Config.DefaultInt(120)
    @Config.RangeInt(min = 0, max = 2000)
    @Config.Order(4)
    public static int hoverDurationMs;
}
