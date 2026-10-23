package com.ecoserv.minigame;

import com.ecoserv.EcoServ;
import com.ecoserv.util.ItemKey;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Откуда берутся задания гонки. С FTB Quests — случайный квест: чем выше сложность, тем длиннее цепочка
 * его зависимостей (игроки проходят её всю по порядку). Без FTB Quests (или если подходящих квестов нет) —
 * случайная цепочка крафта нужной глубины.
 */
public final class RaceTasks {

    /** То, что даёт интеграция с FTB Quests. */
    public interface QuestSource {
        /** Все цепочки: квест + его зависимости по порядку, только с заданиями «предметы». */
        List<RaceTask> chains();

        /** Подходит ли вещь под задание FTB (теги, фильтры, NBT — как проверяет сам FTB Quests). */
        boolean test(String ftbTask, ItemStack stack);
    }

    @Nullable
    private static QuestSource quests;

    private RaceTasks() {
    }

    public static void setQuestSource(@Nullable QuestSource source) {
        quests = source;
    }

    public static boolean questsAvailable() {
        return quests != null;
    }

    /** Подходит ли сложности длина цепочки квестов. */
    static boolean fits(int difficulty, int chainLength) {
        switch (Math.max(1, Math.min(5, difficulty))) {
            case 1:
                return chainLength == 1;
            case 2:
                return chainLength == 2;
            case 3:
                return chainLength >= 3 && chainLength <= 4;
            case 4:
                return chainLength >= 5 && chainLength <= 6;
            default:
                return chainLength >= 7;
        }
    }

    /**
     * Новое задание. preferQuests — брать из FTB Quests, если есть подходящие.
     */
    @Nullable
    public static RaceTask generate(int difficulty, boolean preferQuests, Random rnd) {
        if (preferQuests && quests != null) {
            try {
                List<RaceTask> fit = new ArrayList<RaceTask>();
                List<RaceTask> all = quests.chains();
                for (RaceTask t : all) {
                    if (fits(difficulty, t.steps.size())) {
                        fit.add(t);
                    }
                }
                if (fit.isEmpty()) {
                    // ближайшая по длине цепочка
                    int target = new int[]{1, 2, 3, 5, 7}[Math.max(1, Math.min(5, difficulty)) - 1];
                    int bestDiff = Integer.MAX_VALUE;
                    for (RaceTask t : all) {
                        int d = Math.abs(t.steps.size() - target);
                        if (d < bestDiff) {
                            bestDiff = d;
                            fit.clear();
                        }
                        if (d == bestDiff) {
                            fit.add(t);
                        }
                    }
                }
                if (!fit.isEmpty()) {
                    RaceTask t = fit.get(rnd.nextInt(fit.size()));
                    t.difficulty = difficulty;
                    t.estimate = RecipeBook.estimate(t.steps);
                    return t;
                }
            } catch (Throwable e) {
                EcoServ.LOG.warn("Квестовая гонка: не удалось взять задание из FTB Quests: {}", e.toString());
            }
        }
        RaceTask t = RecipeBook.randomChain(difficulty, rnd, true);
        if (t == null) {
            t = RecipeBook.randomChain(difficulty, rnd, false);
        }
        return t;
    }

    /** Подходит ли вещь под требование шага. */
    public static boolean matches(RaceTask.Need need, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!need.ftb.isEmpty() && quests != null) {
            try {
                return quests.test(need.ftb, stack);
            } catch (Throwable ignored) {
                // FTB Quests перезагрузили — проверим по ключу
            }
        }
        return !need.key.isEmpty() && ItemKey.parse(need.key).matches(stack);
    }
}
