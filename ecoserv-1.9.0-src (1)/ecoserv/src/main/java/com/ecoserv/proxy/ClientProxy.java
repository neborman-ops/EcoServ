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
            }
        });
    }

    @Override
    public void openTradeGui() {
        // Экран торговли открывается через IGuiHandler, здесь только сбрасываем кеш.
        ClientCache.tradeState.reset();
    }
}
