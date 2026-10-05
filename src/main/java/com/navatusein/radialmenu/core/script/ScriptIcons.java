package com.navatusein.radialmenu.core.script;

import java.util.List;

import com.navatusein.radialmenu.core.model.IconSpec;

/**
 * Writes an icon the way a script spells one - the reverse of what {@code ScriptMenus} reads.
 *
 * <p>
 * The script editor's icon button exists because the names are the part nobody can type from memory: a sprite out of
 * a sheet of a thousand, a potion's unlocalized name, the damage value of lime clay. The picker already knows all of
 * them, so what is left is turning its answer back into source - and that lives here, beside the reader, where a test
 * can hold the two against each other.
 */
public final class ScriptIcons {

    private static final String[] PREFIXES = { "sprite", "file", "effect", "player", "item" };

    private ScriptIcons() {}

    /**
     * The Lua expression for an icon: a string where one is enough, a table where it is not.
     *
     * <p>
     * A tint is the only thing the one-string form has nowhere to put, so a tinted sprite or PNG is the one case
     * written as a table. Everything else gets the short form, which is what the guide's examples use and what a
     * player will recognise when they read the script back.
     */
    public static String spell(IconSpec icon) {
        if (icon == null || icon.id == null
            || icon.id.trim()
                .isEmpty()) {
            return "\"\"";
        }

        boolean tintable = icon.kind == IconSpec.Kind.SPRITE || icon.kind == IconSpec.Kind.FILE;
        if (tintable && icon.color != null
            && !icon.color.trim()
                .isEmpty()) {
            return "{ kind = " + quote(
                icon.kind.name()
                    .toLowerCase())
                + ", id = "
                + quote(icon.id)
                + ", color = "
                + quote(icon.color.trim())
                + " }";
        }
        return quote(oneString(icon));
    }

    private static String oneString(IconSpec icon) {
        switch (icon.kind) {
            case SPRITE:
                return "sprite:" + icon.id;
            case FILE:
                return "file:" + icon.id;
            case EFFECT:
                return "effect:" + icon.id;
            case PLAYER:
                return "player:" + icon.id;
            case ITEM:
            default:
                // A mod is free to call itself "file" or "player", and its items would then read as that kind. The
                // explicit prefix is only written where the bare name would be taken for something else.
                String name = icon.meta == 0 ? icon.id : icon.id + ":" + icon.meta;
                return looksPrefixed(icon.id) ? "item:" + name : name;
        }
    }

    private static boolean looksPrefixed(String id) {
        for (String prefix : PREFIXES) {
            if (id.regionMatches(true, 0, prefix + ":", 0, prefix.length() + 1)) {
                return true;
            }
        }
        return false;
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\")
            .replace("\"", "\\\"") + "\"";
    }

    /**
     * The quoted string the caret is inside, or null when it is not inside one.
     *
     * <p>
     * That string is what a picked icon replaces: the caret sitting in {@code "minecraft:stone"} is the player
     * pointing at the icon they want changed, and writing a second pair of quotes into the middle of the first would
     * leave them a syntax error to tidy up. Strictly inside - a caret resting just after the closing quote is past the
     * string, not in it - except where there is no closing quote yet, since a string still being typed ends wherever
     * the line does.
     *
     * <p>
     * Long brackets are left alone. They run across lines, and nobody writes an icon in one.
     */
    public static LuaSyntax.Token stringAt(List<LuaSyntax.Token> lineTokens, String line, int column) {
        if (lineTokens == null || line == null) {
            return null;
        }
        for (LuaSyntax.Token token : lineTokens) {
            if (token.kind != LuaSyntax.Kind.STRING || token.start >= line.length() || token.end > line.length()) {
                continue;
            }
            char opener = line.charAt(token.start);
            if (opener != '"' && opener != '\'') {
                continue;
            }
            boolean closed = token.length() >= 2 && line.charAt(token.end - 1) == opener;
            if (column > token.start && (column < token.end || (!closed && column == token.end))) {
                return token;
            }
        }
        return null;
    }
}
