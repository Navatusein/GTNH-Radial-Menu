package com.navatusein.radialmenu.config;

import static com.navatusein.radialmenu.RadialMenuMod.MODID;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.navatusein.radialmenu.core.model.AccentCoefficients;

/**
 * How one colour becomes a whole wheel.
 *
 * <p>
 * The editor offers an accent as a way of filling the colours in; these say what it fills them with. How opaque each
 * part is, and how far from the accent it sits - 100 is the accent as picked, less is darker, more is lighter.
 *
 * <p>
 * They are here rather than in a profile because they are this mod's look, not the player's choice of hue: a profile
 * shared with someone else should arrive in their palette, not carry a second copy of these ten numbers.
 */
@Config(modid = MODID, configSubDirectory = "RadialMenu", filename = "general", category = "accent")
@Config.LangKey("radialmenu.config.accent")
public class AccentConfig {

    @Config.Comment("Opacity of a ring derived from an accent colour, 0-255.")
    @Config.DefaultInt(153)
    @Config.RangeInt(min = 0, max = 255)
    @Config.Order(1)
    public static int ringAlpha;

    @Config.Comment("Brightness of a ring derived from an accent colour, percent of the accent.")
    @Config.DefaultInt(18)
    @Config.RangeInt(min = 0, max = 200)
    @Config.Order(2)
    public static int ringBrightness;

    @Config.Comment("Opacity of the ring's outline derived from an accent colour, 0-255.")
    @Config.DefaultInt(96)
    @Config.RangeInt(min = 0, max = 255)
    @Config.Order(3)
    public static int borderAlpha;

    @Config.Comment("Brightness of the ring's outline derived from an accent colour, percent of the accent.")
    @Config.DefaultInt(140)
    @Config.RangeInt(min = 0, max = 200)
    @Config.Order(4)
    public static int borderBrightness;

    @Config.Comment("Opacity of a highlight derived from an accent colour, 0-255.")
    @Config.DefaultInt(204)
    @Config.RangeInt(min = 0, max = 255)
    @Config.Order(5)
    public static int highlightAlpha;

    @Config.Comment("Brightness of a highlight derived from an accent colour, percent of the accent.")
    @Config.DefaultInt(100)
    @Config.RangeInt(min = 0, max = 200)
    @Config.Order(6)
    public static int highlightBrightness;

    @Config.Comment("Opacity of the highlighted sector's outline derived from an accent colour, 0-255.")
    @Config.DefaultInt(204)
    @Config.RangeInt(min = 0, max = 255)
    @Config.Order(7)
    public static int highlightBorderAlpha;

    @Config.Comment("Brightness of the highlighted sector's outline derived from an accent colour, percent.")
    @Config.DefaultInt(160)
    @Config.RangeInt(min = 0, max = 200)
    @Config.Order(8)
    public static int highlightBorderBrightness;

    @Config.Comment("Opacity of the background wash derived from an accent colour, 0-255.")
    @Config.DefaultInt(128)
    @Config.RangeInt(min = 0, max = 255)
    @Config.Order(9)
    public static int backgroundAlpha;

    @Config.Comment("Brightness of the background wash derived from an accent colour, percent of the accent.")
    @Config.DefaultInt(8)
    @Config.RangeInt(min = 0, max = 200)
    @Config.Order(10)
    public static int backgroundBrightness;

    public static AccentCoefficients coefficients() {
        return new AccentCoefficients(
            new AccentCoefficients.Tone(ringAlpha, ringBrightness),
            new AccentCoefficients.Tone(highlightAlpha, highlightBrightness),
            new AccentCoefficients.Tone(borderAlpha, borderBrightness),
            new AccentCoefficients.Tone(highlightBorderAlpha, highlightBorderBrightness),
            new AccentCoefficients.Tone(backgroundAlpha, backgroundBrightness));
    }
}
