package com.navatusein.radialmenu.core;

/**
 * Colour strings, in the two shapes the mod stores.
 *
 * <p>
 * The config writes {@code 0xAARRGGBB} because ring colours need transparency; the colour picker writes
 * {@code #RRGGBB} because a player picks a hue, not an opacity. Reading a six-digit value as if it were eight is how
 * a chosen colour ends up fully transparent, so the two are reconciled in one place rather than at each call site.
 */
public final class Colors {

    private Colors() {}

    /** True when the text carries its own alpha channel. */
    public static boolean hasAlpha(String value) {
        return digits(value).length() == 8;
    }

    /**
     * Parses {@code 0xAARRGGBB}, {@code #RRGGBB} or bare hex.
     *
     * @return the fallback when the text is missing or malformed - a bad colour should not stop anything drawing
     */
    public static int parseArgb(String value, int fallback) {
        String digits = digits(value);
        if (digits.length() != 6 && digits.length() != 8) {
            return fallback;
        }
        try {
            return (int) Long.parseLong(digits, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /**
     * Resolves a colour override against the value it overrides.
     *
     * <p>
     * A blank override inherits entirely. An override with its own alpha is taken as written. One without - which is
     * everything the colour picker produces - keeps the hue the player chose and the transparency of what it
     * replaces, so choosing a colour cannot accidentally turn a ring invisible.
     */
    public static int resolve(String override, String inherited, int fallback) {
        return over(override, parseArgb(inherited, fallback));
    }

    /**
     * Applies one override on top of an already-resolved colour.
     *
     * <p>
     * Colours inherit down a chain - a menu falls back to its profile, and a profile to the mod's config - so each
     * step has to layer onto the result of the one before rather than onto a raw string.
     */
    public static int over(String override, int base) {
        if (override == null || override.trim()
            .isEmpty()) {
            return base;
        }
        int picked = parseArgb(override, base);
        if (hasAlpha(override)) {
            return picked;
        }
        return (base & 0xFF000000) | (picked & 0x00FFFFFF);
    }

    /**
     * Scales an RGB colour's brightness by a percentage: below 100 towards black, above 100 towards white.
     *
     * <p>
     * Darkening multiplies, which keeps the hue; lightening mixes towards white instead of multiplying, because
     * multiplying a channel that is already 255 does nothing and a bright accent would refuse to get any lighter.
     */
    public static int scale(int rgb, int percent) {
        if (percent == 100) {
            return rgb & 0x00FFFFFF;
        }
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;

        if (percent < 100) {
            float factor = Math.max(0, percent) / 100f;
            red = Math.round(red * factor);
            green = Math.round(green * factor);
            blue = Math.round(blue * factor);
        } else {
            float towardsWhite = Math.min(100, percent - 100) / 100f;
            red = Math.round(red + (255 - red) * towardsWhite);
            green = Math.round(green + (255 - green) * towardsWhite);
            blue = Math.round(blue + (255 - blue) * towardsWhite);
        }
        return (red << 16) | (green << 8) | blue;
    }

    /** Puts an alpha channel on an RGB colour, replacing whatever it had. */
    public static int withAlpha(int rgb, int alpha) {
        return ((Math.max(0, Math.min(255, alpha))) << 24) | (rgb & 0x00FFFFFF);
    }

    private static String digits(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        if (cleaned.startsWith("0x") || cleaned.startsWith("0X")) {
            return cleaned.substring(2);
        }
        if (cleaned.startsWith("#")) {
            return cleaned.substring(1);
        }
        return cleaned;
    }
}
