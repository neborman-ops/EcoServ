package com.ecoserv.minigame;

import com.ecoserv.util.Fmt;
import com.ecoserv.util.ItemCodec;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Задание «Квестовой гонки»: цепочка шагов (квест и все его зависимости по порядку или цепочка крафта).
 * На каждом шаге игрок сдаёт вещи — их забирают, как в квестах FTB с «забрать предметы».
 */
public class RaceTask {

    public String title = "";
    /** quest — из FTB Quests, recipe — цепочка крафта. */
    public String source = "recipe";
    public int difficulty = 1;
    public List<Step> steps = new ArrayList<Step>();
    public Estimate estimate = new Estimate();

    public static class Step {
        public String title = "";
        public List<Need> needs = new ArrayList<Need>();
    }

    public static class Need {
        /** ItemKey (id[@meta][#nbt]). */
        public String key = "";
        /** Образец для иконки. */
        public String sample = "";
        public String name = "";
        public int count = 1;
        /** Задание FTB Quests (код), если вещь проверяет оно (теги, фильтры). Иначе пусто. */
        public String ftb = "";

        public ItemStack icon() {
            ItemStack s = ItemCodec.decode(sample);
            return s.isEmpty() ? ItemStack.EMPTY : s;
        }
    }

    /** Оценка: сколько всего добыть и сколько это займёт. */
    public static class Estimate {
        /** Сырьё: название → количество. */
        public Map<String, Integer> raw = new LinkedHashMap<String, Integer>();
        public int smelts;
        public int crafts;
        /** Примерное время, секунд (добыча + плавка в 2 печах + крафт). */
        public int seconds;
        /** В цепочке есть то, что не посчитать по рецептам (вещи модов без рецепта и т.п.). */
        public boolean partial;

        public String rawSummary(int max) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (Map.Entry<String, Integer> e : raw.entrySet()) {
                if (i++ >= max) {
                    sb.append(", …ещё ").append(raw.size() - max);
                    break;
                }
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(e.getValue()).append("× ").append(e.getKey());
            }
            return sb.length() == 0 ? "—" : sb.toString();
        }

        public int rawTotal() {
            int n = 0;
            for (int v : raw.values()) {
                n += v;
            }
            return n;
        }

        public String timeText() {
            return "~" + Fmt.duration(Math.max(60, seconds) * 1000L);
        }

        /** Время для автоматического таймера: оценка × 1.5, округлено вверх до 5 минут. */
        public int autoMinutes(int maxMinutes) {
            int m = (int) Math.ceil(seconds * 1.5D / 60.0D);
            m = ((m + 4) / 5) * 5;
            return Math.max(5, Math.min(maxMinutes, m));
        }
    }

    public int totalItems() {
        int n = 0;
        for (Step s : steps) {
            for (Need need : s.needs) {
                n += need.count;
            }
        }
        return n;
    }
}
