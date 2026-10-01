package com.navatusein.radialmenu.client.script;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.ClientChatReceivedEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Every chat line the client receives, kept where a script can read it.
 *
 * <p>
 * <b>The listener runs on the wrong thread.</b> 1.7.10 has no {@code IThreadListener} - that arrived in 1.8 - so a
 * client packet is handled on Netty's worker thread, and this event fires there. Nothing here may touch the game, run a
 * script or open a screen; all it does is put the text in a queue. The tick drains it and the host does the rest, which
 * is the same discipline the rest of the mod uses for a reason.
 *
 * <p>
 * Lines are kept by index rather than consumed, because several scripts may be waiting at once and each needs the whole
 * stream from where it started rather than whatever another script left behind. A reply that arrives before the script
 * gets round to asking for it is therefore still there: the cursor is where that run has read to, not where the buffer
 * happens to end.
 */
public final class ChatCapture {

    /** Lines kept for scripts to catch up on. Chat is noisy, and nothing wants a tenth of a megabyte of backlog. */
    private static final int MAX_LINES = 200;

    private static final ConcurrentLinkedQueue<String> INCOMING = new ConcurrentLinkedQueue<>();

    private static final List<String> LINES = new ArrayList<>();

    /** Global index of {@code LINES.get(0)}, so indices stay stable as the front is trimmed away. */
    private static long firstIndex;

    private ChatCapture() {}

    /**
     * Registered at {@code LOWEST} so a line another mod cancels is never seen.
     *
     * <p>
     * A cancelled message was not shown to the player, and a script reading what the player cannot see would be reading
     * something the server said to somebody else's filter.
     */
    public static final class Listener {

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
        public void onChat(ClientChatReceivedEvent event) {
            if (event.message == null) {
                return;
            }
            String text = event.message.getUnformattedText();
            if (text == null) {
                return;
            }
            // Colour codes are a server's decoration, and a pattern written against them would break the first time
            // somebody changed a plugin's theme.
            INCOMING.add(StringUtils.stripControlCodes(text));
        }
    }

    /** Moves what arrived off-thread into the buffer. Called once per client tick, before any script is pumped. */
    static void drain() {
        String line;
        while ((line = INCOMING.poll()) != null) {
            LINES.add(line);
        }
        while (LINES.size() > MAX_LINES) {
            LINES.remove(0);
            firstIndex++;
        }
    }

    /** Index one past the newest line, which is where a script that starts now begins reading. */
    static long end() {
        return firstIndex + LINES.size();
    }

    /** The line at a global index, or null if there is none yet. */
    static String at(long index) {
        int offset = (int) (index - firstIndex);
        return offset >= 0 && offset < LINES.size() ? LINES.get(offset) : null;
    }

    /**
     * Moves a cursor forward to the oldest line still held, if the buffer has outrun it.
     *
     * <p>
     * A script that took longer than two hundred lines of chat to get round to reading would otherwise wait forever on
     * an index that can never come back. Skipping what was trimmed loses lines; not skipping loses the script.
     */
    static long clampCursor(long cursor) {
        return Math.max(cursor, firstIndex);
    }

    /** Dropped on the way out of a world: the next world's chat is not a continuation of this one's. */
    static void clear() {
        INCOMING.clear();
        LINES.clear();
        firstIndex = 0;
    }
}
