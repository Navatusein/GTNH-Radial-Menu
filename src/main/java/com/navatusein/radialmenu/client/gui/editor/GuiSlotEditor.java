package com.navatusein.radialmenu.client.gui.editor;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiCheckbox;
import com.navatusein.radialmenu.client.icon.IconRenderer;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.core.action.ActionType;
import com.navatusein.radialmenu.core.action.ActionTypes;
import com.navatusein.radialmenu.core.model.IconSpec;
import com.navatusein.radialmenu.core.model.MenuNode;

/**
 * Edits one slot.
 *
 * <p>
 * Two sections - what the entry looks like, and what it does - because those are decided at different moments, and
 * mixing them left the screen an undifferentiated column of buttons. The second section is
 * {@link GuiActionEditor}, shared with the editor for a chain's steps.
 *
 * <p>
 * "Submenu" is one of the action-type tabs rather than a separate switch. A slot does one thing, and opening a
 * submenu is one of the things it can do; as a separate mode it meant two controls had to agree with each other. Its
 * settings - slot count, fixed or dynamic, and the colours - are simply that type's fields.
 */
public class GuiSlotEditor extends GuiActionEditor implements GuiIconPicker.Callback {

    private static final int ID_ICON = 1;
    private static final int ID_KEEP_OPEN = 2;
    private static final int ID_ENTRIES = 3;

    private final MenuNode parent;

    /**
     * Position in the parent's child list, not the sector it is drawn in.
     *
     * <p>
     * The caller has already translated, because only it knows which sector was clicked and where a new entry
     * should go. Translating again here read the wrong entry on any dynamic wheel whose list still had the gaps a
     * fixed layout left behind.
     */
    private final int childIndex;

    /** Working copy; the parent is only touched on save. */
    private final MenuNode draft;

    private GuiTextField titleField;

    /** Survives control rebuilds, which recreate the field. */
    private String titleText;

    private int actionSectionTop;

    private int iconPreviewLeft;
    private int iconPreviewTop;

    public GuiSlotEditor(MenuNode parent, int childIndex) {
        this.parent = parent;
        this.childIndex = childIndex;

        MenuNode existing = parent.childAtIndex(childIndex);
        this.draft = existing != null ? existing.copy()
            : MenuNode.leaf(
                I18n.format("radialmenu.editor.newEntry"),
                IconSpec.item("minecraft:stone", 0),
                ActionTypes.get(ActionTypes.KEYBIND)
                    .newSpec());

        this.titleText = draft.title == null ? "" : draft.title;

        if (draft.isCategory()) {
            // Submenu settings live on the node, so mirror them into a spec the generic field code can drive.
            setSpec(SubmenuFields.toSpec(draft), ActionTypes.SUBMENU);
        } else if (draft.action != null && ActionTypes.isRegistered(draft.action.type)) {
            setSpec(draft.action, draft.action.type);
        } else {
            // A hand-edited file can name a type no build of the mod has. Showing an empty screen would be worse
            // than showing the default one, and the original is only overwritten if the player saves.
            setSpec(
                ActionTypes.get(ActionTypes.KEYBIND)
                    .newSpec(),
                ActionTypes.KEYBIND);
        }
    }

    @Override
    protected String titleKey() {
        return "radialmenu.editor.slot";
    }

    @Override
    protected Object[] titleArgs() {
        // Numbered by the sector, which is what the player clicked; on a dynamic wheel that is not the list position.
        return new Object[] { Integer.valueOf(parent.slotForChildIndex(childIndex) + 1) };
    }

    @Override
    protected int panelWidth() {
        return 330;
    }

    @Override
    protected boolean framedViewport() {
        return true;
    }

    /** A slot can be a submenu, so it offers every type. */
    @Override
    protected List<ActionType> offeredTypes() {
        return ActionTypes.all();
    }

    @Override
    protected void buildControls() {
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

        y = buildActionSection(y, left, controlLeft, controlWidth);

        // The way into a submenu's own entry list. It is here rather than only in the middle of the wheel because
        // an inline submenu has no middle of its own: it unfolds around its entry, so the entry is the only thing
        // there is to point at. A replacing submenu can still be opened and configured from its own dead zone.
        //
        // Only while the submenu tab is the one on screen. It is already a category that is being looked at as a
        // script, and offering a way into entries that saving is about to discard is offering a trapdoor.
        if (draft.isCategory() && ActionTypes.SUBMENU.equals(selectedType)) {
            y += Ui.GAP;
            this.buttonList.add(
                new GuiButton(
                    ID_ENTRIES,
                    controlLeft,
                    y,
                    controlWidth,
                    Ui.ROW,
                    I18n.format("radialmenu.editor.entries", Integer.valueOf(draft.filledCount()))));
            tooltip(ID_ENTRIES, describe("radialmenu.editor.entries.tip"));
            y += Ui.STEP;
        }

        setContentHeight(y - scrolledTop() + Ui.PAD);
        addBottomBar("radialmenu.editor.save", "radialmenu.editor.delete", "gui.cancel");
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
            case ID_ENTRIES:
                openEntries();
                return;
            case ID_KEEP_OPEN:
                ((UiCheckbox) button).toggle();
                draft.keepOpen = ((UiCheckbox) button).checked;
                return;
            default:
                handleActionButton(button);
        }
    }

    /** Captures the title as well, so opening a picker or scrolling cannot discard it. */
    @Override
    protected void captureInputs() {
        if (titleField != null) {
            titleText = titleField.getText();
        }
        draft.title = titleText.trim();
        super.captureInputs();
    }

    private void save() {
        if (!dropsEntries()) {
            write();
            GuiStack.closeAll();
            return;
        }
        // The same loss, reached deliberately this time: the player has switched the type away from submenu and is
        // saving. The tab says so, but a subtree going quietly is worth one question - there is no undo and no
        // older copy of the profile to go back to.
        captureInputs();
        GuiStack.push(
            new GuiConfirm(
                "radialmenu.editor.confirmDiscard",
                I18n.format("radialmenu.editor.confirmDiscard.subject", Integer.valueOf(draft.filledCount())),
                "radialmenu.editor.discard",
                new GuiConfirm.Result() {

                    @Override
                    public void onConfirmed() {
                        write();
                        GuiStack.closeAll();
                    }
                }));
    }

    /** Whether saving would turn a submenu that still holds entries into something that cannot hold any. */
    private boolean dropsEntries() {
        return draft.isCategory() && draft.filledCount() > 0 && !ActionTypes.SUBMENU.equals(selectedType);
    }

    /**
     * Turns the draft into the entry and writes the profile, without closing anything.
     *
     * <p>
     * Split out of {@link #save()} so the way into a submenu's entry list can save first and stay: everything that
     * decides what this slot *is* happens here, and whether the editor closes afterwards is the caller's business.
     */
    private MenuNode write() {
        captureInputs();

        draft.action = spec;
        draft.action.type = selectedType;

        if (ActionTypes.SUBMENU.equals(selectedType)) {
            SubmenuFields.applyToNode(draft);
        } else {
            draft.children = null;
            draft.layout = null;
            draft.style = null;
        }

        draft.normalize();
        store(draft);
        return draft;
    }

    /**
     * Writes this slot and opens the list of what is inside it.
     *
     * <p>
     * Saved first, deliberately. The two screens edit the same node - this one its shape and colours, that one its
     * entries - and leaving both open would mean whichever was saved last quietly undid the other. One owner at a
     * time, and the tooltip says so.
     */
    private void openEntries() {
        // Guarded rather than trusted to the button that calls it. write() is a save, and a save of a slot whose
        // chosen type is not a submenu is what *clears* the children - so reaching here from any other tab would
        // destroy the very list it was asked to open. That is precisely what it did once.
        if (!ActionTypes.SUBMENU.equals(selectedType)) {
            return;
        }
        MenuNode node = write();
        if (node == null || !node.isCategory()) {
            return;
        }
        GuiStack.push(new GuiMenuSettings(node));
    }

    private void apply(MenuNode node) {
        store(node);
        // Editing is finished, so the wheel closes with the editor instead of reappearing beneath it.
        GuiStack.closeAll();
    }

    /** Puts a node in this slot and saves the profile. Null is how an entry is deleted. */
    private void store(MenuNode node) {
        parent.setChildAt(childIndex, node);
        ProfileManager.active()
            .normalize();
        ProfileManager.saveActive();
        IconRenderer.clearCache();
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

        if (isVisibleRow(actionSectionTop)) {
            Ui.sectionHeader(I18n.format("radialmenu.editor.sectionAction"), left, actionSectionTop, contentRight());
        }

        drawActionSection();
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
        if (!isShowing() || !isInsideViewport(mouseY)) {
            return;
        }
        titleField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean handleKey(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_TAB) {
            return true;
        }
        if (titleField.textboxKeyTyped(typedChar, keyCode)) {
            return true;
        }
        return super.handleKey(typedChar, keyCode);
    }
}
