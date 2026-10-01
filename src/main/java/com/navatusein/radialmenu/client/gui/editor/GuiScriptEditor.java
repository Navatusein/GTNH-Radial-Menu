package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatAllowedCharacters;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiScreen;
import com.navatusein.radialmenu.core.script.LuaSyntax;
import com.navatusein.radialmenu.core.script.ScriptSnippets;
import com.navatusein.radialmenu.core.text.TextBuffer;

/**
 * A code editor for the Lua a script entry carries.
 *
 * <p>
 * Written rather than borrowed because there is nothing to borrow: {@code GuiTextField} is one line and draws its text
 * in a single call in a single colour, so neither multiple lines nor a second colour can be had from it. All this
 * screen
 * owns is pixels - every rule about where the caret lands lives in {@link TextBuffer} and every decision about colour
 * in
 * {@link LuaSyntax}, both of them in {@code core} where a test can ask.
 *
 * <p>
 * Three things here are less obvious than they look.
 *
 * <p>
 * <b>One function measures, and the same one draws.</b> 1.7.10's font is not monospaced, so a caret placed by counting
 * characters drifts from the glyphs within a line of code. {@link #widthTo} is the only thing that converts a column to
 * an x, and the text is drawn by walking the same widths.
 *
 * <p>
 * <b>The section sign cannot be drawn.</b> The font renderer reads it as the start of a formatting code and swallows it
 * along with the character after it - and {@code getCharWidth} returns -1 for it, so it does not even have a width. A
 * script that sends a coloured chat line contains them, so for drawing only it is swapped for a currency sign: one
 * character for one character, which keeps every column where it was.
 *
 * <p>
 * <b>Scrolling is by whole lines.</b> A text row is a line, so there is nothing to gain from stopping between two of
 * them - and the caret arithmetic stays in whole rows, which is the half of this that is easy to get wrong.
 */
public class GuiScriptEditor extends UiScreen {

    public interface Result {

        void onScriptEdited(String text);
    }

    /** Line pitch: the font's eight pixels plus enough that descenders do not touch the row below. */
    private static final int LINE_HEIGHT = 11;

    private static final int TEXT_PAD = 3;
    private static final int GUTTER_PAD = 4;
    private static final int SCROLL_ROWS = 3;

    private static final int ID_SNIPPETS = 50;
    private static final int SNIPPET_WIDTH = 96;
    private static final int SNIPPET_ROW = 10;
    private static final int SNIPPET_PAD = 3;

    /** The calls the list offers. Defined in core, beside the rest of what a script can say. */
    private static final List<ScriptSnippets.Snippet> SNIPPETS = ScriptSnippets.all();

    /** Kept between openings rather than per screen: whoever closed it did not want it back on the next script. */
    private static boolean snippetsOpen = true;

    private int snippetScroll;
    private static final String INDENT = "  ";

    /** What the font renderer reads as the start of a formatting code, and the stand-in drawn in its place. */
    private static final char SECTION_SIGN = (char) 0xA7;
    private static final char CURRENCY_SIGN = (char) 0xA4;

    private final String titleKey;
    private final Result result;
    private final TextBuffer buffer;

    /** The last error this entry's script produced, and the line it names. Null and -1 when there is none. */
    private final String errorText;
    private final int errorLine;

    private List<List<LuaSyntax.Token>> tokens;

    private int topLine;
    private int leftPixel;
    private int blinkTicks;
    private boolean dragging;

    public GuiScriptEditor(String titleKey, String source, String errorText, Result result) {
        this.titleKey = titleKey;
        this.result = result;
        this.buffer = new TextBuffer(source);
        this.errorText = errorText;
        this.errorLine = lineOf(errorText);
        retokenize();
    }

    /**
     * The line a Lua error names, from the {@code script:3: ...} it starts with.
     *
     * <p>
     * Worth the few lines: the message is already in hand when the editor opens, and a red line in the gutter is the
     * difference between reading an error and hunting for it.
     */
    private static int lineOf(String error) {
        if (error == null) {
            return -1;
        }
        int first = error.indexOf(':');
        int second = error.indexOf(':', first + 1);
        if (first < 0 || second < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(
                error.substring(first + 1, second)
                    .trim())
                - 1;
        } catch (NumberFormatException ignored) {
            // Some other message shape. No line to point at, which is not an error in itself.
            return -1;
        }
    }

    @Override
    protected String titleKey() {
        return titleKey;
    }

    @Override
    protected int panelWidth() {
        // Wider than the other dialogs on purpose: a line of Lua is longer than a label and a control.
        return 460;
    }

    /** The status line under the box, which never scrolls. */
    @Override
    protected int footerHeight() {
        return 10;
    }

    @Override
    protected void buildControls() {
        // Nothing scrolls in the framework's sense - this screen scrolls its own text by whole lines.
        setContentHeight(0);
        addBottomBar("gui.done", null, "gui.cancel");

        GuiButton toggle = new GuiButton(
            ID_SNIPPETS,
            panelRight - SNIPPET_WIDTH - Ui.PAD,
            Ui.bottomBarY(this.height),
            SNIPPET_WIDTH,
            Ui.ROW,
            I18n.format(snippetsOpen ? "radialmenu.script.snippets.hide" : "radialmenu.script.snippets.show"));
        this.buttonList.add(toggle);
        markFooter(ID_SNIPPETS);
        tooltip(ID_SNIPPETS, I18n.format("radialmenu.script.snippets.tip"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_PRIMARY) {
            result.onScriptEdited(buffer.text());
            GuiStack.pop();
            return;
        }
        if (button.id == ID_SECONDARY) {
            onCancel();
            return;
        }
        if (button.id == ID_SNIPPETS) {
            snippetsOpen = !snippetsOpen;
            // The code area changes width, so what was in view may not be any more.
            afterMove();
            requestRebuild();
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        blinkTicks++;
    }

    private int boxTop() {
        return viewportTop();
    }

    private int boxBottom() {
        return viewportBottom();
    }

    /** Right edge of the code box: the panel, less the snippet column when it is open. */
    private int boxRight() {
        return snippetsOpen ? viewportRight() - SNIPPET_WIDTH - Ui.GAP : viewportRight();
    }

    private int snippetLeft() {
        return viewportRight() - SNIPPET_WIDTH;
    }

    private int snippetRows() {
        return Math.max(1, (boxBottom() - boxTop() - 2 * SNIPPET_PAD) / SNIPPET_ROW);
    }

    private int gutterWidth() {
        return this.fontRendererObj.getStringWidth(String.valueOf(Math.max(99, buffer.lineCount()))) + 2 * GUTTER_PAD;
    }

    private int textLeft() {
        return viewportLeft() + 1 + gutterWidth() + 1 + TEXT_PAD;
    }

    private int textRight() {
        return boxRight() - 1 - TEXT_PAD;
    }

    private int textTop() {
        return boxTop() + 1 + TEXT_PAD;
    }

    private int visibleRows() {
        return Math.max(1, (boxBottom() - 1 - TEXT_PAD - textTop()) / LINE_HEIGHT);
    }

    /**
     * The line as it can be drawn.
     *
     * <p>
     * Only the section sign changes, and only because the font renderer would eat it and the character after it. One
     * character for one, so a column still means the same place.
     */
    private static String display(String line) {
        return line.indexOf(SECTION_SIGN) < 0 ? line : line.replace(SECTION_SIGN, CURRENCY_SIGN);
    }

    /** Pixels from the start of a line to a column. The only converter, used by the caret and by the text alike. */
    private int widthTo(int line, int column) {
        String text = display(buffer.line(line));
        int end = Math.max(0, Math.min(column, text.length()));
        return this.fontRendererObj.getStringWidth(text.substring(0, end));
    }

    /** The column a pixel lands on, rounded to the nearer edge of the character under it. */
    private int columnAt(int line, int x) {
        String text = display(buffer.line(line));
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            int charWidth = this.fontRendererObj.getCharWidth(text.charAt(i));
            if (x < width + charWidth / 2) {
                return i;
            }
            width += charWidth;
        }
        return text.length();
    }

    private void retokenize() {
        tokens = LuaSyntax.tokenize(buffer.lines());
    }

    private void afterEdit() {
        retokenize();
        afterMove();
    }

    private void afterMove() {
        blinkTicks = 0;
        int rows = visibleRows();
        if (buffer.caretLine() < topLine) {
            topLine = buffer.caretLine();
        } else if (buffer.caretLine() >= topLine + rows) {
            topLine = buffer.caretLine() - rows + 1;
        }
        clampScroll();

        int caretX = widthTo(buffer.caretLine(), buffer.caretColumn());
        int width = textRight() - textLeft();
        if (caretX - leftPixel < 0) {
            leftPixel = caretX;
        } else if (caretX - leftPixel > width - 2) {
            leftPixel = caretX - width + 2;
        }
        leftPixel = Math.max(0, leftPixel);
    }

    private void clampScroll() {
        topLine = Math.max(0, Math.min(topLine, Math.max(0, buffer.lineCount() - visibleRows())));
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        int left = viewportLeft();
        int right = boxRight();
        int top = boxTop();
        int bottom = boxBottom();
        if (snippetsOpen) {
            drawSnippets(mouseX, mouseY);
        }

        Ui.list(left, top, right, bottom);
        clampScroll();

        int gutterRight = left + 1 + gutterWidth();
        Gui.drawRect(left + 1, top + 1, gutterRight, bottom - 1, 0x30000000);
        Gui.drawRect(gutterRight, top + 1, gutterRight + 1, bottom - 1, 0x30FFFFFF);

        int rows = visibleRows();
        int textTop = textTop();

        // Behind everything, and across the whole box including the gutter: a line marker that stopped at the text
        // would read as a selection rather than as "you are here".
        for (int row = 0; row < rows; row++) {
            int line = topLine + row;
            if (line >= buffer.lineCount()) {
                break;
            }
            int y = textTop + row * LINE_HEIGHT;
            if (line == errorLine) {
                Gui.drawRect(left + 1, y - 1, right - 1, y + LINE_HEIGHT - 1, Ui.CODE_ERROR_LINE);
            } else if (line == buffer.caretLine() && !buffer.hasSelection()) {
                Gui.drawRect(left + 1, y - 1, right - 1, y + LINE_HEIGHT - 1, Ui.CODE_CURRENT_LINE);
            }

            String number = String.valueOf(line + 1);
            this.fontRendererObj.drawString(
                number,
                gutterRight - GUTTER_PAD - this.fontRendererObj.getStringWidth(number),
                y,
                line == errorLine ? Ui.TEXT_ERROR : line == buffer.caretLine() ? Ui.TEXT_MUTED : Ui.CODE_GUTTER);
        }

        // The text is the only thing that moves sideways, so it is the only thing clipped - the gutter has to stay
        // readable and the frame has to keep its edges.
        Ui.beginClip(gutterRight + 1, top + 1, right - 1, bottom - 1);
        for (int row = 0; row < rows; row++) {
            int line = topLine + row;
            if (line >= buffer.lineCount()) {
                break;
            }
            drawSelection(line, textTop + row * LINE_HEIGHT);
            drawLine(line, textTop + row * LINE_HEIGHT);
        }
        drawCaret(textTop, rows);
        Ui.endClip();

        drawStatus(bottom + Ui.GAP);
    }

    private void drawLine(int line, int y) {
        String text = display(buffer.line(line));
        List<LuaSyntax.Token> lineTokens = line < tokens.size() ? tokens.get(line) : null;
        int x = textLeft() - leftPixel;

        if (lineTokens == null || lineTokens.isEmpty()) {
            if (!text.isEmpty()) {
                this.fontRendererObj.drawString(text, x, y, Ui.CODE_PLAIN);
            }
            return;
        }

        for (LuaSyntax.Token token : lineTokens) {
            int end = Math.min(token.end, text.length());
            if (token.start >= end) {
                continue;
            }
            String part = text.substring(token.start, end);
            // No shadow: a shadow under nine-pixel text smears the difference between a dot and a comma, and the
            // widths this walks are the unshadowed ones.
            this.fontRendererObj.drawString(part, x, y, colourOf(token.kind));
            x += this.fontRendererObj.getStringWidth(part);
        }
    }

    private static int colourOf(LuaSyntax.Kind kind) {
        switch (kind) {
            case KEYWORD:
                return Ui.CODE_KEYWORD;
            case API:
                return Ui.CODE_API;
            case STRING:
                return Ui.CODE_STRING;
            case NUMBER:
                return Ui.CODE_NUMBER;
            case COMMENT:
                return Ui.CODE_COMMENT;
            case OPERATOR:
                return Ui.CODE_OPERATOR;
            default:
                return Ui.CODE_PLAIN;
        }
    }

    private void drawSelection(int line, int y) {
        if (!buffer.hasSelection()) {
            return;
        }
        TextBuffer.Span span = buffer.selection();
        if (line < span.startLine || line > span.endLine) {
            return;
        }

        int from = line == span.startLine ? span.startColumn : 0;
        int to = line == span.endLine ? span.endColumn
            : buffer.line(line)
                .length();
        int x1 = textLeft() + widthTo(line, from) - leftPixel;
        int x2 = textLeft() + widthTo(line, to) - leftPixel;
        // A line selected to its end includes the break after it, and showing that as a couple of pixels past the
        // last character is how the selection reads as covering whole lines.
        if (line < span.endLine) {
            x2 += 3;
        }
        Gui.drawRect(x1, y - 1, Math.max(x1 + 1, x2), y + LINE_HEIGHT - 1, Ui.CODE_SELECTION);
    }

    private void drawCaret(int textTop, int rows) {
        if (blinkTicks / 6 % 2 != 0) {
            return;
        }
        int row = buffer.caretLine() - topLine;
        if (row < 0 || row >= rows) {
            return;
        }
        int x = textLeft() + widthTo(buffer.caretLine(), buffer.caretColumn()) - leftPixel;
        int y = textTop + row * LINE_HEIGHT;
        Gui.drawRect(x, y - 1, x + 1, y + LINE_HEIGHT - 2, Ui.TEXT);
    }

    /**
     * Where the caret is, how big the script is, and what went wrong last time.
     *
     * <p>
     * The error takes the whole line when there is one: a message naming a line number is worth more than a position
     * the player can see from the caret.
     */
    private void drawStatus(int y) {
        if (errorText != null) {
            this.fontRendererObj
                .drawString(Ui.fit(errorText, viewportRight() - viewportLeft()), viewportLeft(), y, Ui.TEXT_ERROR);
            return;
        }
        String position = I18n
            .format("radialmenu.script.position", buffer.caretLine() + 1, buffer.caretColumn() + 1, buffer.lineCount());
        this.fontRendererObj.drawString(position, viewportLeft(), y, Ui.TEXT_MUTED);
    }

    /** The list of calls, as rows that write themselves into the script. */
    private void drawSnippets(int mouseX, int mouseY) {
        int left = snippetLeft();
        int right = viewportRight();
        int top = boxTop();
        int bottom = boxBottom();

        Ui.list(left, top, right, bottom);
        clampSnippetScroll();

        int hovered = snippetAt(mouseX, mouseY);
        int rows = snippetRows();
        for (int row = 0; row < rows; row++) {
            int index = snippetScroll + row;
            if (index >= SNIPPETS.size()) {
                break;
            }
            int y = top + SNIPPET_PAD + row * SNIPPET_ROW;
            if (index == hovered) {
                Gui.drawRect(left + 1, y - 1, right - 1, y + SNIPPET_ROW - 1, Ui.ROW_HOVER);
            }
            this.fontRendererObj.drawString(
                Ui.fit(SNIPPETS.get(index).label, right - left - 2 * SNIPPET_PAD - 2),
                left + SNIPPET_PAD,
                y,
                index == hovered ? Ui.TEXT : Ui.CODE_API);
        }

        if (SNIPPETS.size() > rows) {
            // A list that scrolls says so: the column is too narrow for a scrollbar, so the marker is a thin track.
            int trackTop = top + SNIPPET_PAD;
            int trackHeight = rows * SNIPPET_ROW;
            int thumb = Math.max(8, trackHeight * rows / SNIPPETS.size());
            int offset = (trackHeight - thumb) * snippetScroll / Math.max(1, SNIPPETS.size() - rows);
            Gui.drawRect(right - 3, trackTop, right - 2, trackTop + trackHeight, 0x30FFFFFF);
            Gui.drawRect(right - 3, trackTop + offset, right - 2, trackTop + offset + thumb, 0x90FFFFFF);
        }
    }

    /**
     * The whole call under the cursor, since the row only has space for its name.
     *
     * <p>
     * Drawn as an overlay rather than inside the list, so it is not clipped by the column it belongs to.
     */
    @Override
    protected void drawOverlay(int mouseX, int mouseY, float partialTicks) {
        int index = snippetAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (String line : SNIPPETS.get(index).text.replace(String.valueOf(ScriptSnippets.CARET), "")
            .split("\n")) {
            lines.add(line);
        }
        drawHoveringText(lines, mouseX, mouseY, this.fontRendererObj);
    }

    private void clampSnippetScroll() {
        snippetScroll = Math.max(0, Math.min(snippetScroll, Math.max(0, SNIPPETS.size() - snippetRows())));
    }

    /** Which row the cursor is on, or -1 if it is not over the list at all. */
    private int snippetAt(int mouseX, int mouseY) {
        if (!snippetsOpen || mouseX < snippetLeft() || mouseX >= viewportRight()) {
            return -1;
        }
        int row = (mouseY - boxTop() - SNIPPET_PAD) / SNIPPET_ROW;
        if (row < 0 || row >= snippetRows()) {
            return -1;
        }
        int index = snippetScroll + row;
        return index < SNIPPETS.size() ? index : -1;
    }

    /**
     * Writes a snippet in at the caret, and puts the caret where the argument goes.
     *
     * <p>
     * Stepping back one character at a time rather than arithmetic on the text: the buffer already knows what a
     * character is and where a line ends, and a snippet with a line break in it would otherwise need the same knowledge
     * written again here.
     */
    private void insertSnippet(ScriptSnippets.Snippet snippet) {
        String text = snippet.text;
        int caret = text.indexOf(ScriptSnippets.CARET);
        if (caret >= 0) {
            text = text.replace(String.valueOf(ScriptSnippets.CARET), "");
        }

        buffer.insert(text);
        if (caret >= 0) {
            for (int i = text.length(); i > caret; i--) {
                buffer.moveLeft(false);
            }
        }
        afterEdit();
    }

    private boolean insideText(int mouseX, int mouseY) {
        return mouseX >= viewportLeft() && mouseX < boxRight() && mouseY >= boxTop() && mouseY < boxBottom();
    }

    private void placeCaret(int mouseX, int mouseY, boolean select) {
        int row = (mouseY - textTop() + 1) / LINE_HEIGHT;
        int line = Math.max(0, Math.min(buffer.lineCount() - 1, topLine + Math.max(0, row)));
        buffer.moveTo(line, columnAt(line, mouseX - textLeft() + leftPixel), select);
        afterMove();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            int snippet = snippetAt(mouseX, mouseY);
            if (snippet >= 0) {
                insertSnippet(SNIPPETS.get(snippet));
                return;
            }
        }
        if (mouseButton == 0 && insideText(mouseX, mouseY)) {
            dragging = true;
            placeCaret(mouseX, mouseY, isShiftKeyDown());
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceClick) {
        if (dragging && mouseButton == 0) {
            // Always extending, never starting a new selection: the press set the anchor and the drag is the rest.
            placeCaret(mouseX, mouseY, true);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, mouseButton, timeSinceClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (which == 0) {
            dragging = false;
        }
        super.mouseMovedOrUp(mouseX, mouseY, which);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }

        // Whichever list the cursor is over is the one that moves. Scrolling the code while reading the snippets is
        // the sort of thing that is only noticed as "the wheel does not work here".
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        if (snippetsOpen && mouseX >= snippetLeft()) {
            snippetScroll += wheel > 0 ? -SCROLL_ROWS : SCROLL_ROWS;
            clampSnippetScroll();
            return;
        }

        topLine += wheel > 0 ? -SCROLL_ROWS : SCROLL_ROWS;
        clampScroll();
    }

    @Override
    protected boolean handleKey(char typedChar, int keyCode) {
        boolean shift = isShiftKeyDown();
        boolean control = isCtrlKeyDown();

        if (control && handleShortcut(keyCode)) {
            return true;
        }

        switch (keyCode) {
            case Keyboard.KEY_LEFT:
                if (control) {
                    buffer.moveWordLeft(shift);
                } else {
                    buffer.moveLeft(shift);
                }
                afterMove();
                return true;
            case Keyboard.KEY_RIGHT:
                if (control) {
                    buffer.moveWordRight(shift);
                } else {
                    buffer.moveRight(shift);
                }
                afterMove();
                return true;
            case Keyboard.KEY_UP:
                buffer.moveUp(1, shift);
                afterMove();
                return true;
            case Keyboard.KEY_DOWN:
                buffer.moveDown(1, shift);
                afterMove();
                return true;
            case Keyboard.KEY_PRIOR:
                buffer.moveUp(visibleRows(), shift);
                afterMove();
                return true;
            case Keyboard.KEY_NEXT:
                buffer.moveDown(visibleRows(), shift);
                afterMove();
                return true;
            case Keyboard.KEY_HOME:
                if (control) {
                    buffer.moveToStart(shift);
                } else {
                    buffer.moveToLineStart(shift);
                }
                afterMove();
                return true;
            case Keyboard.KEY_END:
                if (control) {
                    buffer.moveToEnd(shift);
                } else {
                    buffer.moveToLineEnd(shift);
                }
                afterMove();
                return true;
            case Keyboard.KEY_BACK:
                buffer.backspace();
                afterEdit();
                return true;
            case Keyboard.KEY_DELETE:
                buffer.delete();
                afterEdit();
                return true;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
                buffer.newline(INDENT, opensBlock(buffer.line(buffer.caretLine())) ? 1 : 0);
                afterEdit();
                return true;
            case Keyboard.KEY_TAB:
                buffer.indent(INDENT, shift);
                afterEdit();
                return true;
            default:
                break;
        }

        if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
            buffer.insert(String.valueOf(typedChar));
            afterEdit();
            return true;
        }
        // Everything else is swallowed rather than passed on, or a stray key would reach the buttons behind the text.
        return true;
    }

    private boolean handleShortcut(int keyCode) {
        switch (keyCode) {
            case Keyboard.KEY_A:
                buffer.selectAll();
                afterMove();
                return true;
            case Keyboard.KEY_C:
                if (buffer.hasSelection()) {
                    setClipboardString(buffer.selectedText());
                }
                return true;
            case Keyboard.KEY_X:
                if (buffer.hasSelection()) {
                    setClipboardString(buffer.selectedText());
                    buffer.deleteSelection();
                    afterEdit();
                }
                return true;
            case Keyboard.KEY_V:
                String pasted = filter(getClipboardString());
                if (!pasted.isEmpty()) {
                    buffer.insert(pasted);
                    afterEdit();
                }
                return true;
            default:
                return false;
        }
    }

    /**
     * Keeps what can be typed, plus line breaks.
     *
     * <p>
     * Pasted text is where a tab or a stray control character arrives, and a tab the font cannot draw would be a
     * character the caret counts and the eye cannot find. Tabs become an indent instead.
     */
    private static String filter(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder kept = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                kept.append('\n');
            } else if (c == '\t') {
                kept.append(INDENT);
            } else if (ChatAllowedCharacters.isAllowedCharacter(c)) {
                kept.append(c);
            }
        }
        return kept.toString();
    }

    /**
     * Whether a line leaves a block open, so the next one starts a level deeper.
     *
     * <p>
     * Read off the end of the line rather than parsed: this decides an indent, and an indent that is occasionally one
     * level out costs a keypress, where a parser costs a dependency on the code being valid while it is being typed.
     */
    private static boolean opensBlock(String line) {
        String trimmed = line.trim();
        if (trimmed.endsWith("then") || trimmed.endsWith("do")
            || trimmed.endsWith("else")
            || trimmed.endsWith("{")
            || trimmed.endsWith("(")
            || trimmed.endsWith("repeat")) {
            return true;
        }
        return trimmed.startsWith("function") || trimmed.contains("function(") || trimmed.endsWith("function()");
    }
}
