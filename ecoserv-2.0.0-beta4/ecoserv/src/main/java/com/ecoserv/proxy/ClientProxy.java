package com.ecoserv.proxy;

import com.ecoserv.client.ClientCache;
import com.ecoserv.client.InventoryTabHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // 2.0 beta3: свой контент сервера — ресурс-пак и кэш
        com.ecoserv.client.v2.ContentClient.preInit();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new InventoryTabHandler());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.PriceTooltipHandler());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.ClientAbyss());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.ClientGlitchFx());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.MainWorldSky());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.BlackHoleClient());
        com.ecoserv.client.KeyBindings.register();
        // 1.9.0: клиентские настройки (шестерёнка, /ecosettings)
        com.ecoserv.client.ClientSettings.load();
        com.ecoserv.lang.ServerLang.load();
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.ClientEvents());
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(new com.ecoserv.client.ClientEvents.Command());
        // 2.0: режим разработчика (рамки, маркеры, надписи)
        com.ecoserv.client.v2.DevRender.register();
        com.ecoserv.client.v2.EscMenu.register();
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.v2.EscMenu());
        com.ecoserv.client.v2.CameraFx.register();
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.v2.CameraFx());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.v2.DynamicLight());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.v2.ElectricRender());
        com.ecoserv.client.v2.LightingFx.register();
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.v2.LightingFx());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.v2.LightingSky());
        com.ecoserv.client.v2.ContentClient.register();
        com.ecoserv.client.v2.EditorClient.register();
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.v2.EditorClient());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.v2.EditorRender());
        com.ecoserv.client.v2.PanelRender.register();
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.v2.PanelRender());
        MinecraftForge.EVENT_BUS.register(new com.ecoserv.client.v2.DevRender());
        // 2.0 beta4: свечение, блок-дисплеи, камеры, дизайнер окон
        com.ecoserv.client.v2.Beta4Client.init();
    }

    @Override
    public World getClientWorld() {
        return Minecraft.getMinecraft().world;
    }

    @Override
    public EntityPlayer getClientPlayer() {
        return Minecraft.getMinecraft().player;
    }

    @Override
    public void openShopGui(final int tab) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                com.ecoserv.client.InventoryTabHandler.openShopTab(Integer.toString(tab));
            }
        });
    }

    @Override
    public void onClientDisconnect() {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                com.ecoserv.subworld.SubworldDims.clearClient();
                com.ecoserv.client.ClientSubworlds.reset();
                com.ecoserv.client.ClientHooks.windowClosed(-1);
                com.ecoserv.client.ClientGlitchFx.reset();
                com.ecoserv.client.v2.DevRender.reset();
                com.ecoserv.client.v2.EscMenu.reset();
                com.ecoserv.client.v2.CameraFx.reset();
                com.ecoserv.client.v2.DynamicLight.reset();
                com.ecoserv.client.v2.LightingFx.reset();
                com.ecoserv.client.v2.PanelRender.reset();
                com.ecoserv.client.v2.Beta4Client.reset();
            }
        });
    }

    @Override
    public void openTradeGui() {
        // Экран торговли открывается через IGuiHandler, здесь только сбрасываем кеш.
        ClientCache.tradeState.reset();
    }
}
