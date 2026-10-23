package com.ecoserv.auction;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.economy.PlayerStats;
import com.ecoserv.economy.StatsStore;
import com.ecoserv.tile.TileVendor;
import com.ecoserv.trade.TradeMailbox;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.PlayerUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Аукцион игроков. Лоты создаются из блока-склада: товар физически уходит
 * из блока в лот, поэтому покупка не зависит от того, прогружен ли чанк продавца.
 * Непроданное возвращается в блок, а если блока уже нет — в «почту» продавца.
 */
public final class AuctionManager {

    public enum Status {
        OK,
        DISABLED,
        NOT_FOUND,
        NO_MONEY,
        OWN_LISTING,
        EMPTY,
        LIMIT,
        BAD_PRICE
    }

    public static class Result {
        public Status status = Status.OK;
        public long price;
        public int count;
        public String itemName = "";
        public String sellerName = "";
    }

    private static final Type LIST_TYPE = new TypeToken<ArrayList<Listing>>() {
    }.getType();

    private static final List<Listing> listings = new ArrayList<Listing>();
    private static long nextId = 1L;
    private static volatile boolean dirty;
    private static int revision;

    private AuctionManager() {
    }

    public static List<Listing> all() {
        return listings;
    }

    public static int revision() {
        return revision;
    }

    @Nullable
    public static Listing byId(long id) {
        for (int i = 0; i < listings.size(); i++) {
            if (listings.get(i).id == id) {
                return listings.get(i);
            }
        }
        return null;
    }

    public static List<Listing> of(UUID seller) {
        List<Listing> result = new ArrayList<Listing>();
        String key = seller.toString();
        for (int i = 0; i < listings.size(); i++) {
            if (listings.get(i).seller.equals(key)) {
                result.add(listings.get(i));
            }
        }
        return result;
    }

    public static int countOf(UUID seller) {
        return of(seller).size();
    }

    // --- создание лотов -----------------------------------------------------

    /**
     * Выставляет всё содержимое блока-склада по указанной цене за штуку.
     *
     * @return сколько предметов ушло на аукцион
     */
    public static int listAll(TileVendor vendor, EntityPlayerMP seller, double pricePerItem) {
        if (!EcoConfig.auctionEnabled) {
            ChatUtil.send(seller, "&cАукцион отключён на сервере.");
            return 0;
        }
        if (pricePerItem < EcoConfig.auctionMinPrice || pricePerItem > EcoConfig.auctionMaxPrice) {
            ChatUtil.send(seller, "&cЦена должна быть от &f" + EcoConfig.auctionMinPrice
                    + " &cдо &f" + EcoConfig.auctionMaxPrice);
            return 0;
        }

        int listed = 0;
        String sellerId = seller.getUniqueID().toString();
        BlockPos pos = vendor.getPos();
        int dim = vendor.getWorld().provider.getDimension();

        for (int slot = 0; slot < vendor.getInventory().getSlots(); slot++) {
            ItemStack stack = vendor.getInventory().getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Listing existing = null;
            for (int i = 0; i < listings.size(); i++) {
                if (listings.get(i).matches(stack, pricePerItem, sellerId)) {
                    existing = listings.get(i);
                    break;
                }
            }
            if (existing == null) {
                if (countOf(seller.getUniqueID()) >= EcoConfig.auctionMaxListingsPerPlayer) {
                    ChatUtil.send(seller, "&cЛимит лотов: &f" + EcoConfig.auctionMaxListingsPerPlayer
                            + "&c. Сними что-нибудь с продажи.");
                    break;
                }
                Listing listing = Listing.of(stack, stack.getCount(), pricePerItem,
                        sellerId, seller.getName(), dim, pos.toLong());
                listing.id = nextId++;
                listings.add(listing);
            } else {
                existing.count += stack.getCount();
                existing.dim = dim;
                existing.pos = pos.toLong();
            }
            listed += stack.getCount();
            vendor.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
        }

        if (listed > 0) {
            vendor.setPrice(pricePerItem);
            vendor.markDirtyAndSync();
            touch();
            if (EcoConfig.auctionAnnounceNew) {
                ChatUtil.broadcast("&e" + seller.getName() + " выставил на аукцион " + listed + " предметов");
            }
        }
        return listed;
    }

    // --- покупка ------------------------------------------------------------

    public static Result buy(EntityPlayerMP buyer, long id, int amount) {
        Result result = new Result();
        if (!EcoConfig.auctionEnabled) {
            result.status = Status.DISABLED;
            return result;
        }
        Listing listing = byId(id);
        if (listing == null || listing.count <= 0) {
            result.status = Status.NOT_FOUND;
            return result;
        }
        if (listing.seller.equals(buyer.getUniqueID().toString())) {
            result.status = Status.OWN_LISTING;
            return result;
        }
        int count = Math.max(1, Math.min(amount, listing.count));
        long price = listing.totalPrice(count);

        if (!EconomyManager.take(buyer.getUniqueID(), price)) {
            result.status = Status.NO_MONEY;
            result.price = price;
            return result;
        }

        long tax = (long) Math.floor(price * Math.max(0.0D, EcoConfig.auctionTaxPercent) / 100.0D);
        long payout = Math.max(0L, price - tax);

        UUID sellerId = parse(listing.seller);
        if (sellerId != null) {
            EconomyManager.give(sellerId, payout);
            PlayerStats sellerStats = StatsStore.get(sellerId);
            sellerStats.totalEarned += payout;
            EntityPlayerMP sellerOnline = PlayerUtil.online(sellerId);
            if (sellerOnline != null) {
                ChatUtil.send(sellerOnline, "&aПродано на аукционе: &f" + listing.displayName()
                        + " x" + count + " &7-> &e" + Fmt.money(payout)
                        + (tax > 0L ? " &7(комиссия " + Fmt.money(tax) + ")" : ""));
            }
        }

        int left = count;
        ItemStack sample = listing.stack();
        while (left > 0 && !sample.isEmpty()) {
            int portion = Math.min(left, sample.getMaxStackSize());
            ItemStack give = sample.copy();
            give.setCount(portion);
            PlayerUtil.giveOrDrop(buyer, give);
            left -= portion;
        }

        // счётчики на самом блоке-складе, если он прогружен
        World sellerWorld = DimensionManager.getWorld(listing.dim);
        if (sellerWorld != null) {
            BlockPos vendorPos = BlockPos.fromLong(listing.pos);
            if (sellerWorld.isBlockLoaded(vendorPos)) {
                net.minecraft.tileentity.TileEntity te = sellerWorld.getTileEntity(vendorPos);
                if (te instanceof TileVendor) {
                    ((TileVendor) te).addSale(count, payout);
                }
            }
        }

        listing.count -= count;
        if (listing.count <= 0) {
            listings.remove(listing);
        }
        PlayerStats buyerStats = StatsStore.get(buyer.getUniqueID());
        buyerStats.totalSpent += price;
        StatsStore.markDirty();
        touch();

        result.count = count;
        result.price = price;
        result.itemName = listing.displayName();
        result.sellerName = listing.sellerName;
        return result;
    }

    // --- снятие с продажи ---------------------------------------------------

    /** Снимает лот: товар возвращается в блок-склад, иначе в почту. */
    public static boolean cancel(EntityPlayerMP player, long id, boolean adminOverride) {
        Listing listing = byId(id);
        if (listing == null) {
            return false;
        }
        if (!adminOverride && !listing.seller.equals(player.getUniqueID().toString())) {
            return false;
        }
        returnItems(listing);
        listings.remove(listing);
        touch();
        return true;
    }

    /** Снимает все лоты, выставленные из этого блока. */
    public static int cancelFrom(TileVendor vendor, UUID owner) {
        long pos = vendor.getPos().toLong();
        int dim = vendor.getWorld().provider.getDimension();
        String seller = owner.toString();
        int removed = 0;

        for (int i = listings.size() - 1; i >= 0; i--) {
            Listing listing = listings.get(i);
            if (listing.pos != pos || listing.dim != dim || !listing.seller.equals(seller)) {
                continue;
            }
            int left = insertInto(vendor, listing);
            if (left <= 0) {
                listings.remove(i);
                removed++;
            } else {
                listing.count = left;
            }
        }
        if (removed > 0) {
            touch();
            vendor.markDirtyAndSync();
        }
        return removed;
    }

    /** Кладёт товар лота обратно в блок. Возвращает, сколько не влезло. */
    private static int insertInto(TileVendor vendor, Listing listing) {
        ItemStack sample = listing.stack();
        if (sample.isEmpty()) {
            return 0;
        }
        int left = listing.count;
        for (int slot = 0; slot < vendor.getInventory().getSlots() && left > 0; slot++) {
            ItemStack give = sample.copy();
            give.setCount(Math.min(left, sample.getMaxStackSize()));
            ItemStack rest = vendor.getInventory().insertItem(slot, give, false);
            left -= give.getCount() - (rest.isEmpty() ? 0 : rest.getCount());
        }
        return Math.max(0, left);
    }

    /** Возврат товара продавцу: в блок, если он на месте, иначе в почту. */
    private static void returnItems(Listing listing) {
        UUID seller = parse(listing.seller);
        if (seller == null || listing.count <= 0) {
            return;
        }
        World world = DimensionManager.getWorld(listing.dim);
        if (world != null) {
            BlockPos pos = BlockPos.fromLong(listing.pos);
            if (world.isBlockLoaded(pos)) {
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
                if (te instanceof TileVendor) {
                    TileVendor vendor = (TileVendor) te;
                    int left = insertInto(vendor, listing);
                    vendor.markDirtyAndSync();
                    if (left <= 0) {
                        return;
                    }
                    listing.count = left;
                }
            }
        }
        ItemStack sample = listing.stack();
        int left = listing.count;
        while (left > 0 && !sample.isEmpty()) {
            ItemStack part = sample.copy();
            part.setCount(Math.min(left, sample.getMaxStackSize()));
            EntityPlayerMP online = PlayerUtil.online(seller);
            if (online != null) {
                PlayerUtil.giveOrDrop(online, part);
            } else {
                TradeMailbox.store(seller, part);
            }
            left -= part.getCount();
        }
    }

    @Nullable
    private static UUID parse(String uuid) {
        try {
            return UUID.fromString(uuid);
        } catch (Exception e) {
            return null;
        }
    }

    /** Автоснятие просроченных лотов. */
    public static void tick() {
        if (EcoConfig.auctionListingDays <= 0 || listings.isEmpty()) {
            return;
        }
        long limit = EcoConfig.auctionListingDays * 86_400_000L;
        long now = System.currentTimeMillis();
        for (int i = listings.size() - 1; i >= 0; i--) {
            Listing listing = listings.get(i);
            if (now - listing.created > limit) {
                returnItems(listing);
                listings.remove(i);
                touch();
            }
        }
    }

    public static void sortByNewest() {
        Collections.sort(listings, new Comparator<Listing>() {
            @Override
            public int compare(Listing a, Listing b) {
                return Long.compare(b.created, a.created);
            }
        });
    }

    private static void touch() {
        dirty = true;
        revision++;
        com.ecoserv.network.PacketHandler.sendAuctionToAll();
    }

    // --- хранение -----------------------------------------------------------

    public static void load() {
        listings.clear();
        ArrayList<Listing> loaded = JsonUtil.readType(DataStore.file("auction.json"), LIST_TYPE,
                new ArrayList<Listing>());
        listings.addAll(loaded);
        nextId = 1L;
        for (int i = 0; i < listings.size(); i++) {
            listings.get(i).invalidate();
            if (listings.get(i).id >= nextId) {
                nextId = listings.get(i).id + 1L;
            }
        }
        revision++;
        dirty = false;
        EcoServ.LOG.info("Аукцион: {} лотов", listings.size());
    }

    public static void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public static void save() {
        if (JsonUtil.write(DataStore.file("auction.json"), listings)) {
            dirty = false;
        }
    }

    public static String toJson() {
        return JsonUtil.GSON.toJson(listings);
    }

    /** Применение присланного сервером списка (клиент). */
    public static void fromJson(String json) {
        listings.clear();
        try {
            ArrayList<Listing> parsed = JsonUtil.GSON.fromJson(json, LIST_TYPE);
            if (parsed != null) {
                for (int i = 0; i < parsed.size(); i++) {
                    parsed.get(i).invalidate();
                }
                listings.addAll(parsed);
            }
        } catch (Exception e) {
            EcoServ.LOG.error("Не удалось разобрать присланный аукцион", e);
        }
        revision++;
    }
}
