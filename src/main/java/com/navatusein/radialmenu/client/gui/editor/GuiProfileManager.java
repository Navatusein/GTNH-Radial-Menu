package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.client.profile.ProfileStorage;

/**
 * Lists the profiles on disk and manages them.
 *
 * <p>
 * Actions apply to the selected row rather than sitting on every row, which keeps the list readable when a player has
 * a dozen profiles and makes the destructive button easy to keep away from the rest.
 */
public class GuiProfileManager extends GuiScreen {

    private static final int ID_ACTIVATE = 1;
    private static final int ID_NEW = 2;
    private static final int ID_RENAME = 3;
    private static final int ID_DUPLICATE = 4;
    private static final int ID_DELETE = 5;
    private static final int ID_RULES = 6;
    private static final int ID_CLOSE = 7;

    private static final int ROW_HEIGHT = 14;
    private static final int LIST_TOP = 40;

    /** Three rows of 20px buttons with 4px gaps, plus a margin below the last one. */
    private static final int BUTTON_BLOCK_HEIGHT = 74;

    private final List<String> profiles = new ArrayList<>();

    private String selected;

    private int scrollRow;

    /** Set when an operation fails, so a refused name or a failed delete is visible instead of silent. */
    private String errorKey;

    /**
     * Button rebuilds wait for the next frame: GuiScreen.mouseClicked iterates buttonList by index and calls
     * actionPerformed from inside that loop, so swapping the list mid-click makes it walk the new buttons too.
     */
    private boolean rebuildPending;

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        reload();
    }

    private void reload() {
        profiles.clear();
        profiles.addAll(ProfileStorage.listProfileNames());
        if (selected == null || !profiles.contains(selected)) {
            selected = ProfileManager.activeName();
        }
        rebuildButtons();
    }

    private void rebuildButtons() {
        this.buttonList.clear();

        int left = this.width / 2 - 154;
        // Laid out upwards from the bottom edge: buttons are 20 tall, so each row sits 24 above the next.
        int bottom = this.height - BUTTON_BLOCK_HEIGHT;
        boolean hasSelection = selected != null && profiles.contains(selected);
        boolean selectionIsActive = hasSelection && selected.equals(ProfileManager.activeName());

        GuiButton activate = new GuiButton(
            ID_ACTIVATE,
            left,
            bottom,
            100,
            20,
            I18n.format("radialmenu.profiles.activate"));
        activate.enabled = hasSelection && !selectionIsActive;
        this.buttonList.add(activate);

        GuiButton rules = new GuiButton(
            ID_RULES,
            left + 104,
            bottom,
            100,
            20,
            I18n.format("radialmenu.profiles.rules"));
        rules.enabled = hasSelection;
        this.buttonList.add(rules);

        GuiButton duplicate = new GuiButton(
            ID_DUPLICATE,
            left + 208,
            bottom,
            100,
            20,
            I18n.format("radialmenu.profiles.duplicate"));
        duplicate.enabled = hasSelection;
        this.buttonList.add(duplicate);

        this.buttonList.add(new GuiButton(ID_NEW, left, bottom + 24, 100, 20, I18n.format("radialmenu.profiles.new")));

        GuiButton rename = new GuiButton(
            ID_RENAME,
            left + 104,
            bottom + 24,
            100,
            20,
            I18n.format("radialmenu.profiles.rename"));
        rename.enabled = hasSelection;
        this.buttonList.add(rename);

        GuiButton delete = new GuiButton(
            ID_DELETE,
            left + 208,
            bottom + 24,
            100,
            20,
            I18n.format("radialmenu.profiles.delete"));
        delete.enabled = hasSelection;
        this.buttonList.add(delete);

        this.buttonList
            .add(new GuiButton(ID_CLOSE, this.width / 2 - 100, bottom + 48, 200, 20, I18n.format("gui.done")));
    }

    private int rowsVisible() {
        return Math.max(1, (this.height - LIST_TOP - BUTTON_BLOCK_HEIGHT - 12) / ROW_HEIGHT);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        errorKey = null;

        switch (button.id) {
            case ID_ACTIVATE:
                if (!ProfileManager.switchTo(selected)) {
                    errorKey = "radialmenu.profiles.error.load";
                }
                rebuildPending = true;
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

            case ID_RULES:
                GuiStack.push(new GuiAutoBindRules(selected));
                return;

            case ID_CLOSE:
                GuiStack.closeAll();
                return;

            default:
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (rebuildPending) {
            rebuildPending = false;
            reload();
        }

        this.drawDefaultBackground();
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("radialmenu.profiles.title"),
            this.width / 2,
            12,
            0xFFFFFF);

        int left = this.width / 2 - 154;
        int rows = rowsVisible();
        String activeName = ProfileManager.activeName();

        for (int row = 0; row < rows && row + scrollRow < profiles.size(); row++) {
            String name = profiles.get(row + scrollRow);
            int y = LIST_TOP + row * ROW_HEIGHT;

            if (name.equals(selected)) {
                drawRect(left - 2, y - 2, left + 310, y + ROW_HEIGHT - 3, 0x60FFFFFF);
            }

            String label = name.equals(activeName)
                ? EnumChatFormatting.GREEN + name
                    + " "
                    + EnumChatFormatting.DARK_GREEN
                    + I18n.format("radialmenu.profiles.activeMarker")
                : name;
            this.fontRendererObj.drawString(label, left, y + 1, 0xFFFFFF);
        }

        if (profiles.size() > rows) {
            this.drawCenteredString(
                this.fontRendererObj,
                (scrollRow + 1) + "-" + Math.min(profiles.size(), scrollRow + rows) + " / " + profiles.size(),
                this.width / 2,
                this.height - BUTTON_BLOCK_HEIGHT - 10,
                0x808080);
        }

        if (errorKey != null) {
            this.drawCenteredString(
                this.fontRendererObj,
                EnumChatFormatting.RED + I18n.format(errorKey),
                this.width / 2,
                26,
                0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int maxScroll = Math.max(0, profiles.size() - rowsVisible());
            scrollRow = Math.max(0, Math.min(maxScroll, scrollRow + (wheel > 0 ? -1 : 1)));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) {
            return;
        }
        int left = this.width / 2 - 154;
        int rows = rowsVisible();
        for (int row = 0; row < rows && row + scrollRow < profiles.size(); row++) {
            int y = LIST_TOP + row * ROW_HEIGHT;
            if (mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 3 && mouseX >= left - 2 && mouseX <= left + 310) {
                selected = profiles.get(row + scrollRow);
                errorKey = null;
                rebuildPending = true;
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            GuiStack.closeAll();
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
