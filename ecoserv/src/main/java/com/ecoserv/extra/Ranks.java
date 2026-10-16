package com.ecoserv.extra;

import com.ecoserv.EcoServ;
import com.ecoserv.economy.StatsStore;
import com.ecoserv.role.Role;
import com.ecoserv.role.RoleManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayerMP;

/** Звания за наигранное время: часы=роль в настройках (roles.playtime_ranks). Роль выдаётся навсегда, один раз. */
public final class Ranks {
   private Ranks() {
   }

   public static long playtime(UUID player) {
      try {
         return StatsStore.get(player).playtimeMillis;
      } catch (Throwable t) {
         return 0L;
      }
   }

   static void check(EntityPlayerMP player) {
      if (!ExtraConfig.RANK_ROLES.isEmpty()) {
         UUID id = player.getUniqueID();
         long played = playtime(id);
         ExtraStore.Data d = ExtraStore.get(id);

         for (int i = 0; i < ExtraConfig.RANK_ROLES.size(); i++) {
            String roleId = ExtraConfig.RANK_ROLES.get(i);
            long need = ExtraConfig.RANK_HOURS.get(i)[0];
            if (played >= need && !d.ranks.contains(roleId)) {
               Role role = RoleManager.get(roleId);
               if (role == null) {
                  continue;
               }

               d.ranks.add(roleId);
               ExtraStore.markDirty();
               if (!RoleManager.has(id, roleId)) {
                  try {
                     RoleManager.give(id, roleId, 0L, "playtime");
                     RoleManager.saveIfDirty();
                     RoleManager.sync(player);
                  } catch (Throwable t) {
                     EcoServ.LOG.warn("Не удалось выдать звание {} игроку {}", roleId + "", player.getName() + ": " + t);
                     continue;
                  }

                  if (ExtraConfig.ranksAnnounce) {
                     ChatUtil.broadcast("&6" + player.getName() + " &eполучает звание " + role.title() + " &eза " + Fmt.duration(need) + " в игре!");
                  } else {
                     ChatUtil.send(player, "&eНовое звание: " + role.title());
                  }
               }
            }
         }
      }
   }

   /** Следующее звание: {индекс} или -1. */
   public static int next(UUID player) {
      long played = playtime(player);
      int best = -1;
      long bestNeed = Long.MAX_VALUE;

      for (int i = 0; i < ExtraConfig.RANK_ROLES.size(); i++) {
         long need = ExtraConfig.RANK_HOURS.get(i)[0];
         if (need > played && need < bestNeed && !RoleManager.has(player, ExtraConfig.RANK_ROLES.get(i))) {
            best = i;
            bestNeed = need;
         }
      }

      return best;
   }

   @Nullable
   public static String describeNext(UUID player) {
      int i = next(player);
      if (i < 0) {
         return null;
      } else {
         Role r = RoleManager.get(ExtraConfig.RANK_ROLES.get(i));
         long left = ExtraConfig.RANK_HOURS.get(i)[0] - playtime(player);
         return (r == null ? ExtraConfig.RANK_ROLES.get(i) : r.title()) + " &7через &f" + Fmt.duration(Math.max(0L, left));
      }
   }
}
