package com.ecoserv.util;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketParticles;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;

/**
 * Рамки цветной пылью, которые видит только один игрок (зоны, выделения палочками,
 * порталы, триггеры). Пакеты уходят только ему, в мире ничего не спавнится.
 */
public final class Outline {

    private Outline() {
    }

    /** Коробка по блокам включительно: от min до max (оба угла — блоки внутри). */
    public static int blocks(EntityPlayerMP player, BlockPos a, BlockPos b, float r, float g, float bl, int budget) {
        int x1 = Math.min(a.getX(), b.getX());
        int y1 = Math.min(a.getY(), b.getY());
        int z1 = Math.min(a.getZ(), b.getZ());
        int x2 = Math.max(a.getX(), b.getX()) + 1;
        int y2 = Math.max(a.getY(), b.getY()) + 1;
        int z2 = Math.max(a.getZ(), b.getZ()) + 1;
        return box(player, x1, y1, z1, x2, y2, z2, r, g, bl, budget);
    }

    /** Один блок (например, первая точка выделения). */
    public static int block(EntityPlayerMP player, BlockPos pos, float r, float g, float b) {
        return box(player, pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                r, g, b, 60);
    }

    /** 12 рёбер коробки. Возвращает, сколько частиц потрачено. */
    public static int box(EntityPlayerMP player, double x1, double y1, double z1,
                          double x2, double y2, double z2, float r, float g, float b, int budget) {
        if (player == null || player.connection == null || budget <= 0) {
            return 0;
        }
        double longest = Math.max(x2 - x1, Math.max(y2 - y1, z2 - z1));
        double step = Math.max(0.5D, longest / 40.0D);
        double[][] edges = {
                {x1, y1, z1, x2, y1, z1}, {x1, y1, z2, x2, y1, z2}, {x1, y2, z1, x2, y2, z1}, {x1, y2, z2, x2, y2, z2},
                {x1, y1, z1, x1, y2, z1}, {x2, y1, z1, x2, y2, z1}, {x1, y1, z2, x1, y2, z2}, {x2, y1, z2, x2, y2, z2},
                {x1, y1, z1, x1, y1, z2}, {x2, y1, z1, x2, y1, z2}, {x1, y2, z1, x1, y2, z2}, {x2, y2, z1, x2, y2, z2}
        };
        // пыль с нулевым цветом рисуется красной — чуть сдвигаем, чтобы чёрный/синий тоже работали
        float rr = r <= 0.0F ? 0.001F : r;
        int used = 0;
        for (int e = 0; e < edges.length && used < budget; e++) {
            double[] edge = edges[e];
            double dx = edge[3] - edge[0];
            double dy = edge[4] - edge[1];
            double dz = edge[5] - edge[2];
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int points = Math.max(1, (int) Math.ceil(length / step));
            for (int k = 0; k <= points && used < budget; k++) {
                double t = (double) k / points;
                double x = edge[0] + dx * t;
                double y = edge[1] + dy * t;
                double z = edge[2] + dz * t;
                if (player.getDistanceSq(x, y, z) > 96.0D * 96.0D) {
                    continue;
                }
                // count = 0: смещения r, g, b становятся цветом пыли
                player.connection.sendPacket(new SPacketParticles(EnumParticleTypes.REDSTONE, true,
                        (float) x, (float) y, (float) z, rr, g, b, 1.0F, 0));
                used++;
            }
        }
        return used;
    }
}
