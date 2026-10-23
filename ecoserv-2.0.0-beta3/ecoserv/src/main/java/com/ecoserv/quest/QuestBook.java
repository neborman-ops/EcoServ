package com.ecoserv.quest;

import java.util.ArrayList;
import java.util.List;

/**
 * Свои квесты EcoServ (2.0) — без FTB Quests. config/ecoserv/quests.json.
 * Глава → подглавы → квесты → условия (проверки) и награды. Глава может открываться по условиям и
 * становиться недоступной (срок, выбор ветки, закрытие после другой главы).
 */
public class QuestBook {

    public List<Chapter> chapters = new ArrayList<Chapter>();
    /** Прогресс общий для команды (WorldCrews/FTB-команды), иначе у каждого свой. */
    public boolean teamProgress;

    public static class Chapter {
        public String id = "";
        public String title = "";
        public String description = "";
        public String icon = "minecraft:book";
        /** Подглава: id родительской главы (пусто — глава верхнего уровня). */
        public String parent = "";
        public int order;
        public boolean hidden;

        // --- когда открывается ---
        public List<String> needChapters = new ArrayList<String>();
        public List<String> needQuests = new ArrayList<String>();
        public String needRole = "";
        public String needAge = "";
        /** «2026-12-01» — раньше закрыта. */
        public String openFrom = "";

        // --- когда становится недоступной ---
        /** «2026-12-31» — после этой даты закрыта. */
        public String openUntil = "";
        /** Закрывается, когда игрок прошёл любую из этих глав (развилка сюжета). */
        public List<String> closeAfterChapters = new ArrayList<String>();
        /** Группа выбора: начал одну главу группы — остальные закрываются. */
        public String exclusiveGroup = "";
        /** Закрыта вручную админом. */
        public boolean closed;
        /** Свой текст, почему недоступна. */
        public String closedText = "";

        public List<Quest> quests = new ArrayList<Quest>();

        public void normalize() {
            if (id == null) {
                id = "";
            }
            if (title == null || title.isEmpty()) {
                title = id;
            }
            if (description == null) {
                description = "";
            }
            if (icon == null || icon.isEmpty()) {
                icon = "minecraft:book";
            }
            if (parent == null) {
                parent = "";
            }
            needChapters = list(needChapters);
            needQuests = list(needQuests);
            closeAfterChapters = list(closeAfterChapters);
            needRole = str(needRole);
            needAge = str(needAge);
            openFrom = str(openFrom);
            openUntil = str(openUntil);
            exclusiveGroup = str(exclusiveGroup);
            closedText = str(closedText);
            if (quests == null) {
                quests = new ArrayList<Quest>();
            }
            for (Quest q : quests) {
                q.normalize();
            }
        }
    }

    public static class Quest {
        public String id = "";
        public String title = "";
        public String description = "";
        public String icon = "minecraft:paper";
        public List<String> dependencies = new ArrayList<String>();
        public List<Task> tasks = new ArrayList<Task>();
        public List<Reward> rewards = new ArrayList<Reward>();
        /** Достаточно выполнить любое одно условие. */
        public boolean anyTask;
        public boolean optional;
        public boolean hidden;
        /** Награды выдаются сами, без кнопки. */
        public boolean autoClaim;
        /** Можно проходить снова через N минут после получения награды (0 — один раз). */
        public int repeatMinutes;

        public void normalize() {
            if (id == null) {
                id = "";
            }
            if (title == null || title.isEmpty()) {
                title = id;
            }
            if (description == null) {
                description = "";
            }
            if (icon == null || icon.isEmpty()) {
                icon = "minecraft:paper";
            }
            dependencies = list(dependencies);
            if (tasks == null) {
                tasks = new ArrayList<Task>();
            }
            if (rewards == null) {
                rewards = new ArrayList<Reward>();
            }
            for (Task t : tasks) {
                t.normalize();
            }
            for (Reward r : rewards) {
                r.normalize();
            }
        }
    }

    /** Условие (проверка). type — см. QuestTypes. */
    public static class Task {
        public String type = "item";
        public String target = "";
        public long count = 1;
        /** Забрать предметы/деньги при сдаче. */
        public boolean consume = true;
        /** Доп. параметр: координаты «x y z радиус» для visit и т.п. */
        public String extra = "";
        /** Свой текст условия (пусто — автоматический). */
        public String text = "";

        public void normalize() {
            type = str(type).isEmpty() ? "item" : type;
            target = str(target);
            extra = str(extra);
            text = str(text);
            count = Math.max(1, count);
        }
    }

    public static class Reward {
        public String type = "item";
        public String target = "";
        public long count = 1;
        public String text = "";

        public void normalize() {
            type = str(type).isEmpty() ? "item" : type;
            target = str(target);
            text = str(text);
            count = Math.max(0, count);
        }
    }

    static List<String> list(List<String> l) {
        return l == null ? new ArrayList<String>() : l;
    }

    static String str(String s) {
        return s == null ? "" : s;
    }
}
