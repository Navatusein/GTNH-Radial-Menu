package com.navatusein.radialmenu.core.model;

import com.navatusein.radialmenu.core.Colors;

/**
 * Works out what colour each part of the wheel actually is.
 *
 * <p>
 * The chain is mod config, then profile, then the menu on screen, each level naming only what it wants to change.
 * That is what lets a profile have a look of its own without every submenu repeating it, and one submenu of it read
 * differently from the rest.
 *
 * <p>
 * Accents do not appear here. An accent is how a player fills these colours in, and the editor does that at the
 * moment they pick one - by the time a menu is drawn there is nothing left to derive.
 *
 * <p>
 * Pure arithmetic on strings and ints, so it is unit tested without a game.
 */
public final class StyleResolver {

    private StyleResolver() {}

    public static WheelColors resolve(WheelColors defaults, MenuStyle profile, MenuStyle menu) {
        return apply(apply(defaults, profile), menu);
    }

    private static WheelColors apply(WheelColors base, MenuStyle style) {
        if (style == null) {
            return base;
        }
        return new WheelColors(
            Colors.over(style.ringColor, base.ring),
            Colors.over(style.highlightColor, base.highlight),
            Colors.over(style.borderColor, base.border),
            Colors.over(style.highlightBorderColor, base.highlightBorder),
            Colors.over(style.backgroundColor, base.background),
            Colors.over(style.iconColor, base.icon));
    }
}
