package com.navatusein.radialmenu.client.gui.editor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

import com.navatusein.radialmenu.client.gui.GuiStack;
import com.navatusein.radialmenu.client.gui.ui.Ui;
import com.navatusein.radialmenu.client.gui.ui.UiScreen;
import com.navatusein.radialmenu.client.profile.ProfileManager;
import com.navatusein.radialmenu.client.profile.ProfileStorage;
import com.navatusein.radialmenu.core.model.Profile;

/**
 * The versions of one profile that were kept, and the way back to them.
 *
 * <p>
 * The files have always been openable by hand - they are ordinary profiles in a folder - but a player who has just
 * lost a submenu is not in the mood to go looking for a folder. This is the same operation with the folder left out.
 */
public class GuiProfileBackups extends UiScreen {

    private static final int ID_RESTORE_BASE = 100;

    /** Width of the button at the end of a row. */
    private static final int RESTORE_WIDTH = 60;

    /**
     * Absolute rather than "ten minutes ago".
     *
     * <p>
     * A relative time has to be recomputed as the screen sits open and pluralised per language; a date and a time
     * says the same thing in one form, and the question being asked - which of these three is the one from before I
     * broke it - is answered by either.
     */
    private static final SimpleDateFormat WHEN = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final String profile;

    /**
     * Which versions are listed, newest first, with 0 standing for the profile as it is now.
     *
     * <p>
     * The current state is on the list because the question is always a comparison - is what I have now the thing I
     * wanted, or is one of these? Three numbers with nothing to measure them against answer nothing, which is how a
     * version two entries behind the profile looked like a version that had not noticed them going.
     */
    private final List<Integer> ages = new ArrayList<>();

    /** What each kept version holds, read once per rebuild rather than per frame. */
    private final List<String> summaries = new ArrayList<>();

    private final List<int[]> rowPositions = new ArrayList<>();

    public GuiProfileBackups(String profile) {
        this.profile = profile;
    }

    @Override
    protected String titleKey() {
        return "radialmenu.backups.title";
    }

    @Override
    protected Object[] titleArgs() {
        return new Object[] { profile };
    }

    @Override
    protected int panelWidth() {
        return 320;
    }

    @Override
    protected boolean framedViewport() {
        return true;
    }

    @Override
    protected void buildControls() {
        ages.clear();
        summaries.clear();
        rowPositions.clear();

        int y = scrolledTop();

        // The profile as it stands, with no button beside it: there is nothing to restore it to.
        rowPositions.add(new int[] { contentLeft(), y });
        ages.add(Integer.valueOf(0));
        summaries.add(summary(0));
        y += Ui.STEP;

        for (int age = 1; age <= ProfileStorage.keptVersions(); age++) {
            if (!ProfileStorage.hasBackup(profile, age)) {
                continue;
            }
            this.buttonList.add(
                new GuiButton(
                    ID_RESTORE_BASE + age,
                    contentRight() - RESTORE_WIDTH,
                    y,
                    RESTORE_WIDTH,
                    Ui.ROW,
                    I18n.format("radialmenu.backups.restore")));
            tooltip(ID_RESTORE_BASE + age, describe("radialmenu.backups.restore.tip"));
            rowPositions.add(new int[] { contentLeft(), y });
            ages.add(Integer.valueOf(age));
            summaries.add(summary(age));
            y += Ui.STEP;
        }

        // Room for the line that says there is nothing kept yet, which sits under the one row there is.
        setContentHeight(Math.max(Ui.STEP, y - scrolledTop()) + (ages.size() <= 1 ? Ui.STEP : 0) + Ui.PAD);
        addBottomBar("gui.done", null, null);
    }

    @Override
    protected int panelHeightHint() {
        return Ui.STEP * (ProfileStorage.keptVersions() + 1);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_PRIMARY) {
            onCancel();
            return;
        }
        final int age = button.id - ID_RESTORE_BASE;
        if (age < 1 || !ProfileStorage.hasBackup(profile, age)) {
            return;
        }
        GuiStack.push(
            new GuiConfirm(
                "radialmenu.backups.confirmRestore",
                I18n.format("radialmenu.backups.confirmRestore.subject", when(age)),
                "radialmenu.backups.restore",
                new GuiConfirm.Result() {

                    @Override
                    public void onConfirmed() {
                        restore(age);
                    }
                }));
    }

    /**
     * Puts a version back and makes the game show it.
     *
     * <p>
     * The reload is not optional: the profile in memory is what the wheel draws, and a file restored underneath it
     * would leave the player looking at the thing they just undid.
     */
    private void restore(int age) {
        if (!ProfileStorage.restoreBackup(profile, age)) {
            return;
        }
        ProfileManager.load();
        requestRebuild();
    }

    @Override
    protected void onCancel() {
        GuiStack.pop();
    }

    @Override
    protected void drawContent(int mouseX, int mouseY, float partialTicks) {
        for (int i = 0; i < ages.size() && i < rowPositions.size(); i++) {
            int[] position = rowPositions.get(i);
            if (!isVisibleRow(position[1])) {
                continue;
            }
            int age = ages.get(i)
                .intValue();
            int textY = position[1] + (Ui.ROW - 8) / 2;
            boolean current = age == 0;

            this.fontRendererObj.drawString(when(age), position[0], textY, current ? Ui.TEXT : Ui.TEXT_MUTED);
            this.fontRendererObj.drawString(
                Ui.fit(summaries.get(i), contentRight() - RESTORE_WIDTH - Ui.GAP - (position[0] + 92)),
                position[0] + 92,
                textY,
                current ? Ui.TEXT : Ui.TEXT_MUTED);
            if (current) {
                String label = I18n.format("radialmenu.backups.current");
                this.fontRendererObj.drawString(
                    label,
                    contentRight() - this.fontRendererObj.getStringWidth(label),
                    textY,
                    Ui.TEXT_MUTED);
            }
        }

        if (ages.size() <= 1) {
            this.fontRendererObj.drawString(
                I18n.format("radialmenu.backups.none"),
                contentLeft(),
                scrolledTop() + Ui.STEP + 6,
                Ui.TEXT_MUTED);
        }
    }

    private String when(int age) {
        long time = age == 0 ? ProfileStorage.profileFile(profile)
            .lastModified() : ProfileStorage.backupTime(profile, age);
        return time == 0L ? "" : WHEN.format(new Date(time));
    }

    /**
     * What is actually in that version, in a few words.
     *
     * <p>
     * A date alone says when, and the player's question is what: the one with six entries is the one from before
     * the submenu went. Read when the screen is built and kept - drawing runs every frame, and a profile carrying
     * scripts is not something to parse sixty times a second to print one number.
     */
    private String summary(int age) {
        Profile kept = age == 0 ? ProfileStorage.loadProfile(profile) : ProfileStorage.loadBackup(profile, age);
        if (kept == null || kept.root == null) {
            return I18n.format("radialmenu.backups.unreadable");
        }
        // The whole tree, not the first ring of it. A submenu emptied out leaves the wheel above it the same
        // length, and a number that could not see that would be silent about the very loss this screen is for.
        return I18n.format("radialmenu.backups.entries", Integer.valueOf(kept.root.deepCount()));
    }

    private static String describe(String key) {
        String translated = I18n.format(key);
        return translated.equals(key) ? null : translated;
    }
}
