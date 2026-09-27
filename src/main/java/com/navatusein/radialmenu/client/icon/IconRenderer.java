package com.navatusein.radialmenu.client.icon;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.navatusein.radialmenu.core.model.IconSpec;

/**
 * Draws whatever a slot's {@link IconSpec} points at.
 *
 * <p>
 * Item lookups are cached because the wheel resolves every visible icon on every frame, and a miss - a registry name
 * belonging to a mod that is no longer installed - would otherwise be retried just as often.
 */
public final class IconRenderer {

    public static final int ICON_SIZE = 16;

    private static final Map<String, ItemStack> ITEM_CACHE = new HashMap<>();

    private static final ItemStack MISSING = new ItemStack(Item.getItemById(0));

    private IconRenderer() {}

    /** Draws a 16x16 icon with its top-left corner at the given position. */
    public static void draw(IconSpec icon, int x, int y) {
        if (icon == null || icon.id == null) {
            return;
        }
        switch (icon.kind) {
            case ITEM:
                drawItem(icon, x, y);
                break;
            case SPRITE:
            case FILE:
                // The bundled atlas and user PNGs are not wired up yet; a tinted marker keeps the slot readable
                // instead of silently rendering nothing.
                drawPlaceholder(icon, x, y);
                break;
            default:
                break;
        }
    }

    private static void drawItem(IconSpec icon, int x, int y) {
        ItemStack stack = resolveItem(icon);
        if (stack == null || stack == MISSING) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableGUIStandardItemLighting();
        RenderItem.getInstance()
            .renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

    private static void drawPlaceholder(IconSpec icon, int x, int y) {
        int rgb = icon.rgbOrWhite();
        Gui.drawRect(x + 3, y + 3, x + ICON_SIZE - 3, y + ICON_SIZE - 3, 0xFF000000 | rgb);
    }

    private static ItemStack resolveItem(IconSpec icon) {
        String key = icon.id + "#" + icon.meta;
        ItemStack cached = ITEM_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        ItemStack stack = MISSING;
        Object entry = Item.itemRegistry.getObject(icon.id);
        if (entry instanceof Item) {
            stack = new ItemStack((Item) entry, 1, Math.max(0, icon.meta));
        }
        ITEM_CACHE.put(key, stack);
        return stack;
    }

    /** Drops cached lookups, for when the editor wants a freshly typed registry name re-resolved. */
    public static void clearCache() {
        ITEM_CACHE.clear();
    }
}
