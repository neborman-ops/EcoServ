package com.ecoserv.command;

import com.ecoserv.trade.TradeManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** /trade — обмен предметами и деньгами между игроками. */
public class CommandTrade extends CommandBase {

    @Override
    public String getName() {
        return "trade";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/trade <ник> | /trade accept [ник] | /trade deny | /trade cancel";
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
        if (!Perms.has(player, Perms.TRADE)) {
            ChatUtil.send(sender, "&cНет права на торговлю.");
            return;
        }
        if (args.length == 0) {
            ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
            return;
        }
        String sub = args[0].toLowerCase();

        if ("accept".equals(sub)) {
            TradeManager.accept(player, args.length > 1 ? args[1] : null);
            return;
        }
        if ("deny".equals(sub) || "decline".equals(sub)) {
            TradeManager.deny(player);
            return;
        }
        if ("cancel".equals(sub)) {
            TradeManager.abortFor(player.getUniqueID(), "отменено командой");
            return;
        }

        EntityPlayerMP target = PlayerUtil.online(args[0]);
        if (target == null) {
            ChatUtil.send(sender, "&cИгрок &f" + args[0] + " &cне в сети.");
            return;
        }
        TradeManager.sendRequest(player, target);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            List<String> options = new ArrayList<String>();
            options.add("accept");
            options.add("deny");
            options.add("cancel");
            String[] names = server.getOnlinePlayerNames();
            for (int i = 0; i < names.length; i++) {
                options.add(names[i]);
            }
            return getListOfStringsMatchingLastWord(args, options);
        }
        return new ArrayList<String>();
    }
}
