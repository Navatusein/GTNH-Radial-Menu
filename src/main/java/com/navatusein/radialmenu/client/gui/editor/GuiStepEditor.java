package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.core.action.ActionSpec;
import com.navatusein.radialmenu.core.action.ActionType;
import com.navatusein.radialmenu.core.action.ActionTypes;

/**
 * Edits one step of a chain.
 *
 * <p>
 * The same controls as a slot, minus everything that belongs to a slot rather than to an action: a step has no title,
 * no icon and no say in whether the wheel closes afterwards - that is decided once, by the entry that owns the chain.
 *
 * <p>
 * Edits a copy and hands it back on Done, so backing out of a step leaves the chain as it was.
 */
public class GuiStepEditor extends GuiActionEditor {

    public interface Result {

        void onStepEdited(ActionSpec step);
    }

    /** Position of this step in the chain, for the title. One-based, as the list shows it. */
    private final int ordinal;

    private final Result result;

    public GuiStepEditor(ActionSpec step, int ordinal, Result result) {
        this.ordinal = ordinal;
        this.result = result;

        ActionSpec draft = step == null || step.type == null ? ActionTypes.get(ActionTypes.KEYBIND)
            .newSpec() : step.copy();
        setSpec(draft, draft.type);
    }

    @Override
    protected String titleKey() {
        return "radialmenu.steps.title";
    }

    @Override
    protected Object[] titleArgs() {
        return new Object[] { Integer.valueOf(ordinal) };
    }

    @Override
    protected int panelWidth() {
        return 330;
    }

    @Override
    protected boolean framedViewport() {
        return true;
    }

    /**
     * Everything except a submenu.
     *
     * <p>
     * A submenu is a shape rather than an action - it has no executor - so there is nothing for a chain to run. It is
     * offered on a slot because a slot can be one; a step cannot.
     */
    @Override
    protected List<ActionType> offeredTypes() {
        List<ActionType> types = new ArrayList<>();
        for (ActionType type : ActionTypes.all()) {
            if (!ActionTypes.SUBMENU.equals(type.id)) {
                types.add(type);
            }
        }
        return types;
    }

    @Override
    protected void buildControls() {
        int left = contentLeft();
        int controlLeft = left + Ui.LABEL_WIDTH + Ui.GAP;
        int controlWidth = contentRight() - controlLeft;

        int y = buildActionSection(scrolledTop(), left, controlLeft, controlWidth);

        setContentHeight(y - scrolledTop() + Ui.PAD);
        addBottomBar("gui.done", null, "gui.cancel");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case ID_PRIMARY:
                captureInputs();
                spec.type = selectedType;
                spec.normalize();
                result.onStepEdited(spec);
                GuiStack.pop();
                return;
            case ID_SECONDARY:
                onCancel();
                return;
            default:
                handleActionButton(button);
        }
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        drawActionSection();
    }
}
