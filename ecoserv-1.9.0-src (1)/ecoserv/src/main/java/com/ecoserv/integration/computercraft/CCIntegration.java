package com.ecoserv.integration.computercraft;

import com.ecoserv.EcoServ;
import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.tile.TileMarket;
import com.ecoserv.tile.TileVendor;
import com.ecoserv.tile.TileVending;
import dan200.computercraft.api.ComputerCraftAPI;
import net.minecraft.tileentity.TileEntity;

/**
 * ComputerCraft (CC: Tweaked 1.12 или ComputerCraft 1.80). Каждый блок мода — своя периферия, их можно
 * подключить к одному компьютеру сколько угодно (проводные модемы):
 * <ul>
 * <li>{@code ecoserv_vending} — торговый автомат: товар, остаток, покупка игроком с подтверждением,
 * выдача товара и касса для владельца;</li>
 * <li>{@code ecoserv_market} — торговый терминал (скупщик): содержимое, цены, продать сейчас;</li>
 * <li>{@code ecoserv_vendor} — склад продавца: остаток, лоты аукциона, выставить/снять для владельца.</li>
 * </ul>
 * У всех — общие методы (баланс, онлайн, прайс, аукцион, заказы, лобби гонки, оплата с подтверждением).
 * Компилируется, только если jar ComputerCraft лежит в libs/; подключается из EcoServ.init через рефлексию.
 */
public final class CCIntegration {

    private CCIntegration() {
    }

    public static void init() {
        ComputerCraftAPI.registerPeripheralProvider((world, pos, side) -> {
            if (!ExtraConfig.ccEnabled || world == null || world.isRemote) {
                return null;
            }
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileVending) {
                return new VendingPeripheral((TileVending) te);
            }
            if (te instanceof TileMarket) {
                return new MarketPeripheral((TileMarket) te);
            }
            if (te instanceof TileVendor) {
                return new VendorPeripheral((TileVendor) te);
            }
            return null;
        });
        EcoServ.LOG.info("ComputerCraft: периферии ecoserv_vending / ecoserv_market / ecoserv_vendor");
    }
}
