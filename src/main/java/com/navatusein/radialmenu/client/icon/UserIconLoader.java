package com.navatusein.radialmenu.client.icon;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import com.navatusein.radialmenu.RadialMenuMod;
import com.navatusein.radialmenu.client.profile.ProfileStorage;

/**
 * Icons the player drops into {@code <game folder>/RadialMenu/icons} as PNG files.
 *
 * <p>
 * Loaded on demand and cached: the wheel resolves every visible icon each frame, so re-reading a file per frame is
 * not an option, and neither is retrying a missing one.
 */
public final class UserIconLoader {

    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    /** Names that failed to load, remembered so the failure is logged once rather than every frame. */
    private static final Map<String, Boolean> FAILED = new HashMap<>();

    private UserIconLoader() {}

    /** @return a bound texture location, or null when the file is missing or unreadable */
    public static ResourceLocation texture(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        ResourceLocation cached = TEXTURES.get(fileName);
        if (cached != null) {
            return cached;
        }
        if (FAILED.containsKey(fileName)) {
            return null;
        }

        File file = new File(ProfileStorage.iconsDir(), fileName);
        if (!file.isFile()) {
            FAILED.put(fileName, Boolean.TRUE);
            RadialMenuMod.LOG.warn("Icon file not found: " + file.getAbsolutePath());
            return null;
        }

        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                throw new IllegalArgumentException("not a readable image");
            }
            ResourceLocation location = Minecraft.getMinecraft()
                .getTextureManager()
                .getDynamicTextureLocation(RadialMenuMod.MODID + "_icon", new DynamicTexture(image));
            TEXTURES.put(fileName, location);
            return location;
        } catch (Exception e) {
            FAILED.put(fileName, Boolean.TRUE);
            RadialMenuMod.LOG.error("Could not load icon " + fileName, e);
            return null;
        }
    }

    /** PNG files currently in the icons folder, for the picker. */
    public static List<String> listFiles() {
        List<String> names = new ArrayList<>();
        File[] files = ProfileStorage.iconsDir()
            .listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName()
                    .toLowerCase(Locale.ROOT)
                    .endsWith(".png")) {
                    names.add(file.getName());
                }
            }
        }
        Collections.sort(names);
        return names;
    }

    /** Forgets cached textures and past failures, so a newly added file is picked up without a restart. */
    public static void refresh() {
        TEXTURES.clear();
        FAILED.clear();
    }
}
