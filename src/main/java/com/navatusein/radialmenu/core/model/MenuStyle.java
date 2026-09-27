package com.navatusein.radialmenu.core.model;

/**
 * Per-menu colour overrides.
 *
 * <p>
 * Both fields may be null, which means "use the global setting". Storing the override on the node rather than in the
 * config is what lets one submenu read differently from another - a menu of destructive actions can be red without
 * repainting the whole mod.
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

    public boolean isEmpty() {
        return isBlank(ringColor) && isBlank(highlightColor) && isBlank(iconColor) && isBlank(borderColor);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim()
            .isEmpty();
    }

    public static MenuStyle of(String ringColor, String highlightColor, String iconColor, String borderColor) {
        MenuStyle style = new MenuStyle();
        style.ringColor = isBlank(ringColor) ? null : ringColor;
        style.highlightColor = isBlank(highlightColor) ? null : highlightColor;
        style.iconColor = isBlank(iconColor) ? null : iconColor;
        style.borderColor = isBlank(borderColor) ? null : borderColor;
        return style.isEmpty() ? null : style;
    }
}
