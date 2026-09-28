package com.navatusein.radialmenu.core.model;

/**
 * Per-menu colour overrides.
 *
 * <p>
 * Every field may be null, which means "use the global setting". Storing the override on the node rather than in
 * the config is what lets one submenu read differently from another - a menu of destructive actions can be red
 * without repainting the whole mod.
 *
 * <p>
 * There is no accent here. An accent is a way of choosing these colours, not a colour of its own - the editor writes
 * what it produces straight into the fields below, so a file always says outright what it is drawn with.
 */
public class MenuStyle {

    /** {@code #RRGGBB} or {@code 0xAARRGGBB}; null inherits. */
    public String ringColor;

    public String highlightColor;

    /**
     * Tint for sprite icons.
     *
     * <p>
     * Only sprites: they are monochrome by design and a colour is the only thing that tells them apart. Items carry
     * their own colours, and a player's own PNG is left as drawn unless they ask otherwise.
     */
    public String iconColor;

    /**
     * Colour of the lines on the wheel - the dividers between sectors and the edges of the ring.
     *
     * <p>
     * One colour for both, because they are one thing: the outline of the shape. Separate colours would let a menu
     * be drawn with edges that do not match its own divisions.
     */
    public String borderColor;

    /**
     * Outline of the sector under the cursor.
     *
     * <p>
     * Separate from {@link #borderColor}, unlike the ring's own lines, because this one is not part of the shape -
     * it is part of the answer to "which one am I about to press".
     */
    public String highlightBorderColor;

    /**
     * Wash over the screen behind the wheel.
     *
     * <p>
     * Transparent leaves the world as it is, which is the default: the wheel is meant to be usable without taking
     * the game away from the player. A colour here is a readability choice, so it carries its own opacity.
     */
    public String backgroundColor;

    public boolean isEmpty() {
        return isBlank(ringColor) && isBlank(highlightColor)
            && isBlank(iconColor)
            && isBlank(borderColor)
            && isBlank(highlightBorderColor)
            && isBlank(backgroundColor);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim()
            .isEmpty();
    }

    /**
     * Every colour named explicitly, with no shorter overload.
     *
     * <p>
     * There were briefly two of these differing only in which optional colour the third argument meant. The types
     * matched either way round, so the compiler would have said nothing while the colours quietly swapped.
     */
    public static MenuStyle of(String ringColor, String highlightColor, String iconColor, String borderColor,
        String highlightBorderColor, String backgroundColor) {
        MenuStyle style = new MenuStyle();
        style.ringColor = isBlank(ringColor) ? null : ringColor;
        style.highlightColor = isBlank(highlightColor) ? null : highlightColor;
        style.iconColor = isBlank(iconColor) ? null : iconColor;
        style.borderColor = isBlank(borderColor) ? null : borderColor;
        style.highlightBorderColor = isBlank(highlightBorderColor) ? null : highlightBorderColor;
        style.backgroundColor = isBlank(backgroundColor) ? null : backgroundColor;
        return style.isEmpty() ? null : style;
    }
}
