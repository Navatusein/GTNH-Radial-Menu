package com.navatusein.radialmenu.client.script;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.navatusein.radialmenu.RadialMenuMod;
import com.navatusein.radialmenu.client.profile.ProfileStorage;

/**
 * What scripts remember between runs.
 *
 * <p>
 * One flat file of strings under {@code RadialMenu/script-store.json}, shared by every script rather than divided per
 * entry. Shared on purpose: two entries running the same script - a "go home" and a "set home" - want the same value,
 * and a store partitioned by whatever identity an entry happens to have would keep them apart for no reason the
 * player could see. The cost is that keys collide if two unrelated scripts pick the same word, which is why the
 * documentation says to prefix them.
 *
 * <p>
 * Strings only. A script that wants a number writes one and reads it back with {@code tonumber}; a store that
 * remembered types would have to answer what a Lua table is, and a script that needs structure can join and split it
 * far more honestly than this file could guess at.
 */
public final class ScriptStore {

    private static final String FILE = "script-store.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    /** Sorted, so the file a player opens reads like a list rather than like a hash. */
    private static Map<String, String> values;

    /** Written at the end of the tick rather than on every set: a script may write in a loop. */
    private static boolean dirty;

    private ScriptStore() {}

    public static synchronized String get(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        load();
        return values.get(key);
    }

    /** @param value null forgets the key, which is how a script clears something it set */
    public static synchronized void set(String key, String value) {
        if (key == null || key.isEmpty()) {
            return;
        }
        load();
        String previous = value == null ? values.remove(key) : values.put(key, value);
        if (value == null ? previous != null : !value.equals(previous)) {
            dirty = true;
        }
    }

    /**
     * Writes the file if anything changed.
     *
     * <p>
     * Called once a tick by the host rather than from {@code set}, so a script that writes ten keys in one go costs
     * one write of a small file instead of ten.
     */
    public static synchronized void flush() {
        if (!dirty || values == null) {
            return;
        }
        dirty = false;

        File file = new File(ProfileStorage.rootDir(), FILE);
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            GSON.toJson(values, writer);
        } catch (Exception failure) {
            RadialMenuMod.LOG.warn("Could not write the script store", failure);
        } finally {
            close(writer);
        }
    }

    /** Drops what is held in memory, so the next read takes the file as it now stands. */
    public static synchronized void reload() {
        flush();
        values = null;
    }

    private static void load() {
        if (values != null) {
            return;
        }
        values = new TreeMap<>();

        File file = new File(ProfileStorage.rootDir(), FILE);
        if (!file.isFile()) {
            return;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            Map<String, String> read = GSON
                .fromJson(reader, new TypeToken<LinkedHashMap<String, String>>() {}.getType());
            if (read != null) {
                for (Map.Entry<String, String> entry : read.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        values.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        } catch (Exception failure) {
            // A store nobody can read is a store that starts empty. Refusing to run scripts because a file somebody
            // hand-edited has a stray comma would be a far worse answer than losing what it remembered.
            RadialMenuMod.LOG.warn("Could not read the script store; starting from empty", failure);
        } finally {
            close(reader);
        }
    }

    private static void close(java.io.Closeable stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (Exception ignored) {
            // Nothing useful to do about a stream that will not close.
        }
    }
}
