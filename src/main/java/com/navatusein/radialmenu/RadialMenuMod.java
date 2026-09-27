package com.navatusein.radialmenu;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Client-side radial action menu.
 *
 * <p>
 * The mod registers no network channels and no server-side logic: every action it performs is something the player
 * could have done by hand, so it works on unmodified servers.
 */
@Mod(
    modid = RadialMenuMod.MODID,
    name = RadialMenuMod.MODNAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class RadialMenuMod {

    public static final String MODID = "radialmenu";
    public static final String MODNAME = "RadialMenu";

    public static final Logger LOG = LogManager.getLogger(MODNAME);

    @SidedProxy(
        clientSide = "com.navatusein.radialmenu.ClientProxy",
        serverSide = "com.navatusein.radialmenu.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
