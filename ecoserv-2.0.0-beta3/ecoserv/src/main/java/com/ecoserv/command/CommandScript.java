package com.ecoserv.command;

import com.ecoserv.script.Script;
import com.ecoserv.script.ScriptManager;
import com.ecoserv.script.ScriptRunner;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /script — сценарии (п. 14): цепочки шагов с паузами. Запускаются зонами-триггерами,
 * кнопками (/activator command ... script run имя %player%), блоками и командой.
 */
public class CommandScript extends CommandBase {

    @Override
    public String getName() {
        return "script";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("scripts", "сценарий");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/script <list|show|create|delete|add|insert|set|remove|desc|run|stop|reload>";
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
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list": {
                List<Script> all = ScriptManager.all();
                ChatUtil.send(sender, "&6=== Сценарии (" + all.size() + ", сейчас идёт: " + ScriptRunner.running() + ") ===");
                for (Script s : all) {
                    ChatUtil.send(sender, ChatUtil.link(" &f" + s.name + " &7(" + s.steps.size() + " шагов) " + s.description,
                            "/script show " + s.name, "&eПоказать шаги"));
                }
                ChatUtil.sendRaw(sender, " &8Создать: /script create <имя>, шаги: /script add <имя> <шаг>");
                return;
            }
            case "reload":
                ScriptManager.load();
                ChatUtil.send(sender, "&aСценарии перечитаны: &f" + ScriptManager.all().size());
                return;
            case "stop": {
                EntityPlayerMP target = args.length > 1 ? PlayerUtil.online(args[1])
                        : sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
                if (target == null) {
                    ChatUtil.send(sender, "&cИгрок не в сети.");
                    return;
                }
                ChatUtil.send(sender, "&aОстановлено сценариев: &f" + ScriptRunner.stop(target.getUniqueID()));
                return;
            }
            default:
                break;
        }
        if (args.length < 2) {
            ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
            help(sender);
            return;
        }
        String name = args[1];
        if ("create".equals(sub)) {
            Script s = ScriptManager.create(name);
            ChatUtil.send(sender, s == null ? "&cИмя занято или некорректно (буквы, цифры, _ без пробелов)."
                    : "&aСоздан сценарий &f" + s.name + "&a. Добавляй шаги: &f/script add " + s.name + " <шаг>");
            if (s != null) {
                help(sender);
            }
            return;
        }
        Script s = ScriptManager.get(name);
        if (s == null) {
            ChatUtil.send(sender, "&cНет сценария &f" + name + " &7(/script list)");
            return;
        }
        switch (sub) {
            case "show": {
                ChatUtil.send(sender, "&6=== Сценарий " + s.name + " ===" + (s.description.isEmpty() ? "" : " &7" + s.description));
                for (int i = 0; i < s.steps.size(); i++) {
                    String step = s.steps.get(i);
                    ITextComponent line = ChatUtil.component(" &8" + (i + 1) + ". &f" + step + " ");
                    ITextComponent edit = ChatUtil.component("&e[✎]");
                    edit.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            "/script set " + s.name + " " + (i + 1) + " " + step));
                    edit.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, ChatUtil.component("&eИзменить шаг")));
                    ITextComponent del = ChatUtil.component(" &c[✖]");
                    del.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/script remove " + s.name + " " + (i + 1)));
                    del.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, ChatUtil.component("&cУдалить шаг")));
                    line.appendSibling(edit);
                    line.appendSibling(del);
                    ChatUtil.send(sender, line);
                }
                ITextComponent add = ChatUtil.component(" &a[+ шаг]");
                add.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/script add " + s.name + " "));
                ChatUtil.send(sender, add);
                return;
            }
            case "delete":
                ScriptManager.delete(s.name);
                ChatUtil.send(sender, "&aСценарий &f" + s.name + " &aудалён.");
                return;
            case "desc":
                s.description = args.length > 2 ? buildString(args, 2) : "";
                ScriptManager.save();
                ChatUtil.send(sender, "&aОписание обновлено.");
                return;
            case "parallel":
                s.allowParallel = !s.allowParallel;
                ScriptManager.save();
                ChatUtil.send(sender, "&aПараллельный запуск: &f" + (s.allowParallel ? "да" : "нет"));
                return;
            case "add": {
                if (args.length < 3) {
                    help(sender);
                    return;
                }
                String step = buildString(args, 2);
                String error = ScriptManager.validate(step);
                if (error != null) {
                    ChatUtil.send(sender, "&c" + error);
                    return;
                }
                s.steps.add(step);
                ScriptManager.save();
                ChatUtil.send(sender, "&aШаг " + s.steps.size() + ": &f" + step);
                return;
            }
            case "insert":
            case "set": {
                if (args.length < 4) {
                    ChatUtil.send(sender, "&cИспользование: &f/script " + sub + " <имя> <номер> <шаг>");
                    return;
                }
                int index = parseIndex(args[2]);
                String step = buildString(args, 3);
                String error = ScriptManager.validate(step);
                if (error != null) {
                    ChatUtil.send(sender, "&c" + error);
                    return;
                }
                if ("set".equals(sub)) {
                    if (index < 0 || index >= s.steps.size()) {
                        ChatUtil.send(sender, "&cНет шага №" + args[2]);
                        return;
                    }
                    s.steps.set(index, step);
                } else {
                    s.steps.add(Math.max(0, Math.min(index, s.steps.size())), step);
                }
                ScriptManager.save();
                ChatUtil.send(sender, "&aГотово.");
                return;
            }
            case "remove": {
                int index = args.length > 2 ? parseIndex(args[2]) : -1;
                if (index < 0 || index >= s.steps.size()) {
                    ChatUtil.send(sender, "&cНет шага №" + (args.length > 2 ? args[2] : "?"));
                    return;
                }
                String removed = s.steps.remove(index);
                ScriptManager.save();
                ChatUtil.send(sender, "&aУдалён шаг: &f" + removed);
                return;
            }
            case "run": {
                EntityPlayerMP target = args.length > 2 ? PlayerUtil.online(args[2])
                        : sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
                if (target == null) {
                    ChatUtil.send(sender, "&cИгрок не в сети.");
                    return;
                }
                boolean started = ScriptRunner.start(target, s.name, "command");
                if (sender != target || !started) {
                    ChatUtil.send(sender, started ? "&aСценарий &f" + s.name + " &aзапущен для &f" + target.getName()
                            : "&eСценарий уже идёт у этого игрока.");
                }
                return;
            }
            default:
                ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
        }
    }

    private static int parseIndex(String s) {
        try {
            return Integer.parseInt(s.trim()) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void help(ICommandSender sender) {
        ChatUtil.sendRaw(sender, "&7Шаги: &fmsg &7текст | &ftitle &7заголовок|подзаголовок | &factionbar &7текст");
        ChatUtil.sendRaw(sender, "&f sound &7id [громкость высота] | &fwait &740 или 2s | &fcmd &7команда от сервера");
        ChatUtil.sendRaw(sender, "&f pcmd &7команда от игрока | &ftp &7x y z | &ftpworld &7мир [x y z]");
        ChatUtil.sendRaw(sender, "&f activate &7имя | &frequire role &7vip | &frequire age &7>=16 | &frun &7сценарий | &fstop");
        ChatUtil.sendRaw(sender, "&7Подстановки: &f%player% %x% %y% %z% %name%");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "list", "show", "create", "delete", "add", "insert", "set",
                    "remove", "desc", "run", "stop", "reload", "parallel");
        }
        if (args.length == 2 && !"stop".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, ScriptManager.names());
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 3 && "run".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 3 && "add".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "msg", "title", "actionbar", "sound", "wait", "cmd", "pcmd",
                    "tp", "tpworld", "activate", "require", "run", "stop");
        }
        return new ArrayList<String>();
    }
}
