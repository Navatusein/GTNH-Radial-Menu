package com.navatusein.radialmenu.client.script;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.navatusein.radialmenu.RadialMenuMod;
import com.navatusein.radialmenu.client.action.ActionExecutors;
import com.navatusein.radialmenu.client.gui.GuiRadialWheel;
import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.editor.GuiTextPrompt;
import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionTypes;
import com.navatusein.radialmenu.core.model.MenuNode;
import com.navatusein.radialmenu.core.script.ScriptLimits;
import com.navatusein.radialmenu.core.script.ScriptRequest;
import com.navatusein.radialmenu.core.script.ScriptTask;

/**
 * Runs scripts: the half of the contract that is allowed to touch the game.
 *
 * <p>
 * A script asks for one thing at a time and is resumed with the answer, all of it on the client tick. Nothing here
 * blocks, so a script that waits two minutes for a server costs exactly as much as one that waits none.
 *
 * <p>
 * The ordering around a choice is load-bearing. Choosing an entry closes the wheel and queues a
 * {@link ActionTypes#SCRIPT_RESUME} for the next tick - which means the screen is gone <em>before</em> the answer
 * arrives, and a wheel that closed is otherwise indistinguishable from one the player dismissed. So a closed wheel is
 * not resolved on the spot: {@link #onClientTick()} runs after {@code ActionExecutors.runPending()}, looks for a choice
 * first and only then treats the closure as a dismissal.
 */
public final class ScriptHost {

    /**
     * Requests one run may get through in a single tick.
     *
     * <p>
     * A script sending a hundred commands in one go is a script about to be kicked for spam, and spreading them over
     * ticks is both kinder to the server and the only thing that keeps a runaway loop of sends from owning the tick.
     */
    private static final int MAX_STEPS_PER_TICK = 32;

    private static final int DEFAULT_TIMEOUT_TICKS = 600;

    private static final List<Run> RUNS = new ArrayList<>();

    /** The last error of each entry that has one, for the editor to show. */
    private static final Map<ActionSpec, String> LAST_ERRORS = new WeakHashMap<>();

    private static long nextToken;

    private ScriptHost() {}

    /** One running script, and everything the host remembers on its behalf. */
    private static final class Run {

        final String token;

        /** The entry that started this, so triggering it again restarts rather than overlaps. */
        final ActionSpec owner;

        final ScriptTask task;

        /** Ticks of life left, waiting included. 0 means the script was given no limit. */
        int ticksLeft;

        /** Where this run has read to in the chat buffer. */
        long cursor;

        /** Set while the run is counting down a wait; cleared the moment it is resumed. */
        boolean waiting;
        int waitTicksLeft;
        int waitedTicks;

        /** The wheel this run opened, while it is still on screen. */
        GuiRadialWheel wheel;

        /** A choice made but not yet handed over - which is how a keepOpen wheel can be clicked twice in a row. */
        Integer choice;

        /** The wheel went away without a choice reaching us. */
        boolean dismissed;

        /** The text box this run put up, while it is still on screen. */
        GuiTextPrompt prompt;

        /** What the player typed, waiting to be handed over on the next pump. */
        String typed;

        boolean answered;

        Run(String token, ActionSpec owner, ScriptTask task) {
            this.token = token;
            this.owner = owner;
            this.task = task;
        }
    }

    /**
     * Starts the script an entry carries.
     *
     * @return false if there was nothing to run, which is what the executor reports as "did not run"
     */
    public static boolean start(ActionSpec spec) {
        String source = spec.getString(ActionTypes.PARAM_SCRIPT, "");
        if (source.trim()
            .isEmpty()) {
            return false;
        }

        // Restart rather than overlap, the same rule chains and cycling commands already follow.
        cancelOwnedBy(spec, null);

        String token = "script-" + ++nextToken;
        // The chunk is named "script" rather than after the run, because its name is what prefixes the line number in
        // an error the player reads: "script:3: ..." says where to look, "script-7:3: ..." invites a hunt for script 7.
        ScriptTask task = ScriptTask.start(source, "script", token, ClientScriptContext.INSTANCE, ScriptLimits.DEFAULT);

        Run run = new Run(token, spec, task);
        run.ticksLeft = Math.max(0, spec.getInt(ActionTypes.PARAM_TIMEOUT_TICKS, DEFAULT_TIMEOUT_TICKS));
        run.cursor = ChatCapture.end();
        RUNS.add(run);

        // Pumped here rather than next tick: this is called from inside the tick's action queue, and a script whose
        // first act is a command should send it in the tick the player chose the entry.
        pump(run);
        return true;
    }

    /** Hands a script the entry the player chose from a menu it opened. */
    public static void choose(String token, int choice) {
        for (Run run : RUNS) {
            if (run.token.equals(token)) {
                run.choice = choice;
                return;
            }
        }
        // The run is gone - it timed out, or was restarted while its wheel was still up. The click is simply spent.
        RadialMenuMod.LOG.debug("No running script for " + token + "; the choice was discarded");
    }

    /** Called by the wheel as it leaves the screen, whatever took it away. */
    public static void wheelClosed(GuiRadialWheel wheel) {
        for (Run run : RUNS) {
            if (run.wheel == wheel) {
                run.wheel = null;
                run.dismissed = true;
                return;
            }
        }
    }

    public static boolean hasRunning() {
        return !RUNS.isEmpty();
    }

    /** Stops every run - leaving a world, or disconnecting. */
    public static void cancelAll(String reason) {
        for (Run run : new ArrayList<>(RUNS)) {
            cancel(run, reason);
        }
        RUNS.clear();
        ChatCapture.clear();
    }

    /**
     * One tick of every running script.
     *
     * <p>
     * Must run after {@code ActionExecutors.runPending()}, so a choice made last tick is in hand before a closed wheel
     * is read as a dismissal.
     */
    public static void onClientTick() {
        ChatCapture.drain();
        if (RUNS.isEmpty()) {
            // Still worth a look: a run that wrote to the store on its last tick has left it waiting to be saved.
            ScriptStore.flush();
            return;
        }

        for (Run run : new ArrayList<>(RUNS)) {
            if (run.waiting && run.waitTicksLeft > 0) {
                run.waitTicksLeft--;
                run.waitedTicks++;
            }

            if (run.ticksLeft > 0 && --run.ticksLeft == 0) {
                cancel(run, "the script ran out of time");
                RUNS.remove(run);
                continue;
            }

            pump(run);
        }

        // Once per tick rather than per write, so a script setting ten keys in a row costs one file.
        ScriptStore.flush();
    }

    /** Runs a script until it needs a later tick, or finishes. */
    private static void pump(Run run) {
        for (int step = 0; step < MAX_STEPS_PER_TICK; step++) {
            if (run.task.isFinished()) {
                finish(run);
                return;
            }
            ScriptRequest request = run.task.pending();
            if (request == null) {
                finish(run);
                return;
            }
            if (!dispatch(run, request)) {
                return;
            }
        }
    }

    /**
     * Performs one request.
     *
     * @return true if the script was resumed and can be asked again this tick
     */
    private static boolean dispatch(Run run, ScriptRequest request) {
        switch (request.kind) {
            case SEND:
                // Through the command action rather than straight to the chat sender: that is the one path with
                // placeholders, client-side commands and the screen-hiding that mods reading their keys depend on.
                ActionExecutors
                    .runNow(new ActionSpec(ActionTypes.COMMAND).set(ActionTypes.PARAM_COMMAND, request.text));
                run.task.resumeVoid();
                return true;

            case NOTIFY:
                tell(request.text, EnumChatFormatting.GRAY);
                run.task.resumeVoid();
                return true;

            case LOG:
                RadialMenuMod.LOG.info("[" + run.token + "] " + request.text);
                run.task.resumeVoid();
                return true;

            case ACTION:
                runAction(run, request.action);
                run.task.resumeVoid();
                return true;

            case SLEEP:
                if (!run.waiting) {
                    run.waiting = true;
                    run.waitTicksLeft = Math.max(1, request.ticks);
                    return false;
                }
                if (run.waitTicksLeft > 0) {
                    return false;
                }
                run.waiting = false;
                run.task.resumeVoid();
                return true;

            case AWAIT_LINE:
                return awaitLine(run, request);

            case MENU:
                return openOrResolveMenu(run, request.menu);

            case MENU_UPDATE:
                if (run.wheel != null) {
                    run.wheel.replaceRoot(request.menu);
                }
                run.task.resumeVoid();
                return true;

            case MENU_CLOSE:
                closeWheel(run);
                run.task.resumeVoid();
                return true;

            case STORE_SET:
                ScriptStore.set(request.key, request.text);
                run.task.resumeVoid();
                return true;

            case PROMPT:
                return openOrResolvePrompt(run, request);

            default:
                RadialMenuMod.LOG.warn("Unknown script request " + request.kind);
                cancel(run, "the script asked for something this version does not know about");
                RUNS.remove(run);
                return false;
        }
    }

    /**
     * A script may run the mod's own actions, but not the two that would run a script.
     *
     * <p>
     * {@code script} would let one re-enter itself without bound, and {@code scriptResume} is an answer rather than an
     * action - forging one would hand some other run a choice its player never made.
     */
    private static void runAction(Run run, ActionSpec spec) {
        if (spec == null || spec.type == null) {
            return;
        }
        if (ActionTypes.SCRIPT.equals(spec.type) || ActionTypes.SCRIPT_RESUME.equals(spec.type)) {
            RadialMenuMod.LOG.warn("[" + run.token + "] refused to run a '" + spec.type + "' action from a script");
            return;
        }
        ActionExecutors.runNow(spec);
    }

    private static boolean awaitLine(Run run, ScriptRequest request) {
        if (!run.waiting) {
            run.waiting = true;
            run.waitTicksLeft = Math.max(1, request.ticks);
            run.waitedTicks = 0;
        }

        run.cursor = ChatCapture.clampCursor(run.cursor);
        String line = ChatCapture.at(run.cursor);
        if (line != null) {
            run.cursor++;
            int waited = run.waitedTicks;
            run.waiting = false;
            run.task.resumeLine(line, waited);
            return true;
        }

        if (run.waitTicksLeft > 0) {
            return false;
        }

        run.waiting = false;
        run.task.resumeLine(null, request.ticks);
        return true;
    }

    /**
     * Opens the script's wheel, or resolves the one already up.
     *
     * <p>
     * A choice is read before a dismissal, because closing the wheel is how a choice gets made.
     */
    private static boolean openOrResolveMenu(Run run, MenuNode menu) {
        if (run.choice != null) {
            int choice = run.choice;
            run.choice = null;
            run.dismissed = false;
            run.task.resumeChoice(choice);
            return true;
        }

        Minecraft mc = Minecraft.getMinecraft();

        // Already waiting on a wheel of ours: nothing to do until the player acts.
        if (run.wheel != null && mc.currentScreen == run.wheel) {
            return false;
        }

        if (run.dismissed) {
            run.dismissed = false;
            run.wheel = null;
            run.task.resumeChoice(0);
            return true;
        }

        if (mc.thePlayer == null) {
            run.task.resumeChoice(0);
            return true;
        }

        // Somebody else's screen is open - a container, the pause menu, another mod's GUI. Opening over it would take
        // it away from the player mid-use, so the menu counts as dismissed instead.
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiRadialWheel)) {
            RadialMenuMod.LOG.debug("[" + run.token + "] a screen was open, so the script's menu was not shown");
            run.task.resumeChoice(0);
            return true;
        }

        GuiRadialWheel wheel = new GuiRadialWheel(menu);
        run.wheel = wheel;
        mc.displayGuiScreen(wheel);
        // Opening replaces whatever was on screen, and a wheel going away reports itself closed - including the one
        // this run had up a moment ago. That closure is this run's own doing, not a dismissal.
        run.dismissed = false;
        return false;
    }

    /**
     * Puts the text box up, or hands over what came back from it.
     *
     * <p>
     * The same shape as a menu, and for the same reason: the answer arrives from a screen rather than from this
     * call, so the request blocks and is asked again on a later tick. An answer is read before the screen's absence,
     * because confirming is what closes it - the other order would turn every answer into a cancellation.
     *
     * <p>
     * A screen going away with nothing typed is a cancellation however it went: Escape, the cancel button, or
     * another mod taking the screen. All three mean the player is not answering, and {@code nil} is what a script
     * already tests for after a dismissed menu.
     */
    private static boolean openOrResolvePrompt(Run run, ScriptRequest request) {
        if (run.answered) {
            String typed = run.typed;
            run.answered = false;
            run.typed = null;
            run.prompt = null;
            run.task.resumeText(typed);
            return true;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (run.prompt != null) {
            if (mc.currentScreen == run.prompt) {
                return false;
            }
            run.prompt = null;
            run.task.resumeText(null);
            return true;
        }

        if (mc.thePlayer == null) {
            run.task.resumeText(null);
            return true;
        }
        // Somebody else's screen is open, exactly as with a menu: taking it away mid-use is worse than not asking.
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiRadialWheel)) {
            RadialMenuMod.LOG.debug("[" + run.token + "] a screen was open, so the script's prompt was not shown");
            run.task.resumeText(null);
            return true;
        }

        final Run owner = run;
        // The title goes through the translator, which runs it past String.format on the way out - so a per cent
        // sign a script wrote for a player to read is doubled here rather than coming out as "Format error".
        String title = request.text == null ? "" : request.text.replace("%", "%%");
        GuiTextPrompt prompt = new GuiTextPrompt(title, request.key, new GuiTextPrompt.Result() {

            @Override
            public String onConfirm(String value) {
                owner.typed = value;
                owner.answered = true;
                // Nothing to object to: whether the text is any good is the script's business, and it is the one
                // that can say so in chat.
                return null;
            }
        });
        run.prompt = prompt;
        // A wheel of this run's own is taken down first rather than pushed under the box. Left up it would be the
        // screen the prompt returns to when it closes - a wheel nothing is waiting on, belonging to a script that
        // has moved past it - and closing it here is also what keeps that closure from reading as a dismissal.
        closeWheel(run);
        GuiStack.push(prompt);
        return false;
    }

    private static void closeWheel(Run run) {
        Minecraft mc = Minecraft.getMinecraft();
        if (run.wheel != null && mc.currentScreen == run.wheel) {
            mc.displayGuiScreen(null);
        }
        run.wheel = null;
        run.dismissed = false;
    }

    /**
     * What this entry's script failed with last time, or null if it ran.
     *
     * <p>
     * Kept so the editor can point at the line: the message starts with the chunk name and a line number, and it is
     * already in hand by the time anyone opens the script to look. Weakly held, so an entry that is deleted takes its
     * error with it.
     */
    public static String lastError(ActionSpec spec) {
        return spec == null ? null : LAST_ERRORS.get(spec);
    }

    /** Takes a finished run off the list, and says what went wrong if anything did. */
    private static void finish(Run run) {
        RUNS.remove(run);
        closeWheel(run);

        String error = run.task.error();
        if (error == null) {
            // A run that worked clears what the last one said, or the editor would keep pointing at a line that is
            // right now.
            LAST_ERRORS.remove(run.owner);
            return;
        }
        LAST_ERRORS.put(run.owner, error);
        RadialMenuMod.LOG.warn("[" + run.token + "] " + error);
        tell("Script error: " + error, EnumChatFormatting.RED);
    }

    private static void cancel(Run run, String reason) {
        run.task.cancel(reason);
        closeWheel(run);
        RadialMenuMod.LOG.warn("[" + run.token + "] cancelled: " + reason);
    }

    private static void cancelOwnedBy(ActionSpec owner, String reason) {
        Iterator<Run> iterator = RUNS.iterator();
        while (iterator.hasNext()) {
            Run run = iterator.next();
            if (run.owner == owner) {
                iterator.remove();
                run.task.cancel(reason == null ? "restarted" : reason);
                closeWheel(run);
            }
        }
    }

    /** A line in the player's own chat. Nothing a script says is sent anywhere. */
    private static void tell(String text, EnumChatFormatting colour) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || text == null) {
            return;
        }
        mc.thePlayer.addChatMessage(new ChatComponentText(colour + text));
    }
}
