package com.navatusein.radialmenu;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Shared lifecycle hooks. The mod is {@code clientSideOnly}, so in practice only {@link ClientProxy} ever runs; this
 * class exists to keep side-agnostic setup separable if that ever changes.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {}

    public void init(FMLInitializationEvent event) {}
}
