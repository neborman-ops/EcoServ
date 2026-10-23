package com.ecoserv.region;

import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.extra.ExtraEvents;
import com.ecoserv.util.ChatUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Защита порталов от «застревания»:
 *  1) после любого телепорта (портал, команда, вход в подмир, бездна, другой мод) порталы
 *     не срабатывают ExtraConfig.portalGraceSeconds секунд;
 *  2) портал, в котором игрок появился, не сработает, пока игрок из него не выйдет;
 *  3) портал, в который игрок зашёл во время ожидания, сработает сам, когда ожидание
 *     кончится (если игрок всё ещё в нём) — заново заходить не нужно.
 * Телепорт определяется и явно (start), и по прыжку позиции/смене измерения между
 * проверками зон — так ловятся телепорты, о которых мод не знает.
 */
public final class PortalGuard {
   private static final Map<UUID, PortalGuard.State> STATES = new HashMap<>();

   private PortalGuard() {
   }

   private static final class State {
      long graceUntil;
      boolean lockNext;
      int dim = Integer.MIN_VALUE;
      double x;
      double y;
      double z;
      final Set<String> arrival = new HashSet<>();
      final Set<String> deferred = new HashSet<>();
      long lastNotice;
   }

   private static PortalGuard.State state(EntityPlayerMP player) {
      PortalGuard.State s = STATES.get(player.getUniqueID());
      if (s == null) {
         s = new PortalGuard.State();
         s.dim = player.dimension;
         s.x = player.posX;
         s.y = player.posY;
         s.z = player.posZ;
         STATES.put(player.getUniqueID(), s);
      }

      return s;
   }

   /** Явно начать «остывание» порталов: вызывать прямо перед телепортом игрока. */
   public static void start(EntityPlayerMP player) {
      PortalGuard.State s = state(player);
      long ms = Math.max(0, ExtraConfig.portalGraceSeconds) * 1000L;
      s.graceUntil = Math.max(s.graceUntil, System.currentTimeMillis() + ms);
      s.lockNext = true;
      s.deferred.clear();
      ExtraEvents.protectArrival(player, false);
   }

   public static void forget(UUID player) {
      STATES.remove(player);
   }

   public static long graceLeftMillis(EntityPlayerMP player) {
      PortalGuard.State s = STATES.get(player.getUniqueID());
      return s == null ? 0L : Math.max(0L, s.graceUntil - System.currentTimeMillis());
   }

   /**
    * Вызывается из RegionManager.tickPlayer после того, как посчитан набор зон,
    * в которых стоит игрок. Ловит телепорты и снимает «замки», из которых игрок вышел.
    */
   static void observe(EntityPlayerMP player, Set<String> now) {
      PortalGuard.State s = state(player);
      int limit = Math.max(8, ExtraConfig.teleportDetectBlocks);
      double dx = player.posX - s.x;
      double dy = player.posY - s.y;
      double dz = player.posZ - s.z;
      boolean jumped = s.dim != player.dimension || dx * dx + dy * dy + dz * dz > (double)limit * (double)limit;
      s.dim = player.dimension;
      s.x = player.posX;
      s.y = player.posY;
      s.z = player.posZ;
      if (jumped) {
         long ms = Math.max(0, ExtraConfig.portalGraceSeconds) * 1000L;
         s.graceUntil = Math.max(s.graceUntil, System.currentTimeMillis() + ms);
         s.lockNext = true;
         s.deferred.clear();
         ExtraEvents.protectArrival(player, false);
      }

      if (s.lockNext) {
         s.lockNext = false;
         s.arrival.clear();
         if (ExtraConfig.portalArrivalLock) {
            for (String name : now) {
               Region r = RegionManager.get(name);
               if (r != null && r.isPortal()) {
                  s.arrival.add(name);
               }
            }
         }
      }

      Iterator<String> it = s.arrival.iterator();

      while (it.hasNext()) {
         if (!now.contains(it.next())) {
            it.remove();
         }
      }

      it = s.deferred.iterator();

      while (it.hasNext()) {
         if (!now.contains(it.next())) {
            it.remove();
         }
      }
   }

   /** Идёт пауза порталов после телепорта. */
   public static boolean isPaused(EntityPlayerMP player) {
      PortalGuard.State s = STATES.get(player.getUniqueID());
      return s != null && s.graceUntil > System.currentTimeMillis();
   }

   /** true — портал сейчас срабатывать не должен. */
   static boolean blocks(EntityPlayerMP player, Region r) {
      if (!r.isPortal()) {
         return false;
      } else {
         PortalGuard.State s = state(player);
         if (s.arrival.contains(r.name)) {
            notice(player, s, r, -1L);
            return true;
         } else {
            long left = s.graceUntil - System.currentTimeMillis();
            if (left > 0L) {
               s.deferred.add(r.name);
               notice(player, s, r, left);
               return true;
            } else {
               return false;
            }
         }
      }
   }

   /** Порталы, в которые игрок вошёл во время ожидания и всё ещё стоит в них, — пора срабатывать. */
   static Set<String> due(EntityPlayerMP player) {
      PortalGuard.State s = STATES.get(player.getUniqueID());
      if (s != null && !s.deferred.isEmpty() && System.currentTimeMillis() >= s.graceUntil) {
         Set<String> out = new HashSet<>(s.deferred);
         out.removeAll(s.arrival);
         s.deferred.clear();
         return out;
      } else {
         return new HashSet<>();
      }
   }

   private static void notice(EntityPlayerMP player, PortalGuard.State s, Region r, long left) {
      long now = System.currentTimeMillis();
      if (ExtraConfig.portalGraceNotice && now - s.lastNotice >= 1000L) {
         s.lastNotice = now;
         if (left < 0L) {
            ChatUtil.actionBar(player, "&7Ты появился в портале «" + r.name + "» — выйди из него и зайди снова, чтобы перейти");
         } else {
            ChatUtil.actionBar(player, "&7Портал «" + r.name + "» заработает через &f" + (left + 999L) / 1000L + " с");
         }
      }
   }
}
