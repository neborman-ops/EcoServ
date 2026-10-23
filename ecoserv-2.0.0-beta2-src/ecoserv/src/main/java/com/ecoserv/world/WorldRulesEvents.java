package com.ecoserv.world;

import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.Clone;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Правила миров, которые работают через события: доп. правила (без голода, полёт, без урона, защита
 * спавна, эффекты) для подмиров и обычных миров, а для обычных миров — своё сохранение инвентаря и мобы.
 */
public class WorldRulesEvents {

    /** Полёт выдан модом (чтобы забрать при выходе из мира). */
    private static final String FLY_TAG = "ecoserv_world_fly";

    /**
     * Что сделать с вещами после смерти — в сохраняемых данных игрока: если он выйдет из игры на экране
     * смерти, вещи вернутся после возрождения при следующем входе.
     */
    private static final String DEATH_TAG = "ecoserv_death_keep";

    private final Map<UUID, Long> notice = new HashMap<UUID, Long>();
    private int tick;

    // --- где какие правила ------------------------------------------------------------------------

    /** Доп. правила мира (подмира или обычного); null — не настраивали. */
    @Nullable
    public static WorldExtras extrasFor(int dim) {
        Subworld sw = SubworldRegistry.subworldOf(dim);
        if (sw != null) {
            return sw.rules.extras;
        }
        WorldSettings.Settings s = WorldSettings.peek(dim);
        return s == null ? null : s.extras;
    }

    /** Сохраняются ли вещи при смерти в этом мире. */
    public static boolean keepInventory(World world) {
        int dim = world.provider.getDimension();
        if (!SubworldRegistry.isOurs(dim)) {
            WorldSettings.Settings s = WorldSettings.peek(dim);
            if (s != null && WorldSettings.ON.equals(s.keepInventory)) {
                return true;
            }
            if (s != null && WorldSettings.OFF.equals(s.keepInventory)) {
                return false;
            }
        }
        return world.getGameRules().getBoolean("keepInventory");
    }

    // --- раз в секунду -----------------------------------------------------------------------------

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 20 != 0) {
            return;
        }
        net.minecraft.server.MinecraftServer server = com.ecoserv.util.PlayerUtil.server();
        if (server == null) {
            return;
        }
        boolean effectsNow = tick % 100 == 0;
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            WorldExtras x = extrasFor(p.dimension);
            fly(p, x != null && x.allowFly);
            if (x == null) {
                continue;
            }
            if (x.noHunger && p.getFoodStats().needFood()) {
                p.getFoodStats().addStats(20, 1.0F);
            }
            if (effectsNow && x.effects != null) {
                for (String e : x.effects) {
                    PotionEffect effect = effect(e);
                    if (effect != null) {
                        p.addPotionEffect(effect);
                    }
                }
            }
        }
    }

    /** «minecraft:speed:1» → эффект на 15 секунд (обновляется каждые 5). */
    @Nullable
    public static PotionEffect effect(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        int amp = 0;
        int last = t.lastIndexOf(':');
        if (last > 0 && t.indexOf(':') != last) {
            try {
                amp = Integer.parseInt(t.substring(last + 1));
                t = t.substring(0, last);
            } catch (NumberFormatException ignored) {
                // без уровня
            }
        } else if (last > 0 && t.indexOf(':') == last) {
            // «speed:1» без modid
            try {
                amp = Integer.parseInt(t.substring(last + 1));
                t = t.substring(0, last);
            } catch (NumberFormatException ignored) {
                // это modid:name
            }
        }
        Potion potion = Potion.REGISTRY.getObject(new ResourceLocation(t));
        if (potion == null) {
            return null;
        }
        return new PotionEffect(potion, 300, Math.max(0, Math.min(9, amp)), true, false);
    }

    /** Полёт: выдать в мире с полётом, забрать, когда игрок ушёл (если выдавали мы). */
    private static void fly(EntityPlayerMP p, boolean allowed) {
        GameType gt = p.interactionManager.getGameType();
        if (gt == GameType.CREATIVE || gt == GameType.SPECTATOR) {
            p.getEntityData().removeTag(FLY_TAG);
            return;
        }
        boolean ours = p.getEntityData().getBoolean(FLY_TAG);
        if (allowed && !p.capabilities.allowFlying) {
            p.capabilities.allowFlying = true;
            p.getEntityData().setBoolean(FLY_TAG, true);
            p.sendPlayerAbilities();
        } else if (!allowed && ours) {
            p.getEntityData().removeTag(FLY_TAG);
            p.capabilities.allowFlying = false;
            if (p.capabilities.isFlying) {
                p.capabilities.isFlying = false;
                p.fallDistance = 0.0F;
            }
            p.sendPlayerAbilities();
        }
    }

    // --- урон ---------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onFall(LivingFallEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        WorldExtras x = extrasFor(event.getEntityLiving().dimension);
        if (x != null && (x.noFallDamage || x.noDamage)) {
            event.setDistance(0.0F);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        DamageSource src = event.getSource();
        if (src == DamageSource.OUT_OF_WORLD || src.canHarmInCreative()) {
            return;
        }
        WorldExtras x = extrasFor(event.getEntityLiving().dimension);
        if (x != null && x.noDamage) {
            event.setCanceled(true);
        }
    }

    // --- стройка ------------------------------------------------------------------------------------

    /** Нельзя ли игроку ломать и ставить здесь (стройка только админам или защита спавна). */
    private boolean locked(EntityPlayer player, BlockPos pos) {
        if (player == null || player.world.isRemote || player instanceof net.minecraftforge.common.util.FakePlayer
                || Perms.isAdmin(player)) {
            return false;
        }
        WorldExtras x = extrasFor(player.dimension);
        if (x == null) {
            return false;
        }
        String why = null;
        if (x.buildLocked) {
            why = "&cВ этом мире строят только админы.";
        } else if (x.protectRadius > 0) {
            // у края своя точка появления (площадка), у остальных и у подмиров — точка появления мира
            BlockPos spawn = SubworldRegistry.isOurs(player.dimension) ? null : player.world.provider.getSpawnCoordinate();
            if (spawn == null) {
                spawn = player.world.getSpawnPoint();
            }
            double dx = pos.getX() - spawn.getX();
            double dz = pos.getZ() - spawn.getZ();
            if (dx * dx + dz * dz <= (double) x.protectRadius * x.protectRadius) {
                why = "&cЗдесь защита спавна (" + x.protectRadius + " блоков).";
            }
        }
        if (why == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = notice.get(player.getUniqueID());
        if (last == null || now - last > 3000L) {
            notice.put(player.getUniqueID(), now);
            ChatUtil.actionBar(player, why);
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreak(BlockEvent.BreakEvent event) {
        // 1.8.0: палочка разрешения в зоне BLOCK DESTROY — ломать можно и в мире «строят только админы»
        com.ecoserv.zone.Zone permitZone = event.getPlayer() == null ? null
                : com.ecoserv.zone.ZoneManager.at(event.getPlayer().dimension, event.getPos());
        if (com.ecoserv.item.ItemPermitWand.permits(event.getPlayer(), permitZone)) {
            return;
        }
        if (locked(event.getPlayer(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (locked(event.getPlayer(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBucket(FillBucketEvent event) {
        BlockPos pos = event.getTarget() != null && event.getTarget().getBlockPos() != null
                ? event.getTarget().getBlockPos() : event.getEntityPlayer().getPosition();
        if (locked(event.getEntityPlayer(), pos)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onToss(ItemTossEvent event) {
        EntityPlayer player = event.getPlayer();
        if (player == null || player.world.isRemote || Perms.isAdmin(player)) {
            return;
        }
        WorldExtras x = extrasFor(player.dimension);
        if (x == null || !x.noItemDrop) {
            return;
        }
        event.setCanceled(true);
        ItemStack stack = event.getEntityItem().getItem().copy();
        if (!player.inventory.addItemStackToInventory(stack) && !stack.isEmpty()) {
            // места нет — всё же бросаем, чтобы не пропало
            player.dropItem(stack, false, false);
            return;
        }
        player.inventoryContainer.detectAndSendChanges();
        ChatUtil.actionBar(player, "&cВ этом мире нельзя выбрасывать предметы.");
    }

    // --- мобы обычных миров ----------------------------------------------------------------------------

    @SubscribeEvent
    public void onPotentialSpawns(WorldEvent.PotentialSpawns event) {
        World world = event.getWorld();
        if (world.isRemote) {
            return;
        }
        int dim = world.provider.getDimension();
        if (SubworldRegistry.isOurs(dim)) {
            return;
        }
        WorldSettings.Settings s = WorldSettings.peek(dim);
        if (s == null || "all".equals(s.mobs)) {
            return;
        }
        if ("none".equals(s.mobs) || event.getType() == EnumCreatureType.MONSTER) {
            event.setCanceled(true);
        }
    }

    // --- сохранение инвентаря по правилу мира ----------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
        if (player.isSpectator()) {
            return;
        }
        boolean rule = player.world.getGameRules().getBoolean("keepInventory");
        boolean keep = keepInventory(player.world);
        if (keep == rule) {
            persisted(player).removeTag(DEATH_TAG);
            return;
        }
        NBTTagCompound d = new NBTTagCompound();
        d.setBoolean("keep", keep);
        d.setInteger("level", player.experienceLevel);
        d.setInteger("total", player.experienceTotal);
        d.setFloat("progress", player.experience);
        if (keep) {
            // ваниль сейчас выбросит всё — запомним и вернём после возрождения
            d.setTag("inv", player.inventory.writeToNBT(new NBTTagList()));
        } else {
            // ваниль всё сохранит — выбрасываем сами, как при обычной смерти
            dropAll(player);
        }
        persisted(player).setTag(DEATH_TAG, d);
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    @Nullable
    private static NBTTagCompound death(EntityPlayer player) {
        NBTTagCompound p = persisted(player);
        return p.hasKey(DEATH_TAG, 10) ? p.getCompoundTag(DEATH_TAG) : null;
    }

    /** Выбросить всё, как ваниль при смерти (с событием для модов-гробов и хранилища ГЛИТЧ). */
    private static void dropAll(EntityPlayerMP player) {
        player.captureDrops = true;
        player.capturedDrops.clear();
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack s = player.inventory.getStackInSlot(i);
            if (!s.isEmpty() && EnchantmentHelper.hasVanishingCurse(s)) {
                player.inventory.removeStackFromSlot(i);
            }
        }
        player.inventory.dropAllItems();
        player.captureDrops = false;
        List<EntityItem> drops = new ArrayList<EntityItem>(player.capturedDrops);
        player.capturedDrops.clear();
        PlayerDropsEvent drop = new PlayerDropsEvent(player, DamageSource.GENERIC, drops, true);
        if (!net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(drop)) {
            for (EntityItem item : drops) {
                player.world.spawnEntity(item);
            }
        }
        int xp = Math.min(100, player.experienceLevel * 7);
        while (xp > 0) {
            int part = EntityXPOrb.getXPSplit(xp);
            xp -= part;
            player.world.spawnEntity(new EntityXPOrb(player.world, player.posX, player.posY, player.posZ, part));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDrops(PlayerDropsEvent event) {
        NBTTagCompound d = death(event.getEntityPlayer());
        if (d != null && d.getBoolean("keep") && d.hasKey("inv")) {
            // вещи вернутся после возрождения
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onXpDrop(LivingExperienceDropEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        NBTTagCompound d = death((EntityPlayer) event.getEntityLiving());
        if (d != null && d.getBoolean("keep")) {
            event.setCanceled(true);
        }
    }

    /**
     * Сохранение инвентаря — правило своего мира. Если игрок умер там, где вещи сохраняются, а возрождается
     * там, где нет (или наоборот), ваниль потеряла бы вещи или удвоила опыт — сверяем здесь. Действует и для
     * подмиров (у них свои правила игры), и для обычных миров с настройкой «Сохранение инвентаря».
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onClone(Clone event) {
        EntityPlayer old = event.getOriginal();
        EntityPlayer now = event.getEntityPlayer();
        NBTTagCompound d = death(old);
        // Forge копирует сохраняемые данные новому игроку — наша пометка там больше не нужна
        persisted(now).removeTag(DEATH_TAG);
        if (!event.isWasDeath() || now.world == null || old.isSpectator()) {
            return;
        }
        boolean copiedByVanilla = now.world.getGameRules().getBoolean("keepInventory");
        boolean keptAtDeath = d != null ? d.getBoolean("keep")
                : old.world != null && old.world.getGameRules().getBoolean("keepInventory");
        if (keptAtDeath) {
            if (d != null && d.hasKey("inv", 9)) {
                now.inventory.readFromNBT(d.getTagList("inv", 10));
            } else if (!copiedByVanilla) {
                now.inventory.copyInventory(old.inventory);
            }
            if (d != null) {
                now.experienceLevel = d.getInteger("level");
                now.experienceTotal = d.getInteger("total");
                now.experience = d.getFloat("progress");
            } else if (!copiedByVanilla) {
                now.experienceLevel = old.experienceLevel;
                now.experienceTotal = old.experienceTotal;
                now.experience = old.experience;
            }
            now.setScore(old.getScore());
        } else if (copiedByVanilla) {
            // вещи и опыт уже выпали там, где игрок умер
            now.inventory.clear();
            now.experienceLevel = 0;
            now.experienceTotal = 0;
            now.experience = 0.0F;
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        notice.remove(event.player.getUniqueID());
    }
}
