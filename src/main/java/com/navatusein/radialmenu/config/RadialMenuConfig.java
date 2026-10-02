package com.navatusein.radialmenu.config;

import static com.navatusein.radialmenu.RadialMenuMod.MODID;

import com.gtnewhorizon.gtnhlib.config.Config;

/**
 * How the wheel behaves: what opens it, what chooses an entry, and what the game is allowed to do meanwhile.
 *
 * <p>
 * One of four categories, and the split is by what a setting is about rather than by what it touches. A single list
 * of thirty settings is a list nobody reads to the end, and these four are asked at different moments: how it works,
 * what shape it is, what colour it is, and how an accent is turned into those colours.
 *
 * <p>
 * All four write to the same file. They live in the normal {@code config} folder so they can be edited from the mod
 * list screen like everyone else's; the menus themselves are separate files under the game folder, because they are
 * structured data a player will want to copy between installations.
 */
@Config(modid = MODID, configSubDirectory = "RadialMenu", filename = "general", category = "general")
@Config.LangKey("radialmenu.config.general")
public class RadialMenuConfig {

    @Config.Comment("Execute the highlighted entry when the wheel key is released. Disable to require a click instead.")
    @Config.DefaultBoolean(true)
    @Config.Order(1)
    public static boolean releaseToSelect;

    @Config.Comment("Choose the entry with the mouse wheel instead of by pointing at it. The cursor stops mattering, "
        + "which suits a wheel opened with a mouse button.")
    @Config.DefaultBoolean(false)
    @Config.Order(2)
    public static boolean scrollToSelect;

    @Config.Comment("Open the slot editor by right-clicking a slot, instead of shift-clicking it.")
    @Config.DefaultBoolean(false)
    @Config.Order(3)
    public static boolean rightClickToEdit;

    @Config.Comment("Keep feeding keyboard and mouse input to the game while the wheel is open, so you can keep moving.")
    @Config.DefaultBoolean(true)
    @Config.Order(4)
    public static boolean allowInputWhileOpen;

    @Config.Comment("Move the mouse cursor to the middle of the screen when the wheel opens.")
    @Config.DefaultBoolean(true)
    @Config.Order(5)
    public static boolean centerCursorOnOpen;

    @Config.Comment("While an action holds an unbound keybinding down, lend it a key code no keyboard can produce. "
        + "Some mods refuse to look at a binding whose key code is 0 - JourneyMap's zoom is one - so without this "
        + "they cannot be driven from the menu at all. Turn it off if a mod misbehaves around it.")
    @Config.DefaultBoolean(true)
    @Config.Order(6)
    public static boolean lendKeyCodeToUnbound;

    @Config.Comment("Allow entries to run Lua scripts. A script is stored in the profile, so a profile copied from "
        + "somebody else brings theirs with it - it can send anything to the server that you could type. Turn this "
        + "off to refuse to run them at all.")
    @Config.DefaultBoolean(true)
    @Config.Order(7)
    public static boolean enableScripts;

    @Config.Comment("Log what the game believes about key and sneak state on every injected press: our own byte, "
        + "LWJGL's answer, the binding's answer, and what a mod asking \"is the player sneaking\" would get. For "
        + "working out why one particular mod does not react. Noisy; leave it off unless you are chasing something.")
    @Config.DefaultBoolean(false)
    @Config.Order(8)
    public static boolean logKeyState;
}
