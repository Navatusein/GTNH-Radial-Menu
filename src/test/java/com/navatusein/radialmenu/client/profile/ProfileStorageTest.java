package com.navatusein.radialmenu.client.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.navatusein.radialmenu.core.model.Profile;

/**
 * Profile files on disk.
 *
 * <p>
 * Worth testing directly: the storage layer takes a plain directory, so none of this needs Minecraft running, and the
 * case-sensitivity trap it guards against only shows up on a real filesystem.
 */
public class ProfileStorageTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        ProfileStorage.init(folder.getRoot());
    }

    private static void save(String name) {
        ProfileStorage.saveProfile(Profile.empty(name));
    }

    @Test
    public void savesAndListsProfiles() {
        save("alpha");
        save("beta");

        List<String> names = ProfileStorage.listProfileNames();
        assertEquals(2, names.size());
        assertEquals("alpha", names.get(0));
        assertEquals("beta", names.get(1));
    }

    @Test
    public void namesThatCannotBeFileNamesAreCleanedUp() {
        assertEquals("a_b", ProfileStorage.sanitize("a/b"));
        assertEquals("a_b", ProfileStorage.sanitize("a:b"));
        assertEquals("default", ProfileStorage.sanitize("   "));
        assertEquals("default", ProfileStorage.sanitize(null));
    }

    @Test
    public void namesDifferingOnlyInCaseAreTheSameFile() {
        // The whole point: on Windows they are, and the rename path has to know that.
        assertTrue(ProfileStorage.isSameFile("default", "Default"));
        assertTrue(ProfileStorage.isSameFile("Default", "DEFAULT"));
        assertFalse(ProfileStorage.isSameFile("default", "other"));
    }

    @Test
    public void renameChangesTheFileOnDisk() {
        save("alpha");

        assertTrue(ProfileStorage.renameFile("alpha", "beta"));

        assertFalse(ProfileStorage.exists("alpha"));
        assertTrue(ProfileStorage.exists("beta"));
    }

    @Test
    public void renameCanChangeOnlyTheCapitalisation() {
        // Regression: renaming default to Default reported that the profile already existed, because on a
        // case-insensitive filesystem the target file is the source file.
        save("default");

        assertTrue(ProfileStorage.renameFile("default", "Default"));

        List<String> names = ProfileStorage.listProfileNames();
        assertEquals(1, names.size());
        assertEquals("Default", names.get(0));
    }

    @Test
    public void renameLeavesNoTemporaryFileBehind() {
        save("default");
        ProfileStorage.renameFile("default", "Default");

        for (File file : ProfileStorage.profilesDir()
            .listFiles()) {
            assertFalse(
                file.getName(),
                file.getName()
                    .endsWith(".tmp"));
        }
    }

    @Test
    public void renamingSomethingThatIsNotThereFails() {
        assertFalse(ProfileStorage.renameFile("missing", "other"));
    }

    @Test
    public void aProfileRoundTripsThroughDisk() {
        Profile written = Profile.empty("alpha");
        written.root.layout.slots = 12;
        ProfileStorage.saveProfile(written);

        Profile read = ProfileStorage.loadProfile("alpha");
        assertEquals("alpha", read.name);
        assertEquals(12, read.root.slotCount());
    }

    @Test
    public void loadingSomethingThatIsNotThereReturnsNothing() {
        assertEquals(null, ProfileStorage.loadProfile("missing"));
    }
}
