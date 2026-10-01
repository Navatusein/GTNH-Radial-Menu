package com.navatusein.radialmenu;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.navatusein.radialmenu.client.action.ActionExecutors;
import com.navatusein.radialmenu.client.action.CommandActionExecutor;
import com.navatusein.radialmenu.client.action.KeyActionExecutor;
import com.navatusein.radialmenu.client.action.ProfileSwitchExecutor;
import com.navatusein.radialmenu.client.action.ScriptActionExecutor;
import com.navatusein.radialmenu.client.action.ScriptResumeExecutor;
import com.navatusein.radialmenu.client.action.SequenceExecutor;
import com.navatusein.radialmenu.client.command.CommandRadialMenu;
import com.navatusein.radialmenu.client.gui.WheelOverlayHandler;
import com.navatusein.radialmenu.client.icon.IconResourceReloadHandler;
import com.navatusein.radialmenu.client.input.WheelInputHandler;
import com.navatusein.radialmenu.client.input.WheelKeyBindings;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.client.profile.ProfileStorage;
import com.navatusein.radialmenu.client.script.ChatCapture;
import com.navatusein.radialmenu.config.AccentConfig;
import com.navatusein.radialmenu.config.AnimationConfig;
import com.navatusein.radialmenu.config.ColorConfig;
import com.navatusein.radialmenu.config.RadialMenuConfig;
import com.navatusein.radialmenu.config.WheelConfig;
import com.navatusein.radialmenu.core.action.ActionTypes;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/** Client-side setup: config, profile storage, action executors, keybindings and input handling. */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        try {
            // One file, four categories: the config GUI shows a button per registered class.
            ConfigurationManager.registerConfig(RadialMenuConfig.class);
            ConfigurationManager.registerConfig(WheelConfig.class);
            ConfigurationManager.registerConfig(ColorConfig.class);
            ConfigurationManager.registerConfig(AccentConfig.class);
            ConfigurationManager.registerConfig(AnimationConfig.class);
        } catch (ConfigException e) {
            throw new RuntimeException("Failed to register the RadialMenu config", e);
        }

        // Menus live in the game folder rather than config: they are structured data players copy around.
        ProfileStorage.init(Minecraft.getMinecraft().mcDataDir);

        ActionTypes.registerDefaults();
        ActionExecutors.register(new KeyActionExecutor());
        ActionExecutors.register(new ProfileSwitchExecutor());
        ActionExecutors.register(new CommandActionExecutor());
        ActionExecutors.register(new SequenceExecutor());
        ActionExecutors.register(new ScriptActionExecutor());
        // Registered like any other executor, though no profile ever names it: a script's menu entries carry one, and
        // they go through the same queue as everything else.
        ActionExecutors.register(new ScriptResumeExecutor());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        ProfileManager.load();

        ClientCommandHandler.instance.registerCommand(new CommandRadialMenu());

        IconResourceReloadHandler.register();

        // Overlay rendering is a Forge-bus event, unlike the ticks and key input the mod listens to on the FML bus.
        MinecraftForge.EVENT_BUS.register(new WheelOverlayHandler());
        // Chat is a Forge-bus event too - and one that arrives on Netty's thread, which is why the listener only
        // queues what it hears. See ChatCapture.
        MinecraftForge.EVENT_BUS.register(new ChatCapture.Listener());

        WheelKeyBindings.register();
        FMLCommonHandler.instance()
            .bus()
            .register(new WheelInputHandler());
    }
}
