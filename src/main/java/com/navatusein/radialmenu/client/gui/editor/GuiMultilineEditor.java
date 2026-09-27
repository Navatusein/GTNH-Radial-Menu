package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.navatusein.radialmenu.client.gui.GuiStack;

/**
 * Edits a list of lines - in practice, the commands a menu entry sends.
 *
 * <p>
 * 1.7.10 has no multi-line text field, so this is a column of ordinary ones with a remove button each. Enter on the
 * last line adds another, which is what makes typing a handful of commands bearable.
 */
public class GuiMultilineEditor extends GuiScreen {

    public interface Result {

        /** @param text the lines joined by newlines, in the order shown */
        void onLinesEdited(String text);
    }

    private static final int ID_ADD = 1;
    private static final int ID_DONE = 2;
    private static final int ID_CANCEL = 3;
    private static final int ID_REMOVE_BASE = 100;

    private static final int ROW_HEIGHT = 20;
    private static final int LIST_TOP = 40;
    private static final int FIELD_WIDTH = 280;

    private final String titleKey;
    private final Result result;

    private final List<String> lines = new ArrayList<>();
    private final List<GuiTextField> fields = new ArrayList<>();

    private int scrollRow;

    /** Rebuilding inside actionPerformed would make the click loop walk the new buttons; see CLAUDE.md. */
    private boolean rebuildPending;

    public GuiMultilineEditor(String titleKey, String initialText, Result result) {
        this.titleKey = titleKey;
        this.result = result;

        if (initialText != null) {
            for (String line : initialText.split("\\r?\\n")) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        rebuild();
    }

    private int rowsVisible() {
        return Math.max(1, (this.height - LIST_TOP - 60) / ROW_HEIGHT);
    }

    private int listLeft() {
        return this.width / 2 - (FIELD_WIDTH + 24) / 2;
    }

    private void rebuild() {
        this.buttonList.clear();
        fields.clear();

        int left = listLeft();
        int rows = rowsVisible();

        for (int row = 0; row < rows && row + scrollRow < lines.size(); row++) {
            int index = row + scrollRow;
            int y = LIST_TOP + row * ROW_HEIGHT;

            GuiTextField field = new GuiTextField(this.fontRendererObj, left, y + 2, FIELD_WIDTH, 16);
            field.setMaxStringLength(256);
            field.setText(lines.get(index));
            fields.add(field);

            this.buttonList.add(new GuiButton(ID_REMOVE_BASE + row, left + FIELD_WIDTH + 4, y, 20, 18, "X"));
        }

        if (!fields.isEmpty()) {
            fields.get(fields.size() - 1)
                .setFocused(true);
        }

        int bottom = this.height - 50;
        this.buttonList.add(new GuiButton(ID_ADD, left, bottom, 120, 20, I18n.format("radialmenu.lines.add")));
        this.buttonList
            .add(new GuiButton(ID_DONE, this.width / 2 - 100, this.height - 24, 98, 20, I18n.format("gui.done")));
        this.buttonList
            .add(new GuiButton(ID_CANCEL, this.width / 2 + 2, this.height - 24, 98, 20, I18n.format("gui.cancel")));
    }

    /** Text fields are the source of truth while the screen is open, so they are read back before any reshuffle. */
    private void captureFields() {
        for (int row = 0; row < fields.size(); row++) {
            int index = row + scrollRow;
            if (index < lines.size()) {
                lines.set(
                    index,
                    fields.get(row)
                        .getText());
            }
        }
    }

    private void addLine() {
        captureFields();
        lines.add("");
        // Keep the new line on screen.
        scrollRow = Math.max(0, lines.size() - rowsVisible());
        rebuildPending = true;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_ADD) {
            addLine();
            return;
        }
        if (button.id == ID_DONE) {
            captureFields();
            result.onLinesEdited(join());
            GuiStack.pop();
            return;
        }
        if (button.id == ID_CANCEL) {
            GuiStack.pop();
            return;
        }

        int row = button.id - ID_REMOVE_BASE;
        if (row >= 0 && row < fields.size()) {
            captureFields();
            int index = row + scrollRow;
            if (index < lines.size()) {
                lines.remove(index);
            }
            if (lines.isEmpty()) {
                lines.add("");
            }
            scrollRow = Math.min(scrollRow, Math.max(0, lines.size() - 1));
            rebuildPending = true;
        }
    }

    private String join() {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (line.trim()
                .isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line.trim());
        }
        return builder.toString();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (rebuildPending) {
            rebuildPending = false;
            rebuild();
        }

        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRendererObj, I18n.format(titleKey), this.width / 2, 12, 0xFFFFFF);
        this.drawCenteredString(
            this.fontRendererObj,
            EnumChatFormatting.DARK_GRAY + I18n.format("radialmenu.lines.hint"),
            this.width / 2,
            24,
            0xFFFFFF);

        for (GuiTextField field : fields) {
            field.drawTextBox();
        }

        if (lines.size() > rowsVisible()) {
            this.drawCenteredString(
                this.fontRendererObj,
                (scrollRow + 1) + "-" + Math.min(lines.size(), scrollRow + rowsVisible()) + " / " + lines.size(),
                this.width / 2,
                this.height - 62,
                0x808080);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            captureFields();
            int maxScroll = Math.max(0, lines.size() - rowsVisible());
            int next = Math.max(0, Math.min(maxScroll, scrollRow + (wheel > 0 ? -1 : 1)));
            if (next != scrollRow) {
                scrollRow = next;
                rebuildPending = true;
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        for (GuiTextField field : fields) {
            field.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            GuiStack.pop();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            addLine();
            return;
        }
        for (GuiTextField field : fields) {
            if (field.textboxKeyTyped(typedChar, keyCode)) {
                return;
            }
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
