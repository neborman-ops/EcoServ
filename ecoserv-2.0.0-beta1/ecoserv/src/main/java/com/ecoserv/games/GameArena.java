package com.ecoserv.games;

import com.ecoserv.EcoServ;
import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldManager;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Мир арен мини-игр (1.9.0): пустой подмир «ecoserv_games» (создаётся сам, если включено games.auto_world).
 * Каждая игра строит свою трассу в своём «слоте» (полоса по X), после игры она стирается по кусочкам.
 */
public final class GameArena {

    public static final String SUBWORLD = "ecoserv_games";
    public static final int SLOT_WIDTH = 1024;
    public static final int BASE_Y = 100;

    private static final Set<Integer> SLOTS = new HashSet<Integer>();
    /** Очередь уборки: {x1, y1, z1, x2, y2, z2, текущий x}. */
    private static final Deque<int[]> CLEANUP = new ArrayDeque<int[]>();

    private GameArena() {
    }

    /** Подмир арен (создаёт, если можно). null — нет и создать нельзя. */
    @Nullable
    public static SubworldDim dim(boolean create) {
        Subworld sw = SubworldRegistry.get(SUBWORLD);
        if (sw == null) {
            if (!create || !ExtraConfig.gamesAutoWorld) {
                return null;
            }
            sw = new Subworld();
            sw.id = SUBWORLD;
            sw.name = "&dАрены мини-игр";
            sw.description = "Создан автоматически для мини-игр";
            sw.type = Subworld.VOID;
            sw.icon = "minecraft:clock";
            sw.noMenuTeleport = true;
            sw.access = Subworld.ACCESS_ROLE;
            sw.rules.mobs = false;
            sw.rules.pvp = false;
            sw.rules.keepInventory = true;
            sw.rules.fireSpread = false;
            sw.rules.mobGriefing = false;
            sw.rules.time = "noon";
            sw.rules.weather = "clear";
            sw.guests = false;
            String err = SubworldManager.create(sw, null);
            if (err != null) {
                EcoServ.LOG.warn("Мир арен мини-игр не создан: {}", err);
                return null;
            }
            EcoServ.LOG.info("Создан мир арен мини-игр «{}»", SUBWORLD);
        }
        return SubworldRegistry.find(SUBWORLD, SubworldDim.SHARED);
    }

    public static boolean isArenaDim(int dim) {
        Subworld sw = SubworldRegistry.subworldOf(dim);
        return sw != null && SUBWORLD.equals(sw.id);
    }

    @Nullable
    public static WorldServer world(SubworldDim d) {
        return PlayerUtil.server() == null ? null : PlayerUtil.server().getWorld(d.dim);
    }

    public static synchronized int takeSlot() {
        int s = 1;
        while (SLOTS.contains(s)) {
            s++;
        }
        SLOTS.add(s);
        return s;
    }

    public static synchronized void freeSlot(int slot) {
        SLOTS.remove(slot);
    }

    public static BlockPos origin(int slot) {
        return new BlockPos(slot * SLOT_WIDTH, BASE_Y, 0);
    }

    /** Стереть область (по кусочкам, чтобы не тормозить сервер). */
    public static void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        CLEANUP.add(new int[]{Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2),
                Math.max(y1, y2), Math.max(z1, z2), Math.min(x1, x2)});
    }

    /** Раз в тик: уборка не больше ~16 тысяч блоков. */
    public static void tick() {
        if (CLEANUP.isEmpty()) {
            return;
        }
        SubworldDim d = dim(false);
        WorldServer w = d == null ? null : world(d);
        if (w == null) {
            CLEANUP.clear();
            return;
        }
        int budget = 16000;
        IBlockState air = Blocks.AIR.getDefaultState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        while (budget > 0 && !CLEANUP.isEmpty()) {
            int[] job = CLEANUP.peek();
            int x = job[6];
            for (int y = job[1]; y <= job[4]; y++) {
                for (int z = job[2]; z <= job[5]; z++) {
                    m.setPos(x, y, z);
                    if (w.isBlockLoaded(m) && w.getBlockState(m).getBlock() != Blocks.AIR) {
                        w.setBlockState(m, air, 2);
                    }
                    budget--;
                }
            }
            job[6]++;
            if (job[6] > job[3]) {
                CLEANUP.poll();
            }
        }
    }
}
