package com.ecoserv.extra;

import com.ecoserv.economy.EconomyManager;
import com.ecoserv.role.Role;
import com.ecoserv.role.RoleManager;
import com.ecoserv.server.PlaytimeRewards;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import java.util.Map.Entry;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayerMP;

/** Зарплата ролей: роль=сумма/минуты в настройках (категория roles, параметр salary). Время AFK не считается. */
public final class Salary {
   private Salary() {
   }

   /** Самая выгодная зарплата игрока: {сумма, минуты} или null. */
   @Nullable
   public static long[] best(UUID player) {
      long[] best = null;
      double bestRate = 0.0;

      for (Entry<String, long[]> e : ExtraConfig.SALARIES.entrySet()) {
         if (RoleManager.has(player, e.getKey())) {
            double rate = (double)e.getValue()[0] / (double)e.getValue()[1];
            if (rate > bestRate) {
               bestRate = rate;
               best = e.getValue();
            }
         }
      }

      return best;
   }

   @Nullable
   public static String bestRole(UUID player) {
      long[] b = best(player);
      if (b == null) {
         return null;
      } else {
         for (Entry<String, long[]> e : ExtraConfig.SALARIES.entrySet()) {
            if (e.getValue() == b) {
               Role r = RoleManager.get(e.getKey());
               return r == null ? e.getKey() : r.title();
            }
         }

         return null;
      }
   }

   static void secondOnline(EntityPlayerMP player) {
      UUID id = player.getUniqueID();
      long[] b = best(id);
      if (b != null) {
         if (!PlaytimeRewards.isAfk(id)) {
            ExtraStore.Data d = ExtraStore.get(id);
            d.salaryMs += 1000L;
            long period = b[1] * 60000L;
            if (d.salaryMs >= period) {
               d.salaryMs -= period;
               if (d.salaryMs > period) {
                  d.salaryMs = 0L;
               }

               long amount = b[0];
               String role = bestRole(id);
               String note = "Зарплата" + (role == null ? "" : ": " + ChatUtil.strip(ChatUtil.color(role)));
               if (ExtraConfig.salaryToBank && ExtraConfig.bankEnabled) {
                  Bank.credit(id, amount, note);
               } else {
                  MoneyNote.set(note);

                  try {
                     EconomyManager.give(id, amount);
                  } finally {
                     MoneyNote.clear();
                  }
               }

               ExtraStore.markDirty();
               ChatUtil.actionBar(player, "&aЗарплата" + (role == null ? "" : " &7(" + role + "&7)") + "&a: &e+" + Fmt.money(amount));
               ExtraSync.send(player);
            }
         }
      }
   }

   /** Сколько осталось до следующей зарплаты, мс (или -1). */
   public static long untilNext(UUID player) {
      long[] b = best(player);
      if (b == null) {
         return -1L;
      } else {
         ExtraStore.Data d = ExtraStore.peek(player);
         long done = d == null ? 0L : d.salaryMs;
         return Math.max(0L, b[1] * 60000L - done);
      }
   }
}
