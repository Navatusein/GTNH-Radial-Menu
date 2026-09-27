package com.navatusein.radialmenu.client.gui.editor;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;

import com.navatusein.radialmenu.client.gui.GuiStack;

/**
 * Asks for a single line of text.
 *
 * <p>
 * Shared by every place that needs a name - new profile, rename, duplicate - so those three do not each grow their own
 * screen. The caller decides whether the value is acceptable and reports back through {@link Result}, which is what
 * lets a name collision show an error here instead of failing silently after the screen has closed.
 */
public class GuiTextPrompt extends GuiScreen {

    /** Returned by the caller to say whether the prompt may close. */
    public interface Result {

        /** @return null when accepted, otherwise a translation key describing what is wrong */
        String onConfirm(String value);
    }

    private static final int ID_OK = 1;
    private static final int ID_CANCEL = 2;

    private final String titleKey;
    private final String initialValue;
    private final Result result;

    private GuiTextField input;
    private String errorKey;

    public GuiTextPrompt(String titleKey, String initialValue, Result result) {
        this.titleKey = titleKey;
        this.initialValue = initialValue == null ? "" : initialValue;
        this.result = result;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        input = new GuiTextField(this.fontRendererObj, this.width / 2 - 100, this.height / 2 - 10, 200, 18);
        input.setMaxStringLength(48);
        input.setText(initialValue);
        input.setFocused(true);
        input.setCursorPositionEnd();

        this.buttonList.clear();
        this.buttonList
            .add(new GuiButton(ID_OK, this.width / 2 - 100, this.height / 2 + 16, 98, 20, I18n.format("gui.done")));
        this.buttonList
            .add(new GuiButton(ID_CANCEL, this.width / 2 + 2, this.height / 2 + 16, 98, 20, I18n.format("gui.cancel")));
    }

    private void confirm() {
        String value = input.getText()
            .trim();
        if (value.isEmpty()) {
            errorKey = "radialmenu.editor.nameEmpty";
            return;
        }
        errorKey = result.onConfirm(value);
        if (errorKey == null) {
            GuiStack.pop();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_OK) {
            confirm();
        } else if (button.id == ID_CANCEL) {
            GuiStack.pop();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format(titleKey),
            this.width / 2,
            this.height / 2 - 34,
            0xFFFFFF);
        input.drawTextBox();

        if (errorKey != null) {
            this.drawCenteredString(
                this.fontRendererObj,
                EnumChatFormatting.RED + I18n.format(errorKey),
                this.width / 2,
                this.height / 2 + 42,
                0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        input.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            GuiStack.pop();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            confirm();
            return;
        }
        if (input.textboxKeyTyped(typedChar, keyCode)) {
            errorKey = null;
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
