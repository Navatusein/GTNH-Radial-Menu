package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiCheckbox;
import com.navatusein.radialmenu.client.gui.ui.UiScreen;
import com.navatusein.radialmenu.client.gui.ui.UiTabButton;
import com.navatusein.radialmenu.client.profile.ProfileStorage;
import com.navatusein.radialmenu.core.action.ActionField;
import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionSteps;
import com.navatusein.radialmenu.core.action.ActionType;
import com.navatusein.radialmenu.core.action.ActionTypes;
import com.navatusein.radialmenu.core.action.Placeholders;

/**
 * The "what this does" half of an editor: the action-type tabs, the fields the chosen type declares, and - for a
 * chain - the list of steps.
 *
 * <p>
 * Shared because a step of a chain is edited with exactly the controls a slot is. Written twice they would drift, and
 * every new action type would have to be taught to both; this way a type still costs a descriptor and an executor and
 * no GUI code at all.
 *
 * <p>
 * Subclasses own the frame around it: the slot editor puts an appearance section above and saves into a menu node,
 * the step editor hands the result back to the chain it came from.
 */
public abstract class GuiActionEditor extends UiScreen implements GuiKeyBindPicker.Callback {

    protected static final int ID_TAB_BASE = 10;
    protected static final int ID_FIELD_BASE = 40;

    /** Step controls are numbered far apart, so a long chain cannot reach into the next range. */
    private static final int ID_STEP_ADD = 300;
    private static final int ID_STEP_EDIT_BASE = 1000;
    private static final int ID_STEP_UP_BASE = 2000;
    private static final int ID_STEP_DOWN_BASE = 3000;
    private static final int ID_STEP_REMOVE_BASE = 4000;

    private static final int ARROW = 18;

    /** The action being edited. */
    protected ActionSpec spec;

    /** Which type tab is selected. Always a registered type id. */
    protected String selectedType;

    /**
     * What has already been built for each type.
     *
     * <p>
     * Switching tabs used to discard the old spec outright, which is survivable for a keybinding and not for a chain
     * of six steps. A tab now remembers what was on it, and only the selected one is kept on save.
     */
    private final Map<String, ActionSpec> specByType = new LinkedHashMap<>();

    private final List<ActionField> editableFields = new ArrayList<>();
    private final List<GuiTextField> fieldInputs = new ArrayList<>();

    /** Row tops recorded during the build, so drawing never replays the layout arithmetic and disagrees with it. */
    private final List<Integer> fieldRowTops = new ArrayList<>();

    private int stepsSectionTop = -1;
    private int stepCount;

    /** Which action types this editor offers. */
    protected abstract List<ActionType> offeredTypes();

    /** Sets the action to edit. Call from the subclass constructor, before the screen is shown. */
    protected void setSpec(ActionSpec spec, String typeId) {
        this.spec = spec;
        this.selectedType = typeId;
        specByType.put(typeId, spec);
    }

    // -- building ------------------------------------------------------------------------------------------------

    /**
     * Builds the tabs, the selected type's fields and, for a chain, its steps.
     *
     * @return the y below everything it added
     */
    protected int buildActionSection(int y, int left, int controlLeft, int controlWidth) {
        editableFields.clear();
        fieldInputs.clear();
        fieldRowTops.clear();
        stepsSectionTop = -1;
        stepCount = 0;

        List<ActionType> types = offeredTypes();
        int tabWidth = (contentWidth() - (types.size() - 1) * 2) / Math.max(1, types.size());
        for (int i = 0; i < types.size(); i++) {
            ActionType type = types.get(i);
            this.buttonList.add(
                new UiTabButton(
                    ID_TAB_BASE + i,
                    left + i * (tabWidth + 2),
                    y,
                    tabWidth,
                    I18n.format(type.labelKey),
                    type.id.equals(selectedType)));
            tooltip(ID_TAB_BASE + i, describe(type.labelKey + ".tip"));
        }
        y += UiTabButton.HEIGHT + Ui.GAP;

        ActionType selected = ActionTypes.get(selectedType);
        if (selected == null) {
            return y;
        }

        for (ActionField field : selected.fields) {
            addFieldControl(field, controlLeft, controlWidth, y);
            y += Ui.STEP;
        }

        if (selected.chain) {
            y += Ui.GAP;
            stepsSectionTop = y;
            y += 14;
            y = buildStepRows(y, left);
        }
        return y;
    }

    private int buildStepRows(int y, int left) {
        List<ActionSpec> steps = spec.stepsOrEmpty();
        stepCount = steps.size();

        int removeLeft = contentRight() - ARROW;
        int downLeft = removeLeft - ARROW - 2;
        int upLeft = downLeft - ARROW - 2;
        int rowWidth = upLeft - Ui.GAP - left;

        for (int i = 0; i < steps.size(); i++) {
            // The whole row opens the step. A chain is read as a list of things that happen, so the thing it says is
            // also the thing you click.
            String label = (i + 1) + ". " + ActionSummary.of(steps.get(i));
            this.buttonList
                .add(new GuiButton(ID_STEP_EDIT_BASE + i, left, y, rowWidth, Ui.ROW, Ui.fit(label, rowWidth - 8)));
            tooltip(ID_STEP_EDIT_BASE + i, describe("radialmenu.steps.edit.tip"));

            GuiButton up = new GuiButton(ID_STEP_UP_BASE + i, upLeft, y, ARROW, Ui.ROW, "^");
            GuiButton down = new GuiButton(ID_STEP_DOWN_BASE + i, downLeft, y, ARROW, Ui.ROW, "v");
            up.enabled = i > 0;
            down.enabled = i < steps.size() - 1;
            this.buttonList.add(up);
            this.buttonList.add(down);
            this.buttonList.add(new GuiButton(ID_STEP_REMOVE_BASE + i, removeLeft, y, ARROW, Ui.ROW, "x"));

            y += Ui.STEP;
        }

        this.buttonList
            .add(new GuiButton(ID_STEP_ADD, left, y, contentWidth(), Ui.ROW, I18n.format("radialmenu.steps.add")));
        tooltip(ID_STEP_ADD, describe("radialmenu.steps.add.tip"));
        return y + Ui.STEP;
    }

    private void addFieldControl(ActionField field, int controlLeft, int controlWidth, int y) {
        int index = editableFields.size();
        editableFields.add(field);
        fieldRowTops.add(Integer.valueOf(y));
        tooltip(ID_FIELD_BASE + index, describe(field.tooltipKey()));

        String current = spec.getString(field.key, field.defaultValue);

        switch (field.kind) {
            case KEYBIND_REF:
            case MULTILINE_STRING:
            case ENUM:
            case PROFILE_REF:
            case COLOR:
                this.buttonList.add(
                    new GuiButton(
                        ID_FIELD_BASE + index,
                        controlLeft,
                        y,
                        controlWidth,
                        Ui.ROW,
                        buttonValueLabel(field, current)));
                fieldInputs.add(null);
                break;
            case BOOLEAN:
                this.buttonList.add(
                    new UiCheckbox(
                        ID_FIELD_BASE + index,
                        controlLeft,
                        y,
                        controlWidth,
                        I18n.format(field.labelKey),
                        Boolean.parseBoolean(current)));
                fieldInputs.add(null);
                break;
            default:
                GuiTextField input = new GuiTextField(
                    this.fontRendererObj,
                    controlLeft + 1,
                    y + 3,
                    controlWidth - 2,
                    14);
                input.setMaxStringLength(256);
                input.setText(current == null ? "" : current);
                fieldInputs.add(input);
                break;
        }
    }

    private String buttonValueLabel(ActionField field, String current) {
        switch (field.kind) {
            case KEYBIND_REF:
                return current == null || current.isEmpty() ? I18n.format("radialmenu.editor.pickKeybind")
                    : I18n.format(current);
            case MULTILINE_STRING:
                return I18n.format("radialmenu.editor.editLines", Placeholders.splitLines(current).length);
            case COLOR:
                return current == null || current.trim()
                    .isEmpty() ? I18n.format("radialmenu.editor.inherit") : current;
            case ENUM:
                return localizedValue(field, current);
            default:
                return current == null ? "" : current;
        }
    }

    /**
     * Shows an enum value the way the rest of the interface is written.
     *
     * <p>
     * The stored value stays lower case; only the display changes. A value with no translation falls back to itself
     * rather than showing a raw key, so an action type that forgot a string still reads as something.
     */
    private static String localizedValue(ActionField field, String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String key = field.valueLabelKey(value);
        String translated = I18n.format(key);
        return translated.equals(key) ? value : translated;
    }

    // -- input ---------------------------------------------------------------------------------------------------

    /** @return whether the button belonged to the action section */
    protected boolean handleActionButton(GuiButton button) {
        if (button.id >= ID_STEP_EDIT_BASE) {
            return handleStepButton(button.id);
        }
        if (button.id == ID_STEP_ADD) {
            captureInputs();
            editStep(
                spec.stepsOrEmpty()
                    .size(),
                null);
            return true;
        }

        List<ActionType> types = offeredTypes();
        int tabIndex = button.id - ID_TAB_BASE;
        if (tabIndex >= 0 && tabIndex < types.size()) {
            selectType(types.get(tabIndex).id);
            return true;
        }

        int fieldIndex = button.id - ID_FIELD_BASE;
        if (fieldIndex >= 0 && fieldIndex < editableFields.size()) {
            onFieldButton(editableFields.get(fieldIndex), button);
            return true;
        }
        return false;
    }

    private boolean handleStepButton(int id) {
        List<ActionSpec> steps = spec.stepsOrEmpty();

        if (id >= ID_STEP_REMOVE_BASE) {
            int index = id - ID_STEP_REMOVE_BASE;
            if (index < steps.size()) {
                captureInputs();
                steps.remove(index);
                requestRebuild();
            }
            return true;
        }
        if (id >= ID_STEP_DOWN_BASE) {
            moveStep(id - ID_STEP_DOWN_BASE, 1);
            return true;
        }
        if (id >= ID_STEP_UP_BASE) {
            moveStep(id - ID_STEP_UP_BASE, -1);
            return true;
        }

        int index = id - ID_STEP_EDIT_BASE;
        if (index < steps.size()) {
            captureInputs();
            editStep(index, steps.get(index));
        }
        return true;
    }

    private void moveStep(int index, int direction) {
        captureInputs();
        if (ActionSteps.move(spec.stepsOrEmpty(), index, direction)) {
            requestRebuild();
        }
    }

    /** Opens the editor for one step. An index past the end appends, which is what makes "add" the same path. */
    private void editStep(final int index, ActionSpec step) {
        GuiStack.push(new GuiStepEditor(step, index + 1, new GuiStepEditor.Result() {

            @Override
            public void onStepEdited(ActionSpec edited) {
                List<ActionSpec> steps = spec.stepsOrEmpty();
                if (index < steps.size()) {
                    steps.set(index, edited);
                } else {
                    steps.add(edited);
                }
                requestRebuild();
            }
        }));
    }

    private void selectType(String typeId) {
        if (typeId.equals(selectedType)) {
            return;
        }
        captureInputs();
        specByType.put(selectedType, spec);
        selectedType = typeId;
        spec = specFor(typeId);
        requestRebuild();
    }

    /** The spec a tab shows: what was built on it before, or a fresh one carrying the type's defaults. */
    protected ActionSpec specFor(String typeId) {
        ActionSpec remembered = specByType.get(typeId);
        if (remembered != null) {
            return remembered;
        }
        return ActionTypes.get(typeId)
            .newSpec();
    }

    private void onFieldButton(ActionField field, GuiButton button) {
        final String key = field.key;
        String current = spec.getString(key, field.defaultValue);

        switch (field.kind) {
            case KEYBIND_REF:
                captureInputs();
                GuiStack.push(new GuiKeyBindPicker(this));
                return;
            case MULTILINE_STRING:
                captureInputs();
                GuiStack.push(new GuiMultilineEditor(field.labelKey, current, new GuiMultilineEditor.Result() {

                    @Override
                    public void onLinesEdited(String text) {
                        spec.set(key, text);
                        requestRebuild();
                    }
                }));
                return;
            case COLOR:
                captureInputs();
                GuiStack.push(new GuiColorPicker(current, true, new GuiColorPicker.Result() {

                    @Override
                    public void onColorPicked(String hex) {
                        spec.set(key, hex);
                        requestRebuild();
                    }
                }));
                return;
            case BOOLEAN:
                ((UiCheckbox) button).toggle();
                spec.set(key, Boolean.toString(((UiCheckbox) button).checked));
                return;
            case ENUM:
                spec.set(key, next(field.options, current));
                break;
            case PROFILE_REF:
                spec.set(key, next(ProfileStorage.listProfileNames(), current));
                break;
            default:
                return;
        }
        button.displayString = buttonValueLabel(field, spec.getString(key, ""));
    }

    @Override
    public void onKeyBindPicked(String description, String category) {
        spec.set(ActionTypes.PARAM_BINDING, description);
        spec.set(ActionTypes.PARAM_CATEGORY, category == null ? "" : category);
        requestRebuild();
    }

    /** Copies what is typed into the spec. Subclasses capture their own fields too, then call this. */
    protected void captureInputs() {
        for (int i = 0; i < editableFields.size() && i < fieldInputs.size(); i++) {
            GuiTextField input = fieldInputs.get(i);
            if (input != null) {
                spec.set(editableFields.get(i).key, input.getText());
            }
        }
    }

    @Override
    protected void beforeScroll() {
        captureInputs();
    }

    // -- drawing -------------------------------------------------------------------------------------------------

    /** Draws the labels beside the generated controls, and the chain's heading. */
    protected void drawActionSection() {
        int left = contentLeft();

        for (int i = 0; i < editableFields.size() && i < fieldRowTops.size(); i++) {
            ActionField field = editableFields.get(i);
            int y = fieldRowTops.get(i)
                .intValue();
            if (field.kind != ActionField.Kind.BOOLEAN && isVisibleRow(y)) {
                Ui.rowLabel(Ui.fit(I18n.format(field.labelKey), Ui.LABEL_WIDTH), left, y);
            }
        }

        for (GuiTextField input : fieldInputs) {
            if (input != null && isVisibleRow(input.yPosition - 3)) {
                input.drawTextBox();
            }
        }

        if (stepsSectionTop < 0) {
            return;
        }
        if (isVisibleRow(stepsSectionTop)) {
            Ui.sectionHeader(I18n.format("radialmenu.steps.section"), left, stepsSectionTop, contentRight());
        }
        if (stepCount == 0 && isVisibleRow(stepsSectionTop + 14)) {
            this.fontRendererObj
                .drawString(I18n.format("radialmenu.steps.empty"), left, stepsSectionTop + 18, Ui.TEXT_MUTED);
        }
    }

    /** @return the translated text, or null when the key has no string - so a missing tooltip shows nothing */
    protected static String describe(String key) {
        String translated = I18n.format(key);
        return translated.equals(key) ? null : translated;
    }

    private static String next(List<String> options, String current) {
        if (options.isEmpty()) {
            return current == null ? "" : current;
        }
        int index = options.indexOf(current);
        return options.get((index + 1) % options.size());
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        // A field clipped at the panel edge still answers to clicks on the part that was cut away.
        if (!isInsideViewport(mouseY)) {
            return;
        }
        for (GuiTextField input : fieldInputs) {
            if (input != null) {
                input.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    @Override
    protected boolean handleKey(char typedChar, int keyCode) {
        for (GuiTextField input : fieldInputs) {
            if (input != null && input.textboxKeyTyped(typedChar, keyCode)) {
                return true;
            }
        }
        return false;
    }
}
