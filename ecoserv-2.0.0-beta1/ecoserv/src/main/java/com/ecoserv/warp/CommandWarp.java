package com.ecoserv.warp;

import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** /warp [имя] — меню или переход; /warp set|setserver|del|list. */
public class CommandWarp extends CommandBase {

    @Override
    public String getName() {
        return "warp";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("warps", "варп");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/warp [имя] | /warp set <имя> | /warp del <имя> | /warp list";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return sender instanceof EntityPlayerMP;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP p = getCommandSenderAsPlayer(sender);
        if (args.length == 0) {
            WarpScreen.open(p);
            return;
        }
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "set":
            case "setserver": {
                if (args.length < 2) {
                    ChatUtil.send(p, "&c/warp " + sub + " <имя>");
                    return;
                }
                String err = WarpManager.create(p, args[1], "setserver".equals(sub));
                ChatUtil.send(p, err == null ? "&aВарп «" + args[1] + "» поставлен." : err);
                return;
            }
            case "del":
            case "delete": {
                if (args.length < 2) {
                    ChatUtil.send(p, "&c/warp del <имя>");
                    return;
                }
                Warp w = WarpManager.get(args[1]);
                if (w == null || !WarpManager.canEdit(p, w)) {
                    ChatUtil.send(p, "&cНет такого твоего варпа.");
                    return;
                }
                WarpManager.remove(w.name);
                ChatUtil.send(p, "&aВарп удалён.");
                return;
            }
            case "list": {
                StringBuilder sb = new StringBuilder();
                for (Warp w : WarpManager.visibleTo(p)) {
                    sb.append(w.name).append(", ");
                }
                ChatUtil.send(p, sb.length() == 0 ? "&7Варпов нет." : "&eВарпы: &f" + sb.substring(0, sb.length() - 2));
                return;
            }
            default: {
                Warp w = WarpManager.get(args[0]);
                if (w == null || !WarpManager.visible(p, w)) {
                    ChatUtil.send(p, "&cВарпа «" + args[0] + "» нет. &7/warp — меню");
                    return;
                }
                WarpManager.go(p, w);
            }
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        if (args.length == 1 && sender instanceof EntityPlayerMP) {
            List<String> names = new ArrayList<String>(Arrays.asList("set", "del", "list"));
            if (Perms.isAdmin(sender)) {
                names.add("setserver");
            }
            for (Warp w : WarpManager.visibleTo((EntityPlayerMP) sender)) {
                names.add(w.name);
            }
            return getListOfStringsMatchingLastWord(args, names);
        }
        return Collections.emptyList();
    }
}
