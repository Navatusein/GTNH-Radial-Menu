package com.navatusein.radialmenu.client.gui;

import com.navatusein.radialmenu.config.AnimationConfig;
import com.navatusein.radialmenu.core.animation.RevealTiming;
import com.navatusein.radialmenu.core.model.SlotLayout;

/**
 * The wheel's clock.
 *
 * <p>
 * Driven by {@link System#nanoTime()} rather than by ticks: a GUI is drawn as fast as the machine manages, so a
 * wheel that opens over twelve frames takes a quarter of a second on one machine and two seconds on another. The
 * timing arithmetic itself lives in {@link RevealTiming}, where it is unit tested; this keeps the state.
 */
public class WheelAnimator {

    /** One more than a wheel can have sectors, because a dynamic one grows a spare while editing. */
    private static final int MAX_SLOTS = SlotLayout.MAX_SLOTS + 1;

    /** How far each sector is towards being pushed out, 0 to 1. Its own value per sector, so they cross smoothly. */
    private final float[] push = new float[MAX_SLOTS];

    private long lastNano;

    private double revealElapsed;

    /**
     * Starts the animation over.
     *
     * <p>
     * Called when the wheel opens and again on every step into or out of a submenu: a new menu is a new wheel
     * arriving, and one that appeared fully drawn while its neighbours animated would look like a mistake.
     */
    public void reset() {
        lastNano = 0L;
        revealElapsed = 0.0;
        for (int i = 0; i < push.length; i++) {
            push[i] = 0f;
        }
    }

    /** Moves the clock on by however long the last frame took, and settles the hovered sector towards its target. */
    public void advance(int slotCount, int hoveredSlot) {
        long now = System.nanoTime();
        if (lastNano == 0L) {
            lastNano = now;
        }
        // Capped: a frame that took a tenth of a second is a stutter, and letting it through makes the wheel jump.
        double delta = Math.min((now - lastNano) / 1.0e9, 0.1);
        lastNano = now;
        revealElapsed += delta;

        double duration = AnimationConfig.hoverDurationMs / 1000.0;
        double step = duration <= 0.0 ? 1.0 : delta / duration;
        for (int slot = 0; slot < push.length; slot++) {
            float target = slot == hoveredSlot && slot < slotCount ? 1f : 0f;
            push[slot] = (float) RevealTiming.approach(push[slot], target, step);
        }
    }

    /** How far along this sector's arrival is, eased - 1 once it is fully there. */
    public float reveal(int slot, int slotCount) {
        return (float) RevealTiming.easeOutQuint(
            RevealTiming.slotProgress(
                AnimationConfig.revealAnimation,
                slot,
                slotCount,
                revealElapsed,
                AnimationConfig.revealDurationMs / 1000.0));
    }

    /** How far out this sector is pushed, in GUI pixels. */
    public float push(int slot) {
        if (slot < 0 || slot >= push.length) {
            return 0f;
        }
        return push[slot] * AnimationConfig.hoverPush;
    }
}
