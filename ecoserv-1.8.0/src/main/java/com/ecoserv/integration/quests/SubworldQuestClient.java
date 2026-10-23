package com.ecoserv.integration.quests;

import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldDims;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldView;
import com.feed_the_beast.ftblib.lib.util.misc.NameMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;

/**
 * Список подмиров для выпадающего списка в редакторе наград FTB Quests.
 *
 * Причина краша «Rendering screen / NPE в lambda$subworlds$0»: FTBLib строит NameMap так,
 * что значение по умолчанию ищется через get(getName(def)). Для пустой строки get("")
 * возвращает поле defaultValue, которое в этот момент ещё null — и у копии ConfigEnum
 * в окне редактора «по умолчанию» становится null. При наведении на строку FTBLib
 * вызывает getDisplayName(null, null), а старый код делал id.isEmpty() → NPE.
 *
 * Исправление: «не выбран» хранится как непустой маркер NONE, имена значений не
 * переводятся в нижний регистр (getName возвращает id как есть), а отрисовка
 * спокойно переживает null.
 */
final class SubworldQuestClient {
   /** Непустой маркер «подмир не выбран»; в NBT по-прежнему пишется пустая строка. */
   static final String NONE = "*none*";

   private SubworldQuestClient() {
   }

   private static Map<String, String> known(boolean ownQuestsOnly) {
      Map<String, String> out = new LinkedHashMap<>();

      try {
         for (SubworldView v : SubworldDims.CLIENT_VIEWS.values()) {
            if (v != null && v.subworld != null && !v.subworld.isEmpty() && (!ownQuestsOnly || v.ownQuests)) {
               out.put(v.subworld, v.name);
            }
         }
      } catch (Throwable ignored) {
      }

      if (out.isEmpty() && FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
         for (Subworld sw : SubworldRegistry.all()) {
            if (sw != null && sw.id != null && !sw.id.isEmpty() && (!ownQuestsOnly || sw.ownQuests)) {
               out.put(sw.id, sw.name);
            }
         }
      }

      return out;
   }

   /** Значение из награды → значение для списка. */
   static String toUi(@Nullable String id) {
      return id == null || id.isEmpty() ? NONE : id;
   }

   /** Значение из списка → значение для награды. */
   static String fromUi(@Nullable String v) {
      return v == null || v.isEmpty() || NONE.equals(v) ? "" : v;
   }

   static NameMap<String> subworlds(String current, final boolean ownQuestsOnly) {
      final Map<String, String> names = known(ownQuestsOnly);
      List<String> values = new ArrayList<>();
      values.add(NONE);

      for (String id : names.keySet()) {
         if (!values.contains(id)) {
            values.add(id);
         }
      }

      String cur = toUi(current);
      if (!values.contains(cur)) {
         values.add(cur);
      }

      return NameMap.create(NONE, new NameMap.ObjectProperties<String>() {
         @Override
         public String getName(String value) {
            return value == null || value.isEmpty() ? NONE : value;
         }

         @Override
         public ITextComponent getDisplayName(@Nullable ICommandSender sender, String value) {
            return new TextComponentString(SubworldQuestClient.label(value, names, ownQuestsOnly));
         }
      }, values.toArray(new String[0]));
   }

   static String label(@Nullable String id, Map<String, String> names, boolean ownQuestsOnly) {
      if (id == null || id.isEmpty() || NONE.equals(id)) {
         return ownQuestsOnly ? "авто: подмир главы квеста" : "не выбран";
      } else {
         String name = names.get(id);
         return (name == null ? id : strip(name)) + " (" + id + ")";
      }
   }

   static String title(String id) {
      if (id != null && !id.isEmpty() && !NONE.equals(id)) {
         try {
            for (SubworldView v : SubworldDims.CLIENT_VIEWS.values()) {
               if (v != null && id.equals(v.subworld)) {
                  return strip(v.name);
               }
            }
         } catch (Throwable ignored) {
         }

         Subworld sw = SubworldRegistry.get(id);
         return sw == null ? id : strip(sw.name);
      } else {
         return "?";
      }
   }

   private static String strip(String s) {
      return s == null ? "" : s.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
   }
}
