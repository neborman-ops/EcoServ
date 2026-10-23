package com.ecoserv.zone;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.item.ItemZoneWand;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketParticles;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Защита зон палочек BLOCK DESTROY / BLOCK DESTROY DANGER и работа самих палочек. */
public class ZoneEvents {

    private static final Map<UUID, Long> LAST_WARN = new HashMap<UUID, Long>();
    private static final Map<UUID, Long> LAST_INFO = new HashMap<UUID, Long>();
    private int tick;

    // --- палочки -------------------------------------------------------------

    /** ПКМ палочкой по блоку: первый угол, потом второй — и зона создана. */
    public static void onWandClick(EntityPlayerMP player, ItemZoneWand wand, World world, BlockPos pos) {
        if (!Perms.isAdmin(player)) {
            ChatUtil.actionBar(player, "&cЭта палочка только для операторов");
            return;
        }
        int dim = world.provider.getDimension();
        UUID id = player.getUniqueID();
        ZoneManager.Selection selection = ZoneManager.selection(id);
        if (selection == null || selection.dim != dim || !selection.type.equals(wand.zoneType())) {
            ZoneManager.startSelection(id, dim, pos.toImmutable(), wand.zoneType());
            ChatUtil.send(player, "&6[" + wand.title() + "] &fТочка 1: &e" + str(pos)
                    + "&f. Теперь кликни ПКМ по второму углу зоны.");
            return;
        }
        Zone zone = ZoneManager.create(wand.zoneType(), dim, selection.first, pos.toImmutable(), player.getName());
        ZoneManager.clearSelection(id);
        ChatUtil.send(player, "&aЗона создана: &f" + zone.describe());
        ChatUtil.sendRaw(player, " &7Удалить: &f/zone remove " + zone.id
                + " &7| на всю высоту: &f/zone vert " + zone.id + " &7| все зоны: &f/zone list");
    }

    private static String str(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static boolean holdsWand(EntityPlayer player) {
        return player.getHeldItemMainhand().getItem() instanceof ItemZoneWand
                || player.getHeldItemOffhand().getItem() instanceof ItemZoneWand;
    }

    // --- защита --------------------------------------------------------------

    /** Строить в зоне может только оператор в креативе. Автоматика (FakePlayer) — никогда. */
    public static boolean canBuild(EntityPlayer player) {
        return player != null && !(player instanceof FakePlayer) && player.isCreative() && Perms.isAdmin(player);
    }

    private static Zone blocked(World world, BlockPos pos, EntityPlayer player) {
        if (!EcoConfig.zonesEnabled || ZoneManager.isEmpty() || world.isRemote || pos == null) {
            return null;
        }
        Zone zone = ZoneManager.at(world.provider.getDimension(), pos);
        if (zone == null || canBuild(player)) {
            return null;
        }
        return zone;
    }

    /**
     * То же, но с запасом вокруг зоны: огонь, лава, вода и поршни, поставленные рядом,
     * иначе затекли бы или въехали внутрь.
     */
    private static Zone blockedNear(World world, BlockPos pos, EntityPlayer player) {
        if (!EcoConfig.zonesEnabled || ZoneManager.isEmpty() || world.isRemote || pos == null) {
            return null;
        }
        Zone zone = ZoneManager.near(world.provider.getDimension(), pos, Math.max(0, EcoConfig.zonesMargin));
        if (zone == null || canBuild(player)) {
            return null;
        }
        return zone;
    }

    private static boolean isPiston(IBlockState state) {
        return state != null && state.getBlock() instanceof BlockPistonBase;
    }

    /** DANGER-зона предупреждает (не чаще раза в секунду), обычная молчит. */
    private static void warn(EntityPlayer player, Zone zone) {
        if (player == null || !zone.isDanger() || !(player instanceof EntityPlayerMP) || player instanceof FakePlayer) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_WARN.get(player.getUniqueID());
        if (last != null && now - last < 1000L) {
            return;
        }
        LAST_WARN.put(player.getUniqueID(), now);
        ChatUtil.actionBar(player, EcoConfig.zonesDangerMessage);
        ((EntityPlayerMP) player).connection.sendPacket(new SPacketSoundEffect(SoundEvents.BLOCK_NOTE_BASS,
                SoundCategory.BLOCKS, player.posX, player.posY, player.posZ, 0.8F, 0.6F));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        Zone zone = blocked(event.getWorld(), event.getPos(), event.getPlayer());
        if (zone != null && com.ecoserv.item.ItemPermitWand.permits(event.getPlayer(), zone)) {
            return; // 1.8.0: палочка разрешения
        }
        if (zone != null) {
            event.setCanceled(true);
            warn(event.getPlayer(), zone);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        Zone zone = isPiston(event.getPlacedBlock())
                ? blockedNear(event.getWorld(), event.getPos(), event.getPlayer())
                : blocked(event.getWorld(), event.getPos(), event.getPlayer());
        if (zone == null && event instanceof BlockEvent.MultiPlaceEvent) {
            List<BlockSnapshot> parts = ((BlockEvent.MultiPlaceEvent) event).getReplacedBlockSnapshots();
            for (int i = 0; i < parts.size() && zone == null; i++) {
                zone = blocked(event.getWorld(), parts.get(i).getPos(), event.getPlayer());
            }
        }
        if (zone != null) {
            event.setCanceled(true);
            warn(event.getPlayer(), zone);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        if (event.getWorld().isRemote) {
            return;
        }
        if (event.getItemStack().getItem() instanceof ItemZoneWand) {
            // ЛКМ палочкой — показать, какая здесь зона; ломать палочкой нельзя
            event.setCanceled(true);
            showInfo(player, event.getWorld(), event.getPos());
            return;
        }
        Zone zone = blocked(event.getWorld(), event.getPos(), player);
        if (zone != null && com.ecoserv.item.ItemPermitWand.permits(player, zone)) {
            return;
        }
        if (zone != null) {
            event.setCanceled(true);
            warn(player, zone);
        }
    }

    private static void showInfo(EntityPlayer player, World world, BlockPos pos) {
        long now = System.currentTimeMillis();
        Long last = LAST_INFO.get(player.getUniqueID());
        if (last != null && now - last < 500L) {
            return;
        }
        LAST_INFO.put(player.getUniqueID(), now);
        Zone zone = ZoneManager.at(world.provider.getDimension(), pos);
        if (zone == null) {
            ChatUtil.actionBar(player, "&7Здесь нет защищённой зоны (" + str(pos) + ")");
        } else {
            ChatUtil.send(player, "&6Здесь зона: &f" + zone.describe());
        }
    }

    /** Огниво, огненный шар и вёдра в зоне и рядом с ней — тоже «ставить». */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Item item = event.getItemStack().getItem();
        boolean igniter = item == Items.FLINT_AND_STEEL || item == Items.FIRE_CHARGE;
        if (!igniter && !(item instanceof ItemBucket)) {
            return;
        }
        BlockPos target = event.getFace() == null ? event.getPos() : event.getPos().offset(event.getFace());
        Zone zone = blockedNear(event.getWorld(), event.getPos(), event.getEntityPlayer());
        if (zone == null) {
            zone = blockedNear(event.getWorld(), target, event.getEntityPlayer());
        }
        if (zone != null) {
            // дверь или сундук открыть можно, а поджечь или вылить — нет
            event.setUseItem(Event.Result.DENY);
            if (igniter) {
                // TNT поджигается самим блоком при клике огнивом — блок тоже не трогаем
                event.setUseBlock(Event.Result.DENY);
            }
            warn(event.getEntityPlayer(), zone);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBucket(FillBucketEvent event) {
        RayTraceResult hit = event.getTarget();
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        Zone zone = blockedNear(event.getWorld(), pos, event.getEntityPlayer());
        if (zone == null && hit.sideHit != null) {
            zone = blockedNear(event.getWorld(), pos.offset(hit.sideHit), event.getEntityPlayer());
        }
        if (zone != null) {
            event.setCanceled(true);
            warn(event.getEntityPlayer(), zone);
        }
    }

    /** Грядки в зоне не вытаптываются (ни игроками, ни мобами). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!EcoConfig.zonesEnabled || ZoneManager.isEmpty() || event.getWorld().isRemote) {
            return;
        }
        if (ZoneManager.at(event.getWorld().provider.getDimension(), event.getPos()) == null) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof EntityPlayer && canBuild((EntityPlayer) entity)) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (!EcoConfig.zonesEnabled || !EcoConfig.zonesProtectExplosions || ZoneManager.isEmpty()
                || event.getWorld().isRemote) {
            return;
        }
        int dim = event.getWorld().provider.getDimension();
        List<BlockPos> blocks = event.getAffectedBlocks();
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if (ZoneManager.at(dim, blocks.get(i)) != null) {
                blocks.remove(i);
            }
        }
    }

    // --- показ границ частицами ---------------------------------------------

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !EcoConfig.zonesEnabled || !EcoConfig.zonesParticles) {
            return;
        }
        if (++tick % 20 != 0) {
            return;
        }
        List<EntityPlayerMP> players = PlayerUtil.allOnline();
        for (int i = 0; i < players.size(); i++) {
            EntityPlayerMP player = players.get(i);
            if (holdsWand(player) && Perms.isAdmin(player)) {
                drawFor(player);
            }
        }
    }

    private static void drawFor(EntityPlayerMP player) {
        WorldServer world = player.getServerWorld();
        int dim = player.dimension;
        int budget = 900;
        List<Zone> zones = ZoneManager.all();
        for (int i = 0; i < zones.size() && budget > 0; i++) {
            Zone zone = zones.get(i);
            if (zone.dim != dim || zone.distanceTo(player.posX, player.posZ) > 64.0D) {
                continue;
            }
            float r = zone.isDanger() ? 1.0F : 0.001F;
            float g = zone.isDanger() ? 0.15F : 0.6F;
            float b = zone.isDanger() ? 0.15F : 1.0F;
            budget -= drawBox(world, player, zone.minX, zone.minY, zone.minZ,
                    zone.maxX + 1, zone.maxY + 1, zone.maxZ + 1, r, g, b, budget);
        }
        ZoneManager.Selection selection = ZoneManager.selection(player.getUniqueID());
        if (selection != null && selection.dim == dim) {
            BlockPos p = selection.first;
            drawBox(world, player, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1,
                    1.0F, 1.0F, 0.001F, 60);
        }
    }

    /** 12 рёбер коробки цветной пылью. Возвращает, сколько частиц потрачено. */
    private static int drawBox(WorldServer world, EntityPlayerMP player, double x1, double y1, double z1,
                               double x2, double y2, double z2, float r, float g, float b, int budget) {
        double longest = Math.max(x2 - x1, Math.max(y2 - y1, z2 - z1));
        double step = Math.max(0.5D, longest / 40.0D);
        double[][] edges = {
                {x1, y1, z1, x2, y1, z1}, {x1, y1, z2, x2, y1, z2}, {x1, y2, z1, x2, y2, z1}, {x1, y2, z2, x2, y2, z2},
                {x1, y1, z1, x1, y2, z1}, {x2, y1, z1, x2, y2, z1}, {x1, y1, z2, x1, y2, z2}, {x2, y1, z2, x2, y2, z2},
                {x1, y1, z1, x1, y1, z2}, {x2, y1, z1, x2, y1, z2}, {x1, y2, z1, x1, y2, z2}, {x2, y2, z1, x2, y2, z2}
        };
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
                        (float) x, (float) y, (float) z, r, g, b, 1.0F, 0));
                used++;
            }
        }
        return used;
    }
}
