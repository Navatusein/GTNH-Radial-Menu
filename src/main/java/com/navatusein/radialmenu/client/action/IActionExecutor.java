package com.navatusein.radialmenu.client.action;

import com.navatusein.radialmenu.core.action.ActionSpec;

/**
 * Runs one kind of action.
 *
 * <p>
 * Executors are the only place Minecraft appears in the action pipeline; the stored {@link ActionSpec} is plain data.
 * That split is what lets a new action type be added without touching the menu model, the editor or the renderer.
 */
public interface IActionExecutor {

    /** Matches {@link ActionSpec#type}. */
    String typeId();

    /**
     * Performs the action. Called on the client tick after the wheel has closed, so the game already has input focus
     * and any GUI this action opens will not be fighting the wheel.
     *
     * @return false if the action could not run, for example because its keybinding no longer exists
     */
    boolean execute(ActionSpec spec);
}
