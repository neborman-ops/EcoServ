package com.ecoserv.extra;

import com.ecoserv.economy.EconomyManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

/** /bank — вклад с процентами и история операций с деньгами. */
public class CommandBank extends CommandBase {
   public String getName() {
      return "bank";
   }

   public List<String> getAliases() {
      return Arrays.asList("вклад", "банк");
   }

   public String getUsage(ICommandSender sender) {
      return "/bank | /bank deposit <сумма|all> | /bank withdraw <сумма|all> | /bank history [страница] [ник]";
   }

   public int getRequiredPermissionLevel() {
      return 0;
   }

   public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
      return true;
   }

   public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
      if ("history".equals(sub) || "история".equals(sub)) {
         this.history(sender, args);
      } else {
         EntityPlayerMP player = getCommandSenderAsPlayer(sender);
         UUID id = player.getUniqueID();
         switch (sub) {
            case "deposit":
            case "put":
            case "положить":
               if (args.length < 2) {
                  ChatUtil.send(sender, "&cУкажи сумму: &f/bank deposit 100 &cили &f/bank deposit all");
               } else {
                  long amount = "all".equalsIgnoreCase(args[1]) ? EconomyManager.get(id) : parse(args[1]);
                  ChatUtil.send(sender, Bank.deposit(player, amount));
               }
               break;
            case "withdraw":
            case "take":
            case "снять":
               if (args.length < 2) {
                  ChatUtil.send(sender, "&cУкажи сумму: &f/bank withdraw 100 &cили &f/bank withdraw all");
               } else {
                  long amount = "all".equalsIgnoreCase(args[1]) ? Bank.balance(id) : parse(args[1]);
                  ChatUtil.send(sender, Bank.withdraw(player, amount));
               }
               break;
            default:
               this.info(player);
         }
      }
   }

   private void info(EntityPlayerMP player) {
      UUID id = player.getUniqueID();
      ExtraStore.Data d = ExtraStore.get(id);
      ChatUtil.sendRaw(player, ChatUtil.color("&6=== Банк ==="));
      ChatUtil.sendRaw(player, ChatUtil.color("&7Баланс: &f" + Fmt.money(EconomyManager.get(id)) + "   &7Вклад: &e" + Fmt.money(d.bank)));
      if (!ExtraConfig.bankEnabled) {
         ChatUtil.sendRaw(player, ChatUtil.color("&cБанк выключен на сервере — можно только снять вклад."));
      } else if (ExtraConfig.bankInterestPercent > 0.0) {
         long today = Bank.day();
         String when = d.interestDay == today
            ? "&aсегодня уже начислены"
            : "&7начислятся, когда пробудешь в сети &f" + ExtraConfig.bankMinOnlineMinutes + " мин &7(сейчас &f" + (d.onlineDay == today ? d.onlineToday / 60000L : 0L) + "&7)";
         ChatUtil.sendRaw(
            player,
            ChatUtil.color(
               "&7Проценты: &f" + Fmt.price(ExtraConfig.bankInterestPercent) + "% &7в сутки"
                  + (ExtraConfig.bankInterestCap > 0L ? " &8(не больше " + Fmt.money(ExtraConfig.bankInterestCap) + ")" : "")
                  + " &7≈ &e+" + Fmt.money(Bank.interestFor(d.bank))
            )
         );
         ChatUtil.sendRaw(player, ChatUtil.color(when));
         ChatUtil.sendRaw(player, ChatUtil.color("&7Всего получено процентов: &e" + Fmt.money(d.interestTotal)));
      }

      ChatUtil.send(player, ChatUtil.link("&a[Положить всё]", "/bank deposit all", "Положить весь баланс на вклад"));
      ChatUtil.send(player, ChatUtil.link("&e[Снять всё]", "/bank withdraw all", "Снять весь вклад на баланс"));
      ChatUtil.send(player, ChatUtil.link("&b[История операций]", "/bank history", "Последние операции с деньгами"));
   }

   private void history(ICommandSender sender, String[] args) throws CommandException {
      int page = 1;
      String who = null;

      for (int i = 1; i < args.length; i++) {
         try {
            page = Math.max(1, Integer.parseInt(args[i]));
         } catch (NumberFormatException e) {
            who = args[i];
         }
      }

      UUID id;
      String name;
      if (who != null) {
         if (!Perms.isAdmin(sender)) {
            ChatUtil.send(sender, "&cЧужую историю может смотреть только админ.");
            return;
         }

         id = PlayerUtil.uuidOf(who);
         name = who;
         if (id == null) {
            ChatUtil.send(sender, "&cИгрок &f" + who + " &cне найден.");
            return;
         }
      } else {
         EntityPlayerMP p = getCommandSenderAsPlayer(sender);
         id = p.getUniqueID();
         name = p.getName();
      }

      ExtraStore.Data d = ExtraStore.get(id);
      List<ExtraStore.Tx> list;
      synchronized (d) {
         list = new ArrayList<>(d.history);
      }

      int per = 10;
      int pages = Math.max(1, (list.size() + per - 1) / per);
      page = Math.min(page, pages);
      ChatUtil.sendRaw(sender, ChatUtil.color("&6=== История: " + name + " &7(" + page + "/" + pages + ") ==="));
      if (list.isEmpty()) {
         ChatUtil.sendRaw(sender, ChatUtil.color("&7Операций пока нет."));
      } else {
         SimpleDateFormat fmt = new SimpleDateFormat("dd.MM HH:mm");
         int from = list.size() - 1 - (page - 1) * per;

         for (int i = from; i > from - per && i >= 0; i--) {
            ExtraStore.Tx tx = list.get(i);
            ChatUtil.sendRaw(
               sender,
               ChatUtil.color("&8" + fmt.format(new Date(tx.time)) + " " + (tx.amount >= 0L ? "&a+" : "&c") + Fmt.money(tx.amount) + " &7" + tx.note)
            );
         }

         if (page < pages) {
            ChatUtil.send(sender, ChatUtil.link("&b[Дальше →]", "/bank history " + (page + 1) + (who == null ? "" : " " + who), "Следующая страница"));
         }
      }
   }

   static long parse(String s) {
      try {
         String t = s.trim().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
         long mul = 1L;
         if (t.endsWith("k") || t.endsWith("к")) {
            mul = 1000L;
            t = t.substring(0, t.length() - 1);
         } else if (t.endsWith("m") || t.endsWith("м")) {
            mul = 1000000L;
            t = t.substring(0, t.length() - 1);
         }

         double v = Double.parseDouble(t.replace(',', '.'));
         return v > 0.0 && !Double.isInfinite(v) ? (long)Math.min(9.0E18, v * (double)mul) : 0L;
      } catch (Exception e) {
         return 0L;
      }
   }

   public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
      if (args.length == 1) {
         return getListOfStringsMatchingLastWord(args, new String[]{"deposit", "withdraw", "history"});
      } else {
         return args.length == 2 && !"history".equalsIgnoreCase(args[0]) ? getListOfStringsMatchingLastWord(args, new String[]{"all", "100", "1000"}) : new ArrayList<>();
      }
   }
}
