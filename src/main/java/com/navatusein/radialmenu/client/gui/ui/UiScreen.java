package com.navatusein.radialmenu.client.gui.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.navatusein.radialmenu.client.gui.GuiStack;

/**
 * Base for every screen in the mod.
 *
 * <p>
 * Carries the things that were previously copied, and drifted, between screens: a framed panel, a button bar pinned to
 * a fixed distance from the bottom edge, escape handling, and the deferred rebuild that keeps a click from being
 * processed against a button list replaced underneath it.
 */
public abstract class UiScreen extends GuiScreen {

    protected static final int ID_PRIMARY = 90;
    protected static final int ID_SECONDARY = 91;
    protected static final int ID_DANGER = 92;

    private boolean rebuildPending;

    /** How far the content is scrolled. Needed because a panel can be taller than a small screen. */
    protected int scrollOffset;

    /** Total height of the content, set by {@link #buildControls}. */
    private int contentHeight;

    /** Button id to tooltip text. Cleared with the controls, since ids are reassigned on every rebuild. */
    private final Map<Integer, String> tooltips = new HashMap<>();

    /** Ids of controls pinned to the footer, which never scroll out of view. */
    private final Set<Integer> footerIds = new HashSet<>();

    protected int panelLeft;
    protected int panelRight;
    protected int panelTop;
    protected int panelBottom;

    /** y of the button bar. Screen-bottom for full screens, just under the panel for dialogs. */
    private int barY;

    /** Translation key for the title drawn at the top of the panel. */
    protected abstract String titleKey();

    /** Arguments for the title's format string. Override when the title names what is being edited. */
    protected Object[] titleArgs() {
        return new Object[0];
    }

    /** Panel width in pixels. Screens with wide lists override this. */
    protected int panelWidth() {
        return 320;
    }

    /**
     * Preferred panel height, or -1 to fill down to the button bar.
     *
     * <p>
     * A short dialog stretched to the full height reads as an empty box with a line of text lost at the top, so
     * prompts give their own height and the panel is centred against the bar instead.
     */
    protected int panelHeightHint() {
        return -1;
    }

    /** Builds the button list and any text fields. Never call this from {@code actionPerformed}. */
    protected abstract void buildControls();

    /**
     * Asks for the controls to be rebuilt before the next frame.
     *
     * <p>
     * {@code GuiScreen.mouseClicked} walks the button list by index and calls {@code actionPerformed} from inside that
     * loop, so replacing the list mid-click makes the loop continue over the new buttons - which once added menu
     * entries in a runaway loop until the game died.
     */
    protected void requestRebuild() {
        rebuildPending = true;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        int width = Math.min(panelWidth(), this.width - 2 * Ui.PAD);
        panelLeft = (this.width - width) / 2;
        panelRight = panelLeft + width;
        int hint = panelHeightHint();
        if (hint > 0) {
            // A dialog is centred on the screen and carries its buttons directly beneath it. Pinning a short panel to
            // the screen-bottom bar instead left it stranded low with a lot of empty box above the text.
            int total = hint + Ui.GAP + Ui.ROW;
            panelTop = Math.max(Ui.PAD * 2, (this.height - total) / 2);
            panelBottom = panelTop + hint;
            barY = panelBottom + Ui.GAP;
        } else {
            barY = Ui.bottomBarY(this.height);
            panelTop = Ui.PAD * 2;
            panelBottom = barY - Ui.GAP;

            if (snapPanelToRows()) {
                int available = panelBottom - contentTop() - Ui.PAD - footerHeight();
                int wholeRows = Math.max(1, available / Ui.STEP);
                panelBottom = contentTop() + wholeRows * Ui.STEP + footerHeight() + Ui.PAD;
            }
        }

        this.buttonList.clear();
        tooltips.clear();
        footerIds.clear();
        buildControls();
    }

    /**
     * Attaches explanatory text to a button, shown while the cursor rests on it.
     *
     * <p>
     * Tooltips carry the wording that no longer fits on a shortened label, so the labels can stay terse without
     * losing the explanation.
     */
    protected void tooltip(int buttonId, String text) {
        if (text != null && !text.isEmpty()) {
            tooltips.put(Integer.valueOf(buttonId), text);
        }
    }

    /** First row of content inside the panel, below the title. */
    protected int contentTop() {
        return panelTop + Ui.PAD + 14;
    }

    /** Where content actually starts drawing, once scrolling is taken into account. */
    protected int scrolledTop() {
        return contentTop() - scrollOffset;
    }

    /** Called by {@link #buildControls} with the height the content came out to. */
    protected void setContentHeight(int height) {
        contentHeight = height;
    }

    /**
     * Marks a control as part of the footer, so scrolling never hides it.
     *
     * <p>
     * Declared rather than inferred from position: judging it by the y coordinate meant a row scrolled down to the
     * panel edge was mistaken for the footer and drawn anyway, half outside the panel.
     */
    protected void markFooter(int buttonId) {
        footerIds.add(Integer.valueOf(buttonId));
    }

    private boolean isFooterButton(GuiButton button) {
        return footerIds.contains(Integer.valueOf(button.id));
    }

    /** Height reserved at the bottom of the panel for controls that do not scroll. */
    protected int footerHeight() {
        return 0;
    }

    /**
     * Whether the panel should be trimmed to hold a whole number of rows.
     *
     * <p>
     * Rows are scrolled a whole row at a time, so a panel whose height is not a multiple of the row step always has a
     * remainder - which shows up as a band of empty space that moves between the top and the bottom as you scroll.
     * Trimming the panel to fit removes it instead of hiding it.
     */
    protected boolean snapPanelToRows() {
        return false;
    }

    /** Bottom edge of the scrolling area. */
    protected int contentBottom() {
        return panelBottom - Ui.PAD - footerHeight();
    }

    protected int viewportHeight() {
        return contentBottom() - contentTop();
    }

    protected int maxScroll() {
        return Math.max(0, contentHeight - viewportHeight());
    }

    /**
     * True while a row at this y is wholly inside the scrolling area.
     *
     * <p>
     * Strictly inside: allowing a row to hang over the edge let scrolled-away content draw outside the panel
     * entirely, over whatever was behind it.
     */
    protected boolean isVisibleRow(int y) {
        return y >= contentTop() && y + Ui.ROW <= contentBottom();
    }

    protected int contentLeft() {
        return panelLeft + Ui.PAD;
    }

    protected int contentRight() {
        return panelRight - Ui.PAD;
    }

    protected int contentWidth() {
        return contentRight() - contentLeft();
    }

    /**
     * Places up to three buttons in the bar at the bottom of the screen.
     *
     * <p>
     * Always the same height and the same widths, so the bar does not shift as the player moves between screens.
     */
    protected void addBottomBar(String primaryKey, String dangerKey, String secondaryKey) {
        int count = 1 + (dangerKey == null ? 0 : 1) + (secondaryKey == null ? 0 : 1);
        int width = count == 1 ? 200 : (count == 2 ? 98 : 96);
        int total = count * width + (count - 1) * Ui.GAP;
        int x = this.width / 2 - total / 2;
        int y = barY;

        this.buttonList.add(new GuiButton(ID_PRIMARY, x, y, width, Ui.ROW, I18n.format(primaryKey)));
        x += width + Ui.GAP;

        if (dangerKey != null) {
            this.buttonList.add(new GuiButton(ID_DANGER, x, y, width, Ui.ROW, I18n.format(dangerKey)));
            x += width + Ui.GAP;
        }
        if (secondaryKey != null) {
            this.buttonList.add(new GuiButton(ID_SECONDARY, x, y, width, Ui.ROW, I18n.format(secondaryKey)));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (rebuildPending) {
            rebuildPending = false;
            this.buttonList.clear();
            tooltips.clear();
            footerIds.clear();
            buildControls();
        }

        // Buttons scrolled out of the panel must not be clickable either, so visibility is driven from position.
        for (Object raw : this.buttonList) {
            GuiButton button = (GuiButton) raw;
            if (button.id < ID_PRIMARY && !isFooterButton(button)) {
                button.visible = isVisibleRow(button.yPosition);
            }
        }

        this.drawDefaultBackground();
        Ui.panel(panelLeft, panelTop, panelRight, panelBottom);
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format(titleKey(), titleArgs()),
            this.width / 2,
            panelTop + Ui.PAD - 2,
            Ui.TEXT);

        drawContent(mouseX, mouseY, partialTicks);
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawOverlay(mouseX, mouseY, partialTicks);
        drawScrollbar();
        drawTooltip(mouseX, mouseY);
    }

    private void drawTooltip(int mouseX, int mouseY) {
        if (tooltips.isEmpty()) {
            return;
        }
        for (Object raw : this.buttonList) {
            GuiButton button = (GuiButton) raw;
            if (!button.visible || mouseX < button.xPosition
                || mouseX >= button.xPosition + button.width
                || mouseY < button.yPosition
                || mouseY >= button.yPosition + button.height) {
                continue;
            }
            String text = tooltips.get(Integer.valueOf(button.id));
            if (text != null) {
                List<String> lines = new ArrayList<>();
                lines.add(text);
                drawHoveringText(lines, mouseX, mouseY, this.fontRendererObj);
            }
            return;
        }
    }

    /** Drawn after the buttons, for anything that has to sit on top of one. */
    protected void drawOverlay(int mouseX, int mouseY, float partialTicks) {}

    /** Last chance to read the controls before scrolling rebuilds them. */
    protected void beforeScroll() {}

    private void drawScrollbar() {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackTop = contentTop();
        int trackHeight = viewportHeight();
        int thumbHeight = Math.max(16, trackHeight * trackHeight / (trackHeight + max));
        int thumbTop = trackTop + (trackHeight - thumbHeight) * scrollOffset / max;

        int x = panelRight - 4;
        Gui.drawRect(x, trackTop, x + 2, trackTop + trackHeight, 0x40FFFFFF);
        Gui.drawRect(x, thumbTop, x + 2, thumbTop + thumbHeight, 0xC0FFFFFF);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || maxScroll() == 0) {
            return;
        }
        int next = Math.max(0, Math.min(maxScroll(), scrollOffset + (wheel > 0 ? -Ui.STEP : Ui.STEP)));
        if (next != scrollOffset) {
            // Scrolling rebuilds the controls, which recreates the text fields from the model - so whatever is typed
            // has to be read back first, or it is thrown away by the act of scrolling.
            beforeScroll();
            scrollOffset = next;
            requestRebuild();
        }
    }

    /** Everything inside the panel. Buttons draw themselves afterwards. */
    protected abstract void drawContent(int mouseX, int mouseY, float partialTicks);

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            onCancel();
            return;
        }
        if (!handleKey(typedChar, keyCode)) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    /** @return true when the screen consumed the key */
    protected boolean handleKey(char typedChar, int keyCode) {
        return false;
    }

    /** Escape, and the secondary button unless a screen says otherwise. */
    protected void onCancel() {
        GuiStack.pop();
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
