package com.navatusein.radialmenu.core.script;

import java.util.List;

import com.navatusein.radialmenu.core.model.AccentCoefficients;

/**
 * What the host tells a script about the world it is running in.
 *
 * <p>
 * Read when the script touches it, never copied in at the start: a script can be alive across a teleport, and a
 * position captured when it launched would be a lie by the time it is used.
 *
 * <p>
 * The only reason this is an interface is that a test has no Minecraft to ask. It is implemented once on the client and
 * once in the tests, and between them they are the whole of what a script can learn without asking the server.
 */
public interface ScriptContext {

    String playerName();

    int dimension();

    int blockX();

    int blockY();

    int blockZ();

    /**
     * Where the player is looking, in degrees, exactly as the game holds it.
     *
     * <p>
     * Unbounded and starting at south, because that is what it is - a player who has turned around three times
     * carries a yaw in the hundreds. {@link Facing} is where it is made presentable, so this stays the raw number
     * and there is one place that knows the convention rather than two.
     */
    double yaw();

    double pitch();

    /** Hearts, as the game counts them: 20 is full. */
    double health();

    /** Hunger, 0 to 20. */
    int food();

    /** Breath left under water, in ticks, 300 when full. */
    int air();

    /** What is in the player's hand, or null when it is empty. */
    ScriptItem heldItem();

    /**
     * The player's own inventory, hotbar included, empty slots left out.
     *
     * <p>
     * A copy rather than a view: the script reads it on its own thread, and a list that changed underneath it while
     * it was walking it would be a crash in somebody's menu rather than a stale number.
     */
    List<ScriptItem> inventory();

    /** What the crosshair is on, or null for thin air. */
    LookTarget lookingAt();

    /** The world's time of day in ticks, counting from the morning the world began. */
    long worldTime();

    /** The server's address, or the name of the save in single player. */
    String worldName();

    /**
     * A value the player's scripts stored under this key, or null.
     *
     * <p>
     * Reading is a question, so it is answered here; writing changes a file and goes through a request, which is
     * what keeps the one thread that may touch the disk the same one that touches everything else.
     */
    String storeGet(String key);

    /**
     * How far each colour lands from a chosen accent.
     *
     * <p>
     * Comes from the mod config rather than from the script, because that is the mod's look rather than the script
     * author's choice of hue - the same split the editor's accent button lives on.
     */
    AccentCoefficients accentCoefficients();
}
