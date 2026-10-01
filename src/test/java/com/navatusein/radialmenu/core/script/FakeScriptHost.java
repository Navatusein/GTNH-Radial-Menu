package com.navatusein.radialmenu.core.script;

import static org.junit.Assert.fail;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.model.AccentCoefficients;
import com.navatusein.radialmenu.core.model.MenuNode;

/**
 * Stands in for the client while a script runs.
 *
 * <p>
 * This is what the split between a script and its host buys: the whole of a script's logic - a command sent, a reply
 * parsed, a wheel built, an entry chosen, a second command sent - is exercised here with no Minecraft anywhere near it.
 * Queue up what the server will say and what the player will choose, run the script, and read back what it did.
 */
class FakeScriptHost implements ScriptContext {

    /** Lines the "server" will answer with, in order. */
    final Deque<String> incoming = new ArrayDeque<>();

    /** Entries the "player" will choose, one per menu, counted from 1. 0 dismisses the menu. */
    final Deque<Integer> choices = new ArrayDeque<>();

    final List<String> sent = new ArrayList<>();
    final List<String> notices = new ArrayList<>();
    final List<String> logs = new ArrayList<>();
    final List<ActionSpec> actions = new ArrayList<>();

    /** Every wheel the script opened or updated, in order. */
    final List<MenuNode> menus = new ArrayList<>();

    boolean closedMenu;

    String playerName = "Nortcast";
    int dimension = 0;
    int x = 100;
    int y = 64;
    int z = -200;

    ScriptTask run(String source) {
        return run(source, ScriptLimits.DEFAULT);
    }

    ScriptTask run(String source, ScriptLimits limits) {
        ScriptTask task = ScriptTask.start(source, "test", "token-1", this, limits);
        drive(task);
        return task;
    }

    private void drive(ScriptTask task) {
        // A script that cannot be finished in this many exchanges is a test that has hung, and a hung test says far
        // less than a failed one.
        for (int step = 0; step < 2000; step++) {
            ScriptRequest request = task.pending();
            if (request == null) {
                return;
            }
            perform(task, request);
        }
        fail("the script was still asking for things after 2000 exchanges");
    }

    private void perform(ScriptTask task, ScriptRequest request) {
        switch (request.kind) {
            case SEND:
                sent.add(request.text);
                task.resumeVoid();
                break;
            case NOTIFY:
                notices.add(request.text);
                task.resumeVoid();
                break;
            case LOG:
                logs.add(request.text);
                task.resumeVoid();
                break;
            case SLEEP:
                task.resumeVoid();
                break;
            case ACTION:
                actions.add(request.action);
                task.resumeVoid();
                break;
            case AWAIT_LINE:
                // An empty queue is a server that never answered: the wait runs out rather than blocking a test.
                if (incoming.isEmpty()) {
                    task.resumeLine(null, request.ticks);
                } else {
                    task.resumeLine(incoming.poll(), 1);
                }
                break;
            case MENU:
                menus.add(request.menu);
                task.resumeChoice(choices.isEmpty() ? 0 : choices.poll());
                break;
            case MENU_UPDATE:
                menus.add(request.menu);
                task.resumeVoid();
                break;
            case MENU_CLOSE:
                closedMenu = true;
                task.resumeVoid();
                break;
            default:
                fail("unhandled request kind " + request.kind);
        }
    }

    /** The wheel the script opened first, which is the one most tests are about. */
    MenuNode firstMenu() {
        if (menus.isEmpty()) {
            fail("the script opened no menu");
        }
        return menus.get(0);
    }

    @Override
    public String playerName() {
        return playerName;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public int blockX() {
        return x;
    }

    @Override
    public int blockY() {
        return y;
    }

    @Override
    public int blockZ() {
        return z;
    }

    @Override
    public AccentCoefficients accentCoefficients() {
        return new AccentCoefficients(
            new AccentCoefficients.Tone(160, 40),
            new AccentCoefficients.Tone(220, 100),
            new AccentCoefficients.Tone(90, 130),
            new AccentCoefficients.Tone(230, 160),
            new AccentCoefficients.Tone(128, 20));
    }
}
