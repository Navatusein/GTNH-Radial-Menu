package com.navatusein.radialmenu.core.script;

/**
 * Which way a yaw is pointing, as a word.
 *
 * <p>
 * Worked out here rather than asked of the game, because it is arithmetic on a number the context already hands over
 * and arithmetic belongs where it can be tested. The thresholds are vanilla's own: the compass is divided into four
 * quarters centred on the directions, so a yaw a few degrees either side of due south is still south - which is what
 * a script asking "am I facing north" means, and what any answer narrower than that would get wrong for a player
 * standing still.
 *
 * <p>
 * Minecraft's yaw grows from south through west: 0 is south, 90 west, 180 north, 270 east. That is not a convention
 * anybody would choose, but it is the one the numbers come in, and renaming it here would only mean two conventions
 * instead of one.
 */
public final class Facing {

    /** Vanilla's order, which is what the quarter index counts through. */
    private static final String[] COMPASS = { "south", "west", "north", "east" };

    private Facing() {}

    /** The yaw as a number between 0 and 360, however many turns the player has made since. */
    public static double normalizeYaw(double yaw) {
        double turned = yaw % 360.0;
        return turned < 0.0 ? turned + 360.0 : turned;
    }

    /** One of {@code north}, {@code south}, {@code east}, {@code west}. */
    public static String of(double yaw) {
        int quarter = (int) Math.floor(normalizeYaw(yaw) / 90.0 + 0.5) & 3;
        return COMPASS[quarter];
    }
}
