package com.ecoserv.integration.computercraft;

import com.ecoserv.cc.CcRequests;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.extra.MoneyNote;
import com.ecoserv.tile.TileVending;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.PlayerUtil;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Торговый автомат как периферия {@code ecoserv_vending}. Магазин на компьютере: показать товары всех
 * подключённых автоматов, игрок выбирает — requestBuy(ник, раз) — он подтверждает в чате — автомат продаёт
 * как при клике (деньги в кассу, товар игроку), компьютеру приходит событие ecoserv_purchase.
 * Для владельца (после auth): setOffer, dispense (выдать товар в соседний инвентарь), deliver (игроку), takeCash.
 */
public class VendingPeripheral extends BasePeripheral {

    private final TileVending vending;

    public VendingPeripheral(TileVending tile) {
        super(tile, "getOffer", "getStock", "getCash", "requestBuy", "setOffer", "dispense", "deliver", "takeCash");
        this.vending = tile;
    }

    @Nonnull
    @Override
    public String getType() {
        return "ecoserv_vending";
    }

    @Nullable
    @Override
    protected UUID owner() {
        return vending.getOwner();
    }

    @Override
    protected String ownerName() {
        return vending.isAdminShop() ? "сервер" : vending.getOwnerName();
    }

    private int stockItems() {
        int n = 0;
        for (int i = TileVending.STOCK_START; i < TileVending.SIZE; i++) {
            ItemStack s = vending.getInventory().getStackInSlot(i);
            if (matches(vending.getSample(), s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Забрать со склада до count штук товара. */
    private List<ItemStack> takeStock(int count) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        int left = count;
        for (int i = TileVending.STOCK_START; i < TileVending.SIZE && left > 0; i++) {
            ItemStack s = vending.getInventory().getStackInSlot(i);
            if (!matches(vending.getSample(), s)) {
                continue;
            }
            ItemStack part = vending.getInventory().extractItem(i, Math.min(left, s.getCount()), false);
            if (!part.isEmpty()) {
                out.add(part);
                left -= part.getCount();
            }
        }
        vending.markDirtyAndSync();
        return out;
    }

    /** Тот же предмет, что на витрине (как в TileVending). */
    private static boolean matches(ItemStack sample, ItemStack stack) {
        if (sample.isEmpty() || stack.isEmpty() || sample.getItem() != stack.getItem()) {
            return false;
        }
        if (sample.getItemDamage() != stack.getItemDamage()) {
            return false;
        }
        return String.valueOf(sample.getTagCompound()).equals(String.valueOf(stack.getTagCompound()));
    }

    /** Забрать кассу автомата (поле storedMoney закрыто — через рефлексию, чтобы не трогать сам блок). */
    private long takeCash() throws LuaException {
        try {
            java.lang.reflect.Field f = TileVending.class.getDeclaredField("storedMoney");
            f.setAccessible(true);
            long v = f.getLong(vending);
            f.setLong(vending, 0L);
            vending.markDirtyAndSync();
            return v;
        } catch (ReflectiveOperationException e) {
            throw new LuaException("Не удалось открыть кассу: " + e);
        }
    }

    @Nullable
    @Override
    protected Object[] own(String method, Object[] args, IComputerAccess computer) throws LuaException {
        switch (method) {
            case "getOffer": {
                Map<Object, Object> m = new HashMap<Object, Object>();
                ItemStack sample = vending.getSample();
                m.put("configured", !sample.isEmpty() && vending.getPrice() > 0);
                m.put("item", sample.isEmpty() ? null : stack(sample));
                m.put("amount", vending.getAmount());
                m.put("price", vending.getPrice());
                m.put("stock", vending.isAdminShop() ? -1 : stockItems());
                m.put("purchases", vending.isAdminShop() ? -1 : stockItems() / Math.max(1, vending.getAmount()));
                m.put("adminShop", vending.isAdminShop());
                m.put("owner", ownerName());
                return new Object[]{m};
            }
            case "getStock": {
                List<Object> out = new ArrayList<Object>();
                for (int i = TileVending.STOCK_START; i < TileVending.SIZE; i++) {
                    ItemStack s = vending.getInventory().getStackInSlot(i);
                    if (!s.isEmpty()) {
                        out.add(stack(s));
                    }
                }
                return new Object[]{list(out)};
            }
            case "getCash":
                return new Object[]{vending.getStoredMoney()};
            case "requestBuy": {
                final EntityPlayerMP p = nearbyPlayer(args, 0);
                final int times = args.length > 1 && args[1] instanceof Number
                        ? Math.max(1, Math.min(64, ((Number) args[1]).intValue())) : 1;
                ItemStack sample = vending.getSample();
                if (sample.isEmpty() || vending.getPrice() <= 0) {
                    throw new LuaException("Автомат не настроен");
                }
                long total = vending.getPrice() * times;
                CcRequests.Request r = CcRequests.create(p, "ecoserv_purchase",
                        "Купить &f" + sample.getDisplayName() + " ×" + (vending.getAmount() * times) + " &fза &6"
                                + Fmt.money(total) + " &fу &e" + ownerName() + "&f?",
                        player -> {
                            if (vending.isInvalid()) {
                                throw new IllegalStateException("Автомат сломан.");
                            }
                            int bought = 0;
                            long spent = 0;
                            String status = "ok";
                            for (int i = 0; i < times; i++) {
                                TileVending.BuyResult res = vending.buy(player);
                                if (res.status != TileVending.BuyStatus.OK) {
                                    status = res.status.name().toLowerCase();
                                    break;
                                }
                                bought++;
                                spent += res.price;
                            }
                            ChatUtil.send(player, bought > 0 ? "&aКуплено: &f" + vending.getSample().getDisplayName() + " ×"
                                    + (bought * vending.getAmount()) + " &7за &6" + Fmt.money(spent)
                                    : "&cНе куплено: " + status);
                            return new Object[]{status, player.getName(), bought, bought * vending.getAmount(), spent};
                        }, notifier(computer));
                return new Object[]{r.id};
            }
            case "setOffer": {
                requireOwner(computer);
                long price = number(args, 0);
                int amount = args.length > 1 ? (int) number(args, 1) : vending.getAmount();
                vending.configure(price, amount);
                return new Object[]{vending.getPrice(), vending.getAmount()};
            }
            case "dispense": {
                requireOwner(computer);
                int count = (int) Math.max(1, Math.min(64 * 27, number(args, 0)));
                IItemHandler target = neighbour(args.length > 1 ? string(args, 1) : "down");
                int moved = 0;
                for (ItemStack part : takeStock(count)) {
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(target, part, false);
                    moved += part.getCount() - rest.getCount();
                    if (!rest.isEmpty()) {
                        // не влезло — обратно на склад
                        for (int i = TileVending.STOCK_START; i < TileVending.SIZE && !rest.isEmpty(); i++) {
                            rest = vending.getInventory().insertItem(i, rest, false);
                        }
                    }
                }
                return new Object[]{moved};
            }
            case "deliver": {
                requireOwner(computer);
                EntityPlayerMP p = PlayerUtil.online(string(args, 0));
                if (p == null) {
                    throw new LuaException("Игрок не в сети");
                }
                int count = (int) Math.max(1, Math.min(64 * 27, number(args, 1)));
                int given = 0;
                for (ItemStack part : takeStock(count)) {
                    given += part.getCount();
                    PlayerUtil.giveOrDrop(p, part);
                }
                return new Object[]{given};
            }
            case "takeCash": {
                requireOwner(computer);
                UUID owner = vending.getOwner();
                if (owner == null) {
                    return new Object[]{0};
                }
                long cash = takeCash();
                if (cash > 0) {
                    MoneyNote.set("Касса автомата (компьютер)");
                    try {
                        EconomyManager.give(owner, cash);
                    } finally {
                        MoneyNote.clear();
                    }
                }
                return new Object[]{cash};
            }
            default:
                throw new LuaException("Нет такого метода");
        }
    }
}
