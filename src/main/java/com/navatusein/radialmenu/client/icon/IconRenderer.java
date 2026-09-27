package com.navatusein.radialmenu.client.icon;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

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
        draw(icon, x, y, ICON_SIZE);
    }

    public static void draw(IconSpec icon, int x, int y, int size) {
        if (icon == null || icon.id == null) {
            return;
        }
        switch (icon.kind) {
            case ITEM:
                drawItem(icon, x, y);
                break;
            case SPRITE:
                drawSprite(icon, x, y, size);
                break;
            case FILE:
                drawFile(icon, x, y, size);
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

    private static void drawSprite(IconSpec icon, int x, int y, int size) {
        SpriteAtlas.Sprite sprite = SpriteAtlas.find(icon.id);
        if (sprite == null) {
            return;
        }
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(SpriteAtlas.TEXTURE);

        drawTexturedQuad(
            x,
            y,
            size,
            icon.rgbOrWhite(),
            SpriteAtlas.minU(sprite),
            SpriteAtlas.minV(sprite),
            SpriteAtlas.maxU(sprite),
            SpriteAtlas.maxV(sprite));
    }

    private static void drawFile(IconSpec icon, int x, int y, int size) {
        ResourceLocation texture = UserIconLoader.texture(icon.id);
        if (texture == null) {
            return;
        }
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);

        drawTexturedQuad(x, y, size, icon.rgbOrWhite(), 0f, 0f, 1f, 1f);
    }

    /**
     * Draws one tinted, textured quad.
     *
     * <p>
     * Explicit texture coordinates rather than {@code Gui.drawTexturedModalRect}, which assumes a 256x256 sheet - the
     * sprite atlas is 2048x1024, and user icons are whatever size the player saved.
     */
    private static void drawTexturedQuad(int x, int y, int size, int rgb, float minU, float minV, float maxU,
        float maxV) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, 1.0F);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + size, 0.0, minU, maxV);
        tessellator.addVertexWithUV(x + size, y + size, 0.0, maxU, maxV);
        tessellator.addVertexWithUV(x + size, y, 0.0, maxU, minV);
        tessellator.addVertexWithUV(x, y, 0.0, minU, minV);
        tessellator.draw();

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);
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
        UserIconLoader.refresh();
    }
}
