package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiCheckbox;
import com.navatusein.radialmenu.client.gui.ui.UiScreen;
import com.navatusein.radialmenu.core.script.ScriptForm;

/**
 * A script's form: several labelled questions on one screen, answered together.
 *
 * <p>
 * The screen only holds what each control shows, as a string per field. Whether that is a valid answer and what it
 * becomes for the script is {@link ScriptForm}'s to say, so the screen asks it on confirm and marks whatever it
 * objects to - the form stays up until every answer is acceptable.
 *
 * <p>
 * A complaint is one line at the foot of the form, with the field it is about drawn in red, rather than a line under
 * each field. A line under the field has to be reserved before anything is wrong - a row that grew on a wrong answer
 * would move every field below it out from under the cursor - and reserved under every field that could fail it put
 * uneven gaps between rows that looked alike, which is how it read in the game. The foot of the form has nothing below
 * it to move.
 */
public class GuiScriptForm extends UiScreen {

    /** Handed the answers once every one of them is acceptable. */
    public interface Result {

        void onConfirm(Map<String, String> answers);
    }

    /** A line of small text: a line of a description, or the complaint at the foot. */
    private static final int LINE = 10;

    /** The label column never shrinks below this, so a form of one-word labels does not crowd the controls. */
    private static final int MIN_LABEL_WIDTH = 32;

    private static final int TEXT_LENGTH = 256;

    private final ScriptForm form;
    private final Result result;

    /** What every control holds, kept across rebuilds - scrolling and resizing both rebuild the controls. */
    private final Map<String, String> answers;

    private Map<String, ScriptForm.Problem> problems = Collections.emptyMap();

    private final Map<String, GuiTextField> textFields = new LinkedHashMap<>();

    /** Where each field's row starts, for drawing its label and its note. */
    private final Map<String, Integer> rowTops = new HashMap<>();

    private List<String> descriptionLines = Collections.emptyList();

    /** Each field's description, wrapped to the width of its control. */
    private final Map<String, List<String>> noteLines = new HashMap<>();

    /** As wide as the longest label, so a short one does not sit far from its control across empty space. */
    private int labelWidth = Ui.LABEL_WIDTH;

    /** Where the line for a complaint sits, under the last field. */
    private int footTop;

    /** The text field with the caret, carried across rebuilds. Null before the first build picks one. */
    private String focusedKey;

    private boolean overflowing;

    public GuiScriptForm(ScriptForm form, Result result) {
        this.form = form;
        this.result = result;
        this.answers = form.initialAnswers();
    }

    @Override
    protected String titleKey() {
        // Through the translator like any title, which runs it past String.format - so a script's per cent sign is
        // doubled rather than coming out as "Format error".
        return form.title.isEmpty() ? "radialmenu.script.title" : form.title.replace("%", "%%");
    }

    @Override
    protected int panelWidth() {
        return 300;
    }

    @Override
    protected int panelHeightHint() {
        int needed = contentHeight();
        int room = maxPanelHeightHint();
        overflowing = needed > room;
        return Math.min(needed, room);
    }

    @Override
    protected boolean framedViewport() {
        return overflowing;
    }

    /**
     * Measures everything that depends on the panel's width: the label column, and every description wrapped to the
     * room it has.
     */
    private void layout() {
        int widest = 0;
        for (ScriptForm.Field field : form.fields) {
            widest = Math.max(widest, this.fontRendererObj.getStringWidth(field.label));
        }
        labelWidth = Math.max(MIN_LABEL_WIDTH, Math.min(widest, contentWidth() / 2));

        descriptionLines = form.description == null ? Collections.<String>emptyList()
            : wrap(form.description, contentWidth());

        noteLines.clear();
        int noteWidth = contentRight() - controlLeft();
        for (ScriptForm.Field field : form.fields) {
            noteLines.put(
                field.key,
                field.description == null ? Collections.<String>emptyList() : wrap(field.description, noteWidth));
        }
    }

    private int contentHeight() {
        layout();
        int height = Ui.GAP;
        if (!descriptionLines.isEmpty()) {
            height += descriptionLines.size() * LINE + Ui.GAP;
        }
        for (ScriptForm.Field field : form.fields) {
            height += rowHeight(field);
        }
        if (anyCanFail()) {
            height += LINE;
        }
        return height;
    }

    private int rowHeight(ScriptForm.Field field) {
        return Ui.ROW + noteLines.get(field.key)
            .size() * LINE + Ui.GAP;
    }

    /** Whether the foot needs its line for a complaint. */
    private boolean anyCanFail() {
        for (ScriptForm.Field field : form.fields) {
            if (field.canFail()) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<String> wrap(String text, int width) {
        return new ArrayList<>(this.fontRendererObj.listFormattedStringToWidth(text, Math.max(40, width)));
    }

    private int controlLeft() {
        return contentLeft() + labelWidth + Ui.GAP;
    }

    @Override
    protected void buildControls() {
        textFields.clear();
        rowTops.clear();
        int height = contentHeight();

        int top = scrolledTop() + Ui.GAP;
        if (!descriptionLines.isEmpty()) {
            top += descriptionLines.size() * LINE + Ui.GAP;
        }

        int left = controlLeft();
        int width = contentRight() - left;
        for (int i = 0; i < form.fields.size(); i++) {
            ScriptForm.Field field = form.fields.get(i);
            rowTops.put(field.key, Integer.valueOf(top));
            String answer = answers.get(field.key);

            switch (field.type) {
                case TOGGLE:
                    this.buttonList.add(new UiCheckbox(i, left, top, width, "", "true".equals(answer)));
                    break;
                case CHOICE:
                    this.buttonList.add(new GuiButton(i, left, top, width, Ui.ROW, optionLabel(field, answer)));
                    tooltip(i, I18n.format("radialmenu.script.form.choice.tip"));
                    break;
                default:
                    GuiTextField text = new GuiTextField(this.fontRendererObj, left + 1, top + 3, width - 2, 14);
                    text.setMaxStringLength(TEXT_LENGTH);
                    text.setText(answer == null ? "" : answer);
                    text.setCursorPositionEnd();
                    textFields.put(field.key, text);
                    if (focusedKey == null) {
                        focusedKey = field.key;
                    }
                    text.setFocused(field.key.equals(focusedKey));
                    break;
            }
            top += rowHeight(field);
        }
        footTop = top;

        setContentHeight(height);
        addBottomBar("gui.done", null, "gui.cancel");
    }

    private static String optionLabel(ScriptForm.Field field, String key) {
        int index = field.optionIndex(key);
        return field.options.get(Math.max(0, index)).label;
    }

    /** Reads the text fields back into {@link #answers}, before anything rebuilds them. */
    private void readTextFields() {
        for (Map.Entry<String, GuiTextField> entry : textFields.entrySet()) {
            answers.put(
                entry.getKey(),
                entry.getValue()
                    .getText());
        }
    }

    @Override
    protected void beforeScroll() {
        readTextFields();
    }

    private void confirm() {
        readTextFields();
        problems = form.check(answers);
        if (problems.isEmpty()) {
            Map<String, String> confirmed = new LinkedHashMap<>(answers);
            GuiStack.pop();
            result.onConfirm(confirmed);
            return;
        }
        // The caret goes to the first field that is wrong, so fixing it is typing rather than clicking.
        for (String key : problems.keySet()) {
            if (textFields.containsKey(key)) {
                focus(key);
                break;
            }
        }
    }

    private void focus(String key) {
        focusedKey = key;
        for (Map.Entry<String, GuiTextField> entry : textFields.entrySet()) {
            entry.getValue()
                .setFocused(
                    entry.getKey()
                        .equals(key));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_PRIMARY) {
            confirm();
            return;
        }
        if (button.id == ID_SECONDARY) {
            onCancel();
            return;
        }
        if (button.id < 0 || button.id >= form.fields.size()) {
            return;
        }
        ScriptForm.Field field = form.fields.get(button.id);
        if (field.type == ScriptForm.Type.TOGGLE && button instanceof UiCheckbox) {
            UiCheckbox box = (UiCheckbox) button;
            box.toggle();
            answers.put(field.key, Boolean.toString(box.checked));
        } else if (field.type == ScriptForm.Type.CHOICE) {
            int next = (field.optionIndex(answers.get(field.key)) + 1) % field.options.size();
            ScriptForm.Option option = field.options.get(next);
            answers.put(field.key, option.key);
            button.displayString = option.label;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (GuiTextField text : textFields.values()) {
            text.updateCursorCounter();
        }
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        int top = scrolledTop() + Ui.GAP;
        for (String line : descriptionLines) {
            if (top + LINE > contentTop() && top < contentBottom()) {
                this.fontRendererObj.drawString(line, contentLeft(), top, Ui.TEXT_MUTED);
            }
            top += LINE;
        }

        int left = controlLeft();
        for (ScriptForm.Field field : form.fields) {
            int rowTop = rowTops.get(field.key)
                .intValue();
            boolean wrong = problems.containsKey(field.key);
            if (isVisibleRow(rowTop)) {
                Ui.rowLabel(
                    this.fontRendererObj.trimStringToWidth(field.label, labelWidth),
                    contentLeft(),
                    rowTop,
                    labelWidth,
                    wrong ? Ui.TEXT_ERROR : Ui.TEXT_MUTED);

                GuiTextField text = textFields.get(field.key);
                if (text != null) {
                    if (wrong) {
                        // Just outside the box the text field draws for itself, so it reads as the box turning red.
                        drawRect(
                            text.xPosition - 2,
                            text.yPosition - 2,
                            text.xPosition + text.width + 2,
                            text.yPosition + text.height + 2,
                            Ui.TEXT_ERROR);
                    }
                    text.drawTextBox();
                }
            }

            int lineTop = rowTop + Ui.ROW;
            for (String line : noteLines.get(field.key)) {
                if (lineTop + LINE > contentTop() && lineTop < contentBottom()) {
                    this.fontRendererObj.drawString(line, left, lineTop, Ui.TEXT_MUTED);
                }
                lineTop += LINE;
            }
        }

        // The first complaint, at the foot, named by its field - the red label says which, this says why.
        for (ScriptForm.Field field : form.fields) {
            ScriptForm.Problem problem = problems.get(field.key);
            if (problem == null) {
                continue;
            }
            if (footTop + LINE > contentTop() && footTop < contentBottom()) {
                String complaint = field.label + ": " + describe(field, problem);
                this.fontRendererObj.drawString(
                    this.fontRendererObj.trimStringToWidth(complaint, contentWidth()),
                    contentLeft(),
                    footTop,
                    Ui.TEXT_ERROR);
            }
            break;
        }
    }

    private static String describe(ScriptForm.Field field, ScriptForm.Problem problem) {
        switch (problem) {
            case EMPTY:
                return I18n.format("radialmenu.script.form.empty");
            case NOT_A_NUMBER:
                return I18n.format("radialmenu.script.form.notNumber");
            case NOT_WHOLE:
                return I18n.format("radialmenu.script.form.notWhole");
            case TOO_SMALL:
                return I18n.format("radialmenu.script.form.tooSmall", ScriptForm.formatNumber(field.min));
            case TOO_LARGE:
                return I18n.format("radialmenu.script.form.tooLarge", ScriptForm.formatNumber(field.max));
            default:
                return problem.toString();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (!isShowing()) {
            return;
        }
        for (Map.Entry<String, GuiTextField> entry : textFields.entrySet()) {
            GuiTextField text = entry.getValue();
            if (!isVisibleRow(text.yPosition - 3) || !isInsideViewport(mouseY)) {
                text.setFocused(false);
                continue;
            }
            text.mouseClicked(mouseX, mouseY, button);
            if (text.isFocused()) {
                focusedKey = entry.getKey();
            }
        }
    }

    @Override
    protected boolean handleKey(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            confirm();
            return true;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            moveFocus(isShiftKeyDown() ? -1 : 1);
            return true;
        }
        for (Map.Entry<String, GuiTextField> entry : textFields.entrySet()) {
            if (entry.getValue()
                .textboxKeyTyped(typedChar, keyCode)) {
                if (problems.containsKey(entry.getKey())) {
                    problems = new LinkedHashMap<>(problems);
                    problems.remove(entry.getKey());
                }
                return true;
            }
        }
        return false;
    }

    private void moveFocus(int step) {
        List<String> keys = new ArrayList<>(textFields.keySet());
        if (keys.isEmpty()) {
            return;
        }
        int index = keys.indexOf(focusedKey);
        int next = ((index < 0 ? 0 : index + step) % keys.size() + keys.size()) % keys.size();
        focus(keys.get(next));
    }
}
