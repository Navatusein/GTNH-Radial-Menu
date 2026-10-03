package com.navatusein.radialmenu.core.geometry;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Fits several arcs onto one ring without letting them overlap.
 *
 * <p>
 * An inline submenu wants to sit directly outside the entry it came from, and so does the one next to it. Two
 * neighbouring entries expanded at once ask for overlapping stretches of the same ring, and something has to give.
 * What gives is the position: the arcs are slid apart just far enough to clear each other, keeping the order they
 * sit in round the circle, so each one stays as close to its own entry as the crowd allows.
 *
 * <p>
 * Order is kept rather than recomputed because it is the only thing the player can read. An arc that jumped past its
 * neighbour to find room would be drawn outside a different entry than the one it belongs to, which is worse than
 * being a few degrees off its own.
 *
 * <p>
 * Pure maths, no Minecraft, unit tested - the same reason the rest of {@code core/geometry} is.
 */
public final class ArcLayout {

    private static final double FULL = 360.0;

    private ArcLayout() {}

    /** Where one ring ended up: an angle it starts at and how far round it goes. */
    public static final class Arc {

        public final double start;

        public final double span;

        public Arc(double start, double span) {
            this.start = start;
            this.span = span;
        }

        public double center() {
            return start + span / 2.0;
        }

        public double end() {
            return start + span;
        }
    }

    /**
     * Places each arc as near its wanted centre as it can without overlapping its neighbours.
     *
     * <p>
     * When the arcs together want more than a full circle there is no arrangement without overlap, so they are
     * scaled down in proportion and laid end to end instead: a ring that is too full is still a readable ring,
     * while arcs drawn over each other are not readable at all.
     *
     * @param centers where each arc would like to be centred, in degrees
     * @param spans   how wide each arc wants to be, in degrees
     * @return the placed arcs, in the order they were given
     */
    public static Arc[] place(double[] centers, double[] spans) {
        int count = centers.length;
        Arc[] placed = new Arc[count];
        if (count == 0) {
            return placed;
        }

        Integer[] order = sortedByCenter(centers);

        double wanted = 0.0;
        for (double span : spans) {
            wanted += span;
        }

        if (wanted >= FULL) {
            double scale = FULL / wanted;
            double cursor = RadialGeometry.normalizeDegrees(centers[order[0]]) - spans[order[0]] * scale / 2.0;
            for (int i = 0; i < count; i++) {
                int index = order[i];
                double span = spans[index] * scale;
                placed[index] = new Arc(cursor, span);
                cursor += span;
            }
            return placed;
        }

        double[] positions = relax(centers, spans, order);
        for (int i = 0; i < count; i++) {
            int index = order[i];
            placed[index] = new Arc(positions[i] - spans[index] / 2.0, spans[index]);
        }
        return placed;
    }

    private static Integer[] sortedByCenter(final double[] centers) {
        Integer[] order = new Integer[centers.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, new Comparator<Integer>() {

            @Override
            public int compare(Integer left, Integer right) {
                return Double.compare(
                    RadialGeometry.normalizeDegrees(centers[left]),
                    RadialGeometry.normalizeDegrees(centers[right]));
            }
        });
        return order;
    }

    /**
     * Pushes every overlapping pair apart by half the overlap each, round and round until nothing overlaps.
     *
     * <p>
     * Both sides of a pair move, rather than one giving way, so a crowd of arcs spreads outwards from where it is
     * rather than sliding off in whichever direction the loop happened to run. The last arc is paired with the first
     * across the top of the circle, which is what makes this a ring and not a row.
     *
     * <p>
     * Iterating rather than solving: the arcs here number a handful, the constraint is one-dimensional, and a few
     * dozen passes of arithmetic cost less than the solver would take to describe.
     */
    private static double[] relax(double[] centers, double[] spans, Integer[] order) {
        int count = order.length;
        double[] positions = new double[count];
        for (int i = 0; i < count; i++) {
            positions[i] = RadialGeometry.normalizeDegrees(centers[order[i]]);
        }
        if (count == 1) {
            return positions;
        }

        int passes = 32 + count * 8;
        for (int pass = 0; pass < passes; pass++) {
            boolean moved = false;
            for (int i = 0; i < count; i++) {
                int next = (i + 1) % count;
                double gap = positions[next] - positions[i];
                if (next == 0) {
                    gap += FULL;
                }
                double required = (spans[order[i]] + spans[order[next]]) / 2.0;
                double overlap = required - gap;
                if (overlap <= 1.0e-9) {
                    continue;
                }
                positions[i] -= overlap / 2.0;
                positions[next] += overlap / 2.0;
                moved = true;
            }
            if (!moved) {
                break;
            }
        }
        return positions;
    }
}
