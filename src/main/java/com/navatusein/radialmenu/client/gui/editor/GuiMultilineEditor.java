package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiIconButton;
import com.navatusein.radialmenu.client.gui.ui.UiScreen;

/**
 * Edits a list of lines - in practice, the commands a menu entry sends.
 *
 * <p>
 * 1.7.10 has no multi-line text field, so this is a column of ordinary ones with a remove button each. Enter on any
 * line adds another, which is what makes typing a handful of commands bearable.
 *
 * <p>
 * The lines are numbered because a command entry can send one line per press, cycling through them: without the
 * numbers there is no way to see what order that cycle goes in.
 */
public class GuiMultilineEditor extends UiScreen {

    public interface Result {

        /** @param text the lines joined by newlines, in the order shown */
        void onLinesEdited(String text);
    }

    private static final int ID_ADD = 1;
    private static final int ID_REMOVE_BASE = 200;

    private static final int NUMBER_WIDTH = 16;
    private static final int REMOVE_WIDTH = 20;

    private final String titleKey;
    private final Result result;

    private final List<String> lines = new ArrayList<>();

    private final List<GuiTextField> fields = new ArrayList<>();

    /** Which line each built field belongs to - only visible rows have fields, so the two are not parallel. */
    private final List<Integer> fieldLineIndex = new ArrayList<>();

    /** Focused after the next rebuild, so a line added by Enter is the one you carry on typing into. */
    private int focusLine = -1;

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
    protected String titleKey() {
        return titleKey;
    }

    @Override
    protected int panelWidth() {
        return 360;
    }

    @Override
    protected boolean framedViewport() {
        return true;
    }

    /** The hint and the add button, both of which stay put rather than scrolling away with the lines. */
    @Override
    protected int footerHeight() {
        return 10 + Ui.GAP + Ui.ROW;
    }

    @Override
    protected void buildControls() {
        fields.clear();
        fieldLineIndex.clear();

        int left = contentLeft();
        int fieldLeft = left + NUMBER_WIDTH;
        int fieldWidth = contentRight() - fieldLeft - REMOVE_WIDTH - Ui.GAP;
        int y = scrolledTop();

        for (int i = 0; i < lines.size(); i++) {
            // Rows that do not fit are not built at all. Creating them and hiding them afterwards left buttons and
            // text fields disagreeing about where the edge was.
            if (!isVisibleRow(y)) {
                y += Ui.STEP;
                continue;
            }

            GuiTextField field = new GuiTextField(this.fontRendererObj, fieldLeft + 1, y + 3, fieldWidth - 2, 14);
            field.setMaxStringLength(256);
            field.setText(lines.get(i));
            field.setCursorPositionEnd();
            if (i == focusLine) {
                field.setFocused(true);
            }
            fields.add(field);
            fieldLineIndex.add(Integer.valueOf(i));

            this.buttonList.add(
                new UiIconButton(
                    ID_REMOVE_BASE + i,
                    contentRight() - REMOVE_WIDTH,
                    y,
                    REMOVE_WIDTH,
                    Ui.ROW,
                    UiIconButton.Icon.CROSS));
            y += Ui.STEP;
        }
        focusLine = -1;

        setContentHeight(lines.size() * Ui.STEP);

        GuiButton add = new GuiButton(
            ID_ADD,
            left,
            panelBottom - Ui.PAD - Ui.ROW,
            contentWidth(),
            Ui.ROW,
            I18n.format("radialmenu.lines.add"));
        this.buttonList.add(add);
        markFooter(ID_ADD);
        tooltip(ID_ADD, I18n.format("radialmenu.lines.add.tip"));

        addBottomBar("gui.done", null, "gui.cancel");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_PRIMARY) {
            captureFields();
            result.onLinesEdited(join());
            GuiStack.pop();
            return;
        }
        if (button.id == ID_SECONDARY) {
            onCancel();
            return;
        }
        if (button.id == ID_ADD) {
            addLine(lines.size());
            return;
        }

        int index = button.id - ID_REMOVE_BASE;
        if (index >= 0 && index < lines.size()) {
            captureFields();
            lines.remove(index);
            if (lines.isEmpty()) {
                lines.add("");
            }
            requestRebuild();
        }
    }

    /** Adds a line at a position and scrolls it into view, so Enter at the bottom of a long list is not a dead end. */
    private void addLine(int index) {
        captureFields();
        lines.add(Math.min(index, lines.size()), "");
        focusLine = Math.min(index, lines.size() - 1);
        scrollOffset = Math.min(maxScrollAfterAdd(), Math.max(0, (focusLine + 1) * Ui.STEP - viewportHeight()));
        requestRebuild();
    }

    /** {@link #maxScroll} still reflects the old line count, the content height being set during the build. */
    private int maxScrollAfterAdd() {
        return Math.max(0, lines.size() * Ui.STEP - viewportHeight());
    }

    /** Text fields are the source of truth while the screen is open, so they are read back before any reshuffle. */
    private void captureFields() {
        for (int i = 0; i < fields.size(); i++) {
            int index = fieldLineIndex.get(i)
                .intValue();
            if (index < lines.size()) {
                lines.set(
                    index,
                    fields.get(i)
                        .getText());
            }
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
    protected void beforeScroll() {
        captureFields();
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        int left = contentLeft();
        for (int i = 0; i < fields.size(); i++) {
            GuiTextField field = fields.get(i);
            String number = (fieldLineIndex.get(i)
                .intValue() + 1) + ".";
            this.fontRendererObj.drawString(
                number,
                left + NUMBER_WIDTH - 4 - this.fontRendererObj.getStringWidth(number),
                field.yPosition + 3,
                Ui.TEXT_MUTED);
            field.drawTextBox();
        }
    }

    /** The placeholder hint sits with the add button, outside the clip: it is worth reading while typing any line. */
    @Override
    protected void drawOverlay(int mouseX, int mouseY, float partialTicks) {
        this.fontRendererObj
            .drawString(I18n.format("radialmenu.lines.hint"), contentLeft(), viewportBottom() + Ui.GAP, Ui.TEXT_MUTED);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        // A field clipped at the panel edge still answers to clicks on the part that was cut away.
        if (!isInsideViewport(mouseY)) {
            return;
        }
        for (GuiTextField field : fields) {
            field.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    protected boolean handleKey(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            // After the focused line rather than at the end, so a list can be filled in from the middle.
            addLine(focusedLine() + 1);
            return true;
        }
        for (GuiTextField field : fields) {
            if (field.textboxKeyTyped(typedChar, keyCode)) {
                return true;
            }
        }
        return false;
    }

    /** @return the line being typed into, or the last one when nothing has focus */
    private int focusedLine() {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i)
                .isFocused()) {
                return fieldLineIndex.get(i)
                    .intValue();
            }
        }
        return lines.size() - 1;
    }
}
