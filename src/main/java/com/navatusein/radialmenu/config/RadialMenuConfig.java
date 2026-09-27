package com.navatusein.radialmenu.config;

import static com.navatusein.radialmenu.RadialMenuMod.MODID;

import com.gtnewhorizon.gtnhlib.config.Config;

/**
 * Scalar settings - look and feel, not menu contents.
 *
 * <p>
 * These live in the normal {@code config} folder so they can be edited from the mod list screen like everyone else's.
 * The menus themselves are separate files under the game folder, because they are structured data a player will want
 * to copy between installations.
 */
@Config(modid = MODID, configSubDirectory = "RadialMenu", filename = "general")
@Config.LangKey("radialmenu.config.general")
public class RadialMenuConfig {

    @Config.Comment("Execute the highlighted entry when the wheel key is released. Disable to require a click instead.")
    @Config.DefaultBoolean(true)
    @Config.Order(1)
    public static boolean releaseToSelect;

    @Config.Comment("Open the slot editor by right-clicking a slot, instead of shift-clicking it.")
    @Config.DefaultBoolean(false)
    @Config.Order(2)
    public static boolean rightClickToEdit;

    @Config.Comment("Keep feeding keyboard and mouse input to the game while the wheel is open, so you can keep moving.")
    @Config.DefaultBoolean(true)
    @Config.Order(3)
    public static boolean allowInputWhileOpen;

    @Config.Comment("Move the mouse cursor to the middle of the screen when the wheel opens.")
    @Config.DefaultBoolean(true)
    @Config.Order(4)
    public static boolean centerCursorOnOpen;

    @Config.Comment("Outer radius of the ring, in GUI pixels.")
    @Config.DefaultInt(80)
    @Config.RangeInt(min = 32, max = 240)
    @Config.Order(5)
    public static int outerRadius;

    @Config.Comment("Inner radius of the ring. Also the dead zone: pointing inside it selects nothing.")
    @Config.DefaultInt(32)
    @Config.RangeInt(min = 8, max = 200)
    @Config.Order(6)
    public static int innerRadius;

    @Config.Comment("Ring colour, 0xAARRGGBB.")
    @Config.DefaultString("0x99101010")
    @Config.Order(7)
    public static String ringColor;

    @Config.Comment("Colour of the highlighted sector, 0xAARRGGBB.")
    @Config.DefaultString("0xCC4A90D9")
    @Config.Order(8)
    public static String highlightColor;

    /** Guards against a hand-edited config where the hole is bigger than the ring. */
    public static int effectiveInnerRadius() {
        return Math.min(innerRadius, outerRadius - 8);
    }
}
