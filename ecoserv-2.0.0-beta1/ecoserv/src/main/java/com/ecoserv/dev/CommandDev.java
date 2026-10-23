package com.ecoserv.dev;

import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /dev — окно разработчика; /dev on|off — режим; /dev event &lt;имя&gt; [ник] — запустить событие. */
public class CommandDev extends CommandBase {

    @Override
    public String getName() {
        return "dev";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("разработчик", "ecodev");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/dev | /dev on|off | /dev event <имя> [ник]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return Perms.isAdmin(sender);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("event") && args.length > 1) {
            EntityPlayerMP target = args.length > 2 ? getPlayer(server, sender, args[2])
                    : sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
            String[] ev = args[1].split(":", 2);
            DevEvents.fire(ev[0], target, ev.length > 1 ? ev[1] : null);
            ChatUtil.send(sender, "&aСобытие отправлено.");
            return;
        }
        EntityPlayerMP p = getCommandSenderAsPlayer(sender);
        if (sub.equals("on") || sub.equals("off")) {
            DevManager.setDev(p, sub.equals("on"));
            ChatUtil.send(p, sub.equals("on") ? "&aРежим разработчика включён." : "&7Режим разработчика выключен.");
            return;
        }
        DevScreen.open(p);
    }
}
