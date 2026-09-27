package com.navatusein.radialmenu.client.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.SimpleReloadableResourceManager;

import com.navatusein.radialmenu.RadialMenuMod;

/**
 * Drops cached icon data whenever resources are reloaded.
 *
 * <p>
 * Without this, swapping a resource pack that overrides the sprite sheet would leave the atlas coordinates and the
 * resolved item stacks from the previous pack in place - the texture changes underneath while the mod keeps drawing
 * with stale offsets.
 */
public class IconResourceReloadHandler implements IResourceManagerReloadListener {

    public static void register() {
        IResourceManager manager = Minecraft.getMinecraft()
            .getResourceManager();

        if (manager instanceof SimpleReloadableResourceManager) {
            ((SimpleReloadableResourceManager) manager).registerReloadListener(new IconResourceReloadHandler());
        } else {
            RadialMenuMod.LOG.warn("Resource manager is not reloadable; icon caches will not refresh on pack changes");
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        SpriteAtlas.invalidate();
        IconRenderer.clearCache();
    }
}
