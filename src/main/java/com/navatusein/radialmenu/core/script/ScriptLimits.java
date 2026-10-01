package com.navatusein.radialmenu.core.script;

import com.navatusein.radialmenu.core.model.SlotLayout;

/**
 * What a script may spend before it is stopped.
 *
 * <p>
 * Counted per resume rather than per script, because a resume happens on the client tick: a script that waits for an
 * hour costs nothing, while one that spends a tenth of a second in a single stretch of Lua is a visible stutter however
 * briefly it lives. The budget is what turns {@code while true do end} into a stopped script rather than a frozen game.
 */
public final class ScriptLimits {

    /** Bytecode instructions one resume may execute. Generous: parsing a chat line and building a wheel is nothing. */
    public final int instructionsPerResume;

    /**
     * Wall clock one resume may take, in nanoseconds. Catches what the instruction count does not - a long string
     * operation is one instruction.
     */
    public final long nanosPerResume;

    /** Entries a script-built wheel may have, capped by what the wheel can draw. */
    public final int maxMenuItems;

    public static final ScriptLimits DEFAULT = new ScriptLimits(200_000, 20_000_000L, SlotLayout.MAX_SLOTS);

    public ScriptLimits(int instructionsPerResume, long nanosPerResume, int maxMenuItems) {
        this.instructionsPerResume = instructionsPerResume;
        this.nanosPerResume = nanosPerResume;
        this.maxMenuItems = maxMenuItems;
    }
}
