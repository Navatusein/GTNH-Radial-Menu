package com.navatusein.radialmenu.client.gui.editor;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiScreen;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.client.profile.ProfileStorage;
import com.navatusein.radialmenu.config.RadialMenuConfig;
import com.navatusein.radialmenu.core.Colors;
import com.navatusein.radialmenu.core.model.MenuStyle;
import com.navatusein.radialmenu.core.model.Profile;

/**
 * Colours for a whole profile.
 *
 * <p>
 * The middle step of the chain: a menu falls back to its profile, and a profile to the mod's config. That is what
 * lets a profile have a look of its own without every submenu repeating it, and what "Inherit" on a submenu means.
 */
public class GuiProfileColors extends UiScreen {

    /**
     * The colours a profile can override, in the order they are shown.
     *
     * <p>
     * One description of a row rather than four copies of it: the screen grew a fourth colour and every block had to
     * be written out again, which is how a row ends up subtly unlike its neighbours.
     */
    private enum Swatch {

        RING("ring", true),
        HIGHLIGHT("highlight", true),
        ICON("icon", false),
        BORDER("border", true);

        private final String name;

        /** Whether the picker offers opacity. A tint multiplies a texture, so transparency would only dim it. */
        private final boolean alpha;

        Swatch(String name, boolean alpha) {
            this.name = name;
            this.alpha = alpha;
        }

        String labelKey() {
            return "radialmenu.profileColors." + name;
        }
    }

    private static final int ID_PICK_BASE = 10;
    private static final int ID_CLEAR_BASE = 20;

    private static final int CLEAR_WIDTH = 20;

    private final String profileName;

    private Profile profile;

    /** Overrides being edited, indexed by {@link Swatch}. Empty means the profile inherits that colour. */
    private final String[] colors = new String[Swatch.values().length];

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
        return 14 + Ui.STEP * (Swatch.values().length - 1) + Ui.ROW;
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
            colors[Swatch.ICON.ordinal()] = orEmpty(style == null ? null : style.iconColor);
            colors[Swatch.BORDER.ordinal()] = orEmpty(style == null ? null : style.borderColor);
        }

        int controlLeft = contentLeft() + Ui.LABEL_WIDTH + Ui.GAP;
        int clearLeft = contentRight() - CLEAR_WIDTH;
        int controlWidth = clearLeft - Ui.GAP - controlLeft;
        int y = contentTop() + 14;

        for (Swatch swatch : Swatch.values()) {
            int index = swatch.ordinal();

            this.buttonList
                .add(new GuiButton(ID_PICK_BASE + index, controlLeft, y, controlWidth, Ui.ROW, label(colors[index])));
            tooltip(ID_PICK_BASE + index, I18n.format(swatch.labelKey() + ".tip"));

            // One colour at a time: clearing all four to put a single one back was a poor trade.
            GuiButton clear = new GuiButton(ID_CLEAR_BASE + index, clearLeft, y, CLEAR_WIDTH, Ui.ROW, "x");
            clear.enabled = !colors[index].isEmpty();
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

    private String label(String value) {
        return value.isEmpty() ? I18n.format("radialmenu.profileColors.default") : value;
    }

    /** What the profile falls back to for a colour, which is where the picker should open. */
    private static String inherited(Swatch swatch) {
        switch (swatch) {
            case HIGHLIGHT:
                return RadialMenuConfig.highlightColor;
            case ICON:
                return RadialMenuConfig.iconColor;
            case BORDER:
                return RadialMenuConfig.borderColor;
            case RING:
            default:
                return RadialMenuConfig.ringColor;
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

    /** Opens the picker on what the profile actually shows, not on a blank, so editing starts from what is there. */
    private static String effective(String override, String inherited) {
        return String.format("#%08X", Integer.valueOf(Colors.over(override, Colors.parseArgb(inherited, 0xFF000000))));
    }

    private void save() {
        profile.style = MenuStyle.of(
            colors[Swatch.RING.ordinal()],
            colors[Swatch.HIGHLIGHT.ordinal()],
            colors[Swatch.ICON.ordinal()],
            colors[Swatch.BORDER.ordinal()]);
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

        int y = contentTop() + 14;
        for (Swatch swatch : Swatch.values()) {
            Ui.rowLabel(I18n.format(swatch.labelKey()), left, y);
            y += Ui.STEP;
        }
    }
}
