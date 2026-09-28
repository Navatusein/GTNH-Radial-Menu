package com.navatusein.radialmenu.client.gui;

import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.SimpleGuiConfig;
import com.gtnewhorizon.gtnhlib.config.SimpleGuiFactory;
import com.navatusein.radialmenu.RadialMenuMod;

/**
 * Puts the mod's settings on the Mods screen.
 *
 * <p>
 * Declaring {@code @Config} is not enough on its own: without a {@code guiFactory} named in {@code @Mod}, Forge has
 * nothing to open and the Config button leads to an empty screen with a Done button on it. GTNHLib builds the screen
 * from the same annotations the file is written from, so there is nothing here to keep in step with the fields.
 */
public class ConfigGuiFactory implements SimpleGuiFactory {

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return ConfigGui.class;
    }

    public static class ConfigGui extends SimpleGuiConfig {

        public ConfigGui(GuiScreen parent) throws ConfigException {
            super(parent, RadialMenuMod.MODID, RadialMenuMod.MODNAME);
        }
    }
}
