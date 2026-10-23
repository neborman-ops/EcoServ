package com.ecoserv.integration.computercraft;

import com.ecoserv.shop.SaleResult;
import com.ecoserv.shop.ShopEntry;
import com.ecoserv.shop.ShopList;
import com.ecoserv.tile.TileMarket;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Торговый терминал (скупщик) как периферия {@code ecoserv_market}: что лежит, почём примут, продать сейчас
 * (деньги — владельцу терминала, как обычно). Для владельца — включить/выключить автопродажу.
 */
public class MarketPeripheral extends BasePeripheral {

    private final TileMarket market;

    public MarketPeripheral(TileMarket tile) {
        super(tile, "getContents", "quote", "sellNow", "getStats", "setAutoSell");
        this.market = tile;
    }

    @Nonnull
    @Override
    public String getType() {
        return "ecoserv_market";
    }

    @Nullable
    @Override
    protected UUID owner() {
        return market.getOwner();
    }

    @Override
    protected String ownerName() {
        return market.getOwnerName();
    }

    @Nullable
    @Override
    protected Object[] own(String method, Object[] args, IComputerAccess computer) throws LuaException {
        switch (method) {
            case "getContents": {
                List<Object> out = new ArrayList<Object>();
                for (int i = 0; i < TileMarket.SIZE; i++) {
                    ItemStack s = market.getInventory().getStackInSlot(i);
                    if (!s.isEmpty()) {
                        Map<Object, Object> m = stack(s);
                        m.put("slot", i + 1);
                        out.add(m);
                    }
                }
                return new Object[]{list(out)};
            }
            case "quote": {
                // сколько примерно дадут за содержимое по прайсу мира терминала
                ShopList shop = ShopList.forDim(tile.getWorld().provider.getDimension());
                double total = 0;
                for (int i = 0; i < TileMarket.SIZE; i++) {
                    ItemStack s = market.getInventory().getStackInSlot(i);
                    ShopEntry e = s.isEmpty() ? null : shop.find(s);
                    if (e != null && e.canSell()) {
                        total += e.getEffectiveSellPrice() / Math.max(1, e.batchSize()) * s.getCount();
                    }
                }
                return new Object[]{Math.floor(total)};
            }
            case "sellNow": {
                SaleResult r = market.sell(null);
                Map<Object, Object> m = new HashMap<Object, Object>();
                m.put("money", r.net);
                m.put("tax", r.tax);
                m.put("items", r.itemsSold);
                m.put("waiting", r.describeWaiting());
                return new Object[]{m};
            }
            case "getStats": {
                Map<Object, Object> m = new HashMap<Object, Object>();
                m.put("earned", market.getEarnedTotal());
                m.put("itemsSold", market.getItemsSoldTotal());
                m.put("lastSale", market.getLastSaleAmount());
                m.put("autoSell", market.isAutoSell());
                return new Object[]{m};
            }
            case "setAutoSell": {
                requireOwner(computer);
                market.setAutoSell(args.length > 0 && Boolean.TRUE.equals(args[0]));
                return new Object[]{market.isAutoSell()};
            }
            default:
                throw new LuaException("Нет такого метода");
        }
    }
}
