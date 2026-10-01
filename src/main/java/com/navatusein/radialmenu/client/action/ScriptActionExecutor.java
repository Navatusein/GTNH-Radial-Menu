package com.navatusein.radialmenu.client.action;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.navatusein.radialmenu.client.script.ScriptHost;
import com.navatusein.radialmenu.config.RadialMenuConfig;
import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionTypes;

/** Starts the Lua script an entry carries. */
public class ScriptActionExecutor implements IActionExecutor {

    @Override
    public String typeId() {
        return ActionTypes.SCRIPT;
    }

    @Override
    public boolean execute(ActionSpec spec) {
        if (!RadialMenuConfig.enableScripts) {
            // Said out loud rather than ignored: a slot that silently does nothing sends the player looking at the
            // script, which is the one place the fault is not.
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.RED + "Scripts are turned off in the RadialMenu config."));
            }
            return false;
        }
        return ScriptHost.start(spec);
    }
}
