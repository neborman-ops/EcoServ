package com.ecoserv.content;

import com.ecoserv.init.ModRegistry;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.world.WorldTeleport;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Чекпоинты (блок «Чекпоинт», п. 14): игрок наступил — точка сохранена; умер в том же мире —
 * возрождается на последнем чекпоинте. Хранение: world/ecoserv/checkpoints.json.
 */
public final class Checkpoints {

    public static class Point {
        public int dim;
        public int x;
        public int y;
        public int z;
        public String name = "";
        public long time;
    }

    private static final Type MAP = new TypeToken<HashMap<String, Point>>() {
    }.getType();
    private static Map<String, Point> points = new HashMap<String, Point>();
    private static final Map<UUID, Integer> DEATH_DIM = new HashMap<UUID, Integer>();
    private static boolean dirty;

    private Checkpoints() {
    }

    public static void load() {
        Map<String, Point> loaded = JsonUtil.readType(DataStore.file("checkpoints.json"), MAP, new HashMap<String, Point>());
        points = loaded == null ? new HashMap<String, Point>() : loaded;
        dirty = false;
    }

    public static void saveIfDirty() {
        if (dirty && JsonUtil.write(DataStore.file("checkpoints.json"), points)) {
            dirty = false;
        }
    }

    @Nullable
    public static Point get(UUID player) {
        return points.get(player.toString());
    }

    public static boolean clear(UUID player) {
        boolean removed = points.remove(player.toString()) != null;
        dirty |= removed;
        return removed;
    }

    /** Игрок наступил на чекпоинт. */
    public static void touch(EntityPlayerMP player, BlockPos pos, String name) {
        Point current = get(player.getUniqueID());
        if (current != null && current.dim == player.dimension && current.x == pos.getX() && current.y == pos.getY()
                && current.z == pos.getZ()) {
            return;
        }
        Point p = new Point();
        p.dim = player.dimension;
        p.x = pos.getX();
        p.y = pos.getY();
        p.z = pos.getZ();
        p.name = name == null ? "" : name;
        p.time = System.currentTimeMillis();
        points.put(player.getUniqueID().toString(), p);
        dirty = true;
        Actions.title(player, "&a✔ Чекпоинт|&7" + (p.name.isEmpty() ? "точка возрождения сохранена" : p.name), null, 5, 30, 10);
        Actions.sound(player, "minecraft:entity.experience_orb.pickup", 1.0F, 0.8F);
    }

    public static void onDeath(EntityPlayerMP player) {
        DEATH_DIM.put(player.getUniqueID(), player.dimension);
    }

    /** После возрождения: если умер в мире чекпоинта — перенести на чекпоинт. */
    public static void onRespawn(EntityPlayerMP player) {
        Integer deathDim = DEATH_DIM.remove(player.getUniqueID());
        Point p = get(player.getUniqueID());
        if (deathDim == null || p == null || p.dim != deathDim) {
            return;
        }
        WorldServer world = DimensionManager.getWorld(p.dim);
        if (world == null) {
            return;
        }
        BlockPos pos = new BlockPos(p.x, p.y, p.z);
        if (world.getBlockState(pos).getBlock() != ModRegistry.CHECKPOINT) {
            // чекпоинт сломали — точка больше не действует
            clear(player.getUniqueID());
            ChatUtil.actionBar(player, "&7Чекпоинт исчез — обычное возрождение");
            return;
        }
        WorldTeleport.teleport(player, new WorldTeleport.Target(p.dim, p.x + 0.5D, p.y + 0.1D, p.z + 0.5D,
                player.rotationYaw, player.rotationPitch));
    }
}
