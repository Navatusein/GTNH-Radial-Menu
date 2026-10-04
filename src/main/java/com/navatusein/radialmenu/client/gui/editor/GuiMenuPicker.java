package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiScreen;
import com.navatusein.radialmenu.client.icon.IconRenderer;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.core.model.MenuNode;

/**
 * Picks one menu out of the profile, for an entry that is being moved into it.
 *
 * <p>
 * The whole tree rather than a list of names, indented by depth: two submenus called "Tools" in different branches
 * are a perfectly ordinary thing for a profile to have, and a flat list of titles would make the player guess which
 * one they were choosing.
 */
public class GuiMenuPicker extends UiScreen {

    public interface Result {

        void onMenuPicked(MenuNode menu);
    }

    private static final int ID_MENU_BASE = 100;

    /** How far one level of nesting steps the row to the right. */
    private static final int INDENT = 10;

    private final Result result;

    /**
     * The menu the entry is leaving, and the entry itself.
     *
     * <p>
     * Both are left out of the list: moving an entry into the menu it is already in does nothing, and moving a
     * submenu into itself - or into anything inside it - would cut that part of the tree loose from the root with
     * no way back to it.
     */
    private final MenuNode origin;

    private final MenuNode moved;

    private final List<MenuNode> menus = new ArrayList<>();

    private final List<Integer> depths = new ArrayList<>();

    private final List<int[]> rowPositions = new ArrayList<>();

    public GuiMenuPicker(MenuNode origin, MenuNode moved, Result result) {
        this.origin = origin;
        this.moved = moved;
        this.result = result;
    }

    @Override
    protected String titleKey() {
        return "radialmenu.menu.moveTitle";
    }

    @Override
    protected int panelWidth() {
        return 300;
    }

    @Override
    protected boolean framedViewport() {
        return true;
    }

    @Override
    protected void buildControls() {
        menus.clear();
        depths.clear();
        rowPositions.clear();

        collect(ProfileManager.active().root, 0);

        int y = scrolledTop();
        for (int i = 0; i < menus.size(); i++) {
            int indent = depths.get(i)
                .intValue() * INDENT;
            this.buttonList.add(
                new GuiButton(
                    ID_MENU_BASE + i,
                    contentLeft() + indent,
                    y,
                    contentWidth() - indent,
                    Ui.ROW,
                    label(menus.get(i))));
            rowPositions.add(new int[] { contentLeft() + indent, y });
            y += Ui.STEP;
        }

        setContentHeight(Math.max(Ui.STEP, y - scrolledTop()) + Ui.PAD);
        // One button, and it is the way out: there is nothing to confirm here - picking a row is the answer.
        addBottomBar("gui.cancel", null, null);
    }

    /**
     * Every submenu in the profile, depth first, so the list reads in the order the tree does.
     *
     * <p>
     * The subtree of the entry being moved is not walked into at all rather than filtered out of: a menu inside it
     * is just as impossible a destination as the entry itself, and skipping the branch says so once.
     */
    private void collect(MenuNode node, int depth) {
        if (node == null || !node.isCategory() || node == moved) {
            return;
        }
        if (node != origin) {
            menus.add(node);
            depths.add(Integer.valueOf(depth));
        }
        for (MenuNode child : node.childrenOrEmpty()) {
            collect(child, depth + 1);
        }
    }

    /** The root is named after its profile, because that is what the wheel's own header calls it. */
    private String label(MenuNode node) {
        if (node == ProfileManager.active().root) {
            return ProfileManager.activeName();
        }
        return node.title == null || node.title.isEmpty() ? I18n.format("radialmenu.menu.untitled") : node.title;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_PRIMARY) {
            onCancel();
            return;
        }
        int index = button.id - ID_MENU_BASE;
        if (index >= 0 && index < menus.size()) {
            MenuNode picked = menus.get(index);
            GuiStack.pop();
            result.onMenuPicked(picked);
        }
    }

    /** Back to the settings screen that opened this, rather than out of the editor altogether. */
    @Override
    protected void onCancel() {
        GuiStack.pop();
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        if (menus.isEmpty()) {
            this.fontRendererObj.drawString(
                I18n.format("radialmenu.menu.moveNowhere"),
                contentLeft(),
                scrolledTop() + 4,
                Ui.TEXT_MUTED);
            return;
        }

        // The icon goes on with the content rather than over the button: an item drawn after one comes out unlit.
        for (int i = 0; i < menus.size() && i < rowPositions.size(); i++) {
            int[] position = rowPositions.get(i);
            if (!isVisibleRow(position[1])) {
                continue;
            }
            IconRenderer.draw(menus.get(i).icon, position[0] + 3, position[1] + 2);
        }
    }
}
