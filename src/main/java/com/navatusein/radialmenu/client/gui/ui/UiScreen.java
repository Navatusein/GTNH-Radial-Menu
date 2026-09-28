package com.navatusein.radialmenu.client.gui.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

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

    /** How wide a tooltip is allowed to get before it wraps. About two thirds of a 320-wide dialog. */
    private static final int TOOLTIP_WIDTH = 200;

    protected static final int ID_PRIMARY = 90;
    protected static final int ID_SECONDARY = 91;
    protected static final int ID_DANGER = 92;

    /** Pixels per wheel notch. Small enough to read as movement rather than as a jump. */
    private static final int SCROLL_STEP = 12;

    /** Height of the title band: the title, a rule under it and a gap before the first row. */
    private static final int TITLE_BAND = 24;

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
     * Height of the content a short dialog draws, or -1 to fill down to the button bar.
     *
     * <p>
     * A short dialog stretched to the full height reads as an empty box with a line of text lost at the top, so
     * prompts give their own height and the panel is centred against the bar instead. This is the content alone -
     * the title band and the padding are added here, because screens adding them by hand all made a different guess
     * and every one of them broke when the band grew.
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
            // The gap is the one the content keeps inside the frame; without it a dialog sized to exactly fit its
            // own content came out a gap short, and its last row fell outside.
            int panelHeight = Ui.PAD + TITLE_BAND + hint + Ui.GAP + Ui.PAD;
            int total = panelHeight + Ui.GAP + Ui.ROW;
            panelTop = Math.max(Ui.PAD * 2, (this.height - total) / 2);
            panelBottom = panelTop + panelHeight;
            barY = panelBottom + Ui.GAP;
        } else {
            barY = Ui.bottomBarY(this.height);
            panelTop = Ui.PAD * 2;
            panelBottom = barY - Ui.GAP;
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

    /**
     * Top edge of the framed box, below the title band.
     *
     * <p>
     * The band has to be tall enough for the title, a rule under it and a gap: with only four pixels to spare, a row
     * clipped at the top of the scrolling area read as running into the title.
     */
    protected int viewportTop() {
        return panelTop + Ui.PAD + TITLE_BAND - Ui.GAP;
    }

    /**
     * Bottom edge of the framed box, above whatever the screen pinned below it.
     *
     * <p>
     * At the panel's padding, like the left and right edges. Derived from {@code contentBottom} instead, it came out
     * four pixels from the bottom border against eight at the sides - the frame visibly closer to one edge than the
     * others.
     */
    protected int viewportBottom() {
        int footer = footerHeight();
        return panelBottom - Ui.PAD - (footer > 0 ? footer + Ui.GAP : 0);
    }

    /** First row of content, a gap inside the frame. */
    protected int contentTop() {
        return viewportTop() + Ui.GAP;
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
     * Whether the scrolling area gets a frame of its own.
     *
     * <p>
     * Worth having on anything that scrolls. A row cut at the clip boundary reads as colliding with the title or the
     * footer when it ends in mid-air; inside a frame the same cut reads as the edge of a list, which is what it is.
     */
    protected boolean framedViewport() {
        return false;
    }

    /**
     * Bottom edge of the scrolling area, a gap inside the frame.
     *
     * <p>
     * The gap is what keeps a clipped row from reading as a collision: reserving only the footer's own height left
     * four pixels between a cut row and the buttons, and put the frame's bottom edge underneath them.
     */
    protected int contentBottom() {
        return viewportBottom() - Ui.GAP;
    }

    protected int viewportHeight() {
        return contentBottom() - contentTop();
    }

    protected int maxScroll() {
        return Math.max(0, contentHeight - viewportHeight());
    }

    /**
     * True while any part of a row at this y falls inside the scrolling area.
     *
     * <p>
     * Overlapping counts, because the viewport is clipped: a row hanging over the edge is cut at the panel border
     * rather than drawn across whatever is behind it. Without the clip this had to demand the row fit entirely,
     * which is what made scrolling jump a whole row at a time.
     */
    protected boolean isVisibleRow(int y) {
        return y + Ui.ROW > contentTop() && y < contentBottom();
    }

    /**
     * Edges of a framed box inside the panel - the scrolling viewport, or a list a screen frames itself.
     *
     * <p>
     * One definition, because there were two: the viewport framed itself a gap out from the panel border while the
     * icon grid framed itself at the content column, so two screens showed visibly different padding for the same
     * thing. A frame sits at the panel's padding, like the rule under the title.
     */
    protected int viewportLeft() {
        return panelLeft + Ui.PAD;
    }

    protected int viewportRight() {
        return panelRight - Ui.PAD;
    }

    /**
     * Where a row starts.
     *
     * <p>
     * Indented by a gap when there is a frame around it, matching the gap the frame leaves above and below the
     * content, so a row is never pressed against a border it sits inside.
     */
    protected int contentLeft() {
        return viewportLeft() + (framedViewport() ? Ui.GAP : 0);
    }

    protected int contentRight() {
        return viewportRight() - (framedViewport() ? Ui.GAP : 0);
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
        markFooter(ID_PRIMARY);
        x += width + Ui.GAP;

        if (dangerKey != null) {
            this.buttonList.add(new GuiButton(ID_DANGER, x, y, width, Ui.ROW, I18n.format(dangerKey)));
            markFooter(ID_DANGER);
            x += width + Ui.GAP;
        }
        if (secondaryKey != null) {
            this.buttonList.add(new GuiButton(ID_SECONDARY, x, y, width, Ui.ROW, I18n.format(secondaryKey)));
            markFooter(ID_SECONDARY);
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

        for (Object raw : this.buttonList) {
            GuiButton button = (GuiButton) raw;
            if (scrolls(button)) {
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

        // A rule under the title, so the edge of the scrolling area is unmistakably below it. Drawn to the same
        // edges as a frame, so the two line up on a screen that has both.
        Gui.drawRect(viewportLeft(), panelTop + Ui.PAD + 12, viewportRight(), panelTop + Ui.PAD + 13, 0x40FFFFFF);

        if (framedViewport()) {
            Ui.list(viewportLeft(), viewportTop(), viewportRight(), viewportBottom());
        }

        // Buttons are drawn here rather than by super, because the scrolling ones belong inside the clip and the
        // button bar and footer must stay outside it.
        boolean clipped = framedViewport();
        if (clipped) {
            beginClip();
        }
        drawContent(mouseX, mouseY, partialTicks);
        drawButtons(mouseX, mouseY, true);
        if (clipped) {
            endClip();
        }

        drawButtons(mouseX, mouseY, false);
        drawOverlay(mouseX, mouseY, partialTicks);
        drawScrollbar();
        drawTooltip(mouseX, mouseY);
    }

    private void drawButtons(int mouseX, int mouseY, boolean scrolling) {
        for (Object raw : this.buttonList) {
            GuiButton button = (GuiButton) raw;
            if (button.visible && scrolls(button) == scrolling) {
                button.drawButton(this.mc, mouseX, mouseY);
            }
        }
    }

    /**
     * Whether a button belongs to the scrolling content.
     *
     * <p>
     * Everything that is not declared part of the chrome does. Deciding it by id instead - anything below
     * {@link #ID_PRIMARY} - quietly excluded every screen that numbers its rows from a high base, so those rows were
     * drawn outside the clip and over the title while their own text fields were cut at it.
     */
    private boolean scrolls(GuiButton button) {
        return !isFooterButton(button);
    }

    /**
     * Clips drawing to the scrolling area.
     *
     * <p>
     * Only for a screen with a framed viewport: the clip is what keeps a scrolling row from spilling past the frame,
     * and a screen without one has nothing to clip against. Applied to those anyway, it cut the top and bottom edges
     * off the boxes they frame themselves - the profile list drew without a bottom border and so looked bottomless.
     *
     * <p>
     * {@code glScissor} works in real window pixels measured from the bottom-left, while everything here is in
     * scaled GUI pixels measured from the top-left, so both axes have to be converted.
     */
    private void beginClip() {
        ScaledResolution resolution = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);
        int scale = resolution.getScaleFactor();

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
            panelLeft * scale,
            (resolution.getScaledHeight() - contentBottom()) * scale,
            (panelRight - panelLeft) * scale,
            (contentBottom() - contentTop()) * scale);
    }

    private void endClip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
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
                // Wrapped rather than run out as one line: a tooltip explaining a setting is a sentence, and a
                // sentence drawn unbroken reaches the far edge of the screen from a button in the middle of it.
                @SuppressWarnings("unchecked")
                List<String> lines = this.fontRendererObj.listFormattedStringToWidth(text, TOOLTIP_WIDTH);
                drawHoveringText(lines, mouseX, mouseY, this.fontRendererObj);
            }
            return;
        }
    }

    /** Drawn after the buttons, for anything that has to sit on top of one. */
    protected void drawOverlay(int mouseX, int mouseY, float partialTicks) {}

    /** Last chance to read the controls before scrolling rebuilds them. */
    protected void beforeScroll() {}

    /**
     * Whether a click at this height lands in the scrolling area.
     *
     * <p>
     * A row clipped at the panel edge is still a whole button as far as hit testing goes, so a click on the part
     * that was cut away would otherwise press something the player cannot see.
     */
    protected boolean isInsideViewport(int mouseY) {
        return mouseY >= contentTop() && mouseY < contentBottom();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!isInsideViewport(mouseY)) {
            // Hidden buttons are skipped by the vanilla hit test; visibility is recomputed next frame anyway.
            for (Object raw : this.buttonList) {
                GuiButton button = (GuiButton) raw;
                if (scrolls(button)) {
                    button.visible = false;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

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
        int next = Math.max(0, Math.min(maxScroll(), scrollOffset + (wheel > 0 ? -SCROLL_STEP : SCROLL_STEP)));
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
