package com.ecoserv.integration.quests;

import com.ecoserv.EcoServ;
import com.feed_the_beast.ftbquests.client.ClientQuestFile;
import com.feed_the_beast.ftbquests.net.edit.MessageEditObject;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.reward.Reward;
import com.feed_the_beast.ftbquests.quest.task.Task;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Клиентская часть скрытых заданий: следит, открылись ли секреты, и прячет награды
 * квестов со скрытыми заданиями (режим «Награды»).
 *
 * Как прячем: у копии наград на клиенте поле autoclaim временно ставится в INVISIBLE —
 * так FTB Quests сам не рисует их в окне квеста. Сервер ничего не знает о подмене:
 * выдача наград работает как обычно. В режиме редактирования подмена снимается, чтобы
 * редактор видел и сохранял настоящие значения.
 */
@SideOnly(Side.CLIENT)
public class HiddenClient {
   private final Map<Integer, Boolean> known = new HashMap<>();
   private int tick;
   private boolean lastEdit;
   private static final Map<Reward, Object> MASKED = new IdentityHashMap<>();
   private static Field autoclaimField;
   private static Object invisible;
   private static boolean reflectionFailed;

   public static boolean complete(HiddenTask task) {
      return ClientQuestFile.existsWithTeam() && ClientQuestFile.INSTANCE.self != null && task.isComplete(ClientQuestFile.INSTANCE.self);
   }

   @SubscribeEvent
   public void onTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         this.tick++;
         if (!ClientQuestFile.exists()) {
            MASKED.clear();
            return;
         }

         boolean edit = ClientQuestFile.INSTANCE.canEdit();
         if (edit != this.lastEdit) {
            this.lastEdit = edit;
            if (edit && unmaskAll()) {
               ClientQuestFile.INSTANCE.refreshGui();
            }
         }

         if (this.tick % 20 == 0 && ClientQuestFile.existsWithTeam()) {
            boolean changed = false;

            for (HiddenTask task : ClientQuestFile.INSTANCE.collect(HiddenTask.class)) {
               boolean now = complete(task);
               Boolean before = this.known.put(task.id, now);
               if (before != null && before != now) {
                  task.clearCachedData();
                  task.quest.clearCachedData();
                  changed = true;
               } else if (before == null) {
                  task.clearCachedData();
                  task.quest.clearCachedData();
               }
            }

            if (!edit && this.updateMasks()) {
               changed = true;
            }

            if (changed) {
               ClientQuestFile.INSTANCE.refreshGui();
            }
         }
      }
   }

   /** Надо ли сейчас прятать награды этого квеста. */
   static boolean shouldHide(Quest quest) {
      String mode = HiddenTask.questMode(quest);
      if ("show".equals(mode) || ClientQuestFile.INSTANCE.self == null) {
         return false;
      } else if ("until_complete".equals(mode)) {
         return !quest.isComplete(ClientQuestFile.INSTANCE.self);
      } else {
         for (Task t : quest.tasks) {
            if (t instanceof HiddenTask && !complete((HiddenTask)t)) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean updateMasks() {
      if (!reflection()) {
         return false;
      } else {
         boolean changed = false;
         Set<Reward> keep = new HashSet<>();
         Set<Quest> quests = new HashSet<>();

         for (HiddenTask task : ClientQuestFile.INSTANCE.collect(HiddenTask.class)) {
            quests.add(task.quest);
         }

         for (Quest q : quests) {
            if (shouldHide(q)) {
               for (Reward r : q.rewards) {
                  keep.add(r);
                  if (!MASKED.containsKey(r)) {
                     try {
                        Object old = autoclaimField.get(r);
                        if (old != invisible) {
                           MASKED.put(r, old);
                           autoclaimField.set(r, invisible);
                           r.clearCachedData();
                           changed = true;
                        }
                     } catch (Throwable t) {
                        reflectionFailed = true;
                        return changed;
                     }
                  }
               }
            }
         }

         for (Entry<Reward, Object> e : new HashMap<>(MASKED).entrySet()) {
            if (!keep.contains(e.getKey())) {
               restore(e.getKey(), e.getValue());
               changed = true;
            }
         }

         return changed;
      }
   }

   private static void restore(Reward r, Object old) {
      MASKED.remove(r);

      try {
         if (autoclaimField.get(r) == invisible) {
            autoclaimField.set(r, old);
         }

         r.clearCachedData();
      } catch (Throwable ignored) {
      }
   }

   private static boolean unmaskAll() {
      if (MASKED.isEmpty()) {
         return false;
      } else {
         for (Entry<Reward, Object> e : new HashMap<>(MASKED).entrySet()) {
            restore(e.getKey(), e.getValue());
         }

         MASKED.clear();
         return true;
      }
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   private static boolean reflection() {
      if (reflectionFailed) {
         return false;
      } else if (autoclaimField != null) {
         return true;
      } else {
         try {
            autoclaimField = Reward.class.getField("autoclaim");
            Class<? extends Enum> type = (Class<? extends Enum>)autoclaimField.getType();
            invisible = Enum.valueOf(type, "INVISIBLE");
            return true;
         } catch (Throwable t) {
            reflectionFailed = true;
            EcoServ.LOG.warn("Скрытие наград недоступно в этой версии FTB Quests: {}", t.toString());
            return false;
         }
      }
   }

   /** Выставить всем скрытым заданиям квеста тот же режим скрытия наград и сохранить их на сервере. */
   static void syncSiblings(HiddenTask edited) {
      if (ClientQuestFile.exists() && ClientQuestFile.INSTANCE.canEdit()) {
         for (Task t : edited.quest.tasks) {
            if (t != edited && t instanceof HiddenTask) {
               HiddenTask h = (HiddenTask)t;
               if (!h.rewardMode.equals(edited.rewardMode)) {
                  h.rewardMode = edited.rewardMode;
                  new MessageEditObject(h).sendToServer();
               }
            }
         }
      }
   }
}
