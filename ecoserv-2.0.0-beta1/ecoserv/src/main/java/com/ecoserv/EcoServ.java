package com.ecoserv;

import com.ecoserv.command.CommandBackup;
import com.ecoserv.command.CommandEco;
import com.ecoserv.command.CommandEcoAdmin;
import com.ecoserv.command.CommandShop;
import com.ecoserv.command.CommandTrade;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.gui.GuiHandler;
import com.ecoserv.integration.Integrations;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.proxy.CommonProxy;
import com.ecoserv.server.ServerEventHandler;
import com.ecoserv.server.ServerServices;
import com.ecoserv.util.Perms;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = Ref.MODID,
        name = Ref.NAME,
        version = Ref.VERSION,
        acceptedMinecraftVersions = Ref.MC_VERSIONS,
        dependencies = Ref.DEPENDENCIES)
public class EcoServ {

    @Mod.Instance(Ref.MODID)
    public static EcoServ INSTANCE;

    @SidedProxy(clientSide = Ref.CLIENT_PROXY, serverSide = Ref.COMMON_PROXY)
    public static CommonProxy PROXY;

    public static final Logger LOG = LogManager.getLogger(Ref.NAME);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File dir = new File(event.getModConfigurationDirectory(), Ref.MODID);
        if (!dir.exists() && !dir.mkdirs()) {
            LOG.error("Не удалось создать папку конфигурации: {}", dir);
        }
        EcoConfig.init(dir);
        // тип измерения подмиров — до загрузки миров, на обеих сторонах
        com.ecoserv.subworld.SubworldDims.registerType();
        PacketHandler.init();
        PROXY.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(INSTANCE, new GuiHandler());
        MinecraftForge.EVENT_BUS.register(new ServerEventHandler());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.menu.ChatPrompt.Handler());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.world.WorldEvents());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.restrict.RestrictionEvents());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.zone.ZoneEvents());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.activator.ActivatorEvents());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.role.RoleEvents());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.inventory.InventoryEvents());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.minigame.RaceEvents());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.zone.PermitEvents());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.dates.DatesEvents());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.region.RegionEvents());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.content.ContentEvents());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.subworld.SubworldEvents());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.subworld.TemplateEdits.Events());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.ui.UiManager.Events());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.world.WorldRulesEvents());
        // 1.8.0: пауза порталов, защита после телепорта, банк, зарплата, звания
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.extra.ExtraEvents());
        // 1.8.0: публикация шаблона видит все сохранённые регионы (не нужно жать дважды)
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.subworld.PublishTracker());
        // 1.9.0: приваты и мини-игры
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.claim.ClaimEvents());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.games.GameEvents());
        com.ecoserv.subworld.SubworldPortals.install();
        // 2.0: банк, квесты, режим разработчика и т.д.
        com.ecoserv.v2.Features.init();
        Perms.register();
        initQuestTasks();
        initComputerCraft();
        PROXY.init(event);
    }

    /**
     * Условие «Активация» для FTB Quests. Код интеграции собирается только при наличии
     * jar-файлов FTB Quests в папке libs/ и подключается через рефлексию. Регистрация — до
     * postInit FTB Quests, где создаётся реестр условий.
     */
    private void initQuestTasks() {
        if (!net.minecraftforge.fml.common.Loader.isModLoaded("ftbquests")) {
            return;
        }
        try {
            Class.forName("com.ecoserv.integration.quests.QuestActivation").getMethod("init").invoke(null);
            LOG.info("FTB Quests: добавлено условие «Активация»");
        } catch (ClassNotFoundException e) {
            LOG.warn("FTB Quests найден, но мод собран без него (папка libs/ была пуста) — "
                    + "условия «Активация» в квестах не будет. Команды на кнопках (/activator command) работают.");
        } catch (Throwable t) {
            LOG.error("Не удалось добавить условие «Активация» в FTB Quests (другая версия FTB Quests?)", t);
        }
    }

    /** Периферия ComputerCraft (1.8.0): собирается, только если jar ComputerCraft был в libs/. */
    private void initComputerCraft() {
        if (!net.minecraftforge.fml.common.Loader.isModLoaded("computercraft")) {
            return;
        }
        try {
            Class.forName("com.ecoserv.integration.computercraft.CCIntegration").getMethod("init").invoke(null);
        } catch (ClassNotFoundException e) {
            LOG.warn("ComputerCraft найден, но мод собран без него (в libs/ не было jar ComputerCraft) — периферии не будет.");
        } catch (Throwable t) {
            LOG.error("Не удалось подключить периферию ComputerCraft (другая версия CC?)", t);
        }
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        Integrations.detect();
        PROXY.postInit(event);
    }

    /**
     * Мод обязателен: клиент без него (или с другой версией) на сервер не попадёт.
     * Отключается в конфиге general.require_client_mod.
     */
    @NetworkCheckHandler
    public boolean checkRemote(Map<String, String> mods, Side side) {
        if (!EcoConfig.requireClientMod) {
            return true;
        }
        String remote = mods.get(Ref.MODID);
        return remote != null && remote.equals(Ref.VERSION);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandEco());
        event.registerServerCommand(new CommandBackup());
        event.registerServerCommand(new CommandTrade());
        event.registerServerCommand(new CommandShop());
        event.registerServerCommand(new CommandEcoAdmin());
        event.registerServerCommand(new com.ecoserv.command.CommandWorld());
        event.registerServerCommand(new com.ecoserv.command.CommandStash());
        event.registerServerCommand(new com.ecoserv.command.CommandZone());
        event.registerServerCommand(new com.ecoserv.command.CommandActivator());
        event.registerServerCommand(new com.ecoserv.command.CommandRole());
        event.registerServerCommand(new com.ecoserv.command.CommandXtp());
        event.registerServerCommand(new com.ecoserv.command.CommandBirthday());
        event.registerServerCommand(new com.ecoserv.command.CommandRegion(com.ecoserv.region.Region.TRIGGER));
        event.registerServerCommand(new com.ecoserv.command.CommandRegion(com.ecoserv.region.Region.PORTAL));
        event.registerServerCommand(new com.ecoserv.command.CommandScript());
        event.registerServerCommand(new com.ecoserv.command.CommandSubworld());
        // у каждого мира свой барьер: /worldborder меняет барьер мира, где стоит игрок
        event.registerServerCommand(new com.ecoserv.command.CommandWorldBorderEx());
        // 1.8.0
        event.registerServerCommand(new com.ecoserv.economy.CommandOrders());
        event.registerServerCommand(new com.ecoserv.minigame.CommandRace());
        event.registerServerCommand(new com.ecoserv.cc.CommandEcoCC());
        event.registerServerCommand(new com.ecoserv.warp.CommandWarp());
        event.registerServerCommand(new com.ecoserv.claim.CommandClaim());
        event.registerServerCommand(new com.ecoserv.games.CommandGames());
        event.registerServerCommand(new com.ecoserv.extra.CommandWho());
        event.registerServerCommand(new com.ecoserv.extra.CommandRank());
        com.ecoserv.v2.Features.registerCommands(event);
        ServerServices.onServerStarting(event.getServer());
    }

    /** Все моды уже зарегистрировали свои измерения — пересобираем запреты с названиями миров. */
    @Mod.EventHandler
    public void serverStarted(net.minecraftforge.fml.common.event.FMLServerStartedEvent event) {
        com.ecoserv.restrict.ItemRules.reindex();
        // все моды уже зарегистрировали команды: оборачиваем их (роли + RCON в главном потоке)
        com.ecoserv.role.CommandGate.wrapAll(ServerServices.server());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        ServerServices.onServerStopping();
    }

    /** Сервер остановлен: снять регистрацию измерений подмиров (в одиночной игре следующий мир их не видит). */
    @Mod.EventHandler
    public void serverStopped(net.minecraftforge.fml.common.event.FMLServerStoppedEvent event) {
        com.ecoserv.subworld.SubworldDims.unregisterAll();
        com.ecoserv.subworld.SubworldBorder.onServerStopped();
        com.ecoserv.subworld.SubworldTeams.reset();
        com.ecoserv.subworld.SubworldLayers.invalidateAll();
        com.ecoserv.ui.UiManager.reset();
    }
}
