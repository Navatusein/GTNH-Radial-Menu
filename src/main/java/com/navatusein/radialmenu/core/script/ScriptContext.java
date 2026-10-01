package com.navatusein.radialmenu.core.script;

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
     * How far each colour lands from a chosen accent.
     *
     * <p>
     * Comes from the mod config rather than from the script, because that is the mod's look rather than the script
     * author's choice of hue - the same split the editor's accent button lives on.
     */
    AccentCoefficients accentCoefficients();
}
