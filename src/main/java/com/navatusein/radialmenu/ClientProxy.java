package com.navatusein.radialmenu;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.navatusein.radialmenu.client.action.ActionExecutors;
import com.navatusein.radialmenu.client.action.CommandActionExecutor;
import com.navatusein.radialmenu.client.action.KeyActionExecutor;
import com.navatusein.radialmenu.client.action.ProfileSwitchExecutor;
import com.navatusein.radialmenu.client.action.SequenceExecutor;
import com.navatusein.radialmenu.client.command.CommandRadialMenu;
import com.navatusein.radialmenu.client.input.WheelInputHandler;
import com.navatusein.radialmenu.client.input.WheelKeyBindings;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.client.profile.ProfileStorage;
import com.navatusein.radialmenu.config.RadialMenuConfig;
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
            ConfigurationManager.registerConfig(RadialMenuConfig.class);
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
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        ProfileManager.load();

        ClientCommandHandler.instance.registerCommand(new CommandRadialMenu());

        WheelKeyBindings.register();
        FMLCommonHandler.instance()
            .bus()
            .register(new WheelInputHandler());
    }
}
