package com.ecoserv.server;

import com.ecoserv.backup.BackupManager;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.economy.PlayerStats;
import com.ecoserv.economy.StatsStore;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.trade.TradeMailbox;
import com.ecoserv.trade.TradeManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Серверные события: вход/выход игроков, тики, чёрный список предметов. */
public class ServerEventHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerServices.onServerTick();
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        BackupManager.markPlayersSeen();

        PlayerStats stats = StatsStore.get(player.getUniqueID());
        stats.lastSeen = System.currentTimeMillis();
        StatsStore.markDirty();

        EconomyManager.flushPending(player);
        TradeMailbox.deliver(player);
        PlaytimeRewards.onLogin(player);

        PacketHandler.sendShopList(player);
        PacketHandler.sendAuction(player);
        PacketHandler.sendPlayerState(player);

        if (!stats.shopHintShown) {
            stats.shopHintShown = true;
            StatsStore.markDirty();
            if (EcoConfig.welcomeMessage != null && !EcoConfig.welcomeMessage.trim().isEmpty()) {
                ChatUtil.send(player, EcoConfig.welcomeMessage);
            }
            ChatUtil.send(player, "&aМагазин и баланс — кнопка &e[$] &aслева сверху в инвентаре. Обмен: &e/trade <ник>");
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        TradeManager.onLogout(player.getUniqueID());
        com.ecoserv.economy.MoneyRequests.forget(player.getUniqueID());
        PlaytimeRewards.onLogout(player);
        PlayerStats stats = StatsStore.get(player.getUniqueID());
        stats.lastSeen = System.currentTimeMillis();
        StatsStore.markDirty();
    }

    /** Потеря части денег при смерти (настраивается в конфиге). */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (EcoConfig.deathLossPercent <= 0.0D || !(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
        long balance = EconomyManager.get(player.getUniqueID());
        long loss = (long) Math.floor(balance * EcoConfig.deathLossPercent / 100.0D);
        if (EcoConfig.deathLossMax > 0L) {
            loss = Math.min(loss, EcoConfig.deathLossMax);
        }
        if (loss > 0L && EconomyManager.take(player.getUniqueID(), loss)) {
            ChatUtil.send(player, "&cПри смерти потеряно: &f" + com.ecoserv.util.Fmt.money(loss));
            PacketHandler.sendPlayerState(player);
        }
    }

    /** Чужие блоки мода нельзя ломать: иначе автомат унесут вместе с товаром и кассой. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote || event.getPlayer() == null) {
            return;
        }
        if (!EcoConfig.vendingProtectFromOthers) {
            return;
        }
        net.minecraft.tileentity.TileEntity te = event.getWorld().getTileEntity(event.getPos());
        boolean allowed = true;
        String owner = "";
        if (te instanceof com.ecoserv.tile.TileVending) {
            allowed = ((com.ecoserv.tile.TileVending) te).canBreak(event.getPlayer());
            owner = ((com.ecoserv.tile.TileVending) te).getOwnerName();
        } else if (te instanceof com.ecoserv.tile.TileVendor) {
            allowed = ((com.ecoserv.tile.TileVendor) te).canBreak(event.getPlayer());
            owner = ((com.ecoserv.tile.TileVendor) te).getOwnerName();
        } else if (te instanceof com.ecoserv.tile.TileMarket) {
            com.ecoserv.tile.TileMarket market = (com.ecoserv.tile.TileMarket) te;
            allowed = market.canBreak(event.getPlayer());
            owner = market.getOwnerName();
        }
        if (!allowed) {
            event.setCanceled(true);
            ChatUtil.send(event.getPlayer(), "&cЭтот блок принадлежит игроку &f" + owner);
        }
    }

    /**
     * 2.0: взрыв (ТНТ, крипер, чужие моды) не ломает блоки мода с владельцем — раньше склад продавца
     * высыпал товар, а автомат выпадал целиком и его мог подобрать кто угодно.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDetonate(net.minecraftforge.event.world.ExplosionEvent.Detonate event) {
        if (event.getWorld().isRemote) {
            return;
        }
        java.util.Iterator<net.minecraft.util.math.BlockPos> it = event.getAffectedBlocks().iterator();
        while (it.hasNext()) {
            if (isOwnedModBlock(event.getWorld().getTileEntity(it.next()))) {
                it.remove();
            }
        }
    }

    /** Блок мода, у которого есть хозяин (терминал, склад, автомат, датчик, банкомат). */
    public static boolean isOwnedModBlock(@javax.annotation.Nullable net.minecraft.tileentity.TileEntity te) {
        return te instanceof com.ecoserv.tile.TileVending || te instanceof com.ecoserv.tile.TileVendor
                || te instanceof com.ecoserv.tile.TileMarket || te instanceof com.ecoserv.tile.TilePlayerSensor
                || te instanceof com.ecoserv.bank.TileBank;
    }

    // --- чёрный список предметов -------------------------------------------

    private boolean blocked(EntityPlayer player, ItemStack stack) {
        if (!EcoConfig.blacklistEnabled || stack == null || stack.isEmpty()) {
            return false;
        }
        if (EcoConfig.blacklistAdminsBypass && Perms.isAdmin(player)) {
            return false;
        }
        return ItemBlacklist.isBlocked(stack);
    }

    private void warn(EntityPlayer player) {
        if (player != null && !player.world.isRemote) {
            ChatUtil.send(player, EcoConfig.blacklistMessage);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (EcoConfig.blacklistBlockUse && blocked(event.getEntityPlayer(), event.getItemStack())) {
            event.setCanceled(true);
            warn(event.getEntityPlayer());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (EcoConfig.blacklistBlockUse && blocked(event.getEntityPlayer(), event.getItemStack())) {
            event.setCanceled(true);
            warn(event.getEntityPlayer());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (EcoConfig.blacklistBlockPlace && blocked(event.getPlayer(), event.getItemInHand())) {
            event.setCanceled(true);
            warn(event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!EcoConfig.blacklistBlockCraft || event.player == null || event.player.world.isRemote) {
            return;
        }
        if (blocked(event.player, event.crafting)) {
            event.crafting.setCount(0);
            warn(event.player);
        }
    }
}
