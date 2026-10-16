package com.ecoserv.command;

import com.ecoserv.activator.Activator;
import com.ecoserv.activator.ActivatorManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.world.WorldNames;
import com.ecoserv.world.WorldTeleport;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** /activator — именованные кнопки и рычаги для условия «Активация» (только админы). */
public class CommandActivator extends CommandBase {

    @Override
    public String getName() {
        return "activator";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/activator list | remove <название> | command <название> <команда|-> | tp <название> | test <название> | fire <игрок> <название>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return Perms.isAdmin(sender) || super.checkPermission(server, sender);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase();
        EntityPlayerMP player = sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;

        if ("list".equals(sub)) {
            List<Activator> list = ActivatorManager.all();
            ChatUtil.send(sender, "&6=== Кнопки и рычаги с названиями (" + list.size() + ") ===");
            if (list.isEmpty()) {
                ChatUtil.sendRaw(sender, " &7нет. Возьми палку активации и кликни ПКМ по кнопке или рычагу.");
            }
            for (int i = 0; i < list.size(); i++) {
                Activator a = list.get(i);
                ITextComponent line = ChatUtil.component(" &f«" + a.name + "» &7" + WorldNames.nameOf(a.dim)
                        + " " + a.x + " " + a.y + " " + a.z + " &8| нажатий: " + a.uses
                        + (a.command.isEmpty() ? "" : " &8| команда: &7" + a.command));
                line.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/activator tp " + a.name));
                line.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        ChatUtil.component("&eКлик — телепорт\n&7поставил: " + a.creator)));
                ChatUtil.send(sender, line);
            }
            if (!ActivatorManager.hasQuestListener()) {
                ChatUtil.sendRaw(sender, " &eУсловие «Активация» для FTB Quests в этой сборке не подключено — см. README.");
            }
            return;
        }

        if ("fire".equals(sub)) {
            // засчитать «Активацию» игроку без кнопки (для консоли, RCON, ботов и скрытых квестов)
            if (args.length < 3) {
                ChatUtil.send(sender, "&cИспользование: &f/activator fire <игрок> <название>");
                return;
            }
            EntityPlayerMP target = com.ecoserv.util.PlayerUtil.online(args[1]);
            if (target == null) {
                ChatUtil.send(sender, "&cИгрок &f" + args[1] + " &cне в сети.");
                return;
            }
            String fired = buildString(args, 2);
            ActivatorManager.notifyListeners(target, fired);
            ChatUtil.send(sender, "&a«Активация» &f" + fired + " &aзасчитана игроку &f" + target.getName());
            return;
        }

        if (args.length < 2) {
            ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
            return;
        }
        String name = args[1];

        if ("remove".equals(sub)) {
            int removed = ActivatorManager.removeByName(name);
            ChatUtil.send(sender, removed > 0 ? "&aУдалено: &f" + removed : "&cНазвание &f" + name + " &cне найдено.");
            return;
        }

        List<Activator> found = ActivatorManager.byName(name);
        if (found.isEmpty()) {
            ChatUtil.send(sender, "&cНазвание &f" + name + " &cне найдено. Список: &f/activator list");
            return;
        }

        if ("command".equals(sub)) {
            String command = args.length >= 3 ? buildString(args, 2) : "";
            if ("-".equals(command) || "clear".equalsIgnoreCase(command)) {
                command = "";
            }
            for (int i = 0; i < found.size(); i++) {
                found.get(i).command = command;
            }
            ActivatorManager.changed();
            ChatUtil.send(sender, command.isEmpty()
                    ? "&aКоманда у «" + name + "» убрана."
                    : "&aПри нажатии «" + name + "» выполнится: &f/" + (command.startsWith("/") ? command.substring(1) : command)
                    + " &7(%player% — ник игрока)");
            return;
        }

        if (player == null) {
            ChatUtil.send(sender, "&cЭта команда работает только в игре.");
            return;
        }

        if ("tp".equals(sub)) {
            Activator a = found.get(0);
            WorldTeleport.Target target = WorldTeleport.near(player, a.dim, a.pos());
            if (target == null) {
                ChatUtil.send(sender, "&cМир не загрузился.");
                return;
            }
            if (player.dimension != a.dim) {
                WorldTeleport.rememberPosition(player);
            }
            if (!WorldTeleport.teleport(player, target)) {
                ChatUtil.send(sender, "&cПереход в этот мир запрещён другим модом.");
            }
            return;
        }

        if ("test".equals(sub)) {
            ActivatorManager.trigger(player, found.get(0));
            ChatUtil.send(sender, "&aАктивация «" + name + "» выполнена от твоего имени (как будто нажал кнопку).");
            return;
        }

        ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "list", "remove", "command", "tp", "test", "fire");
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, new ArrayList<String>(ActivatorManager.names()));
        }
        return new ArrayList<String>();
    }
}
