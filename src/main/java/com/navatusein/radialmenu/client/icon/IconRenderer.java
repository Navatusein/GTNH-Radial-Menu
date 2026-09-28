package com.navatusein.radialmenu.client.icon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.navatusein.radialmenu.RadialMenuMod;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.config.RadialMenuConfig;
import com.navatusein.radialmenu.core.Colors;
import com.navatusein.radialmenu.core.model.IconSpec;
import com.navatusein.radialmenu.core.model.MenuStyle;

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

    /** Items whose own renderer threw. Drawing one is left to the mod that owns it, and some of them cannot. */
    private static final Set<String> UNRENDERABLE = new HashSet<>();

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
            case EFFECT:
                drawEffect(icon, x, y, size);
                break;
            default:
                break;
        }
    }

    private static void drawItem(IconSpec icon, int x, int y) {
        if (UNRENDERABLE.contains(key(icon))) {
            return;
        }
        ItemStack stack = resolveItem(icon);
        if (stack == null || stack == MISSING) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableGUIStandardItemLighting();

        // The two calls that decide whether a block model comes out lit, copied from GuiContainer's slot pass.
        // A block is drawn scaled ten times, and without GL_RESCALE_NORMAL that scale goes into the normals and the
        // lighting is computed against the wrong ones - flat sprites, which ignore lighting entirely, escape it,
        // which is why only the three-dimensional icons looked dark. The lightmap has to be forced to full
        // brightness too, or an item inherits whatever coordinate world rendering left behind.
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        try {
            RenderItem.getInstance()
                .renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
        } catch (Throwable failure) {
            // Not every registered item can be drawn as a bare stack. Binnie's gene items ask their own breeding
            // system for a colour and throw when there is none, which there is not on a plain meta-0 stack - and
            // that took the client down mid-scroll through the picker, and would do the same in the world if such
            // an item were an entry's icon.
            //
            // Remembered rather than merely caught: the picker redraws every frame, so without this the log fills
            // at sixty stack traces a second and the cost is paid again on every one.
            UNRENDERABLE.add(key(icon));
            RadialMenuMod.LOG.warn("Item '" + icon.id + "' cannot be drawn as an icon", failure);
        } finally {
            // In a finally because the throw comes from the middle of the item renderer: without this the matrix
            // stays pushed and the lighting on, and the next thing drawn inherits both.
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glPopMatrix();
        }
    }

    /**
     * Tint for a sprite: its own colour if it has one, otherwise the profile's, otherwise the mod's config.
     *
     * <p>
     * Only sprites go through this. They are monochrome by design, so a colour is the only thing distinguishing
     * them; items carry their own, and a player's PNG is left as drawn.
     */
    /**
     * Tint for an icon that takes one.
     *
     * <p>
     * A null colour means the artwork keeps its own - that is what the PNG tab's "keep original colours" sets, and
     * it is deliberately different from an empty one, which means inherit.
     */
    private static int tintFor(IconSpec icon) {
        return icon.color == null ? 0xFFFFFF : resolveTint(icon.color);
    }

    /** The same resolution for a bare colour string, so the editor can show what inheriting will look like. */
    public static int resolveTint(String override) {
        int tint = Colors.parseArgb(RadialMenuConfig.iconColor, 0xFFFFFF) & 0x00FFFFFF;
        MenuStyle profileStyle = ProfileManager.active().style;
        if (profileStyle != null) {
            tint = Colors.over(profileStyle.iconColor, tint) & 0x00FFFFFF;
        }
        return Colors.over(override, tint) & 0x00FFFFFF;
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
            tintFor(icon),
            SpriteAtlas.minU(sprite),
            SpriteAtlas.minV(sprite),
            SpriteAtlas.maxU(sprite),
            SpriteAtlas.maxV(sprite));
    }

    /**
     * An effect icon, cut out of the sheet vanilla draws the inventory's effect list from.
     *
     * <p>
     * Untinted, like an item: the artwork is already coloured, and a tint would only muddy it. Only the monochrome
     * sprite set has a colour to gain from one.
     */
    private static void drawEffect(IconSpec icon, int x, int y, int size) {
        Potion potion = PotionIcons.find(icon.id);
        if (potion == null) {
            return;
        }
        // Vanilla's order, and it is load-bearing: bind the vanilla sheet first, then ask for the index. A modded
        // effect overrides getStatusIconIndex to rebind its own texture on the way out, so asking first and binding
        // afterwards would throw that away and cut the mod's index out of vanilla's sheet - a neighbouring icon,
        // drawn with confidence.
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(PotionIcons.TEXTURE);
        int index = potion.getStatusIconIndex();
        if (index < 0) {
            return;
        }

        drawTexturedQuad(
            x,
            y,
            size,
            0xFFFFFF,
            PotionIcons.minU(index),
            PotionIcons.minV(index),
            PotionIcons.maxU(index),
            PotionIcons.maxV(index));
    }

    private static void drawFile(IconSpec icon, int x, int y, int size) {
        ResourceLocation texture = UserIconLoader.texture(icon.id);
        if (texture == null) {
            return;
        }
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);

        // Same resolution as a sprite: an explicit colour wins, an empty one inherits the profile and then the
        // config, and a null one leaves the image exactly as it was drawn.
        drawTexturedQuad(x, y, size, tintFor(icon), 0f, 0f, 1f, 1f);
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

    /** Identifies an item icon, so the cache and the unrenderable list agree on what "the same item" means. */
    private static String key(IconSpec icon) {
        return icon.id + "#" + icon.meta;
    }

    private static ItemStack resolveItem(IconSpec icon) {
        String key = key(icon);
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
