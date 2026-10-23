package com.ecoserv.quest;

/** Пример книги квестов: глава, подглава и развилка из двух веток (одна закрывает другую). */
final class QuestExamples {

    private QuestExamples() {
    }

    static QuestBook book() {
        QuestBook b = new QuestBook();

        QuestBook.Chapter start = chapter("start", "Начало", "minecraft:oak_sapling", "", 0);
        start.description = "Первые шаги на сервере. Пример главы — правится в /quests → «Редактор».";
        QuestBook.Quest wood = quest("wood", "Дерево", "minecraft:log");
        wood.description = "Добудь немного дерева.";
        wood.tasks.add(task("mine", "minecraft:log@*", 8, false));
        wood.rewards.add(reward("money", "", 50));
        start.quests.add(wood);
        QuestBook.Quest tools = quest("tools", "Инструменты", "minecraft:stone_pickaxe");
        tools.dependencies.add("wood");
        tools.tasks.add(task("craft", "minecraft:stone_pickaxe", 1, false));
        tools.rewards.add(reward("item", "minecraft:bread", 8));
        start.quests.add(tools);
        b.chapters.add(start);

        QuestBook.Chapter mine = chapter("start_mine", "Шахта", "minecraft:iron_ore", "start", 10);
        mine.description = "Подглава «Начала»: открывается, когда пройдено «Начало».";
        mine.needChapters.add("start");
        QuestBook.Quest iron = quest("iron", "Железо", "minecraft:iron_ingot");
        iron.tasks.add(task("item", "minecraft:iron_ingot", 16, true));
        iron.rewards.add(reward("currency", "gems", 1));
        mine.quests.add(iron);
        b.chapters.add(mine);

        QuestBook.Chapter light = chapter("path_light", "Путь света", "minecraft:glowstone", "", 20);
        light.description = "Развилка: начнёшь эту ветку — «Путь тени» станет недоступен.";
        light.needChapters.add("start");
        light.exclusiveGroup = "path";
        QuestBook.Quest l1 = quest("light_1", "Свет во тьме", "minecraft:torch");
        l1.tasks.add(task("place", "minecraft:torch", 32, false));
        l1.rewards.add(reward("xp", "", 100));
        light.quests.add(l1);
        b.chapters.add(light);

        QuestBook.Chapter dark = chapter("path_dark", "Путь тени", "minecraft:obsidian", "", 30);
        dark.description = "Развилка: начнёшь эту ветку — «Путь света» станет недоступен.";
        dark.needChapters.add("start");
        dark.exclusiveGroup = "path";
        QuestBook.Quest d1 = quest("dark_1", "Охотник", "minecraft:bone");
        d1.tasks.add(task("kill", "minecraft:skeleton", 10, false));
        d1.rewards.add(reward("xp", "", 100));
        dark.quests.add(d1);
        b.chapters.add(dark);
        return b;
    }

    private static QuestBook.Chapter chapter(String id, String title, String icon, String parent, int order) {
        QuestBook.Chapter c = new QuestBook.Chapter();
        c.id = id;
        c.title = title;
        c.icon = icon;
        c.parent = parent;
        c.order = order;
        return c;
    }

    private static QuestBook.Quest quest(String id, String title, String icon) {
        QuestBook.Quest q = new QuestBook.Quest();
        q.id = id;
        q.title = title;
        q.icon = icon;
        return q;
    }

    private static QuestBook.Task task(String type, String target, long count, boolean consume) {
        QuestBook.Task t = new QuestBook.Task();
        t.type = type;
        t.target = target;
        t.count = count;
        t.consume = consume;
        return t;
    }

    private static QuestBook.Reward reward(String type, String target, long count) {
        QuestBook.Reward r = new QuestBook.Reward();
        r.type = type;
        r.target = target;
        r.count = count;
        return r;
    }
}
