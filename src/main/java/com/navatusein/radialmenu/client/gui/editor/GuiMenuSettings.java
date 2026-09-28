package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiColorButton;
import com.navatusein.radialmenu.client.gui.ui.UiScreen;
import com.navatusein.radialmenu.client.icon.IconRenderer;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.config.ColorConfig;
import com.navatusein.radialmenu.core.action.ActionField;
import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionType;
import com.navatusein.radialmenu.core.action.ActionTypes;
import com.navatusein.radialmenu.core.model.MenuNode;
import com.navatusein.radialmenu.core.model.SlotLayout;
import com.navatusein.radialmenu.core.model.StyleResolver;

import cpw.mods.fml.client.config.GuiSlider;

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
    private static final int ID_CLEAR_BASE = 100;
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

    /**
     * Where each field row was actually put.
     *
     * <p>
     * Recorded rather than recomputed when the labels are drawn: a row that stands apart carries a gap the drawing
     * loop would have to know about too, and two copies of that arithmetic is how a label ends up beside the wrong
     * control.
     */
    private final List<Integer> fieldRowTops = new ArrayList<>();
    private final List<GuiTextField> inputs = new ArrayList<>();

    /** Parallel to the fields, like the text boxes: a slider holds its value in the widget rather than in the spec. */
    private final List<GuiSlider> sliders = new ArrayList<>();

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
    protected boolean framedViewport() {
        return true;
    }

    @Override
    protected void buildControls() {
        fields.clear();
        inputs.clear();
        sliders.clear();
        fieldRowTops.clear();

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
            sliders.add(null);

            if (FieldControls.standsApart(field)) {
                y += Ui.GAP;
            }
            fieldRowTops.add(Integer.valueOf(y));

            if (FieldControls.isButton(field)) {
                int mainWidth = FieldControls.hasClear(field) ? controlWidth - Ui.GAP - FieldControls.CLEAR_WIDTH
                    : controlWidth;

                boolean editable = field.kind != ActionField.Kind.COLOR || FieldControls.colorEnabled(field.key);

                GuiButton main = field.kind == ActionField.Kind.COLOR
                    ? colorButton(ID_FIELD_BASE + index, field, controlLeft, y, mainWidth)
                    : new GuiButton(
                        ID_FIELD_BASE + index,
                        controlLeft,
                        y,
                        mainWidth,
                        Ui.ROW,
                        FieldControls.buttonLabel(field, current));
                main.enabled = editable;
                this.buttonList.add(main);
                inputs.add(null);

                if (FieldControls.hasClear(field)) {
                    GuiButton clear = new GuiButton(
                        ID_CLEAR_BASE + index,
                        controlLeft + mainWidth + Ui.GAP,
                        y,
                        FieldControls.CLEAR_WIDTH,
                        Ui.ROW,
                        "x");
                    clear.enabled = editable && current != null
                        && !current.trim()
                            .isEmpty();
                    this.buttonList.add(clear);
                    tooltip(ID_CLEAR_BASE + index, describe("radialmenu.profileColors.clear.tip"));
                }
                if (!editable) {
                    tooltip(ID_FIELD_BASE + index, FieldControls.colorOffTip(field.key));
                }
            } else if (field.hasRange()) {
                GuiSlider slider = FieldControls
                    .slider(ID_FIELD_BASE + index, controlLeft, y, controlWidth, Ui.ROW, field, current);
                this.buttonList.add(slider);
                sliders.set(index, slider);
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
            if (FieldControls.standsApart(field)) {
                y += Ui.GAP;
            }
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

    /**
     * Where the accent picker opens.
     *
     * <p>
     * On what this wheel is highlighted with now: an accent is not stored anywhere, so there is no last value to go
     * back to, and the highlight is the colour a player would call the menu's.
     */
    private static String accentStart() {
        int highlight = StyleResolver
            .resolve(ColorConfig.defaultColors(), ProfileManager.active().style, null).highlight;
        return String.format("#%06X", Integer.valueOf(highlight & 0x00FFFFFF));
    }

    /** A colour row: the value read live from the draft, beside a square of what it comes out as. */
    private UiColorButton colorButton(int id, final ActionField field, int x, int y, int width) {
        return new UiColorButton(id, x, y, width, new UiColorButton.Value() {

            @Override
            public String get() {
                return draft.getString(field.key, "");
            }
        }, FieldControls.inheritedColor(field.key), I18n.format("radialmenu.editor.inherit"));
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

        int clearIndex = button.id - ID_CLEAR_BASE;
        if (clearIndex >= 0 && clearIndex < fields.size()) {
            capture();
            draft.set(fields.get(clearIndex).key, "");
            requestRebuild();
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

        if (field.kind == ActionField.Kind.ACCENT) {
            capture();
            GuiStack.push(new GuiColorPicker(accentStart(), false, new GuiColorPicker.Result() {

                @Override
                public void onColorPicked(String hex) {
                    FieldControls.applyAccent(draft, field, hex);
                    requestRebuild();
                }
            }));
            return;
        }

        if (field.kind == ActionField.Kind.ENUM) {
            // Rebuilding throws away anything typed but not yet read back, so it is read back first.
            capture();
            int at = field.options.indexOf(current);
            String next = field.options.get((at + 1) % field.options.size());
            draft.set(field.key, next);
            // The layout decides whether an arrow can move an entry into a gap, so the rows have to be rebuilt with
            // it rather than only relabelled.
            requestRebuild();
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
            GuiSlider slider = sliders.get(i);
            if (slider != null) {
                draft.set(fields.get(i).key, Integer.toString(slider.getValueInt()));
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

        for (int i = 0; i < fields.size() && i < fieldRowTops.size(); i++) {
            int rowTop = fieldRowTops.get(i)
                .intValue();
            if (isVisibleRow(rowTop)) {
                Ui.rowLabel(Ui.fit(I18n.format(fields.get(i).labelKey), Ui.LABEL_WIDTH), left, rowTop);
            }
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
        // A field clipped at the panel edge still answers to clicks on the part that was cut away.
        if (!isInsideViewport(mouseY)) {
            return;
        }
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
