package com.navatusein.radialmenu.core.json;

import java.io.Reader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.navatusein.radialmenu.core.model.Profile;

/**
 * Reads and writes the JSON the mod stores on disk.
 *
 * <p>
 * No Minecraft types are involved, so this is unit testable on its own. Nulls are deliberately not serialised for
 * object fields - but a null element inside the children array still is, which is exactly what an empty slot of a
 * fixed-size wheel needs.
 */
public final class ConfigCodec {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .registerTypeAdapterFactory(new LowercaseEnumAdapterFactory())
        .create();

    private ConfigCodec() {}

    public static String writeProfile(Profile profile) {
        return GSON.toJson(profile);
    }

    /**
     * Parses a profile and repairs whatever a hand-edited file got wrong.
     *
     * @throws JsonParseException if the text is not valid JSON at all
     */
    public static Profile readProfile(Reader reader) {
        Profile profile = GSON.fromJson(reader, Profile.class);
        if (profile == null) {
            throw new JsonParseException("Profile file is empty");
        }
        profile.normalize();
        return profile;
    }

    public static Profile readProfile(String json) {
        Profile profile = GSON.fromJson(json, Profile.class);
        if (profile == null) {
            throw new JsonParseException("Profile file is empty");
        }
        profile.normalize();
        return profile;
    }

    public static String writeSettings(Settings settings) {
        return GSON.toJson(settings);
    }

    public static Settings readSettings(Reader reader) {
        Settings settings = GSON.fromJson(reader, Settings.class);
        if (settings == null) {
            settings = new Settings();
        }
        settings.normalize();
        return settings;
    }
}
