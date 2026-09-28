package com.navatusein.radialmenu.config;

import static com.navatusein.radialmenu.RadialMenuMod.MODID;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.navatusein.radialmenu.core.Colors;
import com.navatusein.radialmenu.core.model.WheelColors;

/**
 * The bottom of the colour chain: what a wheel is drawn with when no profile or menu says otherwise.
 *
 * <p>
 * Paired the way the editor lists them - a fill and its outline, for the ring and then for the sector under the
 * cursor - because those are the two that change together.
 */
@Config(modid = MODID, configSubDirectory = "RadialMenu", filename = "general", category = "colors")
@Config.LangKey("radialmenu.config.colors")
public class ColorConfig {

    @Config.Comment("Fill colour of the ring, 0xAARRGGBB.")
    @Config.DefaultString("0x99101010")
    @Config.Order(1)
    public static String ringColor;

    @Config.Comment("Colour of the ring's outline - the sector dividers and its inner and outer edges, 0xAARRGGBB. "
        + "Fully transparent hides them.")
    @Config.DefaultString("0x60FFFFFF")
    @Config.Order(2)
    public static String borderColor;

    @Config.Comment("Fill colour of the sector under the cursor, 0xAARRGGBB.")
    @Config.DefaultString("0xCC4A90D9")
    @Config.Order(3)
    public static String highlightColor;

    @Config.Comment("Colour of the outline around the sector under the cursor, 0xAARRGGBB. "
        + "Fully transparent leaves it outlined like every other sector.")
    @Config.DefaultString("0xCCFFFFFF")
    @Config.Order(4)
    public static String highlightBorderColor;

    @Config.Comment("Colour of the wash behind the wheel, 0xAARRGGBB. Only drawn when the wheel category turns the "
        + "background on. A profile or a single menu can override it.")
    @Config.DefaultString("0x80101010")
    @Config.Order(5)
    public static String backgroundColor;

    @Config.Comment("Default tint for sprite icons, #RRGGBB. Items and your own PNGs are not tinted.")
    @Config.DefaultString("#FFFFFF")
    @Config.Order(6)
    public static String iconColor;

    /** The bottom of the colour chain: what the wheel looks like before any profile or menu has its say. */
    public static WheelColors defaultColors() {
        return new WheelColors(
            Colors.parseArgb(ringColor, 0x99101010),
            Colors.parseArgb(highlightColor, 0xCC4A90D9),
            Colors.parseArgb(borderColor, 0x60FFFFFF),
            Colors.parseArgb(highlightBorderColor, 0xCCFFFFFF),
            Colors.parseArgb(backgroundColor, 0x80101010),
            Colors.parseArgb(iconColor, 0xFFFFFFFF));
    }
}
