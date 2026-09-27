package com.navatusein.radialmenu.client.action;

import com.navatusein.radialmenu.client.input.KeybindStateTracker;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionTypes;

/** Switches the active profile from inside the wheel itself. */
public class ProfileSwitchExecutor implements IActionExecutor {

    @Override
    public String typeId() {
        return ActionTypes.PROFILE_SWITCH;
    }

    @Override
    public boolean execute(ActionSpec spec) {
        String target = spec.getString(ActionTypes.PARAM_PROFILE, null);

        // Any toggle held by the outgoing profile would otherwise stay down with nothing left to release it.
        KeybindStateTracker.releaseAll();

        if (target == null || target.trim()
            .isEmpty()) {
            return ProfileManager.cycle(1);
        }
        return ProfileManager.switchTo(target.trim());
    }
}
