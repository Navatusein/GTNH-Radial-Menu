package com.navatusein.radialmenu.core.animation;

/**
 * When each sector of an opening wheel is how far along.
 *
 * <p>
 * Pure arithmetic on a clock reading, so the whole of the animation's timing is unit tested without a game. The
 * renderer only ever asks "how far along is this sector" and multiplies by it.
 *
 * <p>
 * Time, not ticks. A GUI is drawn as fast as the machine manages and a wheel that opens in twelve frames would take
 * a quarter of a second on one machine and two seconds on another.
 */
public final class RevealTiming {

    /**
     * How much of the total time is spent handing the animation from the first sector to the last.
     *
     * <p>
     * Half: the last sector starts halfway through and still finishes with the rest. Any more and a staggered wheel
     * looks like it is loading; any less and the stagger stops reading as one.
     */
    private static final double SPREAD = 0.5;

    private RevealTiming() {}

    /**
     * How far along one sector is, from 0 to 1.
     *
     * @param elapsedSeconds  time since the wheel opened
     * @param durationSeconds how long the whole animation takes; zero or less means it is already over
     */
    public static double slotProgress(RevealAnimation animation, int slot, int slotCount, double elapsedSeconds,
        double durationSeconds) {
        if (animation == null || animation == RevealAnimation.NONE || durationSeconds <= 0.0 || slotCount <= 0) {
            return 1.0;
        }
        if (elapsedSeconds >= durationSeconds) {
            return 1.0;
        }

        double delay = delayFraction(animation, slot, slotCount) * durationSeconds * SPREAD;
        double span = durationSeconds - delay;
        if (span <= 0.0) {
            return elapsedSeconds >= delay ? 1.0 : 0.0;
        }
        return clamp((elapsedSeconds - delay) / span);
    }

    /** Where in the queue a sector is, from 0 for the first to 1 for the last. */
    private static double delayFraction(RevealAnimation animation, int slot, int slotCount) {
        if (slotCount <= 1) {
            return 0.0;
        }
        switch (animation) {
            case STAGGER_CW:
                return (double) slot / (slotCount - 1);
            case STAGGER_CCW:
                return (double) ((slotCount - slot) % slotCount) / (slotCount - 1);
            case STAGGER_BOTH:
                // Both ways from the top at once, so the queue is only half as long and the two halves meet at the
                // bottom. Divided by the longer half, which is what keeps a wheel with an odd sector count even.
                int distance = Math.min(slot, slotCount - slot);
                return (double) distance / (slotCount / 2);
            default:
                return 0.0;
        }
    }

    /**
     * Fast at first and slow at the end.
     *
     * <p>
     * A wheel is opened to be used, so it should look ready long before it is finished; the fifth power is what
     * makes the last of the movement a settling rather than a wait.
     */
    public static double easeOutQuint(double progress) {
        double t = 1.0 - clamp(progress);
        return 1.0 - t * t * t * t * t;
    }

    /** Moves a value towards a target at a steady rate, for the sector under the cursor pushing out. */
    public static double approach(double current, double target, double step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }

    private static double clamp(double value) {
        return value < 0.0 ? 0.0 : (value > 1.0 ? 1.0 : value);
    }
}
