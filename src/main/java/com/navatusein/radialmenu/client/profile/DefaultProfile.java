package com.navatusein.radialmenu.client.profile;

import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionTypes;
import com.navatusein.radialmenu.core.model.IconSpec;
import com.navatusein.radialmenu.core.model.MenuNode;
import com.navatusein.radialmenu.core.model.Profile;
import com.navatusein.radialmenu.core.model.SlotLayout;

/**
 * The profile created on first run.
 *
 * <p>
 * Built from vanilla keybindings only, so it works in any instance and demonstrates all three press modes without
 * depending on a mod the player may not have.
 */
public final class DefaultProfile {

    private DefaultProfile() {}

    public static Profile create() {
        Profile profile = Profile.empty("default");
        profile.root.layout = SlotLayout.fixed(8);

        // By index, not by append: Profile.empty already padded the wheel to eight empty slots, so appending would
        // put every entry past the last visible sector.
        profile.root
            .setChildAt(0, keybind("Inventory", IconSpec.item("minecraft:chest", 0), "key.inventory", "tap", false));
        profile.root.setChildAt(
            2,
            keybind("Sprint", IconSpec.item("minecraft:golden_boots", 0), "key.sprint", "toggle", false));
        profile.root
            .setChildAt(4, keybind("Sneak", IconSpec.item("minecraft:leather_boots", 0), "key.sneak", "toggle", false));
        profile.root.setChildAt(6, keybind("Jump", IconSpec.item("minecraft:feather", 0), "key.jump", "hold", false));

        profile.root.ensureSlotCapacity();
        return profile;
    }

    private static MenuNode keybind(String title, IconSpec icon, String binding, String mode, boolean keepOpen) {
        ActionSpec action = new ActionSpec(ActionTypes.KEYBIND).set(ActionTypes.PARAM_BINDING, binding)
            .set(ActionTypes.PARAM_MODE, mode)
            .set(ActionTypes.PARAM_HOLD_TICKS, "10");
        MenuNode node = MenuNode.leaf(title, icon, action);
        node.keepOpen = keepOpen;
        return node;
    }
}
