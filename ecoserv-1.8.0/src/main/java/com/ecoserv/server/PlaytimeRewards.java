package com.ecoserv.server;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.economy.PlayerStats;
import com.ecoserv.economy.StatsStore;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Награда за время на сервере + определение АФК.
 * АФК считается по отсутствию движения: стоящий в углу игрок денег не получает.
 */
public final class PlaytimeRewards {

    private static class Activity {
        double x;
        double y;
        double z;
        long lastMove = System.currentTimeMillis();
    }

    private static final Map<UUID, Activity> activity = new HashMap<UUID, Activity>();

    private PlaytimeRewards() {
    }

    public static void onLogin(EntityPlayerMP player) {
        Activity a = new Activity();
        a.x = player.posX;
        a.y = player.posY;
        a.z = player.posZ;
        activity.put(player.getUniqueID(), a);
    }

    public static void onLogout(EntityPlayerMP player) {
        activity.remove(player.getUniqueID());
    }

    public static boolean isAfk(UUID uuid) {
        if (EcoConfig.afkMinutes <= 0) {
            return false;
        }
        Activity a = activity.get(uuid);
        if (a == null) {
            return false;
        }
        return System.currentTimeMillis() - a.lastMove > EcoConfig.afkMinutes * 60_000L;
    }

    /** Вызывается раз в секунду. */
    public static void tick() {
        List<EntityPlayerMP> players = com.ecoserv.util.PlayerUtil.allOnline();
        long now = System.currentTimeMillis();

        for (int i = 0; i < players.size(); i++) {
            EntityPlayerMP player = players.get(i);
            UUID uuid = player.getUniqueID();
            Activity a = activity.get(uuid);
            if (a == null) {
                a = new Activity();
                a.x = player.posX;
                a.y = player.posY;
                a.z = player.posZ;
                activity.put(uuid, a);
            }
            double dx = player.posX - a.x;
            double dy = player.posY - a.y;
            double dz = player.posZ - a.z;
            if (dx * dx + dy * dy + dz * dz > 0.05D) {
                a.x = player.posX;
                a.y = player.posY;
                a.z = player.posZ;
                a.lastMove = now;
            }

            boolean afk = EcoConfig.afkMinutes > 0 && now - a.lastMove > EcoConfig.afkMinutes * 60_000L;
            PlayerStats stats = StatsStore.get(uuid);
            stats.playtimeMillis += 1000L;

            if (!EcoConfig.playtimeRewardEnabled || afk) {
                StatsStore.markDirty();
                continue;
            }

            stats.rewardProgressMillis += 1000L;
            long need = Math.max(1, EcoConfig.playtimeRewardMinutes) * 60_000L;
            if (stats.rewardProgressMillis >= need) {
                stats.rewardProgressMillis -= need;
                // множитель ролей (VIP x1.5 и т.п.)
                long amount = Math.round(EcoConfig.playtimeRewardAmount
                        * com.ecoserv.role.RoleManager.rewardMultiplier(uuid));
                java.util.List<net.minecraft.item.ItemStack> items = RewardItems.playtime();
                if (amount > 0L) {
                    EconomyManager.give(uuid, amount);
                    stats.totalEarned += amount;
                    PacketHandler.sendPlayerState(player);
                }
                if (!items.isEmpty()) {
                    RewardItems.give(player, items);
                }
                if (amount > 0L || !items.isEmpty()) {
                    ChatUtil.send(player, "&aНаграда за игру: &e" + (amount > 0L ? Fmt.money(amount) : "")
                            + (items.isEmpty() ? "" : (amount > 0L ? " &7+ " : "&7") + com.ecoserv.util.ItemCodec.describe(items, 4)));
                }
            }
            StatsStore.markDirty();
        }
    }

    /** Выдать ежедневную награду. Возвращает сумму или -1, если уже получена. */
    public static long claimDaily(EntityPlayerMP player) {
        if (!EcoConfig.dailyEnabled) {
            return -2L;
        }
        PlayerStats stats = StatsStore.get(player.getUniqueID());
        long today = System.currentTimeMillis() / 86_400_000L;
        if (stats.lastDailyDay == today) {
            return -1L;
        }
        if (stats.lastDailyDay == today - 1L) {
            stats.dailyStreak = Math.min(EcoConfig.dailyStreakMax, stats.dailyStreak + 1);
        } else {
            stats.dailyStreak = 1;
        }
        stats.lastDailyDay = today;

        long base = EcoConfig.dailyAmount + EcoConfig.dailyStreakBonus * (stats.dailyStreak - 1);
        long amount = Math.round(base * com.ecoserv.role.RoleManager.rewardMultiplier(player.getUniqueID()));
        if (amount > 0L) {
            EconomyManager.give(player.getUniqueID(), amount);
            stats.totalEarned += amount;
        }
        java.util.List<net.minecraft.item.ItemStack> items = RewardItems.daily();
        if (!items.isEmpty()) {
            RewardItems.give(player, items);
            ChatUtil.send(player, "&aЕжедневная награда, предметы: &f" + com.ecoserv.util.ItemCodec.describe(items, 6));
        }
        StatsStore.markDirty();
        PacketHandler.sendPlayerState(player);
        return Math.max(0L, amount);
    }
}
