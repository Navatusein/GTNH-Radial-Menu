package com.navatusein.radialmenu.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One saved menu configuration. Stored as a single file so profiles can be copied, shared and diffed. */
public class Profile {

    public static final int CURRENT_FORMAT_VERSION = 1;

    public int formatVersion = CURRENT_FORMAT_VERSION;

    public String name;

    /** Rules that make this profile activate automatically. Empty means manual switching only. */
    public List<ProfileBinding> bindings;

    /**
     * Colours for every menu in this profile, unless a menu overrides them.
     *
     * <p>
     * Null inherits the mod's config, which is where the defaults live - so a profile can have a look of its own
     * without every submenu repeating it.
     */
    public MenuStyle style;

    public MenuNode root;

    public static Profile empty(String name) {
        Profile profile = new Profile();
        profile.name = name;
        profile.bindings = new ArrayList<>();
        profile.root = MenuNode.category(name, null, SlotLayout.fixed(SlotLayout.DEFAULT_SLOTS));
        profile.root.ensureSlotCapacity();
        return profile;
    }

    public List<ProfileBinding> bindingsOrEmpty() {
        return bindings == null ? Collections.<ProfileBinding>emptyList() : bindings;
    }

    public boolean matches(String serverAddress, String worldName) {
        for (ProfileBinding binding : bindingsOrEmpty()) {
            if (binding != null && binding.matches(serverAddress, worldName)) {
                return true;
            }
        }
        return false;
    }

    public void normalize() {
        if (formatVersion <= 0) {
            formatVersion = CURRENT_FORMAT_VERSION;
        }
        if (root == null) {
            root = MenuNode.category(name, null, SlotLayout.fixed(SlotLayout.DEFAULT_SLOTS));
        }
        if (root.children == null) {
            root.children = new ArrayList<>();
        }
        root.normalize();
    }
}
