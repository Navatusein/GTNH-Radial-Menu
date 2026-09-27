package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiScreen;
import com.navatusein.radialmenu.client.icon.IconRenderer;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.core.action.ActionField;
import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionType;
import com.navatusein.radialmenu.core.action.ActionTypes;
import com.navatusein.radialmenu.core.model.MenuNode;
import com.navatusein.radialmenu.core.model.SlotLayout;

/**
 * Settings of the wheel currently on screen: its name, how its slots are laid out, and its colours.
 *
 * <p>
 * Works at any depth, root included. A submenu's settings are also reachable from its slot in the parent, but that is
 * no help once you have drilled into it - and the root menu had nowhere to be configured from at all.
 *
 * <p>
 * The fields come from the submenu action type's descriptors, so this screen and the slot editor cannot drift apart
 * about what a menu is allowed to be.
 */
public class GuiMenuSettings extends UiScreen {

    private static final int ID_FIELD_BASE = 40;
    private static final int ID_UP_BASE = 200;
    private static final int ID_DOWN_BASE = 300;

    private static final int ARROW = 18;

    private final MenuNode menu;

    /** A view of the node's layout and colours the generic field code can drive. */
    private final ActionSpec draft;

    private GuiTextField titleField;

    /** Survives control rebuilds, which recreate the field. */
    private String titleText;

    private final List<ActionField> fields = new ArrayList<>();
    private final List<GuiTextField> inputs = new ArrayList<>();

    /**
     * The menu's entries, reordered here and written back only on save.
     *
     * <p>
     * A copy of the list, holding the same nodes: moving swaps references, so cancelling leaves the real menu
     * untouched while saving carries the new order over wholesale.
     */
    private final List<MenuNode> workingChildren;

    /** Where each drawn row sits, so the icon and title can be drawn with the content rather than over a button. */
    private final List<int[]> rowPositions = new ArrayList<>();
    private final List<MenuNode> rowNodes = new ArrayList<>();

    private int generalSectionTop;
    private int entriesSectionTop;

    public GuiMenuSettings(MenuNode menu) {
        this.menu = menu;
        this.draft = SubmenuFields.toSpec(menu);
        this.titleText = menu.title == null ? "" : menu.title;
        this.workingChildren = new ArrayList<>(menu.childrenOrEmpty());
    }

    @Override
    protected String titleKey() {
        return "radialmenu.menu.title";
    }

    @Override
    protected int panelWidth() {
        return 330;
    }

    @Override
    protected void buildControls() {
        fields.clear();
        inputs.clear();

        int left = contentLeft();
        int controlLeft = left + Ui.LABEL_WIDTH + Ui.GAP;
        int controlWidth = contentRight() - controlLeft;

        generalSectionTop = scrolledTop();
        int y = generalSectionTop + 14;

        // Built at its real size: GuiTextField works out its horizontal scroll from the width it has when the
        // text is set, so a placeholder-sized field resized afterwards renders blank until it is clicked.
        titleField = new GuiTextField(this.fontRendererObj, controlLeft + 1, y + 3, controlWidth - 2, 14);
        titleField.setMaxStringLength(48);
        titleField.setText(titleText);
        titleField.setCursorPositionZero();
        y += Ui.STEP;

        ActionType type = ActionTypes.get(ActionTypes.SUBMENU);
        for (ActionField field : type.fields) {
            int index = fields.size();
            fields.add(field);
            tooltip(ID_FIELD_BASE + index, describe(field.tooltipKey()));

            String current = draft.getString(field.key, field.defaultValue);
            if (field.kind == ActionField.Kind.ENUM || field.kind == ActionField.Kind.COLOR) {
                this.buttonList.add(
                    new GuiButton(ID_FIELD_BASE + index, controlLeft, y, controlWidth, Ui.ROW, label(field, current)));
                inputs.add(null);
            } else {
                GuiTextField input = new GuiTextField(
                    this.fontRendererObj,
                    controlLeft + 1,
                    y + 3,
                    controlWidth - 2,
                    14);
                input.setMaxStringLength(8);
                input.setText(current == null ? "" : current);
                inputs.add(input);
            }
            y += Ui.STEP;
        }

        y += Ui.GAP;
        entriesSectionTop = y;
        y += 14;

        rowPositions.clear();
        rowNodes.clear();

        for (int i = 0; i < workingChildren.size(); i++) {
            MenuNode child = workingChildren.get(i);
            if (child == null) {
                continue;
            }

            GuiButton up = new GuiButton(ID_UP_BASE + i, contentRight() - ARROW * 2 - 2, y, ARROW, Ui.ROW, "^");
            GuiButton down = new GuiButton(ID_DOWN_BASE + i, contentRight() - ARROW, y, ARROW, Ui.ROW, "v");
            up.enabled = canMove(i, -1);
            down.enabled = canMove(i, 1);
            this.buttonList.add(up);
            this.buttonList.add(down);

            rowPositions.add(new int[] { left, y });
            rowNodes.add(child);
            y += Ui.STEP;
        }

        setContentHeight(y - scrolledTop() + Ui.PAD);
        addBottomBar("radialmenu.editor.save", null, "gui.cancel");
    }

    /** Whether a move would change anything, so a button that cannot act is visibly disabled. */
    private boolean canMove(int index, int direction) {
        List<MenuNode> probe = new ArrayList<>(workingChildren);
        return MenuNode.moveInList(probe, index, direction, isFixedLayout());
    }

    /** The layout as chosen on screen, which may not yet be the one saved on the node. */
    private boolean isFixedLayout() {
        return draft.getEnum(ActionTypes.PARAM_SLOT_MODE, SlotLayout.Mode.class, SlotLayout.Mode.FIXED)
            == SlotLayout.Mode.FIXED;
    }

    private String label(ActionField field, String current) {
        if (field.kind == ActionField.Kind.COLOR) {
            return current == null || current.trim()
                .isEmpty() ? I18n.format("radialmenu.editor.inherit") : current;
        }
        String key = field.valueLabelKey(current);
        String translated = I18n.format(key);
        return translated.equals(key) ? current : translated;
    }

    private static String describe(String key) {
        String translated = I18n.format(key);
        return translated.equals(key) ? null : translated;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_PRIMARY) {
            save();
            return;
        }
        if (button.id == ID_SECONDARY) {
            onCancel();
            return;
        }

        int upIndex = button.id - ID_UP_BASE;
        if (upIndex >= 0 && upIndex < workingChildren.size()) {
            capture();
            MenuNode.moveInList(workingChildren, upIndex, -1, isFixedLayout());
            requestRebuild();
            return;
        }
        int downIndex = button.id - ID_DOWN_BASE;
        if (downIndex >= 0 && downIndex < workingChildren.size()) {
            capture();
            MenuNode.moveInList(workingChildren, downIndex, 1, isFixedLayout());
            requestRebuild();
            return;
        }

        int index = button.id - ID_FIELD_BASE;
        if (index < 0 || index >= fields.size()) {
            return;
        }
        final ActionField field = fields.get(index);
        String current = draft.getString(field.key, field.defaultValue);

        if (field.kind == ActionField.Kind.COLOR) {
            capture();
            GuiStack.push(new GuiColorPicker(current, true, new GuiColorPicker.Result() {

                @Override
                public void onColorPicked(String hex) {
                    draft.set(field.key, hex);
                    requestRebuild();
                }
            }));
            return;
        }

        if (field.kind == ActionField.Kind.ENUM) {
            int at = field.options.indexOf(current);
            String next = field.options.get((at + 1) % field.options.size());
            draft.set(field.key, next);
            button.displayString = label(field, next);
        }
    }

    private void capture() {
        if (titleField != null) {
            titleText = titleField.getText();
        }
        for (int i = 0; i < fields.size() && i < inputs.size(); i++) {
            GuiTextField input = inputs.get(i);
            if (input != null) {
                draft.set(fields.get(i).key, input.getText());
            }
        }
    }

    private void save() {
        capture();

        String title = titleText.trim();
        menu.title = title.isEmpty() ? null : title;

        menu.children = new ArrayList<>(workingChildren);

        // applyToNode reads the spec off the node, the same way the slot editor hands it over, and clears it
        // afterwards - a category carries no action at any depth.
        menu.action = draft;
        SubmenuFields.applyToNode(menu);

        ProfileManager.active()
            .normalize();
        ProfileManager.saveActive();
        GuiStack.closeAll();
    }

    /** Same as the slot editor: leaving the editor leaves the wheel too. */
    @Override
    protected void onCancel() {
        GuiStack.closeAll();
    }

    @Override
    protected void beforeScroll() {
        capture();
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        int left = contentLeft();

        if (isVisibleRow(generalSectionTop)) {
            Ui.sectionHeader(I18n.format("radialmenu.menu.sectionGeneral"), left, generalSectionTop, contentRight());
        }

        int y = generalSectionTop + 14;

        if (isVisibleRow(y)) {
            Ui.rowLabel(I18n.format("radialmenu.menu.name"), left, y);
            titleField.drawTextBox();
        }
        y += Ui.STEP;

        for (ActionField field : fields) {
            if (isVisibleRow(y)) {
                Ui.rowLabel(Ui.fit(I18n.format(field.labelKey), Ui.LABEL_WIDTH), left, y);
            }
            y += Ui.STEP;
        }

        for (GuiTextField input : inputs) {
            if (input != null && isVisibleRow(input.yPosition - 3)) {
                input.drawTextBox();
            }
        }

        if (isVisibleRow(entriesSectionTop)) {
            Ui.sectionHeader(I18n.format("radialmenu.menu.entries"), left, entriesSectionTop, contentRight());
        }

        if (rowNodes.isEmpty() && isVisibleRow(entriesSectionTop + 14)) {
            this.fontRendererObj
                .drawString(I18n.format("radialmenu.menu.noEntries"), left, entriesSectionTop + 18, Ui.TEXT_MUTED);
        }

        for (int i = 0; i < rowNodes.size(); i++) {
            int[] position = rowPositions.get(i);
            if (!isVisibleRow(position[1])) {
                continue;
            }
            MenuNode child = rowNodes.get(i);

            // Drawn here, with the content, rather than over the arrows - an item rendered after a button comes out
            // unlit.
            IconRenderer.draw(child.icon, position[0] + 2, position[1] + 2);

            String title = child.title == null || child.title.isEmpty() ? I18n.format("radialmenu.menu.untitled")
                : child.title;
            this.fontRendererObj.drawString(
                Ui.fit(title, contentWidth() - ARROW * 2 - 28),
                position[0] + 22,
                position[1] + (Ui.ROW - 8) / 2,
                Ui.TEXT);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        titleField.mouseClicked(mouseX, mouseY, button);
        for (GuiTextField input : inputs) {
            if (input != null) {
                input.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    @Override
    protected boolean handleKey(char typedChar, int keyCode) {
        if (titleField.textboxKeyTyped(typedChar, keyCode)) {
            return true;
        }
        for (GuiTextField input : inputs) {
            if (input != null && input.textboxKeyTyped(typedChar, keyCode)) {
                return true;
            }
        }
        return false;
    }
}
