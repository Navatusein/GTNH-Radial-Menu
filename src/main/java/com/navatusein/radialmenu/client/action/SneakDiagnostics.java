package com.navatusein.radialmenu.client.action;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.navatusein.radialmenu.RadialMenuMod;
import com.navatusein.radialmenu.client.input.KeyBuffer;
import com.navatusein.radialmenu.client.input.KeyInjector;
import com.navatusein.radialmenu.config.RadialMenuConfig;

/**
 * Prints what the game actually believes at the moment of an injected press.
 *
 * <p>
 * Here because two careful readings of the sources in a row predicted behaviour the pack did not show, and the project
 * has been through that before: when a fix aimed at a symptom misses twice, print the numbers. Every value on the line
 * is a link in the one chain - our byte, LWJGL's answer, the binding's answer, the copy the player keeps, and what a
 * mod reading "is the player sneaking" would get - so one line says which link is broken.
 *
 * <p>
 * Off unless {@code logKeyState} is switched on in the config, because this runs on every press.
 */
public final class SneakDiagnostics {

    private SneakDiagnostics() {}

    public static void log(String what, KeyBinding binding) {
        if (!RadialMenuConfig.logKeyState) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityClientPlayerMP player = mc.thePlayer;
        KeyBinding sneak = mc.gameSettings.keyBindSneak;

        StringBuilder line = new StringBuilder("key state [").append(what)
            .append("] ");
        line.append("buffer=")
            .append(KeyBuffer.isAvailable());
        line.append(" binding=")
            .append(binding == null ? "none" : binding.getKeyDescription())
            .append("/code=")
            .append(binding == null ? 0 : binding.getKeyCode())
            .append("/pressed=")
            .append(binding != null && binding.getIsKeyPressed())
            // The field itself, beside what getIsKeyPressed() answers. Controlling rewrites that answer for a binding
            // whose key is a modifier, so the two disagree whenever our byte is not in the buffer - and reading the
            // masked answer as a cleared field sent one diagnosis down the wrong road already.
            .append("/raw=")
            .append(binding != null && KeyInjector.isHeld(binding));
        line.append(" sneakCode=")
            .append(sneak.getKeyCode())
            .append("/pressed=")
            .append(sneak.getIsKeyPressed())
            .append("/raw=")
            .append(KeyInjector.isHeld(sneak))
            .append("/same=")
            .append(binding == sneak);

        // How many bindings answer to the same description. Two would mean the picker cannot tell them apart and the
        // one being pressed is not the one the game reads - which looks exactly like a press that does nothing.
        int sameDescription = 0;
        for (KeyBinding candidate : com.navatusein.radialmenu.client.input.KeyBindingLookup.all()) {
            if (sneak.getKeyDescription()
                .equals(candidate.getKeyDescription())) {
                sameDescription++;
            }
        }
        line.append(" sneakBindings=")
            .append(sameDescription);
        line.append(" lshift=")
            .append(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT))
            .append("/rshift=")
            .append(Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
        line.append(" focus=")
            .append(mc.inGameHasFocus)
            .append("/screen=")
            .append(
                mc.currentScreen == null ? "none"
                    : mc.currentScreen.getClass()
                        .getSimpleName());

        if (player == null) {
            line.append(" player=none");
        } else {
            line.append(" isSneaking=")
                .append(player.isSneaking())
                .append(" movementSneak=")
                .append(player.movementInput == null ? "none" : String.valueOf(player.movementInput.sneak))
                .append(" movementInput=")
                .append(
                    player.movementInput == null ? "none"
                        : player.movementInput.getClass()
                            .getName());
        }

        RadialMenuMod.LOG.info(line.toString());
    }
}
