package com.ecoserv.subworld;

import com.ecoserv.network.PacketHandler;
import com.ecoserv.shop.ShopList;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Свой магазин и свои цены продажи у подмира со своими квестами (п. 6): в его мирах действует
 * прайс config/ecoserv/shops/&lt;подмир&gt;.json. Админ правит его, находясь в подмире.
 */
public final class SubworldShops {

    private SubworldShops() {
    }

    /** Подключить определитель прайса к магазину (при запуске сервера). */
    public static void install() {
        ShopList.setScopeResolver(dim -> {
            Subworld sw = SubworldRegistry.subworldOf(dim);
            if (sw != null) {
                return sw.ownQuests ? sw.id : null;
            }
            // 1.8.0: свой магазин и у обычных миров
            com.ecoserv.world.WorldSettings.Settings ws = com.ecoserv.world.WorldSettings.peek(dim);
            return ws != null && ws.ownQuests ? com.ecoserv.world.WorldAccess.key(dim) : null;
        });
    }

    /** Какой прайс последним отправлен игроку ("" — общий). */
    private static final java.util.Map<java.util.UUID, String> SENT = new java.util.concurrent.ConcurrentHashMap<java.util.UUID, String>();

    /** Игрок перешёл между мирами — прислать прайс, если он другой. */
    public static void onTransfer(EntityPlayerMP player, int from, int to) {
        sync(player);
    }

    /** Прислать игроку прайс его текущего мира, если у него сейчас другой. */
    public static void sync(EntityPlayerMP player) {
        String scope = ShopList.forPlayer(player).scopeName();
        String sent = SENT.get(player.getUniqueID());
        if (!scope.equals(sent)) {
            PacketHandler.sendShopList(player);
        }
    }

    /** Прайс отправлен (вызывается из PacketHandler.sendShopList). */
    public static void markSent(EntityPlayerMP player, ShopList list) {
        SENT.put(player.getUniqueID(), list.scopeName());
    }

    public static void forget(java.util.UUID player) {
        SENT.remove(player);
    }

    /** Настройка «свои квесты» поменялась — всем в подмире прислать актуальный прайс. */
    public static void resync(Subworld sw) {
        for (SubworldDim d : SubworldRegistry.dimsOf(sw.id)) {
            net.minecraft.world.WorldServer w = net.minecraftforge.common.DimensionManager.getWorld(d.dim);
            if (w == null) {
                continue;
            }
            for (net.minecraft.entity.player.EntityPlayer p : w.playerEntities) {
                if (p instanceof EntityPlayerMP) {
                    PacketHandler.sendShopList((EntityPlayerMP) p);
                }
            }
        }
    }

    /** Скопировать общий прайс в прайс подмира. */
    public static int copyGlobal(Subworld sw) {
        ShopList own = ShopList.scope(sw.id);
        own.copyFrom(ShopList.get());
        own.save();
        resync(sw);
        return own.size();
    }
}
