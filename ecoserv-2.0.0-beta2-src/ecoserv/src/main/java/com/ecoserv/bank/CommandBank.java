package com.ecoserv.bank;

import com.ecoserv.ui.Ui;
import com.ecoserv.util.ChatUtil;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /bank — окно банка; /bank pay &lt;ник&gt; &lt;сумма&gt; [валюта]; /bank balance;
 * админ: /bank give|take|set &lt;ник&gt; &lt;валюта&gt; &lt;сумма&gt; (работает из консоли и сценариев).
 */
public class CommandBank extends CommandBase {

    @Override
    public String getName() {
        return "bank";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("банк", "wallet", "кошелёк");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/bank | /bank pay <ник> <сумма> [валюта] | /bank balance | /bank give|take|set <ник> <валюта> <сумма>";
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
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("give") || sub.equals("take") || sub.equals("set")) {
            if (!Perms.isAdmin(sender)) {
                ChatUtil.send(sender, "&cТолько для админов.");
                return;
            }
            if (args.length < 4) {
                ChatUtil.send(sender, "&c/bank " + sub + " <ник> <валюта> <сумма>");
                return;
            }
            UUID who = PlayerUtil.uuidOf(args[1]);
            Currency c = BankManager.currency(args[2]);
            Long amount = Ui.parseMoney(args[3]);
            if (who == null || c == null || amount == null || amount < 0) {
                ChatUtil.send(sender, "&cНе понял: игрок, валюта или сумма.");
                return;
            }
            if (sub.equals("give")) {
                BankManager.give(who, c.id, amount);
            } else if (sub.equals("take")) {
                if (!BankManager.take(who, c.id, amount)) {
                    ChatUtil.send(sender, "&cУ игрока столько нет.");
                    return;
                }
            } else {
                BankManager.set(who, c.id, amount);
            }
            BankManager.markDirty();
            ChatUtil.send(sender, "&aГотово: у " + args[1] + " теперь " + c.format(BankManager.balance(who, c.id)));
            return;
        }
        EntityPlayerMP p = getCommandSenderAsPlayer(sender);
        if (sub.isEmpty()) {
            BankScreen.open(p, null);
            return;
        }
        if (sub.equals("balance") || sub.equals("баланс")) {
            for (Currency c : BankManager.enabledCurrencies()) {
                long v = BankManager.balance(p.getUniqueID(), c.id);
                if (v != 0L || c.main()) {
                    ChatUtil.send(p, "&7" + c.name + ": " + c.format(v));
                }
            }
            return;
        }
        if (sub.equals("pay")) {
            if (args.length < 3) {
                ChatUtil.send(p, "&c/bank pay <ник> <сумма> [валюта]");
                return;
            }
            UUID to = PlayerUtil.uuidOf(args[1]);
            Long amount = Ui.parseMoney(args[2]);
            String cur = args.length > 3 ? args[3] : Currency.MAIN;
            if (to == null || amount == null) {
                ChatUtil.send(p, "&cНе понял: игрок или сумма.");
                return;
            }
            String err = BankManager.pay(p, to, cur, amount);
            ChatUtil.send(p, err != null ? err : "&aПереведено " + BankManager.currency(cur).format(amount) + " &aигроку " + args[1]);
            return;
        }
        ChatUtil.send(p, "&7" + getUsage(p));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<String>(Arrays.asList("pay", "balance"));
            if (Perms.isAdmin(sender)) {
                subs.addAll(Arrays.asList("give", "take", "set"));
            }
            return getListOfStringsMatchingLastWord(args, subs);
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        boolean admin = args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take") || args[0].equalsIgnoreCase("set");
        if ((admin && args.length == 3) || (!admin && args.length == 4)) {
            List<String> ids = new ArrayList<String>();
            for (Currency c : BankManager.enabledCurrencies()) {
                ids.add(c.id);
            }
            return getListOfStringsMatchingLastWord(args, ids);
        }
        return Collections.emptyList();
    }
}
