package com.ecoserv.command;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.dates.AgeFilter;
import com.ecoserv.dates.DatesManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /birthday — день рождения (п. 24). Игрок вводит его один раз («/birthday 15.03.2008»),
 * админ может поменять. Возраст видят только сам игрок и админы.
 */
public class CommandBirthday extends CommandBase {

    private static final String[] MONTHS = {"января", "февраля", "марта", "апреля", "мая", "июня", "июля",
            "августа", "сентября", "октября", "ноября", "декабря"};

    @Override
    public String getName() {
        return "birthday";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("bday", "др");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/birthday [дд.мм.гггг]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    public static String pretty(LocalDate date) {
        return date.getDayOfMonth() + " " + MONTHS[date.getMonthValue() - 1] + " " + date.getYear();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        boolean admin = Perms.isAdmin(sender);
        if (args.length > 0 && admin) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (adminCommand(sender, sub, args)) {
                return;
            }
        }
        if (!EcoConfig.birthdaysEnabled) {
            ChatUtil.send(sender, "&7Ввод дня рождения на сервере выключен"
                    + (admin ? " &8(dates.birthdays_enabled в /ecoadmin config)" : "") + ".");
            return;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            ChatUtil.send(sender, "&cАдмин: /birthday set|clear|info|check|list ...");
            return;
        }
        final EntityPlayerMP player = (EntityPlayerMP) sender;
        LocalDate current = DatesManager.birthday(player.getUniqueID());
        if (args.length == 0) {
            if (current != null) {
                ChatUtil.send(player, "&dТвой день рождения: &f" + pretty(current) + " &7(полных лет: "
                        + DatesManager.age(player.getUniqueID()) + ")");
                if (EcoConfig.birthdayPlayersCanChange) {
                    ChatUtil.sendRaw(player, " &7Изменить: &f/birthday дд.мм.гггг");
                }
            } else {
                ChatUtil.send(player, "&dУкажи свой день рождения: &f/birthday дд.мм.гггг &7(например /birthday 15.03.2008).");
                ChatUtil.sendRaw(player, " &7В этот день тебя ждёт подарок. Дату видишь только ты и админы.");
            }
            return;
        }
        if (current != null && !EcoConfig.birthdayPlayersCanChange && !admin) {
            ChatUtil.send(player, "&cДень рождения уже указан (&f" + pretty(current) + "&c). Изменить может только админ.");
            return;
        }
        final LocalDate date = DatesManager.parseBirthday(args[0]);
        if (date == null) {
            ChatUtil.send(player, "&cНе понял дату &f" + args[0] + "&c. Формат: дд.мм.гггг, например 15.03.2008.");
            return;
        }
        final EntityPlayerMP who = player;
        new com.ecoserv.ui.ConfirmScreen(player, "День рождения", "Твой день рождения — &f" + pretty(date) + "&f?"
                + (EcoConfig.birthdayPlayersCanChange ? "" : " Потом изменить его сможет только админ."),
                "Сохранить", new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.CAKE), () -> {
            DatesManager.setBirthday(who.getUniqueID(), date, who.getName());
            ChatUtil.send(who, "&aСохранено: &f" + pretty(date) + "&a.");
            DatesManager.check(who);
        }).open();
    }

    private boolean adminCommand(ICommandSender sender, String sub, String[] args) {
        switch (sub) {
            case "set": {
                if (args.length < 3) {
                    ChatUtil.send(sender, "&cИспользование: &f/birthday set <ник> <дд.мм.гггг>");
                    return true;
                }
                UUID id = PlayerUtil.uuidOf(args[1]);
                LocalDate date = DatesManager.parseBirthday(args[2]);
                if (id == null || date == null) {
                    ChatUtil.send(sender, id == null ? "&cИгрок не найден." : "&cНе понял дату.");
                    return true;
                }
                DatesManager.setBirthday(id, date, sender.getName());
                ChatUtil.send(sender, "&aДень рождения &f" + args[1] + "&a: " + pretty(date));
                return true;
            }
            case "clear": {
                if (args.length < 2) {
                    ChatUtil.send(sender, "&cИспользование: &f/birthday clear <ник>");
                    return true;
                }
                UUID id = PlayerUtil.uuidOf(args[1]);
                ChatUtil.send(sender, id != null && DatesManager.clearBirthday(id)
                        ? "&aДень рождения &f" + args[1] + " &aудалён — игрок может ввести заново."
                        : "&cУ игрока &f" + args[1] + " &cдень рождения не указан.");
                return true;
            }
            case "info": {
                if (args.length < 2) {
                    ChatUtil.send(sender, "&cИспользование: &f/birthday info <ник>");
                    return true;
                }
                UUID id = PlayerUtil.uuidOf(args[1]);
                LocalDate date = id == null ? null : DatesManager.birthday(id);
                ChatUtil.send(sender, date == null ? "&7У &f" + args[1] + " &7день рождения не указан."
                        : "&d" + args[1] + ": &f" + pretty(date) + " &7(полных лет: "
                        + java.time.Period.between(date, DatesManager.today()).getYears() + ")");
                return true;
            }
            case "check": {
                if (args.length < 3) {
                    ChatUtil.send(sender, "&cИспользование: &f/birthday check <ник> <фильтр: 18 | >=16 | <=12 | 14-17>");
                    return true;
                }
                UUID id = PlayerUtil.uuidOf(args[1]);
                AgeFilter filter = AgeFilter.parse(args[2]);
                if (id == null || filter == null) {
                    ChatUtil.send(sender, id == null ? "&cИгрок не найден." : "&cНе понял фильтр. Примеры: 18, >=16, <=12, 14-17");
                    return true;
                }
                LocalDate date = DatesManager.birthday(id);
                if (date == null) {
                    ChatUtil.send(sender, "&7Проверка возраста: у &f" + args[1] + " &7день рождения не указан — &cне проходит.");
                    return true;
                }
                int age = java.time.Period.between(date, DatesManager.today()).getYears();
                ChatUtil.send(sender, "&7Проверка возраста &f" + args[1] + "&7: " + age + " лет, фильтр «"
                        + filter.describe() + "» — " + (filter.matches(age) ? "&aпроходит" : "&cне проходит"));
                return true;
            }
            case "list": {
                LocalDate today = DatesManager.today();
                ChatUtil.send(sender, "&6Дни рождения в ближайшие 7 дней:");
                int found = 0;
                for (String name : knownNames()) {
                    UUID id = PlayerUtil.uuidOf(name);
                    LocalDate date = id == null ? null : DatesManager.birthday(id);
                    if (date == null) {
                        continue;
                    }
                    LocalDate next = MonthDay.from(date).atYear(today.getYear());
                    if (next.isBefore(today)) {
                        next = MonthDay.from(date).atYear(today.getYear() + 1);
                    }
                    long days = java.time.temporal.ChronoUnit.DAYS.between(today, next);
                    if (days <= 7) {
                        ChatUtil.sendRaw(sender, " &f" + name + " &7— " + next.getDayOfMonth() + " "
                                + MONTHS[next.getMonthValue() - 1] + (days == 0 ? " &d(сегодня)" : " (через " + days + " дн.)"));
                        found++;
                    }
                }
                if (found == 0) {
                    ChatUtil.sendRaw(sender, " &7никого");
                }
                return true;
            }
            default:
                return false;
        }
    }

    private static List<String> knownNames() {
        List<String> out = new ArrayList<String>();
        MinecraftServer server = PlayerUtil.server();
        if (server != null) {
            out.addAll(Arrays.asList(server.getPlayerProfileCache().getUsernames()));
        }
        return out;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (!Perms.isAdmin(sender)) {
            return new ArrayList<String>();
        }
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "set", "clear", "info", "check", "list");
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return new ArrayList<String>();
    }
}
