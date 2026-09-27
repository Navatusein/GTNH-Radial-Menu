package com.navatusein.radialmenu.client.action;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.ClientCommandHandler;

import com.navatusein.radialmenu.RadialMenuMod;
import com.navatusein.radialmenu.core.action.Placeholders;

/**
 * Sends a chat line or slash command as the player.
 *
 * <p>
 * Goes through the ordinary chat path, which is what keeps the mod client-only: the server sees exactly what it would
 * have seen had the player typed it, so nothing needs installing server-side and nothing works that the player could
 * not have done by hand.
 */
public final class ChatSender {

    private ChatSender() {}

    public static void send(String rawText) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        String text = Placeholders.apply(rawText, context(mc.thePlayer))
            .trim();
        if (text.isEmpty()) {
            return;
        }

        // Offer it to client-side commands first, exactly as typing into chat does. EntityClientPlayerMP
        // .sendChatMessage goes straight to the server, so without this a client command - this mod's own
        // /radialmenu included - would be broadcast as chat instead of running.
        if (ClientCommandHandler.instance.executeCommand(mc.thePlayer, text) != 0) {
            RadialMenuMod.LOG.debug("Handled client-side: " + text);
            return;
        }

        mc.thePlayer.sendChatMessage(text);
        RadialMenuMod.LOG.debug("Sent: " + text);
    }

    /** Values available to {@code {name}} placeholders. */
    public static Map<String, String> context(EntityPlayer player) {
        Map<String, String> values = new HashMap<>();
        values.put(Placeholders.PLAYER, player.getCommandSenderName());
        values.put(Placeholders.DIMENSION, Integer.toString(player.dimension));
        values.put(Placeholders.X, Integer.toString((int) Math.floor(player.posX)));
        values.put(Placeholders.Y, Integer.toString((int) Math.floor(player.posY)));
        values.put(Placeholders.Z, Integer.toString((int) Math.floor(player.posZ)));
        return values;
    }
}
