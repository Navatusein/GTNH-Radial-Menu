package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Mouse;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiList;
import com.navatusein.radialmenu.client.gui.ui.UiScreen;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.client.profile.ProfileStorage;

/**
 * Lists the profiles on disk and manages them.
 *
 * <p>
 * The list sits beside a column of actions that apply to whatever is selected, rather than repeating a row of buttons
 * per profile. With a dozen profiles that keeps the list readable, and it keeps the destructive action in one known
 * place instead of next to every row.
 */
public class GuiProfileManager extends UiScreen {

    private static final int ID_ACTIVATE = 1;
    private static final int ID_RULES = 2;
    private static final int ID_COLORS = 7;
    private static final int ID_NEW = 3;
    private static final int ID_RENAME = 4;
    private static final int ID_DUPLICATE = 5;
    private static final int ID_DELETE = 6;
    private static final int ID_BACKUPS = 8;

    private static final int BUTTON_COLUMN = 104;

    private final List<String> profiles = new ArrayList<>();

    private String selected;

    private UiList list;

    /** Kept across rebuilds, which make a fresh list, so selecting a row does not jump back to the top. */
    private int listScroll;

    /** Set when an operation fails, so a refused name or a failed delete is visible instead of silent. */
    private String errorKey;

    @Override
    protected String titleKey() {
        return "radialmenu.profiles.title";
    }

    @Override
    protected int panelWidth() {
        return 360;
    }

    /** A line for the error, reserved only while there is one - the list is worth more than a permanent blank. */
    @Override
    protected int footerHeight() {
        return errorKey == null ? 0 : 10;
    }

    @Override
    protected void buildControls() {
        profiles.clear();
        profiles.addAll(ProfileStorage.listProfileNames());
        if (selected == null || !profiles.contains(selected)) {
            selected = ProfileManager.activeName();
        }

        // The list and the column of actions are siblings, so both take the frame's edges rather than the content
        // column's. Measured from contentTop the box started four pixels below every other screen's frame while its
        // bottom matched, which reads as the whole list having slipped down.
        int listRight = viewportRight() - BUTTON_COLUMN - Ui.GAP;
        list = new UiList(viewportLeft(), viewportTop(), listRight, viewportBottom(), 13);
        list.scrollTo(listScroll, profiles.size());

        int x = listRight + Ui.GAP;
        int y = viewportTop();
        boolean has = profiles.contains(selected);
        boolean isActive = has && selected.equals(ProfileManager.activeName());

        y = addAction(ID_ACTIVATE, "radialmenu.profiles.activate", x, y, has && !isActive);
        y = addAction(ID_RULES, "radialmenu.profiles.rules", x, y, has);
        y = addAction(ID_COLORS, "radialmenu.profiles.colors", x, y, has);
        y += Ui.GAP;
        y = addAction(ID_NEW, "radialmenu.profiles.new", x, y, true);
        y = addAction(ID_RENAME, "radialmenu.profiles.rename", x, y, has);
        y = addAction(ID_DUPLICATE, "radialmenu.profiles.duplicate", x, y, has);
        y = addAction(ID_BACKUPS, "radialmenu.profiles.backups", x, y, has);
        y += Ui.GAP;
        addAction(ID_DELETE, "radialmenu.profiles.delete", x, y, has);

        addBottomBar("gui.done", null, null);
    }

    private int addAction(int id, String labelKey, int x, int y, boolean enabled) {
        GuiButton button = new GuiButton(id, x, y, BUTTON_COLUMN, Ui.ROW, I18n.format(labelKey));
        button.enabled = enabled;
        this.buttonList.add(button);
        tooltip(id, describe(labelKey + ".tip"));
        return y + Ui.STEP;
    }

    private static String describe(String key) {
        String translated = I18n.format(key);
        return translated.equals(key) ? null : translated;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        errorKey = null;

        switch (button.id) {
            case ID_PRIMARY:
                GuiStack.closeAll();
                return;

            case ID_ACTIVATE:
                if (!ProfileManager.switchTo(selected)) {
                    errorKey = "radialmenu.profiles.error.load";
                }
                requestRebuild();
                return;

            case ID_RULES:
                GuiStack.push(new GuiAutoBindRules(selected));
                return;

            case ID_COLORS:
                GuiStack.push(new GuiProfileColors(selected));
                return;

            case ID_NEW:
                GuiStack.push(new GuiTextPrompt("radialmenu.profiles.new", "", new GuiTextPrompt.Result() {

                    @Override
                    public String onConfirm(String value) {
                        if (!ProfileManager.create(value)) {
                            return "radialmenu.profiles.error.exists";
                        }
                        selected = ProfileStorage.sanitize(value);
                        return null;
                    }
                }));
                return;

            case ID_RENAME:
                final String renameFrom = selected;
                GuiStack.push(new GuiTextPrompt("radialmenu.profiles.rename", renameFrom, new GuiTextPrompt.Result() {

                    @Override
                    public String onConfirm(String value) {
                        if (!ProfileManager.rename(renameFrom, value)) {
                            return "radialmenu.profiles.error.exists";
                        }
                        selected = ProfileStorage.sanitize(value);
                        return null;
                    }
                }));
                return;

            case ID_DUPLICATE:
                final String copyFrom = selected;
                GuiStack.push(
                    new GuiTextPrompt("radialmenu.profiles.duplicate", copyFrom + "-copy", new GuiTextPrompt.Result() {

                        @Override
                        public String onConfirm(String value) {
                            if (!ProfileManager.duplicate(copyFrom, value)) {
                                return "radialmenu.profiles.error.exists";
                            }
                            selected = ProfileStorage.sanitize(value);
                            return null;
                        }
                    }));
                return;

            case ID_BACKUPS:
                GuiStack.push(new GuiProfileBackups(selected));
                return;

            case ID_DELETE:
                GuiStack.push(new GuiConfirm("radialmenu.profiles.confirmDelete", selected, new GuiConfirm.Result() {

                    @Override
                    public void onConfirmed() {
                        if (!ProfileManager.delete(selected)) {
                            errorKey = "radialmenu.profiles.error.delete";
                        }
                        selected = null;
                    }
                }));
                return;

            default:
                break;
        }
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        list.drawFrame();

        String activeName = ProfileManager.activeName();
        int hovered = list.itemAt(mouseX, mouseY, profiles.size());

        for (int row = 0; row < list.rowsVisible() && row + list.firstRow() < profiles.size(); row++) {
            int index = row + list.firstRow();
            String name = profiles.get(index);

            list.drawRowBackground(row, name.equals(selected), index == hovered);

            boolean active = name.equals(activeName);
            String label = Ui.fit(name, list.textWidth() - (active ? 42 : 0));
            this.fontRendererObj
                .drawString(label, list.textLeft(), list.rowTop(row) + 3, active ? Ui.TEXT_ACTIVE : Ui.TEXT);

            if (active) {
                String marker = I18n.format("radialmenu.profiles.activeMarker");
                this.fontRendererObj.drawString(
                    EnumChatFormatting.DARK_GREEN + marker,
                    list.right - 5 - this.fontRendererObj.getStringWidth(marker),
                    list.rowTop(row) + 3,
                    Ui.TEXT);
            }
        }

        list.drawScrollbar(profiles.size());

        if (profiles.isEmpty()) {
            this.drawCenteredString(
                this.fontRendererObj,
                EnumChatFormatting.GRAY + I18n.format("radialmenu.profiles.empty"),
                (list.left + list.right) / 2,
                list.top + 8,
                Ui.TEXT);
        }

        if (errorKey != null) {
            // Below the frame rather than over it: at a fixed offset from the panel bottom it landed on the list's
            // own border.
            this.drawCenteredString(
                this.fontRendererObj,
                EnumChatFormatting.RED + I18n.format(errorKey),
                this.width / 2,
                viewportBottom() + Ui.GAP,
                Ui.TEXT);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        list.scroll(Mouse.getEventDWheel(), profiles.size());
        listScroll = list.firstRow();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) {
            return;
        }
        int index = list.itemAt(mouseX, mouseY, profiles.size());
        if (index >= 0) {
            selected = profiles.get(index);
            errorKey = null;
            requestRebuild();
        }
    }

    @Override
    protected void onCancel() {
        GuiStack.closeAll();
    }
}
