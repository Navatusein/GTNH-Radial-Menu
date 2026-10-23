package com.navatusein.radialmenu;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Every translation carries every line the English one does, with the same placeholders.
 *
 * <p>
 * A key missing from a language does not fail anywhere at run time: the game quietly falls back to English, so a
 * player reading Russian meets one English sentence in the middle of a screen and nothing ever says why. Placeholders
 * are worse - a translation that drops a {@code %s} prints without the value, and one that adds a {@code %d} throws
 * inside {@code String.format} and shows "Format error" where the text should be.
 *
 * <p>
 * Languages are found by listing the folder, so a new one is checked the moment it is added. English is the reference
 * because it is the one written alongside the code.
 */
public class LangFilesTest {

    private static final String REFERENCE = "en_US.lang";

    /** A format conversion: {@code %s}, {@code %d}, {@code %1$s}. {@code %%} is a literal and is not counted. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[sd]");

    @Test
    public void everyLanguageHasEveryKeyWithTheSamePlaceholders() throws IOException {
        File folder = langFolder();
        Map<String, String> reference = read(new File(folder, REFERENCE));

        List<String> problems = new ArrayList<>();
        File[] files = folder.listFiles();
        assertTrue("no language files found in " + folder, files != null && files.length > 1);
        for (File file : files) {
            if (!file.getName()
                .endsWith(".lang") || file.getName()
                    .equals(REFERENCE)) {
                continue;
            }
            Map<String, String> translation = read(file);
            for (Map.Entry<String, String> line : reference.entrySet()) {
                String translated = translation.get(line.getKey());
                if (translated == null) {
                    problems.add(file.getName() + ": missing " + line.getKey());
                } else if (!placeholders(line.getValue()).equals(placeholders(translated))) {
                    problems.add(
                        file.getName() + ": "
                            + line.getKey()
                            + " has "
                            + placeholders(translated)
                            + ", English has "
                            + placeholders(line.getValue()));
                }
            }
        }

        if (!problems.isEmpty()) {
            fail("translations out of step with " + REFERENCE + ":\n  " + String.join("\n  ", problems));
        }
    }

    @Test
    public void noFileSetsAKeyTwice() throws IOException {
        // The game keeps whichever comes last, so a key added twice is a translation edited in one place and read
        // from the other.
        List<String> problems = new ArrayList<>();
        for (File file : langFolder().listFiles()) {
            if (!file.getName()
                .endsWith(".lang")) {
                continue;
            }
            Map<String, Integer> seen = new LinkedHashMap<>();
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String key = keyOf(line);
                if (key != null && seen.put(key, Integer.valueOf(1)) != null) {
                    problems.add(file.getName() + ": " + key);
                }
            }
        }
        if (!problems.isEmpty()) {
            fail("keys set twice:\n  " + String.join("\n  ", problems));
        }
    }

    private static File langFolder() {
        URL url = LangFilesTest.class.getResource("/assets/radialmenu/lang/" + REFERENCE);
        if (url == null) {
            fail("cannot find " + REFERENCE + " on the test classpath");
        }
        try {
            return new File(url.toURI()).getParentFile();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> read(File file) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            String key = keyOf(line);
            if (key != null) {
                entries.put(key, line.substring(line.indexOf('=') + 1));
            }
        }
        return entries;
    }

    /** The key of a {@code key=value} line, or null for a blank line or a comment. */
    private static String keyOf(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.indexOf('=') < 0) {
            return null;
        }
        return trimmed.substring(0, trimmed.indexOf('='));
    }

    /** The conversions a value formats, sorted: a translation may need them in another order. */
    private static List<String> placeholders(String value) {
        List<String> found = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(value.replace("%%", ""));
        while (matcher.find()) {
            found.add(
                matcher.group()
                    .replaceAll("\\d+\\$", ""));
        }
        Collections.sort(found);
        return found;
    }
}
