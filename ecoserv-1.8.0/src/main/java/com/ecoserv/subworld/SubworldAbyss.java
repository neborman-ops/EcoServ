package com.ecoserv.subworld;

import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketGlitchFx;
import com.ecoserv.network.packet.PacketSubworldClient;
import com.ecoserv.region.RegionManager;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldTeleport;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ГЛИТЧ-бездна (п. 21): игрок, упавший в бездну подмира с этой меткой, возвращается на спавн мира,
 * а у него на экране — глитч-текстуры по краям, фильтр эндермена и окно ENGINE ERROR с текстом
 * на его языке. Предметы, упавшие в бездну любого подмира, спасаются в хранилище ГЛИТЧ.
 */
public final class SubworldAbyss {

    /** Ниже этой высоты ванильная бездна уже наносит урон — срабатываем раньше. */
    private static final int VANILLA_VOID = -60;

    private static final Map<UUID, Long> COOLDOWN = new HashMap<UUID, Long>();

    private SubworldAbyss() {
    }

    private static int threshold(Subworld.Abyss a) {
        return Math.max(a.y, VANILLA_VOID);
    }

    /** Бездна мира: подмира или обычного мира (из его настроек); null — нет. */
    @javax.annotation.Nullable
    public static Subworld.Abyss abyssFor(int dim) {
        Subworld sw = SubworldRegistry.subworldOf(dim);
        if (sw != null) {
            return sw.abyss;
        }
        com.ecoserv.world.WorldSettings.Settings s = com.ecoserv.world.WorldSettings.peek(dim);
        return s == null ? null : s.abyss;
    }

    /** Каждый тик для игроков в мирах с бездной. */
    public static void tickPlayer(EntityPlayerMP player) {
        Subworld.Abyss a = abyssFor(player.dimension);
        if (a == null || !a.enabled || player.isSpectator() || player.posY >= threshold(a)) {
            return;
        }
        Long until = COOLDOWN.get(player.getUniqueID());
        long now = System.currentTimeMillis();
        if (until != null && now < until) {
            return;
        }
        COOLDOWN.put(player.getUniqueID(), now + 1500L);
        trigger(player);
    }

    /** Упал в бездну: глитч-всплеск там, где упал, возврат на спавн мира, всплеск там, где появился. */
    public static void trigger(EntityPlayerMP player) {
        Subworld.Abyss a = abyssFor(player.dimension);
        if (a == null) {
            return;
        }
        WorldServer world = player.getServerWorld();
        double fx = player.posX;
        double fz = player.posZ;
        if (a.particles) {
            // всплеск там, где игрок исчез (не глубже -8: дальше его скрывает туман бездны)
            burst(world, PacketGlitchFx.FALL, fx, Math.max(player.posY, -8.0D), fz, a.distort, player);
        }
        WorldTeleport.Target t = SubworldTeleport.spawnTarget(player, world);
        RegionManager.suppress(player, 1500L);
        WorldTeleport.teleport(player, t);
        player.fallDistance = 0.0F;
        player.extinguish();
        world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ENDERMEN_TELEPORT,
                SoundCategory.PLAYERS, 1.0F, 0.5F);
        if (!com.ecoserv.extra.ExtraConfig.arrivalProtectAbyss) {
            com.ecoserv.extra.ExtraEvents.unprotect(player);
        }
        if (a.particles) {
            // 1.8.0: искажение пространства и там, где игрок появился (видят окружающие)
            burst(world, PacketGlitchFx.ARRIVE, player.posX, player.posY, player.posZ, a.distort, player);
        }
        show(player, a);
    }

    /** Глитч-всплеск всем рядом (в радиусе 64 блоков), упавшему — со своей пометкой. */
    private static void burst(WorldServer world, int kind, double x, double y, double z, boolean distort,
                              EntityPlayerMP faller) {
        for (EntityPlayer p : world.playerEntities) {
            if (!(p instanceof EntityPlayerMP) || p.getDistanceSq(x, y, z) > 64.0D * 64.0D) {
                continue;
            }
            PacketHandler.sendTo(new PacketGlitchFx(kind, x, y, z, distort, p == faller), (EntityPlayerMP) p);
        }
    }

    /** Совместимость: эффекты по настройкам подмира. */
    public static void show(EntityPlayerMP player, Subworld sw) {
        show(player, sw.abyss);
    }

    /** Только эффекты (и кнопка «проверить на себе» у админа). */
    public static void show(EntityPlayerMP player, Subworld.Abyss a) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("wait", a.waitForOk);
        tag.setInteger("fade", a.fadeSeconds);
        tag.setInteger("effect", a.effectSeconds);
        tag.setBoolean("shader", a.shader);
        tag.setBoolean("overlay", a.overlay);
        NBTTagList texts = new NBTTagList();
        for (Subworld.LangText t : a.texts) {
            NBTTagCompound e = new NBTTagCompound();
            e.setString("lang", t.lang);
            e.setString("title", t.title);
            e.setString("text", t.text);
            e.setString("button", t.button);
            texts.appendTag(e);
        }
        tag.setTag("texts", texts);
        // окно ошибки заменит на клиенте открытый сундук или меню — закрываем его и на сервере,
        // иначе сервер считал бы контейнер открытым
        if (player.openContainer != player.inventoryContainer) {
            player.closeScreen();
        }
        PacketHandler.sendTo(new PacketSubworldClient(PacketSubworldClient.ABYSS, tag), player);
    }

    /** Игрок нажал ОК в окне. */
    public static void onOk(EntityPlayerMP player) {
        COOLDOWN.remove(player.getUniqueID());
    }

    public static void forget(UUID player) {
        COOLDOWN.remove(player);
    }

    /**
     * Дважды в секунду: предметы, упавшие в бездну подмира, — в хранилище ГЛИТЧ владельцу. Ниже нуля
     * блоков нет, а до гибели на -64 предмет летит меньше двух секунд — ловим сразу под нулём.
     */
    public static void rescueItems(WorldServer world, SubworldDim d, Subworld sw) {
        int limit = sw.abyss.enabled ? Math.max(threshold(sw.abyss), 0) : 0;
        List<EntityItem> fallen = new ArrayList<EntityItem>();
        for (Entity e : world.loadedEntityList) {
            if (e instanceof EntityItem && !e.isDead && e.posY < limit) {
                fallen.add((EntityItem) e);
            }
        }
        for (EntityItem item : fallen) {
            ItemStack stack = item.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            // спасаем только то, что выбросил игрок (добыча ферм и мобов сгорает в бездне, как обычно)
            String thrower = item.getThrower();
            if (thrower == null || thrower.isEmpty()) {
                continue;
            }
            // только игроки в сети: поиск по нику офлайн-игрока может уйти в сеть и подвесить сервер
            EntityPlayerMP owner = PlayerUtil.online(thrower);
            if (owner != null) {
                GlitchStorage.add("player:" + owner.getUniqueID(), GlitchStorage.VOID, stack, sw.name);
                item.setDead();
            }
        }
    }

    /** Смерть в мире с пометкой GLITCH: выпавшее — в хранилище ГЛИТЧ, а не на землю. */
    public static boolean captureDrops(EntityPlayer player, List<EntityItem> drops) {
        Subworld sw = SubworldRegistry.subworldOf(player.dimension);
        if (sw == null || !sw.glitch || drops.isEmpty()) {
            return false;
        }
        for (EntityItem item : drops) {
            GlitchStorage.add("player:" + player.getUniqueID(), GlitchStorage.DEATH, item.getItem(), sw.name);
        }
        drops.clear();
        return true;
    }
}
