package com.navatusein.radialmenu.core.script;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The calls a script can make, as text the editor can write for the player.
 *
 * <p>
 * Here rather than in the editor screen because of what it is: knowledge about the script API, which already lives in
 * this package - {@link ScriptApi} defines the functions, {@link LuaSyntax} colours them, and this writes them out.
 * Three lists of the same names in three places would drift, and the one a player reads would be the one that drifted.
 * A test holds them together.
 *
 * <p>
 * Each snippet carries its own punctuation, because a list that only reminds you a function exists is worth much less
 * than one that writes the call. {@link #CARET} marks where the argument goes; the editor strips it and leaves the
 * caret there.
 */
public final class ScriptSnippets {

    /**
     * Where the caret belongs once the snippet is in.
     *
     * <p>
     * A control character rather than a visible placeholder like {@code $0}: it cannot be typed, so it cannot appear in
     * a snippet by accident, and anything that forgets to strip it is obvious rather than subtly wrong.
     */
    public static final char CARET = (char) 1;

    /** One thing the editor can write into a script. */
    public static final class Snippet {

        /** What the list shows: the call's name, with no punctuation. */
        public final String label;

        /** What is written in, with {@link #CARET} where the caret should end up. */
        public final String text;

        Snippet(String label, String text) {
            this.label = label;
            this.text = text;
        }
    }

    /**
     * House style, held by {@code ScriptSnippetsTest} rather than by good intentions:
     * <ul>
     * <li><b>Double quotes.</b> A script writes chat lines full of apostrophes, and a mixture of quote styles in the
     * same file is how an unterminated string gets written.</li>
     * <li><b>Calls are parenthesised</b> - {@code action.run({...})} rather than Lua's bare-table shorthand, which is
     * correct but reads as a syntax error to anyone who has not met it.</li>
     * <li><b>No space inside braces</b>: {@code {key = "x"}}.</li>
     * <li><b>One caret</b>, where the argument goes, and nothing after it that has to be deleted.</li>
     * </ul>
     * Every entry here is compiled by the test, so a snippet that does not parse cannot reach a player.
     */
    private static final List<Snippet> ALL = Collections.unmodifiableList(
        Arrays.asList(
            new Snippet("chat.send", "chat.send(\"" + CARET + "\")"),
            new Snippet("chat.await", "chat.await(\"^" + CARET + "(.+)$\")"),
            new Snippet("chat.awaitAll", "chat.awaitAll(\"^" + CARET + "(.+)$\", 40)"),
            new Snippet("menu.open", "menu.open({" + CARET + "})"),
            new Snippet("menu.open opts", "menu.open(items, {title = \"" + CARET + "\", slots = 8})"),
            new Snippet("menu entry", "{key = \"" + CARET + "\", label = \"\", icon = \"minecraft:stone\"}"),
            new Snippet("onPick", "onPick = function(key, item)\n  " + CARET + "\nend"),
            new Snippet("menu.update", "menu.update(items)"),
            new Snippet("menu.close", "menu.close()"),
            new Snippet("player.name", "player.name"),
            new Snippet("notify", "notify(\"" + CARET + "\")"),
            new Snippet("log", "log(\"" + CARET + "\")"),
            new Snippet("sleep", "sleep(20)"),
            new Snippet("action.run", "action.run({type = \"keybind\", params = {binding = \"" + CARET + "\"}})"),
            new Snippet("local", "local " + CARET + " = "),
            new Snippet("if", "if " + CARET + " then\n  \nend"),
            new Snippet("for", "for i = 1, 10 do\n  " + CARET + "\nend"),
            new Snippet("while", "while true do\n  " + CARET + "\nend")));

    private ScriptSnippets() {}

    public static List<Snippet> all() {
        return ALL;
    }
}
