package com.ecoserv.world;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.economy.PlayerStats;
import com.ecoserv.economy.StatsStore;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ITeleporter;

import javax.annotation.Nullable;

/** Телепорт игрока в другой мир с поиском безопасного места. */
public final class WorldTeleport {

    /** Куда именно попадёт игрок. */
    public static final class Target {
        public final int dim;
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;

        public Target(int dim, double x, double y, double z, float yaw, float pitch) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    /** Кладёт сущность ровно в заданную точку, без поиска порталов. */
    private static final class FixedTeleporter implements ITeleporter {
        private final Target target;

        FixedTeleporter(Target target) {
            this.target = target;
        }

        @Override
        public void placeEntity(World world, Entity entity, float yaw) {
            entity.setLocationAndAngles(target.x, target.y, target.z, target.yaw, target.pitch);
            entity.motionX = 0.0D;
            entity.motionY = 0.0D;
            entity.motionZ = 0.0D;
        }
    }

    private WorldTeleport() {
    }

    /** Запомнить, где игрок стоит сейчас (для возврата в этот мир позже). */
    public static void rememberPosition(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        PlayerStats stats = StatsStore.get(player.getUniqueID());
        stats.lastPositions.put(player.dimension, new double[]{
                player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch});
        StatsStore.markDirty();
    }

    /**
     * Точка прибытия: последняя позиция игрока в этом мире (если включено и там безопасно)
     * или спавн мира. null — мир не загрузился.
     */
    @Nullable
    public static Target pickTarget(EntityPlayerMP player, int dim, boolean toSpawn) {
        MinecraftServer server = PlayerUtil.server();
        if (server == null || com.ecoserv.subworld.SubworldManager.isBusyDim(dim)) {
            // мир подмира, который сейчас устанавливается или публикуется, загружать нельзя
            return null;
        }
        WorldServer world = server.getWorld(dim);
        if (world == null) {
            return null;
        }
        if (!toSpawn && EcoConfig.worldReturnToLast) {
            double[] last = StatsStore.get(player.getUniqueID()).lastPositions.get(dim);
            if (last != null && last.length >= 5) {
                BlockPos feet = new BlockPos(last[0], last[1], last[2]);
                if (isSafe(world, feet)) {
                    return new Target(dim, last[0], last[1], last[2], (float) last[3], (float) last[4]);
                }
                BlockPos near = findSafe(world, feet, Math.max(2, EcoConfig.worldSearchRadius / 2), feet.getY());
                if (near != null) {
                    return new Target(dim, near.getX() + 0.5D, near.getY(), near.getZ() + 0.5D,
                            (float) last[3], (float) last[4]);
                }
            }
        }

        // спавн мира; у Края — обсидиановая площадка там же, где её строит ванильный портал
        BlockPos endSpawn = dim == 1 ? new BlockPos(100, 50, 0) : null;
        if (endSpawn != null) {
            buildEndPlatform(world, endSpawn);
            return new Target(dim, endSpawn.getX() + 0.5D, endSpawn.getY(), endSpawn.getZ() + 0.5D, 90.0F, 0.0F);
        }
        BlockPos spawn = world.getSpawnPoint();
        BlockPos safe = findSafe(world, spawn, EcoConfig.worldSearchRadius, -1);
        if (safe == null) {
            safe = carveSpot(world, spawn);
        }
        return new Target(dim, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D, player.rotationYaw, 0.0F);
    }

    /** Безопасная точка рядом с нужным местом (для /zone tp, /activator tp). */
    @Nullable
    public static Target near(EntityPlayerMP player, int dim, BlockPos around) {
        MinecraftServer server = PlayerUtil.server();
        if (com.ecoserv.subworld.SubworldManager.isBusyDim(dim)) {
            return null;
        }
        WorldServer world = server == null ? null : server.getWorld(dim);
        if (world == null) {
            return null;
        }
        BlockPos safe = findSafe(world, around, 8, around.getY() > 0 && around.getY() < 250 ? around.getY() : -1);
        if (safe == null) {
            safe = findSafe(world, around, 8, -1);
        }
        if (safe == null) {
            safe = carveSpot(world, around);
        }
        return new Target(dim, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                player.rotationYaw, player.rotationPitch);
    }

    /** Телепорт. false — другой мод отменил переход между мирами. */
    public static boolean teleport(EntityPlayerMP player, Target target) {
        if (player.dimension != target.dim && com.ecoserv.subworld.SubworldManager.isBusyDim(target.dim)) {
            return false;
        }
        if (player.isRiding()) {
            player.dismountRidingEntity();
        }
        for (Entity passenger : new java.util.ArrayList<Entity>(player.getPassengers())) {
            passenger.dismountRidingEntity();
        }
        if (player.dimension != target.dim) {
            player.changeDimension(target.dim, new FixedTeleporter(target));
            if (player.dimension != target.dim) {
                return false;
            }
        }
        player.connection.setPlayerLocation(target.x, target.y, target.z, target.yaw, target.pitch);
        player.fallDistance = 0.0F;
        player.motionX = 0.0D;
        player.motionY = 0.0D;
        player.motionZ = 0.0D;
        return true;
    }

    // --- поиск безопасного места -------------------------------------------

    private static int topLimit(World world) {
        // у Незера потолок из бедрока на 128
        return world.provider.isNether() ? 120 : world.getActualHeight() - 2;
    }

    /**
     * Ищет по кольцам вокруг центра столбец, где есть твёрдый пол и два блока воздуха.
     *
     * @param preferY высота, рядом с которой искать в первую очередь (-1 — поверхность)
     */
    @Nullable
    public static BlockPos findSafe(World world, BlockPos center, int radius, int preferY) {
        // сначала поверхность (или нужная высота) по всем кольцам, и только потом пещеры:
        // иначе на спавне посреди океана игрока унесло бы в пещеру под дном
        BlockPos found = searchRings(world, center, radius, preferY, false);
        if (found != null || preferY > 0) {
            return found;
        }
        return searchRings(world, center, radius, preferY, true);
    }

    @Nullable
    private static BlockPos searchRings(World world, BlockPos center, int radius, int preferY, boolean caves) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    BlockPos found = safeInColumn(world, center.getX() + dx, center.getZ() + dz, preferY, caves);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos safeInColumn(World world, int x, int z, int preferY, boolean caves) {
        int top = topLimit(world);
        if (preferY > 0) {
            // рядом с нужной высотой: вверх и вниз по очереди
            for (int d = 0; d <= 8; d++) {
                BlockPos up = new BlockPos(x, Math.min(top, preferY + d), z);
                if (isSafe(world, up)) {
                    return up;
                }
                BlockPos down = new BlockPos(x, Math.max(1, preferY - d), z);
                if (isSafe(world, down)) {
                    return down;
                }
            }
            return null;
        }
        if (!caves) {
            if (world.provider.isNether() || !world.provider.hasSkyLight()) {
                return null;
            }
            // обычный мир: поверхность по карте высот
            int surface = world.getHeight(x, z);
            if (surface > 0 && surface <= top) {
                BlockPos pos = new BlockPos(x, surface, z);
                if (isSafe(world, pos)) {
                    return pos;
                }
            }
            return null;
        }
        // пещерные миры (Незер и т.п.): сверху вниз до первого безопасного места
        for (int y = top; y >= 2; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (isSafe(world, pos)) {
                return pos;
            }
        }
        return null;
    }

    /** Ноги и голова в воздухе, под ногами твёрдый безопасный блок. */
    public static boolean isSafe(World world, BlockPos feet) {
        if (feet.getY() < 1 || feet.getY() >= world.getHeight() - 1) {
            return false;
        }
        BlockPos floorPos = feet.down();
        IBlockState floor = world.getBlockState(floorPos);
        Material floorMaterial = floor.getMaterial();
        if (floorMaterial.isLiquid() || floorMaterial == Material.FIRE || floorMaterial == Material.CACTUS
                || floor.getBlock() == Blocks.MAGMA) {
            return false;
        }
        if (!floor.isSideSolid(world, floorPos, EnumFacing.UP)) {
            return false;
        }
        return passable(world, feet) && passable(world, feet.up());
    }

    private static boolean passable(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Material m = state.getMaterial();
        if (m.isLiquid() || m == Material.FIRE || m == Material.PORTAL || m == Material.WEB || m == Material.CACTUS) {
            return false;
        }
        return !m.blocksMovement();
    }

    /** Крайний случай: безопасного места нет — делаем нишу 1x2 с полом. */
    private static BlockPos carveSpot(World world, BlockPos around) {
        int y = Math.max(5, Math.min(topLimit(world), around.getY() > 0 ? around.getY() : 64));
        BlockPos feet = new BlockPos(around.getX(), y, around.getZ());
        world.setBlockToAir(feet);
        world.setBlockToAir(feet.up());
        if (!world.getBlockState(feet.down()).isSideSolid(world, feet.down(), EnumFacing.UP)) {
            world.setBlockState(feet.down(), Blocks.COBBLESTONE.getDefaultState());
        }
        return feet;
    }

    /** Как ванильный портал в Край: обсидиан 5x5 и воздух над ним. */
    private static void buildEndPlatform(World world, BlockPos spawn) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.setBlockState(spawn.add(dx, -1, dz), Blocks.OBSIDIAN.getDefaultState());
                for (int dy = 0; dy < 3; dy++) {
                    world.setBlockToAir(spawn.add(dx, dy, dz));
                }
            }
        }
    }
}
