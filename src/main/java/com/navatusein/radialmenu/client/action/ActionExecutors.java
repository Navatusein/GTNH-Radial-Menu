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

    /** Runs everything queued since the previous tick. */
    public static void runPending() {
        while (!PENDING.isEmpty()) {
            run(PENDING.poll());
        }
    }

    private static void run(ActionSpec spec) {
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
