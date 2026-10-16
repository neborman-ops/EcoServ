package com.ecoserv.extra;

import com.ecoserv.EcoServ;
import com.ecoserv.region.PortalGuard;
import com.ecoserv.util.PlayerUtil;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.world.WorldEvent.Save;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;

/** События новых механик: защита после телепорта, вход/выход, сохранение, периодические задачи. */
public class ExtraEvents {
   private static final Map<UUID, Long> PROTECT = new HashMap<>();
   private static int tick;

   /** Неуязвимость на ExtraConfig.arrivalProtectSeconds секунд (после телепорта, входа, возрождения). */
   public static void protectArrival(EntityPlayerMP player, boolean always) {
      int sec = ExtraConfig.arrivalProtectSeconds;
      if (player != null && sec > 0) {
         long until = System.currentTimeMillis() + sec * 1000L;
         Long old = PROTECT.get(player.getUniqueID());
         if (always || old == null || old < until) {
            PROTECT.put(player.getUniqueID(), until);
         }
      }
   }

   public static void unprotect(EntityPlayerMP player) {
      if (player != null) {
         PROTECT.remove(player.getUniqueID());
      }
   }

   public static boolean isProtected(EntityPlayerMP player) {
      Long until = PROTECT.get(player.getUniqueID());
      return until != null && until > System.currentTimeMillis();
   }

   /** Вызывается из ServerServices каждый тик сервера. */
   public static void serverTick() {
      tick++;
      if (tick % 20 == 0) {
         try {
            ExtraConfig.tick();
            Iterator<Entry<UUID, Long>> it = PROTECT.entrySet().iterator();
            long now = System.currentTimeMillis();

            while (it.hasNext()) {
               if (it.next().getValue() < now) {
                  it.remove();
               }
            }

            for (EntityPlayerMP p : PlayerUtil.allOnline()) {
               Bank.secondOnline(p);
               Salary.secondOnline(p);
            }

            if (tick % 1200 == 0) {
               for (EntityPlayerMP p : PlayerUtil.allOnline()) {
                  Ranks.check(p);
               }
            }

            if (tick % 1200 == 0) {
               ExtraStore.saveIfDirty();
            }
         } catch (Throwable t) {
            EcoServ.LOG.error("Ошибка в новых механиках EcoServ", t);
         }
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public void onAttack(LivingAttackEvent event) {
      if (event.getEntityLiving() instanceof EntityPlayerMP) {
         EntityPlayerMP player = (EntityPlayerMP)event.getEntityLiving();
         DamageSource src = event.getSource();
         if (src != null && !src.canHarmInCreative() && isProtected(player)) {
            event.setCanceled(true);
         }
      }
   }

   @SubscribeEvent
   public void onLogin(PlayerLoggedInEvent event) {
      if (event.player instanceof EntityPlayerMP) {
         EntityPlayerMP p = (EntityPlayerMP)event.player;
         PortalGuard.start(p);
         protectArrival(p, true);
         Ranks.check(p);
      }
   }

   @SubscribeEvent
   public void onRespawn(PlayerRespawnEvent event) {
      if (event.player instanceof EntityPlayerMP) {
         EntityPlayerMP p = (EntityPlayerMP)event.player;
         PortalGuard.start(p);
         protectArrival(p, true);
      }
   }

   @SubscribeEvent
   public void onLogout(PlayerLoggedOutEvent event) {
      PROTECT.remove(event.player.getUniqueID());
   }

   @SubscribeEvent
   public void onWorldSave(Save event) {
      ExtraStore.saveIfDirty();
   }
}
