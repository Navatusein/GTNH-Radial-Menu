package com.navatusein.radialmenu.client.icon;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import com.google.gson.Gson;
import com.navatusein.radialmenu.RadialMenuMod;

/**
 * The bundled Phosphor sprite sheet.
 *
 * <p>
 * The icons ship as a pre-rendered atlas rather than as font glyphs, because 1.7.10's font renderer only handles
 * bitmap fonts. {@code tools/GenerateIconAtlas.java} bakes the sheet and the catalogue read here.
 */
public final class SpriteAtlas {

    /** Prefix used in a profile: {@code phosphor:sword}. */
    public static final String NAMESPACE = "phosphor";

    public static final ResourceLocation TEXTURE = new ResourceLocation(
        RadialMenuMod.MODID,
        "textures/icons/phosphor.png");

    private static final ResourceLocation CATALOGUE = new ResourceLocation(RadialMenuMod.MODID, "icons/phosphor.json");

    /** One entry of the catalogue; field names match the generated JSON. */
    public static class Sprite {

        public String name;
        public int u;
        public int v;
        /** Space-separated search terms, straight from the Phosphor catalogue. */
        public String tags;
    }

    private static class Catalogue {

        int atlasWidth;
        int atlasHeight;
        int cell;
        List<Sprite> icons;
    }

    private static final Map<String, Sprite> BY_NAME = new HashMap<>();
    private static final List<Sprite> ORDERED = new ArrayList<>();

    private static int atlasWidth = 2048;
    private static int atlasHeight = 1024;
    private static int cell = 32;
    private static boolean loaded;

    private SpriteAtlas() {}

    /** Reads the catalogue once. A failure leaves the atlas empty rather than breaking icon rendering entirely. */
    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Reader reader = null;
        try {
            reader = new InputStreamReader(
                Minecraft.getMinecraft()
                    .getResourceManager()
                    .getResource(CATALOGUE)
                    .getInputStream(),
                Charset.forName("UTF-8"));

            Catalogue catalogue = new Gson().fromJson(reader, Catalogue.class);
            if (catalogue == null || catalogue.icons == null) {
                RadialMenuMod.LOG.error("Sprite catalogue is empty");
                return;
            }

            atlasWidth = catalogue.atlasWidth > 0 ? catalogue.atlasWidth : atlasWidth;
            atlasHeight = catalogue.atlasHeight > 0 ? catalogue.atlasHeight : atlasHeight;
            cell = catalogue.cell > 0 ? catalogue.cell : cell;

            for (Sprite sprite : catalogue.icons) {
                if (sprite != null && sprite.name != null) {
                    BY_NAME.put(sprite.name, sprite);
                    ORDERED.add(sprite);
                }
            }
            RadialMenuMod.LOG.info("Loaded " + ORDERED.size() + " sprite icons");
        } catch (Exception e) {
            RadialMenuMod.LOG.error("Could not read the sprite catalogue", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    // Nothing useful to do; the read already succeeded or was reported.
                }
            }
        }
    }

    /** @param id either {@code phosphor:name} or a bare {@code name} */
    public static Sprite find(String id) {
        load();
        if (id == null) {
            return null;
        }
        String name = id.startsWith(NAMESPACE + ":") ? id.substring(NAMESPACE.length() + 1) : id;
        return BY_NAME.get(name);
    }

    public static List<Sprite> all() {
        load();
        return Collections.unmodifiableList(ORDERED);
    }

    /** Matches a query against the icon's name and the Phosphor catalogue's own search tags. */
    public static List<Sprite> search(String query) {
        load();
        if (query == null || query.trim()
            .isEmpty()) {
            return all();
        }
        String needle = query.trim()
            .toLowerCase();
        List<Sprite> matches = new ArrayList<>();
        for (Sprite sprite : ORDERED) {
            if (sprite.name.contains(needle) || (sprite.tags != null && sprite.tags.contains(needle))) {
                matches.add(sprite);
            }
        }
        return matches;
    }

    public static String qualify(String name) {
        return NAMESPACE + ":" + name;
    }

    public static int cellSize() {
        load();
        return cell;
    }

    public static float minU(Sprite sprite) {
        return sprite.u / (float) atlasWidth;
    }

    public static float maxU(Sprite sprite) {
        return (sprite.u + cell) / (float) atlasWidth;
    }

    public static float minV(Sprite sprite) {
        return sprite.v / (float) atlasHeight;
    }

    public static float maxV(Sprite sprite) {
        return (sprite.v + cell) / (float) atlasHeight;
    }

    /** Dropped when resources reload, so a resource pack swap does not keep stale coordinates. */
    public static void invalidate() {
        loaded = false;
        BY_NAME.clear();
        ORDERED.clear();
    }
}
