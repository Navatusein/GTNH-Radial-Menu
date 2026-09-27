package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.icon.IconRenderer;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.client.profile.ProfileStorage;
import com.navatusein.radialmenu.core.action.ActionField;
import com.navatusein.radialmenu.core.action.ActionType;
import com.navatusein.radialmenu.core.action.ActionTypes;
import com.navatusein.radialmenu.core.action.Placeholders;
import com.navatusein.radialmenu.core.model.IconSpec;
import com.navatusein.radialmenu.core.model.MenuNode;
import com.navatusein.radialmenu.core.model.SlotLayout;

/**
 * Edits one slot.
 *
 * <p>
 * The action's own parameters are not hard-coded here: they are built from the chosen {@link ActionType}'s field
 * descriptors. That is what keeps later action types from needing a screen each - adding the command action in phase 2
 * adds no GUI code at all.
 *
 * <p>
 * Edits are made on a copy and only written back on save, so cancelling really cancels.
 */
public class GuiSlotEditor extends GuiScreen implements GuiKeyBindPicker.Callback, GuiIconPicker.Callback {

    private static final int ID_SAVE = 1;
    private static final int ID_CANCEL = 2;
    private static final int ID_DELETE = 3;
    private static final int ID_TYPE = 4;
    private static final int ID_KEEP_OPEN = 5;
    private static final int ID_ICON = 6;
    private static final int ID_MAKE_CATEGORY = 7;
    private static final int ID_FIELD_BASE = 100;

    private final MenuNode parent;
    private final int slotIndex;

    /** Working copy; the parent is only touched on save. */
    private MenuNode draft;

    private GuiTextField titleField;

    private final List<ActionField> editableFields = new ArrayList<>();
    private final List<GuiTextField> fieldInputs = new ArrayList<>();

    /**
     * Control rebuilds wait for the next frame: GuiScreen.mouseClicked iterates buttonList by index and calls
     * actionPerformed from inside that loop, so replacing the list mid-click makes it walk the new buttons too.
     */
    private boolean rebuildPending;

    public GuiSlotEditor(MenuNode parent, int slotIndex) {
        this.parent = parent;
        this.slotIndex = slotIndex;

        MenuNode existing = parent.childAt(slotIndex);
        if (existing != null) {
            this.draft = existing.copy();
        } else {
            this.draft = MenuNode.leaf(
                "New entry",
                IconSpec.item("minecraft:stone", 0),
                ActionTypes.get(ActionTypes.KEYBIND)
                    .newSpec());
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        int left = this.width / 2 - 150;
        int top = 34;

        titleField = new GuiTextField(this.fontRendererObj, left + 60, top, 240, 16);
        titleField.setMaxStringLength(64);
        titleField.setText(draft.title == null ? "" : draft.title);

        rebuildControls(left, top);
    }

    private void rebuildControls(int left, int top) {
        this.buttonList.clear();
        fieldInputs.clear();
        editableFields.clear();

        int y = top + 22;

        this.buttonList.add(new GuiButton(ID_ICON, left + 60, y, 240, 20, iconLabel()));
        y += 24;

        this.buttonList.add(new GuiButton(ID_KEEP_OPEN, left + 60, y, 240, 20, keepOpenLabel()));
        y += 24;

        this.buttonList.add(new GuiButton(ID_MAKE_CATEGORY, left + 60, y, 240, 20, categoryLabel()));
        y += 24;

        if (!draft.isCategory()) {
            this.buttonList.add(new GuiButton(ID_TYPE, left + 60, y, 240, 20, typeLabel()));
            y += 24;

            ActionType type = ActionTypes.get(draft.action == null ? null : draft.action.type);
            if (type != null) {
                for (ActionField field : type.fields) {
                    addFieldControl(field, left, y);
                    y += 22;
                }
            }
        }

        int buttonsY = Math.min(this.height - 28, y + 6);
        this.buttonList.add(new GuiButton(ID_SAVE, left, buttonsY, 96, 20, I18n.format("radialmenu.editor.save")));
        this.buttonList
            .add(new GuiButton(ID_DELETE, left + 102, buttonsY, 96, 20, I18n.format("radialmenu.editor.delete")));
        this.buttonList.add(new GuiButton(ID_CANCEL, left + 204, buttonsY, 96, 20, I18n.format("gui.cancel")));
    }

    private void addFieldControl(ActionField field, int left, int y) {
        int index = editableFields.size();
        editableFields.add(field);

        String current = draft.action.getString(field.key, field.defaultValue);

        switch (field.kind) {
            case KEYBIND_REF:
            case MULTILINE_STRING:
            case BOOLEAN:
            case ENUM:
            case PROFILE_REF:
                this.buttonList.add(
                    new GuiButton(ID_FIELD_BASE + index, left + 120, y, 180, 20, buttonValueLabel(field, current)));
                fieldInputs.add(null);
                break;
            default:
                GuiTextField input = new GuiTextField(this.fontRendererObj, left + 122, y + 3, 176, 14);
                input.setMaxStringLength(256);
                input.setText(current == null ? "" : current);
                fieldInputs.add(input);
                break;
        }
    }

    private String buttonValueLabel(ActionField field, String current) {
        if (field.kind == ActionField.Kind.KEYBIND_REF) {
            return current == null || current.isEmpty() ? I18n.format("radialmenu.editor.pickKeybind")
                : I18n.format(current);
        }
        if (field.kind == ActionField.Kind.MULTILINE_STRING) {
            // The text itself will not fit on a button, so show how much of it there is.
            int count = Placeholders.splitLines(current).length;
            return I18n.format("radialmenu.editor.editLines", count);
        }
        return current == null ? "" : current;
    }

    private String iconLabel() {
        IconSpec icon = draft.icon;
        return I18n.format("radialmenu.editor.icon") + ": " + (icon == null || icon.id == null ? "-" : icon.id);
    }

    private String keepOpenLabel() {
        return I18n.format("radialmenu.editor.keepOpen") + ": "
            + I18n.format(draft.keepOpen ? "options.on" : "options.off");
    }

    private String categoryLabel() {
        return I18n.format(draft.isCategory() ? "radialmenu.editor.isCategory" : "radialmenu.editor.isAction");
    }

    private String typeLabel() {
        ActionType type = ActionTypes.get(draft.action == null ? null : draft.action.type);
        return I18n.format("radialmenu.editor.actionType") + ": " + (type == null ? "?" : I18n.format(type.labelKey));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case ID_SAVE:
                save();
                return;
            case ID_CANCEL:
                GuiStack.pop();
                return;
            case ID_DELETE:
                deleteEntry();
                return;
            case ID_ICON:
                captureInputs();
                GuiStack.push(new GuiIconPicker(this, draft.icon == null ? null : draft.icon.color));
                return;
            case ID_KEEP_OPEN:
                draft.keepOpen = !draft.keepOpen;
                button.displayString = keepOpenLabel();
                return;
            case ID_MAKE_CATEGORY:
                captureInputs();
                toggleCategory();
                return;
            case ID_TYPE:
                captureInputs();
                cycleActionType();
                return;
            default:
                break;
        }

        int fieldIndex = button.id - ID_FIELD_BASE;
        if (fieldIndex >= 0 && fieldIndex < editableFields.size()) {
            onFieldButton(editableFields.get(fieldIndex), button);
        }
    }

    private void onFieldButton(ActionField field, GuiButton button) {
        String current = draft.action.getString(field.key, field.defaultValue);

        switch (field.kind) {
            case KEYBIND_REF:
                captureInputs();
                GuiStack.push(new GuiKeyBindPicker(this));
                return;
            case MULTILINE_STRING:
                captureInputs();
                openLineEditor(field);
                return;
            case BOOLEAN:
                draft.action.set(field.key, Boolean.toString(!Boolean.parseBoolean(current)));
                break;
            case ENUM:
                draft.action.set(field.key, next(field.options, current));
                break;
            case PROFILE_REF:
                draft.action.set(field.key, next(ProfileStorage.listProfileNames(), current));
                break;
            default:
                return;
        }
        button.displayString = buttonValueLabel(field, draft.action.getString(field.key, ""));
    }

    private void openLineEditor(final ActionField field) {
        GuiStack.push(
            new GuiMultilineEditor(
                field.labelKey,
                draft.action.getString(field.key, ""),
                new GuiMultilineEditor.Result() {

                    @Override
                    public void onLinesEdited(String text) {
                        draft.action.set(field.key, text);
                        rebuildPending = true;
                    }
                }));
    }

    private static String next(List<String> options, String current) {
        if (options.isEmpty()) {
            return current == null ? "" : current;
        }
        int index = options.indexOf(current);
        return options.get((index + 1) % options.size());
    }

    private void cycleActionType() {
        List<ActionType> types = ActionTypes.all();
        if (types.isEmpty()) {
            return;
        }
        String currentId = draft.action == null ? null : draft.action.type;
        int index = -1;
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).id.equals(currentId)) {
                index = i;
                break;
            }
        }
        draft.action = types.get((index + 1) % types.size())
            .newSpec();
        rebuildPending = true;
    }

    private void toggleCategory() {
        if (draft.isCategory()) {
            draft.children = null;
            draft.layout = null;
            draft.action = ActionTypes.get(ActionTypes.KEYBIND)
                .newSpec();
        } else {
            draft.action = null;
            draft.layout = SlotLayout.fixed(SlotLayout.DEFAULT_SLOTS);
            draft.children = new ArrayList<>();
            draft.ensureSlotCapacity();
        }
        rebuildPending = true;
    }

    /**
     * Copies what is typed in the text boxes into the draft.
     *
     * <p>
     * Called before opening a picker as well as on save, because returning from a picker re-runs initGui and rebuilds
     * the boxes from the draft - anything typed but not captured would be silently reverted.
     */
    private void captureInputs() {
        if (titleField != null) {
            draft.title = titleField.getText()
                .trim();
        }
        for (int i = 0; i < editableFields.size() && i < fieldInputs.size(); i++) {
            GuiTextField input = fieldInputs.get(i);
            if (input != null && draft.action != null) {
                draft.action.set(editableFields.get(i).key, input.getText());
            }
        }
    }

    private void save() {
        captureInputs();
        draft.normalize();
        setChild(draft);
        persist();
    }

    private void deleteEntry() {
        setChild(null);
        persist();
    }

    private void setChild(MenuNode node) {
        parent.setChildAt(slotIndex, node);
    }

    private void persist() {
        ProfileManager.active()
            .normalize();
        ProfileManager.saveActive();
        IconRenderer.clearCache();
        GuiStack.pop();
    }

    @Override
    public void onKeyBindPicked(String description, String category) {
        draft.action.set(ActionTypes.PARAM_BINDING, description);
        draft.action.set(ActionTypes.PARAM_CATEGORY, category == null ? "" : category);
        rebuildPending = true;
    }

    @Override
    public void onIconPicked(IconSpec icon) {
        draft.icon = icon;
        rebuildPending = true;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (rebuildPending) {
            rebuildPending = false;
            rebuildControls(this.width / 2 - 150, 34);
        }

        this.drawDefaultBackground();
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("radialmenu.editor.slot", slotIndex + 1),
            this.width / 2,
            12,
            0xFFFFFF);

        int left = this.width / 2 - 150;
        this.fontRendererObj.drawString(I18n.format("radialmenu.editor.title"), left, 38, 0xA0A0A0);
        titleField.drawTextBox();

        for (int i = 0; i < editableFields.size(); i++) {
            GuiTextField input = fieldInputs.get(i);
            int y = input != null ? input.yPosition - 3 : labelYForButton(i);
            this.fontRendererObj.drawString(I18n.format(editableFields.get(i).labelKey), left, y + 6, 0xA0A0A0);
            if (input != null) {
                input.drawTextBox();
            }
        }

        if (draft.icon != null) {
            IconRenderer.draw(draft.icon, left + 20, 58);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int labelYForButton(int fieldIndex) {
        for (Object raw : this.buttonList) {
            GuiButton button = (GuiButton) raw;
            if (button.id == ID_FIELD_BASE + fieldIndex) {
                return button.yPosition;
            }
        }
        return 0;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        titleField.mouseClicked(mouseX, mouseY, button);
        for (GuiTextField input : fieldInputs) {
            if (input != null) {
                input.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            GuiStack.pop();
            return;
        }
        if (titleField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        for (GuiTextField input : fieldInputs) {
            if (input != null && input.textboxKeyTyped(typedChar, keyCode)) {
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
