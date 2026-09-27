package com.navatusein.radialmenu.client.action;

import java.util.ArrayList;
import java.util.List;

import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionTypes;

/**
 * Runs an action's nested steps in order.
 *
 * <p>
 * The {@code steps} field has been in the file format since the first release specifically so that adding chains
 * later could not invalidate anyone's profile - this is the executor that finally reads it.
 *
 * <p>
 * A step can itself be a sequence. Depth is capped rather than trusted, because a profile edited by hand can easily
 * contain a chain that refers to itself, and the client should not lock up over it.
 */
public class SequenceExecutor implements IActionExecutor {

    private static final int MAX_DEPTH = 8;

    private static int depth;

    @Override
    public String typeId() {
        return ActionTypes.SEQUENCE;
    }

    @Override
    public boolean execute(ActionSpec spec) {
        if (depth >= MAX_DEPTH) {
            return false;
        }

        List<ActionSpec> steps = runnableSteps(spec);
        if (steps.isEmpty()) {
            return false;
        }

        int delay = Math.max(0, spec.getInt(ActionTypes.PARAM_DELAY_TICKS, 0));

        // Restart rather than overlap if the entry is triggered again mid-run.
        DelayedActions.cancel(spec);

        depth++;
        try {
            // The first step fires now; with a delay set, the rest follow one delay apart.
            for (int i = 0; i < steps.size(); i++) {
                if (i == 0 || delay == 0) {
                    ActionExecutors.runNow(steps.get(i));
                } else {
                    DelayedActions.scheduleAction(spec, steps.get(i), delay * i);
                }
            }
        } finally {
            depth--;
        }
        return true;
    }

    /**
     * The steps worth running, in order.
     *
     * <p>
     * A step whose type needs a parameter it has not got - a keybinding action with no keybinding, most often, added
     * to the chain and not filled in yet - is passed over rather than run. Running it would do nothing except log a
     * warning on every activation, and the editor already shows it as unset.
     *
     * <p>
     * Filtered before the timing is worked out rather than during: spacing the steps by their position in the
     * original list would leave a silent gap wherever one was skipped.
     */
    private static List<ActionSpec> runnableSteps(ActionSpec spec) {
        List<ActionSpec> runnable = new ArrayList<>();
        if (spec.steps == null) {
            return runnable;
        }
        for (ActionSpec step : spec.steps) {
            if (step != null && step.type != null && ActionTypes.isComplete(step)) {
                runnable.add(step);
            }
        }
        return runnable;
    }
}
