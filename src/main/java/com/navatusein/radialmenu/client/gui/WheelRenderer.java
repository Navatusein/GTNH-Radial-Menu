package com.navatusein.radialmenu.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import com.navatusein.radialmenu.client.icon.IconRenderer;
import com.navatusein.radialmenu.config.RadialMenuConfig;
import com.navatusein.radialmenu.core.geometry.RadialGeometry;
import com.navatusein.radialmenu.core.model.MenuNode;

/**
 * Draws the ring.
 *
 * <p>
 * Everything here works in the GUI coordinate space that {@code drawScreen} already sets up, so no scaling or
 * y-flipping is needed and the geometry helpers can stay pure maths.
 */
public final class WheelRenderer {

    private static final int GAP_DEGREES = 2;

    private WheelRenderer() {}

    public static void drawWheel(MenuNode menu, int centerX, int centerY, int hoveredSlot) {
        int slotCount = menu.slotCount();
        if (slotCount <= 0) {
            return;
        }

        int outer = RadialMenuConfig.outerRadius;
        int inner = RadialMenuConfig.effectiveInnerRadius();
        int ringColor = parseArgb(RadialMenuConfig.ringColor, 0x99101010);
        int highlightColor = parseArgb(RadialMenuConfig.highlightColor, 0xCC4A90D9);

        // Mirrors the state vanilla's Gui.drawRect sets up. Cull face and alpha test are switched off explicitly
        // because arc geometry has no guaranteed winding, and leaving either on makes the ring silently invisible
        // while icons and text still draw.
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        double span = RadialGeometry.sectorSpan(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            boolean filled = menu.childAt(slot) != null;
            int color = slot == hoveredSlot ? highlightColor : ringColor;
            if (!filled) {
                // Empty positions of a fixed-size wheel stay visible but muted, so the angles a player has
                // memorised never move.
                color = fade(color, 0.35F);
            }
            double start = RadialGeometry.slotCenterAngle(slot, slotCount, 0.0) - span / 2.0 + GAP_DEGREES / 2.0;
            drawSector(centerX, centerY, inner, outer, start, span - GAP_DEGREES, color);
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();

        drawIcons(menu, centerX, centerY, slotCount, inner, outer);
        drawLabel(menu, centerX, centerY, hoveredSlot);
    }

    /**
     * Draws one ring segment as a fan of quads.
     *
     * <p>
     * Colour comes from {@code glColor4f} rather than {@code Tessellator.setColorRGBA_I}, matching how vanilla's
     * {@code Gui.drawRect} does it on this version - the tessellator's colour path can be disabled by whatever drew
     * last, and then the sector renders with no colour at all.
     */
    private static void drawSector(int centerX, int centerY, double innerRadius, double outerRadius, double startAngle,
        double spanDegrees, int argb) {
        int segments = RadialGeometry.arcSegments(spanDegrees);

        GL11.glColor4f(
            ((argb >> 16) & 0xFF) / 255.0F,
            ((argb >> 8) & 0xFF) / 255.0F,
            (argb & 0xFF) / 255.0F,
            ((argb >>> 24) & 0xFF) / 255.0F);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        for (int i = 0; i < segments; i++) {
            double from = Math.toRadians(startAngle + spanDegrees * i / segments);
            double to = Math.toRadians(startAngle + spanDegrees * (i + 1) / segments);

            double sinFrom = Math.sin(from);
            double cosFrom = Math.cos(from);
            double sinTo = Math.sin(to);
            double cosTo = Math.cos(to);

            tessellator.addVertex(centerX + sinFrom * innerRadius, centerY - cosFrom * innerRadius, 0.0);
            tessellator.addVertex(centerX + sinFrom * outerRadius, centerY - cosFrom * outerRadius, 0.0);
            tessellator.addVertex(centerX + sinTo * outerRadius, centerY - cosTo * outerRadius, 0.0);
            tessellator.addVertex(centerX + sinTo * innerRadius, centerY - cosTo * innerRadius, 0.0);
        }
        tessellator.draw();
    }

    private static void drawIcons(MenuNode menu, int centerX, int centerY, int slotCount, int inner, int outer) {
        double iconRadius = (inner + outer) / 2.0;
        for (int slot = 0; slot < slotCount; slot++) {
            MenuNode child = menu.childAt(slot);
            if (child == null || child.icon == null) {
                continue;
            }
            double angle = RadialGeometry.slotCenterAngle(slot, slotCount, 0.0);
            int x = (int) Math.round(centerX + RadialGeometry.offsetX(angle, iconRadius)) - IconRenderer.ICON_SIZE / 2;
            int y = (int) Math.round(centerY + RadialGeometry.offsetY(angle, iconRadius)) - IconRenderer.ICON_SIZE / 2;
            IconRenderer.draw(child.icon, x, y);
        }
    }

    private static void drawLabel(MenuNode menu, int centerX, int centerY, int hoveredSlot) {
        MenuNode hovered = menu.childAt(hoveredSlot);
        String text = hovered == null ? menu.title : hovered.title;
        if (text == null || text.isEmpty()) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        font.drawStringWithShadow(
            text,
            centerX - font.getStringWidth(text) / 2,
            centerY - font.FONT_HEIGHT / 2,
            0xFFFFFFFF);
    }

    private static int fade(int argb, float factor) {
        int alpha = (int) (((argb >>> 24) & 0xFF) * factor);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** Accepts {@code 0xAARRGGBB} as written in the config, falling back when a hand edit goes wrong. */
    public static int parseArgb(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String cleaned = value.trim();
        if (cleaned.startsWith("0x") || cleaned.startsWith("0X")) {
            cleaned = cleaned.substring(2);
        } else if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }
        try {
            return (int) Long.parseLong(cleaned, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
