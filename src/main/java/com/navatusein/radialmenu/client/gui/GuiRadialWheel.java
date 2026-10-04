package com.navatusein.radialmenu.client.gui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;

import com.navatusein.radialmenu.client.action.ActionExecutors;
import com.navatusein.radialmenu.client.gui.editor.GuiMenuSettings;
import com.navatusein.radialmenu.client.gui.editor.GuiSlotEditor;
import com.navatusein.radialmenu.client.input.HeldKeyResync;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.client.script.ScriptHost;
import com.navatusein.radialmenu.config.RadialMenuConfig;
import com.navatusein.radialmenu.config.WheelConfig;
import com.navatusein.radialmenu.core.geometry.ArcLayout;
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
 *
 * <p>
 * A submenu arrives one of two ways, and the screen holds the state for both. A replacing one is a {@link #path}:
 * the wheel shows one menu at a time and going back pops. An inline one is an {@link #expanded} branch: the parent
 * stays where it is and the submenu unfolds as a ring outside it, so several can be open at once and the rings on
 * screen are a slice of the tree rather than a single menu.
 */
public class GuiRadialWheel extends GuiScreen {

    /** Nothing deeper than this is drawn, however much room the screen has. Past it the rings are unaimable. */
    private static final int MAX_RING_LEVELS = 5;

    /** The narrowest an inline sector is allowed to get, in degrees. Below it, aiming is guesswork. */
    private static final double MIN_INLINE_SLOT_SPAN = 8.0;

    /**
     * And the widest, in degrees.
     *
     * <p>
     * A menu with two entries has sectors a hundred and eighty degrees across, and a submenu of four matching them
     * would ask for twice the circle. A quarter turn is already a generous target; past that the sector stops being
     * easier to hit and only takes room from everything else.
     */
    private static final double MAX_INLINE_SLOT_SPAN = 45.0;

    /** Path from the root to the menu currently on screen, so going back needs no configured entry. */
    private final Deque<MenuNode> path = new ArrayDeque<>();

    /**
     * Inline submenus currently unfolded.
     *
     * <p>
     * By identity rather than by name or position: it is the node itself that is open, so an entry renamed or moved
     * while the wheel is up stays open, and two submenus that happen to share a title do not.
     */
    private final Set<MenuNode> expanded = Collections.newSetFromMap(new IdentityHashMap<MenuNode, Boolean>());

    /**
     * Set once the wheel should stay up on its own: after drilling into a submenu, or after firing an entry marked
     * keepOpen. Releasing the wheel key no longer closes it.
     */
    private boolean sticky;

    /** Built by a script for one choice, rather than read from a profile. Not editable, and sticky from the start. */
    private final boolean scripted;

    /**
     * The clock behind the opening animation and the push under the cursor, one per menu on screen.
     *
     * <p>
     * Per menu, because an inline submenu arrives while its parent is already there: sharing one clock would either
     * replay the parent's arrival or deny the newcomer its own. Keyed by identity and rebuilt each frame from the
     * rings actually drawn, so a branch closed and opened again arrives afresh rather than snapping into place.
     */
    private Map<MenuNode, WheelAnimator> animators = new IdentityHashMap<>();

    /** The rings on screen, outwards from the middle. Rebuilt every frame from the tree and what is unfolded. */
    private List<WheelRing> rings = Collections.emptyList();

    /** Which ring the selection is on. The middle one unless an inline branch is open and pointed at. */
    private int hoveredRing;

    private int hoveredSlot = RadialGeometry.NO_SLOT;

    /**
     * What the cursor is over, whether or not that is what selects.
     *
     * <p>
     * Editing stays a pointing job even when choosing is not: the dead zone in the middle is how a menu's own
     * settings are reached, and a selection driven by the scroll wheel never sits in it.
     */
    private int pointerRing;

    private int pointerSlot = RadialGeometry.NO_SLOT;

    /** The inline submenu the cursor has settled on, and since when - the hover delay is measured from here. */
    private MenuNode hoverCandidate;

    private long hoverCandidateSince;

    /**
     * The entry whose action was last fired, for a script to hang its next menu off.
     *
     * <p>
     * A script that answers a choice with another menu is answering <em>that</em> entry, so an inline one unfolds
     * there. Remembered rather than worked out afterwards: by the time the script asks, the cursor has moved and the
     * selection may be somewhere else entirely.
     */
    private MenuNode lastChosen;

    /**
     * The entry a script's inline menu was hung off, while the player has not answered it.
     *
     * <p>
     * Kept so folding that branch away can be reported as the dismissal it is. The wheel stays up, so nothing else
     * would tell the script its question is gone, and it would wait for an answer that can no longer come.
     */
    private MenuNode scriptInline;

    /**
     * A wheel a script built, rather than one from the active profile.
     *
     * <p>
     * Sticky from the first frame, because there is no key being held: by the time a script opens a menu the player let
     * go of the wheel key long ago, and a wheel that closed on the next release would never be seen. Not editable
     * either - the menu is built for one choice and thrown away, so a slot editor pointed at it would be editing
     * something that stops existing the moment it is answered.
     */
    public GuiRadialWheel(MenuNode root) {
        this(root, true);
    }

    public GuiRadialWheel() {
        this(ProfileManager.active().root, false);
    }

    private GuiRadialWheel(MenuNode root, boolean scripted) {
        this.allowUserInput = RadialMenuConfig.allowInputWhileOpen;
        this.path.push(root);
        this.scripted = scripted;
        this.sticky = scripted;
    }

    /**
     * Swaps what the wheel is showing, for a script that kept it open.
     *
     * <p>
     * Deliberately without resetting the animators: this is the same wheel showing different entries, and replaying
     * the arrival would read as a second menu rather than as a list that changed.
     */
    public void replaceRoot(MenuNode root) {
        path.clear();
        path.push(root);
        forgetExpanded();
        lastChosen = null;
    }

    /**
     * Hangs a script's menu off one of the entries on screen, as a ring around it.
     *
     * <p>
     * The menu becomes that entry's own submenu - children, layout and colours - which is the whole trick: from here
     * on it is drawn, aimed at, unfolded and folded away by everything that already handles a submenu out of a
     * profile. The entry's action goes with it, and is no loss: it was the {@code scriptResume} the player just fired,
     * and the answer to it is the ring that has taken its place.
     *
     * @return false if there is nothing to unfold it from - no such entry on screen, or a selection driven by the
     *         scroll wheel, which has no way to cross into an inline ring. The caller then shows it as a wheel of its
     *         own: a worse menu than the script asked for, but a working one.
     */
    public boolean attachInline(MenuNode anchor, MenuNode submenu) {
        if (!scripted || anchor == null
            || submenu == null
            || submenu.childrenOrEmpty()
                .isEmpty()) {
            return false;
        }
        if (RadialMenuConfig.scrollToSelect) {
            return false;
        }
        WheelRing parent = ringHolding(anchor);
        if (parent == null) {
            return false;
        }

        anchor.action = null;
        anchor.children = new ArrayList<>(submenu.childrenOrEmpty());
        anchor.layout = submenu.layoutOrDefault();
        anchor.layout.opening = SlotLayout.Opening.INLINE;
        anchor.style = submenu.style;

        // Forgotten before the branch is unfolded, because unfolding folds away whichever sibling was open - and the
        // ring a script put there last is one of the candidates. That question has already been answered; reporting it
        // dismissed now would dismiss the one being asked.
        scriptInline = null;
        expand(parent, anchor);
        scriptInline = anchor;
        // The clock of a branch that was never on screen is nothing to keep, and dropping it is what makes this one
        // arrive rather than appear finished.
        animators.remove(anchor);
        sticky = true;
        return true;
    }

    /** The entry whose action the player last fired, or null if they have not chosen anything. */
    public MenuNode lastChosen() {
        return lastChosen;
    }

    /** Which ring on screen holds this entry, or null if none does. */
    private WheelRing ringHolding(MenuNode node) {
        for (WheelRing ring : rings) {
            for (MenuNode child : ring.menu.childrenOrEmpty()) {
                if (child == node) {
                    return ring;
                }
            }
        }
        return null;
    }

    /**
     * Folds every branch away at once - the wheel is showing something else now.
     *
     * <p>
     * Through here rather than by clearing the set, because a script may be waiting on one of them: a question whose
     * ring is gone has to be reported dismissed, or the script waits for an answer nothing can give it.
     */
    private void forgetExpanded() {
        for (MenuNode node : new ArrayList<>(expanded)) {
            collapse(node);
        }
        expanded.clear();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        // Whatever took the screen away - a choice, Escape, another mod's GUI - the script that opened it has to hear
        // about it, or it waits for an answer that can no longer come.
        if (scripted) {
            ScriptHost.wheelClosed(this);
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        animators = new IdentityHashMap<>();
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
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Reading the modifier per frame costs nothing - it is a cached keyboard state, not a poll.
        boolean editMode = isEditModifierDown();
        rings = buildRings(editMode);

        resolvePointer(mouseX, mouseY, centerX, centerY);

        if (RadialMenuConfig.scrollToSelect) {
            // The wheel decides what is selected, so the cursor does not - but the selection still has to survive a
            // menu whose sector count just changed under it.
            clampScrolledSelection();
        } else {
            hoveredRing = pointerRing;
            hoveredSlot = pointerSlot;
            // An inline branch unfolds from pointing at it, so the rings have to be built again before anything is
            // drawn: a submenu that waited a frame would arrive behind the cursor that asked for it.
            if (expandHovered()) {
                rings = buildRings(editMode);
                resolvePointer(mouseX, mouseY, centerX, centerY);
                hoveredRing = pointerRing;
                hoveredSlot = pointerSlot;
            }
        }

        // Resolved once and shared, so the wash behind the wheel and the wheel itself cannot disagree about which
        // menu's colours they are drawing.
        WheelColors colors = WheelRenderer.colorsFor(currentMenu(), editMode);
        // The switch is the master and the colour is only what it draws with, so a profile that carries a background
        // colour does not quietly turn the wash back on for someone who wanted it off.
        if (WheelConfig.dimBackground && (colors.background >>> 24) != 0) {
            // Not drawDefaultBackground(): that one is vanilla's fixed gradient, and the point here is a colour the
            // player chose - including none at all, which is the default and leaves the world untouched.
            drawRect(0, 0, this.width, this.height, colors.background);
        }

        drawRings(centerX, centerY, colors, editMode);

        WheelRenderer.drawHeader(this.width, ProfileManager.activeName(), breadcrumb(), editMode);
        // Only while the cursor is actually in the dead zone: elsewhere the centre belongs to the hovered entry's
        // name, and the two were drawing on top of each other.
        if (editMode && pointerSlot == RadialGeometry.NO_SLOT) {
            // Against the ring the selection is on, not the one at the middle: the hint sits under whatever name is
            // being drawn, and asking the wrong menu for that name measures the wrong number of lines.
            WheelRing selected = ringAt(hoveredRing);
            WheelRenderer
                .drawCenterHint(selected == null ? currentMenu() : selected.menu, centerX, centerY, hoveredSlot);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /**
     * Draws every ring from the middle outwards, then the name of whatever is selected.
     *
     * <p>
     * Outwards so an inline submenu's first sector overlaps the ring it grew from rather than being overdrawn by it,
     * and the name last and once: it lives in the hole in the middle, which belongs to no ring in particular.
     */
    private void drawRings(int centerX, int centerY, WheelColors baseColors, boolean editMode) {
        Map<MenuNode, WheelAnimator> live = new IdentityHashMap<>();

        for (int i = 0; i < rings.size(); i++) {
            WheelRing ring = rings.get(i);
            WheelAnimator animator = animatorFor(ring.menu, live);
            int selected = i == hoveredRing ? hoveredSlot : RadialGeometry.NO_SLOT;
            animator.advance(ring.slotCount, selected);
            WheelColors colors = i == 0 ? baseColors : WheelRenderer.colorsFor(ring.menu, editMode);
            WheelRenderer.drawRing(ring, colors, centerX, centerY, selected, animator);
        }

        // Only the clocks of menus still on screen are kept, so a branch folded away and opened again arrives
        // rather than appearing finished.
        animators = live;

        WheelRing hovered = ringAt(hoveredRing);
        if (hovered != null) {
            WheelRenderer.drawLabel(hovered.menu, centerX, centerY, hoveredSlot);
        }
    }

    private WheelAnimator animatorFor(MenuNode menu, Map<MenuNode, WheelAnimator> live) {
        WheelAnimator animator = animators.get(menu);
        if (animator == null) {
            animator = new WheelAnimator();
        }
        live.put(menu, animator);
        return animator;
    }

    private WheelRing ringAt(int index) {
        return index >= 0 && index < rings.size() ? rings.get(index) : null;
    }

    /**
     * The rings on screen: the menu at the middle, then every unfolded inline submenu, level by level.
     *
     * <p>
     * Level by level rather than branch by branch, because the arcs of one level have to be placed against each
     * other - two neighbouring entries expanded at once ask for overlapping stretches of the same ring, and
     * {@link ArcLayout} is what settles it. A branch whose parent is not drawn is never reached, so collapsing one
     * folds everything under it away without having to walk the tree.
     */
    private List<WheelRing> buildRings(boolean editMode) {
        List<WheelRing> built = new ArrayList<>();
        MenuNode base = currentMenu();
        built.add(WheelRing.root(base, sectorCount(base, editMode)));

        // However much of the screen is left once the deepest ring would need to fit on it.
        double limit = Math.min(this.width, this.height) / 2.0 - 2.0;

        int levelStart = 0;
        for (int level = 0; level < MAX_RING_LEVELS; level++) {
            if (WheelRing.outerRadius(level + 1) > limit) {
                break;
            }
            int levelEnd = built.size();
            List<int[]> origins = new ArrayList<>();
            List<Double> centers = new ArrayList<>();
            List<Double> spans = new ArrayList<>();

            for (int index = levelStart; index < levelEnd; index++) {
                WheelRing ring = built.get(index);
                for (int slot = 0; slot < ring.slotCount; slot++) {
                    MenuNode child = ring.childAt(slot);
                    if (child == null || !isUnfolded(child)) {
                        continue;
                    }
                    int childSlots = sectorCount(child, editMode);
                    if (childSlots <= 0) {
                        continue;
                    }
                    origins.add(new int[] { index, slot, childSlots });
                    centers.add(ring.slotCenter(slot));
                    spans.add(childSlots * inlineSlotSpan(ring, level + 1));
                }
            }

            if (origins.isEmpty()) {
                break;
            }

            ArcLayout.Arc[] arcs = ArcLayout.place(toArray(centers), toArray(spans));
            for (int i = 0; i < origins.size(); i++) {
                int[] origin = origins.get(i);
                WheelRing parent = built.get(origin[0]);
                built.add(
                    new WheelRing(
                        parent.childAt(origin[1]),
                        level + 1,
                        arcs[i].start,
                        arcs[i].span,
                        origin[2],
                        origin[0],
                        origin[1]));
            }
            levelStart = levelEnd;
        }
        return built;
    }

    /**
     * How wide one sector of an inline submenu is.
     *
     * <p>
     * The same arc length as the sector it unfolded from, which further out is fewer degrees - so an entry is the
     * same size to aim at whichever ring it is on, and a submenu takes up no more of the circle than it needs.
     * Floored, because the arithmetic alone would eventually produce sectors too thin to hit.
     */
    private static double inlineSlotSpan(WheelRing parent, int level) {
        double childMid = (WheelRing.innerRadius(level) + WheelRing.outerRadius(level)) / 2.0;
        if (childMid <= 0.0) {
            return MIN_INLINE_SLOT_SPAN;
        }
        double span = parent.slotSpan() * parent.midRadius() / childMid;
        return Math.max(MIN_INLINE_SLOT_SPAN, Math.min(MAX_INLINE_SLOT_SPAN, span));
    }

    private static double[] toArray(List<Double> values) {
        double[] array = new double[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    /** Whether this entry is an inline submenu the player has opened. */
    private boolean isUnfolded(MenuNode node) {
        return isInlineSubmenu(node) && expanded.contains(node);
    }

    /**
     * Whether this entry opens as a ring around its parent rather than in its place.
     *
     * <p>
     * Never while the selection is driven by the scroll wheel: that model has one ring and one index, and an inline
     * submenu is several rings at once. Such a menu drills in like any other there, which is a worse wheel than the
     * player asked for but a working one, rather than a selection with nowhere to go.
     */
    private boolean isInlineSubmenu(MenuNode node) {
        return node != null && node.isCategory()
            && node.layoutOrDefault()
                .isInline()
            && !RadialMenuConfig.scrollToSelect;
    }

    /**
     * Unfolds the inline submenu the cursor has been resting on.
     *
     * <p>
     * After a delay, because the cursor crosses every sector between the one it left and the one it is going to -
     * expanding on the first frame of contact opens half the wheel on the way past. The delay is what the player
     * sets it to, and zero means they would rather have it at once.
     *
     * @return true if something opened, so the rings have to be built again
     */
    private boolean expandHovered() {
        MenuNode hovered = hoveredNode();
        if (hovered == null || !isInlineSubmenu(hovered) || expanded.contains(hovered)) {
            hoverCandidate = null;
            return false;
        }

        long now = System.currentTimeMillis();
        if (hoverCandidate != hovered) {
            hoverCandidate = hovered;
            hoverCandidateSince = now;
        }
        if (now - hoverCandidateSince < RadialMenuConfig.inlineHoverDelayMs) {
            return false;
        }

        hoverCandidate = null;
        expand(ringAt(hoveredRing), hovered);
        return true;
    }

    /**
     * Unfolds one branch, and folds away the one its own menu had open.
     *
     * <p>
     * Siblings only: what the parent had open is the branch this one is replacing, so it goes, while the chain this
     * entry hangs off stays - folding that away would take the ring the player is pointing at out from under them.
     * One branch to a menu is what keeps the wheel readable; two neighbours unfolded at once put two rings over the
     * same stretch of circle and neither of them where its entry is.
     */
    private void expand(WheelRing parent, MenuNode node) {
        if (parent != null) {
            for (MenuNode sibling : parent.menu.childrenOrEmpty()) {
                if (sibling != null && sibling != node) {
                    collapse(sibling);
                }
            }
        }
        expanded.add(node);
    }

    /**
     * Folds a branch away, and everything that was open inside it.
     *
     * <p>
     * All the way down even where nothing is open, because a branch folded away has to be forgotten: left in the
     * set, whatever was open inside it would come back the next time the branch itself did.
     */
    private void collapse(MenuNode node) {
        if (node == null) {
            return;
        }
        expanded.remove(node);
        if (node == scriptInline) {
            scriptInline = null;
            ScriptHost.inlineCollapsed(this);
        }
        for (MenuNode child : node.childrenOrEmpty()) {
            if (child != null && child.isCategory()) {
                collapse(child);
            }
        }
    }

    /**
     * Which ring and sector the cursor is on.
     *
     * <p>
     * The deepest ring that starts at or before the cursor and covers its angle. Deepest, so a cursor pushed past
     * the rim of an unfolded branch still chooses within it; at or before, so the few pixels between two rings
     * belong to the inner one rather than to nothing at all - aiming at a wheel should not require landing inside
     * a band.
     */
    private void resolvePointer(int mouseX, int mouseY, int centerX, int centerY) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        pointerRing = 0;
        pointerSlot = RadialGeometry.NO_SLOT;
        if (distance < WheelConfig.effectiveInnerRadius()) {
            return;
        }

        double angle = RadialGeometry.angleTo(centerX, centerY, mouseX, mouseY);
        int bestLevel = -1;
        for (int i = 0; i < rings.size(); i++) {
            WheelRing ring = rings.get(i);
            if (distance < ring.inner || ring.level <= bestLevel) {
                continue;
            }
            int slot = ring.slotAt(angle);
            if (slot < 0) {
                continue;
            }
            bestLevel = ring.level;
            pointerRing = i;
            pointerSlot = slot;
        }
    }

    /** Keeps a scrolled selection inside a menu whose sector count has just changed under it. */
    private void clampScrolledSelection() {
        WheelRing ring = ringAt(hoveredRing);
        if (ring == null) {
            hoveredRing = 0;
            ring = ringAt(0);
        }
        int slotCount = ring == null ? 0 : ring.slotCount;
        hoveredSlot = slotCount <= 0 ? RadialGeometry.NO_SLOT : Math.min(hoveredSlot, slotCount - 1);
        if (hoveredSlot < 0 && slotCount > 0) {
            hoveredSlot = 0;
        }
    }

    private MenuNode hoveredNode() {
        WheelRing ring = ringAt(hoveredRing);
        return ring == null ? null : ring.childAt(hoveredSlot);
    }

    private MenuNode pointedNode() {
        WheelRing ring = ringAt(pointerRing);
        return ring == null ? null : ring.childAt(pointerSlot);
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

    /** Shift, unless the player configured right-click for editing instead. Never on a script's wheel. */
    private boolean isEditModifierDown() {
        return !scripted && !RadialMenuConfig.rightClickToEdit && isShiftKeyDown();
    }

    /**
     * Path from the root menu to the one on screen, for the header.
     *
     * <p>
     * The root itself is left out: the header already names the profile, and a root titled after its profile - which
     * is what {@code Profile.empty} creates and what a generated file tends to carry - read as "default > default >
     * Overlays".
     *
     * <p>
     * Inline submenus are not in it. They are on screen with their own entry beside them, so naming them in the
     * header would say twice what the ring already says - and with two branches open there is no single path to
     * write down.
     */
    private String breadcrumb() {
        StringBuilder builder = new StringBuilder();
        MenuNode[] nodes = path.toArray(new MenuNode[0]);
        // The deque has the current menu first, so walk it backwards to read root-to-here. A script's root is named
        // here rather than skipped: the profile name says nothing about a menu the script built, so its own title is
        // the only thing that would.
        for (int i = nodes.length - (scripted ? 1 : 2); i >= 0; i--) {
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
        boolean editRequested = !scripted
            && (RadialMenuConfig.rightClickToEdit ? button == 1 : button == 0 && isShiftKeyDown());

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
     * Against the menu of whichever ring the cursor is on, so an unfolded submenu's entries are edited where they
     * are drawn instead of having to be opened first.
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
        WheelRing ring = ringAt(pointerRing);
        if (ring == null) {
            return;
        }
        MenuNode menu = ring.menu;
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
        WheelRing ring = ringAt(hoveredRing);
        int slotCount = ring == null ? sectorCount(currentMenu(), isEditModifierDown()) : ring.slotCount;
        if (slotCount <= 0) {
            return;
        }
        int step = wheel > 0 ? -1 : 1;
        hoveredSlot = ((hoveredSlot < 0 ? 0 : hoveredSlot) + step + slotCount) % slotCount;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            // Escape is the way out of a screen, not a step back through it: whatever is unfolded or drilled into,
            // the player means the wheel. Right-click is what walks back one level.
            close();
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
        WheelRing ring = ringAt(hoveredRing);
        MenuNode selected = ring == null ? null : ring.childAt(hoveredSlot);
        if (selected == null) {
            close();
            return;
        }

        if (selected.isCategory()) {
            if (isInlineSubmenu(selected)) {
                // Already open by the time it is chosen, nearly always - pointing at it is what opens it. Choosing
                // it is then how the player says to keep the wheel up while they reach for one of its entries.
                expand(ring, selected);
                sticky = true;
                return;
            }
            enterMenu(ring, selected);
            return;
        }

        lastChosen = selected;
        if (selected.keepOpen) {
            sticky = true;
            ActionExecutors.enqueue(selected.action);
            return;
        }

        // Close first, then queue: the action runs on the next tick with the game focused again.
        close();
        ActionExecutors.enqueue(selected.action);
    }

    /**
     * Drills into a replacing submenu, which takes over the whole wheel.
     *
     * <p>
     * The rings between the middle and the chosen entry go onto the path too: they are menus the player walked
     * through to get here, and going back has to walk back out of them in the order they were opened rather than
     * jumping to the root.
     */
    private void enterMenu(WheelRing from, MenuNode selected) {
        Deque<MenuNode> opened = new ArrayDeque<>();
        for (WheelRing ring = from; ring != null && ring.level > 0; ring = ringAt(ring.parent)) {
            opened.push(ring.menu);
        }
        for (MenuNode menu : opened) {
            path.push(menu);
        }
        path.push(selected);

        // Whatever was unfolded belonged to the menu that has just left the screen.
        forgetExpanded();
        sticky = true;
        // A submenu is a new wheel arriving; one that appeared fully drawn while its neighbours animated would
        // look like something went wrong rather than like a choice.
        animators = new IdentityHashMap<>();
        // A submenu opens with nothing chosen, unless the cursor is not what chooses - then it opens on the
        // first entry, because there would otherwise be no way to choose anything at all.
        hoveredRing = 0;
        hoveredSlot = RadialMenuConfig.scrollToSelect ? 0 : RadialGeometry.NO_SLOT;
    }

    /**
     * Back one step: folds away the branch under the cursor, leaves the submenu the wheel is inside, or closes.
     *
     * <p>
     * The unfolded branch first, because it is the most recent thing the player opened and the one they are looking
     * at. Either the entry it hangs off or any of its own sectors will do - both read as "this ring", and demanding
     * the entry would mean aiming back at it through the ring that covers it.
     */
    private void goBackOrClose() {
        MenuNode pointed = pointedNode();
        if (pointed != null && expanded.contains(pointed)) {
            collapse(pointed);
            return;
        }
        WheelRing ring = ringAt(pointerRing);
        if (ring != null && ring.level > 0) {
            collapse(ring.menu);
            return;
        }

        if (path.size() > 1) {
            path.pop();
            forgetExpanded();
            hoveredRing = 0;
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
