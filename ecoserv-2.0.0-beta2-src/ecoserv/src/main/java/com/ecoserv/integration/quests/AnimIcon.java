package com.ecoserv.integration.quests;

import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Анимированная иконка для FTB Quests: PNG-лента из кадров 16×16 одна под другой
 * (assets/ecoserv/textures/icons/*.png). Кадр выбирается по времени, так что иконка
 * «живёт» и в списке квестов, и в окне квеста, и на экранах заданий.
 */
public class AnimIcon extends Icon {
   private final String id;
   private final Icon[] frames;
   private final int frameMs;

   private AnimIcon(String id, Icon[] frames, int frameMs) {
      this.id = id;
      this.frames = frames;
      this.frameMs = Math.max(30, frameMs);
   }

   /** name — имя файла без .png в textures/icons, frames — сколько кадров в ленте. */
   public static Icon of(String name, int frames, int frameMs) {
      String path = "ecoserv:textures/icons/" + name + ".png";

      try {
         Icon base = Icon.getIcon(path);
         Icon[] list = new Icon[Math.max(1, frames)];

         for (int i = 0; i < list.length; i++) {
            list[i] = base.withUV(0.0, (double)i / (double)list.length, 1.0, (double)(i + 1) / (double)list.length);
         }

         return new AnimIcon(path, list, frameMs);
      } catch (Throwable t) {
         return Icon.getIcon(path);
      }
   }

   public void draw(int x, int y, int w, int h) {
      int i = (int)(System.currentTimeMillis() / (long)this.frameMs % (long)this.frames.length);
      this.frames[i].draw(x, y, w, h);
   }

   public JsonElement getJson() {
      return new JsonPrimitive(this.id);
   }
}
