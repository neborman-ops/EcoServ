package com.ecoserv.content;

import com.ecoserv.menu.InventoryMenu;
import com.ecoserv.tile.TileLootChest;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.NbtFiles;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.nbt.NBTTagCompound;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Личные копии сундуков добычи: world/ecoserv/loot/&lt;id&gt;.dat. */
public final class LootStore {

    private static final Map<String, NBTTagCompound> CACHE = new HashMap<String, NBTTagCompound>();

    private LootStore() {
    }

    private static File file(String chestId) {
        return new File(DataStore.file("loot"), chestId.replaceAll("[^a-zA-Z0-9\\-]", "") + ".dat");
    }

    private static NBTTagCompound data(String chestId) {
        NBTTagCompound root = CACHE.get(chestId);
        if (root == null) {
            root = NbtFiles.read(file(chestId));
            if (root == null) {
                root = new NBTTagCompound();
            }
            CACHE.put(chestId, root);
        }
        return root;
    }

    private static void save(String chestId) {
        NbtFiles.write(file(chestId), data(chestId));
    }

    public static void unloadAll() {
        CACHE.clear();
    }

    /** Сбросить личные копии у всех — сундук снова полный для каждого. */
    public static void resetAll(String chestId) {
        CACHE.put(chestId, new NBTTagCompound());
        save(chestId);
    }

    /** Открыть игроку его личную копию сундука. */
    public static void open(final EntityPlayerMP player, TileLootChest chest) {
        final String id = chest.getChestId();
        final String key = player.getUniqueID().toString();
        NBTTagCompound players = data(id).getCompoundTag("players");
        NBTTagCompound mine = players.getCompoundTag(key);
        long now = System.currentTimeMillis();
        boolean first = !players.hasKey(key);
        boolean refill = !first && "refill".equals(chest.getMode())
                && now - mine.getLong("last") >= chest.getRefillMinutes() * 60_000L;
        final InventoryBasic inv = new InventoryBasic(chest.getName(), false, TileLootChest.SIZE);
        if (first || refill) {
            for (int i = 0; i < TileLootChest.SIZE; i++) {
                inv.setInventorySlotContents(i, chest.getTemplate().getStackInSlot(i).copy());
            }
            mine = new NBTTagCompound();
            mine.setLong("last", now);
        } else {
            TileLootChest.readInventory(inv, mine.getTagList("items", 10));
            if (inv.isEmpty()) {
                if ("refill".equals(chest.getMode())) {
                    long left = chest.getRefillMinutes() * 60_000L - (now - mine.getLong("last"));
                    ChatUtil.actionBar(player, "&7Пусто. Снова наполнится через &f" + Fmt.duration(Math.max(0L, left)));
                } else {
                    ChatUtil.actionBar(player, "&7Ты уже забрал эту добычу.");
                }
            }
        }
        final long last = mine.getLong("last");
        new InventoryMenu(player, chest.getName(), inv, new Runnable() {
            @Override
            public void run() {
                NBTTagCompound root = data(id);
                NBTTagCompound all = root.getCompoundTag("players");
                NBTTagCompound entry = new NBTTagCompound();
                entry.setLong("last", last);
                entry.setTag("items", TileLootChest.writeInventory(inv));
                all.setTag(key, entry);
                root.setTag("players", all);
                save(id);
            }
        }).open();
        if (first || refill) {
            // запись о получении — сразу, чтобы добыча не выдавалась снова при падении сервера
            NBTTagCompound root = data(id);
            NBTTagCompound all = root.getCompoundTag("players");
            all.setTag(key, mine);
            root.setTag("players", all);
            save(id);
        }
    }
}
