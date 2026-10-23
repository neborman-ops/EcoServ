package com.ecoserv.gui;

import com.ecoserv.tile.TileMarket;
import com.ecoserv.tile.TileVending;
import com.ecoserv.tile.TileVendor;
import com.ecoserv.trade.ContainerTrade;
import com.ecoserv.trade.TradeManager;
import com.ecoserv.trade.TradeSession;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import javax.annotation.Nullable;

public class GuiHandler implements IGuiHandler {

    public static final int GUI_MARKET = 0;
    public static final int GUI_TRADE = 1;
    public static final int GUI_VENDOR = 2;
    public static final int GUI_VENDING = 3;

    @Nullable
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GUI_MARKET) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof TileMarket && ((TileMarket) te).canAccess(player)) {
                return new ContainerMarket(player, (TileMarket) te);
            }
            return null;
        }
        if (id == GUI_TRADE) {
            TradeSession session = TradeManager.get(x);
            return session == null ? null : new ContainerTrade(player, session);
        }
        if (id == GUI_VENDOR) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof TileVendor && ((TileVendor) te).canAccess(player)) {
                return new ContainerVendor(player, (TileVendor) te);
            }
            return null;
        }
        if (id == GUI_VENDING) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof TileVending && ((TileVending) te).canAccess(player)) {
                return new ContainerVending(player, (TileVending) te);
            }
            return null;
        }
        return null;
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GUI_MARKET) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof TileMarket) {
                TileMarket market = (TileMarket) te;
                return new GuiMarket(new ContainerMarket(player, market), market);
            }
            return null;
        }
        if (id == GUI_TRADE) {
            // на клиенте содержимое слотов приезжает синхронизацией контейнера
            TradeSession dummy = TradeSession.clientDummy(x, player.getName(), "");
            return new GuiTrade(new ContainerTrade(player, dummy));
        }
        if (id == GUI_VENDOR) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof TileVendor) {
                TileVendor vendor = (TileVendor) te;
                return new GuiVendor(new ContainerVendor(player, vendor), vendor);
            }
            return null;
        }
        if (id == GUI_VENDING) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof TileVending) {
                TileVending vending = (TileVending) te;
                return new GuiVending(new ContainerVending(player, vending), vending);
            }
            return null;
        }
        return null;
    }
}
