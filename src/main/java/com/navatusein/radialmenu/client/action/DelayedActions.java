package com.navatusein.radialmenu.client.action;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.navatusein.radialmenu.core.action.ActionSpec;

/**
 * Runs actions a set number of ticks from now.
 *
 * <p>
 * Backs both spaced-out command lines and chains. Each entry remembers which action scheduled it, so re-triggering an
 * action cancels whatever it still had pending: mashing a slot that fires a five-command sequence should restart the
 * sequence, not interleave two copies of it.
 */
public final class DelayedActions {

    private static final class Entry {

        /** The action that scheduled this, used to cancel a half-finished run. */
        Object owner;
        ActionSpec spec;
        String rawCommand;
        int ticksRemaining;
    }

    private static final List<Entry> PENDING = new ArrayList<>();

    private DelayedActions() {}

    /** Schedules an action, cancelling anything the same owner still had queued. */
    public static void scheduleAction(Object owner, ActionSpec spec, int delayTicks) {
        Entry entry = new Entry();
        entry.owner = owner;
        entry.spec = spec;
        entry.ticksRemaining = Math.max(0, delayTicks);
        PENDING.add(entry);
    }

    /** Schedules a single chat line. */
    public static void scheduleCommand(Object owner, String command, int delayTicks) {
        Entry entry = new Entry();
        entry.owner = owner;
        entry.rawCommand = command;
        entry.ticksRemaining = Math.max(0, delayTicks);
        PENDING.add(entry);
    }

    /** Drops everything this owner had queued. Call before scheduling a fresh run. */
    public static void cancel(Object owner) {
        Iterator<Entry> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().owner == owner) {
                iterator.remove();
            }
        }
    }

    public static void cancelAll() {
        PENDING.clear();
    }

    public static boolean hasPending() {
        return !PENDING.isEmpty();
    }

    /** Ages every entry by a tick and runs the ones that came due. */
    public static void onClientTick() {
        if (PENDING.isEmpty()) {
            return;
        }
        // Collected first, because running an entry may schedule or cancel others.
        List<Entry> due = new ArrayList<>();
        Iterator<Entry> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (--entry.ticksRemaining <= 0) {
                due.add(entry);
                iterator.remove();
            }
        }

        for (Entry entry : due) {
            if (entry.rawCommand != null) {
                ChatSender.send(entry.rawCommand);
            } else if (entry.spec != null) {
                ActionExecutors.runNow(entry.spec);
            }
        }
    }
}
