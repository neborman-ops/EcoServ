package com.ecoserv.extra;

import java.util.UUID;

/**
 * Подпись для записи в историю операций. Новый код ставит её явно (set/clear вокруг вызова
 * EconomyManager), для старого кода подпись угадывается по тому, кто вызвал EconomyManager.
 */
public final class MoneyNote {
   private static final ThreadLocal<String> NOTE = new ThreadLocal<>();
   private static final String[][] CALLERS = new String[][]{
      {"com.ecoserv.shop.", "Продажа в магазин"},
      {"com.ecoserv.auction.", "Аукцион"},
      {"com.ecoserv.trade.", "Обмен с игроком"},
      {"com.ecoserv.tile.TileVending", "Торговый автомат"},
      {"com.ecoserv.tile.", "Терминал"},
      {"com.ecoserv.server.PlaytimeRewards", "Награда за игру"},
      {"com.ecoserv.server.ServerEventHandler", "Потеря при смерти"},
      {"com.ecoserv.subworld.SubworldAccess", "Покупка доступа в подмир"},
      {"com.ecoserv.subworld.", "Подмиры"},
      {"com.ecoserv.economy.MoneyRequests", "Запрос денег"},
      {"com.ecoserv.network.packet.PacketPlayerAction", "Перевод игроку"},
      {"com.ecoserv.integration.quests.", "Награда квеста"},
      {"com.ecoserv.integration.", "Другой мод"},
      {"com.ecoserv.dates.", "Подарок к дате"},
      {"com.ecoserv.command.CommandEcoAdmin", "Изменено админом"},
      {"com.ecoserv.command.CommandEco", "Команда /eco"}
   };

   private MoneyNote() {
   }

   public static void set(String note) {
      NOTE.set(note);
   }

   public static void clear() {
      NOTE.remove();
   }

   static String current() {
      String n = NOTE.get();
      if (n != null) {
         return n;
      } else {
         StackTraceElement[] st = new Throwable().getStackTrace();

         for (StackTraceElement e : st) {
            String c = e.getClassName();
            if (c.startsWith("com.ecoserv.") && !c.startsWith("com.ecoserv.economy.EconomyManager") && !c.startsWith("com.ecoserv.extra.")) {
               for (String[] rule : CALLERS) {
                  if (c.startsWith(rule[0])) {
                     return rule[1];
                  }
               }

               return "Операция";
            }
         }

         return "Другой мод";
      }
   }

   /** Хук из EconomyManager: записать изменение баланса. */
   public static void record(UUID player, long delta) {
      try {
         if (delta != 0L && com.ecoserv.util.PlayerUtil.server() != null) {
            ExtraStore.record(player, delta, current());
         }
      } catch (Throwable ignored) {
      }
   }
}
