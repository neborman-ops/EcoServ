package com.ecoserv.economy;

import com.ecoserv.EcoServ;
import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.extra.MoneyNote;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.ItemCodec;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.PlayerUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Заказы (1.8.0, вместо вклада под проценты): игрок заранее платит за вещи — «куплю 64 железа по 5» —
 * деньги лежат в заказе, другие игроки сдают вещи и сразу получают оплату. Новых денег не появляется:
 * всё, что получает сдающий, заплатил заказчик. Несданное по истечении срока возвращается заказчику.
 * Файл world/ecoserv/orders.json.
 */
public final class Orders {

    public static class Order {
        public long id;
        public String owner = "";
        public String ownerName = "";
        /** Ключ предмета (ItemKey) и образец для иконки. */
        public String key = "";
        public String sample = "";
        public String itemName = "";
        public int want;
        public int filled;
        public long price;
        /** Сколько денег ещё лежит в заказе. */
        public long escrow;
        public long created;
        public long expires;
        public boolean closed;
        /** Сданные вещи, которые заказчик ещё не забрал. */
        public List<String> ready = new ArrayList<String>();

        public UUID ownerId() {
            try {
                return UUID.fromString(owner);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public int left() {
            return closed ? 0 : Math.max(0, want - filled);
        }

        public ItemKey itemKey() {
            return ItemKey.parse(key);
        }

        public ItemStack icon() {
            ItemStack s = ItemCodec.decode(sample);
            return s.isEmpty() ? ItemStack.EMPTY : s;
        }

        public int readyCount() {
            int n = 0;
            for (String r : ready) {
                n += ItemCodec.decode(r).getCount();
            }
            return n;
        }
    }

    private static final Type TYPE = new TypeToken<ArrayList<Order>>() {
    }.getType();
    private static final List<Order> ORDERS = new ArrayList<Order>();
    private static boolean loaded;
    private static boolean dirty;
    private static long nextId = 1;
    private static int tick;

    private Orders() {
    }

    private static File file() {
        return DataStore.file("orders.json");
    }

    public static synchronized void load() {
        ORDERS.clear();
        try {
            List<Order> raw = JsonUtil.readType(file(), TYPE, new ArrayList<Order>());
            for (Order o : raw) {
                if (o == null) {
                    continue;
                }
                if (o.ready == null) {
                    o.ready = new ArrayList<String>();
                }
                ORDERS.add(o);
                nextId = Math.max(nextId, o.id + 1);
            }
        } catch (Throwable t) {
            EcoServ.LOG.error("Не удалось прочитать orders.json", t);
        }
        loaded = true;
        dirty = false;
    }

    private static void ensure() {
        if (!loaded) {
            load();
        }
    }

    public static synchronized void saveIfDirty() {
        if (dirty && loaded) {
            dirty = false;
            JsonUtil.write(file(), new ArrayList<Order>(ORDERS));
        }
    }

    public static synchronized void unload() {
        saveIfDirty();
        ORDERS.clear();
        loaded = false;
    }

    private static void changed() {
        dirty = true;
        saveIfDirty();
    }

    /** Открытые заказы (новые сверху). */
    public static synchronized List<Order> open() {
        ensure();
        List<Order> out = new ArrayList<Order>();
        for (Order o : ORDERS) {
            if (!o.closed) {
                out.add(0, o);
            }
        }
        return out;
    }

    /** Заказы игрока: открытые и закрытые, где остались несобранные вещи. */
    public static synchronized List<Order> of(UUID player) {
        ensure();
        String id = player.toString();
        List<Order> out = new ArrayList<Order>();
        for (Order o : ORDERS) {
            if (o.owner.equals(id) && (!o.closed || !o.ready.isEmpty())) {
                out.add(0, o);
            }
        }
        return out;
    }

    public static synchronized int openCount(UUID player) {
        int n = 0;
        for (Order o : of(player)) {
            if (!o.closed) {
                n++;
            }
        }
        return n;
    }

    @Nullable
    public static synchronized Order byId(long id) {
        ensure();
        for (Order o : ORDERS) {
            if (o.id == id) {
                return o;
            }
        }
        return null;
    }

    /** Ключ заказа для предмета: инструменты — без учёта износа, остальное — точь-в-точь. */
    public static ItemKey keyOf(ItemStack stack) {
        boolean damageable = stack.getMaxDamage() > 0;
        return ItemKey.of(stack, stack.hasTagCompound() && !damageable, !damageable);
    }

    /** Новый заказ. Возвращает текст ошибки или null. */
    @Nullable
    public static synchronized String create(EntityPlayerMP player, ItemStack sample, int count, long price) {
        ensure();
        if (!ExtraConfig.ordersEnabled) {
            return "Заказы отключены на сервере.";
        }
        if (sample.isEmpty()) {
            return "Возьми в руку предмет, который хочешь купить.";
        }
        if (count <= 0 || count > 64 * 36) {
            return "Количество — от 1 до " + (64 * 36) + ".";
        }
        if (price <= 0) {
            return "Цена за штуку должна быть больше нуля.";
        }
        if (openCount(player.getUniqueID()) >= ExtraConfig.ordersPerPlayer) {
            return "У тебя уже " + ExtraConfig.ordersPerPlayer + " открытых заказов — закрой лишние.";
        }
        long total;
        try {
            total = Math.multiplyExact(price, (long) count);
        } catch (ArithmeticException e) {
            return "Слишком большая сумма.";
        }
        if (ExtraConfig.ordersMaxTotal > 0 && total > ExtraConfig.ordersMaxTotal) {
            return "Заказ дороже " + Fmt.money(ExtraConfig.ordersMaxTotal) + " — так нельзя.";
        }
        ItemStack one = sample.copy();
        one.setCount(1);
        MoneyNote.set("Заказ: залог за " + count + "× " + ChatUtil.strip(one.getDisplayName()));
        boolean paid;
        try {
            paid = EconomyManager.take(player.getUniqueID(), total);
        } finally {
            MoneyNote.clear();
        }
        if (!paid) {
            return "Не хватает денег: заказ стоит " + Fmt.money(total) + ".";
        }
        Order o = new Order();
        o.id = nextId++;
        o.owner = player.getUniqueID().toString();
        o.ownerName = player.getName();
        o.key = keyOf(one).serialize();
        o.sample = ItemCodec.encode(one);
        o.itemName = ChatUtil.strip(one.getDisplayName());
        o.want = count;
        o.price = price;
        o.escrow = total;
        o.created = System.currentTimeMillis();
        o.expires = o.created + ExtraConfig.ordersDays * 86_400_000L;
        ORDERS.add(o);
        changed();
        PacketHandler.sendPlayerState(player);
        return null;
    }

    /** Сколько подходящих вещей у игрока в инвентаре. */
    public static int countIn(EntityPlayerMP player, Order o) {
        ItemKey key = o.itemKey();
        int n = 0;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack s = player.inventory.getStackInSlot(i);
            if (!s.isEmpty() && key.matches(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /**
     * Сдать вещи в заказ (не больше max). Возвращает {сдано, получено денег}; при ошибке бросает
     * IllegalStateException с текстом для игрока.
     */
    public static synchronized long[] fulfil(EntityPlayerMP player, long orderId, int max) {
        ensure();
        Order o = byId(orderId);
        if (o == null || o.closed) {
            throw new IllegalStateException("Заказ уже закрыт.");
        }
        if (o.owner.equals(player.getUniqueID().toString())) {
            throw new IllegalStateException("Это твой заказ.");
        }
        int need = Math.min(o.left(), max);
        if (need <= 0) {
            throw new IllegalStateException("Заказ уже выполнен.");
        }
        ItemKey key = o.itemKey();
        int taken = 0;
        for (int i = 0; i < player.inventory.getSizeInventory() && taken < need; i++) {
            ItemStack s = player.inventory.getStackInSlot(i);
            if (s.isEmpty() || !key.matches(s)) {
                continue;
            }
            int n = Math.min(s.getCount(), need - taken);
            ItemStack part = player.inventory.decrStackSize(i, n);
            if (!part.isEmpty()) {
                o.ready.add(ItemCodec.encode(part));
                taken += part.getCount();
            }
        }
        if (taken <= 0) {
            throw new IllegalStateException("В инвентаре нет «" + o.itemName + "».");
        }
        player.inventoryContainer.detectAndSendChanges();
        long pay = Math.min(o.escrow, o.price * taken);
        o.escrow -= pay;
        o.filled += taken;
        MoneyNote.set("Заказ " + o.ownerName + ": " + taken + "× " + o.itemName);
        try {
            EconomyManager.give(player.getUniqueID(), pay);
        } finally {
            MoneyNote.clear();
        }
        if (o.left() <= 0) {
            close(o, false);
        }
        changed();
        PacketHandler.sendPlayerState(player);
        EntityPlayerMP owner = PlayerUtil.online(o.ownerId());
        if (owner != null) {
            ChatUtil.send(owner, "&aПо твоему заказу сдали &f" + taken + "× " + o.itemName
                    + " &7(" + o.filled + "/" + o.want + "). &aЗабери в магазине → «Заказы».");
        }
        return new long[]{taken, pay};
    }

    /** Забрать сданные вещи. Возвращает, сколько выдано. */
    public static synchronized int collect(EntityPlayerMP player, long orderId) {
        Order o = byId(orderId);
        if (o == null || !o.owner.equals(player.getUniqueID().toString())) {
            throw new IllegalStateException("Заказ не найден.");
        }
        int n = 0;
        for (String r : o.ready) {
            ItemStack s = ItemCodec.decode(r);
            if (!s.isEmpty()) {
                n += s.getCount();
                PlayerUtil.giveOrDrop(player, s);
            }
        }
        o.ready.clear();
        cleanup();
        changed();
        return n;
    }

    /** Закрыть заказ: несданное — деньги назад заказчику. */
    public static synchronized void cancel(EntityPlayerMP player, long orderId, boolean admin) {
        Order o = byId(orderId);
        if (o == null || (!admin && !o.owner.equals(player.getUniqueID().toString()))) {
            throw new IllegalStateException("Заказ не найден или он не твой.");
        }
        if (o.closed) {
            throw new IllegalStateException("Заказ уже закрыт.");
        }
        close(o, true);
        changed();
    }

    private static void close(Order o, boolean refund) {
        o.closed = true;
        if (o.escrow > 0) {
            long back = o.escrow;
            o.escrow = 0;
            MoneyNote.set("Заказ закрыт: возврат залога");
            try {
                EconomyManager.give(o.ownerId(), back);
            } finally {
                MoneyNote.clear();
            }
            EntityPlayerMP owner = PlayerUtil.online(o.ownerId());
            if (owner != null && refund) {
                ChatUtil.send(owner, "&eЗаказ «" + o.itemName + "» закрыт, вернулось &6" + Fmt.money(back));
            }
        }
        cleanup();
    }

    private static void cleanup() {
        ORDERS.removeIf(x -> x.closed && x.ready.isEmpty());
    }

    /** Раз в секунду: просроченные заказы закрываются. */
    public static synchronized void tick() {
        if (!loaded || ++tick % 30 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean any = false;
        for (Order o : new ArrayList<Order>(ORDERS)) {
            if (!o.closed && o.expires > 0 && o.expires < now) {
                close(o, true);
                any = true;
            }
        }
        if (any) {
            changed();
        }
    }
}
