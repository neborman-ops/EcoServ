package com.ecoserv.extra;

import com.ecoserv.economy.EconomyManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Банковский вклад. Деньги на вкладе нельзя потратить, пока их не снять, зато раз в сутки
 * на них начисляются проценты — если игрок за эти сутки пробыл в сети нужное число минут.
 */
public final class Bank {
   private Bank() {
   }

   public static long day() {
      return Math.floorDiv(System.currentTimeMillis() + java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()), 86400000L);
   }

   public static long balance(UUID player) {
      ExtraStore.Data d = ExtraStore.peek(player);
      return d == null ? 0L : d.bank;
   }

   /** Положить на вклад. Возвращает текст для игрока. */
   public static String deposit(EntityPlayerMP player, long amount) {
      if (!ExtraConfig.bankEnabled) {
         return "&cБанк выключен на сервере.";
      } else if (amount <= 0L) {
         return "&cСумма должна быть больше нуля.";
      } else {
         UUID id = player.getUniqueID();
         ExtraStore.Data d = ExtraStore.get(id);
         if (ExtraConfig.bankMaxDeposit > 0L) {
            long room = ExtraConfig.bankMaxDeposit - d.bank;
            if (room <= 0L) {
               return "&eВклад уже максимальный: &f" + Fmt.money(ExtraConfig.bankMaxDeposit);
            }

            amount = Math.min(amount, room);
         }

         if (!EconomyManager.has(id, amount)) {
            return "&cНа балансе нет &f" + Fmt.money(amount) + "&c.";
         } else {
            MoneyNote.set("Вклад: пополнение");

            boolean ok;
            try {
               ok = EconomyManager.take(id, amount);
            } finally {
               MoneyNote.clear();
            }

            if (!ok) {
               return "&cНе удалось списать деньги.";
            } else {
               synchronized (d) {
                  d.bank += amount;
               }

               ExtraStore.markDirty();
               ExtraSync.send(player);
               return "&aНа вклад положено &f" + Fmt.money(amount) + "&a. Во вкладе: &e" + Fmt.money(d.bank);
            }
         }
      }
   }

   /** Снять со вклада. */
   public static String withdraw(EntityPlayerMP player, long amount) {
      if (amount <= 0L) {
         return "&cСумма должна быть больше нуля.";
      } else {
         UUID id = player.getUniqueID();
         ExtraStore.Data d = ExtraStore.get(id);
         synchronized (d) {
            if (d.bank <= 0L) {
               return "&eВклад пуст.";
            }

            amount = Math.min(amount, d.bank);
            d.bank -= amount;
         }

         MoneyNote.set("Вклад: снятие");

         try {
            EconomyManager.give(id, amount);
         } finally {
            MoneyNote.clear();
         }

         ExtraStore.markDirty();
         ExtraSync.send(player);
         return "&aСо вклада снято &f" + Fmt.money(amount) + "&a. Во вкладе: &e" + Fmt.money(d.bank);
      }
   }

   /** Положить деньги прямо во вклад (зарплата, награда квеста). */
   public static void credit(UUID player, long amount, String note) {
      if (amount > 0L) {
         ExtraStore.Data d = ExtraStore.get(player);
         synchronized (d) {
            d.bank += amount;
         }

         ExtraStore.record(player, amount, note + " (во вклад)");
         ExtraStore.markDirty();
      }
   }

   public static long interestFor(long bank) {
      if (bank > 0L && !(ExtraConfig.bankInterestPercent <= 0.0)) {
         long v = (long)Math.floor((double)bank * ExtraConfig.bankInterestPercent / 100.0);
         if (ExtraConfig.bankInterestCap > 0L) {
            v = Math.min(v, ExtraConfig.bankInterestCap);
         }

         return Math.max(0L, v);
      } else {
         return 0L;
      }
   }

   /** Раз в секунду для каждого игрока в сети: учёт времени в сети за сутки и начисление процентов. */
   static void secondOnline(EntityPlayerMP player) {
      if (ExtraConfig.bankEnabled) {
         ExtraStore.Data d = ExtraStore.get(player.getUniqueID());
         long today = day();
         if (d.onlineDay != today) {
            d.onlineDay = today;
            d.onlineToday = 0L;
         }

         d.onlineToday += 1000L;
         if (d.bank > 0L && d.interestDay != today && d.onlineToday >= ExtraConfig.bankMinOnlineMinutes * 60000L) {
            d.interestDay = today;
            long add = interestFor(d.bank);
            if (add > 0L) {
               synchronized (d) {
                  d.bank += add;
                  d.interestTotal += add;
               }

               ExtraStore.record(player.getUniqueID(), add, "Проценты по вкладу (во вклад)");
               ChatUtil.send(player, "&aПроценты по вкладу: &e+" + Fmt.money(add) + "&a. Во вкладе: &f" + Fmt.money(d.bank));
               ExtraSync.send(player);
            }
         }

         ExtraStore.markDirty();
      }
   }
}
