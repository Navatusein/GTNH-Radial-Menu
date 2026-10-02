package com.navatusein.radialmenu.client.action;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import com.navatusein.radialmenu.RadialMenuMod;
import com.navatusein.radialmenu.client.input.KeyBindingLookup;
import com.navatusein.radialmenu.client.input.KeybindStateTracker;
import com.navatusein.radialmenu.client.input.VanillaKeyEffects;
import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionTypes;
import com.navatusein.radialmenu.core.action.Modifiers;

/** Presses a keybinding, bound or not. The reason this mod exists. */
public class KeyActionExecutor implements IActionExecutor {

    /** Ticks between starting the sneak and pressing the binding, so the player's own update lands in between. */
    private static final int SNEAK_LEAD_TICKS = 1;

    /** How long the sneak is held when nothing longer is asked for. */
    private static final int SNEAK_HOLD_TICKS = 4;

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

        if (spec.getBoolean(ActionTypes.PARAM_SNEAK, false)) {
            return sneakThenPress(spec, mode, holdTicks);
        }

        boolean activated = KeybindStateTracker.activate(binding, mode, holdTicks, Modifiers.of(spec));
        SneakDiagnostics.log("pressed " + description, binding);
        return activated;
    }

    /**
     * Starts sneaking, and presses the binding a tick later.
     *
     * <p>
     * The delay is the whole point. {@code EntityPlayerSP.isSneaking()} does not read the binding - it returns
     * {@code movementInput.sneak}, a copy refreshed once per tick inside the player's own update, which happens after
     * the tick event this runs in. A mod asking "is the player sneaking?" from the key event we post would therefore
     * read last tick's answer. One tick later it reads this one.
     *
     * <p>
     * Minecraft Backpack is the case it was written for: shift with its key puts the backpack on your back, and the
     * check is {@code player.isSneaking()} rather than anything to do with the Shift key.
     */
    private boolean sneakThenPress(ActionSpec spec, KeybindStateTracker.Mode mode, int holdTicks) {
        KeyBinding sneak = Minecraft.getMinecraft().gameSettings.keyBindSneak;
        // Held a little past the press it is there for, so the mod reading it has a tick either side.
        int sneakTicks = mode == KeybindStateTracker.Mode.HOLD ? Math.max(SNEAK_HOLD_TICKS, holdTicks)
            : SNEAK_HOLD_TICKS;
        KeybindStateTracker.activate(sneak, KeybindStateTracker.Mode.HOLD, sneakTicks, Modifiers.NONE);
        SneakDiagnostics.log("sneak started", sneak);

        // The same action without the sneak flag, so the deferred run presses the binding and does not start again.
        ActionSpec pressOnly = spec.copy();
        pressOnly.set(ActionTypes.PARAM_SNEAK, "false");
        DelayedActions.scheduleAction(spec, pressOnly, SNEAK_LEAD_TICKS);
        return true;
    }
}
