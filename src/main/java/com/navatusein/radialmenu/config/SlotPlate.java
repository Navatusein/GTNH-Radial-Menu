package com.navatusein.radialmenu.config;

/**
 * What to draw behind an entry's icon.
 *
 * <p>
 * The art is vanilla's own - the inventory's slot cell and the hotbar's selection frame - rather than something of
 * ours. Partly because the mod cannot ship Mojang's files and would have to redraw them, and partly because a wheel
 * that borrows the hotbar's own plate reads as part of this version of the game rather than as a visitor from a
 * later one.
 */
public enum SlotPlate {

    /** Nothing behind the icon. */
    NONE,

    /** An inventory slot cell under every icon. */
    SLOT,

    /** The same cells, with the hotbar's selection frame around the one under the cursor. */
    HOTBAR,

    /**
     * Cells with that frame around every one.
     *
     * <p>
     * Nothing is singled out, which is the point: the wheel already says what is selected by filling the sector in,
     * and a plate that also says it twice over is one signal too many for some tastes.
     */
    SELECTED
}
