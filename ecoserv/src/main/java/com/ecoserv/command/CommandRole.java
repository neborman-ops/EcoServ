package com.ecoserv.command;

import com.ecoserv.role.Role;
import com.ecoserv.role.RoleManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /role — роли (теги) игроков. Все подкоманды работают и из консоли/RCON.
 * Без аргументов игрок видит свои роли.
 */
public class CommandRole extends CommandBase {

    private static final List<String> FIELDS = Arrays.asList("name", "color", "prefix", "priority", "xaero",
            "multiplier", "description");

    @Override
    public String getName() {
        return "role";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("roles");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/role [menu|list|info|create|delete|give|take|player|set|cmd|perm|world|reload]";
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
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        boolean admin = Perms.isAdmin(sender);
        if (args.length == 0 || "me".equalsIgnoreCase(args[0])) {
            if (sender instanceof EntityPlayerMP) {
                showPlayer(sender, ((EntityPlayerMP) sender).getUniqueID(), sender.getName());
                if (admin) {
                    ITextComponent line = ChatUtil.component("&7Управление: ");
                    line.appendSibling(ChatUtil.link("&b[Окно «Роли и теги»]", "/role menu", "&eОткрыть окно ролей"));
                    line.appendSibling(ChatUtil.component(" &7или &f" + getUsage(sender)));
                    ChatUtil.send(sender, line);
                }
            } else {
                ChatUtil.send(sender, getUsage(sender));
            }
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("list".equals(sub)) {
            list(sender, admin);
            return;
        }
        if (!admin) {
            ChatUtil.send(sender, "&cУправлять ролями может только админ. Свои роли: &f/role");
            return;
        }
        switch (sub) {
            case "menu":
            case "gui":
                if (sender instanceof EntityPlayerMP) {
                    com.ecoserv.role.RolesScreen.open((EntityPlayerMP) sender);
                } else {
                    ChatUtil.send(sender, "&cОкно ролей открывается только в игре. Из консоли: &f" + getUsage(sender));
                }
                return;
            case "info":
                info(sender, args);
                return;
            case "create":
                create(sender, args);
                return;
            case "delete":
            case "remove":
                if (args.length < 2) {
                    ChatUtil.send(sender, "&cИспользование: &f/role delete <роль>");
                    return;
                }
                ChatUtil.send(sender, RoleManager.delete(args[1])
                        ? "&aРоль &f" + args[1] + " &aудалена (и снята со всех игроков)."
                        : "&cРоли &f" + args[1] + " &cнет.");
                return;
            case "give":
            case "add":
                give(sender, args);
                return;
            case "take":
                take(sender, args);
                return;
            case "player":
            case "who":
                if (args.length < 2) {
                    ChatUtil.send(sender, "&cИспользование: &f/role player <ник>");
                    return;
                }
                UUID id = PlayerUtil.uuidOf(args[1]);
                if (id == null) {
                    ChatUtil.send(sender, "&cИгрок &f" + args[1] + " &cне найден.");
                    return;
                }
                showPlayer(sender, id, args[1]);
                return;
            case "set":
                set(sender, args);
                return;
            case "cmd":
            case "command":
                editList(sender, args, "cmd");
                return;
            case "perm":
            case "permission":
                editList(sender, args, "perm");
                return;
            case "world":
                editList(sender, args, "world");
                return;
            case "reload":
                RoleManager.load();
                RoleManager.syncAll();
                ChatUtil.send(sender, "&aРоли перечитаны: &f" + RoleManager.all().size());
                return;
            default:
                ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
        }
    }

    private void list(ICommandSender sender, boolean admin) {
        List<Role> roles = RoleManager.all();
        ChatUtil.send(sender, "&6=== Роли (" + roles.size() + ") ===");
        if (roles.isEmpty()) {
            ChatUtil.sendRaw(sender, " &7пусто. Создать: &f/role create <id> <название>");
        }
        for (Role role : roles) {
            ITextComponent line = ChatUtil.component(" " + role.title() + " &8(" + role.id + ")"
                    + (role.prefix.isEmpty() ? "" : " &7тег: " + role.prefix)
                    + " &7игроков: &f" + RoleManager.holders(role.id).size());
            if (admin) {
                line.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/role info " + role.id));
                line.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        ChatUtil.component("&eКлик — подробности")));
            }
            ChatUtil.send(sender, line);
        }
    }

    private void info(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            ChatUtil.send(sender, "&cИспользование: &f/role info <роль>");
            return;
        }
        Role role = RoleManager.get(args[1]);
        if (role == null) {
            ChatUtil.send(sender, "&cРоли &f" + args[1] + " &cнет. Список: &f/role list");
            return;
        }
        ChatUtil.send(sender, "&6=== Роль " + role.title() + " &8(" + role.id + ") &6===");
        if (!role.description.isEmpty()) {
            ChatUtil.sendRaw(sender, " &7" + role.description);
        }
        ChatUtil.sendRaw(sender, " &7Тег в чате: " + (role.prefix.isEmpty() ? "&8нет" : role.prefix)
                + " &7| приоритет: &f" + role.priority);
        ChatUtil.sendRaw(sender, " &7Телепорт по карте Xaero: " + (role.xaeroTp ? "&aда" : "&8нет")
                + " &7| множитель наград: &fx" + Fmt.price(role.rewardMultiplier));
        ChatUtil.sendRaw(sender, " &7Команды: &f" + (role.commands.isEmpty() ? "—" : joinNames(role.commands)));
        ChatUtil.sendRaw(sender, " &7Права: &f" + (role.permissions.isEmpty() ? "—" : joinNames(role.permissions)));
        ChatUtil.sendRaw(sender, " &7Миры для /world: &f" + (role.worlds.isEmpty() ? "—" : joinNames(role.worlds)));
        List<UUID> holders = RoleManager.holders(role.id);
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < holders.size() && i < 30; i++) {
            if (i > 0) {
                names.append(", ");
            }
            names.append(PlayerUtil.nameOf(holders.get(i)));
        }
        if (holders.size() > 30) {
            names.append(" …");
        }
        ChatUtil.sendRaw(sender, " &7Игроки (" + holders.size() + "): &f" + (holders.isEmpty() ? "—" : names));
        ChatUtil.sendRaw(sender, " &8/role set " + role.id + " <name|color|prefix|priority|xaero|multiplier|description> <значение>");
        ChatUtil.sendRaw(sender, " &8/role cmd|perm|world " + role.id + " add|remove <значение>");
    }

    private void create(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            ChatUtil.send(sender, "&cИспользование: &f/role create <id> [название]");
            ChatUtil.sendRaw(sender, " &7id — латиница, цифры и _, например &fvip&7, &fmoder&7, &fbuilder");
            return;
        }
        String name = args.length > 2 ? buildString(args, 2) : args[1];
        Role role = RoleManager.create(args[1], name);
        if (role == null) {
            ChatUtil.send(sender, "&cНе получилось: id занят или содержит не только латиницу/цифры/_ (до 32 символов).");
            return;
        }
        ChatUtil.send(sender, "&aРоль создана: " + role.title() + " &8(" + role.id + ")");
        ChatUtil.sendRaw(sender, " &7Выдать: &f/role give <ник> " + role.id + " [дней]");
        ChatUtil.sendRaw(sender, " &7Тег: &f/role set " + role.id + " prefix &6[" + role.name + "]");
        ChatUtil.sendRaw(sender, " &7Команды: &f/role cmd " + role.id + " add tp");
    }

    private void give(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            ChatUtil.send(sender, "&cИспользование: &f/role give <ник> <роль> [срок: 30 | 30d | 12h | 90m]");
            return;
        }
        UUID id = PlayerUtil.uuidOf(args[1]);
        if (id == null) {
            ChatUtil.send(sender, "&cИгрок &f" + args[1] + " &cне найден (должен хотя бы раз зайти на сервер).");
            return;
        }
        Role role = RoleManager.get(args[2]);
        if (role == null) {
            ChatUtil.send(sender, "&cРоли &f" + args[2] + " &cнет. Список: &f/role list");
            return;
        }
        long until = 0L;
        if (args.length > 3) {
            long duration = parseDuration(args[3]);
            if (duration <= 0L) {
                ChatUtil.send(sender, "&cНе понял срок &f" + args[3] + "&c. Примеры: 30 (дней), 30d, 12h, 90m");
                return;
            }
            until = System.currentTimeMillis() + duration;
        }
        RoleManager.give(id, role.id, until, sender.getName());
        ChatUtil.send(sender, "&aИгроку &f" + args[1] + " &aвыдана роль " + role.title()
                + (until > 0L ? " &7до &f" + Fmt.timestamp(until) : " &7навсегда"));
        EntityPlayerMP online = PlayerUtil.online(id);
        if (online != null && online != sender) {
            ChatUtil.send(online, "&aТебе выдана роль " + role.title()
                    + (until > 0L ? " &7до &f" + Fmt.timestamp(until) : ""));
        }
    }

    private void take(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            ChatUtil.send(sender, "&cИспользование: &f/role take <ник> <роль>");
            return;
        }
        UUID id = PlayerUtil.uuidOf(args[1]);
        if (id == null) {
            ChatUtil.send(sender, "&cИгрок &f" + args[1] + " &cне найден.");
            return;
        }
        ChatUtil.send(sender, RoleManager.take(id, args[2])
                ? "&aРоль &f" + args[2] + " &aснята с &f" + args[1]
                : "&cУ игрока &f" + args[1] + " &cнет роли &f" + args[2]);
    }

    private void showPlayer(ICommandSender sender, UUID id, String name) {
        List<RoleManager.Assignment> list = RoleManager.assignments(id);
        ChatUtil.send(sender, "&6Роли &f" + name + "&6: " + (list.isEmpty() ? "&7нет" : ""));
        for (RoleManager.Assignment a : list) {
            Role role = RoleManager.get(a.role);
            if (role == null) {
                continue;
            }
            ChatUtil.sendRaw(sender, " " + role.title() + (a.until > 0L
                    ? " &7до &f" + Fmt.timestamp(a.until) + " &7(осталось " + Fmt.duration(a.until - System.currentTimeMillis()) + ")"
                    : " &7навсегда"));
        }
    }

    private void set(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            ChatUtil.send(sender, "&cИспользование: &f/role set <роль> <name|color|prefix|priority|xaero|multiplier|description> <значение>");
            return;
        }
        Role role = RoleManager.get(args[1]);
        if (role == null) {
            ChatUtil.send(sender, "&cРоли &f" + args[1] + " &cнет.");
            return;
        }
        String field = args[2].toLowerCase(Locale.ROOT);
        String value = buildString(args, 3);
        try {
            switch (field) {
                case "name":
                    role.name = value;
                    break;
                case "color":
                    role.color = value.startsWith("&") ? value : "&" + value;
                    break;
                case "prefix":
                    role.prefix = "-".equals(value) || "none".equalsIgnoreCase(value) ? "" : value;
                    break;
                case "priority":
                    role.priority = Integer.parseInt(value.trim());
                    break;
                case "xaero":
                    role.xaeroTp = parseBool(value);
                    break;
                case "multiplier":
                    role.rewardMultiplier = Math.max(0.0D, Double.parseDouble(value.trim().replace(',', '.')));
                    break;
                case "description":
                    role.description = value;
                    break;
                default:
                    ChatUtil.send(sender, "&cПоля: &f" + joinNames(FIELDS));
                    return;
            }
        } catch (NumberFormatException e) {
            ChatUtil.send(sender, "&cНужно число: &f" + value);
            return;
        }
        role.normalize();
        RoleManager.changed();
        ChatUtil.send(sender, "&aРоль " + role.title() + "&a: " + field + " = &f" + value);
    }

    private void editList(ICommandSender sender, String[] args, String kind) {
        if (args.length < 4) {
            ChatUtil.send(sender, "&cИспользование: &f/role " + kind + " <роль> add|remove <значение>");
            return;
        }
        Role role = RoleManager.get(args[1]);
        if (role == null) {
            ChatUtil.send(sender, "&cРоли &f" + args[1] + " &cнет.");
            return;
        }
        List<String> list = "cmd".equals(kind) ? role.commands : "perm".equals(kind) ? role.permissions : role.worlds;
        String value = args[3].trim();
        if ("cmd".equals(kind)) {
            while (value.startsWith("/")) {
                value = value.substring(1);
            }
            value = value.toLowerCase(Locale.ROOT);
        }
        boolean add = "add".equalsIgnoreCase(args[2]);
        if (add) {
            if (!list.contains(value)) {
                list.add(value);
            }
        } else if (!list.remove(value)) {
            ChatUtil.send(sender, "&cВ списке нет &f" + value);
            return;
        }
        role.normalize();
        RoleManager.changed();
        ChatUtil.send(sender, "&aРоль " + role.title() + "&a: " + (add ? "добавлено" : "убрано") + " &f" + value);
        if ("cmd".equals(kind) && add) {
            ChatUtil.sendRaw(sender, " &7Игроки с этой ролью смогут выполнять &f/" + value + " &7без OP.");
        }
    }

    private static long parseDuration(String text) {
        String s = text.trim().toLowerCase(Locale.ROOT);
        long unit = 86_400_000L;
        if (s.endsWith("d") || s.endsWith("д")) {
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("h") || s.endsWith("ч")) {
            unit = 3_600_000L;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m") || s.endsWith("м")) {
            unit = 60_000L;
            s = s.substring(0, s.length() - 1);
        }
        try {
            double v = Double.parseDouble(s.replace(',', '.'));
            return v <= 0 ? -1L : (long) (v * unit);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static boolean parseBool(String v) {
        String s = v.trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("on") || s.equals("yes") || s.equals("да") || s.equals("1") || s.equals("вкл");
    }

    private static String joinNames(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (!Perms.isAdmin(sender)) {
            return args.length == 1 ? getListOfStringsMatchingLastWord(args, "me", "list") : new ArrayList<String>();
        }
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "menu", "list", "info", "create", "delete", "give", "take",
                    "player", "set", "cmd", "perm", "world", "reload");
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if ("give".equals(sub) || "take".equals(sub) || "player".equals(sub)) {
                return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
            }
            if ("info".equals(sub) || "delete".equals(sub) || "set".equals(sub) || "cmd".equals(sub)
                    || "perm".equals(sub) || "world".equals(sub)) {
                return getListOfStringsMatchingLastWord(args, RoleManager.ids());
            }
        }
        if (args.length == 3) {
            if ("give".equals(sub) || "take".equals(sub)) {
                return getListOfStringsMatchingLastWord(args, RoleManager.ids());
            }
            if ("set".equals(sub)) {
                return getListOfStringsMatchingLastWord(args, FIELDS);
            }
            if ("cmd".equals(sub) || "perm".equals(sub) || "world".equals(sub)) {
                return getListOfStringsMatchingLastWord(args, "add", "remove");
            }
        }
        if (args.length == 4 && "cmd".equals(sub) && "add".equalsIgnoreCase(args[2])) {
            return getListOfStringsMatchingLastWord(args, server.getCommandManager().getCommands().keySet());
        }
        if (args.length == 4 && "give".equals(sub)) {
            return getListOfStringsMatchingLastWord(args, "7d", "30d", "12h");
        }
        return new ArrayList<String>();
    }
}
