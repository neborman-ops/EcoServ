package com.ecoserv.extra;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraftforge.common.config.Configuration;

/**
 * Настройки новых механик. Живут в том же ecoserv.cfg (отдельные категории), поэтому видны
 * и меняются во внутриигровом редакторе настроек. Перечитываются раз в несколько секунд —
 * изменения из редактора или /eco reload подхватываются без перезапуска.
 */
public final class ExtraConfig {
   public static final String CAT_PORTALS = "portals";
   public static final String CAT_BANK = "bank";
   public static final String CAT_ROLES = "roles";
   public static final String CAT_WORLDS = "worlds";

   // --- порталы ---
   public static int portalGraceSeconds = 15;
   public static boolean portalArrivalLock = true;
   public static boolean portalGraceNotice = true;
   public static int teleportDetectBlocks = 32;

   // --- миры ---
   public static int arrivalProtectSeconds = 5;
   public static boolean arrivalProtectAbyss = true;

   // --- банк ---
   public static boolean bankEnabled = true;
   public static double bankInterestPercent = 1.0;
   public static long bankInterestCap = 1000L;
   public static long bankMaxDeposit = 0L;
   public static int bankMinOnlineMinutes = 10;
   public static int historySize = 50;

   // --- роли ---
   public static final Map<String, long[]> SALARIES = new LinkedHashMap<>();
   public static final List<long[]> RANK_HOURS = new ArrayList<>();
   public static final List<String> RANK_ROLES = new ArrayList<>();
   public static boolean salaryToBank = false;
   public static boolean ranksAnnounce = true;

   private static long lastRefresh;
   private static boolean loadedOnce;

   private ExtraConfig() {
   }

   /** Перечитать не чаще раза в 5 секунд (дёшево: только поиск в картах Configuration). */
   public static void tick() {
      long now = System.currentTimeMillis();
      if (!loadedOnce || now - lastRefresh > 5000L) {
         lastRefresh = now;
         refresh();
      }
   }

   public static void refresh() {
      Configuration cfg = EcoConfig.raw();
      if (cfg == null) {
         return;
      }

      try {
         portalGraceSeconds = cfg.getInt(
            "grace_seconds", CAT_PORTALS, portalGraceSeconds, 0, 600,
            "Сколько секунд после любого телепорта порталы не срабатывают (защита от застревания и перебрасывания туда-обратно). 0 — выключить"
         );
         portalArrivalLock = cfg.getBoolean(
            "arrival_lock", CAT_PORTALS, portalArrivalLock,
            "Если игрок появился прямо в портале — этот портал не сработает, пока игрок из него не выйдет"
         );
         portalGraceNotice = cfg.getBoolean(
            "grace_notice", CAT_PORTALS, portalGraceNotice, "Показывать над хотбаром, через сколько секунд портал снова заработает"
         );
         teleportDetectBlocks = cfg.getInt(
            "teleport_detect_blocks", CAT_PORTALS, teleportDetectBlocks, 8, 4096,
            "Прыжок дальше этого числа блоков за 5 тиков считается телепортом (команды, другие моды, жемчуг)"
         );
         arrivalProtectSeconds = cfg.getInt(
            "arrival_protect_seconds", CAT_WORLDS, arrivalProtectSeconds, 0, 120,
            "Неуязвимость после телепорта, входа в мир и возрождения (сек). 0 — выключить"
         );
         arrivalProtectAbyss = cfg.getBoolean(
            "arrival_protect_abyss", CAT_WORLDS, arrivalProtectAbyss, "Давать ту же защиту после падения в ГЛИТЧ-бездну"
         );
         bankEnabled = cfg.getBoolean("enabled", CAT_BANK, bankEnabled, "Банковский вклад: /bank, вкладка «Банк» в окне магазина");
         bankInterestPercent = parseDouble(
            cfg.getString("interest_percent_per_day", CAT_BANK, "1.0", "Процент на вклад за реальные сутки (дробное число, 0 — без процентов)"),
            1.0
         );
         bankInterestCap = cfg.getInt(
            "interest_cap_per_day", CAT_BANK, (int)Math.min(Integer.MAX_VALUE, bankInterestCap), 0, Integer.MAX_VALUE,
            "Не больше стольки монет процентов в сутки (0 — без ограничения)"
         );
         bankMaxDeposit = cfg.getInt(
            "max_deposit", CAT_BANK, (int)Math.min(Integer.MAX_VALUE, bankMaxDeposit), 0, Integer.MAX_VALUE,
            "Самый большой вклад одного игрока (0 — без ограничения)"
         );
         bankMinOnlineMinutes = cfg.getInt(
            "interest_min_online_minutes", CAT_BANK, bankMinOnlineMinutes, 0, 1440,
            "Проценты начисляются, только если игрок был в сети хотя бы столько минут за эти сутки"
         );
         historySize = cfg.getInt("history_size", CAT_BANK, historySize, 5, 500, "Сколько последних операций с деньгами хранить у игрока");
         String[] salary = cfg.getStringList(
            "salary", CAT_ROLES, new String[]{"vip=50/30"},
            "Зарплата ролей: роль=сумма/минуты (vip=50/30 — 50 монет каждые 30 минут в сети). Выдаётся за самую выгодную роль игрока"
         );
         salaryToBank = cfg.getBoolean("salary_to_bank", CAT_ROLES, salaryToBank, "Зарплату класть сразу во вклад, а не на баланс");
         String[] ranks = cfg.getStringList(
            "playtime_ranks", CAT_ROLES, new String[0],
            "Звания за наигранное время: часы=роль (10=regular). Роль выдаётся навсегда, когда игрок наиграет нужное время"
         );
         ranksAnnounce = cfg.getBoolean("playtime_ranks_announce", CAT_ROLES, ranksAnnounce, "Объявлять в чат о новом звании");
         parseSalary(salary);
         parseRanks(ranks);
         loadedOnce = true;
         if (cfg.hasChanged()) {
            cfg.save();
         }
      } catch (Throwable t) {
         EcoServ.LOG.warn("Новые настройки EcoServ не прочитаны: {}", t.toString());
      }
   }

   private static void parseSalary(String[] lines) {
      SALARIES.clear();
      if (lines != null) {
         for (String line : lines) {
            if (line != null) {
               String[] kv = line.split("=", 2);
               if (kv.length == 2) {
                  String[] am = kv[1].split("/", 2);
                  try {
                     long amount = Long.parseLong(am[0].trim());
                     long minutes = am.length > 1 ? Long.parseLong(am[1].trim()) : 60L;
                     if (amount > 0L && minutes > 0L) {
                        SALARIES.put(kv[0].trim().toLowerCase(Locale.ROOT), new long[]{amount, minutes});
                     }
                  } catch (NumberFormatException ignored) {
                  }
               }
            }
         }
      }
   }

   private static void parseRanks(String[] lines) {
      RANK_HOURS.clear();
      RANK_ROLES.clear();
      if (lines != null) {
         for (String line : lines) {
            if (line != null) {
               String[] kv = line.split("=", 2);
               if (kv.length == 2) {
                  try {
                     double hours = Double.parseDouble(kv[0].trim().replace(',', '.'));
                     String role = kv[1].trim().toLowerCase(Locale.ROOT);
                     if (hours >= 0.0 && !role.isEmpty()) {
                        RANK_HOURS.add(new long[]{(long)(hours * 3600000.0)});
                        RANK_ROLES.add(role);
                     }
                  } catch (NumberFormatException ignored) {
                  }
               }
            }
         }
      }
   }

   static double parseDouble(String s, double def) {
      try {
         double d = Double.parseDouble(s.trim().replace(',', '.'));
         return !Double.isNaN(d) && !Double.isInfinite(d) ? Math.max(0.0, d) : def;
      } catch (Exception e) {
         return def;
      }
   }
}
