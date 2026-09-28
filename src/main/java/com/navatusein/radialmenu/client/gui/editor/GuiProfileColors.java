package com.navatusein.radialmenu.client.gui.editor;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiColorButton;
import com.navatusein.radialmenu.client.gui.ui.UiScreen;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.client.profile.ProfileStorage;
import com.navatusein.radialmenu.config.ColorConfig;
import com.navatusein.radialmenu.core.Colors;
import com.navatusein.radialmenu.core.action.ActionTypes;
import com.navatusein.radialmenu.core.model.MenuStyle;
import com.navatusein.radialmenu.core.model.Profile;
import com.navatusein.radialmenu.core.model.WheelColors;

/**
 * Colours for a whole profile.
 *
 * <p>
 * The middle step of the chain: a menu falls back to its profile, and a profile to the mod's config. That is what
 * lets a profile have a look of its own without every submenu repeating it, and what "Inherit" on a submenu means.
 *
 * <p>
 * The accent above them is a tool, not a seventh colour. Picking one fills the rows in from the mod's coefficients
 * and is then done with - nothing about it is stored, and what the profile carries afterwards is the colours
 * themselves, ready to be adjusted one at a time.
 */
public class GuiProfileColors extends UiScreen {

    /**
     * The colours a profile can override, in the order they are shown.
     *
     * <p>
     * Paired: the ring and its outline, then the highlighted sector and its outline. The wheel has two states and
     * each is a fill with a line around it, so the rows that change together sit together - listed in the order they
     * happen to have been added, the same four colours read as a list of unrelated settings.
     *
     * <p>
     * One description of a row rather than six copies of it: the screen grew a fourth colour and every block had to
     * be written out again, which is how a row ends up subtly unlike its neighbours.
     */
    private enum Swatch {

        RING("ring", true, ActionTypes.PARAM_RING_COLOR),
        BORDER("border", true, ActionTypes.PARAM_BORDER_COLOR),
        HIGHLIGHT("highlight", true, ActionTypes.PARAM_HIGHLIGHT_COLOR),
        HIGHLIGHT_BORDER("highlightBorder", true, ActionTypes.PARAM_HIGHLIGHT_BORDER_COLOR),
        BACKGROUND("background", true, ActionTypes.PARAM_BACKGROUND_COLOR),
        ICON("icon", false, ActionTypes.PARAM_ICON_COLOR);

        private final String name;

        /** Whether the picker offers opacity. A tint multiplies a texture, so transparency would only dim it. */
        private final boolean alpha;

        /**
         * What this colour is called everywhere else.
         *
         * <p>
         * The same names the submenu fields use, so which parts of the wheel are switched off is decided in one
         * place for both screens rather than once per screen.
         */
        private final String key;

        Swatch(String name, boolean alpha, String key) {
            this.name = name;
            this.alpha = alpha;
            this.key = key;
        }

        String labelKey() {
            return "radialmenu.profileColors." + name;
        }
    }

    private static final int ID_PICK_BASE = 10;
    private static final int ID_CLEAR_BASE = 20;
    private static final int ID_ACCENT = 30;

    private static final int CLEAR_WIDTH = 20;

    private final String profileName;

    private Profile profile;

    /** Overrides being edited, indexed by {@link Swatch}. Empty means the profile inherits that colour. */
    private final String[] colors = new String[Swatch.values().length];

    private int accentTop;

    public GuiProfileColors(String profileName) {
        this.profileName = profileName;
    }

    @Override
    protected String titleKey() {
        return "radialmenu.profileColors.title";
    }

    @Override
    protected Object[] titleArgs() {
        return new Object[] { profileName };
    }

    @Override
    protected int panelWidth() {
        return 320;
    }

    @Override
    protected int panelHeightHint() {
        // The accent, the gap that keeps it apart from what it fills in, then the colours themselves.
        return 14 + Ui.STEP + Ui.GAP + Ui.STEP * (Swatch.values().length - 1) + Ui.ROW;
    }

    @Override
    protected void buildControls() {
        if (profile == null) {
            profile = ProfileStorage.loadProfile(profileName);
            if (profile == null) {
                GuiStack.pop();
                return;
            }
            MenuStyle style = profile.style;
            colors[Swatch.RING.ordinal()] = orEmpty(style == null ? null : style.ringColor);
            colors[Swatch.HIGHLIGHT.ordinal()] = orEmpty(style == null ? null : style.highlightColor);
            colors[Swatch.BORDER.ordinal()] = orEmpty(style == null ? null : style.borderColor);
            colors[Swatch.HIGHLIGHT_BORDER.ordinal()] = orEmpty(style == null ? null : style.highlightBorderColor);
            colors[Swatch.BACKGROUND.ordinal()] = orEmpty(style == null ? null : style.backgroundColor);
            colors[Swatch.ICON.ordinal()] = orEmpty(style == null ? null : style.iconColor);
        }

        int controlLeft = contentLeft() + Ui.LABEL_WIDTH + Ui.GAP;
        int clearLeft = contentRight() - CLEAR_WIDTH;
        int controlWidth = clearLeft - Ui.GAP - controlLeft;
        int y = contentTop() + 14;

        // The accent sits with the colours it writes rather than under them, because it is read as "fill these in"
        // and then followed downwards. The gap below is what keeps it from being read as one of them.
        accentTop = y;
        this.buttonList.add(
            new GuiButton(
                ID_ACCENT,
                controlLeft,
                y,
                controlWidth,
                Ui.ROW,
                I18n.format("radialmenu.editor.pickAccent")));
        tooltip(ID_ACCENT, I18n.format("radialmenu.profileColors.accent.tip"));

        y += Ui.STEP + Ui.GAP;

        for (final Swatch swatch : Swatch.values()) {
            final int index = swatch.ordinal();
            boolean editable = FieldControls.colorEnabled(swatch.key);

            UiColorButton pick = new UiColorButton(
                ID_PICK_BASE + index,
                controlLeft,
                y,
                controlWidth,
                new UiColorButton.Value() {

                    @Override
                    public String get() {
                        return colors[index];
                    }
                },
                Colors.parseArgb(inherited(swatch), 0xFF000000),
                I18n.format("radialmenu.profileColors.default"));
            // Shown but not editable when its part of the wheel is switched off in the config. The value stays, and
            // an accent still writes into it, so turning that part back on brings back the colour chosen for it.
            pick.enabled = editable;
            this.buttonList.add(pick);
            tooltip(
                ID_PICK_BASE + index,
                editable ? I18n.format(swatch.labelKey() + ".tip") : FieldControls.colorOffTip(swatch.key));

            // One colour at a time: clearing all of them to put a single one back was a poor trade.
            GuiButton clear = new GuiButton(ID_CLEAR_BASE + index, clearLeft, y, CLEAR_WIDTH, Ui.ROW, "x");
            clear.enabled = editable && !colors[index].isEmpty();
            this.buttonList.add(clear);
            tooltip(ID_CLEAR_BASE + index, I18n.format("radialmenu.profileColors.clear.tip"));

            y += Ui.STEP;
        }

        // The reset used to sit in the column of values with no label beside it, where it read as a fifth colour.
        // It is a decision about the screen as a whole, so it belongs with the others.
        addBottomBar("radialmenu.editor.save", "radialmenu.profileColors.reset", "gui.cancel");
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /** What the profile falls back to for a colour, which is where the picker should open. */
    private static String inherited(Swatch swatch) {
        switch (swatch) {
            case HIGHLIGHT:
                return ColorConfig.highlightColor;
            case ICON:
                return ColorConfig.iconColor;
            case BORDER:
                return ColorConfig.borderColor;
            case HIGHLIGHT_BORDER:
                return ColorConfig.highlightBorderColor;
            case BACKGROUND:
                return ColorConfig.backgroundColor;
            case RING:
            default:
                return ColorConfig.ringColor;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case ID_PRIMARY:
                save();
                return;
            case ID_SECONDARY:
                onCancel();
                return;
            case ID_DANGER:
                // With nothing of its own, the profile shows the config's colours - that is the whole reset.
                for (int i = 0; i < colors.length; i++) {
                    colors[i] = "";
                }
                requestRebuild();
                return;
            case ID_ACCENT:
                pickAccent();
                return;
            default:
                break;
        }

        Swatch[] swatches = Swatch.values();

        int clearIndex = button.id - ID_CLEAR_BASE;
        if (clearIndex >= 0 && clearIndex < swatches.length) {
            colors[clearIndex] = "";
            requestRebuild();
            return;
        }

        int pickIndex = button.id - ID_PICK_BASE;
        if (pickIndex >= 0 && pickIndex < swatches.length) {
            pick(swatches[pickIndex]);
        }
    }

    private void pick(Swatch swatch) {
        final int index = swatch.ordinal();
        GuiStack.push(
            new GuiColorPicker(effective(colors[index], inherited(swatch)), swatch.alpha, new GuiColorPicker.Result() {

                @Override
                public void onColorPicked(String hex) {
                    colors[index] = hex;
                    requestRebuild();
                }
            }));
    }

    /**
     * Picks one colour and writes the whole set from it.
     *
     * <p>
     * Every row, including ones already chosen: asking for an accent is asking for the set it makes, and a row left
     * behind because it had been set earlier would be the one that no longer matches. The icon tint is not in the
     * set - an icon that changes hue with the ring stops saying what it is.
     */
    private void pickAccent() {
        GuiStack.push(new GuiColorPicker(accentStart(), false, new GuiColorPicker.Result() {

            @Override
            public void onColorPicked(String hex) {
                WheelColors derived = FieldControls.derive(hex);
                colors[Swatch.RING.ordinal()] = hex(derived.ring);
                colors[Swatch.HIGHLIGHT.ordinal()] = hex(derived.highlight);
                colors[Swatch.BORDER.ordinal()] = hex(derived.border);
                colors[Swatch.HIGHLIGHT_BORDER.ordinal()] = hex(derived.highlightBorder);
                colors[Swatch.BACKGROUND.ordinal()] = hex(derived.background);
                requestRebuild();
            }
        }));
    }

    /** Where the accent picker opens: the profile's highlight, which is the colour its look is named by. */
    private String accentStart() {
        String highlight = colors[Swatch.HIGHLIGHT.ordinal()];
        int shown = Colors.over(highlight, Colors.parseArgb(ColorConfig.highlightColor, 0xFF4A90D9));
        return String.format("#%06X", Integer.valueOf(shown & 0x00FFFFFF));
    }

    private static String hex(int argb) {
        return String.format("#%08X", Integer.valueOf(argb));
    }

    /** Opens the picker on what the profile actually shows, not on a blank, so editing starts from what is there. */
    private static String effective(String override, String inherited) {
        return String.format("#%08X", Integer.valueOf(Colors.over(override, Colors.parseArgb(inherited, 0xFF000000))));
    }

    private void save() {
        profile.style = MenuStyle.of(
            colors[Swatch.RING.ordinal()],
            colors[Swatch.HIGHLIGHT.ordinal()],
            colors[Swatch.ICON.ordinal()],
            colors[Swatch.BORDER.ordinal()],
            colors[Swatch.HIGHLIGHT_BORDER.ordinal()],
            colors[Swatch.BACKGROUND.ordinal()]);
        ProfileStorage.saveProfile(profile);

        // The edited profile may be the one loaded in memory; reload so the wheel shows it straight away.
        if (profileName.equals(ProfileManager.activeName())) {
            ProfileManager.switchTo(profileName);
        }
        GuiStack.pop();
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        int left = contentLeft();
        Ui.sectionHeader(I18n.format("radialmenu.profileColors.section"), left, contentTop(), contentRight());

        Ui.rowLabel(I18n.format("radialmenu.profileColors.accent"), left, accentTop);

        int y = accentTop + Ui.STEP + Ui.GAP;
        for (Swatch swatch : Swatch.values()) {
            Ui.rowLabel(I18n.format(swatch.labelKey()), left, y);
            y += Ui.STEP;
        }
    }
}
