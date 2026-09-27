package com.navatusein.radialmenu.client.gui;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;

import com.navatusein.radialmenu.client.action.ActionExecutors;
import com.navatusein.radialmenu.client.gui.editor.GuiSlotEditor;
import com.navatusein.radialmenu.client.input.HeldKeyResync;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.config.RadialMenuConfig;
import com.navatusein.radialmenu.core.geometry.RadialGeometry;
import com.navatusein.radialmenu.core.model.MenuNode;

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

    private int hoveredSlot = RadialGeometry.NO_SLOT;

    public GuiRadialWheel() {
        this.allowUserInput = RadialMenuConfig.allowInputWhileOpen;
        this.path.push(ProfileManager.active().root);
    }

    @Override
    public void initGui() {
        super.initGui();
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

        hoveredSlot = RadialGeometry.slotAtPoint(
            centerX,
            centerY,
            mouseX,
            mouseY,
            menu.slotCount(),
            0.0,
            RadialMenuConfig.effectiveInnerRadius());

        WheelRenderer.drawWheel(menu, centerX, centerY, hoveredSlot);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        boolean editRequested = RadialMenuConfig.rightClickToEdit ? button == 1 : button == 0 && isShiftKeyDown();

        if (editRequested && hoveredSlot != RadialGeometry.NO_SLOT) {
            openEditor();
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

    /** Opens the editor for the slot under the cursor, empty or not - that is how a new entry gets added. */
    private void openEditor() {
        GuiStack.push(new GuiSlotEditor(currentMenu(), hoveredSlot));
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
            hoveredSlot = RadialGeometry.NO_SLOT;
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
