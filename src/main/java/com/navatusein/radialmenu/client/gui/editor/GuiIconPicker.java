package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.icon.IconRenderer;
import com.navatusein.radialmenu.core.model.IconSpec;

/**
 * Picks an item to use as a slot icon.
 *
 * <p>
 * Icons are stored as registry names rather than serialised item stacks, so a profile file stays readable and
 * hand-editable. The bundled sprite set and user PNG tabs are not built yet; this covers the item case, which is what
 * the default profile and most entries use.
 */
public class GuiIconPicker extends GuiScreen {

    public interface Callback {

        void onIconPicked(IconSpec icon);
    }

    private static final int COLUMNS = 16;
    private static final int CELL = 18;
    private static final int GRID_TOP = 48;

    private final Callback callback;

    private final List<String> allNames = new ArrayList<>();
    private final List<String> visible = new ArrayList<>();

    private GuiTextField searchField;

    private int scrollRow;

    public GuiIconPicker(Callback callback) {
        this.callback = callback;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        allNames.clear();
        for (Object key : Item.itemRegistry.getKeys()) {
            allNames.add(String.valueOf(key));
        }

        searchField = new GuiTextField(this.fontRendererObj, this.width / 2 - 140, 26, 280, 16);
        searchField.setFocused(true);

        this.buttonList.clear();
        this.buttonList
            .add(new GuiButton(0, this.width / 2 - 100, this.height - 26, 200, 20, I18n.format("gui.cancel")));

        refilter();
    }

    private int rowsVisible() {
        return Math.max(1, (this.height - GRID_TOP - 34) / CELL);
    }

    private void refilter() {
        String query = searchField.getText()
            .trim()
            .toLowerCase();
        visible.clear();
        for (String name : allNames) {
            if (query.isEmpty() || name.toLowerCase()
                .contains(query)) {
                visible.add(name);
            }
        }
        scrollRow = 0;
    }

    private int gridLeft() {
        return this.width / 2 - COLUMNS * CELL / 2;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("radialmenu.editor.pickIcon"),
            this.width / 2,
            10,
            0xFFFFFF);
        searchField.drawTextBox();

        int left = gridLeft();
        int rows = rowsVisible();
        String hoveredName = null;

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int index = (row + scrollRow) * COLUMNS + column;
                if (index >= visible.size()) {
                    break;
                }
                int x = left + column * CELL;
                int y = GRID_TOP + row * CELL;

                if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                    drawRect(x, y, x + CELL, y + CELL, 0x80FFFFFF);
                    hoveredName = visible.get(index);
                }
                IconRenderer.draw(IconSpec.item(visible.get(index), 0), x + 1, y + 1);
            }
        }

        if (hoveredName != null) {
            this.drawCenteredString(this.fontRendererObj, hoveredName, this.width / 2, this.height - 40, 0xFFFF80);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int maxScroll = Math.max(0, (visible.size() + COLUMNS - 1) / COLUMNS - rowsVisible());
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
        int left = gridLeft();
        int rows = rowsVisible();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int index = (row + scrollRow) * COLUMNS + column;
                if (index >= visible.size()) {
                    return;
                }
                int x = left + column * CELL;
                int y = GRID_TOP + row * CELL;
                if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                    callback.onIconPicked(IconSpec.item(visible.get(index), 0));
                    GuiStack.pop();
                    return;
                }
            }
        }
    }

    /** Offers whatever the player is holding, which is usually faster than searching for its name. */
    public ItemStack heldStack() {
        return this.mc.thePlayer == null ? null : this.mc.thePlayer.getHeldItem();
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
