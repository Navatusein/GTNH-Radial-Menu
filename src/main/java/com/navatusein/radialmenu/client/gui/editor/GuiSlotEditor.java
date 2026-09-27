package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiCheckbox;
import com.navatusein.radialmenu.client.gui.ui.UiScreen;
import com.navatusein.radialmenu.client.gui.ui.UiTabButton;
import com.navatusein.radialmenu.client.icon.IconRenderer;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.client.profile.ProfileStorage;
import com.navatusein.radialmenu.core.action.ActionField;
import com.navatusein.radialmenu.core.action.ActionType;
import com.navatusein.radialmenu.core.action.ActionTypes;
import com.navatusein.radialmenu.core.action.Placeholders;
import com.navatusein.radialmenu.core.model.IconSpec;
import com.navatusein.radialmenu.core.model.MenuNode;

/**
 * Edits one slot.
 *
 * <p>
 * Two sections - what the entry looks like, and what it does - because those are decided at different moments, and
 * mixing them left the screen an undifferentiated column of buttons.
 *
 * <p>
 * "Submenu" is one of the action-type tabs rather than a separate switch. A slot does one thing, and opening a
 * submenu is one of the things it can do; as a separate mode it meant two controls had to agree with each other. Its
 * settings - slot count, fixed or dynamic, and the colours - are simply that type's fields.
 *
 * <p>
 * Action fields are still generated from the type's descriptors, so a new action type needs no code here.
 */
public class GuiSlotEditor extends UiScreen implements GuiKeyBindPicker.Callback, GuiIconPicker.Callback {

    private static final int ID_ICON = 1;
    private static final int ID_KEEP_OPEN = 2;
    private static final int ID_TAB_BASE = 10;
    private static final int ID_FIELD_BASE = 40;

    private final MenuNode parent;
    private final int slotIndex;

    /** Working copy; the parent is only touched on save. */
    private final MenuNode draft;

    /** Which type tab is selected. Drives the fields shown, and whether the node becomes a category. */
    private String selectedType;

    private GuiTextField titleField;

    /** Survives control rebuilds, which recreate the field. */
    private String titleText;

    private final List<ActionField> editableFields = new ArrayList<>();
    private final List<GuiTextField> fieldInputs = new ArrayList<>();

    private int actionSectionTop;

    private int iconPreviewLeft;
    private int iconPreviewTop;

    public GuiSlotEditor(MenuNode parent, int slotIndex) {
        this.parent = parent;
        this.slotIndex = slotIndex;

        MenuNode existing = parent.childAt(slotIndex);
        this.draft = existing != null ? existing.copy()
            : MenuNode.leaf(
                I18n.format("radialmenu.editor.newEntry"),
                IconSpec.item("minecraft:stone", 0),
                ActionTypes.get(ActionTypes.KEYBIND)
                    .newSpec());

        this.titleText = draft.title == null ? "" : draft.title;
        this.selectedType = draft.isCategory() ? ActionTypes.SUBMENU
            : (draft.action == null ? ActionTypes.KEYBIND : draft.action.type);

        // Submenu settings live on the node, so mirror them into a spec the generic field code can drive.
        if (ActionTypes.SUBMENU.equals(selectedType)) {
            draft.action = SubmenuFields.toSpec(draft);
        }
    }

    @Override
    protected String titleKey() {
        return "radialmenu.editor.slot";
    }

    @Override
    protected Object[] titleArgs() {
        return new Object[] { Integer.valueOf(slotIndex + 1) };
    }

    @Override
    protected int panelWidth() {
        return 330;
    }

    @Override
    protected boolean framedViewport() {
        return true;
    }

    @Override
    protected void buildControls() {
        editableFields.clear();
        fieldInputs.clear();

        int left = contentLeft();
        int controlLeft = left + Ui.LABEL_WIDTH + Ui.GAP;
        int controlWidth = contentRight() - controlLeft;
        int y = scrolledTop() + 14;

        // Built at its real size: GuiTextField works out its horizontal scroll from the width it has when the
        // text is set, so a placeholder-sized field resized afterwards renders blank until it is clicked.
        titleField = new GuiTextField(this.fontRendererObj, controlLeft + 1, y + 3, controlWidth - 2, 14);
        titleField.setMaxStringLength(64);
        titleField.setText(titleText);
        titleField.setCursorPositionZero();
        y += Ui.STEP;

        // The preview sits beside the button rather than on it. Drawing an item over a vanilla button means drawing
        // it after the button, and the GL state a button leaves behind renders items dark - this keeps the icon on
        // the known-good path, before the buttons, and reads as a preview besides.
        iconPreviewLeft = controlLeft;
        iconPreviewTop = y;
        this.buttonList.add(
            new GuiButton(
                ID_ICON,
                controlLeft + Ui.ROW + Ui.GAP,
                y,
                controlWidth - Ui.ROW - Ui.GAP,
                Ui.ROW,
                iconLabel()));
        tooltip(ID_ICON, describe("radialmenu.editor.icon.tip"));
        y += Ui.STEP;

        this.buttonList.add(
            new UiCheckbox(
                ID_KEEP_OPEN,
                controlLeft,
                y,
                controlWidth,
                I18n.format("radialmenu.editor.keepOpen"),
                draft.keepOpen));
        tooltip(ID_KEEP_OPEN, describe("radialmenu.editor.keepOpen.tip"));
        y += Ui.STEP + Ui.GAP;

        actionSectionTop = y;
        y += 14;

        List<ActionType> types = ActionTypes.all();
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
        if (selected != null) {
            for (ActionField field : selected.fields) {
                addFieldControl(field, controlLeft, controlWidth, y);
                y += Ui.STEP;
            }
        }

        setContentHeight(y - scrolledTop() + Ui.PAD);
        addBottomBar("radialmenu.editor.save", "radialmenu.editor.delete", "gui.cancel");
    }

    private void addFieldControl(ActionField field, int controlLeft, int controlWidth, int y) {
        int index = editableFields.size();
        editableFields.add(field);
        tooltip(ID_FIELD_BASE + index, describe(field.tooltipKey()));

        String current = draft.action.getString(field.key, field.defaultValue);

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
                apply(null);
                return;
            case ID_ICON:
                captureInputs();
                GuiStack.push(new GuiIconPicker(this, draft.icon == null ? null : draft.icon.color));
                return;
            case ID_KEEP_OPEN:
                ((UiCheckbox) button).toggle();
                draft.keepOpen = ((UiCheckbox) button).checked;
                return;
            default:
                break;
        }

        List<ActionType> types = ActionTypes.all();
        int tabIndex = button.id - ID_TAB_BASE;
        if (tabIndex >= 0 && tabIndex < types.size()) {
            selectType(types.get(tabIndex).id);
            return;
        }

        int fieldIndex = button.id - ID_FIELD_BASE;
        if (fieldIndex >= 0 && fieldIndex < editableFields.size()) {
            onFieldButton(editableFields.get(fieldIndex), button);
        }
    }

    private void selectType(String typeId) {
        if (typeId.equals(selectedType)) {
            return;
        }
        captureInputs();
        selectedType = typeId;

        if (ActionTypes.SUBMENU.equals(typeId)) {
            draft.action = SubmenuFields.toSpec(draft);
        } else {
            draft.action = ActionTypes.get(typeId)
                .newSpec();
        }
        requestRebuild();
    }

    private void onFieldButton(ActionField field, GuiButton button) {
        final String key = field.key;
        String current = draft.action.getString(key, field.defaultValue);

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
                        draft.action.set(key, text);
                        requestRebuild();
                    }
                }));
                return;
            case COLOR:
                captureInputs();
                GuiStack.push(new GuiColorPicker(current, true, new GuiColorPicker.Result() {

                    @Override
                    public void onColorPicked(String hex) {
                        draft.action.set(key, hex);
                        requestRebuild();
                    }
                }));
                return;
            case BOOLEAN:
                ((UiCheckbox) button).toggle();
                draft.action.set(key, Boolean.toString(((UiCheckbox) button).checked));
                return;
            case ENUM:
                draft.action.set(key, next(field.options, current));
                break;
            case PROFILE_REF:
                draft.action.set(key, next(ProfileStorage.listProfileNames(), current));
                break;
            default:
                return;
        }
        button.displayString = buttonValueLabel(field, draft.action.getString(key, ""));
    }

    /** @return the translated text, or null when the key has no string - so a missing tooltip shows nothing */
    private static String describe(String key) {
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

    /** Copies what is typed into the draft, so opening a picker cannot discard it. */
    private void captureInputs() {
        if (titleField != null) {
            titleText = titleField.getText();
        }
        draft.title = titleText.trim();
        for (int i = 0; i < editableFields.size() && i < fieldInputs.size(); i++) {
            GuiTextField input = fieldInputs.get(i);
            if (input != null && draft.action != null) {
                draft.action.set(editableFields.get(i).key, input.getText());
            }
        }
    }

    private void save() {
        captureInputs();

        if (ActionTypes.SUBMENU.equals(selectedType)) {
            SubmenuFields.applyToNode(draft);
        } else {
            draft.children = null;
            draft.layout = null;
            draft.style = null;
        }

        draft.normalize();
        apply(draft);
    }

    private void apply(MenuNode node) {
        parent.setChildAt(slotIndex, node);
        ProfileManager.active()
            .normalize();
        ProfileManager.saveActive();
        IconRenderer.clearCache();
        // Editing is finished, so the wheel closes with the editor instead of reappearing beneath it.
        GuiStack.closeAll();
    }

    @Override
    public void onKeyBindPicked(String description, String category) {
        draft.action.set(ActionTypes.PARAM_BINDING, description);
        draft.action.set(ActionTypes.PARAM_CATEGORY, category == null ? "" : category);
        requestRebuild();
    }

    @Override
    public void onIconPicked(IconSpec icon) {
        draft.icon = icon;
        requestRebuild();
    }

    /**
     * Escape and Cancel close the wheel along with the editor.
     *
     * <p>
     * Returning to a wheel that is no longer being held open just leaves something on screen the player then has to
     * dismiss separately.
     */
    @Override
    protected void onCancel() {
        GuiStack.closeAll();
    }

    @Override
    protected void beforeScroll() {
        captureInputs();
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        int left = contentLeft();
        int y = scrolledTop();

        Ui.sectionHeader(I18n.format("radialmenu.editor.sectionLook"), left, y, contentRight());
        y += 14;

        if (isVisibleRow(y)) {
            Ui.rowLabel(I18n.format("radialmenu.editor.title"), left, y);
            titleField.drawTextBox();
        }
        y += Ui.STEP;

        if (isVisibleRow(y)) {
            Ui.rowLabel(I18n.format("radialmenu.editor.icon"), left, y);
        }

        Ui.sectionHeader(I18n.format("radialmenu.editor.sectionAction"), left, actionSectionTop, contentRight());

        int fieldY = actionSectionTop + 14 + UiTabButton.HEIGHT + Ui.GAP;
        for (ActionField field : editableFields) {
            if (field.kind != ActionField.Kind.BOOLEAN && isVisibleRow(fieldY)) {
                Ui.rowLabel(Ui.fit(I18n.format(field.labelKey), Ui.LABEL_WIDTH), left, fieldY);
            }
            fieldY += Ui.STEP;
        }

        for (GuiTextField input : fieldInputs) {
            if (input != null && isVisibleRow(input.yPosition - 3)) {
                input.drawTextBox();
            }
        }

        drawIconPreview();
    }

    private String iconLabel() {
        return draft.icon == null || draft.icon.id == null ? I18n.format("radialmenu.editor.pickIcon")
            : Ui.fit(draft.icon.id, contentWidth() - Ui.LABEL_WIDTH - Ui.ROW - 16);
    }

    /** The preview tile, drawn with the content so it never has to fight a button for the GL state. */
    private void drawIconPreview() {
        if (!isVisibleRow(iconPreviewTop)) {
            return;
        }
        Ui.frame(
            iconPreviewLeft,
            iconPreviewTop,
            iconPreviewLeft + Ui.ROW,
            iconPreviewTop + Ui.ROW,
            0xFF101010,
            Ui.PANEL_BORDER);
        if (draft.icon != null) {
            IconRenderer.draw(draft.icon, iconPreviewLeft + 2, iconPreviewTop + 2);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        // A field clipped at the panel edge still answers to clicks on the part that was cut away.
        if (!isInsideViewport(mouseY)) {
            return;
        }
        titleField.mouseClicked(mouseX, mouseY, button);
        for (GuiTextField input : fieldInputs) {
            if (input != null) {
                input.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    @Override
    protected boolean handleKey(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_TAB) {
            return true;
        }
        if (titleField.textboxKeyTyped(typedChar, keyCode)) {
            return true;
        }
        for (GuiTextField input : fieldInputs) {
            if (input != null && input.textboxKeyTyped(typedChar, keyCode)) {
                return true;
            }
        }
        return false;
    }
}
