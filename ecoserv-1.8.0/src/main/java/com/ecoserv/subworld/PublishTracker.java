package com.ecoserv.subworld;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkDataEvent.Save;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Помнит, какие файлы регионов шаблона записывались после последней публикации.
 *
 * Почему «Опубликовать» приходилось жать дважды: публикация ищет изменённые файлы по
 * размеру и времени изменения. Майнкрафт держит файлы регионов открытыми (RegionFileCache),
 * и на Windows время изменения открытого файла обновляется с задержкой — первая публикация
 * сохраняла мир, но «не видела» изменений, вторая видела. Теперь файлы, в которые мир
 * сохранял чанки после прошлой публикации, публикуются всегда — независимо от того,
 * что ОС сообщает о времени изменения. (Закрывать открытые файлы регионов нельзя:
 * в это время их может читать поток асинхронной загрузки чанков Forge.)
 */
public class PublishTracker {
   private static final Map<String, Set<String>> TOUCHED = new ConcurrentHashMap<>();

   @SubscribeEvent
   public void onChunkSave(Save event) {
      try {
         World w = event.getWorld();
         if (w != null && !w.isRemote) {
            SubworldDim d = SubworldRegistry.dim(w.provider.getDimension());
            if (d != null && d.isTemplate()) {
               Chunk c = event.getChunk();
               String name = "r." + (c.x >> 5) + "." + (c.z >> 5) + ".mca";
               TOUCHED.computeIfAbsent(d.subworld, k -> ConcurrentHashMap.newKeySet()).add(name);
            }
         }
      } catch (Throwable ignored) {
      }
   }

   static Set<String> touched(String subworld) {
      Set<String> s = TOUCHED.get(subworld);
      return s == null ? new HashSet<>() : new HashSet<>(s);
   }

   static void published(String subworld, Set<String> files) {
      Set<String> s = TOUCHED.get(subworld);
      if (s != null) {
         s.removeAll(files);
      }
   }

   static void forget(String subworld) {
      TOUCHED.remove(subworld);
   }
}
