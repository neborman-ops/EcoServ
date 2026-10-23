package com.ecoserv.custommenu;

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
import java.util.List;

/** /menu &lt;id&gt; [ник] — открыть своё меню; /menu без аргументов (админ) — конструктор. */
public class CommandMenu extends CommandBase {

    @Override
    public String getName() {
        return "menu";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("меню");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/menu <id> [ник]";
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
            EntityPlayerMP p = getCommandSenderAsPlayer(sender);
            if (Perms.isAdmin(p)) {
                new MenuEditor(p, null).open();
            } else {
                throw new CommandException("/menu <id>");
            }
            return;
        }
        EntityPlayerMP target = args.length > 1 && Perms.isAdmin(sender) ? getPlayer(server, sender, args[1])
                : getCommandSenderAsPlayer(sender);
        MenuManager.open(target, args[0]);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        if (args.length == 1) {
            List<String> ids = new ArrayList<String>();
            for (CustomMenu m : MenuManager.all()) {
                ids.add(m.id);
            }
            return getListOfStringsMatchingLastWord(args, ids);
        }
        return super.getTabCompletions(server, sender, args, pos);
    }
}
