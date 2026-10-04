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
 * That protects against a save that stops halfway; it protects against nothing a save that completes. The previous
 * few versions are kept under {@code RadialMenu/backups} for the other case - an editor that wrote exactly what it
 * was told to and destroyed something the player wanted.
 */
public final class ProfileStorage {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final String PROFILES_DIR = "profiles";
    private static final String BACKUPS_DIR = "backups";
    private static final String ICONS_DIR = "icons";
    private static final String SETTINGS_FILE = "settings.json";
    private static final String PROFILE_SUFFIX = ".json";

    /**
     * How many previous versions of a profile are kept.
     *
     * <p>
     * Three, because the mistakes this is for are noticed within a step or two - a slot saved as the wrong type, a
     * submenu converted, an entry removed - and a deeper history would mostly be a folder nobody reads. A profile
     * is a few kilobytes, so the cost is not what decides it.
     */
    private static final int KEEP_BACKUPS = 3;

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

    /**
     * Previous versions of each profile.
     *
     * <p>
     * Beside the profiles rather than inside them, so nothing that lists the folder has to know to skip it - and
     * as ordinary {@code .json} files, because the only thing anyone ever wants from a backup is to open it or copy
     * it back by hand.
     */
    public static File backupsDir() {
        return new File(rootDir, BACKUPS_DIR);
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
        String json = ConfigCodec.writeProfile(profile);

        // An editor saves whenever it closes, which is far more often than anything actually changes. Writing the
        // same bytes again would cost nothing on its own - but it would push a real previous version out of the
        // window of kept copies, which is the one thing the window is for.
        if (json.equals(read(target))) {
            return true;
        }
        rotateBackups(profile.name);

        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        Writer writer = null;
        try {
            mkdirs(target.getParentFile());
            writer = new OutputStreamWriter(new FileOutputStream(temp), UTF_8);
            writer.write(json);
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

    /**
     * Moves the profile as it stands into the backups, pushing the older copies along one.
     *
     * <p>
     * Copied rather than renamed: the live file has to stay where it is until the new one has been written, or a
     * failed write would leave the player with no profile at all and a backup they have to find.
     *
     * <p>
     * A failure here is logged and ignored. A backup is insurance, and insurance that refuses the save it was
     * protecting would be worse than none.
     */
    private static void rotateBackups(String name) {
        File live = profileFile(name);
        if (!live.isFile() || KEEP_BACKUPS <= 0) {
            return;
        }
        mkdirs(backupsDir());

        File oldest = backupFile(name, KEEP_BACKUPS);
        if (oldest.exists() && !oldest.delete()) {
            RadialMenuMod.LOG.warn("Could not drop the oldest backup " + oldest.getName());
            return;
        }
        for (int age = KEEP_BACKUPS - 1; age >= 1; age--) {
            File from = backupFile(name, age);
            if (from.isFile()) {
                from.renameTo(backupFile(name, age + 1));
            }
        }
        copy(live, backupFile(name, 1));
    }

    /** @param age 1 is the version saved over most recently */
    private static File backupFile(String name, int age) {
        return new File(backupsDir(), sanitize(name) + "." + age + PROFILE_SUFFIX);
    }

    private static void copy(File from, File to) {
        String content = read(from);
        if (content == null) {
            return;
        }
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(to), UTF_8);
            writer.write(content);
        } catch (IOException e) {
            RadialMenuMod.LOG.warn("Could not write the backup " + to.getName(), e);
        } finally {
            close(writer);
        }
    }

    /** The whole of a file, or null when there is not one to read. */
    private static String read(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), UTF_8);
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[4096];
            int got;
            while ((got = reader.read(buffer)) > 0) {
                text.append(buffer, 0, got);
            }
            return text.toString();
        } catch (IOException e) {
            RadialMenuMod.LOG.warn("Could not read " + file.getName(), e);
            return null;
        } finally {
            close(reader);
        }
    }

    /**
     * Deletes a profile, and leaves its backups where they are.
     *
     * <p>
     * Deliberately. A profile deleted by mistake is exactly the case the backups exist for, and taking them along
     * with it would make the one moment they are needed the one moment they are gone.
     */
    public static boolean deleteProfile(String name) {
        return profileFile(name).delete();
    }

    /**
     * Renames a profile's file.
     *
     * <p>
     * Goes through a temporary name because Windows filesystems are case-insensitive: renaming
     * {@code default.json} straight to {@code Default.json} is a no-op there, and writing the new name then deleting
     * the old one would delete the file just written.
     */
    public static boolean renameFile(String from, String to) {
        File source = profileFile(from);
        File target = profileFile(to);
        if (!source.isFile()) {
            return false;
        }

        File temp = new File(profilesDir(), sanitize(from) + ".rename.tmp");
        if (temp.exists() && !temp.delete()) {
            RadialMenuMod.LOG.error("Could not clear " + temp.getName());
            return false;
        }
        if (!source.renameTo(temp)) {
            RadialMenuMod.LOG.error("Could not rename " + source.getName());
            return false;
        }
        if (!temp.renameTo(target)) {
            // Put it back rather than leaving the profile under a temporary name.
            temp.renameTo(source);
            RadialMenuMod.LOG.error("Could not rename " + temp.getName() + " to " + target.getName());
            return false;
        }

        // The history follows the name. Left behind, it would be a set of files named after a profile that no
        // longer exists, next to a profile whose past looks empty.
        for (int age = 1; age <= KEEP_BACKUPS; age++) {
            File backup = backupFile(from, age);
            if (backup.isFile()) {
                backup.renameTo(backupFile(to, age));
            }
        }
        return true;
    }

    /** True when two names would land on the same file, which on Windows includes differing only in case. */
    public static boolean isSameFile(String a, String b) {
        return sanitize(a).equalsIgnoreCase(sanitize(b));
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
