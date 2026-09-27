package com.navatusein.radialmenu.client.gui.editor;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import com.navatusein.radialmenu.client.gui.GuiStack;

/** Confirmation for the one action that destroys a file. Cancel is focused by position, not by default selection. */
public class GuiConfirm extends GuiScreen {

    public interface Result {

        void onConfirmed();
    }

    private static final int ID_YES = 1;
    private static final int ID_NO = 2;

    private final String questionKey;
    private final String subject;
    private final Result result;

    public GuiConfirm(String questionKey, String subject, Result result) {
        this.questionKey = questionKey;
        this.subject = subject;
        this.result = result;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        this.buttonList
            .add(new GuiButton(ID_NO, this.width / 2 - 100, this.height / 2 + 8, 98, 20, I18n.format("gui.cancel")));
        this.buttonList.add(
            new GuiButton(
                ID_YES,
                this.width / 2 + 2,
                this.height / 2 + 8,
                98,
                20,
                I18n.format("radialmenu.profiles.delete")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_YES) {
            result.onConfirmed();
        }
        GuiStack.pop();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format(questionKey),
            this.width / 2,
            this.height / 2 - 24,
            0xFFFFFF);
        this.drawCenteredString(
            this.fontRendererObj,
            subject == null ? "" : subject,
            this.width / 2,
            this.height / 2 - 10,
            0xFFFF80);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            GuiStack.pop();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
