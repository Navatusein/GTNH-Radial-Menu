package com.navatusein.radialmenu.client.action;

import com.navatusein.radialmenu.client.script.ScriptHost;
import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionTypes;

/**
 * Hands a script the entry the player chose.
 *
 * <p>
 * Not an action anyone writes: the entries of a script-built wheel carry one, so a choice travels the same road as
 * every other entry's - close the wheel, queue for the next tick, run here. A script-built menu therefore needs nothing
 * special from the wheel itself.
 */
public class ScriptResumeExecutor implements IActionExecutor {

    @Override
    public String typeId() {
        return ActionTypes.SCRIPT_RESUME;
    }

    @Override
    public boolean execute(ActionSpec spec) {
        String token = spec.getString(ActionTypes.PARAM_TOKEN, null);
        int choice = spec.getInt(ActionTypes.PARAM_CHOICE, 0);
        if (token == null || choice <= 0) {
            return false;
        }
        // Recorded rather than run: the script is resumed by the host's own pump later in this tick, so a script that
        // opens another menu does not do it from inside the action queue being iterated.
        ScriptHost.choose(token, choice);
        return true;
    }
}
