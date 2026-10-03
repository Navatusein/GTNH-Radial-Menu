package com.navatusein.radialmenu.core.script;

/**
 * Between a world coordinate and the chunk it lives in.
 *
 * <p>
 * Three lines of arithmetic, kept in one place because it is the arithmetic everyone gets wrong on the negative side
 * of the world. A chunk is sixteen blocks, so the conversion looks like a division and a remainder - but block -1 is
 * in chunk -1, eight blocks into it, while an integer division towards zero says chunk 0 and a remainder says -1.
 * The shift and the mask say what Minecraft means, in both halves of the world, which is why they are used rather
 * than {@code / 16} and {@code % 16}.
 *
 * <p>
 * Pure, so a script's view of it and the wheel's cannot drift apart, and so a test can reach it without a game.
 */
public final class ChunkCoords {

    /** Blocks to a chunk, along one axis. */
    public static final int SIZE = 16;

    private ChunkCoords() {}

    /** Which chunk a world coordinate falls in. */
    public static int chunkOf(int world) {
        return world >> 4;
    }

    /** Where in its chunk a world coordinate sits, 0 to 15. */
    public static int offsetOf(int world) {
        return world & 15;
    }

    /**
     * The world coordinate an offset inside a chunk stands for.
     *
     * <p>
     * The offset is not clamped. A script that works out "four blocks past the edge" and passes 19 means the next
     * chunk along, and the arithmetic already says so - refusing it would only make the caller do the carrying.
     */
    public static int toWorld(int chunk, int offset) {
        return chunk * SIZE + offset;
    }
}
