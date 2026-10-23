package com.ecoserv.subworld;

import com.ecoserv.network.packet.PacketSubworldAction;
import com.ecoserv.subworld.menu.WorldsScreen;
import net.minecraft.entity.player.EntityPlayerMP;

/** Действия, присланные клиентом: кнопка-планета, «ОК» в окне ENGINE ERROR. */
public final class SubworldActions {

    private SubworldActions() {
    }

    public static void handle(EntityPlayerMP player, int action, String arg) {
        switch (action) {
            case PacketSubworldAction.OPEN_MENU:
                WorldsScreen.open(player);
                break;
            case PacketSubworldAction.OPEN_SHOP:
                com.ecoserv.shop.ShopScreen.open(player, arg);
                break;
            case PacketSubworldAction.ABYSS_OK:
                SubworldAbyss.onOk(player);
                break;
            default:
        }
    }
}
