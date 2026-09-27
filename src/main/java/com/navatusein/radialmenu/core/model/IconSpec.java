package com.navatusein.radialmenu.core.model;

import com.google.gson.annotations.SerializedName;

/**
 * What to draw in a slot.
 *
 * <p>
 * {@link Kind#SPRITE} refers to the bundled icon atlas by name ({@code phosphor:sword}); those glyphs are monochrome,
 * so {@link #color} exists to tell entries apart. Minecraft 1.7.10's font renderer cannot load a TrueType font, which
 * is why the set ships as an atlas rather than as glyphs the way newer radial-menu mods do it.
 */
public class IconSpec {

    public enum Kind {

        /** An item or block from the registry, drawn with the vanilla item renderer. */
        @SerializedName("item")
        ITEM,

        /** A sprite from the bundled atlas. */
        @SerializedName("sprite")
        SPRITE,

        /** A PNG the player dropped into the mod's {@code icons} folder. */
        @SerializedName("file")
        FILE
    }

    public Kind kind = Kind.ITEM;

    /** Registry name, atlas sprite name or file name, depending on {@link #kind}. */
    public String id;

    /** Item damage/metadata, only for {@link Kind#ITEM}. */
    public int meta;

    /** Optional {@code #RRGGBB} tint, only for {@link Kind#SPRITE} and {@link Kind#FILE}. */
    public String color;

    public static IconSpec item(String registryName, int meta) {
        IconSpec icon = new IconSpec();
        icon.kind = Kind.ITEM;
        icon.id = registryName;
        icon.meta = meta;
        return icon;
    }

    public static IconSpec sprite(String spriteName, String color) {
        IconSpec icon = new IconSpec();
        icon.kind = Kind.SPRITE;
        icon.id = spriteName;
        icon.color = color;
        return icon;
    }

    public static IconSpec file(String fileName) {
        IconSpec icon = new IconSpec();
        icon.kind = Kind.FILE;
        icon.id = fileName;
        return icon;
    }

    public IconSpec copy() {
        IconSpec copy = new IconSpec();
        copy.kind = kind;
        copy.id = id;
        copy.meta = meta;
        copy.color = color;
        return copy;
    }

    /**
     * Parses {@link #color} into 0xRRGGBB, falling back to white when absent or malformed - a bad colour should dim an
     * icon, never break the menu.
     */
    public int rgbOrWhite() {
        return parseRgb(color, 0xFFFFFF);
    }

    /**
     * Reads a colour for tinting, ignoring any alpha channel.
     *
     * <p>
     * The colour picker writes eight digits so ring colours can carry opacity; an icon tint has no use for it and
     * would otherwise read the alpha byte as part of the red channel.
     */
    public static int parseRgb(String hex, int fallback) {
        if (hex == null || hex.trim()
            .isEmpty()) {
            return fallback;
        }
        return com.navatusein.radialmenu.core.Colors.parseArgb(hex, fallback) & 0x00FFFFFF;
    }

    public void normalize() {
        if (kind == null) {
            kind = Kind.ITEM;
        }
    }
}
