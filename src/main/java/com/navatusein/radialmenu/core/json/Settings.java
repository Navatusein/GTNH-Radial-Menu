package com.navatusein.radialmenu.core.json;

/**
 * Everything that is not part of a single profile: which profile is active right now.
 *
 * <p>
 * Auto-bind rules deliberately live inside each profile rather than here, so that copying one profile file to another
 * installation carries its rules with it.
 */
public class Settings {

    public static final int CURRENT_FORMAT_VERSION = 1;

    public static final String DEFAULT_PROFILE = "default";

    public int formatVersion = CURRENT_FORMAT_VERSION;

    public String activeProfile = DEFAULT_PROFILE;

    public void normalize() {
        if (formatVersion <= 0) {
            formatVersion = CURRENT_FORMAT_VERSION;
        }
        if (activeProfile == null || activeProfile.trim()
            .isEmpty()) {
            activeProfile = DEFAULT_PROFILE;
        }
    }
}
