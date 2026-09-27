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

    public boolean isEmpty() {
        return isBlank(ringColor) && isBlank(highlightColor) && isBlank(iconColor);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim()
            .isEmpty();
    }

    public static MenuStyle of(String ringColor, String highlightColor) {
        return of(ringColor, highlightColor, null);
    }

    public static MenuStyle of(String ringColor, String highlightColor, String iconColor) {
        MenuStyle style = new MenuStyle();
        style.ringColor = isBlank(ringColor) ? null : ringColor;
        style.highlightColor = isBlank(highlightColor) ? null : highlightColor;
        style.iconColor = isBlank(iconColor) ? null : iconColor;
        return style.isEmpty() ? null : style;
    }
}
