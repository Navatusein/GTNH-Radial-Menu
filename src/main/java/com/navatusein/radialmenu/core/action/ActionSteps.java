package com.navatusein.radialmenu.core.action;

import java.util.List;

/**
 * Ordering of the steps in a chain.
 *
 * <p>
 * Here rather than in the editor for the same reason {@code MenuNode.moveInList} is: index arithmetic written inside
 * a screen is arithmetic nothing tests, and the order of a chain is the one thing about it that has to be right.
 */
public final class ActionSteps {

    private ActionSteps() {}

    /**
     * Moves one step up or down by a place.
     *
     * <p>
     * Unlike a fixed-layout menu, a chain has no empty places to skip over - a step either has a neighbour to swap
     * with or is already at the end it is being moved towards.
     *
     * @param direction negative to move earlier, positive to move later
     * @return whether anything moved, which is also what decides if the button is offered at all
     */
    public static boolean move(List<ActionSpec> steps, int index, int direction) {
        if (steps == null || direction == 0 || index < 0 || index >= steps.size()) {
            return false;
        }
        int target = index + (direction < 0 ? -1 : 1);
        if (target < 0 || target >= steps.size()) {
            return false;
        }
        steps.set(index, steps.set(target, steps.get(index)));
        return true;
    }
}
