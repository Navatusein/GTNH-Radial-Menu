package com.navatusein.radialmenu.client.profile;

import java.util.List;

import com.navatusein.radialmenu.RadialMenuMod;
import com.navatusein.radialmenu.core.json.Settings;
import com.navatusein.radialmenu.core.model.Profile;

/**
 * Holds the profile that is active right now and switches between them.
 *
 * <p>
 * The active profile is remembered in {@code settings.json}; the auto-bind rules that can override it live inside each
 * profile, so copying a profile file carries its rules along.
 */
public final class ProfileManager {

    private static Settings settings = new Settings();

    private static Profile active;

    private ProfileManager() {}

    /** Loads settings and the active profile, creating a starter profile on first run. */
    public static void load() {
        settings = ProfileStorage.loadSettings();

        if (ProfileStorage.listProfileNames()
            .isEmpty()) {
            Profile starter = DefaultProfile.create();
            ProfileStorage.saveProfile(starter);
            settings.activeProfile = starter.name;
            ProfileStorage.saveSettings(settings);
        }

        if (!switchTo(settings.activeProfile)) {
            List<String> names = ProfileStorage.listProfileNames();
            if (!names.isEmpty()) {
                switchTo(names.get(0));
            } else {
                active = DefaultProfile.create();
            }
        }
    }

    public static Profile active() {
        if (active == null) {
            active = DefaultProfile.create();
        }
        return active;
    }

    public static String activeName() {
        return active().name;
    }

    /** @return false when the profile does not exist or could not be read */
    public static boolean switchTo(String name) {
        Profile loaded = ProfileStorage.loadProfile(name);
        if (loaded == null) {
            return false;
        }
        active = loaded;
        if (!name.equals(settings.activeProfile)) {
            settings.activeProfile = name;
            ProfileStorage.saveSettings(settings);
        }
        RadialMenuMod.LOG.info("Active profile: " + name);
        return true;
    }

    /** Moves to the next profile on disk, wrapping around. Backs the cycle keybinding. */
    public static boolean cycle(int direction) {
        List<String> names = ProfileStorage.listProfileNames();
        if (names.size() < 2) {
            return false;
        }
        int current = names.indexOf(activeName());
        int next = current < 0 ? 0 : Math.floorMod(current + direction, names.size());
        return switchTo(names.get(next));
    }

    public static void saveActive() {
        ProfileStorage.saveProfile(active());
    }

    /**
     * Applies the first auto-bind rule that matches the world the player just joined.
     *
     * @param serverAddress server address, or null in single player
     * @param worldName     single-player world folder name, or null on a server
     * @return true if the active profile changed
     */
    public static boolean applyAutoBind(String serverAddress, String worldName) {
        for (String name : ProfileStorage.listProfileNames()) {
            if (name.equals(activeName())) {
                continue;
            }
            Profile candidate = ProfileStorage.loadProfile(name);
            if (candidate != null && candidate.matches(serverAddress, worldName)) {
                RadialMenuMod.LOG.info(
                    "Auto-binding to profile " + name + " for " + (serverAddress == null ? worldName : serverAddress));
                return switchTo(name);
            }
        }
        return false;
    }
}
