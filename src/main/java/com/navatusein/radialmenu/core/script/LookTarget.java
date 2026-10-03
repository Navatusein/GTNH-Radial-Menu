package com.navatusein.radialmenu.core.script;

/**
 * What the player's crosshair is on.
 *
 * <p>
 * A block or an entity, flattened into one shape with a {@link #kind} saying which - a script asking "what am I
 * looking at" wants one answer and a branch, not two calls it has to try in order.
 *
 * <p>
 * Only what the client already has on screen. This is the mod's whole bargain: everything it knows is something the
 * player could have read off their own display, so a server sees nothing it could not have seen anyway.
 */
public final class LookTarget {

    public static final String BLOCK = "block";

    public static final String ENTITY = "entity";

    public final String kind;

    /** The block's registry name, or the entity's type name. */
    public final String id;

    /** What the game calls it: the block's item name, or the entity's display name. */
    public final String label;

    /** The block's damage value. Zero for an entity. */
    public final int meta;

    /** Block position, or the entity's own, floored. */
    public final int x;

    public final int y;

    public final int z;

    public LookTarget(String kind, String id, String label, int meta, int x, int y, int z) {
        this.kind = kind;
        this.id = id == null ? "" : id;
        this.label = label == null ? this.id : label;
        this.meta = meta;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static LookTarget block(String id, String label, int meta, int x, int y, int z) {
        return new LookTarget(BLOCK, id, label, meta, x, y, z);
    }

    public static LookTarget entity(String id, String label, int x, int y, int z) {
        return new LookTarget(ENTITY, id, label, 0, x, y, z);
    }
}
