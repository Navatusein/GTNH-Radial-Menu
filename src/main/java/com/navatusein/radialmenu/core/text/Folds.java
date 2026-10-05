package com.navatusein.radialmenu.core.text;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

/**
 * Which blocks of a script are folded away, and what is left to draw.
 *
 * <p>
 * A block is found by indentation, the same reading {@link IndentGuides} gives the rules: a line can be folded when
 * the lines after it sit deeper, and what folds is every line down to the first that does not. No parser - the editor
 * is looking at code while it is being typed, which is exactly when it does not parse, and a fold that came and went
 * with every unbalanced {@code end} would be worse than one that is occasionally a line generous. It also means the
 * closing {@code end} or brace, which sits level with the opener, stays on screen under it.
 *
 * <p>
 * All that is remembered is the set of folded opening lines. Where each one ends is worked out when asked, so an edit
 * inside a block never leaves a stale extent behind - only the line numbers themselves have to follow the text, which
 * is what {@link #edited} is for.
 */
public final class Folds {

    private final TreeSet<Integer> folded = new TreeSet<>();

    /**
     * The last line of the block a line opens, or -1 when it opens none.
     *
     * @param depths what {@link IndentGuides#depths} made of the same lines, blank lines filled in - which is what
     *               lets a blank line inside a block fold with it instead of ending it
     */
    public static int endOf(List<String> lines, int[] depths, int line) {
        if (line < 0 || line >= lines.size() || line >= depths.length || isBlank(lines.get(line))) {
            return -1;
        }
        int end = line;
        while (end + 1 < depths.length && depths[end + 1] > depths[line]) {
            end++;
        }
        return end > line ? end : -1;
    }

    private static boolean isBlank(String line) {
        return line == null || line.trim()
            .isEmpty();
    }

    public boolean isEmpty() {
        return folded.isEmpty();
    }

    public boolean isFolded(int line) {
        return folded.contains(Integer.valueOf(line));
    }

    /** Folds the block a line opens, or opens it again. Returns false for a line with nothing under it. */
    public boolean toggle(List<String> lines, int[] depths, int line) {
        if (folded.remove(Integer.valueOf(line))) {
            return true;
        }
        if (endOf(lines, depths, line) < 0) {
            return false;
        }
        folded.add(Integer.valueOf(line));
        return true;
    }

    /**
     * The folded line a line is hidden under, or -1 when it is on show.
     *
     * <p>
     * The outermost one, where folds are nested: that is the line actually on screen, and so the only one a caret
     * pushed out of the block can be put on.
     */
    public int hiddenUnder(List<String> lines, int[] depths, int line) {
        for (Integer header : folded) {
            if (header.intValue() >= line) {
                break;
            }
            if (line <= endOf(lines, depths, header.intValue())) {
                return header.intValue();
            }
        }
        return -1;
    }

    /**
     * Opens every fold a line is hidden under, so the line can be seen. Returns whether anything opened.
     *
     * <p>
     * For the caret after an edit: typing must never happen somewhere the player cannot see it.
     */
    public boolean reveal(List<String> lines, int[] depths, int line) {
        boolean changed = false;
        for (Iterator<Integer> it = folded.iterator(); it.hasNext();) {
            int header = it.next()
                .intValue();
            if (header < line && line <= endOf(lines, depths, header)) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Carries the folds across an edit.
     *
     * <p>
     * The edit replaced lines {@code from} to {@code to} - the selection, or the caret's own line - and left the
     * script {@code delta} lines longer. A fold below it keeps its block and moves by that much. One that opened
     * inside the replaced span is forgotten when lines came or went: its opening line was deleted, or joined to
     * another, or pushed down by a line break in front of it, and guessing which leaves a fold sitting on a line the
     * player never folded. Forgetting costs them one click; a wrong fold hides code they did not ask to lose sight of.
     *
     * <p>
     * Whatever is left is then checked against the new text, since an edit that never changed the line count can
     * still have taken the block out from under a fold - outdenting it, say.
     *
     * @param lines  the text after the edit
     * @param depths its depths
     */
    public void edited(int from, int to, int delta, List<String> lines, int[] depths) {
        if (folded.isEmpty()) {
            return;
        }
        List<Integer> kept = new ArrayList<>();
        for (Integer header : folded) {
            int line = header.intValue();
            if (delta != 0 && line >= from && line <= to) {
                continue;
            }
            if (line > to) {
                line += delta;
            }
            if (endOf(lines, depths, line) >= 0) {
                kept.add(Integer.valueOf(line));
            }
        }
        folded.clear();
        folded.addAll(kept);
    }

    /** The lines left on show, in order. A folded line is one of them; the block under it is not. */
    public int[] visibleLines(List<String> lines, int[] depths) {
        int count = lines.size();
        int[] visible = new int[count];
        int size = 0;
        int line = 0;
        while (line < count) {
            visible[size++] = line;
            int end = isFolded(line) ? endOf(lines, depths, line) : -1;
            line = end < 0 ? line + 1 : end + 1;
        }
        int[] trimmed = new int[size];
        System.arraycopy(visible, 0, trimmed, 0, size);
        return trimmed;
    }
}
