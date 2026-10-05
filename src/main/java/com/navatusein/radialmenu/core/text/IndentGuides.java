package com.navatusein.radialmenu.core.text;

import java.util.List;

/**
 * How deep each line of a script sits, for the vertical rules an editor draws down a block.
 *
 * <p>
 * The answer for a line with text on it is its leading spaces and nothing more. The reason this is a class is the
 * line with nothing on it: an empty line has no indentation of its own, and a rule that stopped at every one would be
 * cut into pieces by exactly the blank lines that make a script readable. So a blank line borrows the deeper of its
 * two neighbours - the deeper, because a blank line straight after {@code if x then} or straight before {@code end}
 * is still inside the block, and the shallower neighbour is the one that says otherwise.
 */
public final class IndentGuides {

    private IndentGuides() {}

    /**
     * Leading spaces per line, with blank lines filled in from the lines around them.
     *
     * <p>
     * Spaces only: the editor turns a pasted tab into an indent on the way in, so a tab never reaches the buffer.
     */
    public static int[] depths(List<String> lines) {
        int count = lines.size();
        int[] depths = new int[count];
        boolean[] blank = new boolean[count];

        for (int i = 0; i < count; i++) {
            String line = lines.get(i);
            int spaces = 0;
            while (line != null && spaces < line.length() && line.charAt(spaces) == ' ') {
                spaces++;
            }
            blank[i] = line == null || spaces == line.length();
            depths[i] = blank[i] ? 0 : spaces;
        }

        // Each run of blank lines is settled at once, from the line above it and the line below it. A run at either
        // end of the script has only one neighbour, and the missing one counts as the margin.
        int above = 0;
        int i = 0;
        while (i < count) {
            if (!blank[i]) {
                above = depths[i];
                i++;
                continue;
            }
            int end = i;
            while (end < count && blank[end]) {
                end++;
            }
            int below = end < count ? depths[end] : 0;
            int depth = Math.max(above, below);
            for (int line = i; line < end; line++) {
                depths[line] = depth;
            }
            i = end;
        }
        return depths;
    }

    /**
     * How many rules a line of this depth has to its left: one per indent it is inside, the first at the margin.
     *
     * <p>
     * A partial indent rounds up - a line three spaces in under a two-space indent is inside two levels, and its
     * second rule sits at column two, still left of its text.
     */
    public static int count(int depth, int indentWidth) {
        if (depth <= 0 || indentWidth <= 0) {
            return 0;
        }
        return (depth + indentWidth - 1) / indentWidth;
    }
}
