package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Gui;
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
    private static final int INDENT = 12;

    /** Where in a level's column the connector is drawn. */
    private static final int STEM = 4;

    /** The colour of the tree, dimmer than the text: it is structure, not content. */
    private static final int LINE = 0xFF6A6A6A;

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

    /**
     * For each row, which ancestor levels still have a sibling coming after them.
     *
     * <p>
     * This is the whole of what a tree drawing needs: a vertical line is carried down past a row at every level
     * whose branch has not finished yet, and stops at the one that has. Without it a nested list is a column of
     * indents the reader has to pair up by eye.
     */
    private final List<boolean[]> trunks = new ArrayList<>();

    /** Whether each row is the last of its parent's submenus, which is what turns its elbow into a corner. */
    private final List<Boolean> lastOfParent = new ArrayList<>();

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
        trunks.clear();
        lastOfParent.clear();
        rowPositions.clear();

        collect(ProfileManager.active().root, 0, new boolean[0], true);

        int y = scrolledTop();
        for (int i = 0; i < menus.size(); i++) {
            int indent = depths.get(i)
                .intValue() * INDENT;
            GuiButton row = new GuiButton(
                ID_MENU_BASE + i,
                contentLeft() + indent,
                y,
                contentWidth() - indent,
                Ui.ROW,
                label(menus.get(i)));
            // The menu the entry is leaving is drawn but cannot be chosen: moving it there would do nothing, and
            // leaving the row out would put a gap in the tree where the player is standing.
            row.enabled = menus.get(i) != origin;
            this.buttonList.add(row);
            rowPositions.add(new int[] { contentLeft() + indent, y });
            y += Ui.STEP;
        }

        // The line that says there is nowhere to move to scrolls with the tree, so it is part of the height.
        setContentHeight(Math.max(Ui.STEP, y - scrolledTop()) + (hasTarget() ? 0 : Ui.STEP) + Ui.PAD);
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
    private void collect(MenuNode node, int depth, boolean[] trunk, boolean last) {
        if (node == null || !node.isCategory() || node == moved) {
            return;
        }
        menus.add(node);
        depths.add(Integer.valueOf(depth));
        trunks.add(trunk);
        lastOfParent.add(Boolean.valueOf(last));

        List<MenuNode> submenus = new ArrayList<>();
        for (MenuNode child : node.childrenOrEmpty()) {
            if (child != null && child.isCategory() && child != moved) {
                submenus.add(child);
            }
        }

        // A child carries its parent's trunk plus one more mark: a line through this level, unless this row was
        // the last of its own parent and the branch has nothing further to carry down.
        boolean[] childTrunk = new boolean[depth + 1];
        System.arraycopy(trunk, 0, childTrunk, 0, trunk.length);
        if (depth > 0) {
            childTrunk[depth - 1] = !last;
        }
        for (int i = 0; i < submenus.size(); i++) {
            collect(submenus.get(i), depth + 1, childTrunk, i == submenus.size() - 1);
        }
    }

    /** Whether anything in the list can actually be chosen - the menu being left does not count. */
    private boolean hasTarget() {
        for (MenuNode node : menus) {
            if (node != origin) {
                return true;
            }
        }
        return false;
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

    /**
     * The lines that say what sits inside what.
     *
     * <p>
     * Drawn rather than written. The box-drawing characters this imitates are not in Minecraft's font, and even
     * where a glyph exists its width is whatever the font says, so a column of them lines up only by luck. Three
     * rectangles per row always line up.
     *
     * <p>
     * The row's own height is used rather than the step between rows, so the vertical of a carried branch runs from
     * the top of one row to the top of the next with no gap - a dashed trunk reads as two trees.
     */
    private void drawBranch(int index, int top) {
        int depth = depths.get(index)
            .intValue();
        if (depth == 0) {
            return;
        }
        boolean[] trunk = trunks.get(index);
        int middle = top + Ui.ROW / 2;

        for (int level = 0; level < depth - 1 && level < trunk.length; level++) {
            if (trunk[level]) {
                int x = stemX(level);
                Gui.drawRect(x, top - (Ui.STEP - Ui.ROW), x + 1, top + Ui.ROW, LINE);
            }
        }

        int x = stemX(depth - 1);
        // Down to this row from the one above, and on past it only while the branch has more to come.
        Gui.drawRect(
            x,
            top - (Ui.STEP - Ui.ROW),
            x + 1,
            lastOfParent.get(index)
                .booleanValue() ? middle + 1 : top + Ui.ROW,
            LINE);
        Gui.drawRect(x, middle, contentLeft() + depth * INDENT, middle + 1, LINE);
    }

    private int stemX(int level) {
        return contentLeft() + level * INDENT + STEM;
    }

    /** Back to the settings screen that opened this, rather than out of the editor altogether. */
    @Override
    protected void onCancel() {
        GuiStack.pop();
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        if (!hasTarget()) {
            // Under the tree rather than instead of it: the profile still has a shape worth seeing, and the row
            // the player is standing in is drawn greyed - the message says why nothing else can be clicked.
            int y = rowPositions.isEmpty() ? scrolledTop() : rowPositions.get(rowPositions.size() - 1)[1] + Ui.STEP;
            this.fontRendererObj
                .drawString(I18n.format("radialmenu.menu.moveNowhere"), contentLeft(), y + 4, Ui.TEXT_MUTED);
        }

        // The icon goes on with the content rather than over the button: an item drawn after one comes out unlit.
        for (int i = 0; i < menus.size() && i < rowPositions.size(); i++) {
            int[] position = rowPositions.get(i);
            if (!isVisibleRow(position[1])) {
                continue;
            }
            drawBranch(i, position[1]);
            IconRenderer.draw(menus.get(i).icon, position[0] + 3, position[1] + 2);
        }
    }
}
