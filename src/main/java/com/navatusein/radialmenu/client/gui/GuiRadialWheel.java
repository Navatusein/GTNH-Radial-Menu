package com.navatusein.radialmenu.client.gui;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;

import com.navatusein.radialmenu.client.action.ActionExecutors;
import com.navatusein.radialmenu.client.gui.editor.GuiMenuSettings;
import com.navatusein.radialmenu.client.gui.editor.GuiSlotEditor;
import com.navatusein.radialmenu.client.input.HeldKeyResync;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.config.RadialMenuConfig;
import com.navatusein.radialmenu.config.WheelConfig;
import com.navatusein.radialmenu.core.geometry.RadialGeometry;
import com.navatusein.radialmenu.core.model.MenuNode;
import com.navatusein.radialmenu.core.model.SlotLayout;
import com.navatusein.radialmenu.core.model.WheelColors;

/**
 * The wheel itself.
 *
 * <p>
 * Two things about this screen are deliberate and easy to get wrong:
 * <ul>
 * <li>{@code doesGuiPauseGame()} returns false, or single player would freeze whenever the wheel is up.</li>
 * <li>{@code allowUserInput} is set, because 1.7.10 gates its whole keyboard and mouse block on
 * {@code currentScreen == null || currentScreen.allowUserInput}. Without it the player stops moving. Opening the
 * screen still runs {@code unPressAllKeys()} once, so any key already held has to be re-applied by hand - see
 * {@link HeldKeyResync}.</li>
 * </ul>
 */
public class GuiRadialWheel extends GuiScreen {

    /** Path from the root to the menu currently on screen, so going back needs no configured entry. */
    private final Deque<MenuNode> path = new ArrayDeque<>();

    /**
     * Set once the wheel should stay up on its own: after drilling into a submenu, or after firing an entry marked
     * keepOpen. Releasing the wheel key no longer closes it.
     */
    private boolean sticky;

    /** What is selected: where the cursor points, or what the wheel was turned to. */
    /** The clock behind the opening animation and the push under the cursor. */
    private final WheelAnimator animator = new WheelAnimator();

    private int hoveredSlot = RadialGeometry.NO_SLOT;

    /**
     * What the cursor is over, whether or not that is what selects.
     *
     * <p>
     * Editing stays a pointing job even when choosing is not: the dead zone in the middle is how a menu's own
     * settings are reached, and a selection driven by the scroll wheel never sits in it.
     */
    private int pointerSlot = RadialGeometry.NO_SLOT;

    public GuiRadialWheel() {
        this.allowUserInput = RadialMenuConfig.allowInputWhileOpen;
        this.path.push(ProfileManager.active().root);
    }

    @Override
    public void initGui() {
        super.initGui();
        animator.reset();
        if (RadialMenuConfig.centerCursorOnOpen) {
            Mouse.setCursorPosition(this.mc.displayWidth / 2, this.mc.displayHeight / 2);
        }
        // Must happen after the screen is current: displayGuiScreen unpresses every binding on the way in.
        HeldKeyResync.resyncHeldKeys();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    public boolean isSticky() {
        return sticky;
    }

    private MenuNode currentMenu() {
        return path.peek();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        MenuNode menu = currentMenu();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Reading the modifier per frame costs nothing - it is a cached keyboard state, not a poll.
        boolean editMode = isEditModifierDown();
        int slotCount = sectorCount(menu, editMode);

        pointerSlot = RadialGeometry
            .slotAtPoint(centerX, centerY, mouseX, mouseY, slotCount, 0.0, WheelConfig.effectiveInnerRadius());

        if (RadialMenuConfig.scrollToSelect) {
            // The wheel decides what is selected, so the cursor does not - but the selection still has to survive a
            // menu whose sector count just changed under it.
            hoveredSlot = slotCount <= 0 ? RadialGeometry.NO_SLOT : Math.min(hoveredSlot, slotCount - 1);
            if (hoveredSlot < 0 && slotCount > 0) {
                hoveredSlot = 0;
            }
        } else {
            hoveredSlot = pointerSlot;
        }

        // Resolved once and shared, so the wash behind the wheel and the wheel itself cannot disagree about which
        // menu's colours they are drawing.
        WheelColors colors = WheelRenderer.colorsFor(menu, editMode);
        // The switch is the master and the colour is only what it draws with, so a profile that carries a background
        // colour does not quietly turn the wash back on for someone who wanted it off.
        if (WheelConfig.dimBackground && (colors.background >>> 24) != 0) {
            // Not drawDefaultBackground(): that one is vanilla's fixed gradient, and the point here is a colour the
            // player chose - including none at all, which is the default and leaves the world untouched.
            drawRect(0, 0, this.width, this.height, colors.background);
        }

        animator.advance(slotCount, hoveredSlot);
        WheelRenderer.drawWheel(menu, colors, slotCount, centerX, centerY, hoveredSlot, editMode, animator);
        WheelRenderer.drawHeader(this.width, ProfileManager.activeName(), breadcrumb(), editMode);
        // Only while the cursor is actually in the dead zone: elsewhere the centre belongs to the hovered entry's
        // name, and the two were drawing on top of each other.
        if (editMode && pointerSlot == RadialGeometry.NO_SLOT) {
            WheelRenderer.drawCenterHint(menu, centerX, centerY, hoveredSlot);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /**
     * Sectors to draw.
     *
     * <p>
     * A dynamic wheel gains one empty sector while editing, because every sector it has is already occupied and
     * there would otherwise be nowhere to click to add an entry.
     */
    private int sectorCount(MenuNode menu, boolean editMode) {
        int count = menu.slotCount();
        if (editMode && menu.layoutOrDefault().mode == SlotLayout.Mode.DYNAMIC) {
            count++;
        }
        return count;
    }

    /** Shift, unless the player configured right-click for editing instead. */
    private boolean isEditModifierDown() {
        return !RadialMenuConfig.rightClickToEdit && isShiftKeyDown();
    }

    /**
     * Path from the root menu to the one on screen, for the header.
     *
     * <p>
     * The root itself is left out: the header already names the profile, and a root titled after its profile - which
     * is what {@code Profile.empty} creates and what a generated file tends to carry - read as "default > default >
     * Overlays".
     */
    private String breadcrumb() {
        StringBuilder builder = new StringBuilder();
        MenuNode[] nodes = path.toArray(new MenuNode[0]);
        // The deque has the current menu first, so walk it backwards to read root-to-here.
        for (int i = nodes.length - 2; i >= 0; i--) {
            String title = nodes[i].title;
            if (title == null || title.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" > ");
            }
            builder.append(title);
        }
        return builder.toString();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        boolean editRequested = RadialMenuConfig.rightClickToEdit ? button == 1 : button == 0 && isShiftKeyDown();

        if (editRequested) {
            // The dead zone is the menu itself rather than any one entry, so editing there edits the menu - which is
            // also the only way to reach the root menu's settings.
            if (pointerSlot == RadialGeometry.NO_SLOT) {
                GuiStack.push(new GuiMenuSettings(currentMenu()));
            } else {
                openEditor();
            }
            return;
        }
        if (button == 1) {
            goBackOrClose();
            return;
        }
        if (button == 0) {
            activateHovered();
        }
    }

    /**
     * Opens the editor for the sector under the cursor, empty or not - that is how a new entry gets added.
     *
     * <p>
     * Sectors and list positions are not the same thing on a dynamic wheel, so the sector is translated first and
     * the editor is handed a list position throughout.
     *
     * <p>
     * An empty sector is a new entry: on a fixed wheel it is the position that sector stands for, on a dynamic one
     * the end of the list, which is where that wheel puts its extra sector.
     */
    private void openEditor() {
        MenuNode menu = currentMenu();
        int index = menu.childIndexForSlot(pointerSlot);
        if (index < 0) {
            index = menu.layoutOrDefault().mode == SlotLayout.Mode.DYNAMIC ? menu.appendIndex() : pointerSlot;
        }
        GuiStack.push(new GuiSlotEditor(menu, index));
    }

    /**
     * Turning the wheel moves the selection, when the player asked for that instead of pointing.
     *
     * <p>
     * Wrapping at both ends, because a ring has no first or last entry - stopping at one would be an edge the wheel
     * itself does not have.
     */
    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        if (!RadialMenuConfig.scrollToSelect) {
            return;
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int slotCount = sectorCount(currentMenu(), isEditModifierDown());
        if (slotCount <= 0) {
            return;
        }
        int step = wheel > 0 ? -1 : 1;
        hoveredSlot = ((hoveredSlot < 0 ? 0 : hoveredSlot) + step + slotCount) % slotCount;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            goBackOrClose();
            return;
        }
        // Everything else is intentionally ignored: with allowUserInput set, vanilla is already feeding keys through
        // to the game so the player can keep moving.
    }

    /** Called by the input handler when the wheel key is released. */
    public void onWheelKeyReleased() {
        if (sticky) {
            return;
        }
        if (RadialMenuConfig.releaseToSelect) {
            activateHovered();
        } else {
            close();
        }
    }

    /**
     * Runs whatever the cursor is on: enters a submenu, or queues a leaf's action and closes unless the entry asked to
     * stay open.
     */
    private void activateHovered() {
        MenuNode selected = currentMenu().childAt(hoveredSlot);
        if (selected == null) {
            close();
            return;
        }

        if (selected.isCategory()) {
            path.push(selected);
            sticky = true;
            // A submenu is a new wheel arriving; one that appeared fully drawn while its neighbours animated would
            // look like something went wrong rather than like a choice.
            animator.reset();
            // A submenu opens with nothing chosen, unless the cursor is not what chooses - then it opens on the
            // first entry, because there would otherwise be no way to choose anything at all.
            hoveredSlot = RadialMenuConfig.scrollToSelect ? 0 : RadialGeometry.NO_SLOT;
            return;
        }

        if (selected.keepOpen) {
            sticky = true;
            ActionExecutors.enqueue(selected.action);
            return;
        }

        // Close first, then queue: the action runs on the next tick with the game focused again.
        close();
        ActionExecutors.enqueue(selected.action);
    }

    private void goBackOrClose() {
        if (path.size() > 1) {
            path.pop();
            hoveredSlot = RadialGeometry.NO_SLOT;
        } else {
            close();
        }
    }

    private void close() {
        Minecraft.getMinecraft()
            .displayGuiScreen(null);
    }
}
