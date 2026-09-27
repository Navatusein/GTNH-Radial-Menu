package com.navatusein.radialmenu.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.navatusein.radialmenu.client.icon.IconRenderer;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.config.RadialMenuConfig;
import com.navatusein.radialmenu.core.Colors;
import com.navatusein.radialmenu.core.geometry.RadialGeometry;
import com.navatusein.radialmenu.core.model.MenuNode;
import com.navatusein.radialmenu.core.model.MenuStyle;

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

    public static void drawWheel(MenuNode menu, int slotCount, int centerX, int centerY, int hoveredSlot,
        boolean editMode) {
        if (slotCount <= 0) {
            return;
        }

        int outer = RadialMenuConfig.outerRadius;
        int inner = RadialMenuConfig.effectiveInnerRadius();

        // A menu may override the global colours, so one submenu can read differently from another. An override
        // from the colour picker carries no alpha, so it takes the transparency of the value it replaces - read as
        // an eight-digit colour it would be fully transparent, and the ring would simply vanish.
        MenuStyle style = menu.style;
        // Colours inherit down a chain: this menu, then the profile, then the mod's config, which holds the defaults.
        MenuStyle profileStyle = ProfileManager.active().style;

        int ringColor = Colors.parseArgb(RadialMenuConfig.ringColor, 0x99101010);
        ringColor = Colors.over(profileStyle == null ? null : profileStyle.ringColor, ringColor);
        ringColor = Colors.over(style == null ? null : style.ringColor, ringColor);

        int highlightColor = Colors.parseArgb(RadialMenuConfig.highlightColor, 0xCC4A90D9);
        highlightColor = Colors.over(profileStyle == null ? null : profileStyle.highlightColor, highlightColor);
        highlightColor = Colors.over(style == null ? null : style.highlightColor, highlightColor);

        if (editMode) {
            // Unmistakably a different mode: the ring takes on the edit tint rather than relying on a label alone.
            ringColor = blend(ringColor, 0xFFAA00, 0.25F);
            highlightColor = blend(highlightColor, 0xFFAA00, 0.45F);
        }

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

    /** Only the hovered entry's name. The profile and the path live in the header, out of the way of the ring. */
    private static void drawLabel(MenuNode menu, int centerX, int centerY, int hoveredSlot) {
        MenuNode hovered = menu.childAt(hoveredSlot);
        String text = hovered == null ? null : hovered.title;
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

    /**
     * Draws the profile name and the path into the current submenu across the top of the screen.
     *
     * <p>
     * These used to sit in the middle of the ring, where they competed with the hovered entry's name and moved the
     * one thing the player is actually reading while aiming.
     */
    public static void drawHeader(int screenWidth, String profileName, String breadcrumb, boolean editMode) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        String left = EnumChatFormatting.GRAY + profileName;
        String path = breadcrumb == null || breadcrumb.isEmpty() ? "" : EnumChatFormatting.WHITE + breadcrumb;

        StringBuilder line = new StringBuilder(left);
        if (!path.isEmpty()) {
            line.append(EnumChatFormatting.DARK_GRAY)
                .append("  >  ")
                .append(path);
        }
        if (editMode) {
            line.append("   ")
                .append(EnumChatFormatting.GOLD)
                .append(EnumChatFormatting.BOLD)
                .append(I18n.format("radialmenu.wheel.editing"));
        }

        String text = line.toString();
        int width = font.getStringWidth(text);
        int x = screenWidth / 2 - width / 2;

        Gui.drawRect(x - 6, 4, x + width + 6, 20, 0x80000000);
        font.drawStringWithShadow(text, x, 8, 0xFFFFFFFF);
    }

    /**
     * While editing, says what the middle of the wheel does.
     *
     * <p>
     * The dead zone is otherwise dead space, which makes it the natural home for the menu's own settings - and the
     * only place the root menu could be reached from at all.
     */
    public static void drawCenterHint(int centerX, int centerY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String text = I18n.format("radialmenu.wheel.menuSettings");
        font.drawStringWithShadow(
            text,
            centerX - font.getStringWidth(text) / 2,
            centerY - font.FONT_HEIGHT / 2,
            0xFFFFAA00);
    }

    /** Mixes an RGB tint into an ARGB colour, keeping its alpha. */
    private static int blend(int argb, int rgb, float amount) {
        int alpha = argb >>> 24;
        int r = Math.round(((argb >> 16) & 0xFF) * (1 - amount) + ((rgb >> 16) & 0xFF) * amount);
        int g = Math.round(((argb >> 8) & 0xFF) * (1 - amount) + ((rgb >> 8) & 0xFF) * amount);
        int b = Math.round((argb & 0xFF) * (1 - amount) + (rgb & 0xFF) * amount);
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    private static int fade(int argb, float factor) {
        int alpha = (int) (((argb >>> 24) & 0xFF) * factor);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

}
