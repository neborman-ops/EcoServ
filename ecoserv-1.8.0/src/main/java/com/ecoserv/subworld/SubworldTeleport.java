package com.ecoserv.subworld;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.region.RegionManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldTeleport;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.border.WorldBorder;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;

/** Вход в подмиры: выбор измерения (общий мир / локальный мир команды), доступ, безопасная точка. */
public final class SubworldTeleport {

    /** Куда вернуть игрока по кнопке «Вернуться» (мир, откуда он пришёл в подмиры). */
    private static final String RETURN_TAG = "ecoserv_sw_return";

    private SubworldTeleport() {
    }

    // --- выбор измерения ----------------------------------------------------------

    /**
     * Измерение подмира для игрока: общий мир или активный локальный мир его команды.
     * create — создать первый локальный мир, если его ещё нет.
     */
    @Nullable
    public static SubworldDim targetDim(EntityPlayerMP player, Subworld sw, boolean create) {
        if (!sw.perOwner()) {
            return SubworldRegistry.find(sw.id, SubworldDim.SHARED);
        }
        String owner = SubworldTeams.ownerKey(player, sw);
        SubworldDim active = SubworldRegistry.activeInstance(sw.id, owner);
        if (active != null || !create) {
            return active;
        }
        return SubworldManager.createInstance(sw, owner, SubworldTeams.ownerName(owner), "", player);
    }

    // --- вход ---------------------------------------------------------------------

    /** Войти в подмир: проверка доступа, свой мир команды, точка прибытия. */
    public static boolean enter(EntityPlayerMP player, Subworld sw, boolean toSpawn) {
        return enter(player, sw, toSpawn, null);
    }

    /**
     * @param point точка внутри мира (портал «в точку»), null — спавн или где был
     */
    public static boolean enter(EntityPlayerMP player, Subworld sw, boolean toSpawn, @Nullable double[] point) {
        if (!EcoConfig.subworldsEnabled && !Perms.isAdmin(player)) {
            ChatUtil.send(player, "&cПодмиры выключены на сервере.");
            return false;
        }
        if (!SubworldRegistry.isCurrent(sw)) {
            ChatUtil.send(player, "&cЭтого подмира уже нет — открой меню заново.");
            return false;
        }
        SubworldAccess.Result access = SubworldAccess.check(player, sw);
        if (!access.ok) {
            ChatUtil.send(player, access.reason == null ? "&cНет доступа." : access.reason);
            if (access.canBuy) {
                ChatUtil.send(player, ChatUtil.link("&a[Купить доступ]", "/world buy " + sw.id,
                        "Купить доступ в подмир"));
            }
            return false;
        }
        SubworldDim d = targetDim(player, sw, true);
        if (d == null) {
            ChatUtil.send(player, "&cНе удалось подготовить мир подмира «" + sw.name + "&c».");
            return false;
        }
        return teleportTo(player, d, toSpawn, point);
    }

    /** Войти в конкретное измерение (свой сохранённый мир, мир, куда пригласили). */
    public static boolean teleportTo(EntityPlayerMP player, SubworldDim d, boolean toSpawn, @Nullable double[] point) {
        if (!SubworldRegistry.isCurrent(d)) {
            // меню держало удалённый мир: не загружать его заново
            ChatUtil.send(player, "&cЭтого мира уже нет.");
            return false;
        }
        if (SubworldManager.isBusy(d.subworld)) {
            // идёт установка загруженного мира или публикация в фоне: мир нельзя загружать даже админам,
            // иначе недокопированные места навсегда останутся пустыми
            ChatUtil.send(player, "&eПодмир сейчас готовится (установка мира или публикация) — подожди немного.");
            return false;
        }
        WorldServer world = PlayerUtil.server() == null ? null : PlayerUtil.server().getWorld(d.dim);
        if (world == null) {
            ChatUtil.send(player, "&cМир не загрузился (измерение " + d.dim + ").");
            return false;
        }
        ensureSpawn(world, d);
        WorldTeleport.Target target;
        if (point != null) {
            BlockPos p = new BlockPos(point[0], point[1], point[2]);
            target = WorldTeleport.isSafe(world, p)
                    ? new WorldTeleport.Target(d.dim, point[0], point[1], point[2], (float) point[3], (float) point[4])
                    : WorldTeleport.near(player, d.dim, p);
        } else {
            boolean spawn = toSpawn || player.dimension == d.dim;
            target = spawn ? spawnTarget(player, world) : WorldTeleport.pickTarget(player, d.dim, false);
            if (target != null && !spawn && SubworldBorder.outsideReason(d.dim, target.x, target.z) != null) {
                target = spawnTarget(player, world);
            }
        }
        if (target == null) {
            ChatUtil.send(player, "&cНе нашлось места для входа.");
            return false;
        }
        rememberReturn(player);
        if (player.dimension != d.dim) {
            WorldTeleport.rememberPosition(player);
        }
        RegionManager.suppress(player, 1500L);
        if (!WorldTeleport.teleport(player, target)) {
            ChatUtil.send(player, "&cПереход запрещён другим модом.");
            return false;
        }
        d.lastUsed = System.currentTimeMillis();
        SubworldRegistry.markDirty();
        return true;
    }

    /** Гость: войти в чужой локальный мир. */
    public static boolean visit(EntityPlayerMP player, SubworldDim d) {
        Subworld sw = SubworldRegistry.get(d.subworld);
        if (sw == null) {
            return false;
        }
        boolean admin = Perms.isAdmin(player);
        boolean member = SubworldTeams.isMember(player.getUniqueID(), d.owner);
        if (!member && d.guest(player.getUniqueID().toString()) == null && !admin) {
            ChatUtil.send(player, "&cТебя не приглашали в этот мир.");
            return false;
        }
        if (member && !admin) {
            // участник команды входит как в свой мир — со всеми условиями доступа
            SubworldAccess.Result access = SubworldAccess.check(player, sw);
            if (!access.ok) {
                ChatUtil.send(player, access.reason == null ? "&cНет доступа." : access.reason);
                return false;
            }
        } else if (!admin) {
            // гостю доступ не покупать, но подмир должен быть открыт и возраст — подходить
            if (!sw.enabled) {
                ChatUtil.send(player, "&cПодмир «" + sw.name + "&c» закрыт.");
                return false;
            }
            com.ecoserv.dates.AgeFilter age = com.ecoserv.dates.AgeFilter.parse(sw.age);
            if (age != null && !com.ecoserv.dates.DatesManager.checkAge(player.getUniqueID(), age)) {
                ChatUtil.send(player, "&cПодмир «" + sw.name + "&c» доступен по возрасту: &f" + age.describe());
                return false;
            }
        }
        return teleportTo(player, d, false, null);
    }

    // --- возврат --------------------------------------------------------------------

    private static NBTTagCompound persisted(EntityPlayerMP player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayerMP.PERSISTED_NBT_TAG, 10)) {
            data.setTag(EntityPlayerMP.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayerMP.PERSISTED_NBT_TAG);
    }

    /** Запомнить обычный мир, из которого игрок уходит в подмиры. */
    private static void rememberReturn(EntityPlayerMP player) {
        if (!SubworldRegistry.isOurs(player.dimension)) {
            persisted(player).setInteger(RETURN_TAG, player.dimension);
        }
    }

    /** Мир, куда вернуть игрока из подмира. */
    public static int returnDim(EntityPlayerMP player) {
        NBTTagCompound tag = persisted(player);
        int dim = tag.hasKey(RETURN_TAG) ? tag.getInteger(RETURN_TAG) : 0;
        return SubworldRegistry.isOurs(dim) || !DimensionManager.isDimensionRegistered(dim) ? 0 : dim;
    }

    /** Вернуться из подмира туда, откуда пришёл. */
    public static boolean leave(EntityPlayerMP player) {
        if (!SubworldRegistry.isOurs(player.dimension)) {
            ChatUtil.send(player, "&eТы не в подмире.");
            return false;
        }
        int dim = returnDim(player);
        WorldTeleport.Target t = WorldTeleport.pickTarget(player, dim, false);
        if (t == null) {
            ChatUtil.send(player, "&cНе удалось вернуться.");
            return false;
        }
        WorldTeleport.rememberPosition(player);
        RegionManager.suppress(player, 1500L);
        return WorldTeleport.teleport(player, t);
    }

    /** Выгнать игрока из измерения (удаление мира, снятие гостя). */
    public static void evict(EntityPlayerMP player, String message) {
        int dim = returnDim(player);
        WorldTeleport.Target t = WorldTeleport.pickTarget(player, dim, false);
        if (t == null) {
            t = WorldTeleport.pickTarget(player, 0, true);
        }
        if (t != null) {
            RegionManager.suppress(player, 1500L);
            WorldTeleport.teleport(player, t);
        }
        if (message != null && !message.isEmpty()) {
            ChatUtil.send(player, message);
        }
    }

    // --- точка появления --------------------------------------------------------------

    /** Точка у спавна мира; если стоять негде (пустота) — строим площадку. */
    public static WorldTeleport.Target spawnTarget(EntityPlayerMP player, WorldServer world) {
        BlockPos feet = safeSpawn(world);
        return new WorldTeleport.Target(world.provider.getDimension(), feet.getX() + 0.5D, feet.getY(),
                feet.getZ() + 0.5D, player.rotationYaw, 0.0F);
    }

    /** Безопасные «ноги» у спавна мира (для входа и возрождения). */
    public static BlockPos safeSpawn(WorldServer world) {
        BlockPos spawn = world.getSpawnPoint();
        if (!SubworldRegistry.isOurs(world.provider.getDimension())) {
            // обычный край: точка появления — обсидиановая площадка, а не координаты спавна основного мира
            BlockPos special = world.provider.getSpawnCoordinate();
            if (special != null) {
                spawn = special;
            }
        }
        int radius = Math.max(4, Math.min(EcoConfig.worldSearchRadius, 24));
        BlockPos safe = WorldTeleport.findSafe(world, spawn, radius, -1);
        if (safe == null && spawn.getY() > 0) {
            safe = WorldTeleport.findSafe(world, spawn, radius, spawn.getY());
        }
        if (safe != null && inside(world, safe)) {
            return safe;
        }
        return platform(world, spawn);
    }

    private static boolean inside(WorldServer world, BlockPos pos) {
        WorldBorder b = world.getWorldBorder();
        return b.contains(pos);
    }

    /** Площадка 3x3 под точкой спавна (пустота, свой мир без земли у спавна). */
    public static BlockPos platform(WorldServer world, BlockPos around) {
        int y = around.getY();
        if (y < 2 || y > world.getActualHeight() - 3) {
            y = 64;
        }
        BlockPos feet = new BlockPos(around.getX(), y, around.getZ());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos floor = feet.add(dx, -1, dz);
                if (!world.getBlockState(floor).isSideSolid(world, floor, EnumFacing.UP)) {
                    world.setBlockState(floor, Blocks.STONE.getDefaultState());
                }
                world.setBlockToAir(feet.add(dx, 0, dz));
                world.setBlockToAir(feet.add(dx, 1, dz));
            }
        }
        return feet;
    }

    /**
     * При первом входе в мир ищем нормальную точку появления (как у ванили — на поверхности у 0,0),
     * чтобы спавн не оказался в скале или над океаном.
     */
    public static void ensureSpawn(WorldServer world, SubworldDim d) {
        if (d.spawnChecked) {
            return;
        }
        d.spawnChecked = true;
        SubworldRegistry.markDirty();
        Subworld sw = SubworldRegistry.get(d.subworld);
        if (sw == null || d.isInstance()) {
            // локальный мир взял спавн у шаблона
            return;
        }
        try {
            BlockPos spawn = world.getSpawnPoint();
            BlockPos found = null;
            if (Subworld.NORMAL.equals(sw.type) || Subworld.FLAT.equals(sw.type)) {
                found = WorldTeleport.findSafe(world, new BlockPos(spawn.getX(), 0, spawn.getZ()), 48, -1);
            } else if (Subworld.NETHER.equals(sw.type)) {
                found = WorldTeleport.findSafe(world, new BlockPos(spawn.getX(), 64, spawn.getZ()), 32, -1);
            } else if (Subworld.END.equals(sw.type)) {
                found = new BlockPos(100, 50, 0);
                buildEndPlatform(world, found);
            }
            if (found != null) {
                world.setSpawnPoint(found);
                SubworldInfo.save(world, d);
            }
        } catch (Exception e) {
            EcoServ.LOG.warn("Не удалось найти точку появления в измерении {}: {}", d.dim, e.toString());
        }
    }

    private static void buildEndPlatform(WorldServer world, BlockPos spawn) {
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
