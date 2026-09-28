package com.navatusein.radialmenu.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.navatusein.radialmenu.client.icon.IconRenderer;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.config.AnimationConfig;
import com.navatusein.radialmenu.config.ColorConfig;
import com.navatusein.radialmenu.config.SlotPlate;
import com.navatusein.radialmenu.config.WheelConfig;
import com.navatusein.radialmenu.core.animation.RevealAnimation;
import com.navatusein.radialmenu.core.geometry.RadialGeometry;
import com.navatusein.radialmenu.core.model.MenuNode;
import com.navatusein.radialmenu.core.model.StyleResolver;
import com.navatusein.radialmenu.core.model.WheelColors;

/**
 * Draws the ring.
 *
 * <p>
 * Everything here works in the GUI coordinate space that {@code drawScreen} already sets up, so no scaling or
 * y-flipping is needed and the geometry helpers can stay pure maths.
 */
public final class WheelRenderer {

    /** Below this the wrapping is worse than the overflow, one short word to a line. */
    private static final int MIN_LABEL_WIDTH = 48;

    /** How far the soft edge reaches. One pixel: enough to lose the staircase, not enough to look blurred. */
    private static final double FEATHER = 1.0;

    /** Vanilla's inventory sheet, for the slot cell at (7, 141). */
    private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");

    /** Vanilla's widget sheet, for the hotbar selection frame at (0, 22). */
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");

    private WheelRenderer() {}

    /**
     * What this menu's wheel is coloured with: the mod's config, then the profile, then the menu itself.
     *
     * <p>
     * Resolved once per frame and handed to everything that draws, rather than each part working out its own colour -
     * the chain is three levels deep and an accent expands into five values, which is far too much arithmetic to have
     * two copies of.
     */
    public static WheelColors colorsFor(MenuNode menu, boolean editMode) {
        WheelColors colors = StyleResolver
            .resolve(ColorConfig.defaultColors(), ProfileManager.active().style, menu.style);

        if (!editMode) {
            return colors;
        }
        // Unmistakably a different mode: the ring takes on the edit tint rather than relying on a label alone.
        return new WheelColors(
            blend(colors.ring, 0xFFAA00, 0.25F),
            blend(colors.highlight, 0xFFAA00, 0.45F),
            colors.border,
            colors.highlightBorder,
            colors.background,
            colors.icon);
    }

    public static void drawWheel(MenuNode menu, WheelColors colors, int slotCount, int centerX, int centerY,
        int hoveredSlot, boolean editMode, WheelAnimator animator) {
        if (slotCount <= 0) {
            return;
        }

        int outer = WheelConfig.outerRadius;
        int inner = WheelConfig.effectiveInnerRadius();

        // Only the growing kinds scale; a fade leaves the wheel where it is and brings it up in place.
        boolean grows = AnimationConfig.revealAnimation != RevealAnimation.FADE;
        double gap = Math.max(0.0, WheelConfig.sectorGap);
        double width = Math.max(1, WheelConfig.borderWidth);

        boolean outline = (colors.border >>> 24) != 0 && WheelConfig.drawOutline;
        boolean smooth = WheelConfig.smoothEdges;

        // The outline belongs to each sector, never to the wheel. Drawn as two circles and a set of dividers it is
        // the same picture while nothing moves - and the moment one sector grows or leans out towards the cursor,
        // the ring stays behind and cuts across it. Sides half a width each when there is no gap, so two
        // neighbours' lines meet as the single line that used to straddle the boundary.
        double sideWidth = gap > 0.0 ? width : width / 2.0;

        beginShapes();

        double span = RadialGeometry.sectorSpan(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            float reveal = animator.reveal(slot, slotCount);
            if (reveal <= 0.01f) {
                continue;
            }
            double sectorInner = sectorRadius(inner, reveal, grows, animator.push(slot));
            double sectorOuter = sectorRadius(outer, reveal, grows, animator.push(slot));

            boolean filled = menu.childAt(slot) != null;
            int color = slot == hoveredSlot ? colors.highlight : colors.ring;
            if (!filled) {
                // Empty positions of a fixed-size wheel stay visible but muted, so the angles a player has
                // memorised never move.
                color = fade(color, 0.35F);
            }
            color = fade(color, reveal);
            int lineColor = fade(colors.border, reveal);
            double start = RadialGeometry.slotCenterAngle(slot, slotCount, 0.0) - span / 2.0;

            fillSector(centerX, centerY, sectorInner, sectorOuter, start, span, gap, color);

            if (outline) {
                outlineSector(
                    centerX,
                    centerY,
                    sectorInner,
                    sectorOuter,
                    start,
                    span,
                    gap,
                    width,
                    sideWidth,
                    lineColor);
            }

            if (smooth) {
                // Whatever sits on an edge is what fades out of it: the outline where it is drawn, the fill where
                // it is not. Feathering both would lay two fades over the same pixels, which reads as a thicker,
                // dirtier outline rather than a softer one.
                featherArcs(centerX, centerY, sectorInner, sectorOuter, start, span, gap, outline ? lineColor : color);
                // Only across a gap: with none, a sector's sides touch its neighbours' and the two fades would
                // darken every boundary from the inside.
                if (gap > 0.0) {
                    featherSides(
                        centerX,
                        centerY,
                        sectorInner,
                        sectorOuter,
                        start,
                        span,
                        gap,
                        outline ? lineColor : color);
                }
            }
        }

        if (WheelConfig.drawHighlightOutline && (colors.highlightBorder >>> 24) != 0
            && hoveredSlot >= 0
            && hoveredSlot < slotCount) {
            float reveal = animator.reveal(hoveredSlot, slotCount);
            double start = RadialGeometry.slotCenterAngle(hoveredSlot, slotCount, 0.0) - span / 2.0;
            outlineSector(
                centerX,
                centerY,
                sectorRadius(inner, reveal, grows, animator.push(hoveredSlot)),
                sectorRadius(outer, reveal, grows, animator.push(hoveredSlot)),
                start,
                span,
                gap,
                width,
                width,
                fade(colors.highlightBorder, reveal));
        }

        endShapes();

        drawPlates(menu, centerX, centerY, slotCount, inner, outer, hoveredSlot, grows, animator);
        drawIcons(menu, centerX, centerY, slotCount, inner, outer, grows, animator);
        drawLabel(menu, centerX, centerY, hoveredSlot);
    }

    /**
     * Where a radius sits this frame: scaled by how far the sector has arrived, then pushed out by the hover.
     *
     * <p>
     * The push is added rather than scaled in, so a sector leans out of the ring by the same few pixels whether the
     * wheel is large or small - it is a nudge towards the cursor, not a size.
     */
    private static double sectorRadius(double radius, float reveal, boolean grows, double push) {
        return (grows ? radius * reveal : radius) + push;
    }

    /**
     * Mirrors the state vanilla's {@code Gui.drawRect} sets up.
     *
     * <p>
     * Cull face and alpha test are switched off explicitly because arc geometry has no guaranteed winding, and
     * leaving either on makes the ring silently invisible while icons and text still draw.
     */
    private static void beginShapes() {
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void endShapes() {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    /**
     * Draws one ring segment as a fan of quads.
     *
     * <p>
     * Colour comes from {@code glColor4f} rather than {@code Tessellator.setColorRGBA_I}, matching how vanilla's
     * {@code Gui.drawRect} does it on this version - the tessellator's colour path can be disabled by whatever drew
     * last, and then the sector renders with no colour at all. The bands that fade do set a colour per vertex, which
     * is safe because every vertex of those gets one.
     *
     * <p>
     * The gap is taken off each end as a distance rather than an angle, so the inner end of a sector is inset by
     * more degrees than the outer one and the two sectors stay the same distance apart all the way along.
     */
    private static void fillSector(int centerX, int centerY, double inner, double outer, double startAngle,
        double spanDegrees, double gap, int argb) {
        double insetInner = inset(inner, gap, spanDegrees);
        double insetOuter = inset(outer, gap, spanDegrees);
        int segments = RadialGeometry.arcSegments(spanDegrees);

        glColor(argb);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        for (int i = 0; i < segments; i++) {
            double t0 = (double) i / segments;
            double t1 = (double) (i + 1) / segments;

            double innerFrom = lerpAngle(startAngle, spanDegrees, insetInner, t0);
            double innerTo = lerpAngle(startAngle, spanDegrees, insetInner, t1);
            double outerFrom = lerpAngle(startAngle, spanDegrees, insetOuter, t0);
            double outerTo = lerpAngle(startAngle, spanDegrees, insetOuter, t1);

            addVertex(tessellator, centerX, centerY, innerFrom, inner);
            addVertex(tessellator, centerX, centerY, outerFrom, outer);
            addVertex(tessellator, centerX, centerY, outerTo, outer);
            addVertex(tessellator, centerX, centerY, innerTo, inner);
        }
        tessellator.draw();
    }

    /** Half the gap at a radius, in degrees, never eating more than the sector has. */
    private static double inset(double radius, double gap, double spanDegrees) {
        return Math.min(RadialGeometry.gapInsetDegrees(radius, gap), spanDegrees / 2.0);
    }

    /** Angle a fraction of the way across a sector that has been inset at both ends. */
    private static double lerpAngle(double startAngle, double spanDegrees, double inset, double fraction) {
        double from = startAngle + inset;
        double to = startAngle + spanDegrees - inset;
        return from + (to - from) * fraction;
    }

    /**
     * The one pixel fade that stands in for hardware anti-aliasing.
     *
     * <p>
     * {@code GL_POLYGON_SMOOTH} would be the obvious answer and is the wrong one twice over. It wants the geometry
     * sorted and blended with {@code GL_SRC_ALPHA_SATURATE}, and a sector here is a strip of quads that share edges -
     * each shared edge would come out at partial coverage and the ring would be drawn with seams across it. And it
     * is a legacy path that several drivers quietly ignore or route through software, so the wheel would look
     * different on every third machine. A band with its alpha ramped to nothing costs a few polygons and looks the
     * same everywhere.
     */
    private static void featherArcs(int centerX, int centerY, double inner, double outer, double startAngle,
        double spanDegrees, double gap, int argb) {
        double insetInner = inset(inner, gap, spanDegrees);
        double insetOuter = inset(outer, gap, spanDegrees);

        arcBand(
            centerX,
            centerY,
            outer,
            outer + FEATHER,
            startAngle + insetOuter,
            startAngle + spanDegrees - insetOuter,
            argb,
            transparent(argb));
        arcBand(
            centerX,
            centerY,
            inner - FEATHER,
            inner,
            startAngle + insetInner,
            startAngle + spanDegrees - insetInner,
            transparent(argb),
            argb);
    }

    /** The fade along a sector's straight sides, which only exist once there is a gap to see them against. */
    private static void featherSides(int centerX, int centerY, double inner, double outer, double startAngle,
        double spanDegrees, double gap, int argb) {
        sideBand(centerX, centerY, inner, outer, startAngle, spanDegrees, gap, FEATHER, true, argb);
        sideBand(centerX, centerY, inner, outer, startAngle, spanDegrees, gap, FEATHER, false, argb);
    }

    /**
     * The outline of one sector: an arc at each radius and a line down each side.
     *
     * @param width     thickness of the two arcs
     * @param sideWidth thickness of the sides, which is half as much when there is no gap and a neighbour is
     *                  drawing its own line against this one
     */
    private static void outlineSector(int centerX, int centerY, double inner, double outer, double startAngle,
        double spanDegrees, double gap, double width, double sideWidth, int argb) {
        double insetInner = inset(inner, gap, spanDegrees);
        double insetOuter = inset(outer, gap, spanDegrees);

        arcBand(
            centerX,
            centerY,
            outer - width,
            outer,
            startAngle + insetOuter,
            startAngle + spanDegrees - insetOuter,
            argb,
            argb);
        arcBand(
            centerX,
            centerY,
            inner,
            inner + width,
            startAngle + insetInner,
            startAngle + spanDegrees - insetInner,
            argb,
            argb);
        sideBand(centerX, centerY, inner, outer, startAngle, spanDegrees, gap, -sideWidth, true, argb);
        sideBand(centerX, centerY, inner, outer, startAngle, spanDegrees, gap, -sideWidth, false, argb);
    }

    /**
     * A band between two radii over a range of angles, with a colour at each radius.
     *
     * <p>
     * One shape for the ring's edges, the highlight's outline and every fade: they differ only in how far apart the
     * radii are and whether the two colours are the same.
     */
    private static void arcBand(int centerX, int centerY, double from, double to, double fromAngle, double toAngle,
        int fromArgb, int toArgb) {
        double sweep = toAngle - fromAngle;
        int segments = RadialGeometry.arcSegments(Math.abs(sweep));

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        for (int i = 0; i < segments; i++) {
            double a = fromAngle + sweep * i / segments;
            double b = fromAngle + sweep * (i + 1) / segments;

            vertexColored(tessellator, centerX, centerY, a, from, fromArgb);
            vertexColored(tessellator, centerX, centerY, a, to, toArgb);
            vertexColored(tessellator, centerX, centerY, b, to, toArgb);
            vertexColored(tessellator, centerX, centerY, b, from, fromArgb);
        }
        tessellator.draw();
    }

    /**
     * A band along one straight side of a sector.
     *
     * <p>
     * A positive width runs outwards, into the gap, and fades to nothing - that is the soft edge. A negative one
     * runs inwards at full strength, which is the sector's own outline. Either way it follows the side exactly,
     * because the side is a straight line and an arc would drift away from it.
     *
     * @param leading the side the sector starts at, as against the one it ends at
     */
    private static void sideBand(int centerX, int centerY, double inner, double outer, double startAngle,
        double spanDegrees, double gap, double width, boolean leading, int argb) {
        double insetInner = inset(inner, gap, spanDegrees);
        double insetOuter = inset(outer, gap, spanDegrees);

        double innerAngle = leading ? startAngle + insetInner : startAngle + spanDegrees - insetInner;
        double outerAngle = leading ? startAngle + insetOuter : startAngle + spanDegrees - insetOuter;

        double x1 = centerX + RadialGeometry.offsetX(innerAngle, inner);
        double y1 = centerY + RadialGeometry.offsetY(innerAngle, inner);
        double x2 = centerX + RadialGeometry.offsetX(outerAngle, outer);
        double y2 = centerY + RadialGeometry.offsetY(outerAngle, outer);

        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0) {
            return;
        }

        // Two candidate normals; the one pointing away from the middle of the sector is the outward one. Worked out
        // rather than assumed, because which of the two it is flips with the side and again past half the circle.
        double nx = dy / length;
        double ny = -dx / length;
        double midAngle = startAngle + spanDegrees / 2.0;
        double midX = centerX + RadialGeometry.offsetX(midAngle, (inner + outer) / 2.0);
        double midY = centerY + RadialGeometry.offsetY(midAngle, (inner + outer) / 2.0);
        if (nx * ((x1 + x2) / 2.0 - midX) + ny * ((y1 + y2) / 2.0 - midY) < 0.0) {
            nx = -nx;
            ny = -ny;
        }

        boolean fade = width > 0.0;
        double offset = Math.abs(width) * (fade ? 1.0 : -1.0);
        int nearArgb = argb;
        int farArgb = fade ? transparent(argb) : argb;

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        vertexColored(tessellator, x1, y1, nearArgb);
        vertexColored(tessellator, x2, y2, nearArgb);
        vertexColored(tessellator, x2 + nx * offset, y2 + ny * offset, farArgb);
        vertexColored(tessellator, x1 + nx * offset, y1 + ny * offset, farArgb);
        tessellator.draw();
    }

    private static void addVertex(Tessellator tessellator, int centerX, int centerY, double angle, double radius) {
        tessellator.addVertex(
            centerX + RadialGeometry.offsetX(angle, radius),
            centerY + RadialGeometry.offsetY(angle, radius),
            0.0);
    }

    private static void vertexColored(Tessellator tessellator, int centerX, int centerY, double angle, double radius,
        int argb) {
        vertexColored(
            tessellator,
            centerX + RadialGeometry.offsetX(angle, radius),
            centerY + RadialGeometry.offsetY(angle, radius),
            argb);
    }

    /**
     * A vertex carrying its own colour.
     *
     * <p>
     * Every vertex of such a draw has to set one: the tessellator remembers the last colour it was given and writes
     * it into each vertex, so one that is added before any colour at all comes out transparent black.
     */
    private static void vertexColored(Tessellator tessellator, double x, double y, int argb) {
        tessellator.setColorRGBA((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
        tessellator.addVertex(x, y, 0.0);
    }

    private static int transparent(int argb) {
        return argb & 0x00FFFFFF;
    }

    private static void glColor(int argb) {
        GL11.glColor4f(
            ((argb >> 16) & 0xFF) / 255.0F,
            ((argb >> 8) & 0xFF) / 255.0F,
            (argb & 0xFF) / 255.0F,
            ((argb >>> 24) & 0xFF) / 255.0F);
    }

    /**
     * The plate behind each icon, when the player has asked for one.
     *
     * <p>
     * Drawn from vanilla's own sheets: the inventory's slot cell and the hotbar's selection frame. Newer radial
     * menus use 1.14's gamemode-switcher art, which does not exist here and could not be shipped if it did - and
     * the hotbar's own plate looks more at home under a 1.7.10 item than a visitor from a later version would.
     *
     * <p>
     * Every cell first and the selection frame last, so the frame overlaps its neighbours rather than being clipped
     * by whichever cell happens to be drawn after it.
     */
    private static void drawPlates(MenuNode menu, int centerX, int centerY, int slotCount, int inner, int outer,
        int hoveredSlot, boolean grows, WheelAnimator animator) {
        SlotPlate plate = WheelConfig.slotPlate;
        if (plate == null || plate == SlotPlate.NONE) {
            return;
        }

        for (int slot = 0; slot < slotCount; slot++) {
            if (menu.childAt(slot) == null || !arrived(animator, slot, slotCount)) {
                continue;
            }
            double iconRadius = iconRadius(inner, outer, slot, slotCount, grows, animator);
            int x = iconLeft(centerX, slot, slotCount, iconRadius);
            int y = iconTop(centerY, slot, slotCount, iconRadius);
            drawTexture(INVENTORY, x - 1, y - 1, 7, 141, 18, 18);
        }

        if (plate == SlotPlate.SELECTED) {
            for (int slot = 0; slot < slotCount; slot++) {
                if (menu.childAt(slot) == null || !arrived(animator, slot, slotCount)) {
                    continue;
                }
                double iconRadius = iconRadius(inner, outer, slot, slotCount, grows, animator);
                drawSelection(
                    iconLeft(centerX, slot, slotCount, iconRadius),
                    iconTop(centerY, slot, slotCount, iconRadius));
            }
            return;
        }

        if (plate != SlotPlate.HOTBAR || hoveredSlot < 0
            || menu.childAt(hoveredSlot) == null
            || !arrived(animator, hoveredSlot, slotCount)) {
            return;
        }
        double hoveredRadius = iconRadius(inner, outer, hoveredSlot, slotCount, grows, animator);
        int x = iconLeft(centerX, hoveredSlot, slotCount, hoveredRadius);
        int y = iconTop(centerY, hoveredSlot, slotCount, hoveredRadius);
        drawSelection(x, y);
    }

    /**
     * The hotbar's selection frame around a 16x16 icon.
     *
     * <p>
     * Twenty-four square. Vanilla draws this same sprite two pixels short - the hotbar sits at the bottom of the
     * screen, where the missing edge is off it - and copying that number cut the frame's bottom border off in the
     * middle of the ring.
     */
    private static void drawSelection(int x, int y) {
        drawTexture(WIDGETS, x - 4, y - 4, 0, 22, 24, 24);
    }

    /**
     * Draws a piece of a 256x256 vanilla sheet.
     *
     * <p>
     * With the coordinates worked out here rather than through {@code Gui.drawTexturedModalRect}: that one is an
     * instance method of a screen, and this renderer is not one.
     */
    private static void drawTexture(ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        float scale = 1.0F / 256.0F;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0, u * scale, (v + height) * scale);
        tessellator.addVertexWithUV(x + width, y + height, 0.0, (u + width) * scale, (v + height) * scale);
        tessellator.addVertexWithUV(x + width, y, 0.0, (u + width) * scale, v * scale);
        tessellator.addVertexWithUV(x, y, 0.0, u * scale, v * scale);
        tessellator.draw();

        GL11.glDisable(GL11.GL_BLEND);
    }

    private static int iconLeft(int centerX, int slot, int slotCount, double iconRadius) {
        double angle = RadialGeometry.slotCenterAngle(slot, slotCount, 0.0);
        return (int) Math.round(centerX + RadialGeometry.offsetX(angle, iconRadius)) - IconRenderer.ICON_SIZE / 2;
    }

    private static int iconTop(int centerY, int slot, int slotCount, double iconRadius) {
        double angle = RadialGeometry.slotCenterAngle(slot, slotCount, 0.0);
        return (int) Math.round(centerY + RadialGeometry.offsetY(angle, iconRadius)) - IconRenderer.ICON_SIZE / 2;
    }

    /**
     * Where a sector's icon sits, following its sector out and back.
     *
     * <p>
     * Midway between the two radii, which is where it has always been - it only moves now because the radii do.
     */
    private static double iconRadius(int inner, int outer, int slot, int slotCount, boolean grows,
        WheelAnimator animator) {
        float reveal = animator.reveal(slot, slotCount);
        return (grows ? (inner + outer) / 2.0 * reveal : (inner + outer) / 2.0) + animator.push(slot);
    }

    /**
     * Whether a sector is far enough along to carry its icon.
     *
     * <p>
     * An icon cannot be faded - an item is drawn by vanilla's own renderer, which sets its own colour - so it waits
     * instead. Drawn from the first frame they would all be piled in the middle of the wheel while it grows.
     */
    private static boolean arrived(WheelAnimator animator, int slot, int slotCount) {
        return animator.reveal(slot, slotCount) > 0.5f;
    }

    private static void drawIcons(MenuNode menu, int centerX, int centerY, int slotCount, int inner, int outer,
        boolean grows, WheelAnimator animator) {
        for (int slot = 0; slot < slotCount; slot++) {
            MenuNode child = menu.childAt(slot);
            if (child == null || child.icon == null || !arrived(animator, slot, slotCount)) {
                continue;
            }
            double iconRadius = iconRadius(inner, outer, slot, slotCount, grows, animator);
            IconRenderer.draw(
                child.icon,
                iconLeft(centerX, slot, slotCount, iconRadius),
                iconTop(centerY, slot, slotCount, iconRadius));
        }
    }

    /**
     * The hovered entry's name, wrapped to the hole in the middle.
     *
     * <p>
     * Worked out separately from the drawing because the edit hint has to know how much room the name took: the two
     * share the middle of the wheel, and with the selection driven by the scroll wheel they are both on screen at
     * once - the cursor sits in the dead zone while an entry is selected.
     */
    private static List<String> labelLines(MenuNode menu, int hoveredSlot) {
        MenuNode hovered = menu.childAt(hoveredSlot);
        String text = hovered == null ? null : hovered.title;
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        // Measured across the hole, less a little air. Clamped, because the inner radius goes down to eight pixels
        // and wrapping every word onto its own line would be worse than overflowing.
        int wrapWidth = Math.max(MIN_LABEL_WIDTH, WheelConfig.effectiveInnerRadius() * 2 - 8);
        return wrapWords(font, text, wrapWidth);
    }

    /**
     * Only the hovered entry's name. The profile and the path live in the header, out of the way of the ring.
     *
     * <p>
     * Wrapped to the hole in the middle rather than drawn as one line: a two-word name written across the ring
     * covers the very sectors the player is choosing between. However many lines that takes - a name is something
     * the player wrote, and cutting the end off it to save a line of pixels answers a question nobody asked.
     */
    private static void drawLabel(MenuNode menu, int centerX, int centerY, int hoveredSlot) {
        MenuNode hovered = menu.childAt(hoveredSlot);
        String text = hovered == null ? null : hovered.title;
        if (text == null || text.isEmpty()) {
            return;
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        List<String> lines = labelLines(menu, hoveredSlot);
        int top = centerY - lines.size() * font.FONT_HEIGHT / 2;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            font.drawStringWithShadow(
                line,
                centerX - font.getStringWidth(line) / 2,
                top + i * font.FONT_HEIGHT,
                0xFFFFFFFF);
        }
    }

    /**
     * Breaks a name into lines at the spaces, and nowhere else.
     *
     * <p>
     * Vanilla's own wrapping breaks mid-word once a word is wider than the space allowed, which turned an entry
     * called MORKOVKA_17 into "MORKOVKA_" and "17" - two fragments that have to be read back together. A single long
     * word is left whole and allowed to hang over the ring instead: the overhang lasts as long as the cursor is on
     * that sector, and it is still the word the player wrote.
     */
    private static List<String> wrapWords(FontRenderer font, String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (line.length() == 0) {
                line.append(word);
                continue;
            }
            if (font.getStringWidth(line + " " + word) <= width) {
                line.append(' ')
                    .append(word);
                continue;
            }
            lines.add(line.toString());
            line.setLength(0);
            line.append(word);
        }

        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
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
    public static void drawCenterHint(MenuNode menu, int centerX, int centerY, int hoveredSlot) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String text = I18n.format("radialmenu.wheel.menuSettings");

        // Under the name rather than through it. They only collide when the cursor is in the dead zone while
        // something else is selected, which is every moment of editing a wheel driven by the scroll wheel.
        int lines = labelLines(menu, hoveredSlot).size();
        int y = lines == 0 ? centerY - font.FONT_HEIGHT / 2 : centerY + lines * font.FONT_HEIGHT / 2 + 1;

        font.drawStringWithShadow(text, centerX - font.getStringWidth(text) / 2, y, 0xFFFFAA00);
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
