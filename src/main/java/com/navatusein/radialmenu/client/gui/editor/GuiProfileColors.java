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
 * lets a profile have a look of its own without every submenu repeating it, and what "Inherit" on a submenu now
 * means.
 */
public class GuiProfileColors extends UiScreen {

    private static final int ID_RING = 1;
    private static final int ID_HIGHLIGHT = 2;
    private static final int ID_ICON = 3;
    private static final int ID_RESET = 4;

    private final String profileName;

    private Profile profile;

    private String ringColor;
    private String highlightColor;
    private String iconColor;

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
        return 14 + Ui.STEP * 3 + Ui.ROW;
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
            ringColor = style == null || style.ringColor == null ? "" : style.ringColor;
            highlightColor = style == null || style.highlightColor == null ? "" : style.highlightColor;
            iconColor = style == null || style.iconColor == null ? "" : style.iconColor;
        }

        int controlLeft = contentLeft() + Ui.LABEL_WIDTH + Ui.GAP;
        int controlWidth = contentRight() - controlLeft;
        int y = contentTop() + 14;

        this.buttonList.add(new GuiButton(ID_RING, controlLeft, y, controlWidth, Ui.ROW, label(ringColor)));
        tooltip(ID_RING, I18n.format("radialmenu.profileColors.ring.tip"));
        y += Ui.STEP;

        this.buttonList.add(new GuiButton(ID_HIGHLIGHT, controlLeft, y, controlWidth, Ui.ROW, label(highlightColor)));
        tooltip(ID_HIGHLIGHT, I18n.format("radialmenu.profileColors.highlight.tip"));
        y += Ui.STEP;

        this.buttonList.add(new GuiButton(ID_ICON, controlLeft, y, controlWidth, Ui.ROW, label(iconColor)));
        tooltip(ID_ICON, I18n.format("radialmenu.profileColors.icon.tip"));
        y += Ui.STEP;

        GuiButton reset = new GuiButton(
            ID_RESET,
            controlLeft,
            y,
            controlWidth,
            Ui.ROW,
            I18n.format("radialmenu.profileColors.reset"));
        reset.enabled = !ringColor.isEmpty() || !highlightColor.isEmpty() || !iconColor.isEmpty();
        this.buttonList.add(reset);
        tooltip(ID_RESET, I18n.format("radialmenu.profileColors.reset.tip"));

        addBottomBar("radialmenu.editor.save", null, "gui.cancel");
    }

    private String label(String value) {
        return value == null || value.isEmpty() ? I18n.format("radialmenu.profileColors.default") : value;
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
            case ID_RESET:
                // Clearing both is the reset: with nothing of its own, the profile shows the config's colours.
                ringColor = "";
                highlightColor = "";
                iconColor = "";
                requestRebuild();
                return;
            case ID_RING:
                GuiStack.push(
                    new GuiColorPicker(
                        effective(ringColor, RadialMenuConfig.ringColor),
                        true,
                        new GuiColorPicker.Result() {

                            @Override
                            public void onColorPicked(String hex) {
                                ringColor = hex;
                                requestRebuild();
                            }
                        }));
                return;
            case ID_HIGHLIGHT:
                GuiStack.push(
                    new GuiColorPicker(
                        effective(highlightColor, RadialMenuConfig.highlightColor),
                        true,
                        new GuiColorPicker.Result() {

                            @Override
                            public void onColorPicked(String hex) {
                                highlightColor = hex;
                                requestRebuild();
                            }
                        }));
                return;
            case ID_ICON:
                // No opacity here: a tint multiplies a texture, so transparency would only dim it.
                GuiStack.push(
                    new GuiColorPicker(
                        effective(iconColor, RadialMenuConfig.iconColor),
                        false,
                        new GuiColorPicker.Result() {

                            @Override
                            public void onColorPicked(String hex) {
                                iconColor = hex;
                                requestRebuild();
                            }
                        }));
                return;
            default:
                break;
        }
    }

    /** Opens the picker on what the profile actually shows, not on a blank, so editing starts from what is there. */
    private static String effective(String override, String inherited) {
        return String.format("#%08X", Integer.valueOf(Colors.over(override, Colors.parseArgb(inherited, 0xFF000000))));
    }

    private void save() {
        profile.style = MenuStyle.of(ringColor, highlightColor, iconColor);
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
        Ui.rowLabel(I18n.format("radialmenu.profileColors.ring"), left, y);
        y += Ui.STEP;
        Ui.rowLabel(I18n.format("radialmenu.profileColors.highlight"), left, y);
        y += Ui.STEP;
        Ui.rowLabel(I18n.format("radialmenu.profileColors.icon"), left, y);
    }
}
