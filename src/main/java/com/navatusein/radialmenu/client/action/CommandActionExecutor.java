package com.navatusein.radialmenu.client.action;

import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionTypes;
import com.navatusein.radialmenu.core.action.Placeholders;

/**
 * Sends chat lines or slash commands.
 *
 * <p>
 * Three behaviours out of the same stored text, because they are the three things a menu entry is actually wanted
 * for: fire everything at once, space the lines out over time when a server needs a moment between them, or step
 * through the lines one activation at a time.
 */
public class CommandActionExecutor implements IActionExecutor {

    @Override
    public String typeId() {
        return ActionTypes.COMMAND;
    }

    @Override
    public boolean execute(ActionSpec spec) {
        String[] lines = Placeholders.splitLines(spec.getString(ActionTypes.PARAM_COMMAND, ""));
        if (lines.length == 0) {
            return false;
        }

        if (spec.getBoolean(ActionTypes.PARAM_CYCLE, false)) {
            // One line per activation. The cursor lives on the spec but is transient, so it never reaches the file.
            int index = Math.floorMod(spec.cycleCursor, lines.length);
            spec.cycleCursor = index + 1;
            ChatSender.send(lines[index]);
            return true;
        }

        int delay = Math.max(0, spec.getInt(ActionTypes.PARAM_DELAY_TICKS, 0));

        if (delay == 0) {
            for (String line : lines) {
                ChatSender.send(line);
            }
            return true;
        }

        // Restart rather than overlap if the entry is triggered again mid-run.
        DelayedActions.cancel(spec);
        ChatSender.send(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            DelayedActions.scheduleCommand(spec, lines[i], delay * i);
        }
        return true;
    }
}
