package com.ecoserv.proxy;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
    }

    public void init(FMLInitializationEvent event) {
    }

    public void postInit(FMLPostInitializationEvent event) {
    }

    /** Мир клиента (на сервере не используется). */
    public World getClientWorld() {
        return null;
    }

    public EntityPlayer getClientPlayer() {
        return null;
    }

    /** Открыть клиентский экран магазина/админки. */
    public void openShopGui(int tab) {
    }

    /** Открыть клиентский экран торговли. */
    public void openTradeGui() {
    }

    /** Клиент отключился от сервера (снять регистрацию измерений подмиров и т.п.). */
    public void onClientDisconnect() {
    }
}
