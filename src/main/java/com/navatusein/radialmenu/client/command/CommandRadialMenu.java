package com.navatusein.radialmenu.client.command;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.editor.GuiProfileManager;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.client.profile.ProfileStorage;

/**
 * Client-side {@code /radialmenu}.
 *
 * <p>
 * Registered through {@code ClientCommandHandler}, so it never reaches the server and works on any server, modded or
 * not. It is also the entry point that costs no keybinding - the point of the mod being to stop spending keys.
 */
public class CommandRadialMenu extends CommandBase {

    @Override
    public String getCommandName() {
        return "radialmenu";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/radialmenu <edit|profile|profiles|reload>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        // Client-side only: nothing here touches the server, so no permission is needed.
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            reply(sender, getCommandUsage(sender));
            return;
        }

        String sub = args[0].toLowerCase();

        if ("edit".equals(sub)) {
            // Deferred by a tick: opening a screen from here would be undone by the chat screen closing afterwards.
            GuiStack.requestOpen(new GuiProfileManager());
            return;
        }

        if ("profiles".equals(sub)) {
            List<String> names = ProfileStorage.listProfileNames();
            reply(sender, "Profiles: " + (names.isEmpty() ? "-" : join(names)));
            reply(sender, "Active: " + ProfileManager.activeName());
            return;
        }

        if ("profile".equals(sub)) {
            if (args.length < 2) {
                reply(sender, "Active profile: " + ProfileManager.activeName());
                return;
            }
            String target = args[1];
            if (ProfileManager.switchTo(target)) {
                reply(sender, "Switched to profile " + target);
            } else {
                replyError(sender, "No profile named " + target);
            }
            return;
        }

        if ("reload".equals(sub)) {
            ProfileManager.load();
            reply(sender, "Reloaded profiles from disk");
            return;
        }

        reply(sender, getCommandUsage(sender));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "edit", "profile", "profiles", "reload");
        }
        if (args.length == 2 && "profile".equalsIgnoreCase(args[0])) {
            List<String> names = ProfileStorage.listProfileNames();
            return getListOfStringsMatchingLastWord(args, names.toArray(new String[0]));
        }
        return new ArrayList<>();
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static void reply(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "[RadialMenu] " + message));
    }

    private static void replyError(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[RadialMenu] " + message));
    }
}
