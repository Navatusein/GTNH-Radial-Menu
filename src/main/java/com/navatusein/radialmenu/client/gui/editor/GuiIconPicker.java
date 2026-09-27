package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Mouse;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiCheckbox;
import com.navatusein.radialmenu.client.gui.ui.UiScreen;
import com.navatusein.radialmenu.client.gui.ui.UiTabButton;
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
public class GuiIconPicker extends UiScreen {

    public interface Callback {

        void onIconPicked(IconSpec icon);
    }

    private enum Tab {
        ITEMS,
        SPRITES,
        FILES
    }

    private static final int ID_TAB_BASE = 10;
    private static final int ID_COLOR = 20;
    private static final int ID_REFRESH = 21;
    private static final int ID_ORIGINAL = 22;

    private static final int CELL = 20;

    /** Enough for the swatch and a six-digit hex beside it. */
    private static final int COLOR_WIDTH = 66;

    /** Room under the grid for the name of the icon being hovered. */
    private static final int NAME_HEIGHT = 10;

    private final Callback callback;

    private Tab tab = Tab.ITEMS;

    private final List<String> itemNames = new ArrayList<>();
    private final List<String> visible = new ArrayList<>();

    private GuiTextField searchField;
    private String query = "";

    /**
     * Tint applied to sprites and user PNGs; items carry their own colours and ignore it.
     *
     * <p>
     * Blank by default, meaning the sprite inherits the profile's tint and the profile the mod's config. Starting
     * from an explicit white would quietly pin every new icon to a colour the player never chose.
     */
    private String color = "";

    /**
     * Whether a user PNG keeps the colours it was drawn with.
     *
     * <p>
     * A white tint already multiplies to no change, so this is not about the arithmetic - it is about not silently
     * carrying a colour picked for the monochrome sprites over onto artwork that has its own.
     */
    private boolean originalColors = true;

    private int gridTop;
    private int gridLeft;
    private int columns;
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
    protected String titleKey() {
        return "radialmenu.editor.pickIcon";
    }

    @Override
    protected int panelWidth() {
        return 360;
    }

    @Override
    protected void buildControls() {
        if (itemNames.isEmpty()) {
            for (Object key : Item.itemRegistry.getKeys()) {
                itemNames.add(String.valueOf(key));
            }
        }

        int left = contentLeft();
        int y = contentTop();

        // Search first, then the tabs. A selected tab deliberately has no bottom edge so it joins the sheet below
        // it, so anything between the two reads as a mistake.
        //
        // Measured from the right edge inwards, each control from the one beside it. Three independent offsets left
        // the gaps at three pixels on one side of the colour button and four on the other - the sort of difference
        // that is invisible to write and obvious to look at.
        int refreshLeft = contentRight() - Ui.ROW;
        int colorLeft = refreshLeft - Ui.GAP - COLOR_WIDTH;
        int searchWidth = colorLeft - Ui.GAP - left;

        searchField = new GuiTextField(this.fontRendererObj, left + 1, y + 3, searchWidth - 2, 14);
        searchField.setMaxStringLength(64);
        searchField.setText(query);
        searchField.setCursorPositionEnd();
        searchField.setFocused(true);

        // Blank label: the swatch and the hex are drawn together in the overlay, because a centred button label
        // would sit underneath the swatch.
        GuiButton colorButton = new GuiButton(ID_COLOR, colorLeft, y, COLOR_WIDTH, Ui.ROW, "");
        // Only sprites and untinted-by-choice PNGs take a colour; an item would ignore it.
        colorButton.enabled = tab != Tab.ITEMS && !(tab == Tab.FILES && originalColors);
        this.buttonList.add(colorButton);
        tooltip(ID_COLOR, I18n.format("radialmenu.icons.color.tip"));
        this.buttonList.add(new GuiButton(ID_REFRESH, refreshLeft, y, Ui.ROW, Ui.ROW, "R"));
        tooltip(ID_REFRESH, I18n.format("radialmenu.icons.refresh.tip"));

        y += Ui.STEP;

        Tab[] tabs = Tab.values();
        int tabWidth = (contentWidth() - (tabs.length - 1) * 2) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            this.buttonList.add(
                new UiTabButton(
                    ID_TAB_BASE + i,
                    left + i * (tabWidth + 2),
                    y,
                    tabWidth,
                    I18n.format(
                        "radialmenu.icons." + tabs[i].name()
                            .toLowerCase()),
                    tabs[i] == tab));
        }

        int afterTabs = y + UiTabButton.HEIGHT;

        if (tab == Tab.FILES) {
            this.buttonList.add(
                new UiCheckbox(
                    ID_ORIGINAL,
                    left,
                    afterTabs + Ui.GAP,
                    contentWidth(),
                    I18n.format("radialmenu.icons.original"),
                    originalColors));
            tooltip(ID_ORIGINAL, I18n.format("radialmenu.icons.original.tip"));
            afterTabs += Ui.STEP;
        }

        // The grid frame starts exactly where the tabs end, so the selected one runs into it.
        gridTop = afterTabs;
        gridLeft = left;
        columns = Math.max(1, contentWidth() / CELL);

        refilter();
        addBottomBar("gui.cancel", null, null);
    }

    /** The hovered name is drawn below the grid, so it gets a line of its own rather than the panel's padding. */
    @Override
    protected int footerHeight() {
        return NAME_HEIGHT;
    }

    private int gridBottom() {
        return viewportBottom();
    }

    private int rowsVisible() {
        return Math.max(1, (gridBottom() - gridTop - 2) / CELL);
    }

    private void refilter() {
        String needle = query.trim()
            .toLowerCase();
        visible.clear();

        switch (tab) {
            case ITEMS:
                for (String name : itemNames) {
                    if (needle.isEmpty() || name.toLowerCase()
                        .contains(needle)) {
                        visible.add(name);
                    }
                }
                break;
            case SPRITES:
                for (SpriteAtlas.Sprite sprite : SpriteAtlas.search(needle)) {
                    visible.add(sprite.name);
                }
                break;
            case FILES:
                for (String name : UserIconLoader.listFiles()) {
                    if (needle.isEmpty() || name.toLowerCase()
                        .contains(needle)) {
                        visible.add(name);
                    }
                }
                break;
            default:
                break;
        }
        clampScroll();
    }

    private void clampScroll() {
        int maxRow = Math.max(0, (visible.size() + columns - 1) / columns - rowsVisible());
        scrollRow = Math.max(0, Math.min(maxRow, scrollRow));
    }

    /** Builds the spec for an entry of the current tab, so drawing and picking cannot disagree about it. */
    private IconSpec specFor(String name) {
        switch (tab) {
            case SPRITES:
                return IconSpec.sprite(SpriteAtlas.qualify(name), color);
            case FILES:
                IconSpec file = IconSpec.file(name);
                // Null means untinted, which is how the artwork's own colours survive.
                file.color = originalColors ? null : color;
                return file;
            case ITEMS:
            default:
                return IconSpec.item(name, 0);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_PRIMARY) {
            onCancel();
            return;
        }
        if (button.id == ID_COLOR) {
            GuiStack.push(new GuiColorPicker(color, new GuiColorPicker.Result() {

                @Override
                public void onColorPicked(String hex) {
                    color = hex;
                    requestRebuild();
                }
            }));
            return;
        }
        if (button.id == ID_ORIGINAL) {
            ((UiCheckbox) button).toggle();
            originalColors = ((UiCheckbox) button).checked;
            requestRebuild();
            return;
        }
        if (button.id == ID_REFRESH) {
            // Picks up a PNG just dropped into the folder, without restarting the game.
            UserIconLoader.refresh();
            requestRebuild();
            return;
        }

        int tabIndex = button.id - ID_TAB_BASE;
        Tab[] tabs = Tab.values();
        if (tabIndex >= 0 && tabIndex < tabs.length) {
            tab = tabs[tabIndex];
            scrollRow = 0;
            requestRebuild();
        }
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        searchField.drawTextBox();
        // The grid needs a ground of its own; against the bare panel the icons read as scattered rather than listed.
        Ui.list(gridLeft, gridTop, contentRight(), gridBottom());

        String hoveredName = null;
        int rows = rowsVisible();

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = (row + scrollRow) * columns + column;
                if (index >= visible.size()) {
                    break;
                }
                int x = gridLeft + 2 + column * CELL;
                int y = gridTop + 2 + row * CELL;

                if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                    drawRect(x, y, x + CELL, y + CELL, Ui.ROW_HOVER);
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
                gridTop + 8,
                Ui.TEXT);
        }

        if (hoveredName != null) {
            this.drawCenteredString(
                this.fontRendererObj,
                hoveredName,
                this.width / 2,
                gridBottom() + Ui.GAP,
                0xFFFFFF80);
        }
    }

    @Override
    protected void drawOverlay(int mouseX, int mouseY, float partialTicks) {
        for (Object raw : this.buttonList) {
            GuiButton button = (GuiButton) raw;
            if (button.id != ID_COLOR) {
                continue;
            }
            int swatchTop = button.yPosition + 4;
            boolean inherited = color.trim()
                .isEmpty();

            // An inherited tint still has a colour - the profile's, or the config's - so the swatch shows that one
            // rather than a blank, and the label says where it came from.
            int shown = inherited ? IconRenderer.resolveTint(null) : IconSpec.parseRgb(color, 0xFFFFFF);
            Ui.frame(
                button.xPosition + 4,
                swatchTop,
                button.xPosition + 16,
                swatchTop + 12,
                0xFF000000 | shown,
                0xFF000000);

            String label = inherited ? I18n.format("radialmenu.editor.inherit") : color;
            this.fontRendererObj.drawString(
                Ui.fit(label, button.width - 24),
                button.xPosition + 20,
                button.yPosition + (Ui.ROW - 8) / 2,
                button.enabled ? Ui.TEXT : Ui.TEXT_MUTED);
            return;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollRow += wheel > 0 ? -1 : 1;
            clampScroll();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        searchField.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton != 0) {
            return;
        }
        int rows = rowsVisible();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = (row + scrollRow) * columns + column;
                if (index >= visible.size()) {
                    return;
                }
                int x = gridLeft + 2 + column * CELL;
                int y = gridTop + 2 + row * CELL;
                if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                    callback.onIconPicked(specFor(visible.get(index)));
                    GuiStack.pop();
                    return;
                }
            }
        }
    }

    @Override
    protected boolean handleKey(char typedChar, int keyCode) {
        if (searchField.textboxKeyTyped(typedChar, keyCode)) {
            query = searchField.getText();
            scrollRow = 0;
            refilter();
            return true;
        }
        return false;
    }
}
