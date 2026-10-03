package com.navatusein.radialmenu.client.script;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.navatusein.radialmenu.config.AccentConfig;
import com.navatusein.radialmenu.core.model.AccentCoefficients;
import com.navatusein.radialmenu.core.script.ScriptContext;

/** The one place a script's view of the game is answered from Minecraft. */
public final class ClientScriptContext implements ScriptContext {

    public static final ClientScriptContext INSTANCE = new ClientScriptContext();

    private ClientScriptContext() {}

    /**
     * Every field reads the player afresh.
     *
     * <p>
     * A script outlives the moment it started - that is the whole point of one that waits for a chat reply - so the
     * player may be somewhere else, or gone, by the time it looks.
     */
    private static EntityPlayer player() {
        return Minecraft.getMinecraft().thePlayer;
    }

    @Override
    public String playerName() {
        EntityPlayer player = player();
        return player == null ? "" : player.getCommandSenderName();
    }

    @Override
    public int dimension() {
        EntityPlayer player = player();
        return player == null ? 0 : player.dimension;
    }

    @Override
    public int blockX() {
        EntityPlayer player = player();
        return player == null ? 0 : (int) Math.floor(player.posX);
    }

    @Override
    public int blockY() {
        EntityPlayer player = player();
        return player == null ? 0 : (int) Math.floor(player.posY);
    }

    @Override
    public int blockZ() {
        EntityPlayer player = player();
        return player == null ? 0 : (int) Math.floor(player.posZ);
    }

    @Override
    public double yaw() {
        EntityPlayer player = player();
        return player == null ? 0.0 : player.rotationYaw;
    }

    @Override
    public double pitch() {
        EntityPlayer player = player();
        return player == null ? 0.0 : player.rotationPitch;
    }

    @Override
    public AccentCoefficients accentCoefficients() {
        return AccentConfig.coefficients();
    }
}
