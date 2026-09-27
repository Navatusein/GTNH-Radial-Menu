package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.input.KeyBindingLookup;

/**
 * Picks a keybinding for a menu entry.
 *
 * <p>
 * Every registered binding is listed, including ones with no key assigned and ones whose default is a mouse button.
 * MineMenu's equivalent filters on {@code getKeyCodeDefault() >= 0}, which quietly drops the mouse-default bindings;
 * since the whole point here is to reach bindings the player has not given a key, the list stays unfiltered.
 */
public class GuiKeyBindPicker extends GuiScreen {

    /** Receives the chosen binding's description and category. */
    public interface Callback {

        void onKeyBindPicked(String description, String category);
    }

    private static final int ROW_HEIGHT = 14;
    private static final int LIST_TOP = 48;
    private static final int LIST_BOTTOM_MARGIN = 34;

    private final Callback callback;

    private final List<KeyBinding> allBindings = new ArrayList<>();

    private final List<KeyBinding> visible = new ArrayList<>();

    private GuiTextField searchField;

    private int scrollRow;

    public GuiKeyBindPicker(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        allBindings.clear();
        allBindings.addAll(KeyBindingLookup.all());

        searchField = new GuiTextField(this.fontRendererObj, this.width / 2 - 140, 26, 280, 16);
        searchField.setFocused(true);

        this.buttonList.clear();
        this.buttonList
            .add(new GuiButton(0, this.width / 2 - 100, this.height - 26, 200, 20, I18n.format("gui.cancel")));

        refilter();
    }

    private int rowsVisible() {
        return Math.max(1, (this.height - LIST_TOP - LIST_BOTTOM_MARGIN) / ROW_HEIGHT);
    }

    private void refilter() {
        String query = searchField.getText()
            .trim()
            .toLowerCase();
        visible.clear();
        for (KeyBinding binding : allBindings) {
            if (query.isEmpty() || matches(binding, query)) {
                visible.add(binding);
            }
        }
        scrollRow = 0;
    }

    private boolean matches(KeyBinding binding, String query) {
        return contains(I18n.format(binding.getKeyDescription()), query) || contains(binding.getKeyDescription(), query)
            || contains(I18n.format(binding.getKeyCategory()), query)
            || contains(binding.getKeyCategory(), query);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase()
            .contains(needle);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("radialmenu.editor.pickKeybind"),
            this.width / 2,
            10,
            0xFFFFFF);
        searchField.drawTextBox();

        int rows = rowsVisible();
        for (int row = 0; row < rows && row + scrollRow < visible.size(); row++) {
            KeyBinding binding = visible.get(row + scrollRow);
            int y = LIST_TOP + row * ROW_HEIGHT;
            boolean hovered = mouseY >= y && mouseY < y + ROW_HEIGHT
                && mouseX >= this.width / 2 - 150
                && mouseX <= this.width / 2 + 150;

            String label = I18n.format(binding.getKeyDescription());
            String key = KeyBindingLookup.isUnbound(binding)
                ? EnumChatFormatting.DARK_GRAY + I18n.format("radialmenu.editor.unbound")
                : EnumChatFormatting.GRAY + GameSettings.getKeyDisplayString(binding.getKeyCode());

            this.fontRendererObj.drawString(
                (hovered ? EnumChatFormatting.YELLOW.toString() : "") + label,
                this.width / 2 - 150,
                y + 3,
                0xFFFFFF);
            this.fontRendererObj.drawString(key, this.width / 2 + 60, y + 3, 0xFFFFFF);
        }

        if (visible.size() > rows) {
            this.drawCenteredString(
                this.fontRendererObj,
                (scrollRow + 1) + "-" + Math.min(visible.size(), scrollRow + rows) + " / " + visible.size(),
                this.width / 2,
                this.height - 38,
                0x808080);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int maxScroll = Math.max(0, visible.size() - rowsVisible());
            scrollRow = Math.max(0, Math.min(maxScroll, scrollRow + (wheel > 0 ? -1 : 1)));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        searchField.mouseClicked(mouseX, mouseY, button);

        if (button != 0) {
            return;
        }
        int rows = rowsVisible();
        for (int row = 0; row < rows && row + scrollRow < visible.size(); row++) {
            int y = LIST_TOP + row * ROW_HEIGHT;
            if (mouseY >= y && mouseY < y + ROW_HEIGHT
                && mouseX >= this.width / 2 - 150
                && mouseX <= this.width / 2 + 150) {
                KeyBinding picked = visible.get(row + scrollRow);
                callback.onKeyBindPicked(picked.getKeyDescription(), picked.getKeyCategory());
                GuiStack.pop();
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            GuiStack.pop();
            return;
        }
        if (searchField.textboxKeyTyped(typedChar, keyCode)) {
            refilter();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            GuiStack.pop();
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
