package com.ecoserv.integration.computercraft;

import com.ecoserv.auction.AuctionManager;
import com.ecoserv.auction.Listing;
import com.ecoserv.tile.TileVendor;
import com.ecoserv.util.PlayerUtil;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Склад продавца как периферия {@code ecoserv_vendor}: что на складе, лоты владельца на аукционе.
 * Для владельца (после auth, владелец должен быть в сети): выставить всё со склада по цене, снять свои лоты.
 */
public class VendorPeripheral extends BasePeripheral {

    private final TileVendor vendor;

    public VendorPeripheral(TileVendor tile) {
        super(tile, "getStock", "getListings", "listAll", "cancelAll");
        this.vendor = tile;
    }

    @Nonnull
    @Override
    public String getType() {
        return "ecoserv_vendor";
    }

    @Nullable
    @Override
    protected UUID owner() {
        return vendor.getOwner();
    }

    @Override
    protected String ownerName() {
        return vendor.getOwnerName();
    }

    @Nullable
    @Override
    protected Object[] own(String method, Object[] args, IComputerAccess computer) throws LuaException {
        switch (method) {
            case "getStock": {
                List<Object> out = new ArrayList<Object>();
                for (int i = 0; i < vendor.getInventory().getSlots(); i++) {
                    ItemStack s = vendor.getInventory().getStackInSlot(i);
                    if (!s.isEmpty()) {
                        out.add(stack(s));
                    }
                }
                return new Object[]{list(out)};
            }
            case "getListings": {
                List<Object> out = new ArrayList<Object>();
                UUID owner = vendor.getOwner();
                if (owner != null) {
                    for (Listing l : AuctionManager.of(owner)) {
                        out.add(listing(l));
                    }
                }
                return new Object[]{list(out)};
            }
            case "listAll": {
                requireOwner(computer);
                EntityPlayerMP owner = PlayerUtil.online(vendor.getOwner());
                if (owner == null) {
                    throw new LuaException("Владелец склада должен быть в сети");
                }
                double price = args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).doubleValue() : vendor.getPrice();
                return new Object[]{AuctionManager.listAll(vendor, owner, price)};
            }
            case "cancelAll": {
                requireOwner(computer);
                UUID owner = vendor.getOwner();
                return new Object[]{owner == null ? 0 : AuctionManager.cancelFrom(vendor, owner)};
            }
            default:
                throw new LuaException("Нет такого метода");
        }
    }
}
