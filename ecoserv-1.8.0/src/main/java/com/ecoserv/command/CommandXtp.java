package com.ecoserv.command;

import com.ecoserv.role.RoleManager;
import com.ecoserv.role.XaeroTp;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;
import java.util.List;

/**
 * /xtp x y z — телепорт по карте для игроков с ролью «телепорт по карте Xaero».
 * В настройках карты (Waypoints → Teleport command) можно указать «/xtp {x} {y} {z}».
 */
public class CommandXtp extends CommandBase {

    @Override
    public String getName() {
        return "xtp";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("maptp");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/xtp <x> <y|~> <z>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) {
            return false;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        return Perms.isAdmin(player) || RoleManager.xaeroTp(player.getUniqueID());
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (args.length < 3) {
            ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
            return;
        }
        XaeroTp.teleport(player, args[0], args[1], args[2]);
    }
}
