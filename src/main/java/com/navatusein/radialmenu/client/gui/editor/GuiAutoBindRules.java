package com.navatusein.radialmenu.client.gui.editor;

import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.client.profile.ProfileStorage;
import com.navatusein.radialmenu.core.model.Profile;
import com.navatusein.radialmenu.core.model.ProfileBinding;

/**
 * Edits one profile's auto-bind rules - the conditions that make it activate on its own.
 *
 * <p>
 * "Use current" fills the value from the world the player is in right now, which is the only reliable way to get a
 * server address exactly as the client will compare it later.
 */
public class GuiAutoBindRules extends GuiScreen {

    private static final int ID_ADD = 1;
    private static final int ID_DONE = 2;
    private static final int ID_USE_CURRENT = 3;
    private static final int ID_ROW_BASE = 100;
    private static final int ID_REMOVE_BASE = 200;

    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 40;

    private final String profileName;

    private Profile profile;

    private final ArrayList<GuiTextField> valueFields = new ArrayList<>();

    /**
     * Rebuilding the button list has to wait for the next frame.
     *
     * <p>
     * GuiScreen.mouseClicked walks buttonList by index and calls actionPerformed from inside that loop, so replacing
     * the list mid-click makes the loop run over the new buttons: the add button ends up clicked again and again,
     * adding rules until the process dies.
     */
    private boolean rebuildPending;

    public GuiAutoBindRules(String profileName) {
        this.profileName = profileName;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        if (profile == null) {
            profile = ProfileStorage.loadProfile(profileName);
            if (profile == null) {
                GuiStack.pop();
                return;
            }
            if (profile.bindings == null) {
                profile.bindings = new ArrayList<>();
            }
        }
        rebuild();
    }

    private void rebuild() {
        this.buttonList.clear();
        valueFields.clear();

        int left = this.width / 2 - 160;

        for (int i = 0; i < profile.bindings.size(); i++) {
            ProfileBinding binding = profile.bindings.get(i);
            int y = LIST_TOP + i * ROW_HEIGHT;

            this.buttonList.add(new GuiButton(ID_ROW_BASE + i, left, y, 90, 20, typeLabel(binding)));

            GuiTextField field = new GuiTextField(this.fontRendererObj, left + 94, y + 3, 186, 14);
            field.setMaxStringLength(128);
            field.setText(binding.value == null ? "" : binding.value);
            field.setEnabled(binding.type != ProfileBinding.Type.SINGLEPLAYER);
            valueFields.add(field);

            this.buttonList.add(new GuiButton(ID_REMOVE_BASE + i, left + 284, y, 36, 20, "X"));
        }

        int bottom = this.height - 50;
        this.buttonList.add(new GuiButton(ID_ADD, left, bottom, 150, 20, I18n.format("radialmenu.rules.add")));
        this.buttonList.add(
            new GuiButton(ID_USE_CURRENT, left + 170, bottom, 150, 20, I18n.format("radialmenu.rules.useCurrent")));
        this.buttonList
            .add(new GuiButton(ID_DONE, this.width / 2 - 100, this.height - 24, 200, 20, I18n.format("gui.done")));
    }

    private String typeLabel(ProfileBinding binding) {
        ProfileBinding.Type type = binding.type == null ? ProfileBinding.Type.SERVER : binding.type;
        return I18n.format(
            "radialmenu.rules.type." + type.name()
                .toLowerCase());
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_DONE) {
            save();
            return;
        }

        if (button.id == ID_ADD) {
            captureFields();
            profile.bindings.add(ProfileBinding.server(""));
            rebuildPending = true;
            return;
        }

        if (button.id == ID_USE_CURRENT) {
            captureFields();
            profile.bindings.add(currentWorldRule());
            rebuildPending = true;
            return;
        }

        int rowIndex = button.id - ID_ROW_BASE;
        if (rowIndex >= 0 && rowIndex < profile.bindings.size()) {
            captureFields();
            cycleType(profile.bindings.get(rowIndex));
            rebuildPending = true;
            return;
        }

        int removeIndex = button.id - ID_REMOVE_BASE;
        if (removeIndex >= 0 && removeIndex < profile.bindings.size()) {
            captureFields();
            profile.bindings.remove(removeIndex);
            rebuildPending = true;
        }
    }

    private static void cycleType(ProfileBinding binding) {
        ProfileBinding.Type[] types = ProfileBinding.Type.values();
        int index = binding.type == null ? -1 : binding.type.ordinal();
        binding.type = types[(index + 1) % types.length];
    }

    /** Builds a rule matching wherever the player currently is. */
    private ProfileBinding currentWorldRule() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isSingleplayer()) {
            return mc.getIntegratedServer() == null ? ProfileBinding.world("")
                : ProfileBinding.world(
                    mc.getIntegratedServer()
                        .getFolderName());
        }
        ServerData data = mc.func_147104_D();
        return ProfileBinding.server(data == null ? "" : data.serverIP);
    }

    /** Text fields are the source of truth for values, so they are read back before any structural change. */
    private void captureFields() {
        for (int i = 0; i < valueFields.size() && i < profile.bindings.size(); i++) {
            profile.bindings.get(i).value = valueFields.get(i)
                .getText()
                .trim();
        }
    }

    private void save() {
        captureFields();
        ProfileStorage.saveProfile(profile);

        // The edited profile may be the one currently loaded in memory; reload so the change takes effect now.
        if (profileName.equals(ProfileManager.activeName())) {
            ProfileManager.switchTo(profileName);
        }
        GuiStack.pop();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (rebuildPending) {
            rebuildPending = false;
            rebuild();
        }

        this.drawDefaultBackground();
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("radialmenu.rules.title", profileName),
            this.width / 2,
            12,
            0xFFFFFF);

        if (profile != null && profile.bindings.isEmpty()) {
            this.drawCenteredString(
                this.fontRendererObj,
                EnumChatFormatting.GRAY + I18n.format("radialmenu.rules.empty"),
                this.width / 2,
                LIST_TOP + 6,
                0xFFFFFF);
        }

        for (GuiTextField field : valueFields) {
            field.drawTextBox();
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        for (GuiTextField field : valueFields) {
            field.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            GuiStack.pop();
            return;
        }
        for (GuiTextField field : valueFields) {
            if (field.textboxKeyTyped(typedChar, keyCode)) {
                return;
            }
        }
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
