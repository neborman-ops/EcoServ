package com.ecoserv.util;

import net.minecraft.entity.player.EntityPlayerMP;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Общие ограничения для телепортов, которые делает сам мод (карта Xaero, /world, порталы).
 * Например, в локальном мире команды нельзя улететь за барьер своего острова.
 */
public final class TeleportRules {

    public interface Rule {
        /** Причина запрета (для игрока) или null, если можно. */
        @Nullable
        String check(EntityPlayerMP player, int dim, double x, double y, double z);
    }

    private static final List<Rule> RULES = new CopyOnWriteArrayList<Rule>();

    private TeleportRules() {
    }

    public static void register(Rule rule) {
        RULES.add(rule);
    }

    @Nullable
    public static String check(EntityPlayerMP player, int dim, double x, double y, double z) {
        for (Rule rule : RULES) {
            String reason = rule.check(player, dim, x, y, z);
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }
}
