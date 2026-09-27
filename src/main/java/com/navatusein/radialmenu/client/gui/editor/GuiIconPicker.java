package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.icon.IconRenderer;
import com.navatusein.radialmenu.client.icon.SpriteAtlas;
import com.navatusein.radialmenu.client.icon.UserIconLoader;
import com.navatusein.radialmenu.core.model.IconSpec;

/**
 * Picks a slot icon: a registry item, a sprite from the bundled Phosphor sheet, or a PNG the player supplied.
 *
 * <p>
 * Icons are stored as names rather than serialised stacks or raw pixels, so a profile file stays readable and
 * hand-editable.
 */
public class GuiIconPicker extends GuiScreen {

    public interface Callback {

        void onIconPicked(IconSpec icon);
    }

    private enum Tab {
        ITEMS,
        SPRITES,
        FILES
    }

    private static final int ID_TAB_ITEMS = 1;
    private static final int ID_TAB_SPRITES = 2;
    private static final int ID_TAB_FILES = 3;
    private static final int ID_CANCEL = 4;
    private static final int ID_REFRESH = 5;
    private static final int ID_COLOR = 6;

    private static final int COLUMNS = 16;
    private static final int CELL = 20;
    private static final int GRID_TOP = 72;

    private final Callback callback;

    private Tab tab = Tab.ITEMS;

    private final List<String> itemNames = new ArrayList<>();

    private final List<String> visible = new ArrayList<>();

    private GuiTextField searchField;

    /** Tint applied to sprites and user PNGs; items ignore it, since they carry their own colours. */
    private String color = "#FFFFFF";

    private int scrollRow;

    public GuiIconPicker(Callback callback) {
        this(callback, null);
    }

    /** @param currentColor tint of the icon being replaced, so re-picking does not silently reset it to white */
    public GuiIconPicker(Callback callback, String currentColor) {
        this.callback = callback;
        if (currentColor != null && !currentColor.trim()
            .isEmpty()) {
            this.color = currentColor;
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        if (itemNames.isEmpty()) {
            for (Object key : Item.itemRegistry.getKeys()) {
                itemNames.add(String.valueOf(key));
            }
        }

        int left = gridLeft();
        searchField = new GuiTextField(this.fontRendererObj, left, 26, COLUMNS * CELL, 16);
        searchField.setFocused(true);

        this.buttonList.clear();
        this.buttonList.add(new GuiButton(ID_COLOR, left, 47, 70, 18, color));
        this.buttonList.add(new GuiButton(ID_TAB_ITEMS, left + 80, 47, 70, 18, I18n.format("radialmenu.icons.items")));
        this.buttonList
            .add(new GuiButton(ID_TAB_SPRITES, left + 154, 47, 70, 18, I18n.format("radialmenu.icons.sprites")));
        this.buttonList.add(new GuiButton(ID_TAB_FILES, left + 228, 47, 70, 18, I18n.format("radialmenu.icons.files")));
        this.buttonList.add(new GuiButton(ID_REFRESH, left + 302, 47, 18, 18, "R"));
        this.buttonList
            .add(new GuiButton(ID_CANCEL, this.width / 2 - 100, this.height - 24, 200, 20, I18n.format("gui.cancel")));

        refilter();
    }

    private int gridLeft() {
        return this.width / 2 - COLUMNS * CELL / 2;
    }

    private int rowsVisible() {
        return Math.max(1, (this.height - GRID_TOP - 40) / CELL);
    }

    private void refilter() {
        String query = searchField.getText()
            .trim()
            .toLowerCase();
        visible.clear();

        switch (tab) {
            case ITEMS:
                for (String name : itemNames) {
                    if (query.isEmpty() || name.toLowerCase()
                        .contains(query)) {
                        visible.add(name);
                    }
                }
                break;
            case SPRITES:
                for (SpriteAtlas.Sprite sprite : SpriteAtlas.search(query)) {
                    visible.add(sprite.name);
                }
                break;
            case FILES:
                for (String name : UserIconLoader.listFiles()) {
                    if (query.isEmpty() || name.toLowerCase()
                        .contains(query)) {
                        visible.add(name);
                    }
                }
                break;
            default:
                break;
        }
        scrollRow = 0;
    }

    /** Builds the spec for an entry of the current tab, so drawing and picking cannot disagree about it. */
    private IconSpec specFor(String name) {
        switch (tab) {
            case SPRITES:
                return IconSpec.sprite(SpriteAtlas.qualify(name), color);
            case FILES:
                IconSpec file = IconSpec.file(name);
                file.color = color;
                return file;
            case ITEMS:
            default:
                return IconSpec.item(name, 0);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case ID_TAB_ITEMS:
                tab = Tab.ITEMS;
                refilter();
                return;
            case ID_TAB_SPRITES:
                tab = Tab.SPRITES;
                refilter();
                return;
            case ID_TAB_FILES:
                tab = Tab.FILES;
                refilter();
                return;
            case ID_REFRESH:
                // Picks up a PNG the player just dropped into the folder, without restarting the game.
                UserIconLoader.refresh();
                refilter();
                return;
            case ID_COLOR:
                GuiStack.push(new GuiColorPicker(color, new GuiColorPicker.Result() {

                    @Override
                    public void onColorPicked(String hex) {
                        color = hex;
                    }
                }));
                return;
            case ID_CANCEL:
                GuiStack.pop();
                return;
            default:
                break;
        }
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
        // Swatch on the colour button, so the current tint is visible without reading a hex code.
        drawRect(left + 2, 49, left + 14, 63, 0xFF000000 | IconSpec.parseRgb(color, 0xFFFFFF));
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
                IconRenderer.draw(specFor(visible.get(index)), x + 2, y + 2);
            }
        }

        if (visible.isEmpty()) {
            this.drawCenteredString(
                this.fontRendererObj,
                EnumChatFormatting.GRAY
                    + I18n.format(tab == Tab.FILES ? "radialmenu.icons.noFiles" : "radialmenu.icons.noMatches"),
                this.width / 2,
                GRID_TOP + 8,
                0xFFFFFF);
        }

        if (hoveredName != null) {
            this.drawCenteredString(this.fontRendererObj, hoveredName, this.width / 2, this.height - 38, 0xFFFF80);
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
                    callback.onIconPicked(specFor(visible.get(index)));
                    GuiStack.pop();
                    return;
                }
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            GuiStack.pop();
            return;
        }
        if (searchField.textboxKeyTyped(typedChar, keyCode)) {
            refilter();
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
