package com.ecoserv.integration.quests;

import com.ecoserv.minigame.RaceTask;
import com.ecoserv.minigame.RaceTasks;
import com.ecoserv.util.ItemCodec;
import com.ecoserv.util.ItemKey;
import com.feed_the_beast.ftbquests.quest.Chapter;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.QuestObject;
import com.feed_the_beast.ftbquests.quest.ServerQuestFile;
import com.feed_the_beast.ftbquests.quest.task.ItemTask;
import com.feed_the_beast.ftbquests.quest.task.Task;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Задания «Квестовой гонки» из FTB Quests: квест и все его зависимости (по порядку — от первых к последнему).
 * Берутся только цепочки, где у каждого квеста есть задания «предметы» и нет других заданий (их на арене
 * не проверить). Проверку вещи делает сам ItemTask — с тегами, фильтрами и NBT, как в книге квестов.
 */
public class RaceQuestSource implements RaceTasks.QuestSource {

    /** Код задания → задание (обновляется при каждом построении списка). */
    private final Map<String, ItemTask> tasks = new HashMap<String, ItemTask>();

    @Override
    public synchronized List<RaceTask> chains() {
        List<RaceTask> out = new ArrayList<RaceTask>();
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) {
            return out;
        }
        tasks.clear();
        for (Chapter ch : file.chapters) {
            for (Quest q : ch.quests) {
                List<Quest> chain = chain(q);
                if (chain == null) {
                    continue;
                }
                RaceTask t = new RaceTask();
                t.source = "quest";
                t.title = plain(q.getUnformattedTitle());
                for (Quest step : chain) {
                    RaceTask.Step s = new RaceTask.Step();
                    s.title = plain(step.getUnformattedTitle());
                    for (Task task : step.tasks) {
                        ItemTask it = (ItemTask) task;
                        ItemStack sample = it.items.isEmpty() ? ItemStack.EMPTY : it.items.get(0).copy();
                        if (sample.isEmpty()) {
                            continue;
                        }
                        sample.setCount(1);
                        RaceTask.Need n = new RaceTask.Need();
                        n.ftb = it.getCodeString();
                        n.key = ItemKey.of(sample, false, sample.getHasSubtypes()).serialize();
                        n.sample = ItemCodec.encode(sample);
                        n.name = it.items.size() > 1 ? plain(it.getUnformattedTitle()) : sample.getDisplayName();
                        n.count = (int) Math.max(1L, Math.min(64L * 36L, it.count));
                        s.needs.add(n);
                        tasks.put(n.ftb, it);
                    }
                    if (!s.needs.isEmpty()) {
                        t.steps.add(s);
                    }
                }
                if (!t.steps.isEmpty()) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    /** Квест и его зависимости по порядку; null — в цепочке есть то, что на арене не проверить. */
    private static List<Quest> chain(Quest target) {
        Set<Quest> seen = new LinkedHashSet<Quest>();
        if (!collect(target, seen, 0)) {
            return null;
        }
        return new ArrayList<Quest>(seen);
    }

    private static boolean collect(Quest q, Set<Quest> seen, int depth) {
        if (seen.contains(q)) {
            return true;
        }
        if (depth > 20 || q.tasks.isEmpty() || q.canRepeat) {
            return false;
        }
        for (Task t : q.tasks) {
            if (!(t instanceof ItemTask)) {
                return false;
            }
        }
        for (QuestObject dep : q.dependencies) {
            if (dep instanceof Quest) {
                if (!collect((Quest) dep, seen, depth + 1)) {
                    return false;
                }
            } else if (dep != null) {
                // зависимость от главы/награды и т.п. — не цепочка квестов
                return false;
            }
        }
        seen.add(q); // после зависимостей: порядок «от первых к последнему»
        return true;
    }

    private static String plain(String s) {
        String p = TextFormatting.getTextWithoutFormattingCodes(s);
        return p == null ? "" : p;
    }

    @Override
    public synchronized boolean test(String ftbTask, ItemStack stack) {
        ItemTask t = tasks.get(ftbTask);
        if (t == null) {
            throw new IllegalStateException("нет задания");
        }
        return t.test(stack);
    }
}
