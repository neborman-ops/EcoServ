package com.ecoserv.subworld;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** События подмиров: синхронизация измерений, граница, правила, защита локальных миров, выгрузка. */
public class SubworldEvents {

    private int tick;
    private final Map<Integer, Integer> emptySeconds = new HashMap<Integer, Integer>();
    private final Map<UUID, Long> denyNotice = new HashMap<UUID, Long>();

    // --- сеть -----------------------------------------------------------------------------

    /** Клиент подключается: до пакета входа в мир он должен знать наши измерения (иначе вылет). */
    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ServerConnectionFromClientEvent event) {
        try {
            event.getManager().sendPacket(PacketHandler.raw(SubworldDims.fullSync(), PacketHandler.ID_SUBWORLD_SYNC));
        } catch (Throwable t) {
            EcoServ.LOG.error("Не удалось отправить клиенту список измерений подмиров", t);
        }
    }

    @SubscribeEvent
    public void onDisconnectFromServer(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        EcoServ.PROXY.onClientDisconnect();
    }

    // --- миры -------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote || !(world instanceof WorldServer)) {
            return;
        }
        int dim = world.provider.getDimension();
        if (dim == 0) {
            SubworldBorder.hookOverworld();
            return;
        }
        // мир загрузили, пока копируются его файлы (установка, публикация)? — отметить
        SubworldManager.onWorldLoaded(dim);
        SubworldDim d = SubworldRegistry.dim(dim);
        if (d == null) {
            // обычный мир: свой барьер (если включено «у каждого мира свой барьер»)
            com.ecoserv.world.WorldBorders.onLoad((WorldServer) world);
            return;
        }
        WorldServer ws = (WorldServer) world;
        SubworldBorder.unlink(ws);
        Subworld sw = SubworldRegistry.get(d.subworld);
        if (sw != null && SubworldRules.ownInfo(ws)) {
            SubworldInfo.applyRules(ws.getWorldInfo(), sw.rules);
        }
        SubworldBorder.apply(ws, d, false);
        emptySeconds.remove(dim);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (world.isRemote) {
            return;
        }
        int dim = world.provider.getDimension();
        SubworldDim d = SubworldRegistry.dim(dim);
        if (d != null && world instanceof WorldServer && !((WorldServer) world).disableLevelSaving
                && SubworldRules.ownInfo((WorldServer) world)) {
            // при выгрузке ваниль сохраняет только чанки: время, погода, правила и спавн — наши
            SubworldInfo.save(world, d);
        }
        emptySeconds.remove(dim);
        TemplateEdits.onWorldUnload(dim);
        SubworldManager.onWorldUnloaded(dim);
    }

    // --- игроки ------------------------------------------------------------------------------

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (com.ecoserv.world.WorldBorders.ownBorder(event.toDim)) {
            // ваниль при переходе прислала барьер основного мира
            com.ecoserv.world.WorldBorders.send(player);
        }
        SubworldRules.onTransfer(player, event.fromDim, event.toDim);
        SubworldShops.onTransfer(player, event.fromDim, event.toDim);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (Perms.isAdmin(player)) {
            if (SubworldRegistry.loadFailed()) {
                ChatUtil.send(player, SubworldRegistry.BROKEN_NOTICE);
            }
            String blocked = SubworldManager.blockedNotice();
            if (blocked != null) {
                ChatUtil.send(player, blocked);
            }
        }
        SubworldDim here = SubworldRegistry.dim(player.dimension);
        if (here != null && SubworldManager.isBusy(here.subworld)) {
            // вышел из игры в мире подмира, который сейчас готовится, и зашёл снова
            ChatUtil.send(player, SubworldManager.hasPurgeMark(here)
                    ? "&eВ этот мир ещё устанавливается загруженный мир — всё, что ты здесь сделаешь, сотрётся. "
                    + "Выйди: &f/world leave"
                    : "&eЭтот шаблон сейчас публикуется — из-за того, что ты его загрузил, публикация отменится. "
                    + "Опубликуй ещё раз, когда она закончится.");
        }
        // игрок мог выйти в подмире, который потом удалили: вернуть прежний режим игры
        SubworldRules.reconcile(player);
        if (com.ecoserv.world.WorldBorders.ownBorder(player.dimension)) {
            com.ecoserv.world.WorldBorders.send(player);
        }
        SubworldDim d = SubworldRegistry.dim(player.dimension);
        if (d == null) {
            return;
        }
        if (d.isInstance() && !Perms.isAdmin(player) && !SubworldTeams.isMember(player.getUniqueID(), d.owner)
                && d.guest(player.getUniqueID().toString()) == null) {
            // пока игрок был не в сети, его убрали из команды или гостей
            final EntityPlayerMP p = player;
            com.ecoserv.server.ServerTasks.later(20, new Runnable() {
                @Override
                public void run() {
                    if (p.connection != null && SubworldRegistry.dim(p.dimension) != null) {
                        SubworldTeleport.evict(p, "&eТы больше не участник этого локального мира.");
                    }
                }
            });
        }
    }

    /** Возрождение в другом мире не считается «переходом»: режим, прайс и границу сверяем здесь. */
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (com.ecoserv.world.WorldBorders.ownBorder(player.dimension)) {
            com.ecoserv.world.WorldBorders.send(player);
        }
        SubworldRules.reconcile(player);
        SubworldShops.sync(player);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        denyNotice.remove(event.player.getUniqueID());
        UploadReceiver.onLogout(event.player.getUniqueID());
        SubworldAbyss.forget(event.player.getUniqueID());
        SubworldShops.forget(event.player.getUniqueID());
    }

    /** Смерть в ГЛИТЧ-мире: выпавшее — в хранилище ГЛИТЧ. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayerMP && !event.getEntityLiving().world.isRemote) {
            SubworldAbyss.captureDrops((EntityPlayer) event.getEntityLiving(), event.getDrops());
        }
    }

    /** Урон бездны в подмире с ГЛИТЧ-бездной — вместо него возврат на спавн. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getSource() != net.minecraft.util.DamageSource.OUT_OF_WORLD
                || !(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
        Subworld.Abyss abyss = SubworldAbyss.abyssFor(player.dimension);
        if (abyss != null && abyss.enabled && player.posY < 0 && !player.isSpectator()) {
            event.setCanceled(true);
            SubworldAbyss.trigger(player);
        }
    }

    // --- PvP и защита локальных миров ------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP victim = (EntityPlayerMP) event.getEntityLiving();
        Entity src = event.getSource().getTrueSource();
        if (src instanceof EntityPlayer && src != victim && !SubworldRules.pvpAllowed(victim.dimension)) {
            event.setCanceled(true);
        }
    }

    /** Строить и ломать в локальном мире: участники владельца, гости с правом стройки и админы. */
    public static boolean canBuild(EntityPlayer player, int dim) {
        SubworldDim d = SubworldRegistry.dim(dim);
        if (d == null || !d.isInstance() || player instanceof net.minecraftforge.common.util.FakePlayer) {
            // машины модов (ломатели, установщики) работают от «ненастоящего» игрока — их не трогаем
            return true;
        }
        if (Perms.isAdmin(player) || SubworldTeams.isMember(player.getUniqueID(), d.owner)) {
            return true;
        }
        SubworldDim.Guest g = d.guest(player.getUniqueID().toString());
        return g != null && g.build;
    }

    private boolean deny(EntityPlayer player) {
        if (player == null || player.world.isRemote || canBuild(player, player.dimension)) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = denyNotice.get(player.getUniqueID());
        if (last == null || now - last > 3000L) {
            denyNotice.put(player.getUniqueID(), now);
            ChatUtil.actionBar(player, "&cТы в гостях: строить здесь нельзя.");
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (deny(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (deny(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote || canBuild(player, player.dimension)) {
            return;
        }
        // предметы на блоки (огниво, костная мука, вёдра, блоки) — нельзя
        event.setUseItem(Event.Result.DENY);
        // двери, кнопки и рычаги — можно; сундуки и машины (блоки с содержимым) — нет
        TileEntity te = player.world.getTileEntity(event.getPos());
        if (te != null) {
            deny(player);
            event.setUseBlock(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBucket(FillBucketEvent event) {
        if (deny(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof EntityPlayer) && deny(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof EntityPlayer) && deny(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteractEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof EntityPlayer) && deny(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    // --- раз в секунду -----------------------------------------------------------------------------

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // ГЛИТЧ-бездна проверяется каждый тик: игрок не должен успеть получить урон бездны
        net.minecraft.server.MinecraftServer server = com.ecoserv.util.PlayerUtil.server();
        if (server != null) {
            for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
                SubworldAbyss.tickPlayer(p);
            }
        }
        ++tick;
        if (tick % 10 == 0) {
            // предметы в бездне: ниже нуля они летят быстро — проверяем дважды в секунду
            try {
                for (SubworldDim d : SubworldRegistry.dims()) {
                    WorldServer w = DimensionManager.getWorld(d.dim);
                    Subworld sw = w == null ? null : SubworldRegistry.get(d.subworld);
                    if (sw != null) {
                        SubworldAbyss.rescueItems(w, d, sw);
                    }
                }
            } catch (Throwable t) {
                EcoServ.LOG.error("Ошибка при спасении предметов из бездны подмира", t);
            }
        }
        if (tick % 20 != 0) {
            return;
        }
        try {
            UploadReceiver.tick();
            if (tick % 200 == 0) {
                SubworldManager.retryUnloads();
            }
            for (SubworldDim d : SubworldRegistry.dims()) {
                WorldServer w = DimensionManager.getWorld(d.dim);
                if (w == null) {
                    continue;
                }
                if (w.playerEntities.isEmpty()) {
                    int secs = emptySeconds.containsKey(d.dim) ? emptySeconds.get(d.dim) + 1 : 1;
                    emptySeconds.put(d.dim, secs);
                    if (secs % EcoConfig.subworldsUnloadEmptySeconds == 0) {
                        // чанки, загруженные без игроков (поиск точки и т.п.), сами не выгружаются
                        w.getChunkProvider().queueUnloadAll();
                    }
                    continue;
                }
                emptySeconds.remove(d.dim);
                for (EntityPlayer p : new java.util.ArrayList<EntityPlayer>(w.playerEntities)) {
                    if (p instanceof EntityPlayerMP) {
                        SubworldBorder.enforce((EntityPlayerMP) p);
                    }
                }
            }
            TemplateEdits.tick();
        } catch (Throwable t) {
            EcoServ.LOG.error("Ошибка в периодической проверке подмиров", t);
        }
    }
}
