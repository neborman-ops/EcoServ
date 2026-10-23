package com.ecoserv.server;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ItemCodec;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Предметы в наградах за игру и ежедневках (деньги — в конфиге).
 * Файл: config/ecoserv/rewards.json, правится командой /ecoadmin rewards ... item add.
 */
public final class RewardItems {

    public static class Data {
        public List<String> daily = new ArrayList<String>();
        public List<String> playtime = new ArrayList<String>();
    }

    private static Data data = new Data();

    private RewardItems() {
    }

    private static File file() {
        return new File(EcoConfig.getConfigDir(), "rewards.json");
    }

    public static void load() {
        Data loaded = JsonUtil.read(file(), Data.class, null);
        data = loaded == null ? new Data() : loaded;
        if (data.daily == null) {
            data.daily = new ArrayList<String>();
        }
        if (data.playtime == null) {
            data.playtime = new ArrayList<String>();
        }
        if (loaded == null) {
            save();
        }
    }

    public static void save() {
        JsonUtil.write(file(), data);
    }

    public static List<ItemStack> daily() {
        return ItemCodec.decodeAll(data.daily);
    }

    public static List<ItemStack> playtime() {
        return ItemCodec.decodeAll(data.playtime);
    }

    public static void add(boolean daily, ItemStack stack) {
        (daily ? data.daily : data.playtime).add(ItemCodec.encode(stack));
        save();
    }

    public static void clear(boolean daily) {
        (daily ? data.daily : data.playtime).clear();
        save();
    }

    /** Выдать список предметов (что не влезло — под ноги). */
    public static void give(EntityPlayerMP player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            PlayerUtil.giveOrDrop(player, stack.copy());
        }
    }
}
