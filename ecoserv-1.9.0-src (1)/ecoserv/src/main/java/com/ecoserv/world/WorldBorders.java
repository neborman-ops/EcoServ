package com.ecoserv.world;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketWorldBorder;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldServerMulti;
import net.minecraft.world.border.IBorderListener;
import net.minecraft.world.border.WorldBorder;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Свой барьер у каждого мира. Ваниль держит один барьер (основного мира): ад, край и миры модов его
 * повторяют, а /worldborder меняет его для всех. Здесь каждый мир отвязывается от основного, его барьер
 * хранится в world_settings.json, рассылается только игрокам этого мира, а /worldborder меняет барьер
 * того мира, где стоит игрок. Выключается настройкой worlds.separate_borders.
 */
public final class WorldBorders {

    private static Field multiListener;
    /** Миры, на чей барьер уже повешен наш слушатель. */
    private static final Set<WorldBorder> HOOKED = Collections.newSetFromMap(new WeakHashMap<WorldBorder, Boolean>());

    private WorldBorders() {
    }

    public static boolean separate() {
        return EcoConfig.worldsSeparateBorders;
    }

    /** Отвязать барьер измерения от основного мира. */
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
            EcoServ.LOG.warn("Не удалось отвязать барьер измерения {} от основного мира: {}",
                    world.provider.getDimension(), t.toString());
        }
    }

    /** Мир загрузился (из WorldEvent.Load): обычный мир (не основной и не подмир). */
    public static void onLoad(WorldServer world) {
        int dim = world.provider.getDimension();
        if (dim == 0 || SubworldRegistry.isOurs(dim)) {
            return;
        }
        if (!separate()) {
            return;
        }
        unlink(world);
        WorldSettings.Settings s = WorldSettings.peek(dim);
        if (s != null && s.border != null) {
            applyData(world.getWorldBorder(), s.border);
        }
        hook(world);
    }

    /** Слушатель барьера мира: изменения — игрокам этого мира и в файл настроек. */
    public static void hook(final WorldServer world) {
        WorldBorder border = world.getWorldBorder();
        if (HOOKED.contains(border)) {
            return;
        }
        HOOKED.add(border);
        final int dim = world.provider.getDimension();
        border.addListener(new IBorderListener() {
            @Override
            public void onSizeChanged(WorldBorder b, double newSize) {
                broadcast(world, new SPacketWorldBorder(b, SPacketWorldBorder.Action.SET_SIZE));
                remember(dim, b);
            }

            @Override
            public void onTransitionStarted(WorldBorder b, double oldSize, double newSize, long time) {
                broadcast(world, new SPacketWorldBorder(b, SPacketWorldBorder.Action.LERP_SIZE));
                remember(dim, b);
            }

            @Override
            public void onCenterChanged(WorldBorder b, double x, double z) {
                broadcast(world, new SPacketWorldBorder(b, SPacketWorldBorder.Action.SET_CENTER));
                remember(dim, b);
            }

            @Override
            public void onWarningTimeChanged(WorldBorder b, int newTime) {
                broadcast(world, new SPacketWorldBorder(b, SPacketWorldBorder.Action.SET_WARNING_TIME));
                remember(dim, b);
            }

            @Override
            public void onWarningDistanceChanged(WorldBorder b, int newDistance) {
                broadcast(world, new SPacketWorldBorder(b, SPacketWorldBorder.Action.SET_WARNING_BLOCKS));
                remember(dim, b);
            }

            @Override
            public void onDamageAmountChanged(WorldBorder b, double newAmount) {
                remember(dim, b);
            }

            @Override
            public void onDamageBufferChanged(WorldBorder b, double newSize) {
                remember(dim, b);
            }
        });
    }

    private static void broadcast(WorldServer world, SPacketWorldBorder packet) {
        for (EntityPlayer p : world.playerEntities) {
            if (p instanceof EntityPlayerMP && ((EntityPlayerMP) p).connection != null) {
                ((EntityPlayerMP) p).connection.sendPacket(packet);
            }
        }
    }

    /** Барьер обычного мира поменялся: записать (барьеры подмиров ведут их настройки). */
    private static void remember(int dim, WorldBorder b) {
        if (dim == 0 || SubworldRegistry.isOurs(dim)) {
            return;
        }
        WorldSettings.Settings s = WorldSettings.get(dim);
        if (s.border == null) {
            s.border = new WorldSettings.BorderData();
        }
        s.border.centerX = b.getCenterX();
        s.border.centerZ = b.getCenterZ();
        s.border.size = b.getTargetSize();
        s.border.damage = b.getDamageAmount();
        s.border.buffer = b.getDamageBuffer();
        s.border.warningTime = b.getWarningTime();
        s.border.warningDistance = b.getWarningDistance();
        WorldSettings.markDirty();
    }

    public static void applyData(WorldBorder border, WorldSettings.BorderData d) {
        border.setCenter(d.centerX, d.centerZ);
        border.setDamageAmount(d.damage);
        border.setDamageBuffer(d.buffer);
        border.setWarningTime(d.warningTime);
        border.setWarningDistance(d.warningDistance);
        border.setTransition(Math.max(1.0D, Math.min(6.0E7D, d.size)));
    }

    /** Отправить игроку барьер его мира. */
    public static void send(EntityPlayerMP player) {
        if (player.connection == null) {
            return;
        }
        player.connection.sendPacket(new SPacketWorldBorder(player.getServerWorld().getWorldBorder(),
                SPacketWorldBorder.Action.INITIALIZE));
    }

    /** Нужно ли досылать игроку свой барьер (ваниль прислала барьер основного мира). */
    public static boolean ownBorder(int dim) {
        if (dim == 0) {
            return false;
        }
        return SubworldRegistry.isOurs(dim) || separate();
    }

    /** Всем игрокам вне основного мира — барьер их мира (после изменения барьера основного мира). */
    public static void resendAll() {
        net.minecraft.server.MinecraftServer server = com.ecoserv.util.PlayerUtil.server();
        if (server == null) {
            return;
        }
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            if (ownBorder(p.dimension)) {
                send(p);
            }
        }
    }

    /** Поставить барьер миру (окно настроек): радиус в блоках, 0 — без барьера. */
    public static void setRadius(WorldServer world, double centerX, double centerZ, int radius) {
        WorldBorder b = world.getWorldBorder();
        b.setCenter(centerX, centerZ);
        b.setTransition(radius <= 0 ? 6.0E7D : radius * 2.0D);
        int dim = world.provider.getDimension();
        if (dim != 0) {
            for (EntityPlayer p : world.playerEntities) {
                if (p instanceof EntityPlayerMP) {
                    send((EntityPlayerMP) p);
                }
            }
        }
    }

    /** Для подмира: барьер локального мира хранится в его настройках. */
    public static void rememberSubworld(int dim, WorldBorder b) {
        SubworldDim d = SubworldRegistry.dim(dim);
        if (d == null || !d.isInstance()) {
            return;
        }
        double size = b.getTargetSize();
        int radius = size >= 2.9E7D ? 0 : (int) Math.round(size / 2.0D);
        if (radius != d.border) {
            d.border = radius;
            SubworldRegistry.markDirty();
        }
    }
}
