package com.ecoserv.world;

import com.ecoserv.economy.EconomyManager;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldAccess;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Доступ в обычные миры (1.8.0) — как у подмиров: всем, за деньги, за достижение, наградой квеста, по роли,
 * плюс возраст. Купленный/открытый доступ хранится там же, где у подмиров, под ключом world_&lt;измерение&gt;.
 */
public final class WorldAccess {

    private WorldAccess() {
    }

    public static String key(int dim) {
        return "world_" + (dim < 0 ? "m" + (-dim) : Integer.toString(dim));
    }

    public static SubworldAccess.Result check(EntityPlayerMP player, int dim) {
        WorldSettings.Settings s = WorldSettings.peek(dim);
        if (s == null || Perms.isAdmin(player)) {
            return SubworldAccess.Result.yes();
        }
        return SubworldAccess.check(player, s.accessView());
    }

    public static boolean buy(EntityPlayerMP player, int dim) {
        WorldSettings.Settings s = WorldSettings.peek(dim);
        if (s == null || !Subworld.ACCESS_PRICE.equals(s.access) || SubworldRegistry.loadFailed()
                || SubworldRegistry.unlocked(player.getUniqueID().toString(), key(dim))) {
            return false;
        }
        if (s.price > 0 && !EconomyManager.take(player.getUniqueID(), s.price)) {
            return false;
        }
        SubworldRegistry.unlock(player.getUniqueID().toString(), key(dim));
        return true;
    }
}
