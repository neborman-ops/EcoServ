package com.ecoserv.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/** Горячие клавиши. Переназначаются в настройках управления, категория EcoServ. */
@SideOnly(Side.CLIENT)
public final class KeyBindings {

    public static KeyBinding openShop;
    public static KeyBinding openTrade;
    public static KeyBinding openWorlds;

    private KeyBindings() {
    }

    public static void register() {
        openShop = new KeyBinding("key.ecoserv.shop", Keyboard.KEY_K, "key.categories.ecoserv");
        ClientRegistry.registerKeyBinding(openShop);

        openTrade = new KeyBinding("key.ecoserv.trade", Keyboard.KEY_J, "key.categories.ecoserv");
        ClientRegistry.registerKeyBinding(openTrade);

        // без клавиши по умолчанию: меню миров открывается кнопкой-планетой в инвентаре
        openWorlds = new KeyBinding("key.ecoserv.worlds", Keyboard.KEY_NONE, "key.categories.ecoserv");
        ClientRegistry.registerKeyBinding(openWorlds);
    }
}
