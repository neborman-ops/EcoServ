package com.ecoserv.integration;

import com.ecoserv.economy.EconomyManager;
import com.ecoserv.economy.IEconomy;
import com.ecoserv.shop.ShopEntry;
import com.ecoserv.shop.ShopList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.UUID;

/**
 * Публичное API для других модов и скриптов (CraftTweaker и т.п.).
 * Пример из другого мода:
 * EcoServApi.give(player.getUniqueID(), 500);
 */
public final class EcoServApi {

    private EcoServApi() {
    }

    public static long getBalance(UUID player) {
        return EconomyManager.get(player);
    }

    public static long getBalance(EntityPlayer player) {
        return EconomyManager.getBalance(player);
    }

    public static void give(UUID player, long amount) {
        EconomyManager.give(player, amount);
    }

    public static boolean take(UUID player, long amount) {
        return EconomyManager.take(player, amount);
    }

    /** Подменить источник денег своим (вызывать в postInit). */
    public static void registerEconomy(IEconomy economy) {
        EconomyManager.setProvider(economy);
    }

    /** Средняя цена одной штуки (цена приёма / размер приёма) или 0, если предмет не принимают. */
    public static double getSellPrice(ItemStack stack) {
        ShopEntry entry = ShopList.get().find(stack);
        return entry == null || !entry.canSell() ? 0.0D : entry.getUnitPrice();
    }

    /** Сколько штук забирается за один приём (0 — предмет не принимают). */
    public static int getBatch(ItemStack stack) {
        ShopEntry entry = ShopList.get().find(stack);
        return entry == null || !entry.canSell() ? 0 : entry.batchSize();
    }

    /** Сколько платится за один приём (0 — предмет не принимают). */
    public static double getBatchPrice(ItemStack stack) {
        ShopEntry entry = ShopList.get().find(stack);
        return entry == null || !entry.canSell() ? 0.0D : entry.getEffectiveSellPrice();
    }

    public static double getBuyPrice(ItemStack stack) {
        ShopEntry entry = ShopList.get().find(stack);
        return entry == null ? 0.0D : entry.buyPrice;
    }

    /** Добавить/обновить позицию «1 шт = sellPrice» (старый вариант API). */
    public static void setPrice(ItemStack stack, double sellPrice, double buyPrice) {
        ShopList.get().put(stack, sellPrice, buyPrice);
        ShopList.get().save();
    }

    /** Добавить/обновить позицию «batch шт = price». */
    public static void setBatchPrice(ItemStack stack, int batch, double price) {
        ShopList.get().putBatch(stack, batch, price);
        ShopList.get().save();
    }
}
