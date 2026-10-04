package com.navatusein.radialmenu.client.gui.editor;

import net.minecraft.client.gui.GuiButton;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiScreen;

/** Confirmation for the things that destroy something there is no getting back. */
public class GuiConfirm extends UiScreen {

    public interface Result {

        void onConfirmed();
    }

    private final String questionKey;
    private final String subject;
    private final String confirmKey;
    private final Result result;

    public GuiConfirm(String questionKey, String subject, Result result) {
        this(questionKey, subject, "radialmenu.profiles.delete", result);
    }

    /**
     * @param confirmKey what the confirming button says, because "Delete" is wrong for everything that destroys
     *                   something without deleting a file
     */
    public GuiConfirm(String questionKey, String subject, String confirmKey, Result result) {
        this.questionKey = questionKey;
        this.subject = subject;
        this.confirmKey = confirmKey;
        this.result = result;
    }

    @Override
    protected String titleKey() {
        return questionKey;
    }

    @Override
    protected int panelWidth() {
        return 260;
    }

    @Override
    protected int panelHeightHint() {
        return Ui.STEP * 2;
    }

    @Override
    protected void buildControls() {
        setContentHeight(Ui.STEP);
        // Cancel first: the destructive choice should not be the one under the cursor by default.
        addBottomBar("gui.cancel", null, confirmKey);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_SECONDARY) {
            result.onConfirmed();
        }
        GuiStack.pop();
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        this.drawCenteredString(
            this.fontRendererObj,
            subject == null ? "" : subject,
            this.width / 2,
            contentTop() + Ui.GAP,
            0xFFFFFF80);
    }
}
