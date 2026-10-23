package com.ecoserv.economy;

import com.ecoserv.shop.ShopScreen;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;
import java.util.List;

/** /orders (/заказы) — окно магазина на вкладке «Заказы». */
public class CommandOrders extends CommandBase {

    @Override
    public String getName() {
        return "orders";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("заказы", "заказ", "order");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/orders — заказы: купить вещи у игроков или сдать свои";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        ShopScreen.open(player, ShopScreen.Tab.ORDERS);
    }
}
