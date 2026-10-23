package com.ecoserv.quest;

import com.ecoserv.bank.BankManager;
import com.ecoserv.bank.Currency;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.advancements.Advancement;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Виды условий и наград своих квестов (2.0): названия для редактора, проверки, выдача. */
public final class QuestTypes {

    public static final class TaskType {
        public final String id;
        public final String title;
        public final String targetHint;
        /** Живая проверка (смотрим на игрока раз в секунду), а не счётчик событий. */
        public final boolean live;
        /** Можно сдать кнопкой (если «забирать» включено). */
        public final boolean submit;
        public final String icon;

        TaskType(String id, String title, String targetHint, boolean live, boolean submit, String icon) {
            this.id = id;
            this.title = title;
            this.targetHint = targetHint;
            this.live = live;
            this.submit = submit;
            this.icon = icon;
        }
    }

    public static final class RewardType {
        public final String id;
        public final String title;
        public final String targetHint;
        public final String icon;

        RewardType(String id, String title, String targetHint, String icon) {
            this.id = id;
            this.title = title;
            this.targetHint = targetHint;
            this.icon = icon;
        }
    }

    private static final Map<String, TaskType> TASKS = new LinkedHashMap<String, TaskType>();
    private static final Map<String, RewardType> REWARDS = new LinkedHashMap<String, RewardType>();

    static {
        task("item", "Принести предметы", "предмет (кнопка …)", true, true, "minecraft:chest");
        task("kill", "Убить существ", "minecraft:zombie, player или * — любое", false, false, "minecraft:iron_sword");
        task("mine", "Добыть блоки", "блок (minecraft:stone, * — любой)", false, false, "minecraft:iron_pickaxe");
        task("craft", "Скрафтить", "предмет", false, false, "minecraft:crafting_table");
        task("place", "Поставить блоки", "блок", false, false, "minecraft:grass");
        task("visit", "Прийти в место", "мир (overworld, nether, имя подмира); доп.: «x y z радиус»", true, false, "minecraft:compass");
        task("zone", "Войти в зону", "имя зоны-триггера или портала", true, false, "minecraft:map");
        task("activation", "Нажать кнопку", "название кнопки (палка активации)", false, false, "minecraft:stone_button");
        task("money", "Деньги", "— (количество = сумма)", true, true, "minecraft:gold_nugget");
        task("currency", "Валюта", "id валюты (gems…)", true, true, "minecraft:diamond");
        task("role", "Иметь роль", "id роли", true, false, "minecraft:name_tag");
        task("level", "Уровень опыта", "— (количество = уровень)", true, false, "minecraft:experience_bottle");
        task("advancement", "Достижение", "minecraft:story/mine_diamond", true, false, "minecraft:knowledge_book");
        task("talk", "Поговорить с NPC", "id NPC", false, false, "minecraft:emerald");
        task("game", "Победить в мини-игре", "deathrun, pressure, chase… или *", false, false, "minecraft:gold_ingot");
        task("event", "Событие сценария", "имя (шаг сценария «quest event имя»)", false, false, "minecraft:command_block");
        task("check", "Отметить галочкой", "— (игрок сам нажимает)", false, false, "minecraft:lever");

        reward("item", "Предметы", "предмет (кнопка …)", "minecraft:chest");
        reward("money", "Деньги", "— (количество = сумма)", "minecraft:gold_nugget");
        reward("currency", "Валюта", "id валюты", "minecraft:diamond");
        reward("xp", "Опыт", "— (количество = очки опыта)", "minecraft:experience_bottle");
        reward("role", "Роль", "id роли; количество = дней (0 — навсегда)", "minecraft:name_tag");
        reward("command", "Команда", "без /, %player% — ник", "minecraft:command_block");
        reward("script", "Сценарий", "имя сценария", "minecraft:writable_book");
        reward("title", "Надпись на экране", "заголовок|подзаголовок", "minecraft:paper");
    }

    private QuestTypes() {
    }

    private static void task(String id, String title, String hint, boolean live, boolean submit, String icon) {
        TASKS.put(id, new TaskType(id, title, hint, live, submit, icon));
    }

    private static void reward(String id, String title, String hint, String icon) {
        REWARDS.put(id, new RewardType(id, title, hint, icon));
    }

    public static List<TaskType> tasks() {
        return new ArrayList<TaskType>(TASKS.values());
    }

    public static List<RewardType> rewards() {
        return new ArrayList<RewardType>(REWARDS.values());
    }

    @Nullable
    public static TaskType task(String id) {
        return TASKS.get(id);
    }

    @Nullable
    public static RewardType reward(String id) {
        return REWARDS.get(id);
    }

    // --- условия ---------------------------------------------------------------------------------------

    /** Подходит ли событие под условие (target «*» — любое). */
    static boolean matches(QuestBook.Task t, String target) {
        if (t.target.isEmpty() || "*".equals(t.target)) {
            return true;
        }
        String want = t.target.toLowerCase(Locale.ROOT);
        String got = target == null ? "" : target.toLowerCase(Locale.ROOT);
        if (want.equals(got)) {
            return true;
        }
        // без modid: «zombie» == «minecraft:zombie»
        if (want.indexOf(':') < 0 && got.endsWith(":" + want)) {
            return true;
        }
        // предмет с мета-значением: «minecraft:wool@*»
        if (("mine".equals(t.type) || "craft".equals(t.type) || "place".equals(t.type)) && got.contains("@")) {
            ItemKey key = ItemKey.parse(t.target);
            String gotId = got.substring(0, got.indexOf('@'));
            int gotMeta;
            try {
                gotMeta = Integer.parseInt(got.substring(got.indexOf('@') + 1));
            } catch (NumberFormatException e) {
                gotMeta = 0;
            }
            return key.id.equals(gotId) && (key.meta == ItemKey.ANY_META || key.meta == gotMeta);
        }
        return false;
    }

    /** Живое значение условия прямо сейчас. */
    static long liveValue(EntityPlayerMP p, QuestBook.Task t) {
        switch (t.type) {
            case "item":
                return QuestManager.countItems(p, t.target);
            case "money":
                return EconomyManager.get(p.getUniqueID());
            case "currency":
                return BankManager.balance(p.getUniqueID(), t.target);
            case "role":
                return com.ecoserv.role.RoleManager.has(p, t.target) ? t.count : 0;
            case "level":
                return p.experienceLevel;
            case "advancement":
                return hasAdvancement(p, t.target) ? t.count : 0;
            case "visit":
                return atPlace(p, t) ? t.count : 0;
            case "zone":
                return inZone(p, t.target) ? t.count : 0;
            default:
                return 0;
        }
    }

    private static boolean hasAdvancement(EntityPlayerMP p, String id) {
        try {
            Advancement a = p.getServer().getAdvancementManager().getAdvancement(new ResourceLocation(id));
            return a != null && p.getAdvancements().getProgress(a).isDone();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean atPlace(EntityPlayerMP p, QuestBook.Task t) {
        if (!t.target.isEmpty()) {
            Integer dim = com.ecoserv.world.WorldNames.resolve(t.target);
            if (dim == null) {
                com.ecoserv.subworld.Subworld sw = com.ecoserv.subworld.SubworldRegistry.get(t.target);
                if (sw == null || !com.ecoserv.world.WorldNames.matches(p.dimension, t.target)) {
                    return false;
                }
            } else if (dim != p.dimension) {
                return false;
            }
        }
        String[] e = t.extra.trim().isEmpty() ? new String[0] : t.extra.trim().split("\\s+");
        if (e.length >= 3) {
            try {
                double x = Double.parseDouble(e[0]);
                double y = Double.parseDouble(e[1]);
                double z = Double.parseDouble(e[2]);
                double r = e.length > 3 ? Double.parseDouble(e[3]) : 5.0D;
                return p.getDistanceSq(x, y, z) <= r * r;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return true;
    }

    private static boolean inZone(EntityPlayerMP p, String name) {
        int rd = com.ecoserv.region.RegionManager.regionDim(p.dimension);
        for (com.ecoserv.region.Region r : com.ecoserv.region.RegionManager.at(rd, new BlockPos(p))) {
            if (r.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Забрать до need единиц для сдачи; сколько забрали. */
    static long take(EntityPlayerMP p, QuestBook.Task t, long need) {
        switch (t.type) {
            case "item": {
                ItemKey key = ItemKey.parse(t.target);
                long got = 0;
                for (int i = 0; i < p.inventory.getSizeInventory() && got < need; i++) {
                    ItemStack s = p.inventory.getStackInSlot(i);
                    if (!s.isEmpty() && key.matches(s)) {
                        int n = (int) Math.min(s.getCount(), need - got);
                        s.shrink(n);
                        got += n;
                    }
                }
                p.inventoryContainer.detectAndSendChanges();
                return got;
            }
            case "money": {
                long have = EconomyManager.get(p.getUniqueID());
                long n = Math.min(have, need);
                return n > 0 && EconomyManager.take(p.getUniqueID(), n) ? n : 0;
            }
            case "currency": {
                long have = BankManager.balance(p.getUniqueID(), t.target);
                long n = Math.min(have, need);
                return n > 0 && BankManager.take(p.getUniqueID(), t.target, n) ? n : 0;
            }
            default:
                return 0;
        }
    }

    /** Текст условия для книги. */
    public static String describe(QuestBook.Task t) {
        if (!t.text.isEmpty()) {
            return t.text;
        }
        switch (t.type) {
            case "item":
                return (t.consume ? "Сдать " : "Иметь ") + t.count + " × " + itemName(t.target);
            case "kill":
                return "Убить " + ("*".equals(t.target) || t.target.isEmpty() ? "кого угодно" : t.target) + " × " + t.count;
            case "mine":
                return "Добыть " + itemName(t.target) + " × " + t.count;
            case "craft":
                return "Скрафтить " + itemName(t.target) + " × " + t.count;
            case "place":
                return "Поставить " + itemName(t.target) + " × " + t.count;
            case "visit":
                return "Прийти: " + (t.target.isEmpty() ? "" : t.target + " ") + t.extra;
            case "zone":
                return "Войти в зону «" + t.target + "»";
            case "activation":
                return "Нажать «" + t.target + "» × " + t.count;
            case "money":
                return (t.consume ? "Заплатить " : "Накопить ") + Fmt.money(t.count);
            case "currency": {
                Currency c = BankManager.currency(t.target);
                return (t.consume ? "Заплатить " : "Накопить ") + (c == null ? t.count + " " + t.target : UiUtil.plain(c.format(t.count)));
            }
            case "role":
                return "Получить роль " + t.target;
            case "level":
                return "Достичь " + t.count + " уровня";
            case "advancement":
                return "Достижение " + t.target;
            case "talk":
                return "Поговорить с " + com.ecoserv.npc.NpcManager.titleOf(t.target);
            case "game":
                return "Победить в мини-игре " + ("*".equals(t.target) || t.target.isEmpty() ? "" : t.target) + " × " + t.count;
            case "event":
                return "Событие «" + t.target + "»";
            case "check":
                return "Отметить";
            default:
                return t.type + " " + t.target;
        }
    }

    public static String describe(QuestBook.Reward r) {
        if (!r.text.isEmpty()) {
            return r.text;
        }
        switch (r.type) {
            case "item":
                return r.count + " × " + itemName(r.target);
            case "money":
                return Fmt.money(r.count);
            case "currency": {
                Currency c = BankManager.currency(r.target);
                return c == null ? r.count + " " + r.target : UiUtil.plain(c.format(r.count));
            }
            case "xp":
                return r.count + " опыта";
            case "role":
                return "Роль " + r.target + (r.count > 0 ? " на " + r.count + " дн." : "");
            case "command":
                return "Команда";
            case "script":
                return "Сценарий «" + r.target + "»";
            case "title":
                return "Надпись";
            default:
                return r.type;
        }
    }

    static String itemName(String id) {
        if (id == null || id.isEmpty() || "*".equals(id)) {
            return "что угодно";
        }
        ItemKey k = ItemKey.parse(id);
        ItemStack s = k.isValid() ? k.toStack(1) : ItemStack.EMPTY;
        return s.isEmpty() ? id : s.getDisplayName();
    }

    public static ItemStack icon(QuestBook.Task t) {
        if (("item".equals(t.type) || "mine".equals(t.type) || "craft".equals(t.type) || "place".equals(t.type))
                && !t.target.isEmpty() && !"*".equals(t.target)) {
            ItemKey k = ItemKey.parse(t.target);
            if (k.isValid()) {
                return k.toStack(1);
            }
        }
        TaskType tt = TASKS.get(t.type);
        return UiUtil.icon(tt == null ? "minecraft:paper" : tt.icon);
    }

    public static ItemStack icon(QuestBook.Reward r) {
        if ("item".equals(r.type) && !r.target.isEmpty()) {
            ItemKey k = ItemKey.parse(r.target);
            if (k.isValid()) {
                return k.toStack((int) Math.max(1, Math.min(64, r.count)));
            }
        }
        if ("currency".equals(r.type)) {
            Currency c = BankManager.currency(r.target);
            if (c != null) {
                return UiUtil.icon(c.icon);
            }
        }
        RewardType rt = REWARDS.get(r.type);
        return UiUtil.icon(rt == null ? "minecraft:paper" : rt.icon);
    }

    // --- награды ---------------------------------------------------------------------------------------

    static void give(EntityPlayerMP p, QuestBook.Reward r, QuestBook.Quest q) {
        switch (r.type) {
            case "item": {
                ItemKey k = ItemKey.parse(r.target);
                long left = r.count;
                while (left > 0 && k.isValid()) {
                    ItemStack s = k.toStack(1);
                    int n = (int) Math.min(left, s.getMaxStackSize());
                    s.setCount(n);
                    PlayerUtil.giveOrDrop(p, s);
                    left -= n;
                }
                break;
            }
            case "money":
                EconomyManager.give(p.getUniqueID(), r.count);
                break;
            case "currency":
                BankManager.give(p.getUniqueID(), r.target, r.count);
                break;
            case "xp":
                p.addExperience((int) Math.min(Integer.MAX_VALUE, r.count));
                break;
            case "role": {
                long until = r.count > 0 ? System.currentTimeMillis() + r.count * 86400000L : 0L;
                com.ecoserv.role.RoleManager.give(p.getUniqueID(), r.target, until, "квест " + q.id);
                break;
            }
            case "command":
                com.ecoserv.content.Actions.serverCommand(r.target, p, q.id);
                break;
            case "script":
                com.ecoserv.script.ScriptRunner.start(p, r.target, q.id);
                break;
            case "title":
                com.ecoserv.content.Actions.title(p, r.target, q.id, 5, 50, 10);
                break;
            default:
                break;
        }
    }

    // --- проверка ---------------------------------------------------------------------------------------

    @Nullable
    static String validate(QuestBook.Task t) {
        if (!TASKS.containsKey(t.type)) {
            return "неизвестное условие «" + t.type + "»";
        }
        if (("item".equals(t.type) || "craft".equals(t.type)) && !"*".equals(t.target) && !ItemKey.parse(t.target).isValid()) {
            return "нет предмета «" + t.target + "»";
        }
        if ("currency".equals(t.type) && BankManager.currency(t.target) == null) {
            return "нет валюты «" + t.target + "»";
        }
        if ("role".equals(t.type) && com.ecoserv.role.RoleManager.get(t.target) == null) {
            return "нет роли «" + t.target + "»";
        }
        if ("zone".equals(t.type) && com.ecoserv.region.RegionManager.get(t.target) == null) {
            return "нет зоны «" + t.target + "»";
        }
        if ("talk".equals(t.type) && com.ecoserv.npc.NpcManager.get(t.target) == null) {
            return "нет NPC «" + t.target + "»";
        }
        return null;
    }

    @Nullable
    static String validate(QuestBook.Reward r) {
        if (!REWARDS.containsKey(r.type)) {
            return "неизвестная награда «" + r.type + "»";
        }
        if ("item".equals(r.type) && !ItemKey.parse(r.target).isValid()) {
            return "нет предмета «" + r.target + "»";
        }
        if ("currency".equals(r.type) && BankManager.currency(r.target) == null) {
            return "нет валюты «" + r.target + "»";
        }
        if ("role".equals(r.type) && com.ecoserv.role.RoleManager.get(r.target) == null) {
            return "нет роли «" + r.target + "»";
        }
        if ("script".equals(r.type) && com.ecoserv.script.ScriptManager.get(r.target) == null) {
            return "нет сценария «" + r.target + "»";
        }
        return null;
    }
}
