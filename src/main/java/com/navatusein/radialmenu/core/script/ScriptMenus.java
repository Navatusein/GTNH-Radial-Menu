package com.navatusein.radialmenu.core.script;

import org.luaj.vm2.LuaValue;

import com.navatusein.radialmenu.core.Colors;
import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionTypes;
import com.navatusein.radialmenu.core.model.AccentCoefficients;
import com.navatusein.radialmenu.core.model.IconSpec;
import com.navatusein.radialmenu.core.model.MenuNode;
import com.navatusein.radialmenu.core.model.MenuStyle;
import com.navatusein.radialmenu.core.model.SlotLayout;
import com.navatusein.radialmenu.core.model.WheelColors;

/**
 * Turns the list a script passed to {@code menu.open} into a wheel.
 *
 * <p>
 * The result is an ordinary {@link MenuNode}, which is the point: a script-built wheel is drawn by the same renderer,
 * navigated by the same screen and resolved down the same road as one that came out of a profile. The only thing
 * peculiar to it is what its entries do - a {@link ActionTypes#SCRIPT_RESUME} carrying the run's token and the entry's
 * position, so choosing one hands the answer back to the script that asked.
 *
 * <p>
 * Everything here is defensive on purpose. The list comes from a script, the wheel is redrawn every frame, and a
 * malformed entry must cost that entry its picture rather than the menu its existence.
 */
final class ScriptMenus {

    private ScriptMenus() {}

    static MenuNode build(LuaValue items, LuaValue opts, String token, ScriptContext context, ScriptLimits limits) {
        LuaValue options = opts == null || !opts.istable() ? LuaValue.tableOf() : opts;

        int count = Math.min(items.length(), limits.maxMenuItems);
        boolean keepOpen = options.get("keepOpen")
            .toboolean();

        MenuNode menu = MenuNode.category(blankToNull(options.get("title")), null, layoutOf(options, count));
        menu.style = styleOf(options, context);

        for (int i = 1; i <= count; i++) {
            LuaValue item = items.get(i);
            MenuNode leaf = MenuNode.leaf(blankToNull(item.get("label")), iconOf(item), resumeAction(token, i));
            leaf.keepOpen = keepOpen;
            menu.children.add(leaf);
        }

        return menu;
    }

    private static ActionSpec resumeAction(String token, int choice) {
        return new ActionSpec(ActionTypes.SCRIPT_RESUME).set(ActionTypes.PARAM_TOKEN, token)
            .set(ActionTypes.PARAM_CHOICE, Integer.toString(choice));
    }

    /**
     * {@code slots} is a minimum, not a cap.
     *
     * <p>
     * A fixed wheel exists so an entry keeps its angle, which eight sectors holding three entries still does. Eleven
     * entries on an eight-slot wheel is the other case, and there the only alternatives are to stretch the ring or to
     * drop three things the script went to the trouble of parsing.
     */
    private static SlotLayout layoutOf(LuaValue options, int count) {
        int slots = options.get("slots")
            .optint(0);
        if (slots <= 0) {
            return SlotLayout.dynamic();
        }
        return SlotLayout.fixed(SlotLayout.clampSlots(Math.max(slots, count)));
    }

    /**
     * The colours, with an accent expanded first and the named ones laid over it.
     *
     * <p>
     * An accent is written out rather than remembered, exactly as the editor's accent button writes it out: the menu
     * then says outright what it is drawn with, and "accent or explicit colour, which wins" is not a question anyone
     * has
     * to answer. Anything left unset stays null and inherits - profile, then mod config.
     */
    private static MenuStyle styleOf(LuaValue options, ScriptContext context) {
        String ring = optString(options, "ring");
        String highlight = optString(options, "highlight");
        String border = optString(options, "border");
        String highlightBorder = optString(options, "highlightBorder");
        String background = optString(options, "background");

        String accent = optString(options, "accent");
        AccentCoefficients coefficients = context == null ? null : context.accentCoefficients();
        if (accent != null && coefficients != null) {
            // The icon tint is never derived from an accent - an icon that changes hue with the ring stops reading as
            // itself - so it is carried through untouched and left to inherit.
            WheelColors derived = new WheelColors(0, 0, 0, 0, 0, 0)
                .fromAccent(Colors.parseArgb(accent, 0xFFFFFF) & 0x00FFFFFF, coefficients);
            ring = ring == null ? Colors.toHex8(derived.ring) : ring;
            highlight = highlight == null ? Colors.toHex8(derived.highlight) : highlight;
            border = border == null ? Colors.toHex8(derived.border) : border;
            highlightBorder = highlightBorder == null ? Colors.toHex8(derived.highlightBorder) : highlightBorder;
            background = background == null ? Colors.toHex8(derived.background) : background;
        }

        return MenuStyle.of(ring, highlight, optString(options, "icon"), border, highlightBorder, background);
    }

    /**
     * An item's icon, as a string or as a table spelling the same thing out.
     *
     * <p>
     * The item's own {@code color} fills in a tint the icon did not name itself, which is what makes
     * {@code { icon = "sprite:phosphor:house", color = "#7FD4FF" }} read the way it looks like it should.
     */
    private static IconSpec iconOf(LuaValue item) {
        LuaValue value = item.get("icon");
        String color = optString(item, "color");

        IconSpec icon = null;
        if (value.isstring()) {
            icon = IconSpec.parse(value.tojstring());
        } else if (value.istable()) {
            icon = fromTable(value);
        }

        if (icon != null && icon.color == null
            && (icon.kind == IconSpec.Kind.SPRITE || icon.kind == IconSpec.Kind.FILE)) {
            icon.color = color;
        }
        return icon;
    }

    private static IconSpec fromTable(LuaValue value) {
        String id = optString(value, "id");
        if (id == null) {
            return null;
        }
        String kind = optString(value, "kind");
        String color = optString(value, "color");
        int meta = value.get("meta")
            .optint(0);

        if ("sprite".equalsIgnoreCase(kind)) {
            return IconSpec.sprite(id, color);
        }
        if ("file".equalsIgnoreCase(kind)) {
            IconSpec icon = IconSpec.file(id);
            icon.color = color;
            return icon;
        }
        if ("effect".equalsIgnoreCase(kind)) {
            return IconSpec.effect(id);
        }
        // An unknown kind is read as an item, which is also what a missing one means. A table naming a kind nobody
        // recognises is a typo, and an item icon that draws nothing is a better answer than no icon at all.
        return IconSpec.item(id, meta);
    }

    private static String optString(LuaValue table, String key) {
        return blankToNull(table.get(key));
    }

    private static String blankToNull(LuaValue value) {
        if (value == null || !value.isstring()) {
            return null;
        }
        String text = value.tojstring()
            .trim();
        return text.isEmpty() ? null : text;
    }
}
