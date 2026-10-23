package com.ecoserv.subworld;

import com.ecoserv.EcoServ;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketWorldBorder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldServerMulti;
import net.minecraft.world.border.IBorderListener;
import net.minecraft.world.border.WorldBorder;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import com.ecoserv.world.WorldTeleport;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * Барьер локальных миров (как в skyblock): у каждого измерения подмира своя граница мира, не связанная
 * с основным миром. Расширяется наградой квеста «Расширение мира».
 * <p>
 * Ванильный сервер шлёт игроку границу ОСНОВНОГО мира при каждом переходе, поэтому свою границу мы
 * досылаем после перехода, входа и возрождения.
 */
public final class SubworldBorder {

    /** «Бесконечная» граница, как у ванили. */
    public static final double NONE = 6.0E7D;

    private static Field multiListener;
    private static boolean overworldHooked;
    /** Когда поставлена досылка границы (0 — не стоит). Старше 2 с — задачу сбросили при старте сервера. */
    private static long resendQueuedAt;

    private SubworldBorder() {
    }

    /** Отвязать границу измерения от основного мира (вызывается из WorldEvent.Load). */
    public static void unlink(World world) {
        if (!(world instanceof WorldServerMulti)) {
            return;
        }
        World overworld = DimensionManager.getWorld(0);
        if (overworld == null) {
            return;
        }
        try {
            if (multiListener == null) {
                multiListener = ObfuscationReflectionHelper.findField(WorldServerMulti.class, "borderListener");
            }
            IBorderListener listener = (IBorderListener) multiListener.get(world);
            if (listener != null) {
                overworld.getWorldBorder().removeListener(listener);
            }
        } catch (Throwable t) {
            EcoServ.LOG.warn("Не удалось отвязать границу измерения {} от основного мира: {}",
                    world.provider.getDimension(), t.toString());
        }
    }

    /** Граница основного мира поменялась (её получили все игроки) — вернуть свою тем, кто в подмирах. */
    public static void hookOverworld() {
        World overworld = DimensionManager.getWorld(0);
        if (overworld == null || overworldHooked) {
            return;
        }
        overworldHooked = true;
        overworld.getWorldBorder().addListener(new IBorderListener() {
            @Override
            public void onSizeChanged(WorldBorder border, double newSize) {
                queueResend();
            }

            @Override
            public void onTransitionStarted(WorldBorder border, double oldSize, double newSize, long time) {
                queueResend();
            }

            @Override
            public void onCenterChanged(WorldBorder border, double x, double z) {
                queueResend();
            }

            @Override
            public void onWarningTimeChanged(WorldBorder border, int newTime) {
                queueResend();
            }

            @Override
            public void onWarningDistanceChanged(WorldBorder border, int newDistance) {
                queueResend();
            }

            @Override
            public void onDamageAmountChanged(WorldBorder border, double newAmount) {
            }

            @Override
            public void onDamageBufferChanged(WorldBorder border, double newSize) {
            }
        });
    }

    public static void onServerStopped() {
        overworldHooked = false;
        resendQueuedAt = 0L;
    }

    /**
     * Досылаем свою границу на следующем тике: ванильный обработчик, который рассылает границу основного
     * мира всем игрокам, подписан позже нас и сработал бы уже после нашей рассылки.
     */
    private static void queueResend() {
        long now = System.currentTimeMillis();
        if (resendQueuedAt != 0L && now - resendQueuedAt < 2000L) {
            return;
        }
        resendQueuedAt = now;
        com.ecoserv.server.ServerTasks.later(1, new Runnable() {
            @Override
            public void run() {
                resendQueuedAt = 0L;
                resendAll();
            }
        });
    }

    /** Поставить миру границу из настроек измерения подмира. */
    public static void apply(WorldServer world, SubworldDim d, boolean animate) {
        WorldBorder border = world.getWorldBorder();
        double target = d.isInstance() && d.border > 0 ? d.border * 2.0D : NONE;
        BlockPos spawn = world.getSpawnPoint();
        double cx = spawn.getX() + 0.5D;
        double cz = spawn.getZ() + 0.5D;
        if (target >= NONE) {
            cx = 0.0D;
            cz = 0.0D;
        }
        if (border.getCenterX() != cx || border.getCenterZ() != cz) {
            border.setCenter(cx, cz);
        }
        border.setWarningDistance(target >= NONE ? 5 : 2);
        border.setDamageBuffer(target >= NONE ? 5.0D : 1.0D);
        border.setDamageAmount(0.2D);
        double current = border.getDiameter();
        if (animate && target < NONE && current < NONE && current != target) {
            long ms = (long) Math.min(10000.0D, Math.max(1000.0D, Math.abs(target - current) * 100.0D));
            border.setTransition(current, target, ms);
        } else if (current != target || border.getTargetSize() != target) {
            border.setTransition(target);
        }
        for (EntityPlayer p : world.playerEntities) {
            if (p instanceof EntityPlayerMP) {
                send((EntityPlayerMP) p);
            }
        }
    }

    /** Отправить игроку границу его текущего мира. */
    public static void send(EntityPlayerMP player) {
        if (player.connection == null) {
            return;
        }
        player.connection.sendPacket(new SPacketWorldBorder(player.getServerWorld().getWorldBorder(),
                SPacketWorldBorder.Action.INITIALIZE));
    }

    /** Всем игрокам вне основного мира (подмиры и миры со своим барьером) — их границу. */
    public static void resendAll() {
        com.ecoserv.world.WorldBorders.resendAll();
    }

    /** Расширить барьер локального мира. Возвращает новый радиус или -1. */
    public static int expand(SubworldDim d, int amount) {
        Subworld sw = SubworldRegistry.get(d.subworld);
        if (sw == null || !d.isInstance() || d.border <= 0) {
            return -1;
        }
        int step = amount > 0 ? amount : sw.borderStep;
        int next = d.border + step;
        if (sw.borderMax > 0) {
            next = Math.min(next, sw.borderMax);
        }
        if (next == d.border) {
            return d.border;
        }
        d.border = next;
        SubworldRegistry.markDirty();
        WorldServer w = DimensionManager.getWorld(d.dim);
        if (w != null) {
            apply(w, d, true);
        }
        return next;
    }

    /** Точка внутри барьера (для телепортов); null — барьера нет. */
    @Nullable
    public static String outsideReason(int dim, double x, double z) {
        SubworldDim d = SubworldRegistry.dim(dim);
        if (d == null || !d.isInstance() || d.border <= 0) {
            return null;
        }
        WorldServer w = DimensionManager.getWorld(dim);
        if (w == null) {
            return null;
        }
        WorldBorder border = w.getWorldBorder();
        if (x < border.minX() || x > border.maxX() || z < border.minZ() || z > border.maxZ()) {
            return "&cЭта точка за барьером локального мира.";
        }
        return null;
    }

    /** Раз в секунду: игрока, оказавшегося за барьером (телепорт другим модом и т.п.), возвращаем. */
    public static void enforce(EntityPlayerMP player) {
        SubworldDim d = SubworldRegistry.dim(player.dimension);
        if (d == null || !d.isInstance() || d.border <= 0 || Perms.isAdmin(player)) {
            return;
        }
        WorldServer world = player.getServerWorld();
        WorldBorder border = world.getWorldBorder();
        double margin = 1.5D;
        if (player.posX >= border.minX() - margin && player.posX <= border.maxX() + margin
                && player.posZ >= border.minZ() - margin && player.posZ <= border.maxZ() + margin) {
            return;
        }
        double x = Math.max(border.minX() + 1.5D, Math.min(border.maxX() - 1.5D, player.posX));
        double z = Math.max(border.minZ() + 1.5D, Math.min(border.maxZ() - 1.5D, player.posZ));
        BlockPos near = WorldTeleport.findSafe(world, new BlockPos(x, player.posY, z), 3,
                player.posY > 1 && player.posY < 250 ? (int) player.posY : -1);
        BlockPos feet = near != null && border.contains(near) ? near : SubworldTeleport.safeSpawn(world);
        player.connection.setPlayerLocation(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D,
                player.rotationYaw, player.rotationPitch);
        player.fallDistance = 0.0F;
        ChatUtil.actionBar(player, "&cЗа барьер локального мира нельзя.");
    }
}
