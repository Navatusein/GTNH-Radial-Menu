package com.navatusein.radialmenu.client.action;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import com.navatusein.radialmenu.RadialMenuMod;
import com.navatusein.radialmenu.core.action.ActionSpec;

/**
 * Registry of executors, plus the one-tick delay queue actions are run through.
 *
 * <p>
 * Nothing runs an action immediately. Selecting an entry closes the wheel and queues the action for the next client
 * tick, because a handler on the receiving end may check that the game has input focus - AdventureBackpack2 does - and
 * may open a GUI of its own, which a still-open wheel would immediately replace.
 */
public final class ActionExecutors {

    private static final Map<String, IActionExecutor> EXECUTORS = new HashMap<>();

    private static final Deque<ActionSpec> PENDING = new ArrayDeque<>();

    private ActionExecutors() {}

    public static void register(IActionExecutor executor) {
        EXECUTORS.put(executor.typeId(), executor);
    }

    public static IActionExecutor get(String typeId) {
        return typeId == null ? null : EXECUTORS.get(typeId);
    }

    /** Queues an action to run on the next client tick. */
    public static void enqueue(ActionSpec spec) {
        if (spec != null && spec.type != null) {
            PENDING.add(spec);
        }
    }

    /** Runs everything queued since the previous tick, then ages anything scheduled for later. */
    public static void runPending() {
        while (!PENDING.isEmpty()) {
            runNow(PENDING.poll());
        }
        DelayedActions.onClientTick();
    }

    /**
     * Runs an action immediately.
     *
     * <p>
     * Only for callers already on the client tick - chains and delayed command lines. Everything triggered from the
     * wheel itself goes through {@link #enqueue} instead.
     *
     * <p>
     * An entry marked {@code keepOpen} leaves the wheel up, and so do the later steps of a chain it started, so the
     * one funnel every action passes through is where the wheel is hidden for the duration of the call. See
     * {@link WithoutScreen} for what that is worth and why it is conditional.
     */
    public static void runNow(ActionSpec spec) {
        if (WithoutScreen.wheelIsUp()) {
            WithoutScreen.runAction(spec);
            return;
        }
        execute(spec);
    }

    /** The execution itself, with no regard for what is on screen. Only {@link WithoutScreen} calls this directly. */
    static void execute(ActionSpec spec) {
        IActionExecutor executor = get(spec.type);
        if (executor == null) {
            RadialMenuMod.LOG.warn("No executor registered for action type '" + spec.type + "'");
            return;
        }
        try {
            if (!executor.execute(spec)) {
                RadialMenuMod.LOG.warn("Action '" + spec.type + "' did not run");
            }
        } catch (Exception e) {
            // An action misbehaving must not take the client down with it.
            RadialMenuMod.LOG.error("Action '" + spec.type + "' failed", e);
        }
    }
}
