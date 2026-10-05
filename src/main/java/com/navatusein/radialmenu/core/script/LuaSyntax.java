package com.navatusein.radialmenu.core.script;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Splits Lua source into coloured spans for the editor.
 *
 * <p>
 * In {@code core} and not in the editor screen, for the usual reason: what counts as a string is a question with right
 * and wrong answers, and answering it here means a test can ask. The screen is left with nothing but
 * "draw this range in this colour".
 *
 * <p>
 * Two things decide the shape of this. The whole text is tokenized at once rather than line by line, because
 * {@code --[[ ]]} and {@code [[ ]]} carry across lines and a line read on its own cannot know it is inside one. And the
 * tokens of a line <b>tile it completely</b> - every character belongs to exactly one token, whitespace included - so
 * the renderer can walk them in order and never has to work out what it has not drawn yet.
 *
 * <p>
 * This is a highlighter, not a parser: it never reports an error, and the worst a malformed file costs is a stretch of
 * the wrong colour.
 */
public final class LuaSyntax {

    public enum Kind {
        /** Identifiers, whitespace, anything unremarkable. */
        PLAIN,
        /** Lua's reserved words that shape the code: {@code if}, {@code local}, {@code function}. */
        KEYWORD,
        /**
         * {@code local}, on its own.
         *
         * <p>
         * It is the one reserved word that says nothing about what the code does and everything about where a name
         * lives, and it opens more lines of a script than any other - in the keywords' colour it turns the left edge
         * of a script into one solid stripe.
         */
        DECLARATION,
        /**
         * Words that stand for a value rather than shape anything: {@code nil}, {@code true}, {@code false}.
         *
         * <p>
         * They are reserved words and were coloured as such, which put {@code return nil} in one colour from end to
         * end - the statement and the thing it hands back, indistinguishable.
         */
        CONSTANT,
        /** {@code self}: not reserved at all, but a name nobody chooses and everybody reads the same way. */
        SELF,
        /** The tables a script is handed - {@code chat}, {@code player}, {@code string} - where they are not called. */
        API,
        /** A name with a bracket after it: something being called, or defined, whoever it belongs to. */
        FUNCTION,
        /** A known member read rather than called: {@code player.name}, {@code stack.icon}. */
        FIELD,
        STRING,
        NUMBER,
        COMMENT,
        OPERATOR
    }

    /** One coloured run inside a line: {@code [start, end)} in characters. */
    public static final class Token {

        public final int start;
        public final int end;
        public final Kind kind;

        Token(int start, int end, Kind kind) {
            this.start = start;
            this.end = end;
            this.kind = kind;
        }

        public int length() {
            return end - start;
        }

        @Override
        public String toString() {
            return kind + "[" + start + "," + end + ")";
        }
    }

    private static final Set<String> KEYWORDS = new HashSet<>(
        Arrays.asList(
            "and",
            "break",
            "do",
            "else",
            "elseif",
            "end",
            "for",
            "function",
            "goto",
            "if",
            "in",
            "not",
            "or",
            "repeat",
            "return",
            "then",
            "until",
            "while"));

    private static final Set<String> CONSTANTS = new HashSet<>(Arrays.asList("nil", "true", "false"));

    /**
     * Lua's own libraries, which are globals a script is handed but not ones this mod wrote.
     *
     * <p>
     * The difference matters after a dot. {@link #MEMBERS} lists what the mod's tables carry, so a name missing from it
     * is a misspelling and is left plain to say so; it does not list {@code string.format} and its forty relatives,
     * and holding those to the same list would paint every correct call as a typo.
     */
    private static final Set<String> STANDARD = new HashSet<>(Arrays.asList("string", "table", "math"));

    /** What a script is handed: see {@code ScriptApi} and the prelude. */
    private static final Set<String> GLOBALS = new HashSet<>(
        Arrays.asList(
            "chat",
            "menu",
            "player",
            "chunk",
            "world",
            "inventory",
            "store",
            "prompt",
            "action",
            "notify",
            "log",
            "sleep",
            "print",
            "string",
            "table",
            "math"));

    /**
     * Members worth colouring after a dot.
     *
     * <p>
     * Only after a dot, which is the point: {@code chat.send} is coloured and {@code chat.sned} is left plain, bracket
     * or no bracket, so the editor says what a careful read of the docs would have.
     */
    private static final Set<String> MEMBERS = new HashSet<>(
        Arrays.asList(
            "send",
            "await",
            "awaitAll",
            "line",
            "open",
            "close",
            "update",
            "run",
            "maxEntries",
            "name",
            "dim",
            "x",
            "y",
            "z",
            "chunkX",
            "chunkZ",
            "xInChunk",
            "zInChunk",
            "yaw",
            "pitch",
            "facing",
            "of",
            "offset",
            "lookingAt",
            "time",
            "day",
            "isDay",
            "items",
            "count",
            "has",
            "get",
            "set",
            "health",
            "food",
            "air",
            "held",
            "meta",
            "slot",
            "kind",
            "toWorld",
            "size",
            "key",
            "label",
            "icon",
            "color",
            "onPick"));

    private static final String OPERATOR_CHARS = "+-*/%^#=~<>(){}[];:,.";

    private LuaSyntax() {}

    /** Whether a name is one of the globals a script is handed. */
    public static boolean isGlobal(String name) {
        return GLOBALS.contains(name);
    }

    /** Whether a name is one of the members those globals carry. */
    public static boolean isMember(String name) {
        return MEMBERS.contains(name);
    }

    /** Convenience for a single string; the editor already holds its text as lines. */
    public static List<List<Token>> tokenize(String text) {
        return tokenize(Arrays.asList((text == null ? "" : text).split("\r?\n", -1)));
    }

    public static List<List<Token>> tokenize(List<String> lines) {
        List<List<Token>> all = new ArrayList<>();
        if (lines == null) {
            return all;
        }

        // -1 when not inside a long bracket, otherwise the number of '=' in the opener that has to be matched.
        int longLevel = -1;
        boolean longIsComment = false;

        for (String raw : lines) {
            String line = raw == null ? "" : raw;
            List<Token> tokens = new ArrayList<>();
            int index = 0;
            int plainFrom = -1;

            while (index < line.length()) {
                if (longLevel >= 0) {
                    index = flushPlain(tokens, plainFrom, index);
                    plainFrom = -1;
                    int close = indexOfLongClose(line, index, longLevel);
                    int end = close < 0 ? line.length() : close + longLevel + 2;
                    tokens.add(new Token(index, end, longIsComment ? Kind.COMMENT : Kind.STRING));
                    index = end;
                    if (close >= 0) {
                        longLevel = -1;
                    }
                    continue;
                }

                char c = line.charAt(index);

                // A line comment, or the opener of a long one.
                if (c == '-' && index + 1 < line.length() && line.charAt(index + 1) == '-') {
                    plainFrom = flushPlainAt(tokens, plainFrom, index);
                    int level = longBracketLevel(line, index + 2);
                    if (level >= 0) {
                        int openerEnd = index + 2 + level + 2;
                        tokens.add(new Token(index, openerEnd, Kind.COMMENT));
                        longLevel = level;
                        longIsComment = true;
                        index = openerEnd;
                    } else {
                        tokens.add(new Token(index, line.length(), Kind.COMMENT));
                        index = line.length();
                    }
                    continue;
                }

                if (c == '[') {
                    int level = longBracketLevel(line, index);
                    if (level >= 0) {
                        plainFrom = flushPlainAt(tokens, plainFrom, index);
                        int openerEnd = index + level + 2;
                        tokens.add(new Token(index, openerEnd, Kind.STRING));
                        longLevel = level;
                        longIsComment = false;
                        index = openerEnd;
                        continue;
                    }
                }

                if (c == '"' || c == '\'') {
                    plainFrom = flushPlainAt(tokens, plainFrom, index);
                    int end = endOfShortString(line, index);
                    tokens.add(new Token(index, end, Kind.STRING));
                    index = end;
                    continue;
                }

                if (isDigit(c) || (c == '.' && index + 1 < line.length() && isDigit(line.charAt(index + 1)))) {
                    plainFrom = flushPlainAt(tokens, plainFrom, index);
                    int end = endOfNumber(line, index);
                    tokens.add(new Token(index, end, Kind.NUMBER));
                    index = end;
                    continue;
                }

                if (isWordStart(c)) {
                    plainFrom = flushPlainAt(tokens, plainFrom, index);
                    int end = index;
                    while (end < line.length() && isWordPart(line.charAt(end))) {
                        end++;
                    }
                    tokens.add(
                        new Token(
                            index,
                            end,
                            wordKind(line.substring(index, end), ownerBeforeDot(line, index), isCalled(line, end))));
                    index = end;
                    continue;
                }

                if (OPERATOR_CHARS.indexOf(c) >= 0) {
                    plainFrom = flushPlainAt(tokens, plainFrom, index);
                    int end = index;
                    while (end < line.length() && OPERATOR_CHARS.indexOf(line.charAt(end)) >= 0) {
                        end++;
                    }
                    tokens.add(new Token(index, end, Kind.OPERATOR));
                    index = end;
                    continue;
                }

                // Whitespace and anything unclassified: gathered into one plain run rather than a token each.
                if (plainFrom < 0) {
                    plainFrom = index;
                }
                index++;
            }

            flushPlain(tokens, plainFrom, line.length());
            all.add(Collections.unmodifiableList(tokens));
        }
        return all;
    }

    /**
     * The name a word is a member of: what stands before the dot in front of it. Null when there is no dot, and empty
     * when there is one with something other than a name before it - a closing bracket, say.
     *
     * <p>
     * Exactly one dot: two are a concatenation, and the word after {@code ..} is a name of its own, not a member of
     * anything.
     */
    private static String ownerBeforeDot(String line, int wordStart) {
        int index = skipSpacesBack(line, wordStart - 1);
        if (index < 0 || line.charAt(index) != '.' || (index > 0 && line.charAt(index - 1) == '.')) {
            return null;
        }
        int end = skipSpacesBack(line, index - 1) + 1;
        int start = end;
        while (start > 0 && isWordPart(line.charAt(start - 1))) {
            start--;
        }
        return line.substring(start, end);
    }

    private static int skipSpacesBack(String line, int from) {
        int index = from;
        while (index >= 0 && (line.charAt(index) == ' ' || line.charAt(index) == '\t')) {
            index--;
        }
        return index;
    }

    /** Whether a bracket opens after the word, which is what a call and a definition both look like. */
    private static boolean isCalled(String line, int wordEnd) {
        int index = wordEnd;
        while (index < line.length() && (line.charAt(index) == ' ' || line.charAt(index) == '\t')) {
            index++;
        }
        return index < line.length() && line.charAt(index) == '(';
    }

    /**
     * What a word is, from the word and the two things next to it.
     *
     * <p>
     * A member of one of the mod's own tables is the strict case: it has to be one the table really carries, and a
     * name it does not carry stays plain even with a bracket after it - that bracket is on a call that will fail.
     * Everywhere else a bracket is enough to make a function, because a script's own functions and the methods of its
     * values are nothing this could have a list of.
     */
    private static Kind wordKind(String word, String owner, boolean called) {
        if (KEYWORDS.contains(word)) {
            return Kind.KEYWORD;
        }
        if ("local".equals(word)) {
            return Kind.DECLARATION;
        }
        if (CONSTANTS.contains(word)) {
            return Kind.CONSTANT;
        }
        if ("self".equals(word)) {
            return Kind.SELF;
        }
        if (owner != null) {
            boolean known = MEMBERS.contains(word);
            if (GLOBALS.contains(owner) && !STANDARD.contains(owner) && !known) {
                return Kind.PLAIN;
            }
            if (called) {
                return Kind.FUNCTION;
            }
            return known ? Kind.FIELD : Kind.PLAIN;
        }
        if (called) {
            return Kind.FUNCTION;
        }
        return GLOBALS.contains(word) ? Kind.API : Kind.PLAIN;
    }

    /** The number of {@code =} in a long bracket opening at this position, or -1 if one does not open here. */
    private static int longBracketLevel(String line, int at) {
        if (at >= line.length() || line.charAt(at) != '[') {
            return -1;
        }
        int level = 0;
        int index = at + 1;
        while (index < line.length() && line.charAt(index) == '=') {
            level++;
            index++;
        }
        return index < line.length() && line.charAt(index) == '[' ? level : -1;
    }

    private static int indexOfLongClose(String line, int from, int level) {
        StringBuilder closer = new StringBuilder("]");
        for (int i = 0; i < level; i++) {
            closer.append('=');
        }
        closer.append(']');
        return line.indexOf(closer.toString(), from);
    }

    /**
     * Where a quoted string ends, counting backslash escapes.
     *
     * <p>
     * An unterminated one stops at the end of the line rather than running on: Lua would refuse the file, but the
     * editor
     * has to keep drawing, and a missing quote that recolours the rest of the script is how a one-character typo looks
     * like a disaster.
     */
    private static int endOfShortString(String line, int start) {
        char quote = line.charAt(start);
        int index = start + 1;
        while (index < line.length()) {
            char c = line.charAt(index);
            if (c == '\\') {
                index += 2;
                continue;
            }
            index++;
            if (c == quote) {
                return index;
            }
        }
        return line.length();
    }

    private static int endOfNumber(String line, int start) {
        int index = start;
        boolean hex = line.startsWith("0x", start) || line.startsWith("0X", start);
        if (hex) {
            index += 2;
        }
        while (index < line.length()) {
            char c = line.charAt(index);
            if (isDigit(c) || c == '.' || (hex && isHexDigit(c))) {
                index++;
            } else if (!hex && (c == 'e' || c == 'E')) {
                index++;
                if (index < line.length() && (line.charAt(index) == '+' || line.charAt(index) == '-')) {
                    index++;
                }
            } else {
                break;
            }
        }
        return index;
    }

    private static int flushPlainAt(List<Token> tokens, int plainFrom, int index) {
        flushPlain(tokens, plainFrom, index);
        return -1;
    }

    private static int flushPlain(List<Token> tokens, int plainFrom, int until) {
        if (plainFrom >= 0 && until > plainFrom) {
            tokens.add(new Token(plainFrom, until, Kind.PLAIN));
        }
        return until;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHexDigit(char c) {
        return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isWordStart(char c) {
        return c == '_' || Character.isLetter(c);
    }

    private static boolean isWordPart(char c) {
        return c == '_' || Character.isLetterOrDigit(c);
    }
}
