package com.navatusein.radialmenu.core.script;

/**
 * The game's clock, read the way a person reads one.
 *
 * <p>
 * Minecraft counts ticks since the world was made and never resets, so the raw number is in the millions and says
 * nothing on its own. What a script wants is the time of day and which day it is, and both are a division the host
 * has no business doing - it answers what the game holds, and this turns it into an answer.
 *
 * <p>
 * Daylight is 0 to 12000: dawn at 0, dusk at 12000, and the night running back round to 24000. That is vanilla's
 * division, the same one that decides whether a bed may be used.
 */
public final class WorldTime {

    /** Ticks in one full day and night. */
    public static final long DAY = 24000L;

    /** When the sun sets. */
    public static final long DUSK = 12000L;

    private WorldTime() {}

    /** Ticks since this morning, 0 to 23999. */
    public static long timeOfDay(long worldTime) {
        long time = worldTime % DAY;
        return time < 0 ? time + DAY : time;
    }

    /** How many days the world has seen, counting the first one as 0. */
    public static long day(long worldTime) {
        return Math.floorDiv(worldTime, DAY);
    }

    public static boolean isDay(long worldTime) {
        return timeOfDay(worldTime) < DUSK;
    }
}
