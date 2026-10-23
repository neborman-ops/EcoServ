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
   public static final String CAT_MONEY = "money";
   public static final String CAT_MINIGAME = "minigame";
   public static final String CAT_CC = "computercraft";
   public static final String CAT_ROLES = "roles";
   public static final String CAT_WORLDS = "worlds";
   // 1.9.0
   public static final String CAT_WARPS = "warps";
   public static final String CAT_CLAIMS = "claims";
   public static final String CAT_PLAYER_WORLDS = "player_worlds";
   public static final String CAT_GAMES = "games";

   // --- варпы ---
   public static boolean warpsEnabled = true;
   public static int warpsPerPlayer = 3;
   public static long warpSetPrice = 0L;
   public static int warpCooldownSeconds = 5;
   public static int warpDelaySeconds = 3;

   // --- приваты ---
   public static boolean claimsEnabled = true;
   public static int claimsMaxChunks = 16;
   public static long claimChunkPrice = 0L;
   public static boolean claimsExplosions = false;
   public static boolean claimsShowEnter = true;
   public static String claimsBlockedDims = "";

   // --- свои подмиры игроков ---
   public static boolean playerWorlds = false;
   public static int playerWorldsMax = 1;
   public static long playerWorldPrice = 0L;
   public static int playerWorldBorder = 512;
   public static String playerWorldTypes = "normal,flat,void";
   public static int playerWorldMembers = 10;

   // --- мини-игры (1.9.0) ---
   public static boolean gamesEnabled = true;
   public static String gamesDisabled = "";
   public static boolean gamesAutoWorld = true;
   public static int gamesMaxPlayers = 16;
   public static long gamesStakeMax = 10000L;

   // --- порталы ---
   public static int portalGraceSeconds = 15;
   public static boolean portalArrivalLock = true;
   public static boolean portalGraceNotice = true;
   public static int teleportDetectBlocks = 32;

   // --- миры ---
   public static int arrivalProtectSeconds = 5;
   public static boolean arrivalProtectAbyss = true;

   // --- деньги: история и заказы (вместо вклада) ---
   public static int historySize = 50;
   public static boolean ordersEnabled = true;
   public static int ordersPerPlayer = 5;
   public static int ordersDays = 7;
   public static long ordersMaxTotal = 0L;

   // --- мини-игра «Квестовая гонка» ---
   public static boolean minigameEnabled = true;
   public static long stakeMin = 10L;
   public static long stakeMax = 100000L;
   public static int minigameMaxPlayers = 20;
   public static int minigameMinPlayers = 2;
   public static int minigameMaxMinutes = 120;
   public static int lobbyIdleMinutes = 30;
   public static double minigameCutPercent = 0.0;
   public static String minigameArena = "";
   public static int[] timeOptions = {10, 15, 20, 30, 45, 60};

   // --- ComputerCraft ---
   public static boolean ccEnabled = true;
   public static boolean ccAllowMessages = false;
   public static int ccDistance = 16;
   public static long ccPayMax = 100000L;
   public static int ccRequestSeconds = 60;

   // 1.9.0: настройки «Чёрных дыр» и монолитов — в игре (меню порталов → «Настройка для всех»), world/ecoserv/portal_fx.json

   // --- роли ---
   public static final Map<String, long[]> SALARIES = new LinkedHashMap<>();
   public static final List<long[]> RANK_HOURS = new ArrayList<>();
   public static final List<String> RANK_ROLES = new ArrayList<>();
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
         // 1.8.0: вклад под проценты убран (слишком щедро для экономики) — старые настройки стираем
         if (cfg.hasCategory("bank")) {
            cfg.removeCategory(cfg.getCategory("bank"));
         }
         if (cfg.hasCategory(CAT_ROLES)) {
            cfg.getCategory(CAT_ROLES).remove("salary_to_bank");
         }
         historySize = cfg.getInt("history_size", CAT_MONEY, historySize, 5, 500, "Сколько последних операций с деньгами хранить у игрока");
         ordersEnabled = cfg.getBoolean("orders_enabled", CAT_MONEY, ordersEnabled,
            "Заказы: игрок заранее платит за вещи («куплю 64 железа по 5»), другие сдают их и сразу получают деньги");
         ordersPerPlayer = cfg.getInt("orders_per_player", CAT_MONEY, ordersPerPlayer, 1, 100, "Сколько открытых заказов может быть у игрока");
         ordersDays = cfg.getInt("orders_days", CAT_MONEY, ordersDays, 1, 90,
            "Через сколько дней заказ закрывается сам (несданное — деньги возвращаются заказчику)");
         ordersMaxTotal = cfg.getInt("orders_max_total", CAT_MONEY, (int)Math.min(Integer.MAX_VALUE, ordersMaxTotal), 0, Integer.MAX_VALUE,
            "Самый дорогой заказ (цена × количество), 0 — без ограничения");

         // --- 1.9.0 ---
         warpsEnabled = cfg.getBoolean("enabled", CAT_WARPS, warpsEnabled, "Варпы: /warp, меню варпов");
         warpsPerPlayer = cfg.getInt("per_player", CAT_WARPS, warpsPerPlayer, 0, 100,
            "Сколько своих варпов может поставить игрок (0 — только админы ставят варпы)");
         warpSetPrice = cfg.getInt("set_price", CAT_WARPS, (int)Math.min(Integer.MAX_VALUE, warpSetPrice), 0, Integer.MAX_VALUE,
            "Сколько стоит поставить свой варп");
         warpCooldownSeconds = cfg.getInt("cooldown_seconds", CAT_WARPS, warpCooldownSeconds, 0, 3600, "Пауза между телепортами на варпы");
         warpDelaySeconds = cfg.getInt("delay_seconds", CAT_WARPS, warpDelaySeconds, 0, 30,
            "Сколько секунд стоять на месте перед телепортом (0 — сразу; у админов всегда сразу)");

         claimsEnabled = cfg.getBoolean("enabled", CAT_CLAIMS, claimsEnabled, "Приваты территории по чанкам: /claim, меню-карта");
         claimsMaxChunks = cfg.getInt("max_chunks", CAT_CLAIMS, claimsMaxChunks, 0, 10000,
            "Сколько чанков может занять игрок (команда — сумма участников, но не больше чем x4)");
         claimChunkPrice = cfg.getInt("chunk_price", CAT_CLAIMS, (int)Math.min(Integer.MAX_VALUE, claimChunkPrice), 0, Integer.MAX_VALUE,
            "Цена одного чанка (возвращается половина при снятии привата)");
         claimsExplosions = cfg.getBoolean("explosions", CAT_CLAIMS, claimsExplosions, "Взрывы ломают блоки в приватах");
         claimsShowEnter = cfg.getBoolean("show_enter", CAT_CLAIMS, claimsShowEnter, "Показывать над хотбаром, чья территория, при входе");
         claimsBlockedDims = cfg.getString("blocked_dims", CAT_CLAIMS, claimsBlockedDims,
            "Номера измерений через запятую, где приватить нельзя (например спавн-мир)").trim();

         playerWorlds = cfg.getBoolean("enabled", CAT_PLAYER_WORLDS, playerWorlds,
            "Игроки могут создавать свои подмиры (вкладка «Мои миры» в меню миров появляется только когда включено)");
         playerWorldsMax = cfg.getInt("max_per_player", CAT_PLAYER_WORLDS, playerWorldsMax, 1, 20, "Сколько своих миров может быть у игрока");
         playerWorldPrice = cfg.getInt("price", CAT_PLAYER_WORLDS, (int)Math.min(Integer.MAX_VALUE, playerWorldPrice), 0, Integer.MAX_VALUE,
            "Цена создания своего мира");
         playerWorldBorder = cfg.getInt("border", CAT_PLAYER_WORLDS, playerWorldBorder, 0, 100000,
            "Размер барьера своего мира в блоках (0 — без барьера)");
         playerWorldTypes = cfg.getString("types", CAT_PLAYER_WORLDS, playerWorldTypes,
            "Какие типы миров можно создавать: normal, flat, void, nether, end (через запятую)").trim();
         playerWorldMembers = cfg.getInt("max_members", CAT_PLAYER_WORLDS, playerWorldMembers, 0, 200, "Сколько игроков можно пригласить в свой мир");

         gamesEnabled = cfg.getBoolean("enabled", CAT_GAMES, gamesEnabled, "Мини-игры 1.9.0: дезран, давление, погоня и другие (/games)");
         gamesDisabled = cfg.getString("disabled", CAT_GAMES, gamesDisabled,
            "id выключенных мини-игр через запятую (их можно выключать и в меню /games)").trim();
         gamesAutoWorld = cfg.getBoolean("auto_world", CAT_GAMES, gamesAutoWorld,
            "Если игре нужна арена, а подмира-арены нет — создать пустой мир для арен автоматически");
         gamesMaxPlayers = cfg.getInt("max_players", CAT_GAMES, gamesMaxPlayers, 1, 100, "Больше игроков в одной игре нельзя");
         gamesStakeMax = cfg.getInt("stake_max", CAT_GAMES, (int)Math.min(Integer.MAX_VALUE, gamesStakeMax), 0, Integer.MAX_VALUE,
            "Самая большая ставка в мини-играх (0 — без ставок)");

         minigameEnabled = cfg.getBoolean("enabled", CAT_MINIGAME, minigameEnabled, "Мини-игра «Квестовая гонка»: /race, кнопка в меню миров");
         stakeMin = cfg.getInt("stake_min", CAT_MINIGAME, (int)stakeMin, 0, Integer.MAX_VALUE,
            "Самая маленькая ставка, которую можно поставить в лобби (создатель лобби может поднять)");
         stakeMax = cfg.getInt("stake_max", CAT_MINIGAME, (int)Math.min(Integer.MAX_VALUE, stakeMax), 1, Integer.MAX_VALUE,
            "Самая большая ставка (создатель лобби может опустить)");
         if (stakeMax < stakeMin) {
            stakeMax = stakeMin;
         }
         minigameMaxPlayers = cfg.getInt("max_players", CAT_MINIGAME, minigameMaxPlayers, 2, 100, "Больше игроков в одном лобби нельзя");
         minigameMinPlayers = cfg.getInt("min_players", CAT_MINIGAME, minigameMinPlayers, 1, 100,
            "Меньше игроков начать нельзя (1 — для проверки в одиночку: тогда ставки не берутся)");
         minigameMaxMinutes = cfg.getInt("max_minutes", CAT_MINIGAME, minigameMaxMinutes, 5, 1440,
            "Обычный режим (без таймера) всё равно заканчивается через столько минут");
         lobbyIdleMinutes = cfg.getInt("lobby_idle_minutes", CAT_MINIGAME, lobbyIdleMinutes, 2, 1440, "Лобби, которое так и не начало игру, закрывается");
         minigameCutPercent = parseDouble(cfg.getString("server_cut_percent", CAT_MINIGAME, "0",
            "Комиссия сервера с банка игры, % (0 — всё уходит победителям)"), 0.0);
         minigameCutPercent = Math.min(50.0, minigameCutPercent);
         minigameArena = cfg.getString("arena", CAT_MINIGAME, minigameArena,
            "id подмира-арены. Пусто — любой подмир с пометкой «Мини-игра» (у каждой игры своя копия мира)").trim();
         timeOptions = parseInts(cfg.getString("time_options", CAT_MINIGAME, "10,15,20,30,45,60",
            "Варианты времени (минуты) для голосования в режиме «На время»"), timeOptions);

         ccEnabled = cfg.getBoolean("enabled", CAT_CC, ccEnabled,
            "Периферия ComputerCraft у блоков мода (терминал, склад, торговец): баланс, цены, аукцион, онлайн, мини-игры");
         ccAllowMessages = cfg.getBoolean("allow_messages", CAT_CC, ccAllowMessages,
            "Разрешить компьютерам писать игрокам в чат (sendMessage). Выключено, чтобы компьютеры не спамили");
         ccDistance = cfg.getInt("request_distance", CAT_CC, ccDistance, 1, 256,
            "Покупку/оплату с компьютера можно предложить только игроку не дальше стольких блоков от блока мода");
         ccPayMax = cfg.getInt("pay_max", CAT_CC, (int)Math.min(Integer.MAX_VALUE, ccPayMax), 0, Integer.MAX_VALUE,
            "Сколько максимум компьютер владельца может выплатить игроку за один вызов pay() (0 — pay() выключен)");
         ccRequestSeconds = cfg.getInt("request_seconds", CAT_CC, ccRequestSeconds, 10, 600,
            "Сколько секунд игрок может подтвердить покупку/оплату, предложенную компьютером");
         String[] salary = cfg.getStringList(
            "salary", CAT_ROLES, new String[]{"vip=50/30"},
            "Зарплата ролей: роль=сумма/минуты (vip=50/30 — 50 монет каждые 30 минут в сети). Выдаётся за самую выгодную роль игрока"
         );
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

   static int[] parseInts(String s, int[] def) {
      List<Integer> out = new ArrayList<>();
      for (String p : (s == null ? "" : s).split("[,; ]+")) {
         try {
            int v = Integer.parseInt(p.trim());
            if (v > 0 && v <= 1440 && !out.contains(v)) {
               out.add(v);
            }
         } catch (NumberFormatException ignored) {
         }
      }
      if (out.isEmpty()) {
         return def;
      }
      java.util.Collections.sort(out);
      int[] r = new int[out.size()];
      for (int i = 0; i < r.length; i++) {
         r[i] = out.get(i);
      }
      return r;
   }

   static double parseDouble(String s, double def) {
      try {
         double d = Double.parseDouble(s.trim().replace(',', '.'));
         return !Double.isNaN(d) && !Double.isInfinite(d) ? Math.max(0.0, d) : def;
      } catch (Exception e) {
         return def;
      }
   }

   /** Измерение, где приватить нельзя. */
   public static boolean claimBlocked(int dim) {
      for (String p : claimsBlockedDims.split("[,; ]+")) {
         if (p.trim().equals(Integer.toString(dim))) {
            return true;
         }
      }
      return false;
   }

   public static boolean gameDisabled(String id) {
      for (String p : gamesDisabled.split("[,; ]+")) {
         if (p.trim().equalsIgnoreCase(id)) {
            return true;
         }
      }
      return false;
   }

   public static boolean playerWorldType(String type) {
      for (String p : playerWorldTypes.split("[,; ]+")) {
         if (p.trim().equalsIgnoreCase(type)) {
            return true;
         }
      }
      return false;
   }
}
