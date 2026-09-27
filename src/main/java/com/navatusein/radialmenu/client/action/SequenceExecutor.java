package com.navatusein.radialmenu.client.action;

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
        List<ActionSpec> steps = spec.steps;
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        if (depth >= MAX_DEPTH) {
            return false;
        }

        int delay = Math.max(0, spec.getInt(ActionTypes.PARAM_DELAY_TICKS, 0));

        // Restart rather than overlap if the entry is triggered again mid-run.
        DelayedActions.cancel(spec);

        depth++;
        try {
            if (delay == 0) {
                for (ActionSpec step : steps) {
                    if (step != null && step.type != null) {
                        ActionExecutors.runNow(step);
                    }
                }
                return true;
            }

            // Spaced out: the first step fires now, the rest follow one delay apart.
            for (int i = 0; i < steps.size(); i++) {
                ActionSpec step = steps.get(i);
                if (step == null || step.type == null) {
                    continue;
                }
                if (i == 0) {
                    ActionExecutors.runNow(step);
                } else {
                    DelayedActions.scheduleAction(spec, step, delay * i);
                }
            }
        } finally {
            depth--;
        }
        return true;
    }
}
