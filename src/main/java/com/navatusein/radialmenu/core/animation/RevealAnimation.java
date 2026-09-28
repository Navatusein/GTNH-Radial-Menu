package com.navatusein.radialmenu.core.animation;

/**
 * How a wheel arrives on screen.
 *
 * <p>
 * The three staggered ones differ only in the order the sectors take their turn; what each sector then does is the
 * same. Kept as one setting rather than an order and a manner, because "clockwise fade" and "outward-from-the-top
 * zoom" are not combinations anyone asks for - a player picks a way for the menu to appear.
 */
public enum RevealAnimation {

    /** Fully drawn on the first frame. */
    NONE,

    /** Fades up in place, all sectors together. */
    FADE,

    /** Grows out of the middle as it fades up, all sectors together. */
    ZOOM,

    /** The same, sector by sector clockwise from the top. */
    STAGGER_CW,

    /** Sector by sector anticlockwise. */
    STAGGER_CCW,

    /** Both ways at once from the top, meeting at the bottom. */
    STAGGER_BOTH
}
