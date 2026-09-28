package com.navatusein.radialmenu.core.model;

import com.navatusein.radialmenu.core.Colors;

/**
 * The wheel's colours, resolved to {@code 0xAARRGGBB} and ready to draw with.
 *
 * <p>
 * Resolving happens once per frame in {@link StyleResolver} rather than colour by colour at each draw: the chain is
 * three levels deep, an accent expands into five values, and working that out inside the renderer is how the same
 * arithmetic ended up written twice and drifting.
 */
public class WheelColors {

    public final int ring;
    public final int highlight;
    public final int border;

    /**
     * Outline of the sector under the cursor. Its own colour, so a highlight can be outlined rather than only filled.
     */
    public final int highlightBorder;

    /** Behind the whole screen. Transparent means the world shows through untouched. */
    public final int background;

    /** Tint for sprite icons. Never derived from an accent: an icon that changes hue with the ring stops reading. */
    public final int icon;

    public WheelColors(int ring, int highlight, int border, int highlightBorder, int background, int icon) {
        this.ring = ring;
        this.highlight = highlight;
        this.border = border;
        this.highlightBorder = highlightBorder;
        this.background = background;
        this.icon = icon;
    }

    /**
     * The full set derived from one accent, keeping the icon tint that was already resolved.
     *
     * @param accentRgb the accent with no alpha channel of its own; every opacity comes from the coefficients
     */
    public WheelColors fromAccent(int accentRgb, AccentCoefficients coefficients) {
        return new WheelColors(
            tone(accentRgb, coefficients.ring),
            tone(accentRgb, coefficients.highlight),
            tone(accentRgb, coefficients.border),
            tone(accentRgb, coefficients.highlightBorder),
            tone(accentRgb, coefficients.background),
            icon);
    }

    private static int tone(int accentRgb, AccentCoefficients.Tone tone) {
        return Colors.withAlpha(Colors.scale(accentRgb, tone.brightness), tone.alpha);
    }
}
