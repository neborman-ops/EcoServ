package com.ecoserv.command;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.economy.MoneyRequests;
import com.ecoserv.economy.MoneyRequestsScreen;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.server.PlaytimeRewards;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /eco — баланс, переводы, ежедневная награда, админские операции с деньгами. */
public class CommandEco extends CommandBase {

    @Override
    public String getName() {
        return "eco";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("money", "balance", "bal");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/eco [balance|pay|take|accept|deny|requests|top|daily|give|set]";
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
        if (args.length == 0 || "balance".equalsIgnoreCase(args[0]) || "bal".equalsIgnoreCase(args[0])) {
            balance(sender, args);
            return;
        }
        String sub = args[0].toLowerCase();

        if ("pay".equals(sub)) {
            pay(sender, args);
            return;
        }
        if ("top".equals(sub)) {
            top(sender);
            return;
        }
        if ("daily".equals(sub)) {
            daily(sender);
            return;
        }
        if ("pending".equals(sub)) {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            ChatUtil.send(sender, "&eОтложено к выдаче: &f"
                    + Fmt.money(EconomyManager.pendingOf(player.getUniqueID())));
            return;
        }
        if ("take".equals(sub) || "request".equals(sub) || "ask".equals(sub)) {
            // игрок не забирает деньги силой, а просит («можно денег?»); админ — с force, консоль — как раньше
            boolean force = args.length > 3 && "force".equalsIgnoreCase(args[args.length - 1]);
            if (!(sender instanceof EntityPlayerMP) || ("take".equals(sub) && force && Perms.isAdmin(sender))) {
                admin(sender, args, "take");
                return;
            }
            request(sender, args);
            return;
        }
        if ("accept".equals(sub) || "deny".equals(sub)) {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            String from = args.length > 1 ? args[1] : null;
            String err = "accept".equals(sub) ? MoneyRequests.accept(player, from) : MoneyRequests.deny(player, from);
            if (err != null) {
                ChatUtil.send(sender, err);
            } else if ("deny".equals(sub)) {
                ChatUtil.send(sender, "&7Отказано.");
            }
            return;
        }
        if ("requests".equals(sub)) {
            new MoneyRequestsScreen(getCommandSenderAsPlayer(sender)).open();
            return;
        }
        if ("give".equals(sub) || "set".equals(sub)) {
            admin(sender, args, sub);
            return;
        }
        ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
    }

    private void balance(ICommandSender sender, String[] args) throws CommandException {
        if (args.length >= 2) {
            if (!Perms.isAdmin(sender)) {
                ChatUtil.send(sender, "&cМожно смотреть только свой баланс.");
                return;
            }
            UUID target = PlayerUtil.uuidOf(args[1]);
            if (target == null) {
                ChatUtil.send(sender, "&cИгрок не найден.");
                return;
            }
            ChatUtil.send(sender, "&eБаланс &f" + args[1] + "&e: &a" + Fmt.money(EconomyManager.get(target)));
            return;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        ChatUtil.send(sender, "&eТвой баланс: &a" + Fmt.money(EconomyManager.get(player.getUniqueID())));
    }

    private void pay(ICommandSender sender, String[] args) throws CommandException {
        if (!EcoConfig.allowPayCommand) {
            ChatUtil.send(sender, "&cПереводы отключены.");
            return;
        }
        if (args.length < 3) {
            ChatUtil.send(sender, "&cИспользование: &f/eco pay <ник> <сумма>");
            return;
        }
        EntityPlayerMP from = getCommandSenderAsPlayer(sender);
        UUID target = PlayerUtil.uuidOf(args[1]);
        if (target == null) {
            ChatUtil.send(sender, "&cИгрок &f" + args[1] + " &cне найден.");
            return;
        }
        if (target.equals(from.getUniqueID())) {
            ChatUtil.send(sender, "&cСебе переводить нельзя.");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            ChatUtil.send(sender, "&cНекорректная сумма.");
            return;
        }
        if (amount < EcoConfig.payMinAmount) {
            ChatUtil.send(sender, "&cМинимальная сумма перевода: &f" + Fmt.money(EcoConfig.payMinAmount));
            return;
        }
        long received = EconomyManager.transfer(from.getUniqueID(), target, amount);
        if (received < 0L) {
            ChatUtil.send(sender, "&cНе хватает денег.");
            return;
        }
        ChatUtil.send(sender, "&aПереведено &f" + args[1] + "&a: &e" + Fmt.money(received)
                + (received < amount ? " &7(комиссия " + Fmt.money(amount - received) + ")" : ""));
        EntityPlayerMP to = PlayerUtil.online(target);
        if (to != null) {
            ChatUtil.send(to, "&aПолучено от &f" + from.getName() + "&a: &e" + Fmt.money(received));
            PacketHandler.sendPlayerState(to);
        }
        PacketHandler.sendPlayerState(from);
    }

    /** «Можно денег?» — просьба к другому игроку: он нажмёт «Дать» или «Отказать». */
    private void request(ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (args.length < 3) {
            ChatUtil.send(sender, "&cИспользование: &f/eco take <ник> <сумма> [зачем] &7— попросить денег у игрока");
            if (Perms.isAdmin(sender)) {
                ChatUtil.sendRaw(sender, " &7Админ: списать без спроса — &f/eco take <ник> <сумма> force");
            }
            return;
        }
        UUID target = PlayerUtil.knownUuid(args[1]);
        if (target == null) {
            ChatUtil.send(sender, "&cИгрок &f" + args[1] + " &cне найден.");
            return;
        }
        Long amount = com.ecoserv.ui.Ui.parseMoney(args[2]);
        if (amount == null || amount <= 0) {
            ChatUtil.send(sender, "&cНекорректная сумма. Примеры: 500, 1.5k");
            return;
        }
        String reason = args.length > 3 ? buildString(args, 3) : "";
        if (reason.equalsIgnoreCase("force")) {
            reason = "";
        }
        String err = MoneyRequests.ask(player, target, amount, reason);
        ChatUtil.send(sender, err != null ? err : "&aПросьба отправлена: &f" + PlayerUtil.nameOf(target) + " &7— &6"
                + Fmt.money(amount) + "&7. Он может дать или отказать.");
    }

    private void top(ICommandSender sender) {
        List<Map.Entry<UUID, Long>> top = EconomyManager.top(EcoConfig.topListSize);
        if (top.isEmpty()) {
            ChatUtil.send(sender, "&eПока пусто (или источник денег не умеет отдавать топ).");
            return;
        }
        ChatUtil.send(sender, "&6=== Богатейшие игроки ===");
        for (int i = 0; i < top.size(); i++) {
            ChatUtil.sendRaw(sender, " &e" + (i + 1) + ". &f" + PlayerUtil.nameOf(top.get(i).getKey())
                    + " &7- &a" + Fmt.money(top.get(i).getValue()));
        }
    }

    private void daily(ICommandSender sender) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        long amount = PlaytimeRewards.claimDaily(player);
        if (amount == -2L) {
            ChatUtil.send(sender, "&cЕжедневные награды отключены.");
        } else if (amount == -1L) {
            ChatUtil.send(sender, "&eСегодня награда уже получена. Заходи завтра!");
        } else {
            ChatUtil.send(sender, "&aЕжедневная награда: &e" + Fmt.money(amount)
                    + " &7(серия: " + com.ecoserv.economy.StatsStore.get(player.getUniqueID()).dailyStreak + ")");
        }
    }

    private void admin(ICommandSender sender, String[] args, String sub) {
        if (!Perms.isAdmin(sender)) {
            ChatUtil.send(sender, "&cНужны права администратора.");
            return;
        }
        if (args.length < 3) {
            ChatUtil.send(sender, "&cИспользование: &f/eco " + sub + " <ник> <сумма>");
            return;
        }
        UUID target = PlayerUtil.uuidOf(args[1]);
        if (target == null) {
            ChatUtil.send(sender, "&cИгрок &f" + args[1] + " &cне найден.");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            ChatUtil.send(sender, "&cНекорректная сумма.");
            return;
        }
        if ("give".equals(sub)) {
            EconomyManager.give(target, amount);
            ChatUtil.send(sender, "&aВыдано &f" + args[1] + "&a: &e" + Fmt.money(amount));
        } else if ("take".equals(sub)) {
            if (EconomyManager.take(target, amount)) {
                ChatUtil.send(sender, "&aСписано у &f" + args[1] + "&a: &e" + Fmt.money(amount));
            } else {
                ChatUtil.send(sender, "&cУ игрока недостаточно денег.");
                return;
            }
        } else {
            EconomyManager.set(target, amount);
            ChatUtil.send(sender, "&aБаланс &f" + args[1] + "&a установлен: &e" + Fmt.money(amount));
        }
        EntityPlayerMP online = PlayerUtil.online(target);
        if (online != null) {
            PacketHandler.sendPlayerState(online);
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return Perms.isAdmin(sender)
                    ? getListOfStringsMatchingLastWord(args, "balance", "pay", "take", "accept", "deny", "requests", "top",
                    "daily", "pending", "give", "set")
                    : getListOfStringsMatchingLastWord(args, "balance", "pay", "take", "accept", "deny", "requests", "top",
                    "daily", "pending");
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return new ArrayList<String>();
    }
}
