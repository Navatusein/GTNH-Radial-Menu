package com.navatusein.radialmenu.core.script;

/**
 * One stack, as much of it as a script has any business knowing.
 *
 * <p>
 * A registry name, a damage value, a count and the name the game prints - and nothing else. An {@code ItemStack} is a
 * Minecraft type with NBT hanging off it, and letting one across this line would put the whole item API inside the
 * sandbox and Minecraft inside {@code core}. What a script actually does with an item is count it, name it, and use
 * it as a menu icon, all of which these four answer.
 *
 * <p>
 * The id and the damage value are kept apart rather than joined into {@code "id:meta"}, because that is the form an
 * icon wants and the form a comparison wants is the id alone - a script that asked "do I have a pickaxe" should not
 * have to care which damage value this one happens to be on.
 */
public final class ScriptItem {

    /** Registry name, e.g. {@code minecraft:diamond_pickaxe}. */
    public final String id;

    public final int meta;

    public final int count;

    /** What the game calls it, already translated - which is what a menu entry should be labelled with. */
    public final String label;

    /** Where it sits in the inventory, counted from 1 as Lua counts. 0 when it is not from a slot. */
    public final int slot;

    public ScriptItem(String id, int meta, int count, String label, int slot) {
        this.id = id == null ? "" : id;
        this.meta = meta;
        this.count = count;
        this.label = label == null ? this.id : label;
        this.slot = slot;
    }
}
