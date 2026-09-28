package com.navatusein.radialmenu.client.icon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;

/**
 * The status-effect icons, read out of the potion registry.
 *
 * <p>
 * No atlas of our own: vanilla already ships every effect icon on the inventory sheet, and a mod adding an effect
 * hangs its own icon off the same mechanism. Taking them from the registry therefore covers modded effects, which
 * drawing our own copies never would.
 *
 * <p>
 * Identified by the potion's unlocalized name rather than its numeric id. Effect ids are assigned in load order and
 * shift when a pack changes, which would silently repoint an icon at a different effect; the name does not.
 */
public final class PotionIcons {

    /** Vanilla's own sheet, where the effect list in the inventory takes its icons from. */
    public static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/inventory.png");

    /** Icons sit in an 8-wide grid of 18x18 cells, this far down a 256x256 sheet. */
    private static final int CELL = 18;
    private static final int COLUMNS = 8;
    private static final int SHEET = 256;
    private static final int TOP = 198;

    private static final Map<String, Potion> BY_NAME = new HashMap<>();

    private static List<String> names;

    private PotionIcons() {}

    /** Every effect that has an icon, in registry order. */
    public static List<String> list() {
        load();
        return names;
    }

    public static Potion find(String name) {
        load();
        return BY_NAME.get(name);
    }

    public static float minU(int index) {
        return (index % COLUMNS) * CELL / (float) SHEET;
    }

    public static float minV(int index) {
        return (TOP + (index / COLUMNS) * CELL) / (float) SHEET;
    }

    public static float maxU(int index) {
        return minU(index) + CELL / (float) SHEET;
    }

    public static float maxV(int index) {
        return minV(index) + CELL / (float) SHEET;
    }

    private static void load() {
        if (names != null) {
            return;
        }
        names = new ArrayList<>();
        for (Potion potion : Potion.potionTypes) {
            // The array is indexed by id and has gaps; an effect with no icon has nothing to show.
            if (potion == null || !potion.hasStatusIcon()) {
                continue;
            }
            names.add(potion.getName());
            BY_NAME.put(potion.getName(), potion);
        }
    }
}
