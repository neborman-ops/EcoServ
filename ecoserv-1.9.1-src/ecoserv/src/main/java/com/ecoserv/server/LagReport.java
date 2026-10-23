package com.ecoserv.server;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Быстрый отчёт «что тормозит сервер»: TPS, сущности и тайлы по измерениям
 * и топ чанков по количеству сущностей — обычно именно там стоит ферма или
 * скопилась куча мобов.
 */
public final class LagReport {

    private LagReport() {
    }

    public static double meanTickMillis() {
        MinecraftServer server = ServerServices.server();
        if (server == null || server.tickTimeArray == null || server.tickTimeArray.length == 0) {
            return 0.0D;
        }
        long sum = 0L;
        int count = 0;
        for (int i = 0; i < server.tickTimeArray.length; i++) {
            if (server.tickTimeArray[i] > 0L) {
                sum += server.tickTimeArray[i];
                count++;
            }
        }
        return count == 0 ? 0.0D : (sum / (double) count) / 1_000_000.0D;
    }

    public static double tps() {
        double mean = meanTickMillis();
        if (mean <= 0.0D) {
            return 20.0D;
        }
        return Math.min(20.0D, 1000.0D / mean);
    }

    public static List<String> build(int topChunks) {
        List<String> lines = new ArrayList<String>();
        double mean = meanTickMillis();
        lines.add(String.format(Locale.ROOT, "§6=== Нагрузка сервера === §fTPS %.1f §7(%.2f мс/тик)", tps(), mean));

        WorldServer[] worlds = DimensionManager.getWorlds();
        int totalEntities = 0;
        int totalTiles = 0;

        List<String> chunkLines = new ArrayList<String>();

        for (int w = 0; w < worlds.length; w++) {
            final WorldServer world = worlds[w];
            if (world == null) {
                continue;
            }
            int entities = world.loadedEntityList.size();
            int tiles = world.loadedTileEntityList.size();
            int chunks = 0;
            try {
                chunks = world.getChunkProvider().getLoadedChunkCount();
            } catch (Throwable ignored) {
                // некоторые модовые провайдеры чанков могут не отвечать
            }
            totalEntities += entities;
            totalTiles += tiles;

            lines.add(String.format(Locale.ROOT,
                    "§eDIM %d §f%s§7: сущностей %d, тайлов %d, чанков %d",
                    world.provider.getDimension(), world.provider.getDimensionType().getName(),
                    entities, tiles, chunks));

            Map<Long, int[]> perChunk = new HashMap<Long, int[]>();
            for (int i = 0; i < world.loadedEntityList.size(); i++) {
                Entity entity = world.loadedEntityList.get(i);
                long key = (((long) entity.chunkCoordX) << 32) ^ (entity.chunkCoordZ & 0xFFFFFFFFL);
                int[] counter = perChunk.get(Long.valueOf(key));
                if (counter == null) {
                    counter = new int[]{0, entity.chunkCoordX, entity.chunkCoordZ, world.provider.getDimension()};
                    perChunk.put(Long.valueOf(key), counter);
                }
                counter[0]++;
            }
            List<int[]> sorted = new ArrayList<int[]>(perChunk.values());
            Collections.sort(sorted, new Comparator<int[]>() {
                @Override
                public int compare(int[] a, int[] b) {
                    return b[0] - a[0];
                }
            });
            for (int i = 0; i < sorted.size() && i < topChunks; i++) {
                int[] c = sorted.get(i);
                if (c[0] < 20) {
                    break;
                }
                chunkLines.add(String.format(Locale.ROOT,
                        "  §c%d §7сущностей в чанке §f[%d, %d] §7(блоки %d, %d) DIM %d",
                        c[0], c[1], c[2], c[1] * 16, c[2] * 16, c[3]));
            }
        }

        lines.add("§7Всего: сущностей " + totalEntities + ", тайлов " + totalTiles);
        if (!chunkLines.isEmpty()) {
            lines.add("§6Самые загруженные чанки:");
            for (int i = 0; i < chunkLines.size() && i < topChunks; i++) {
                lines.add(chunkLines.get(i));
            }
        }

        // самые «тяжёлые» типы тайлов — часто это модовые машины
        Map<String, Integer> tileTypes = new HashMap<String, Integer>();
        for (int w = 0; w < worlds.length; w++) {
            WorldServer world = worlds[w];
            if (world == null) {
                continue;
            }
            for (int i = 0; i < world.loadedTileEntityList.size(); i++) {
                TileEntity te = world.loadedTileEntityList.get(i);
                String name = te.getClass().getSimpleName();
                Integer prev = tileTypes.get(name);
                tileTypes.put(name, prev == null ? 1 : prev + 1);
            }
        }
        List<Map.Entry<String, Integer>> tileList = new ArrayList<Map.Entry<String, Integer>>(tileTypes.entrySet());
        Collections.sort(tileList, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        if (!tileList.isEmpty()) {
            lines.add("§6Топ тайлов:");
            for (int i = 0; i < tileList.size() && i < 5; i++) {
                lines.add("  §f" + tileList.get(i).getKey() + " §7x" + tileList.get(i).getValue());
            }
        }
        return lines;
    }
}
