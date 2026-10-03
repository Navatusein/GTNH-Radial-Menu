package com.navatusein.radialmenu.client.icon;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.navatusein.radialmenu.RadialMenuMod;
import com.navatusein.radialmenu.client.profile.ProfileStorage;

/**
 * The face off a player's skin, kept after they log off.
 *
 * <p>
 * A head is learned rather than fetched: while that player is loaded on the client their skin is already a texture
 * the game downloaded to render them with, so the face is cut straight out of it. Nothing is asked of a skin server,
 * which in 1.7.10 is an address that has not answered for years - whatever the pack does about skins, this follows
 * it, because it reads the result rather than repeating the request.
 *
 * <p>
 * Cut faces are written to {@code RadialMenu/cache/heads} as 8x8 PNGs, which is the whole point: an entry naming a
 * person is at its most useful when they are not around, and a head that went blank the moment they left would be
 * blank exactly then. The cache is refreshed whenever they are back and in range, so a changed skin catches up on
 * its own and a face captured before the download landed does not stick.
 */
public final class PlayerHeadIcons {

    /** The face is eight pixels square on a skin of the standard width. */
    private static final int FACE = 8;

    /** Where the face and the hat layer sit on a 64-wide skin. */
    private static final int FACE_U = 8;

    private static final int FACE_V = 8;

    private static final int HAT_U = 40;

    private static final int HAT_V = 8;

    /** The width every skin is laid out against; a wider one is the same layout at a multiple of it. */
    private static final int SKIN_WIDTH = 64;

    /** How often a head is cut again while its player is here, in milliseconds. */
    private static final long REFRESH_MS = 10_000L;

    private static final Map<String, Head> HEADS = new HashMap<>();

    private PlayerHeadIcons() {}

    /** One player's face: the texture it is drawn from, and when it was last cut out of their skin. */
    private static final class Head {

        ResourceLocation location;

        DynamicTexture texture;

        int size;

        long lastAttempt;

        /** Whether what is on screen is also what is on disk, so an unchanged face is not written every refresh. */
        boolean saved;
    }

    /**
     * The texture to draw for a name, or null while there is nothing to draw.
     *
     * <p>
     * Null rather than a stand-in head: the player may simply not have been seen yet, and a Steve face that later
     * turned into somebody is worse than a slot that was briefly empty.
     */
    public static ResourceLocation texture(String name) {
        String key = key(name);
        if (key == null) {
            return null;
        }

        Head head = HEADS.get(key);
        if (head == null) {
            head = new Head();
            HEADS.put(key, head);
            loadFromDisk(key, head);
        }

        AbstractClientPlayer player = findLoaded(key);
        if (player != null && System.currentTimeMillis() - head.lastAttempt >= REFRESH_MS) {
            head.lastAttempt = System.currentTimeMillis();
            capture(key, player.getLocationSkin(), head);
        }
        return head.location;
    }

    /** Names a head is already known for, for the picker. Sorted, because it is a list someone reads. */
    public static List<String> listCached() {
        List<String> names = new ArrayList<>();
        File[] files = headsDir().listFiles();
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                if (file.isFile() && fileName.toLowerCase(Locale.ROOT)
                    .endsWith(".png")) {
                    names.add(fileName.substring(0, fileName.length() - 4));
                }
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Names of the players the client currently has loaded, for the picker. */
    public static List<String> listLoaded() {
        List<String> names = new ArrayList<>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return names;
        }
        for (Object entity : mc.theWorld.playerEntities) {
            if (entity instanceof EntityPlayer) {
                String name = ((EntityPlayer) entity).getCommandSenderName();
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /**
     * Forgets every head held in memory, so the next draw reads the folder again.
     *
     * <p>
     * The GL textures go with them: a dynamic texture is an id the game will not hand back on its own, and this is
     * called from the editor's refresh button, which a player can press as often as they like.
     */
    public static void refresh() {
        for (Head head : HEADS.values()) {
            release(head);
        }
        HEADS.clear();
    }

    private static void release(Head head) {
        if (head.location != null) {
            Minecraft.getMinecraft()
                .getTextureManager()
                .deleteTexture(head.location);
        }
        head.location = null;
        head.texture = null;
        head.size = 0;
    }

    /**
     * Cuts the face and the hat layer out of a skin and keeps the result.
     *
     * <p>
     * Read back off the GPU rather than out of the image the download produced: that image is private to a vanilla
     * texture object, while the texture itself is something any mod may ask about - and whatever a pack does to
     * skins ends up there, HD ones included, which is why the layout is measured as a multiple of 64 wide rather
     * than assumed.
     */
    private static void capture(String key, ResourceLocation skin, Head head) {
        if (skin == null) {
            return;
        }
        try {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(skin);

            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            // Not uploaded yet, or not a skin. Either way there is nothing to cut and the next refresh will try
            // again - a head is allowed to arrive a moment late.
            if (width < SKIN_WIDTH || height < width / 2) {
                return;
            }
            int scale = width / SKIN_WIDTH;

            ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);

            int size = FACE * scale;
            int[] face = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int skinPixel = read(pixels, width, FACE_U * scale + x, FACE_V * scale + y);
                    int hatPixel = read(pixels, width, HAT_U * scale + x, HAT_V * scale + y);
                    face[y * size + x] = over(hatPixel, skinPixel);
                }
            }

            store(key, head, face, size);
        } catch (Throwable failure) {
            // A head is decoration. Whatever the driver or the pack did here, it must not take the wheel with it.
            RadialMenuMod.LOG.warn("Could not read the skin of " + key, failure);
        }
    }

    /** One pixel of the read-back skin, as 0xAARRGGBB. The buffer is RGBA bytes, row by row from the top. */
    private static int read(ByteBuffer pixels, int width, int x, int y) {
        int index = (y * width + x) * 4;
        int r = pixels.get(index) & 0xFF;
        int g = pixels.get(index + 1) & 0xFF;
        int b = pixels.get(index + 2) & 0xFF;
        int a = pixels.get(index + 3) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * The hat layer over the face.
     *
     * <p>
     * Blended rather than drawn as a second quad, because the result is one small texture that then costs nothing
     * per frame. A legacy skin has no hat to speak of - vanilla's own download makes that half of the sheet
     * transparent where it is unused - so this comes out as the bare face there.
     */
    private static int over(int top, int bottom) {
        int topAlpha = top >>> 24;
        if (topAlpha == 0) {
            return bottom;
        }
        if (topAlpha == 255) {
            return top;
        }
        float alpha = topAlpha / 255.0F;
        int r = Math.round(((top >> 16) & 0xFF) * alpha + ((bottom >> 16) & 0xFF) * (1 - alpha));
        int g = Math.round(((top >> 8) & 0xFF) * alpha + ((bottom >> 8) & 0xFF) * (1 - alpha));
        int b = Math.round((top & 0xFF) * alpha + (bottom & 0xFF) * (1 - alpha));
        int a = Math.max(bottom >>> 24, topAlpha);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Puts a freshly cut face on screen and, if it is new, on disk.
     *
     * <p>
     * The dynamic texture is written through rather than replaced: this runs again every few seconds for as long as
     * its player is around, and a new texture each time would hand the driver a fresh id to keep forever.
     */
    private static void store(String key, Head head, int[] face, int size) {
        if (head.texture == null || head.size != size) {
            release(head);
            head.texture = new DynamicTexture(size, size);
            head.size = size;
            head.location = Minecraft.getMinecraft()
                .getTextureManager()
                .getDynamicTextureLocation(RadialMenuMod.MODID + "_head", head.texture);
            head.saved = false;
        }

        int[] data = head.texture.getTextureData();
        boolean changed = false;
        for (int i = 0; i < face.length && i < data.length; i++) {
            if (data[i] != face[i]) {
                data[i] = face[i];
                changed = true;
            }
        }
        if (changed || !head.saved) {
            head.texture.updateDynamicTexture();
            head.saved = save(key, face, size);
        }
    }

    private static boolean save(String key, int[] face, int size) {
        File file = headFile(key);
        if (file == null) {
            return false;
        }
        try {
            File dir = file.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return false;
            }
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, size, size, face, 0, size);
            ImageIO.write(image, "png", file);
            return true;
        } catch (Exception failure) {
            RadialMenuMod.LOG.warn("Could not cache the head of " + key, failure);
            return false;
        }
    }

    private static void loadFromDisk(String key, Head head) {
        File file = headFile(key);
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null || image.getWidth() <= 0) {
                return;
            }
            int size = image.getWidth();
            int[] face = new int[size * size];
            image.getRGB(0, 0, size, Math.min(size, image.getHeight()), face, 0, size);
            store(key, head, face, size);
            head.saved = true;
        } catch (Exception failure) {
            RadialMenuMod.LOG.warn("Could not read the cached head of " + key, failure);
        }
    }

    /** The loaded player going by this name, or null when nobody here does. */
    private static AbstractClientPlayer findLoaded(String key) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return null;
        }
        for (Object entity : mc.theWorld.playerEntities) {
            if (!(entity instanceof AbstractClientPlayer)) {
                continue;
            }
            AbstractClientPlayer player = (AbstractClientPlayer) entity;
            if (key.equals(key(player.getCommandSenderName()))) {
                return player;
            }
        }
        return null;
    }

    /**
     * What a name is filed under: lower case, and nothing a file system could object to.
     *
     * <p>
     * Lower case because the player types the name and the server capitalises it, and those should not be two
     * different heads. Anything outside a plain name becomes an underscore rather than being refused - a server
     * that allows such names should still get a head out of this.
     */
    private static String key(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim()
            .toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            builder.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' ? c : '_');
        }
        return builder.toString();
    }

    private static File headsDir() {
        return new File(new File(ProfileStorage.rootDir(), "cache"), "heads");
    }

    private static File headFile(String key) {
        return key == null || key.isEmpty() ? null : new File(headsDir(), key + ".png");
    }
}
