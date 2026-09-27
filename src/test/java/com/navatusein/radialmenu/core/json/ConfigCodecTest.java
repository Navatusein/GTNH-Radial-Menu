package com.navatusein.radialmenu.core.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.model.IconSpec;
import com.navatusein.radialmenu.core.model.MenuNode;
import com.navatusein.radialmenu.core.model.Profile;
import com.navatusein.radialmenu.core.model.ProfileBinding;
import com.navatusein.radialmenu.core.model.SlotLayout;

public class ConfigCodecTest {

    private static Profile sampleProfile() {
        Profile profile = Profile.empty("sample");
        profile.bindings.add(ProfileBinding.server("gtnh.example.com"));

        MenuNode leaf = MenuNode.leaf(
            "Flight",
            IconSpec.sprite("phosphor:wings", "#7FD4FF"),
            new ActionSpec("keybind").set("binding", "key.toggleFlight")
                .set("mode", "toggle"));
        leaf.keepOpen = true;

        MenuNode submenu = MenuNode.category("Tools", IconSpec.item("minecraft:iron_pickaxe", 0), SlotLayout.dynamic());
        submenu
            .setChildAt(0, MenuNode.leaf("Inventory", null, new ActionSpec("keybind").set("binding", "key.inventory")));

        profile.root.setChildAt(0, leaf);
        profile.root.setChildAt(3, submenu);
        profile.root.ensureSlotCapacity();
        return profile;
    }

    @Test
    public void profileSurvivesARoundTrip() {
        Profile written = sampleProfile();
        Profile read = ConfigCodec.readProfile(ConfigCodec.writeProfile(written));

        assertEquals("sample", read.name);
        assertEquals(Profile.CURRENT_FORMAT_VERSION, read.formatVersion);
        assertEquals(written.root.slotCount(), read.root.slotCount());

        MenuNode leaf = read.root.childAt(0);
        assertNotNull(leaf);
        assertEquals("Flight", leaf.title);
        assertTrue(leaf.keepOpen);
        assertEquals("toggle", leaf.action.getString("mode", null));
        assertEquals(IconSpec.Kind.SPRITE, leaf.icon.kind);
        assertEquals(0x7FD4FF, leaf.icon.rgbOrWhite());

        MenuNode submenu = read.root.childAt(3);
        assertNotNull(submenu);
        assertTrue(submenu.isCategory());
        assertEquals("Inventory", submenu.childAt(0).title);
    }

    @Test
    public void emptySlotsSurviveAsNulls() {
        // Null array elements are what make a fixed wheel keep its angles; if Gson dropped them every entry would
        // shift by one on the next load.
        Profile read = ConfigCodec.readProfile(ConfigCodec.writeProfile(sampleProfile()));
        assertNull(read.root.childAt(1));
        assertNull(read.root.childAt(2));
        assertNotNull(read.root.childAt(3));
    }

    @Test
    public void enumsAreWrittenInLowerCase() {
        String json = ConfigCodec.writeProfile(sampleProfile());
        assertTrue(json.contains("\"fixed\""));
        assertTrue(json.contains("\"server\""));
        assertFalse(json.contains("\"FIXED\""));
    }

    @Test
    public void enumsAreReadCaseInsensitively() {
        // Gson 2.2.4 ignores @SerializedName on enum constants, so casing is handled by an adapter - and a file
        // edited by hand with the old upper-case spelling still has to load.
        String json = "{\"name\":\"x\",\"root\":{\"layout\":{\"mode\":\"FIXED\",\"slots\":4},\"children\":[]}}";
        Profile read = ConfigCodec.readProfile(json);
        assertEquals(SlotLayout.Mode.FIXED, read.root.layout.mode);
        assertEquals(4, read.root.slotCount());
    }

    @Test
    public void unknownEnumValueFallsBackInsteadOfThrowing() {
        String json = "{\"name\":\"x\",\"root\":{\"layout\":{\"mode\":\"spiral\",\"slots\":4},\"children\":[]}}";
        Profile read = ConfigCodec.readProfile(json);
        assertEquals(SlotLayout.Mode.DYNAMIC, read.root.layout.mode);
    }

    @Test
    public void chainStepsRoundTripEvenWithoutAnExecutor() {
        // steps is parsed from day one so that adding chains later cannot invalidate existing files.
        ActionSpec chain = new ActionSpec("sequence");
        chain.stepsOrEmpty()
            .add(new ActionSpec("keybind").set("binding", "key.inventory"));
        chain.stepsOrEmpty()
            .add(new ActionSpec("command").set("command", "/home"));

        Profile profile = Profile.empty("chained");
        profile.root.setChildAt(0, MenuNode.leaf("Chain", null, chain));

        Profile read = ConfigCodec.readProfile(ConfigCodec.writeProfile(profile));
        ActionSpec readChain = read.root.childAt(0).action;

        assertEquals("sequence", readChain.type);
        assertEquals(2, readChain.steps.size());
        assertEquals(
            "/home",
            readChain.steps.get(1)
                .getString("command", null));
    }

    @Test
    public void settingsRoundTrip() {
        Settings settings = new Settings();
        settings.activeProfile = "gtnh-main";

        Settings read = ConfigCodec.readSettings(new java.io.StringReader(ConfigCodec.writeSettings(settings)));

        assertEquals("gtnh-main", read.activeProfile);
    }

    @Test
    public void blankSettingsFallBackToTheDefaultProfile() {
        Settings read = ConfigCodec.readSettings(new java.io.StringReader("{\"activeProfile\":\"  \"}"));
        assertEquals(Settings.DEFAULT_PROFILE, read.activeProfile);
    }
}
