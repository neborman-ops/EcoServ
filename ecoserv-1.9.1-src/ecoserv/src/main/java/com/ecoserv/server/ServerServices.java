package com.ecoserv.server;

import com.ecoserv.EcoServ;
import com.ecoserv.backup.BackupManager;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.economy.StatsStore;
import com.ecoserv.integration.FTBQuestsBridge;
import com.ecoserv.shop.SalesTracker;
import com.ecoserv.shop.ShopList;
import com.ecoserv.trade.TradeMailbox;
import com.ecoserv.trade.TradeManager;
import com.ecoserv.util.DataStore;
import net.minecraft.server.MinecraftServer;

/** Жизненный цикл всех серверных подсистем мода. */
public final class ServerServices {

    private static MinecraftServer server;
    private static int tick;

    private ServerServices() {
    }

    public static MinecraftServer server() {
        return server;
    }

    public static void onServerStarting(MinecraftServer mcServer) {
        server = mcServer;
        tick = 0;

        DataStore.init(mcServer);
        ShopList.get().load();
        ItemBlacklist.load();
        com.ecoserv.world.WorldNames.reload();
        com.ecoserv.restrict.ItemRules.load();
        com.ecoserv.restrict.Stash.load();
        com.ecoserv.zone.ZoneManager.load();
        com.ecoserv.activator.ActivatorManager.load();
        com.ecoserv.role.RoleManager.load();
        RewardItems.load();
        com.ecoserv.dates.DatesManager.load();
        com.ecoserv.region.RegionManager.load();
        com.ecoserv.region.HoleParams.load();
        com.ecoserv.lang.ServerLang.load();
        com.ecoserv.warp.WarpManager.load();
        com.ecoserv.claim.ClaimManager.load();
        com.ecoserv.games.GameManager.onServerStart();
        com.ecoserv.script.ScriptManager.load();
        com.ecoserv.content.Checkpoints.load();
        com.ecoserv.content.LootStore.unloadAll();
        ServerTasks.clear();
        // подмиры: реестр, уборка удалённых папок, регистрация измерений в Forge
        com.ecoserv.subworld.SubworldRegistry.load();
        com.ecoserv.subworld.WorldCrews.load();
        com.ecoserv.economy.MoneyRequests.reset();
        com.ecoserv.world.WorldSettings.load();
        com.ecoserv.subworld.GlitchStorage.reset();
        com.ecoserv.subworld.SubworldManager.reset();
        com.ecoserv.subworld.SubworldManager.cleanupOnStart();
        com.ecoserv.subworld.SubworldDims.registerAll();
        com.ecoserv.subworld.SubworldShops.install();
        // провайдер выбираем на старте сервера: к этому моменту классы чужих модов уже загружены
        com.ecoserv.integration.Integrations.selectEconomy();
        EconomyManager.init();
        StatsStore.load();
        SalesTracker.load();
        TradeMailbox.load();
        com.ecoserv.auction.AuctionManager.load();

        BackupManager.onServerStart(mcServer);
        RestartScheduler.onServerStart();
        Announcer.onServerStart();
        FTBQuestsBridge.refresh();
        com.ecoserv.extra.ExtraStore.load();
        com.ecoserv.extra.ExtraConfig.refresh();
        com.ecoserv.economy.Orders.load();
        com.ecoserv.minigame.RaceManager.onServerStart();

        EcoServ.LOG.info("EcoServ запущен. Экономика: {}, позиций в прайсе: {}",
                EconomyManager.provider().getName(), ShopList.get().size());
    }

    /** Раз в тик из ServerTickEvent. */
    public static void onServerTick() {
        if (server == null) {
            return;
        }
        tick++;
        ServerTasks.tick();
        com.ecoserv.content.FillJobs.tick();
        com.ecoserv.extra.ExtraEvents.serverTick();
        com.ecoserv.minigame.RaceManager.tick();
        com.ecoserv.region.BlackHoles.tick();
        com.ecoserv.games.GameManager.tick();
        if (tick % 20 == 0) {
            try {
                BackupManager.tick();
                TradeManager.tick();
                RestartScheduler.tick();
                Announcer.tick();
                PlaytimeRewards.tick();
                com.ecoserv.auction.AuctionManager.tick();
                com.ecoserv.role.RoleManager.tick();
                com.ecoserv.dates.DatesManager.tick();
            } catch (Throwable t) {
                EcoServ.LOG.error("Ошибка в периодических задачах", t);
            }
        }
        if (tick % 600 == 0) {
            saveAll(false);
        }
    }

    public static void saveAll(boolean force) {
        try {
            if (force) {
                StatsStore.save();
                ShopList.get().save();
                ShopList.saveScopes();
            } else {
                StatsStore.saveIfDirty();
            }
            EconomyManager.save();
            com.ecoserv.extra.ExtraStore.saveIfDirty();
            com.ecoserv.economy.Orders.saveIfDirty();
            com.ecoserv.minigame.RaceManager.saveIfDirty();
            SalesTracker.saveIfDirty();
            com.ecoserv.auction.AuctionManager.saveIfDirty();
            TradeMailbox.save();
            if (force) {
                com.ecoserv.restrict.Stash.save();
            } else {
                com.ecoserv.restrict.Stash.saveIfDirty();
            }
            com.ecoserv.zone.ZoneManager.saveIfDirty();
            com.ecoserv.claim.ClaimManager.saveIfDirty();
            com.ecoserv.activator.ActivatorManager.saveIfDirty();
            com.ecoserv.role.RoleManager.saveIfDirty();
            com.ecoserv.dates.DatesManager.saveIfDirty();
            com.ecoserv.region.RegionManager.saveIfDirty();
            com.ecoserv.content.Checkpoints.saveIfDirty();
            com.ecoserv.subworld.SubworldRegistry.saveIfDirty();
            com.ecoserv.subworld.GlitchStorage.saveIfDirty();
            com.ecoserv.world.WorldSettings.saveIfDirty();
        } catch (Throwable t) {
            EcoServ.LOG.error("Ошибка сохранения данных", t);
        }
    }

    /** /ecoadmin reload и кнопка в админ-панели. */
    public static void reloadAll() {
        com.ecoserv.config.EcoConfig.reload();
        ShopList.get().load();
        ShopList.reloadScopes();
        ItemBlacklist.load();
        com.ecoserv.world.WorldNames.reload();
        com.ecoserv.restrict.ItemRules.load();
        com.ecoserv.role.RoleManager.saveIfDirty();
        com.ecoserv.role.RoleManager.load();
        com.ecoserv.role.RoleManager.syncAll();
        RewardItems.load();
        com.ecoserv.dates.DatesManager.saveIfDirty();
        com.ecoserv.dates.DatesManager.load();
        com.ecoserv.script.ScriptManager.load();
        com.ecoserv.integration.Integrations.selectEconomy();
        FTBQuestsBridge.refresh();
        com.ecoserv.network.PacketHandler.sendShopListToAll();
    }

    public static void onServerStopping() {
        TradeManager.shutdown();
        com.ecoserv.subworld.UploadReceiver.shutdown();
        saveAll(true);
        BackupManager.onServerStop();
        com.ecoserv.minigame.RaceManager.onServerStop();
        com.ecoserv.games.GameManager.onServerStop();
        com.ecoserv.claim.ClaimManager.saveIfDirty();
        com.ecoserv.economy.Orders.unload();
        com.ecoserv.cc.CcKeys.unload();
        com.ecoserv.extra.ExtraStore.unload();
        DataStore.shutdown();
        server = null;
        EcoServ.LOG.info("EcoServ остановлен, данные сохранены.");
    }
}
