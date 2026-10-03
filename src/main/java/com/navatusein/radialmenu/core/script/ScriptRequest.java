package com.navatusein.radialmenu.core.script;

import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.model.MenuNode;

/**
 * What a suspended script is waiting for.
 *
 * <p>
 * A script never touches the game itself: it asks, and the host does it. That is not tidiness but the only safe shape.
 * LuaJ runs a coroutine on a Java thread of its own, so anything the script called directly would run off the client
 * thread - and the client thread is the only one allowed to send chat, open a screen or press a binding. The one thing
 * that crosses the line is this value, read by the host where it is allowed to act on it.
 *
 * <p>
 * Plain data with no Minecraft in it, for the same reason the rest of {@code core} is: the whole of a script's logic
 * can then be driven by a test with no game running.
 */
public final class ScriptRequest {

    public enum Kind {

        /** Send a chat line or slash command. */
        SEND,

        /**
         * Hand over the next chat line the client receives.
         *
         * <p>
         * Deliberately not "wait for a line matching this pattern". The patterns are Lua patterns, and matching them
         * is {@code string.match}'s job - putting that on the host would mean a second implementation of them in Java,
         * drifting from the one the script itself can see. The host answers with a line and how long it waited; the
         * prelude does the matching and asks again.
         */
        AWAIT_LINE,

        /** Open a wheel built from the script's items and wait for a choice. */
        MENU,

        /** Replace the contents of the wheel already on screen. */
        MENU_UPDATE,

        /** Close a wheel that an entry's keepOpen left up. */
        MENU_CLOSE,

        /** Wait a number of ticks. */
        SLEEP,

        /** Run one of the mod's own actions, which is how a script reaches keybindings and profiles. */
        ACTION,

        /** Print a line in the player's own chat, visible to nobody else. */
        NOTIFY,

        /** Write a line to the client log. */
        LOG,

        /** Remember a value under a key, or forget it when the value is null. */
        STORE_SET,

        /**
         * Ask the player to type something, and hand back what they typed.
         *
         * <p>
         * A screen, so it blocks exactly the way a menu does - and like a menu, the answer may be nothing at all,
         * because the player is always allowed to change their mind.
         */
        PROMPT
    }

    public final Kind kind;

    /** {@link Kind#SEND}, {@link Kind#NOTIFY} and {@link Kind#LOG}. */
    public final String text;

    /** {@link Kind#AWAIT_LINE} timeout and {@link Kind#SLEEP} duration, in ticks. */
    public final int ticks;

    /** {@link Kind#MENU} and {@link Kind#MENU_UPDATE}: the wheel, ready to open. */
    public final MenuNode menu;

    /** {@link Kind#ACTION}: what to run. */
    public final ActionSpec action;

    /** {@link Kind#STORE_SET}: the key being written. {@link Kind#PROMPT}: what the box starts out holding. */
    public final String key;

    private ScriptRequest(Kind kind, String text, int ticks, MenuNode menu, ActionSpec action, String key) {
        this.kind = kind;
        this.text = text;
        this.ticks = ticks;
        this.menu = menu;
        this.action = action;
        this.key = key;
    }

    public static ScriptRequest send(String text) {
        return new ScriptRequest(Kind.SEND, text, 0, null, null, null);
    }

    public static ScriptRequest notify(String text) {
        return new ScriptRequest(Kind.NOTIFY, text, 0, null, null, null);
    }

    public static ScriptRequest log(String text) {
        return new ScriptRequest(Kind.LOG, text, 0, null, null, null);
    }

    public static ScriptRequest awaitLine(int timeoutTicks) {
        return new ScriptRequest(Kind.AWAIT_LINE, null, timeoutTicks, null, null, null);
    }

    public static ScriptRequest sleep(int ticks) {
        return new ScriptRequest(Kind.SLEEP, null, ticks, null, null, null);
    }

    public static ScriptRequest menu(MenuNode menu) {
        return new ScriptRequest(Kind.MENU, null, 0, menu, null, null);
    }

    public static ScriptRequest menuUpdate(MenuNode menu) {
        return new ScriptRequest(Kind.MENU_UPDATE, null, 0, menu, null, null);
    }

    public static ScriptRequest menuClose() {
        return new ScriptRequest(Kind.MENU_CLOSE, null, 0, null, null, null);
    }

    public static ScriptRequest action(ActionSpec action) {
        return new ScriptRequest(Kind.ACTION, null, 0, null, action, null);
    }

    /** @param value null to forget the key */
    public static ScriptRequest storeSet(String key, String value) {
        return new ScriptRequest(Kind.STORE_SET, value, 0, null, null, key);
    }

    public static ScriptRequest prompt(String title, String initial) {
        return new ScriptRequest(Kind.PROMPT, title, 0, null, null, initial);
    }

    @Override
    public String toString() {
        switch (kind) {
            case SEND:
            case NOTIFY:
            case LOG:
                return kind + "(" + text + ")";
            case AWAIT_LINE:
            case SLEEP:
                return kind + "(" + ticks + " ticks)";
            case MENU:
            case MENU_UPDATE:
                return kind + "("
                    + menu.childrenOrEmpty()
                        .size()
                    + " entries)";
            case ACTION:
                return kind + "(" + action.type + ")";
            case STORE_SET:
                return kind + "(" + key + " = " + text + ")";
            case PROMPT:
                return kind + "(" + text + ")";
            default:
                return kind.toString();
        }
    }
}
