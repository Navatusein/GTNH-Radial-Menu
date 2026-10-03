package com.navatusein.radialmenu.config;

import static com.navatusein.radialmenu.RadialMenuMod.MODID;

import com.gtnewhorizon.gtnhlib.config.Config;

/** What shape the wheel is: how big, how far apart, and which of its lines are drawn. */
@Config(modid = MODID, configSubDirectory = "RadialMenu", filename = "general", category = "wheel")
@Config.LangKey("radialmenu.config.wheel")
public class WheelConfig {

    @Config.Comment("Outer radius of the ring, in GUI pixels.")
    @Config.DefaultInt(80)
    @Config.RangeInt(min = 32, max = 240)
    @Config.Order(1)
    public static int outerRadius;

    @Config.Comment("Inner radius of the ring. Also the dead zone: pointing inside it selects nothing.")
    @Config.DefaultInt(32)
    @Config.RangeInt(min = 8, max = 200)
    @Config.Order(2)
    public static int innerRadius;

    @Config.Comment("Distance between sectors, in GUI pixels. The gap is only drawn - which sector a click lands in "
        + "is still worked out from the full sector, so aiming at a gap picks the sector it belongs to.")
    @Config.DefaultFloat(0f)
    @Config.RangeFloat(min = 0f, max = 12f)
    @Config.Order(3)
    public static float sectorGap;

    @Config.Comment("Distance between a submenu opened inline and the ring it unfolded from, in GUI pixels. Every "
        + "ring is as thick as the first, so this is the only thing between them.")
    @Config.DefaultInt(2)
    @Config.RangeInt(min = 0, max = 32)
    @Config.Order(6)
    public static int inlineRingSpacing;

    @Config.Comment("Thickness of the wheel's lines, in GUI pixels.")
    @Config.DefaultInt(1)
    @Config.RangeInt(min = 1, max = 6)
    @Config.Order(4)
    public static int borderWidth;

    @Config.Comment("Draw the wheel's outline: the lines between sectors and along the ring's inner and outer "
        + "edges. One switch, because they are one shape - a ring edged but undivided, or divided but unedged, is "
        + "a wheel half drawn.")
    @Config.DefaultBoolean(true)
    @Config.Order(5)
    public static boolean drawOutline;

    @Config.Comment("Draw an outline around the sector under the cursor. Independent of the other two, so a wheel "
        + "with no lines at all can still say which entry it is on.")
    @Config.DefaultBoolean(true)
    @Config.Order(7)
    public static boolean drawHighlightOutline;

    @Config.Comment("Soften the wheel's outline with a one pixel fade. Costs a handful of extra polygons and works "
        + "on every driver, unlike the OpenGL polygon smoothing it replaces.")
    @Config.DefaultBoolean(false)
    @Config.Order(8)
    public static boolean smoothEdges;

    @Config.Comment("Size of a slot's icon, in GUI pixels. The plate behind it follows, so the two stay the "
        + "proportions vanilla drew them at.")
    @Config.DefaultInt(16)
    @Config.RangeInt(min = 8, max = 48)
    @Config.Order(9)
    public static int iconSize;

    @Config.Comment("What to draw behind each icon: NONE, SLOT for an inventory cell, HOTBAR for cells with the "
        + "hotbar's selection frame around the entry under the cursor, or SELECTED for that frame around every one.")
    @Config.DefaultEnum("NONE")
    @Config.Order(10)
    public static SlotPlate slotPlate;

    @Config.Comment("Wash the screen behind the wheel with a colour, to make it easier to read against a busy world.")
    @Config.DefaultBoolean(false)
    @Config.Order(11)
    public static boolean dimBackground;

    /** Guards against a hand-edited config where the hole is bigger than the ring. */
    public static int effectiveInnerRadius() {
        return Math.min(innerRadius, outerRadius - 8);
    }

    /**
     * How big an icon is drawn on the wheel.
     *
     * <p>
     * Only on the wheel. The editors draw icons into cells of their own, which are measured in {@code Ui} and have
     * nothing to do with how large the player likes them in the world - a thirty-two pixel icon in a twenty pixel
     * grid cell would be the setting reaching somewhere it was never about.
     */
    public static int effectiveIconSize() {
        return Math.max(4, iconSize);
    }
}
