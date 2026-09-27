package com.navatusein.radialmenu.client.gui.editor;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.core.model.IconSpec;

/**
 * Picks a colour.
 *
 * <p>
 * Gradients are drawn as grids of small solid rectangles rather than as vertex-interpolated quads. That is a few
 * hundred draws per frame, which a GUI can afford, and it avoids depending on the tessellator's colour path - which
 * is exactly what made the wheel render invisible earlier.
 */
public class GuiColorPicker extends GuiScreen {

    public interface Result {

        void onColorPicked(String hex);
    }

    private static final int ID_DONE = 1;
    private static final int ID_CANCEL = 2;

    /** A spread of hues plus a greyscale ramp - the common cases, one click away. */
    private static final int[] PRESETS = { 0xFFFFFF, 0xC0C0C0, 0x808080, 0x404040, 0x000000, 0xFF5555, 0xFF8800,
        0xFFD700, 0x7FFF00, 0x2ECC71, 0x1ABC9C, 0x4A90D9, 0x3355FF, 0x8E44AD, 0xFF69B4, 0x8B4513 };

    private static final int SQUARE_SIZE = 128;
    private static final int HUE_WIDTH = 16;
    private static final int SWATCH = 16;
    private static final int STEPS = 32;

    private final Result result;

    private float hue;
    private float saturation = 1f;
    private float value = 1f;

    private GuiTextField hexField;

    private int squareLeft;
    private int squareTop;
    private int hueLeft;
    private int presetsTop;

    /** Which control the current drag started on, so dragging off a control keeps steering that control. */
    private int dragging;

    public GuiColorPicker(String initialHex, Result result) {
        this.result = result;
        setRgb(IconSpec.parseRgb(initialHex, 0xFFFFFF));
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        squareLeft = this.width / 2 - 110;
        squareTop = 46;
        hueLeft = squareLeft + SQUARE_SIZE + 10;
        presetsTop = squareTop + SQUARE_SIZE + 12;

        hexField = new GuiTextField(this.fontRendererObj, hueLeft + HUE_WIDTH + 12, squareTop + 44, 66, 16);
        hexField.setMaxStringLength(7);
        hexField.setText(toHex());

        this.buttonList.clear();
        this.buttonList
            .add(new GuiButton(ID_DONE, this.width / 2 - 100, this.height - 28, 98, 20, I18n.format("gui.done")));
        this.buttonList
            .add(new GuiButton(ID_CANCEL, this.width / 2 + 2, this.height - 28, 98, 20, I18n.format("gui.cancel")));
    }

    // -- colour conversion -------------------------------------------------------------------------------------

    /** Hand-rolled rather than via AWT, so nothing here depends on a graphics environment being available. */
    private static int hsvToRgb(float h, float s, float v) {
        float r;
        float g;
        float b;
        int sector = (int) Math.floor(h * 6f) % 6;
        float f = h * 6f - (float) Math.floor(h * 6f);
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);

        switch (sector) {
            case 0:
                r = v;
                g = t;
                b = p;
                break;
            case 1:
                r = q;
                g = v;
                b = p;
                break;
            case 2:
                r = p;
                g = v;
                b = t;
                break;
            case 3:
                r = p;
                g = q;
                b = v;
                break;
            case 4:
                r = t;
                g = p;
                b = v;
                break;
            default:
                r = v;
                g = p;
                b = q;
                break;
        }
        return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }

    private void setRgb(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        value = max;
        saturation = max == 0f ? 0f : delta / max;

        if (delta == 0f) {
            hue = 0f;
        } else if (max == r) {
            hue = ((g - b) / delta % 6f) / 6f;
        } else if (max == g) {
            hue = ((b - r) / delta + 2f) / 6f;
        } else {
            hue = ((r - g) / delta + 4f) / 6f;
        }
        if (hue < 0f) {
            hue += 1f;
        }
    }

    private int currentRgb() {
        return hsvToRgb(hue, saturation, value);
    }

    private String toHex() {
        return String.format("#%06X", currentRgb());
    }

    // -- drawing -----------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("radialmenu.color.title"),
            this.width / 2,
            16,
            0xFFFFFF);

        drawSaturationValueSquare();
        drawHueStrip();
        drawPresets();

        // Preview, with a border so a white colour is still visible against the dark background.
        int previewLeft = hueLeft + HUE_WIDTH + 12;
        drawRect(previewLeft - 1, squareTop - 1, previewLeft + 67, squareTop + 40, 0xFF000000);
        drawRect(previewLeft, squareTop, previewLeft + 66, squareTop + 39, 0xFF000000 | currentRgb());

        hexField.drawTextBox();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSaturationValueSquare() {
        int step = SQUARE_SIZE / STEPS;
        for (int sx = 0; sx < STEPS; sx++) {
            for (int sy = 0; sy < STEPS; sy++) {
                float s = sx / (float) (STEPS - 1);
                float v = 1f - sy / (float) (STEPS - 1);
                int x = squareLeft + sx * step;
                int y = squareTop + sy * step;
                drawRect(x, y, x + step, y + step, 0xFF000000 | hsvToRgb(hue, s, v));
            }
        }
        drawCrosshair(
            squareLeft + Math.round(saturation * (SQUARE_SIZE - 1)),
            squareTop + Math.round((1f - value) * (SQUARE_SIZE - 1)));
    }

    private void drawHueStrip() {
        int step = Math.max(1, SQUARE_SIZE / 64);
        for (int i = 0; i * step < SQUARE_SIZE; i++) {
            int y = squareTop + i * step;
            drawRect(
                hueLeft,
                y,
                hueLeft + HUE_WIDTH,
                y + step,
                0xFF000000 | hsvToRgb(i * step / (float) SQUARE_SIZE, 1f, 1f));
        }
        int markerY = squareTop + Math.round(hue * (SQUARE_SIZE - 1));
        drawRect(hueLeft - 2, markerY - 1, hueLeft + HUE_WIDTH + 2, markerY + 1, 0xFFFFFFFF);
    }

    private void drawPresets() {
        for (int i = 0; i < PRESETS.length; i++) {
            int x = squareLeft + (i % 8) * (SWATCH + 2);
            int y = presetsTop + (i / 8) * (SWATCH + 2);
            drawRect(x - 1, y - 1, x + SWATCH + 1, y + SWATCH + 1, 0xFF000000);
            drawRect(x, y, x + SWATCH, y + SWATCH, 0xFF000000 | PRESETS[i]);
        }
    }

    /** Two thin bars rather than a filled square, so the colour underneath stays visible. */
    private void drawCrosshair(int x, int y) {
        int colour = value > 0.5f && saturation < 0.5f ? 0xFF000000 : 0xFFFFFFFF;
        drawRect(x - 4, y, x + 5, y + 1, colour);
        drawRect(x, y - 4, x + 1, y + 5, colour);
    }

    // -- input -------------------------------------------------------------------------------------------------

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        hexField.mouseClicked(mouseX, mouseY, button);

        if (button != 0) {
            return;
        }

        if (inSquare(mouseX, mouseY)) {
            dragging = 1;
            applySquare(mouseX, mouseY);
            return;
        }
        if (inHueStrip(mouseX, mouseY)) {
            dragging = 2;
            applyHue(mouseY);
            return;
        }

        for (int i = 0; i < PRESETS.length; i++) {
            int x = squareLeft + (i % 8) * (SWATCH + 2);
            int y = presetsTop + (i / 8) * (SWATCH + 2);
            if (mouseX >= x && mouseX < x + SWATCH && mouseY >= y && mouseY < y + SWATCH) {
                setRgb(PRESETS[i]);
                syncHexField();
                return;
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long heldTime) {
        if (dragging == 1) {
            applySquare(mouseX, mouseY);
        } else if (dragging == 2) {
            applyHue(mouseY);
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state != -1) {
            dragging = 0;
        }
    }

    private boolean inSquare(int x, int y) {
        return x >= squareLeft && x < squareLeft + SQUARE_SIZE && y >= squareTop && y < squareTop + SQUARE_SIZE;
    }

    private boolean inHueStrip(int x, int y) {
        return x >= hueLeft && x < hueLeft + HUE_WIDTH && y >= squareTop && y < squareTop + SQUARE_SIZE;
    }

    private void applySquare(int mouseX, int mouseY) {
        saturation = clamp01((mouseX - squareLeft) / (float) (SQUARE_SIZE - 1));
        value = 1f - clamp01((mouseY - squareTop) / (float) (SQUARE_SIZE - 1));
        syncHexField();
    }

    private void applyHue(int mouseY) {
        hue = clamp01((mouseY - squareTop) / (float) (SQUARE_SIZE - 1));
        syncHexField();
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private void syncHexField() {
        hexField.setText(toHex());
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            GuiStack.pop();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            confirm();
            return;
        }
        if (hexField.textboxKeyTyped(typedChar, keyCode)) {
            // Typing drives the sliders, but only once the text is a complete colour - otherwise every keystroke of a
            // half-typed value would drag the picker somewhere meaningless.
            String text = hexField.getText()
                .trim();
            if (text.length() == 7 || text.length() == 6) {
                setRgb(IconSpec.parseRgb(text, currentRgb()));
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_DONE) {
            confirm();
        } else if (button.id == ID_CANCEL) {
            GuiStack.pop();
        }
    }

    private void confirm() {
        result.onColorPicked(toHex());
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
