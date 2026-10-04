package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiColorButton;
import com.navatusein.radialmenu.client.gui.ui.UiDragReorder;
import com.navatusein.radialmenu.client.gui.ui.UiIconButton;
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
    private static final int ID_COPY_BASE = 400;
    private static final int ID_DELETE_BASE = 500;
    private static final int ID_MOVE_BASE = 600;

    private static final int ARROW = 18;

    /** Between the group that changes what an entry is and the group that changes where it sits. */
    private static final int GROUP_GAP = 6;

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

    /**
     * Which entry of {@link #workingChildren} each drawn row is.
     *
     * <p>
     * Not the same number: a fixed wheel keeps its empty slots in the list and they are not drawn, so the fourth row on
     * screen can be the sixth entry. The arrows have always worked in list positions; dragging works in rows, and this
     * is where the two meet.
     */
    private final List<Integer> rowIndices = new ArrayList<>();

    private final UiDragReorder drag = new UiDragReorder();

    /**
     * Entries on their way to another menu, applied when this screen is saved.
     *
     * <p>
     * Deferred for the same reason the reordering is: nothing a player does here touches the profile until they say
     * so. Writing the entry into its new menu on the spot and only removing it from this one on save would leave it
     * in both places the moment they changed their mind, which is the one outcome a move must never have.
     */
    private final List<MenuNode[]> pendingMoves = new ArrayList<>();

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
        for (int fieldIndex = 0; fieldIndex < type.fields.size(); fieldIndex++) {
            ActionField field = type.fields.get(fieldIndex);
            if (!FieldControls.isRelevant(field, draft)) {
                continue;
            }
            ActionField nextField = fieldIndex + 1 < type.fields.size() ? type.fields.get(fieldIndex + 1) : null;
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
                    GuiButton clear = new UiIconButton(
                        ID_CLEAR_BASE + index,
                        controlLeft + mainWidth + Ui.GAP,
                        y,
                        FieldControls.CLEAR_WIDTH,
                        Ui.ROW,
                        UiIconButton.Icon.CROSS);
                    // Clearing is not gated on editable: the colour is still stored while that part of the wheel is
                    // off,
                    // and dropping it is how the row goes back to inheriting.
                    clear.enabled = current != null && !current.trim()
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
            y += FieldControls.stepAfter(field, nextField);
            if (FieldControls.standsApart(field)) {
                y += Ui.GAP;
            }
        }

        y += Ui.GAP;
        entriesSectionTop = y;
        y += 14;

        rowPositions.clear();
        rowNodes.clear();
        rowIndices.clear();

        for (int i = 0; i < workingChildren.size(); i++) {
            MenuNode child = workingChildren.get(i);
            if (child == null) {
                continue;
            }

            GuiButton move = new UiIconButton(
                ID_MOVE_BASE + i,
                rowButtonsLeft(),
                y,
                ARROW,
                Ui.ROW,
                UiIconButton.Icon.RIGHT);
            GuiButton copy = new UiIconButton(
                ID_COPY_BASE + i,
                rowButtonsLeft() + ARROW + 2,
                y,
                ARROW,
                Ui.ROW,
                UiIconButton.Icon.COPY);
            GuiButton delete = new UiIconButton(
                ID_DELETE_BASE + i,
                rowButtonsLeft() + (ARROW + 2) * 2,
                y,
                ARROW,
                Ui.ROW,
                UiIconButton.Icon.CROSS);
            GuiButton up = new UiIconButton(
                ID_UP_BASE + i,
                contentRight() - ARROW * 2 - 2,
                y,
                ARROW,
                Ui.ROW,
                UiIconButton.Icon.UP);
            GuiButton down = new UiIconButton(
                ID_DOWN_BASE + i,
                contentRight() - ARROW,
                y,
                ARROW,
                Ui.ROW,
                UiIconButton.Icon.DOWN);
            up.enabled = canMove(i, -1);
            down.enabled = canMove(i, 1);
            this.buttonList.add(move);
            this.buttonList.add(copy);
            this.buttonList.add(delete);
            this.buttonList.add(up);
            this.buttonList.add(down);
            tooltip(ID_MOVE_BASE + i, describe("radialmenu.menu.move.tip"));
            tooltip(ID_COPY_BASE + i, describe("radialmenu.menu.copy.tip"));
            tooltip(ID_DELETE_BASE + i, describe("radialmenu.menu.remove.tip"));

            rowPositions.add(new int[] { left, y });
            rowNodes.add(child);
            rowIndices.add(Integer.valueOf(i));
            y += Ui.STEP;
        }

        // The queued moves are drawn under the list, so they are part of what scrolls - left out of the height
        // they would sit outside the clip and be cut off by the frame.
        y += pendingMoves.size() * 10;

        setContentHeight(y - scrolledTop() + Ui.PAD);
        addBottomBar("radialmenu.editor.save", null, "gui.cancel");
    }

    /**
     * Where a row's buttons start.
     *
     * <p>
     * Worked out once because the title is cut to fit against it: two copies of this arithmetic is how a name comes
     * to run under the buttons on one screen and stop short of them on another.
     */
    private int rowButtonsLeft() {
        return contentRight() - ARROW * 5 - 4 - GROUP_GAP;
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

        int copyIndex = button.id - ID_COPY_BASE;
        if (copyIndex >= 0 && copyIndex < workingChildren.size()) {
            capture();
            duplicate(copyIndex);
            requestRebuild();
            return;
        }
        int deleteIndex = button.id - ID_DELETE_BASE;
        if (deleteIndex >= 0 && deleteIndex < workingChildren.size()) {
            capture();
            remove(deleteIndex);
            requestRebuild();
            return;
        }
        int moveIndex = button.id - ID_MOVE_BASE;
        if (moveIndex >= 0 && moveIndex < workingChildren.size()) {
            capture();
            askWhereToMove(moveIndex);
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

    /**
     * Puts a copy of an entry beside the original.
     *
     * <p>
     * Where "beside" is depends on the layout, because that is what the player sees. A dynamic wheel is a list, so
     * the copy goes straight after. A fixed one is a set of angles, and inserting into it would shift every entry
     * after this one onto a different sector - so the copy takes the first free position instead, and only widens
     * the wheel when there is none.
     */
    private void duplicate(int index) {
        MenuNode original = workingChildren.get(index);
        if (original == null) {
            return;
        }
        MenuNode copy = original.copy();

        if (!isFixedLayout()) {
            workingChildren.add(index + 1, copy);
            return;
        }
        for (int i = index + 1; i < workingChildren.size(); i++) {
            if (workingChildren.get(i) == null) {
                workingChildren.set(i, copy);
                return;
            }
        }
        workingChildren.add(copy);
    }

    /**
     * Takes an entry off the wheel, leaving the position it held.
     *
     * <p>
     * The same thing the slot editor's delete does: the position stays as an empty one rather than closing up, so a
     * fixed wheel keeps every other entry on the angle the player memorised. A dynamic wheel does not draw it at
     * all, and the next entry added reuses it.
     *
     * <p>
     * Nothing is written until this screen is saved, so a misplaced click is undone by cancelling - which is why
     * there is no question asked first.
     */
    private void remove(int index) {
        MenuNode removed = workingChildren.get(index);
        workingChildren.set(index, null);
        // An entry queued to move into a submenu that is itself being deleted would arrive nowhere.
        dropMovesInto(removed);
    }

    private void dropMovesInto(MenuNode gone) {
        if (gone == null || !gone.isCategory()) {
            return;
        }
        for (int i = pendingMoves.size() - 1; i >= 0; i--) {
            if (pendingMoves.get(i)[1] == gone) {
                pendingMoves.remove(i);
            }
        }
    }

    /**
     * Asks which menu an entry should go to, and queues the move.
     *
     * <p>
     * The row disappears as soon as the destination is chosen, which is how the player sees that the move was taken;
     * where it has gone is said under the list until the screen is saved.
     */
    private void askWhereToMove(final int index) {
        final MenuNode moved = workingChildren.get(index);
        if (moved == null) {
            return;
        }
        GuiStack.push(new GuiMenuPicker(menu, moved, new GuiMenuPicker.Result() {

            @Override
            public void onMenuPicked(MenuNode target) {
                if (target == null || target == menu) {
                    return;
                }
                workingChildren.set(index, null);
                pendingMoves.add(new MenuNode[] { moved, target });
                requestRebuild();
            }
        }));
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

        // After this menu's own list, so an entry moving into one of its own submenus is written to a node that is
        // already where it will stay. Appended through the same position the wheel uses for a new entry, which
        // reuses an empty position at the end rather than widening a fixed layout by however many it carried.
        for (MenuNode[] move : pendingMoves) {
            MenuNode node = move[0];
            MenuNode target = move[1];
            target.setChildAt(target.appendIndex(), node);
            target.ensureSlotCapacity();
        }

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
            int iconLeft = position[0] + UiDragReorder.GRIP + 2;

            if (drag.isDragging() && drag.grabbedRow() == i) {
                // The row being carried, marked where it came from: without it, a drag of one row in a list of
                // identical-looking rows gives no clue what is moving.
                Gui.drawRect(position[0], position[1], contentRight(), position[1] + Ui.ROW, Ui.ROW_SELECTED);
            }
            UiDragReorder.drawGrip(position[0], position[1], Ui.ROW, drag.isDragging() && drag.grabbedRow() == i);

            // Drawn here, with the content, rather than over the arrows - an item rendered after a button comes out
            // unlit.
            IconRenderer.draw(child.icon, iconLeft, position[1] + 2);

            String title = child.title == null || child.title.isEmpty() ? I18n.format("radialmenu.menu.untitled")
                : child.title;
            this.fontRendererObj.drawString(
                Ui.fit(title, rowButtonsLeft() - iconLeft - 24),
                iconLeft + 20,
                position[1] + (Ui.ROW - 8) / 2,
                Ui.TEXT);
        }

        drawPendingMoves();

        drag.drawInsertion(rows(), contentLeft(), contentRight());
        scrollTowardsDrag();
    }

    /**
     * Says where entries have gone, for as long as they have not gone there yet.
     *
     * <p>
     * A row that vanished on a click is a row the player has to take on trust. One line under the list saying what
     * left and where to is the difference between a move and a disappearance.
     */
    private void drawPendingMoves() {
        if (pendingMoves.isEmpty()) {
            return;
        }
        int y = rowPositions.isEmpty() ? entriesSectionTop + 18
            : rowPositions.get(rowPositions.size() - 1)[1] + Ui.STEP;
        for (MenuNode[] move : pendingMoves) {
            if (!isVisibleRow(y)) {
                y += 10;
                continue;
            }
            this.fontRendererObj.drawString(
                Ui.fit(I18n.format("radialmenu.menu.movePending", name(move[0]), name(move[1])), contentWidth()),
                contentLeft(),
                y + 2,
                Ui.TEXT_MUTED);
            y += 10;
        }
    }

    private String name(MenuNode node) {
        if (node == ProfileManager.active().root) {
            return ProfileManager.activeName();
        }
        return node.title == null || node.title.isEmpty() ? I18n.format("radialmenu.menu.untitled") : node.title;
    }

    /**
     * Scrolls the list while a row is held past its edge.
     *
     * <p>
     * Without it a list longer than the panel can only be reordered as far as the screen reaches, and the player has to
     * drop the row, scroll, and pick it up again - which is the arrows with extra steps.
     */
    private void scrollTowardsDrag() {
        if (!drag.isDragging()) {
            return;
        }
        int overshoot = drag.overshoot(rows());
        if (overshoot == 0) {
            return;
        }
        int step = Math.max(-6, Math.min(6, overshoot));
        int next = Math.max(0, Math.min(maxScroll(), scrollOffset + step));
        if (next != scrollOffset) {
            beforeScroll();
            scrollOffset = next;
            requestRebuild();
        }
    }

    /** Where the entry rows are, for the drag gesture. */
    private UiDragReorder.Rows rows() {
        return new UiDragReorder.Rows() {

            @Override
            public int count() {
                return rowPositions.size();
            }

            @Override
            public int top(int row) {
                return rowPositions.get(row)[1];
            }

            @Override
            public int height() {
                return Ui.ROW;
            }
        };
    }

    /**
     * Moves a drawn row to where it was dropped, one step at a time.
     *
     * <p>
     * Through {@link MenuNode#moveInList} rather than by cutting and inserting, so a drag is exactly what pressing the
     * arrow that many times would do - including walking an entry through the empty slots of a fixed wheel, where an
     * insertion would instead shift every entry after it onto a different sector.
     */
    private void moveRow(int from, int to) {
        if (from < 0 || to < 0 || from == to || from >= rowIndices.size() || to >= rowIndices.size()) {
            return;
        }

        int direction = to > from ? 1 : -1;
        int index = rowIndices.get(from)
            .intValue();
        int steps = 0;
        int cap = workingChildren.size() * 2;

        while (rowOf(index) != to && steps++ < cap) {
            if (!MenuNode.moveInList(workingChildren, index, direction, isFixedLayout())) {
                break;
            }
            index = workingChildren.indexOf(rowNodes.get(from));
        }
    }

    /** Which drawn row a list position is, counting only the entries that are drawn. */
    private int rowOf(int index) {
        int row = 0;
        for (int i = 0; i < index && i < workingChildren.size(); i++) {
            if (workingChildren.get(i) != null) {
                row++;
            }
        }
        return row;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        // Before super, and the whole point of the grip: a press that lands on one must not reach a button at all.
        if (button == 0 && isInsideViewport(mouseY) && drag.press(mouseX, mouseY, contentLeft(), rows())) {
            return;
        }
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
    protected void mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceClick) {
        drag.moveTo(mouseY);
        super.mouseClickMove(mouseX, mouseY, mouseButton, timeSinceClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (which == 0) {
            int from = drag.grabbedRow();
            int to = drag.release(rows());
            if (to >= 0 && to != from) {
                capture();
                moveRow(from, to);
                requestRebuild();
            }
        }
        super.mouseMovedOrUp(mouseX, mouseY, which);
    }

    @Override
    protected boolean handleKey(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE && drag.isDragging()) {
            // Escape puts a half-made drag back rather than leaving the screen with it still held.
            drag.cancel();
            return true;
        }
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
