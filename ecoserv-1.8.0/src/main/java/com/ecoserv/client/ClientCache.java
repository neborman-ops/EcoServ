package com.ecoserv.client;

import com.ecoserv.network.NetUtil;
import com.ecoserv.shop.ShopList;

/** Клиентский кеш данных, присланных сервером. */
public final class ClientCache {

    public static long balance;
    public static long pending;
    public static long totalEarned;
    public static long totalSpent;
    public static boolean admin;
    public static boolean dailyAvailable;
    public static int streak;
    public static long playtimeMinutes;

    /** Настройки, присланные сервером (у клиента свой конфиг, ему верить нельзя). */
    public static double serverTaxPercent;
    public static int serverSellInterval = 100;

    public static final TradeState tradeState = new TradeState();

    /** Названия ролей своего игрока (с цветом) — для меню. */
    public static final java.util.List<String> roleTitles = new java.util.ArrayList<String>();

    private static byte[][] auctionChunks;
    private static int auctionExpected;

    private static byte[][] chunks;
    private static int expectedChunks;

    private ClientCache() {
    }

    public static class TradeState {
        public int sessionId;
        public long ownMoney;
        public long otherMoney;
        public boolean ownConfirm;
        public boolean otherConfirm;
        public String otherName = "";

        public void reset() {
            sessionId = 0;
            ownMoney = 0L;
            otherMoney = 0L;
            ownConfirm = false;
            otherConfirm = false;
            otherName = "";
        }
    }

    /** Сборка списка лотов аукциона из присланных частей. */
    public static void acceptAuctionChunk(int part, int total, byte[] data) {
        if (auctionChunks == null || auctionExpected != total || part == 0) {
            auctionChunks = new byte[total][];
            auctionExpected = total;
        }
        if (part < 0 || part >= total) {
            return;
        }
        auctionChunks[part] = data;
        for (int i = 0; i < total; i++) {
            if (auctionChunks[i] == null) {
                return;
            }
        }
        int size = 0;
        for (int i = 0; i < total; i++) {
            size += auctionChunks[i].length;
        }
        byte[] all = new byte[size];
        int offset = 0;
        for (int i = 0; i < total; i++) {
            System.arraycopy(auctionChunks[i], 0, all, offset, auctionChunks[i].length);
            offset += auctionChunks[i].length;
        }
        auctionChunks = null;
        auctionExpected = 0;
        com.ecoserv.auction.AuctionManager.fromJson(NetUtil.gunzip(all));
    }

    /** Сборка прайс-листа из присланных частей. */
    public static void acceptChunk(int part, int total, byte[] data) {
        if (chunks == null || expectedChunks != total || part == 0) {
            chunks = new byte[total][];
            expectedChunks = total;
        }
        if (part < 0 || part >= total) {
            return;
        }
        chunks[part] = data;

        for (int i = 0; i < total; i++) {
            if (chunks[i] == null) {
                return;
            }
        }
        int size = 0;
        for (int i = 0; i < total; i++) {
            size += chunks[i].length;
        }
        byte[] all = new byte[size];
        int offset = 0;
        for (int i = 0; i < total; i++) {
            System.arraycopy(chunks[i], 0, all, offset, chunks[i].length);
            offset += chunks[i].length;
        }
        chunks = null;
        expectedChunks = 0;
        ShopList.get().fromJson(NetUtil.gunzip(all));
    }
}
