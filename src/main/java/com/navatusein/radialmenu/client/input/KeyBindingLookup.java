package com.navatusein.radialmenu.client.input;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

/**
 * Finds a {@link KeyBinding} from what a profile stored.
 *
 * <p>
 * Bindings are stored by their description key - {@code key.inventory} and the like - because that is the one
 * identifier that survives the player rebinding the key, which is the entire point of this mod. The category is stored
 * alongside only to break ties between two mods that picked the same description.
 */
public final class KeyBindingLookup {

    private KeyBindingLookup() {}

    /** Every registered binding, vanilla and modded alike. */
    public static List<KeyBinding> all() {
        List<KeyBinding> bindings = new ArrayList<>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings == null || mc.gameSettings.keyBindings == null) {
            return bindings;
        }
        for (KeyBinding binding : mc.gameSettings.keyBindings) {
            if (binding != null) {
                bindings.add(binding);
            }
        }
        return bindings;
    }

    /**
     * Resolves a stored reference, preferring an exact description plus category match and falling back to description
     * alone.
     *
     * @return the binding, or null if no mod provides it any more
     */
    public static KeyBinding find(String description, String category) {
        if (description == null || description.isEmpty()) {
            return null;
        }
        KeyBinding descriptionOnlyMatch = null;
        for (KeyBinding binding : all()) {
            if (!description.equalsIgnoreCase(binding.getKeyDescription())) {
                continue;
            }
            if (category != null && !category.isEmpty() && category.equalsIgnoreCase(binding.getKeyCategory())) {
                return binding;
            }
            if (descriptionOnlyMatch == null) {
                descriptionOnlyMatch = binding;
            }
        }
        return descriptionOnlyMatch;
    }

    /** True when the binding has no physical key assigned - the case this mod exists to cover. */
    public static boolean isUnbound(KeyBinding binding) {
        return binding != null && binding.getKeyCode() == 0;
    }
}
