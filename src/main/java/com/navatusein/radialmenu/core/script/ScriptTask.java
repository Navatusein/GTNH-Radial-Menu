package com.navatusein.radialmenu.core.script;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * One run of one script.
 *
 * <p>
 * Driven rather than driving: the host asks what the script is waiting for, does it, and resumes with the answer.
 *
 * <pre>
 * ScriptTask task = ScriptTask.start(source, "Homes", token, context, ScriptLimits.DEFAULT);
 * while (task.pending() != null) {
 *     // perform task.pending(), then resume with whatever it produced
 * }
 * </pre>
 *
 * <p>
 * Nothing here blocks on the game, so the loop above is a tick's worth of work at a time on the client and a plain loop
 * in a test. The coroutine itself runs on a Java thread LuaJ owns, but only ever while this one is inside
 * {@link LuaThread#resume}, so the two never run at once - and the script cannot reach the game regardless, since
 * everything it can ask for comes back out as a {@link ScriptRequest}.
 */
public final class ScriptTask {

    /** How often the watchdog is consulted. Small enough to stop a tight loop promptly, large enough to be free. */
    private static final int HOOK_INTERVAL = 500;

    private final String token;
    private final ScriptContext context;
    private final ScriptLimits limits;
    private final Globals globals;

    private LuaThread thread;

    private ScriptRequest pending;
    private boolean finished;
    private String error;

    private int instructionsThisResume;
    private long resumeDeadlineNanos;

    private ScriptTask(String token, ScriptContext context, ScriptLimits limits) {
        this.token = token;
        this.context = context;
        this.limits = limits;
        this.globals = ScriptSandbox.newGlobals();
    }

    /**
     * Compiles a script and runs it up to the first thing it waits for.
     *
     * <p>
     * A script that does not compile comes back {@linkplain #isFinished() finished} with an {@link #error}, the same as
     * one that threw halfway. The caller reports both the same way, because to the player they are the same event: the
     * slot did not work and here is the line to look at.
     *
     * @param token identifies this run to the entries of a menu it opens
     */
    public static ScriptTask start(String source, String name, String token, ScriptContext context,
        ScriptLimits limits) {
        ScriptTask task = new ScriptTask(token, context, limits);
        task.launch(source == null ? "" : source, name == null ? "script" : name);
        return task;
    }

    private void launch(String source, String name) {
        LuaValue chunk;
        try {
            chunk = globals.load(source, name);
        } catch (LuaError e) {
            fail(e);
            return;
        }

        try {
            ScriptApi.install(this, globals);
        } catch (LuaError e) {
            fail(e);
            return;
        }

        thread = new LuaThread(globals, chunk);
        // Written straight onto the coroutine's state rather than through debug.sethook, so there is no moment when a
        // script could see the hook being installed and no Lua handle on it afterwards.
        thread.state.hookfunc = new Watchdog();
        thread.state.hookcount = HOOK_INTERVAL;

        resume(LuaValue.NONE);
    }

    /** What the script is waiting for, or null when it has finished or failed. */
    public ScriptRequest pending() {
        return pending;
    }

    public boolean isFinished() {
        return finished;
    }

    /** The message of whatever stopped the script, or null if it ran to the end. */
    public String error() {
        return error;
    }

    public String token() {
        return token;
    }

    public ScriptContext context() {
        return context;
    }

    public ScriptLimits limits() {
        return limits;
    }

    /** Answers a request that produces nothing: a sent line, a slept tick, a closed menu. */
    public void resumeVoid() {
        resume(LuaValue.NONE);
    }

    /**
     * Answers {@link ScriptRequest.Kind#AWAIT_LINE}.
     *
     * @param line        the chat line received, or null if the wait timed out
     * @param ticksWaited how long it took, so the script can keep its own deadline
     */
    public void resumeLine(String line, int ticksWaited) {
        expect(ScriptRequest.Kind.AWAIT_LINE);
        resume(
            LuaValue.varargsOf(
                line == null ? LuaValue.NIL : LuaValue.valueOf(line),
                LuaValue.valueOf(Math.max(0, ticksWaited))));
    }

    /**
     * Answers {@link ScriptRequest.Kind#MENU}.
     *
     * @param choice which entry was chosen, counted from 1 as Lua counts, or 0 for a menu the player dismissed
     */
    public void resumeChoice(int choice) {
        expect(ScriptRequest.Kind.MENU);
        resume(choice <= 0 ? LuaValue.NIL : LuaValue.valueOf(choice));
    }

    /**
     * Abandons the run - a timeout, a disconnect, the entry triggered again.
     *
     * <p>
     * The coroutine is dropped rather than unwound: it is suspended inside a yield, and there is no way to make it
     * return from there that the script could not catch. LuaJ reaps the parked thread once nothing references it, which
     * is why nothing here holds onto it afterwards.
     */
    public void cancel(String reason) {
        pending = null;
        thread = null;
        finished = true;
        if (error == null) {
            error = reason;
        }
    }

    /** Sets the request the script is now waiting for and suspends it. Called from the script's own thread. */
    Varargs yieldRequest(ScriptRequest request) {
        this.pending = request;
        return globals.yield(LuaValue.NONE);
    }

    private void resume(Varargs args) {
        if (finished || thread == null) {
            return;
        }

        pending = null;
        instructionsThisResume = 0;
        resumeDeadlineNanos = System.nanoTime() + limits.nanosPerResume;

        Varargs result;
        try {
            result = thread.resume(args);
        } catch (LuaError e) {
            fail(e);
            return;
        }

        if (!result.arg1()
            .toboolean()) {
            finished = true;
            pending = null;
            error = result.arg(2)
                .tojstring();
            return;
        }

        // A successful resume says nothing about whether the script is still alive: it either yielded a request on its
        // way past, or returned. Which of the two is exactly what pending answers.
        if (pending == null) {
            finished = true;
        }
    }

    private void fail(LuaError e) {
        finished = true;
        pending = null;
        error = e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private void expect(ScriptRequest.Kind kind) {
        if (pending == null || pending.kind != kind) {
            throw new IllegalStateException(
                "script is waiting for " + (pending == null ? "nothing" : pending.kind) + ", not " + kind);
        }
    }

    /**
     * Stops a script that is spending the client's tick rather than waiting.
     *
     * <p>
     * Throwing out of the hook is what makes the loop breakable at all: the host is blocked inside {@code resume} while
     * this runs, so it cannot interrupt from outside, and the error surfaces as an ordinary failed resume.
     */
    private final class Watchdog extends VarArgFunction {

        @Override
        public Varargs invoke(Varargs args) {
            instructionsThisResume += HOOK_INTERVAL;
            if (instructionsThisResume > limits.instructionsPerResume) {
                throw new LuaError(
                    "script ran " + limits.instructionsPerResume + " instructions without waiting for anything");
            }
            if (System.nanoTime() > resumeDeadlineNanos) {
                throw new LuaError(
                    "script ran for more than " + limits.nanosPerResume / 1_000_000L + "ms without waiting");
            }
            return LuaValue.NONE;
        }
    }
}
