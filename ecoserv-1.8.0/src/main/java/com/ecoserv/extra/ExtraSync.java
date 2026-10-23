package com.ecoserv.extra;

import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketExtraState;
import com.ecoserv.util.ChatUtil;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/** Отправка клиенту данных для вкладок «Банк» и «История» окна магазина. */
public final class ExtraSync {
   private ExtraSync() {
   }

   public static void send(EntityPlayerMP player) {
      if (player != null) {
         try {
            PacketHandler.sendTo(build(player), player);
         } catch (Throwable ignored) {
         }
      }
   }

   static PacketExtraState build(EntityPlayerMP player) {
      UUID id = player.getUniqueID();
      ExtraStore.Data d = ExtraStore.get(id);
      PacketExtraState m = new PacketExtraState();
      m.bankEnabled = ExtraConfig.bankEnabled;
      m.bank = d.bank;
      m.interestTotal = d.interestTotal;
      m.interestPercent = ExtraConfig.bankInterestPercent;
      m.interestCap = ExtraConfig.bankInterestCap;
      m.interestNext = Bank.interestFor(d.bank);
      long today = Bank.day();
      m.interestDoneToday = d.interestDay == today;
      m.onlineMinutesToday = d.onlineDay == today ? (int)(d.onlineToday / 60000L) : 0;
      m.needOnlineMinutes = ExtraConfig.bankMinOnlineMinutes;
      long[] sal = Salary.best(id);
      if (sal != null) {
         m.salaryAmount = sal[0];
         m.salaryMinutes = (int)sal[1];
         m.salaryLeftMs = Salary.untilNext(id);
         String role = Salary.bestRole(id);
         m.salaryRole = role == null ? "" : ChatUtil.color(role);
      }

      String next = Ranks.describeNext(id);
      m.nextRank = next == null ? "" : ChatUtil.color(next);
      m.playtimeMs = Ranks.playtime(id);
      synchronized (d) {
         int from = Math.max(0, d.history.size() - 100);

         for (int i = d.history.size() - 1; i >= from; i--) {
            ExtraStore.Tx tx = d.history.get(i);
            m.txTimeAmount.add(new long[]{tx.time, tx.amount});
            m.txNotes.add(tx.note);
         }
      }

      return m;
   }
}
