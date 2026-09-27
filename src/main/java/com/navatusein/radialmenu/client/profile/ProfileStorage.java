package com.navatusein.radialmenu.client.profile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.navatusein.radialmenu.RadialMenuMod;
import com.navatusein.radialmenu.core.json.ConfigCodec;
import com.navatusein.radialmenu.core.json.Settings;
import com.navatusein.radialmenu.core.model.Profile;

/**
 * Reads and writes the mod's files under {@code <game folder>/RadialMenu}.
 *
 * <p>
 * Deliberately not in {@code config}: menus are structured data a player will want to copy between instances and keep
 * in version control, and one file per profile makes that a file copy rather than a merge. The same choice MineMenu
 * made for its menu file.
 *
 * <p>
 * Writes go through a temporary file and a rename, so an interrupted save cannot leave a half-written profile behind.
 */
public final class ProfileStorage {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final String PROFILES_DIR = "profiles";
    private static final String ICONS_DIR = "icons";
    private static final String SETTINGS_FILE = "settings.json";
    private static final String PROFILE_SUFFIX = ".json";

    private static File rootDir;

    private ProfileStorage() {}

    /** @param gameDir {@code Minecraft.mcDataDir} - the game folder, not the config folder */
    public static void init(File gameDir) {
        rootDir = new File(gameDir, "RadialMenu");
        mkdirs(rootDir);
        mkdirs(profilesDir());
        mkdirs(iconsDir());
    }

    public static File rootDir() {
        return rootDir;
    }

    public static File profilesDir() {
        return new File(rootDir, PROFILES_DIR);
    }

    /** Where players drop their own PNG icons. */
    public static File iconsDir() {
        return new File(rootDir, ICONS_DIR);
    }

    public static File profileFile(String name) {
        return new File(profilesDir(), sanitize(name) + PROFILE_SUFFIX);
    }

    /** Profile names present on disk, sorted, so the editor and the cycle keybinding agree on the order. */
    public static List<String> listProfileNames() {
        List<String> names = new ArrayList<>();
        File[] files = profilesDir().listFiles();
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                if (file.isFile() && fileName.endsWith(PROFILE_SUFFIX)) {
                    names.add(fileName.substring(0, fileName.length() - PROFILE_SUFFIX.length()));
                }
            }
        }
        Collections.sort(names);
        return names;
    }

    public static boolean exists(String name) {
        return profileFile(name).isFile();
    }

    /**
     * Loads a profile.
     *
     * @return the profile, or null when it is missing or unreadable - a broken file must not stop the game from
     *         starting, so the caller falls back to a default instead
     */
    public static Profile loadProfile(String name) {
        File file = profileFile(name);
        if (!file.isFile()) {
            return null;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), UTF_8);
            Profile profile = ConfigCodec.readProfile(reader);
            if (profile.name == null || profile.name.trim()
                .isEmpty()) {
                profile.name = name;
            }
            return profile;
        } catch (Exception e) {
            RadialMenuMod.LOG.error("Failed to read profile " + file.getName() + ", ignoring it", e);
            return null;
        } finally {
            close(reader);
        }
    }

    public static boolean saveProfile(Profile profile) {
        if (profile == null || profile.name == null) {
            return false;
        }
        File target = profileFile(profile.name);
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        Writer writer = null;
        try {
            mkdirs(target.getParentFile());
            writer = new OutputStreamWriter(new FileOutputStream(temp), UTF_8);
            writer.write(ConfigCodec.writeProfile(profile));
            writer.close();
            writer = null;
            return replace(temp, target);
        } catch (IOException e) {
            RadialMenuMod.LOG.error("Failed to write profile " + target.getName(), e);
            return false;
        } finally {
            close(writer);
        }
    }

    public static boolean deleteProfile(String name) {
        return profileFile(name).delete();
    }

    public static Settings loadSettings() {
        File file = new File(rootDir, SETTINGS_FILE);
        if (!file.isFile()) {
            return new Settings();
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), UTF_8);
            return ConfigCodec.readSettings(reader);
        } catch (Exception e) {
            RadialMenuMod.LOG.error("Failed to read settings, falling back to defaults", e);
            return new Settings();
        } finally {
            close(reader);
        }
    }

    public static void saveSettings(Settings settings) {
        File target = new File(rootDir, SETTINGS_FILE);
        File temp = new File(rootDir, SETTINGS_FILE + ".tmp");
        Writer writer = null;
        try {
            mkdirs(rootDir);
            writer = new OutputStreamWriter(new FileOutputStream(temp), UTF_8);
            writer.write(ConfigCodec.writeSettings(settings));
            writer.close();
            writer = null;
            replace(temp, target);
        } catch (IOException e) {
            RadialMenuMod.LOG.error("Failed to write settings", e);
        } finally {
            close(writer);
        }
    }

    /** Keeps a profile name usable as a file name on Windows as well as Linux. */
    public static String sanitize(String name) {
        if (name == null) {
            return "default";
        }
        String cleaned = name.trim()
            .replaceAll("[\\\\/:*?\"<>|]", "_");
        return cleaned.isEmpty() ? "default" : cleaned;
    }

    private static boolean replace(File temp, File target) {
        if (target.exists() && !target.delete()) {
            RadialMenuMod.LOG.error("Could not replace " + target.getName());
            return false;
        }
        if (!temp.renameTo(target)) {
            RadialMenuMod.LOG.error("Could not rename " + temp.getName() + " to " + target.getName());
            return false;
        }
        return true;
    }

    private static void mkdirs(File dir) {
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            RadialMenuMod.LOG.error("Could not create directory " + dir.getAbsolutePath());
        }
    }

    private static void close(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // Nothing useful to do; the save already reported any real failure.
            }
        }
    }
}
