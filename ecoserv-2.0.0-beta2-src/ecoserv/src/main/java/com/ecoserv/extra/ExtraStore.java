package com.ecoserv.extra;

import com.ecoserv.EcoServ;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Данные новых механик по игрокам: вклад, история операций, зарплата, звания. Файл extras.json в папке данных мода. */
public final class ExtraStore {
   private static final Type TYPE = (new TypeToken<HashMap<String, ExtraStore.Data>>() {
   }).getType();
   private static final Map<UUID, ExtraStore.Data> DATA = new ConcurrentHashMap<>();
   private static volatile boolean dirty;
   private static volatile boolean loaded;

   private ExtraStore() {
   }

   public static final class Tx {
      public long time;
      public long amount;
      public String note = "";

      public Tx() {
      }

      Tx(long time, long amount, String note) {
         this.time = time;
         this.amount = amount;
         this.note = note == null ? "" : note;
      }
   }

   public static final class Data {
      /** Вклад из 1.8.0-предварительной (банк убран): при запуске возвращается на баланс. */
      public long bank;
      /** Накопленное время в сети для зарплаты, мс. */
      public long salaryMs;
      public List<ExtraStore.Tx> history = new ArrayList<>();
      public Set<String> ranks = new LinkedHashSet<>();

      void normalize() {
         if (this.history == null) {
            this.history = new ArrayList<>();
         }

         if (this.ranks == null) {
            this.ranks = new LinkedHashSet<>();
         }

         if (this.bank < 0L) {
            this.bank = 0L;
         }
      }
   }

   private static File file() {
      return DataStore.file("extras.json");
   }

   public static synchronized void load() {
      DATA.clear();

      try {
         HashMap<String, ExtraStore.Data> raw = JsonUtil.readType(file(), TYPE, new HashMap<>());

         for (Entry<String, ExtraStore.Data> e : raw.entrySet()) {
            try {
               if (e.getValue() != null) {
                  e.getValue().normalize();
                  DATA.put(UUID.fromString(e.getKey()), e.getValue());
               }
            } catch (IllegalArgumentException ignored) {
            }
         }
      } catch (Throwable t) {
         EcoServ.LOG.error("Не удалось прочитать extras.json", t);
      }

      loaded = true;
      dirty = false;
   }

   public static void ensureLoaded() {
      if (!loaded) {
         load();
      }
   }

   public static void unload() {
      refunded = false;
      saveIfDirty();
      DATA.clear();
      loaded = false;
   }

   private static boolean refunded;

   /**
    * Банк (вклад под проценты) убран: всё, что лежало во вкладах, один раз возвращается владельцам на баланс
    * (оффлайн-игрокам — как отложенная выплата). Вызывается из тика сервера, когда экономика уже готова.
    */
   public static void refundClosedBank() {
      if (refunded) {
         return;
      }
      refunded = true;
      int n = 0;
      for (Entry<UUID, ExtraStore.Data> e : view().entrySet()) {
         ExtraStore.Data d = e.getValue();
         long sum;
         synchronized (d) {
            sum = d.bank;
            d.bank = 0L;
         }
         if (sum > 0L) {
            MoneyNote.set("Возврат вклада (банк закрыт)");
            try {
               com.ecoserv.economy.EconomyManager.give(e.getKey(), sum);
            } finally {
               MoneyNote.clear();
            }
            n++;
         }
      }
      if (n > 0) {
         markDirty();
         saveIfDirty();
         EcoServ.LOG.info("Банк убран: вклады возвращены на баланс {} игрокам", n);
      }
   }

   public static ExtraStore.Data get(UUID player) {
      ensureLoaded();
      ExtraStore.Data d = DATA.get(player);
      if (d == null) {
         d = new ExtraStore.Data();
         DATA.put(player, d);
      }

      return d;
   }

   public static ExtraStore.Data peek(UUID player) {
      ensureLoaded();
      return DATA.get(player);
   }

   public static Map<UUID, ExtraStore.Data> view() {
      ensureLoaded();
      return DATA;
   }

   public static void markDirty() {
      dirty = true;
   }

   public static synchronized void saveIfDirty() {
      if (dirty && loaded) {
         HashMap<String, ExtraStore.Data> raw = new HashMap<>();

         for (Entry<UUID, ExtraStore.Data> e : DATA.entrySet()) {
            raw.put(e.getKey().toString(), e.getValue());
         }

         if (JsonUtil.write(file(), raw)) {
            dirty = false;
         }
      }
   }

   /** Записать операцию в историю игрока. */
   public static void record(UUID player, long amount, String note) {
      if (player != null && amount != 0L) {
         ExtraStore.Data d = get(player);
         synchronized (d) {
            d.history.add(new ExtraStore.Tx(System.currentTimeMillis(), amount, note));
            int max = Math.max(5, ExtraConfig.historySize);

            while (d.history.size() > max) {
               d.history.remove(0);
            }
         }

         dirty = true;
      }
   }
}
