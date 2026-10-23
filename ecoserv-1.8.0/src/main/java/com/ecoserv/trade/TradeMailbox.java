package com.ecoserv.trade;

import com.ecoserv.EcoServ;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.PlayerUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * «Почта»: предметы, которые некому было вернуть (игрок вышел во время обмена).
 * Выдаются при следующем входе, ничего не теряется.
 */
public final class TradeMailbox {

    private static final Type TYPE = new TypeToken<HashMap<String, ArrayList<String>>>() {
    }.getType();

    private static final Map<String, ArrayList<String>> box = new HashMap<String, ArrayList<String>>();
    private static boolean dirty;

    private TradeMailbox() {
    }

    public static synchronized void store(@Nullable UUID player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        ArrayList<String> list = box.get(player.toString());
        if (list == null) {
            list = new ArrayList<String>();
            box.put(player.toString(), list);
        }
        list.add(stack.writeToNBT(new NBTTagCompound()).toString());
        dirty = true;
        save();
    }

    public static synchronized void deliver(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        ArrayList<String> list = box.remove(player.getUniqueID().toString());
        if (list == null || list.isEmpty()) {
            return;
        }
        int count = 0;
        for (int i = 0; i < list.size(); i++) {
            try {
                NBTTagCompound tag = JsonToNBT.getTagFromJson(list.get(i));
                ItemStack stack = new ItemStack(tag);
                if (!stack.isEmpty()) {
                    PlayerUtil.giveOrDrop(player, stack);
                    count++;
                }
            } catch (Exception e) {
                EcoServ.LOG.error("Не удалось выдать предмет из почты игроку {}", player.getName(), e);
            }
        }
        dirty = true;
        save();
        if (count > 0) {
            ChatUtil.send(player, "&aВозвращено предметов из незавершённого обмена: &f" + count);
        }
    }

    public static synchronized void load() {
        box.clear();
        HashMap<String, ArrayList<String>> raw =
                JsonUtil.readType(DataStore.file("mailbox.json"), TYPE, new HashMap<String, ArrayList<String>>());
        box.putAll(raw);
        dirty = false;
    }

    public static synchronized void save() {
        if (!dirty) {
            return;
        }
        if (JsonUtil.write(DataStore.file("mailbox.json"), box)) {
            dirty = false;
        }
    }

    public static synchronized List<String> pending(UUID player) {
        ArrayList<String> list = box.get(player.toString());
        return list == null ? new ArrayList<String>() : list;
    }
}
