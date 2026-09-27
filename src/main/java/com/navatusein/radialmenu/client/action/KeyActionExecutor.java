package com.navatusein.radialmenu.client.action;

import net.minecraft.client.settings.KeyBinding;

import com.navatusein.radialmenu.RadialMenuMod;
import com.navatusein.radialmenu.client.input.KeyBindingLookup;
import com.navatusein.radialmenu.client.input.KeybindStateTracker;
import com.navatusein.radialmenu.client.input.VanillaKeyEffects;
import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionTypes;

/** Presses a keybinding, bound or not. The reason this mod exists. */
public class KeyActionExecutor implements IActionExecutor {

    @Override
    public String typeId() {
        return ActionTypes.KEYBIND;
    }

    @Override
    public boolean execute(ActionSpec spec) {
        String description = spec.getString(ActionTypes.PARAM_BINDING, null);
        String category = spec.getString(ActionTypes.PARAM_CATEGORY, null);

        KeyBinding binding = KeyBindingLookup.find(description, category);
        if (binding == null) {
            // Most likely the mod that owned this keybinding was removed.
            RadialMenuMod.LOG.warn("Keybinding '" + description + "' is not registered by any mod");
            return false;
        }

        // A couple of vanilla bindings are only ever read from inside the keyboard event loop, so a synthetic press
        // would go unnoticed. Those are applied directly instead.
        if (VanillaKeyEffects.tryRun(binding)) {
            return true;
        }

        KeybindStateTracker.Mode mode = spec
            .getEnum(ActionTypes.PARAM_MODE, KeybindStateTracker.Mode.class, KeybindStateTracker.Mode.TAP);
        int holdTicks = spec.getInt(ActionTypes.PARAM_HOLD_TICKS, 20);

        return KeybindStateTracker.activate(binding, mode, holdTicks);
    }
}
