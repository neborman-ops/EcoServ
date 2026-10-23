package com.ecoserv.v2;

import com.ecoserv.EcoServ;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

/**
 * EcoServ 2.0: подключение новых систем в одном месте — события, команды, загрузка, тик, остановка.
 * Старые классы мода вызывают только эти методы.
 */
public final class Features {

    private static int tick;

    private Features() {
    }

    /** FMLInitializationEvent (обе стороны). */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new FeatureEvents());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.auth.AuthEvents());
        com.ecoserv.quest.QuestEvents quests = new com.ecoserv.quest.QuestEvents();
        MinecraftForge.EVENT_BUS.register(quests);
        com.ecoserv.activator.ActivatorManager.addListener(quests);
        com.ecoserv.npc.NpcFeature.init();
        com.ecoserv.custommenu.MenuFeature.init();
        com.ecoserv.cutscene.CutsceneFeature.init();
        com.ecoserv.build.BuildFeature.init();
        com.ecoserv.electric.ElectricFeature.init();
        com.ecoserv.customitem.CustomItemFeature.init();
        com.ecoserv.scene.Beta3Feature.init();
        com.ecoserv.scene.Beta4Feature.init();
    }

    /** FMLServerStartingEvent: команды. */
    public static void registerCommands(FMLServerStartingEvent event) {
        event.registerServerCommand(new com.ecoserv.bank.CommandBank());
        event.registerServerCommand(new com.ecoserv.auth.CommandAuth(com.ecoserv.auth.CommandAuth.LOGIN));
        event.registerServerCommand(new com.ecoserv.auth.CommandAuth(com.ecoserv.auth.CommandAuth.REGISTER));
        event.registerServerCommand(new com.ecoserv.auth.CommandAuth(com.ecoserv.auth.CommandAuth.CHANGE));
        event.registerServerCommand(new com.ecoserv.auth.CommandAuth(com.ecoserv.auth.CommandAuth.ADMIN));
        event.registerServerCommand(new com.ecoserv.quest.CommandQuests());
        event.registerServerCommand(new com.ecoserv.dev.CommandDev());
        event.registerServerCommand(new com.ecoserv.custommenu.CommandMenu());
        event.registerServerCommand(new com.ecoserv.cutscene.CutsceneFeature.Command());
        event.registerServerCommand(new com.ecoserv.build.BuildFeature.Command());
        for (String n : new String[]{"editor", "lighting", "panels", "content"}) {
            event.registerServerCommand(new com.ecoserv.scene.Beta3Feature.Command(n));
        }
        for (String n : new String[]{"camera", "gui", "displays"}) {
            event.registerServerCommand(new com.ecoserv.scene.Beta4Feature.Command(n));
        }
    }

    /** Старт сервера: загрузка данных. */
    public static void onServerStart(MinecraftServer server) {
        tick = 0;
        safe("банк", com.ecoserv.bank.BankManager::load);
        safe("аккаунты", com.ecoserv.auth.AuthManager::load);
        safe("квесты", com.ecoserv.quest.QuestManager::load);
        safe("режим разработчика", com.ecoserv.dev.DevManager::load);
        safe("NPC", com.ecoserv.npc.NpcManager::load);
        safe("меню", com.ecoserv.custommenu.MenuManager::load);
        safe("катсцены", com.ecoserv.cutscene.CutsceneManager::load);
        safe("стройка", com.ecoserv.build.BuildManager::load);
        safe("свои предметы", com.ecoserv.customitem.CustomItems::load);
        safe("освещение", com.ecoserv.lighting.LightingManager::load);
        safe("3D-панели", com.ecoserv.panel.PanelManager::load);
        safe("свой контент", com.ecoserv.content.CustomContent::load);
        safe("дисплеи, камеры, окна", com.ecoserv.scene.Beta4Feature::load);
    }

    public static void tick() {
        tick++;
        try {
            com.ecoserv.bank.BankManager.tick();
            com.ecoserv.quest.QuestManager.tick();
            com.ecoserv.dev.DevManager.tick();
            com.ecoserv.npc.NpcManager.tick();
            com.ecoserv.cutscene.CutsceneManager.tick();
            com.ecoserv.build.BuildFeature.tick();
            com.ecoserv.electric.ElectricGrid.tick();
            com.ecoserv.panel.PanelManager.tick();
            com.ecoserv.scene.Beta4Feature.tick();
        } catch (Throwable t) {
            EcoServ.LOG.error("EcoServ 2.0: ошибка в тике", t);
        }
    }

    public static void onServerStop() {
        safe("банк", com.ecoserv.bank.BankManager::onStop);
        safe("квесты", com.ecoserv.quest.QuestManager::onStop);
        safe("режим разработчика", com.ecoserv.dev.DevManager::onStop);
        safe("NPC", com.ecoserv.npc.NpcManager::onStop);
    }

    static void safe(String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            EcoServ.LOG.error("EcoServ 2.0: {} — ошибка", what, t);
        }
    }

    /** Игрок зашёл: разослать ему состояние новых систем (меню ESC, голограммы и т.п.). */
    static void onPlayerJoin(net.minecraft.entity.player.EntityPlayerMP p) {
        for (java.util.function.Consumer<net.minecraft.entity.player.EntityPlayerMP> c : JOIN) {
            try {
                c.accept(p);
            } catch (Throwable t) {
                EcoServ.LOG.error("EcoServ 2.0: ошибка при входе игрока", t);
            }
        }
    }

    static void onPlayerLeave(net.minecraft.entity.player.EntityPlayerMP p) {
        for (java.util.function.Consumer<net.minecraft.entity.player.EntityPlayerMP> c : LEAVE) {
            try {
                c.accept(p);
            } catch (Throwable t) {
                EcoServ.LOG.error("EcoServ 2.0: ошибка при выходе игрока", t);
            }
        }
    }

    static void onPlayerChangeDim(net.minecraft.entity.player.EntityPlayerMP p) {
        for (java.util.function.Consumer<net.minecraft.entity.player.EntityPlayerMP> c : DIM) {
            try {
                c.accept(p);
            } catch (Throwable t) {
                EcoServ.LOG.error("EcoServ 2.0: ошибка при смене мира", t);
            }
        }
    }

    public static final java.util.List<java.util.function.Consumer<net.minecraft.entity.player.EntityPlayerMP>> JOIN =
            new java.util.ArrayList<java.util.function.Consumer<net.minecraft.entity.player.EntityPlayerMP>>();
    public static final java.util.List<java.util.function.Consumer<net.minecraft.entity.player.EntityPlayerMP>> LEAVE =
            new java.util.ArrayList<java.util.function.Consumer<net.minecraft.entity.player.EntityPlayerMP>>();
    public static final java.util.List<java.util.function.Consumer<net.minecraft.entity.player.EntityPlayerMP>> DIM =
            new java.util.ArrayList<java.util.function.Consumer<net.minecraft.entity.player.EntityPlayerMP>>();

    public static int ticks() {
        return tick;
    }
}
