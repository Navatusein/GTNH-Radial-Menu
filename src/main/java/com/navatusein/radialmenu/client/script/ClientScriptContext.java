package com.navatusein.radialmenu.client.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

import com.navatusein.radialmenu.config.AccentConfig;
import com.navatusein.radialmenu.core.model.AccentCoefficients;
import com.navatusein.radialmenu.core.script.LookTarget;
import com.navatusein.radialmenu.core.script.ScriptContext;
import com.navatusein.radialmenu.core.script.ScriptItem;

/** The one place a script's view of the game is answered from Minecraft. */
public final class ClientScriptContext implements ScriptContext {

    public static final ClientScriptContext INSTANCE = new ClientScriptContext();

    private ClientScriptContext() {}

    /**
     * Every field reads the player afresh.
     *
     * <p>
     * A script outlives the moment it started - that is the whole point of one that waits for a chat reply - so the
     * player may be somewhere else, or gone, by the time it looks.
     */
    private static EntityPlayer player() {
        return Minecraft.getMinecraft().thePlayer;
    }

    @Override
    public String playerName() {
        EntityPlayer player = player();
        return player == null ? "" : player.getCommandSenderName();
    }

    @Override
    public int dimension() {
        EntityPlayer player = player();
        return player == null ? 0 : player.dimension;
    }

    @Override
    public int blockX() {
        EntityPlayer player = player();
        return player == null ? 0 : (int) Math.floor(player.posX);
    }

    @Override
    public int blockY() {
        EntityPlayer player = player();
        return player == null ? 0 : (int) Math.floor(player.posY);
    }

    @Override
    public int blockZ() {
        EntityPlayer player = player();
        return player == null ? 0 : (int) Math.floor(player.posZ);
    }

    @Override
    public double yaw() {
        EntityPlayer player = player();
        return player == null ? 0.0 : player.rotationYaw;
    }

    @Override
    public double pitch() {
        EntityPlayer player = player();
        return player == null ? 0.0 : player.rotationPitch;
    }

    @Override
    public double health() {
        EntityPlayer player = player();
        return player == null ? 0.0 : player.getHealth();
    }

    @Override
    public int food() {
        EntityPlayer player = player();
        return player == null ? 0
            : player.getFoodStats()
                .getFoodLevel();
    }

    @Override
    public int air() {
        EntityPlayer player = player();
        return player == null ? 0 : player.getAir();
    }

    @Override
    public ScriptItem heldItem() {
        EntityPlayer player = player();
        return player == null ? null : itemOf(player.getHeldItem(), 0);
    }

    @Override
    public List<ScriptItem> inventory() {
        EntityPlayer player = player();
        if (player == null || player.inventory == null) {
            return Collections.emptyList();
        }
        ItemStack[] slots = player.inventory.mainInventory;
        List<ScriptItem> items = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            // Counted from 1, because the script counts from 1 - and the hotbar is the first nine either way.
            ScriptItem item = itemOf(slots[i], i + 1);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public LookTarget lookingAt() {
        Minecraft mc = Minecraft.getMinecraft();
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || mc.theWorld == null) {
            return null;
        }

        if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            Block block = mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ);
            if (block == null) {
                return null;
            }
            int meta = mc.theWorld.getBlockMetadata(hit.blockX, hit.blockY, hit.blockZ);
            return LookTarget.block(
                name(Block.blockRegistry.getNameForObject(block)),
                blockLabel(block, meta),
                meta,
                hit.blockX,
                hit.blockY,
                hit.blockZ);
        }

        if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && hit.entityHit != null) {
            Entity entity = hit.entityHit;
            String id = EntityList.getEntityString(entity);
            String label = entity.getCommandSenderName();
            return LookTarget.entity(
                id == null || id.isEmpty() ? name(label) : id,
                label,
                (int) Math.floor(entity.posX),
                (int) Math.floor(entity.posY),
                (int) Math.floor(entity.posZ));
        }
        return null;
    }

    @Override
    public long worldTime() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld == null ? 0L : mc.theWorld.getWorldTime();
    }

    /**
     * What the player would call where they are: the server's address, or the save's folder.
     *
     * <p>
     * The same thing a profile's auto-bind rules match on, so a script and a rule agree about which server this is.
     */
    @Override
    public String worldName() {
        Minecraft mc = Minecraft.getMinecraft();
        // func_147104_D is getCurrentServerData; the deobfuscated name never made it into this mapping, and the
        // auto-bind rules call it the same way.
        ServerData server = mc.func_147104_D();
        if (server != null && server.serverIP != null) {
            return server.serverIP;
        }
        if (mc.getIntegratedServer() != null) {
            return name(
                mc.getIntegratedServer()
                    .getFolderName());
        }
        return "";
    }

    @Override
    public String storeGet(String key) {
        return ScriptStore.get(key);
    }

    /**
     * One stack as a script sees it.
     *
     * <p>
     * The display name is asked for inside a guard: it is the item's own code, and a modded one can throw on a stack
     * built outside the context it expects - the same hazard the icon renderer catches. A name is worth losing; a
     * script is not.
     */
    private static ScriptItem itemOf(ItemStack stack, int slot) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        String id = name(Item.itemRegistry.getNameForObject(stack.getItem()));
        String label = id;
        try {
            label = stack.getDisplayName();
        } catch (Throwable ignored) {
            // The item would rather not be named outside its own world. Its registry name says enough.
        }
        return new ScriptItem(id, stack.getItemDamage(), stack.stackSize, label, slot);
    }

    private static String blockLabel(Block block, int meta) {
        try {
            return new ItemStack(block, 1, meta).getDisplayName();
        } catch (Throwable ignored) {
            // Blocks with no item form, and blocks whose item throws on a bare stack.
        }
        try {
            return block.getLocalizedName();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String name(Object value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public AccentCoefficients accentCoefficients() {
        return AccentConfig.coefficients();
    }
}
