package com.ecoserv.command;

import com.ecoserv.restrict.Stash;
import com.ecoserv.restrict.StashScreen;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * /stash — свой буфер предметов (то, что нельзя носить в текущем мире).
 * Админ: /stash &lt;игрок&gt; — посмотреть, /stash &lt;игрок&gt; clear — очистить.
 */
public class CommandStash extends CommandBase {

    @Override
    public String getName() {
        return "stash";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("buffer");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/stash | /stash <игрок> [clear]";
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
        if (args.length == 0) {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            if (!com.ecoserv.restrict.RestrictionService.isAlive(player)) {
                return;
            }
            new StashScreen(player, player.getUniqueID(), player.getName()).open();
            return;
        }
        if (!Perms.isAdmin(sender)) {
            ChatUtil.send(sender, "&cЧужой буфер может смотреть только админ.");
            return;
        }
        UUID target = PlayerUtil.uuidOf(args[0]);
        if (target == null) {
            ChatUtil.send(sender, "&cИгрок &f" + args[0] + " &cне найден.");
            return;
        }
        if (args.length >= 2 && "clear".equalsIgnoreCase(args[1])) {
            int count = Stash.count(target);
            Stash.clear(target);
            ChatUtil.send(sender, "&aБуфер игрока &f" + args[0] + " &aочищен (" + count + " шт).");
            return;
        }
        if (sender instanceof EntityPlayerMP) {
            new StashScreen((EntityPlayerMP) sender, target, args[0]).open();
        } else {
            ChatUtil.send(sender, "&7В буфере игрока &f" + args[0] + "&7: &f" + Stash.count(target) + " шт");
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (!Perms.isAdmin(sender)) {
            return new ArrayList<String>();
        }
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, "clear");
        }
        return new ArrayList<String>();
    }
}
