package com.ecoserv.quest;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.PlayerUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Свои квесты (2.0): книга, прогресс игроков, проверки условий, награды. */
public final class QuestManager {

    public static final Pattern ID = Pattern.compile("^[a-z0-9_\\-]{1,40}$");
    private static final Type PROGRESS = new TypeToken<LinkedHashMap<String, Progress>>() {
    }.getType();

    public enum State {
        HIDDEN, LOCKED, ACTIVE, DONE, CLAIMED, CLOSED
    }

    /** Прогресс одного игрока (или команды). */
    public static class Progress {
        /** квест -> счётчики по условиям. */
        public Map<String, long[]> counts = new HashMap<String, long[]>();
        /** квест -> когда выполнен. */
        public Map<String, Long> done = new HashMap<String, Long>();
        /** квест -> когда получена награда. */
        public Map<String, Long> claimed = new HashMap<String, Long>();
        /** Главы, в которых игрок начал проходить (для развилок). */
        public Set<String> started = new HashSet<String>();
        /** Ручные отметки (условие «отметить»). */
        public Set<String> checks = new HashSet<String>();

        void normalize() {
            if (counts == null) {
                counts = new HashMap<String, long[]>();
            }
            if (done == null) {
                done = new HashMap<String, Long>();
            }
            if (claimed == null) {
                claimed = new HashMap<String, Long>();
            }
            if (started == null) {
                started = new HashSet<String>();
            }
            if (checks == null) {
                checks = new HashSet<String>();
            }
        }
    }

    private static QuestBook book = new QuestBook();
    private static Map<String, Progress> progress = new LinkedHashMap<String, Progress>();
    private static final Map<String, QuestBook.Quest> QUESTS = new HashMap<String, QuestBook.Quest>();
    private static final Map<String, QuestBook.Chapter> QUEST_CHAPTER = new HashMap<String, QuestBook.Chapter>();
    private static final Map<String, QuestBook.Chapter> CHAPTERS = new LinkedHashMap<String, QuestBook.Chapter>();
    private static boolean dirty;
    private static int tick;

    private QuestManager() {
    }

    // --- загрузка -------------------------------------------------------------------------------------

    static File bookFile() {
        return new File(EcoConfig.getConfigDir(), "quests.json");
    }

    public static void load() {
        File f = bookFile();
        QuestBook b = f.exists() ? JsonUtil.read(f, QuestBook.class, null) : null;
        if (b == null) {
            b = QuestExamples.book();
            JsonUtil.write(f, b);
        }
        book = b;
        reindex();
        Map<String, Progress> p = JsonUtil.readType(DataStore.file("quest_progress.json"), PROGRESS, null);
        progress = p == null ? new LinkedHashMap<String, Progress>() : p;
        for (Progress pr : progress.values()) {
            pr.normalize();
        }
        dirty = false;
    }

    public static void reindex() {
        if (book.chapters == null) {
            book.chapters = new ArrayList<QuestBook.Chapter>();
        }
        QUESTS.clear();
        QUEST_CHAPTER.clear();
        CHAPTERS.clear();
        book.chapters.sort((a, c) -> Integer.compare(a.order, c.order));
        for (QuestBook.Chapter c : book.chapters) {
            c.normalize();
            CHAPTERS.put(c.id, c);
            for (QuestBook.Quest q : c.quests) {
                QUESTS.put(q.id, q);
                QUEST_CHAPTER.put(q.id, c);
            }
        }
    }

    public static void saveBook() {
        reindex();
        JsonUtil.write(bookFile(), book);
    }

    public static void saveProgress() {
        JsonUtil.write(DataStore.file("quest_progress.json"), progress);
        dirty = false;
    }

    public static void onStop() {
        if (dirty) {
            saveProgress();
        }
    }

    public static QuestBook book() {
        return book;
    }

    public static List<QuestBook.Chapter> chapters() {
        return new ArrayList<QuestBook.Chapter>(book.chapters);
    }

    @Nullable
    public static QuestBook.Chapter chapter(String id) {
        return CHAPTERS.get(id);
    }

    @Nullable
    public static QuestBook.Quest quest(String id) {
        return QUESTS.get(id);
    }

    @Nullable
    public static QuestBook.Chapter chapterOf(QuestBook.Quest q) {
        return QUEST_CHAPTER.get(q.id);
    }

    public static List<QuestBook.Chapter> children(String parent) {
        List<QuestBook.Chapter> out = new ArrayList<QuestBook.Chapter>();
        for (QuestBook.Chapter c : book.chapters) {
            if (parent.equals(c.parent)) {
                out.add(c);
            }
        }
        return out;
    }

    // --- прогресс -------------------------------------------------------------------------------------

    static String keyOf(UUID player) {
        if (book.teamProgress) {
            try {
                String team = com.ecoserv.subworld.WorldCrews.keyOf(player);
                if (team != null) {
                    return "team:" + team;
                }
            } catch (Throwable ignored) {
                // без команд — у каждого свой
            }
        }
        return player.toString();
    }

    public static Progress progress(UUID player) {
        String k = keyOf(player);
        Progress p = progress.get(k);
        if (p == null) {
            p = new Progress();
            progress.put(k, p);
        }
        return p;
    }

    /** Сбросить прогресс игрока (весь или одного квеста). */
    public static void reset(UUID player, @Nullable String questId) {
        Progress p = progress(player);
        if (questId == null) {
            progress.remove(keyOf(player));
        } else {
            p.counts.remove(questId);
            p.done.remove(questId);
            p.claimed.remove(questId);
            p.checks.removeIf(s -> s.startsWith(questId + "#"));
        }
        dirty = true;
    }

    // --- состояния ------------------------------------------------------------------------------------

    private static long date(String s) {
        if (s == null || s.trim().isEmpty()) {
            return -1L;
        }
        try {
            return java.time.LocalDate.parse(s.trim()).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return -1L;
        }
    }

    /** Почему глава недоступна (null — доступна). Учитывает родителя. */
    @Nullable
    public static String chapterLock(EntityPlayerMP p, QuestBook.Chapter c) {
        return chapterLock(p, c, 0);
    }

    @Nullable
    private static String chapterLock(EntityPlayerMP p, QuestBook.Chapter c, int depth) {
        if (depth > 8) {
            return "&cцикл подглав";
        }
        if (!c.parent.isEmpty()) {
            QuestBook.Chapter parent = CHAPTERS.get(c.parent);
            if (parent != null) {
                String up = chapterLock(p, parent, depth + 1);
                if (up != null) {
                    return up;
                }
            }
        }
        long now = System.currentTimeMillis();
        long from = date(c.openFrom);
        if (from > 0 && now < from) {
            return "&7откроется " + c.openFrom;
        }
        for (String need : c.needChapters) {
            QuestBook.Chapter n = CHAPTERS.get(need);
            if (n != null && !chapterDone(p, n)) {
                return "&7сначала пройди главу «" + n.title + "&7»";
            }
        }
        for (String need : c.needQuests) {
            QuestBook.Quest q = QUESTS.get(need);
            if (q != null && !isDone(p, q)) {
                return "&7сначала выполни «" + q.title + "&7»";
            }
        }
        if (!c.needRole.isEmpty() && !com.ecoserv.role.RoleManager.has(p, c.needRole)) {
            return "&7нужна роль " + c.needRole;
        }
        if (!c.needAge.isEmpty() && !com.ecoserv.dates.DatesManager.checkAge(p.getUniqueID(),
                com.ecoserv.dates.AgeFilter.parse(c.needAge))) {
            return "&7не подходит по возрасту";
        }
        return null;
    }

    /** Почему глава стала недоступной навсегда (null — не закрыта). */
    @Nullable
    public static String chapterClosed(EntityPlayerMP p, QuestBook.Chapter c) {
        if (c.closed) {
            return c.closedText.isEmpty() ? "&cглава закрыта" : c.closedText;
        }
        long until = date(c.openUntil);
        if (until > 0 && System.currentTimeMillis() >= until + 86400000L) {
            return c.closedText.isEmpty() ? "&cсрок главы прошёл (" + c.openUntil + ")" : c.closedText;
        }
        Progress pr = progress(p.getUniqueID());
        for (String other : c.closeAfterChapters) {
            QuestBook.Chapter o = CHAPTERS.get(other);
            if (o != null && chapterDone(p, o)) {
                return c.closedText.isEmpty() ? "&cзакрыта: ты выбрал главу «" + o.title + "&c»" : c.closedText;
            }
        }
        if (!c.exclusiveGroup.isEmpty() && !pr.started.contains(c.id)) {
            for (QuestBook.Chapter o : book.chapters) {
                if (o != c && c.exclusiveGroup.equals(o.exclusiveGroup) && pr.started.contains(o.id)) {
                    return c.closedText.isEmpty() ? "&cзакрыта: выбрана ветка «" + o.title + "&c»" : c.closedText;
                }
            }
        }
        return null;
    }

    public static boolean chapterDone(EntityPlayerMP p, QuestBook.Chapter c) {
        boolean any = false;
        for (QuestBook.Quest q : c.quests) {
            if (q.optional) {
                continue;
            }
            any = true;
            if (!isDone(p, q)) {
                return false;
            }
        }
        return any;
    }

    public static boolean isDone(EntityPlayerMP p, QuestBook.Quest q) {
        return progress(p.getUniqueID()).done.containsKey(q.id);
    }

    public static State state(EntityPlayerMP p, QuestBook.Quest q) {
        Progress pr = progress(p.getUniqueID());
        if (pr.claimed.containsKey(q.id)) {
            return State.CLAIMED;
        }
        if (pr.done.containsKey(q.id)) {
            return State.DONE;
        }
        QuestBook.Chapter c = QUEST_CHAPTER.get(q.id);
        if (c != null && chapterClosed(p, c) != null) {
            return State.CLOSED;
        }
        if (c != null && chapterLock(p, c) != null) {
            return State.LOCKED;
        }
        for (String dep : q.dependencies) {
            QuestBook.Quest d = QUESTS.get(dep);
            if (d != null && !pr.done.containsKey(d.id)) {
                return q.hidden ? State.HIDDEN : State.LOCKED;
            }
        }
        return State.ACTIVE;
    }

    // --- условия ---------------------------------------------------------------------------------------

    private static long[] counts(Progress pr, QuestBook.Quest q) {
        long[] c = pr.counts.get(q.id);
        if (c == null || c.length != q.tasks.size()) {
            long[] n = new long[q.tasks.size()];
            if (c != null) {
                System.arraycopy(c, 0, n, 0, Math.min(c.length, n.length));
            }
            c = n;
            pr.counts.put(q.id, c);
        }
        return c;
    }

    /** Сколько сделано по условию (для живых проверок — считается сейчас). */
    public static long current(EntityPlayerMP p, QuestBook.Quest q, int i) {
        QuestBook.Task t = q.tasks.get(i);
        Progress pr = progress(p.getUniqueID());
        long stored = counts(pr, q)[i];
        if (pr.done.containsKey(q.id)) {
            return t.count;
        }
        QuestTypes.TaskType type = QuestTypes.task(t.type);
        if (type != null && type.live && !(type.submit && t.consume)) {
            return Math.max(stored, QuestTypes.liveValue(p, t));
        }
        if ("check".equals(t.type)) {
            return pr.checks.contains(q.id + "#" + i) ? 1 : 0;
        }
        return stored;
    }

    public static boolean taskDone(EntityPlayerMP p, QuestBook.Quest q, int i) {
        return current(p, q, i) >= q.tasks.get(i).count;
    }

    /** Событие игры: убил, добыл, скрафтил, нажал кнопку, поговорил, выиграл… */
    public static void event(EntityPlayerMP p, String type, String target, long amount) {
        if (book.chapters.isEmpty() || p == null) {
            return;
        }
        Progress pr = progress(p.getUniqueID());
        for (QuestBook.Quest q : QUESTS.values()) {
            boolean touched = false;
            for (int i = 0; i < q.tasks.size(); i++) {
                QuestBook.Task t = q.tasks.get(i);
                if (!t.type.equals(type) || !QuestTypes.matches(t, target)) {
                    continue;
                }
                if (state(p, q) != State.ACTIVE) {
                    break;
                }
                long[] c = counts(pr, q);
                if (c[i] < t.count) {
                    c[i] = Math.min(t.count, c[i] + amount);
                    touched = true;
                }
            }
            if (touched) {
                dirty = true;
                started(p, q);
                checkComplete(p, q);
            }
        }
    }

    private static void started(EntityPlayerMP p, QuestBook.Quest q) {
        QuestBook.Chapter c = QUEST_CHAPTER.get(q.id);
        if (c != null && progress(p.getUniqueID()).started.add(c.id)) {
            dirty = true;
        }
    }

    /** Сдать условие (предметы, деньги, валюта). null — получилось. */
    @Nullable
    public static String submit(EntityPlayerMP p, QuestBook.Quest q, int i) {
        if (state(p, q) != State.ACTIVE) {
            return "&cКвест сейчас недоступен.";
        }
        QuestBook.Task t = q.tasks.get(i);
        Progress pr = progress(p.getUniqueID());
        long[] c = counts(pr, q);
        long need = t.count - c[i];
        if (need <= 0) {
            return null;
        }
        long got = QuestTypes.take(p, t, need);
        if (got <= 0) {
            return "&cНечего сдать.";
        }
        c[i] += got;
        dirty = true;
        started(p, q);
        checkComplete(p, q);
        return null;
    }

    /** Отметить ручное условие. */
    public static void check(EntityPlayerMP p, QuestBook.Quest q, int i) {
        if (state(p, q) != State.ACTIVE) {
            return;
        }
        Progress pr = progress(p.getUniqueID());
        pr.checks.add(q.id + "#" + i);
        dirty = true;
        started(p, q);
        checkComplete(p, q);
    }

    static void checkComplete(EntityPlayerMP p, QuestBook.Quest q) {
        Progress pr = progress(p.getUniqueID());
        if (pr.done.containsKey(q.id) || q.tasks.isEmpty()) {
            return;
        }
        int ok = 0;
        for (int i = 0; i < q.tasks.size(); i++) {
            if (taskDone(p, q, i)) {
                ok++;
            }
        }
        if (q.anyTask ? ok == 0 : ok < q.tasks.size()) {
            return;
        }
        pr.done.put(q.id, System.currentTimeMillis());
        dirty = true;
        ChatUtil.send(p, "&aКвест выполнен: &f" + q.title + (q.autoClaim || q.rewards.isEmpty() ? "" : " &7— забери награду: /quests"));
        com.ecoserv.content.Actions.title(p, "&aКвест выполнен|&f" + q.title, null, 5, 40, 10);
        com.ecoserv.init.ModSounds.play(p, com.ecoserv.init.ModSounds.GAME_WIN, 0.7F, 1.2F);
        com.ecoserv.dev.DevEvents.fire("quest_done", p, q.id);
        if (q.autoClaim || q.rewards.isEmpty()) {
            claim(p, q);
        }
        // квест мог открыть следующие — проверить живые условия у них
        passive(p);
    }

    /** Забрать награду. null — получилось. */
    @Nullable
    public static String claim(EntityPlayerMP p, QuestBook.Quest q) {
        Progress pr = progress(p.getUniqueID());
        if (!pr.done.containsKey(q.id)) {
            return "&cКвест ещё не выполнен.";
        }
        if (pr.claimed.containsKey(q.id)) {
            return "&7Награда уже получена.";
        }
        pr.claimed.put(q.id, System.currentTimeMillis());
        dirty = true;
        for (QuestBook.Reward r : q.rewards) {
            try {
                QuestTypes.give(p, r, q);
            } catch (Throwable t) {
                EcoServ.LOG.error("Квест {}: награда {} не выдалась", q.id, r.type, t);
            }
        }
        if (!q.rewards.isEmpty()) {
            com.ecoserv.init.ModSounds.play(p, com.ecoserv.init.ModSounds.COIN, 0.8F, 1.0F);
        }
        return null;
    }

    // --- тик ------------------------------------------------------------------------------------------

    public static void tick() {
        tick++;
        if (tick % 20 == 0 && !book.chapters.isEmpty()) {
            for (EntityPlayerMP p : PlayerUtil.allOnline()) {
                try {
                    passive(p);
                    repeats(p);
                } catch (Throwable t) {
                    EcoServ.LOG.error("Квесты: ошибка проверки для {}", p.getName(), t);
                }
            }
        }
        if (dirty && tick % 600 == 0) {
            saveProgress();
        }
    }

    /** Живые условия (есть предмет, деньги, роль, место…) — проверка раз в секунду. */
    static void passive(EntityPlayerMP p) {
        for (QuestBook.Quest q : QUESTS.values()) {
            boolean live = false;
            for (QuestBook.Task t : q.tasks) {
                QuestTypes.TaskType tt = QuestTypes.task(t.type);
                if (tt != null && (tt.live || "check".equals(t.type))) {
                    live = true;
                    break;
                }
            }
            if (live && state(p, q) == State.ACTIVE) {
                checkComplete(p, q);
            }
        }
    }

    /** Повторяемые квесты: через N минут после награды — снова доступны. */
    private static void repeats(EntityPlayerMP p) {
        Progress pr = progress(p.getUniqueID());
        if (pr.claimed.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (QuestBook.Quest q : QUESTS.values()) {
            Long at = pr.claimed.get(q.id);
            if (q.repeatMinutes > 0 && at != null && now - at >= q.repeatMinutes * 60000L) {
                pr.claimed.remove(q.id);
                pr.done.remove(q.id);
                pr.counts.remove(q.id);
                pr.checks.removeIf(s -> s.startsWith(q.id + "#"));
                dirty = true;
            }
        }
    }

    // --- правка книги (админ) ----------------------------------------------------------------------------

    @Nullable
    public static QuestBook.Chapter createChapter(String id, String title, String parent) {
        String k = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(k).matches() || CHAPTERS.containsKey(k)) {
            return null;
        }
        QuestBook.Chapter c = new QuestBook.Chapter();
        c.id = k;
        c.title = title == null || title.trim().isEmpty() ? k : title.trim();
        c.parent = parent == null ? "" : parent;
        c.order = book.chapters.size() * 10;
        c.normalize();
        book.chapters.add(c);
        saveBook();
        return c;
    }

    @Nullable
    public static QuestBook.Quest createQuest(QuestBook.Chapter c, String id, String title) {
        String k = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(k).matches() || QUESTS.containsKey(k)) {
            return null;
        }
        QuestBook.Quest q = new QuestBook.Quest();
        q.id = k;
        q.title = title == null || title.trim().isEmpty() ? k : title.trim();
        q.normalize();
        c.quests.add(q);
        saveBook();
        return q;
    }

    public static void deleteChapter(QuestBook.Chapter c) {
        book.chapters.remove(c);
        for (QuestBook.Chapter o : book.chapters) {
            if (c.id.equals(o.parent)) {
                o.parent = c.parent;
            }
        }
        saveBook();
    }

    public static void deleteQuest(QuestBook.Quest q) {
        QuestBook.Chapter c = QUEST_CHAPTER.get(q.id);
        if (c != null) {
            c.quests.remove(q);
        }
        for (QuestBook.Quest o : QUESTS.values()) {
            o.dependencies.remove(q.id);
        }
        saveBook();
    }

    public static void markDirty() {
        dirty = true;
    }

    // --- проверка книги ---------------------------------------------------------------------------------

    /** Найти ошибки в книге: неизвестные предметы, ссылки, циклы, пустые квесты. */
    public static List<String> validate() {
        List<String> out = new ArrayList<String>();
        for (QuestBook.Chapter c : book.chapters) {
            if (!c.parent.isEmpty() && !CHAPTERS.containsKey(c.parent)) {
                out.add("Глава «" + c.title + "»: нет родительской главы «" + c.parent + "»");
            }
            Set<String> seen = new HashSet<String>();
            QuestBook.Chapter up = c;
            while (up != null && !up.parent.isEmpty()) {
                if (!seen.add(up.id)) {
                    out.add("Глава «" + c.title + "»: подглавы замкнуты в круг");
                    break;
                }
                up = CHAPTERS.get(up.parent);
            }
            for (String n : c.needChapters) {
                if (!CHAPTERS.containsKey(n)) {
                    out.add("Глава «" + c.title + "»: нужна несуществующая глава «" + n + "»");
                }
            }
            for (String n : c.closeAfterChapters) {
                if (!CHAPTERS.containsKey(n)) {
                    out.add("Глава «" + c.title + "»: закрывается после несуществующей главы «" + n + "»");
                }
            }
            for (String n : c.needQuests) {
                if (!QUESTS.containsKey(n)) {
                    out.add("Глава «" + c.title + "»: нужен несуществующий квест «" + n + "»");
                }
            }
            if (!c.openFrom.isEmpty() && date(c.openFrom) < 0) {
                out.add("Глава «" + c.title + "»: дата открытия не по формату ГГГГ-ММ-ДД");
            }
            if (!c.openUntil.isEmpty() && date(c.openUntil) < 0) {
                out.add("Глава «" + c.title + "»: дата закрытия не по формату ГГГГ-ММ-ДД");
            }
            for (QuestBook.Quest q : c.quests) {
                if (q.tasks.isEmpty()) {
                    out.add("Квест «" + q.title + "»: нет условий — его нельзя выполнить");
                }
                for (String d : q.dependencies) {
                    if (!QUESTS.containsKey(d)) {
                        out.add("Квест «" + q.title + "»: зависит от несуществующего «" + d + "»");
                    }
                }
                if (cycle(q, new HashSet<String>())) {
                    out.add("Квест «" + q.title + "»: зависимости замкнуты в круг");
                }
                for (QuestBook.Task t : q.tasks) {
                    String err = QuestTypes.validate(t);
                    if (err != null) {
                        out.add("Квест «" + q.title + "»: " + err);
                    }
                }
                for (QuestBook.Reward r : q.rewards) {
                    String err = QuestTypes.validate(r);
                    if (err != null) {
                        out.add("Квест «" + q.title + "», награда: " + err);
                    }
                }
            }
        }
        return out;
    }

    private static boolean cycle(QuestBook.Quest q, Set<String> path) {
        if (!path.add(q.id)) {
            return true;
        }
        for (String d : q.dependencies) {
            QuestBook.Quest n = QUESTS.get(d);
            if (n != null && cycle(n, path)) {
                return true;
            }
        }
        path.remove(q.id);
        return false;
    }

    /** Есть ли в инвентаре столько предметов. */
    static long countItems(EntityPlayerMP p, String id) {
        ItemKey key = ItemKey.parse(id);
        if (!key.isValid()) {
            return 0;
        }
        long n = 0;
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (!s.isEmpty() && key.matches(s)) {
                n += s.getCount();
            }
        }
        return n;
    }
}
