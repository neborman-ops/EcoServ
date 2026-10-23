package com.ecoserv.command;

import com.ecoserv.dates.AgeFilter;
import com.ecoserv.region.Region;
import com.ecoserv.region.RegionManager;
import com.ecoserv.region.RegionScreen;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldNames;
import com.ecoserv.world.WorldTeleport;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /trigger — зоны-триггеры (п. 14), /portal — порталы (п. 4). Работает и из консоли/RCON. */
public class CommandRegion extends CommandBase {

    private static final List<String> FIELDS = Arrays.asList("message", "title", "sound", "script", "mode", "cooldown",
            "roles", "age", "deny", "enabled", "activation", "target", "targetmode");

    private final String kind;

    public CommandRegion(String kind) {
        this.kind = kind;
    }

    private boolean portal() {
        return Region.PORTAL.equals(kind);
    }

    @Override
    public String getName() {
        return portal() ? "portal" : "trigger";
    }

    @Override
    public List<String> getAliases() {
        return portal() ? Arrays.asList("portals") : Arrays.asList("triggers");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/" + getName() + " <list|menu|info|tp|test|set|cmd|reset|remove> ...";
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
        EntityPlayerMP player = sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : (player != null ? "menu" : "list");
        if ("list".equals(sub)) {
            List<Region> list = RegionManager.all(kind);
            ChatUtil.send(sender, "&6=== " + (portal() ? "Порталы" : "Зоны-триггеры") + " (" + list.size() + ") ===");
            for (Region r : list) {
                String text = " &f" + r.name + " &7" + WorldNames.displayName(r.dim) + " " + r.describeArea()
                        + (r.isPortal() ? " → &f" + (r.target.isEmpty() ? "&cне настроен" : r.target) : "")
                        + (r.enabled ? "" : " &c(выкл)");
                ChatUtil.send(sender, player == null ? ChatUtil.component(text)
                        : ChatUtil.link(text, "/" + getName() + " menu " + r.name, "&eНастроить"));
            }
            if (list.isEmpty()) {
                ChatUtil.sendRaw(sender, " &7пусто. Возьми " + (portal() ? "палочку порталов" : "палочку триггера")
                        + " из вкладки мода в креативе.");
            }
            return;
        }
        if ("menu".equals(sub)) {
            if (player == null) {
                ChatUtil.send(sender, "&cМеню открывается только в игре.");
                return;
            }
            if (args.length > 1) {
                Region r = RegionManager.get(args[1]);
                if (r == null) {
                    ChatUtil.send(sender, "&cНет области &f" + args[1]);
                    return;
                }
                new RegionScreen(player, r.name).open();
            } else {
                RegionScreen.openList(player, kind);
            }
            return;
        }
        if (args.length < 2) {
            ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
            return;
        }
        Region r = RegionManager.get(args[1]);
        if (r == null) {
            ChatUtil.send(sender, "&cНет области &f" + args[1] + " &7(/" + getName() + " list)");
            return;
        }
        switch (sub) {
            case "info":
                ChatUtil.send(sender, "&6=== " + (r.isPortal() ? "Портал " : "Зона ") + r.name + " ===");
                ChatUtil.sendRaw(sender, " &7Мир: &f" + WorldNames.displayName(r.dim) + " (" + r.dim + ") &7" + r.describeArea());
                ChatUtil.sendRaw(sender, " &7Срабатывает: &f" + r.modeTitle() + (r.enabled ? "" : " &c(выключено)"));
                if (!r.message.isEmpty()) {
                    ChatUtil.sendRaw(sender, " &7Сообщение: &f" + r.message);
                }
                if (!r.title.isEmpty()) {
                    ChatUtil.sendRaw(sender, " &7Заголовок: &f" + r.title);
                }
                if (!r.sound.isEmpty()) {
                    ChatUtil.sendRaw(sender, " &7Звук: &f" + r.sound);
                }
                for (String c : r.commands) {
                    ChatUtil.sendRaw(sender, " &7Команда: &f/" + c);
                }
                if (!r.script.isEmpty()) {
                    ChatUtil.sendRaw(sender, " &7Сценарий: &f" + r.script);
                }
                ChatUtil.sendRaw(sender, " &7«Активация» для квестов: &f" + (r.activation ? "да" : "нет")
                        + " &7| роли: &f" + (r.roles.isEmpty() ? "—" : String.join(", ", r.roles))
                        + " &7| возраст: &f" + (r.age.isEmpty() ? "—" : r.age));
                if (r.isPortal()) {
                    ChatUtil.sendRaw(sender, " &7Ведёт в: &f" + (r.target.isEmpty() ? "не выбрано" : r.target)
                            + " &7(" + r.targetMode + ")");
                }
                return;
            case "remove":
            case "delete":
                RegionManager.remove(r.name);
                ChatUtil.send(sender, "&aУдалено: &f" + r.name);
                return;
            case "tp":
                if (player != null) {
                    WorldTeleport.teleport(player, WorldTeleport.near(player, r.dim, r.center()));
                }
                return;
            case "test": {
                EntityPlayerMP target = args.length > 2 ? PlayerUtil.online(args[2]) : player;
                if (target == null) {
                    ChatUtil.send(sender, "&cИгрок не в сети.");
                    return;
                }
                RegionManager.fire(target, r, true);
                ChatUtil.send(sender, "&aСработало для &f" + target.getName());
                return;
            }
            case "reset": {
                UUID id = args.length > 2 ? PlayerUtil.uuidOf(args[2]) : null;
                ChatUtil.send(sender, "&aСброшено у игроков: &f" + RegionManager.resetState(r.name, id));
                return;
            }
            case "cmd": {
                if (args.length < 3) {
                    ChatUtil.send(sender, "&cИспользование: &f/" + getName() + " cmd <имя> add <команда> | clear");
                    return;
                }
                if ("clear".equalsIgnoreCase(args[2])) {
                    r.commands.clear();
                } else if ("add".equalsIgnoreCase(args[2]) && args.length > 3) {
                    String c = buildString(args, 3);
                    while (c.startsWith("/")) {
                        c = c.substring(1);
                    }
                    r.commands.add(c);
                } else {
                    ChatUtil.send(sender, "&cadd <команда> или clear");
                    return;
                }
                RegionManager.changed();
                ChatUtil.send(sender, "&aКоманд: &f" + r.commands.size());
                return;
            }
            case "set": {
                if (args.length < 3) {
                    ChatUtil.send(sender, "&cПоля: &f" + String.join(", ", FIELDS));
                    return;
                }
                String value = args.length > 3 ? buildString(args, 3) : "";
                String error = set(r, args[2].toLowerCase(Locale.ROOT), value);
                if (error != null) {
                    ChatUtil.send(sender, "&c" + error);
                    return;
                }
                RegionManager.changed();
                ChatUtil.send(sender, "&aГотово: &f" + r.name + "." + args[2] + " = " + value);
                return;
            }
            default:
                ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
        }
    }

    @Nullable
    private static String set(Region r, String field, String value) {
        String v = value.trim();
        boolean clear = v.isEmpty() || "-".equals(v) || "none".equalsIgnoreCase(v);
        switch (field) {
            case "message":
                r.message = clear ? "" : value;
                return null;
            case "title":
                r.title = clear ? "" : value;
                return null;
            case "sound":
                r.sound = clear ? "" : v;
                return null;
            case "script":
                r.script = clear ? "" : com.ecoserv.script.ScriptManager.key(v);
                return null;
            case "deny":
                r.denyMessage = clear ? "" : value;
                return null;
            case "mode":
                if (!v.equals("enter") && !v.equals("once") && !v.equals("cooldown")) {
                    return "mode: enter, once или cooldown";
                }
                r.mode = v;
                return null;
            case "cooldown":
                try {
                    r.cooldownSeconds = Math.max(0, Integer.parseInt(v));
                    return null;
                } catch (NumberFormatException e) {
                    return "нужно число секунд";
                }
            case "roles":
                r.roles.clear();
                if (!clear) {
                    for (String part : v.split(",")) {
                        if (!part.trim().isEmpty()) {
                            r.roles.add(part.trim().toLowerCase(Locale.ROOT));
                        }
                    }
                }
                return null;
            case "age":
                if (!clear && AgeFilter.parse(v) == null) {
                    return "фильтр: 18, >=16, <=12, 14-17";
                }
                r.age = clear ? "" : v;
                return null;
            case "enabled":
                r.enabled = parseBool(v);
                return null;
            case "activation":
                r.activation = parseBool(v);
                return null;
            case "target":
                if (!clear && WorldNames.resolve(v) == null && !WorldNames.isGroup(v)) {
                    return "нет мира/подмира «" + v + "»";
                }
                r.target = clear ? "" : v;
                return null;
            case "targetmode":
                if (!v.equals("spawn") && !v.equals("last") && !v.equals("point")) {
                    return "targetmode: spawn, last или point";
                }
                r.targetMode = v;
                return null;
            default:
                return "поля: " + String.join(", ", FIELDS);
        }
    }

    private static boolean parseBool(String v) {
        String s = v.toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("on") || s.equals("да") || s.equals("1") || s.equals("вкл");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "list", "menu", "info", "tp", "test", "set", "cmd", "reset", "remove");
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<String>();
            for (Region r : RegionManager.all(kind)) {
                names.add(r.name);
            }
            return getListOfStringsMatchingLastWord(args, names);
        }
        if (args.length == 3 && "set".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, FIELDS);
        }
        if (args.length == 3 && "cmd".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "add", "clear");
        }
        if (args.length == 3 && ("test".equalsIgnoreCase(args[0]) || "reset".equalsIgnoreCase(args[0]))) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return new ArrayList<String>();
    }
}
