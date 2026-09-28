package com.navatusein.radialmenu.core.model;

/**
 * How each of the wheel's colours is derived from one accent colour.
 *
 * <p>
 * An accent is a hue and nothing else - it is picked with no alpha channel, because a player choosing "blue" is not
 * choosing an opacity. Everything that separates a ring from a highlight from an outline is here instead: how opaque
 * that part is, and how far from the accent it sits. So the accent answers "which colour", and these answer "what
 * this mod does with a colour", which is why they belong in the mod's config rather than in a profile.
 */
public class AccentCoefficients {

    /** One derived colour: an opacity, and a distance from the accent. */
    public static class Tone {

        /** 0 to 255. */
        public final int alpha;

        /**
         * Percent of the accent's brightness. 100 is the accent as picked, below darkens towards black, above
         * lightens towards white.
         */
        public final int brightness;

        public Tone(int alpha, int brightness) {
            this.alpha = clamp(alpha, 0, 255);
            this.brightness = clamp(brightness, 0, 200);
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    public final Tone ring;
    public final Tone highlight;
    public final Tone border;
    public final Tone highlightBorder;
    public final Tone background;

    /**
     * Every tone named explicitly, with no shorter constructor.
     *
     * <p>
     * Five arguments of the same type in a row is exactly the shape that lets two of them swap places without the
     * compiler noticing, so there is one call site per caller and no overload to pick the wrong one.
     */
    public AccentCoefficients(Tone ring, Tone highlight, Tone border, Tone highlightBorder, Tone background) {
        this.ring = ring;
        this.highlight = highlight;
        this.border = border;
        this.highlightBorder = highlightBorder;
        this.background = background;
    }
}
