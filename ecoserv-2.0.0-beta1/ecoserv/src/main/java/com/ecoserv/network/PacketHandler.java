package com.ecoserv.network;

import com.ecoserv.Ref;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.economy.PlayerStats;
import com.ecoserv.economy.StatsStore;
import com.ecoserv.auction.AuctionManager;
import com.ecoserv.network.packet.PacketAdminAction;
import com.ecoserv.network.packet.PacketAuctionAction;
import com.ecoserv.network.packet.PacketAuctionSync;
import com.ecoserv.network.packet.PacketBuy;
import com.ecoserv.network.packet.PacketMarketAction;
import com.ecoserv.network.packet.PacketOpenShop;
import com.ecoserv.network.packet.PacketPlayerAction;
import com.ecoserv.network.packet.PacketPlayerState;
import com.ecoserv.network.packet.PacketRequestSync;
import com.ecoserv.network.packet.PacketShopEdit;
import com.ecoserv.network.packet.PacketShopSync;
import com.ecoserv.network.packet.PacketTradeAction;
import com.ecoserv.network.packet.PacketTradeState;
import com.ecoserv.network.packet.PacketVendingAction;
import com.ecoserv.shop.ShopList;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.List;

public final class PacketHandler {

    public static final SimpleNetworkWrapper NET = NetworkRegistry.INSTANCE.newSimpleChannel(Ref.CHANNEL);

    private static int nextId;
    /** Номера пакетов, которые шлём «сырыми» из сетевых потоков (см. raw). */
    public static int ID_SUBWORLD_SYNC;
    public static int ID_UPLOAD;
    public static int ID_UPLOAD_REPLY;

    private PacketHandler() {
    }

    public static void init() {
        NET.registerMessage(PacketShopSync.Handler.class, PacketShopSync.class, nextId++, Side.CLIENT);
        NET.registerMessage(PacketPlayerState.Handler.class, PacketPlayerState.class, nextId++, Side.CLIENT);
        NET.registerMessage(PacketOpenShop.Handler.class, PacketOpenShop.class, nextId++, Side.CLIENT);
        NET.registerMessage(PacketTradeState.Handler.class, PacketTradeState.class, nextId++, Side.CLIENT);
        NET.registerMessage(PacketAuctionSync.Handler.class, PacketAuctionSync.class, nextId++, Side.CLIENT);
        NET.registerMessage(com.ecoserv.network.packet.PacketRoles.Handler.class,
                com.ecoserv.network.packet.PacketRoles.class, nextId++, Side.CLIENT);
        ID_SUBWORLD_SYNC = nextId;
        NET.registerMessage(com.ecoserv.network.packet.PacketSubworldSync.Handler.class,
                com.ecoserv.network.packet.PacketSubworldSync.class, nextId++, Side.CLIENT);
        ID_UPLOAD_REPLY = nextId;
        NET.registerMessage(com.ecoserv.network.packet.PacketUploadReply.Handler.class,
                com.ecoserv.network.packet.PacketUploadReply.class, nextId++, Side.CLIENT);
        NET.registerMessage(com.ecoserv.network.packet.PacketSubworldClient.Handler.class,
                com.ecoserv.network.packet.PacketSubworldClient.class, nextId++, Side.CLIENT);

        NET.registerMessage(PacketRequestSync.Handler.class, PacketRequestSync.class, nextId++, Side.SERVER);
        NET.registerMessage(PacketShopEdit.Handler.class, PacketShopEdit.class, nextId++, Side.SERVER);
        NET.registerMessage(PacketBuy.Handler.class, PacketBuy.class, nextId++, Side.SERVER);
        NET.registerMessage(PacketMarketAction.Handler.class, PacketMarketAction.class, nextId++, Side.SERVER);
        NET.registerMessage(PacketTradeAction.Handler.class, PacketTradeAction.class, nextId++, Side.SERVER);
        NET.registerMessage(PacketAdminAction.Handler.class, PacketAdminAction.class, nextId++, Side.SERVER);
        NET.registerMessage(PacketPlayerAction.Handler.class, PacketPlayerAction.class, nextId++, Side.SERVER);
        NET.registerMessage(PacketAuctionAction.Handler.class, PacketAuctionAction.class, nextId++, Side.SERVER);
        NET.registerMessage(PacketVendingAction.Handler.class, PacketVendingAction.class, nextId++, Side.SERVER);
        ID_UPLOAD = nextId;
        NET.registerMessage(com.ecoserv.network.packet.PacketUpload.Handler.class,
                com.ecoserv.network.packet.PacketUpload.class, nextId++, Side.SERVER);
        NET.registerMessage(com.ecoserv.network.packet.PacketSubworldAction.Handler.class,
                com.ecoserv.network.packet.PacketSubworldAction.class, nextId++, Side.SERVER);
        // 1.7.0: окна в стиле FTB Quests и эффекты ГЛИТЧ
        NET.registerMessage(com.ecoserv.network.packet.PacketUi.Handler.class,
                com.ecoserv.network.packet.PacketUi.class, nextId++, Side.CLIENT);
        NET.registerMessage(com.ecoserv.network.packet.PacketUiAction.Handler.class,
                com.ecoserv.network.packet.PacketUiAction.class, nextId++, Side.SERVER);
        NET.registerMessage(com.ecoserv.network.packet.PacketGlitchFx.Handler.class,
                com.ecoserv.network.packet.PacketGlitchFx.class, nextId++, Side.CLIENT);
        // 1.8.0: порталы «Чёрная дыра»
        NET.registerMessage(com.ecoserv.network.packet.PacketBlackHole.Handler.class,
                com.ecoserv.network.packet.PacketBlackHole.class, nextId++, Side.CLIENT);
        // 1.9.0: клиентские настройки -> сервер
        NET.registerMessage(com.ecoserv.network.packet.PacketClientPrefs.Handler.class,
                com.ecoserv.network.packet.PacketClientPrefs.class, nextId++, Side.SERVER);
        // 2.0: общие пакеты новых систем
        NET.registerMessage(com.ecoserv.network.packet.PacketV2.Handler.class,
                com.ecoserv.network.packet.PacketV2.class, nextId++, Side.CLIENT);
        NET.registerMessage(com.ecoserv.network.packet.PacketV2Action.Handler.class,
                com.ecoserv.network.packet.PacketV2Action.class, nextId++, Side.SERVER);
    }

    /**
     * Пакет, собранный вручную (без общего канала FML). Его можно отправлять из любого потока через
     * NetworkManager.sendPacket — общий канал SimpleNetworkWrapper не потокобезопасен.
     */
    public static net.minecraftforge.fml.common.network.internal.FMLProxyPacket raw(IMessage message, int id) {
        io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        buf.writeByte(id);
        message.toBytes(buf);
        return new net.minecraftforge.fml.common.network.internal.FMLProxyPacket(
                new net.minecraft.network.PacketBuffer(buf), Ref.CHANNEL);
    }

    public static void sendTo(IMessage message, EntityPlayerMP player) {
        if (player != null) {
            NET.sendTo(message, player);
        }
    }

    public static void sendToServer(IMessage message) {
        NET.sendToServer(message);
    }

    public static void sendToAll(IMessage message) {
        NET.sendToAll(message);
    }

    /** Отправить игроку весь прайс-лист (по частям, чтобы влезть в лимит пакета). */
    public static void sendShopList(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        ShopList list = ShopList.forPlayer(player);
        com.ecoserv.subworld.SubworldShops.markSent(player, list);
        byte[] data = NetUtil.gzip(list.toJson());
        int total = Math.max(1, (data.length + NetUtil.CHUNK - 1) / NetUtil.CHUNK);
        for (int i = 0; i < total; i++) {
            int from = i * NetUtil.CHUNK;
            int to = Math.min(data.length, from + NetUtil.CHUNK);
            byte[] chunk = new byte[to - from];
            System.arraycopy(data, from, chunk, 0, to - from);
            sendTo(new PacketShopSync(i, total, chunk), player);
        }
    }

    /** Разослать прайс тем, у кого он сейчас действует. */
    public static void sendShopListToScope(ShopList shop) {
        List<EntityPlayerMP> players = com.ecoserv.util.PlayerUtil.allOnline();
        for (int i = 0; i < players.size(); i++) {
            if (ShopList.forPlayer(players.get(i)) == shop) {
                sendShopList(players.get(i));
            }
        }
    }

    public static void sendShopListToAll() {
        List<EntityPlayerMP> players = com.ecoserv.util.PlayerUtil.allOnline();
        for (int i = 0; i < players.size(); i++) {
            sendShopList(players.get(i));
        }
    }

    /** Отправить игроку все лоты аукциона (по частям). */
    public static void sendAuction(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        byte[] data = NetUtil.gzip(AuctionManager.toJson());
        int total = Math.max(1, (data.length + NetUtil.CHUNK - 1) / NetUtil.CHUNK);
        for (int i = 0; i < total; i++) {
            int from = i * NetUtil.CHUNK;
            int to = Math.min(data.length, from + NetUtil.CHUNK);
            byte[] chunk = new byte[to - from];
            System.arraycopy(data, from, chunk, 0, to - from);
            sendTo(new PacketAuctionSync(i, total, chunk), player);
        }
    }

    public static void sendAuctionToAll() {
        List<EntityPlayerMP> players = com.ecoserv.util.PlayerUtil.allOnline();
        for (int i = 0; i < players.size(); i++) {
            sendAuction(players.get(i));
        }
    }

    /** Отправить игроку его баланс и права. */
    public static void sendPlayerState(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        PlayerStats stats = StatsStore.get(player.getUniqueID());
        long balance = EconomyManager.get(player.getUniqueID());
        boolean dailyAvailable = stats.lastDailyDay != System.currentTimeMillis() / 86_400_000L;
        sendTo(new PacketPlayerState(balance, Perms.isAdmin(player), dailyAvailable, stats.dailyStreak,
                stats.playtimeMillis / 60_000L, EconomyManager.pendingOf(player.getUniqueID()),
                stats.totalEarned, stats.totalSpent,
                com.ecoserv.config.EcoConfig.marketTaxPercent,
                com.ecoserv.config.EcoConfig.marketSellIntervalTicks), player);
    }

    public static void sendPlayerStateToAll() {
        List<EntityPlayerMP> players = com.ecoserv.util.PlayerUtil.allOnline();
        for (int i = 0; i < players.size(); i++) {
            sendPlayerState(players.get(i));
        }
    }
}
