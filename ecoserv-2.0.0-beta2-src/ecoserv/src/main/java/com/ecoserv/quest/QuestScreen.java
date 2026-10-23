package com.ecoserv.quest;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Книга квестов (2.0): главы и подглавы слева, квесты, условия с прогрессом и кнопками сдачи, награды.
 * Админ включает «Редактор» — правит всё прямо здесь, и «Проверку» — поиск ошибок в книге.
 */
public class QuestScreen extends UiScreen {

    private String chapter;
    @Nullable
    private String quest;
    private boolean edit;
    private boolean validate;
    /** Правка условия/награды: индекс (-1 — нет). */
    private int editTask = -1;
    private int editReward = -1;

    public QuestScreen(EntityPlayerMP player) {
        super(player);
    }

    public static void open(EntityPlayerMP player) {
        new QuestScreen(player).open();
    }

    public static void open(EntityPlayerMP player, String questId) {
        QuestScreen s = new QuestScreen(player);
        QuestBook.Quest q = QuestManager.quest(questId);
        if (q != null) {
            QuestBook.Chapter c = QuestManager.chapterOf(q);
            s.chapter = c == null ? null : c.id;
            s.quest = q.id;
        }
        s.open();
    }

    private boolean admin() {
        return Perms.isAdmin(player);
    }

    @Override
    protected int refreshSeconds() {
        return quest != null && !edit ? 2 : 0;
    }

    @Override
    protected void build(Ui ui) {
        if (edit && !admin()) {
            edit = false;
        }
        ui.title(edit ? "Квесты — редактор" : "Квесты").icon(UiIcons.questBook());
        List<QuestBook.Chapter> visible = visibleChapters();
        if (chapter == null || QuestManager.chapter(chapter) == null || (!edit && !contains(visible, chapter))) {
            chapter = visible.isEmpty() ? null : visible.get(0).id;
        }
        for (QuestBook.Chapter c : visible) {
            String prefix = depth(c) > 0 ? repeat("  ", depth(c) - 1) + "↳ " : "";
            String color = "";
            if (!edit) {
                if (QuestManager.chapterClosed(player, c) != null) {
                    color = "&8";
                } else if (QuestManager.chapterLock(player, c) != null) {
                    color = "&7";
                } else if (QuestManager.chapterDone(player, c)) {
                    color = "&a";
                }
            }
            final String id = c.id;
            ui.tab(prefix + color + c.title, UiUtil.icon(c.icon), id.equals(chapter), () -> {
                chapter = id;
                quest = null;
                editTask = editReward = -1;
                validate = false;
            });
        }
        if (admin()) {
            ui.sideLabel("&8Админ");
            ui.sideButton(edit ? "Выйти из редактора" : "Редактор", UiIcons.anvil(), edit ? Ui.YELLOW : Ui.PURPLE, () -> {
                edit = !edit;
                validate = false;
                editTask = editReward = -1;
            });
            ui.sideButton("Проверка", UiIcons.reload(), Ui.BLUE, () -> {
                validate = !validate;
                quest = null;
            }).tip("Найти ошибки в книге: несуществующие предметы, ссылки, круги зависимостей");
        }
        if (validate && admin()) {
            buildValidate(ui);
            return;
        }
        if (chapter == null) {
            ui.note(Ui.BLUE, admin() ? "Глав пока нет — включи «Редактор» и создай первую." : "Квестов пока нет.");
            if (edit) {
                newChapter(ui, "");
            }
            return;
        }
        QuestBook.Chapter c = QuestManager.chapter(chapter);
        QuestBook.Quest q = quest == null ? null : QuestManager.quest(quest);
        if (q != null && QuestManager.chapterOf(q) == c) {
            if (edit) {
                buildQuestEdit(ui, c, q);
            } else {
                buildQuest(ui, c, q);
            }
            return;
        }
        quest = null;
        if (edit) {
            buildChapterEdit(ui, c);
        } else {
            buildChapter(ui, c);
        }
    }

    private static boolean contains(List<QuestBook.Chapter> list, String id) {
        for (QuestBook.Chapter c : list) {
            if (c.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            b.append(s);
        }
        return b.toString();
    }

    private int depth(QuestBook.Chapter c) {
        int d = 0;
        QuestBook.Chapter up = c;
        while (up != null && !up.parent.isEmpty() && d < 6) {
            up = QuestManager.chapter(up.parent);
            d++;
        }
        return d;
    }

    /** Главы по дереву: родитель, за ним подглавы. */
    private List<QuestBook.Chapter> visibleChapters() {
        List<QuestBook.Chapter> out = new ArrayList<QuestBook.Chapter>();
        for (QuestBook.Chapter c : QuestManager.chapters()) {
            if (c.parent.isEmpty() || QuestManager.chapter(c.parent) == null) {
                add(out, c, 0);
            }
        }
        return out;
    }

    private void add(List<QuestBook.Chapter> out, QuestBook.Chapter c, int depth) {
        if (depth > 6 || out.contains(c)) {
            return;
        }
        boolean show = edit || !c.hidden || QuestManager.chapterLock(player, c) == null;
        if (!show) {
            return;
        }
        out.add(c);
        for (QuestBook.Chapter ch : QuestManager.children(c.id)) {
            add(out, ch, depth + 1);
        }
    }

    // --- игрок: глава -----------------------------------------------------------------------------------

    private void buildChapter(Ui ui, QuestBook.Chapter c) {
        ui.subtitle(c.title);
        if (!c.description.isEmpty()) {
            ui.text("&7" + c.description);
        }
        String closed = QuestManager.chapterClosed(player, c);
        String lock = QuestManager.chapterLock(player, c);
        if (closed != null) {
            ui.note(Ui.RED, "Глава недоступна: " + closed);
        } else if (lock != null) {
            ui.note(Ui.YELLOW, "Глава закрыта: " + lock);
        } else if (!c.openUntil.isEmpty()) {
            ui.note(Ui.BLUE, "Глава доступна до " + c.openUntil + ".");
        }
        if (!c.exclusiveGroup.isEmpty() && closed == null) {
            ui.note(Ui.YELLOW, "Это развилка: начнёшь эту главу — другие ветки выбора станут недоступны.");
        }
        int done = 0;
        int total = 0;
        for (QuestBook.Quest q : c.quests) {
            if (!q.optional) {
                total++;
                if (QuestManager.isDone(player, q)) {
                    done++;
                }
            }
        }
        if (total > 0) {
            ui.bar("Пройдено", done, total, done >= total ? Ui.GREEN : Ui.BLUE);
        }
        ui.header("Квесты", done + " / " + total);
        for (final QuestBook.Quest q : c.quests) {
            QuestManager.State st = QuestManager.state(player, q);
            if (st == QuestManager.State.HIDDEN) {
                continue;
            }
            Ui.Row r = ui.row(UiUtil.icon(q.icon), color(st) + q.title);
            int ok = 0;
            for (int i = 0; i < q.tasks.size(); i++) {
                if (QuestManager.taskDone(player, q, i)) {
                    ok++;
                }
            }
            r.line("&7условий: " + ok + " / " + q.tasks.size() + (q.optional ? " &8· необязательный" : "")
                    + (q.repeatMinutes > 0 ? " &8· повторяемый" : ""));
            chip(r, st);
            r.onClick(() -> quest = q.id);
            if (st == QuestManager.State.DONE) {
                r.button("Награда", Ui.GREEN, () -> claim(q));
            }
        }
        List<QuestBook.Chapter> subs = QuestManager.children(c.id);
        if (!subs.isEmpty()) {
            ui.header("Подглавы");
            for (final QuestBook.Chapter s : subs) {
                if (s.hidden && QuestManager.chapterLock(player, s) != null) {
                    continue;
                }
                String why = QuestManager.chapterClosed(player, s);
                if (why == null) {
                    why = QuestManager.chapterLock(player, s);
                }
                ui.row(UiUtil.icon(s.icon), "&f" + s.title).line(why == null ? "&aоткрыта" : why)
                        .onClick(() -> chapter = s.id);
            }
        }
    }

    private static String color(QuestManager.State st) {
        switch (st) {
            case CLAIMED:
                return "&a";
            case DONE:
                return "&e";
            case ACTIVE:
                return "&f";
            case CLOSED:
                return "&8";
            default:
                return "&7";
        }
    }

    private static void chip(Ui.Row r, QuestManager.State st) {
        switch (st) {
            case CLAIMED:
                r.chip("выполнен", Ui.GREEN);
                break;
            case DONE:
                r.chip("забери награду", Ui.YELLOW);
                break;
            case ACTIVE:
                r.chip("доступен", Ui.BLUE);
                break;
            case CLOSED:
                r.chip("недоступен", Ui.RED);
                break;
            default:
                r.chip("закрыт", Ui.GREY);
        }
    }

    private void claim(QuestBook.Quest q) {
        String err = QuestManager.claim(player, q);
        if (err != null) {
            throw new UiError(UiUtil.plain(err));
        }
        toast("Награда получена!");
    }

    // --- игрок: квест -----------------------------------------------------------------------------------

    private void buildQuest(Ui ui, QuestBook.Chapter c, final QuestBook.Quest q) {
        ui.back(() -> quest = null);
        ui.title(q.title).subtitle(c.title).icon(UiUtil.icon(q.icon));
        QuestManager.State st = QuestManager.state(player, q);
        if (!q.description.isEmpty()) {
            ui.text("&7" + q.description);
        }
        if (st == QuestManager.State.LOCKED) {
            List<String> need = new ArrayList<String>();
            for (String d : q.dependencies) {
                QuestBook.Quest dq = QuestManager.quest(d);
                if (dq != null && !QuestManager.isDone(player, dq)) {
                    need.add(dq.title);
                }
            }
            String lock = QuestManager.chapterLock(player, c);
            ui.note(Ui.YELLOW, lock != null ? "Глава закрыта: " + lock : "Сначала: " + String.join(", ", need));
        } else if (st == QuestManager.State.CLOSED) {
            ui.note(Ui.RED, "Недоступен: " + QuestManager.chapterClosed(player, c));
        }
        ui.header(q.anyTask ? "Условия (любое одно)" : "Условия");
        for (int i = 0; i < q.tasks.size(); i++) {
            final int idx = i;
            QuestBook.Task t = q.tasks.get(i);
            long cur = QuestManager.current(player, q, i);
            boolean ok = cur >= t.count;
            Ui.Row r = ui.row(QuestTypes.icon(t), (ok ? "&a✔ " : "&f") + QuestTypes.describe(t))
                    .line("&7" + Math.min(cur, t.count) + " / " + t.count);
            QuestTypes.TaskType tt = QuestTypes.task(t.type);
            if (!ok && st == QuestManager.State.ACTIVE && tt != null && tt.submit && t.consume) {
                r.button("Сдать", Ui.GREEN, () -> {
                    String err = QuestManager.submit(player, q, idx);
                    if (err != null) {
                        throw new UiError(UiUtil.plain(err));
                    }
                });
            }
            if (!ok && st == QuestManager.State.ACTIVE && "check".equals(t.type)) {
                r.button("Отметить", Ui.GREEN, () -> QuestManager.check(player, q, idx));
            }
        }
        if (!q.rewards.isEmpty()) {
            ui.header("Награды");
            for (QuestBook.Reward rw : q.rewards) {
                ui.row(QuestTypes.icon(rw), "&e" + QuestTypes.describe(rw));
            }
        }
        if (st == QuestManager.State.DONE) {
            ui.footer("Забрать награду", Ui.GREEN, () -> claim(q));
        } else if (st == QuestManager.State.CLAIMED) {
            ui.note(Ui.GREEN, "Квест выполнен" + (q.repeatMinutes > 0 ? " — повторить можно через " + q.repeatMinutes + " мин." : "."));
        }
    }

    // --- проверка -------------------------------------------------------------------------------------

    private void buildValidate(Ui ui) {
        ui.subtitle("Проверка книги");
        List<String> errs = QuestManager.validate();
        if (errs.isEmpty()) {
            ui.note(Ui.GREEN, "Ошибок не найдено: все предметы, роли, валюты, зоны и ссылки на месте, кругов нет.");
        } else {
            ui.note(Ui.RED, "Найдено ошибок: " + errs.size());
            for (String e : errs) {
                ui.row(new ItemStack(Blocks.BARRIER), "&c" + e);
            }
        }
        ui.footer("Назад", Ui.GREY, () -> validate = false);
    }

    // --- редактор: глава ----------------------------------------------------------------------------------

    private void newChapter(Ui ui, final String parent) {
        ui.field(parent.isEmpty() ? "Новая глава" : "Новая подглава", "id: латиница, цифры, _ и - — Enter", "", v -> {
            QuestBook.Chapter c = QuestManager.createChapter(v, v, parent);
            if (c == null) {
                throw new UiError("id занят или неверный.");
            }
            chapter = c.id;
            toast("Создано: " + c.id);
        }).placeholder(parent.isEmpty() ? "chapter_2" : parent + "_part").max(40);
    }

    private void buildChapterEdit(Ui ui, final QuestBook.Chapter c) {
        ui.subtitle("&eглава " + c.id);
        ui.header("Глава");
        ui.field("Название", "& — цвета", c.title, v -> {
            c.title = v.trim().isEmpty() ? c.id : v.trim();
            QuestManager.saveBook();
        }).max(48);
        ui.field("Описание", "", c.description, v -> {
            c.description = v.trim();
            QuestManager.saveBook();
        }).clearable().max(240);
        ui.field("Иконка", "Предмет", c.icon, v -> {
            String id = UiUtil.parseItemId(v);
            if (id == null) {
                throw new UiError("Не нашёл такой предмет.");
            }
            c.icon = id;
            QuestManager.saveBook();
        }).picker(Ui.PICK_ITEM);
        Ui.Select parent = ui.select("Родитель", "Подглава — внутри другой главы", c.parent, v -> {
            c.parent = v;
            QuestManager.saveBook();
        });
        parent.option("", "— (глава верхнего уровня)");
        for (QuestBook.Chapter o : QuestManager.chapters()) {
            if (o != c) {
                parent.option(o.id, o.title);
            }
        }
        ui.number("Порядок", "Меньше — выше в списке", c.order, -10000, 10000, v -> {
            c.order = (int) v;
            QuestManager.saveBook();
        });
        ui.toggle("Скрыта, пока закрыта", "", c.hidden, on -> {
            c.hidden = on;
            QuestManager.saveBook();
        });
        ui.header("Когда открывается");
        listField(ui, "Нужно пройти главы", "id через запятую", c.needChapters);
        listField(ui, "Нужно выполнить квесты", "id через запятую", c.needQuests);
        ui.field("Нужна роль", "id роли", c.needRole, v -> {
            c.needRole = v.trim().toLowerCase(Locale.ROOT);
            QuestManager.saveBook();
        }).clearable().max(32);
        ui.field("Возраст", "18, >=16, <=12, 14-17", c.needAge, v -> {
            c.needAge = v.trim();
            QuestManager.saveBook();
        }).clearable().max(16);
        ui.field("Открыть с даты", "ГГГГ-ММ-ДД", c.openFrom, v -> {
            c.openFrom = v.trim();
            QuestManager.saveBook();
        }).clearable().max(10);
        ui.header("Когда становится недоступной");
        ui.field("Доступна до", "ГГГГ-ММ-ДД — после этого дня закрыта (ивент)", c.openUntil, v -> {
            c.openUntil = v.trim();
            QuestManager.saveBook();
        }).clearable().max(10);
        listField(ui, "Закрывается после глав", "Прошёл любую из них — эта закрыта (сюжетный выбор)", c.closeAfterChapters);
        ui.field("Группа выбора", "У глав с одной группой: начал одну — остальные закрыты", c.exclusiveGroup, v -> {
            c.exclusiveGroup = v.trim().toLowerCase(Locale.ROOT);
            QuestManager.saveBook();
        }).clearable().max(32);
        ui.toggle("Закрыта вручную", "", c.closed, on -> {
            c.closed = on;
            QuestManager.saveBook();
        });
        ui.field("Текст, почему закрыта", "", c.closedText, v -> {
            c.closedText = v.trim();
            QuestManager.saveBook();
        }).clearable().max(120);

        ui.header("Квесты главы", c.quests.size() + "");
        for (final QuestBook.Quest q : c.quests) {
            ui.row(UiUtil.icon(q.icon), "&f" + q.title + " &8" + q.id)
                    .line("&7условий " + q.tasks.size() + ", наград " + q.rewards.size()
                            + (q.dependencies.isEmpty() ? "" : ", после: " + String.join(", ", q.dependencies)))
                    .onClick(() -> quest = q.id)
                    .button("↑", Ui.GREY, () -> move(c.quests, q, -1))
                    .button("↓", Ui.GREY, () -> move(c.quests, q, 1));
        }
        ui.field("Новый квест", "id: латиница, цифры, _ и - — Enter", "", v -> {
            QuestBook.Quest q = QuestManager.createQuest(c, v, v);
            if (q == null) {
                throw new UiError("id занят или неверный.");
            }
            quest = q.id;
        }).placeholder("find_iron").max(40);
        ui.header("Ещё");
        newChapter(ui, c.id);
        newChapter(ui, "");
        ui.buttons().add("Удалить главу", Ui.RED, () -> {
            QuestManager.deleteChapter(c);
            chapter = null;
            toast("Глава удалена.");
        }).confirm("Удалить главу «" + c.title + "» со всеми квестами? Подглавы поднимутся на уровень выше.");
        if (admin()) {
            ui.header("Прогресс игрока");
            ui.field("Сбросить прогресс", "Ник — весь его прогресс квестов обнулится", "", v -> {
                UUID id = PlayerUtil.uuidOf(v.trim());
                if (id == null) {
                    throw new UiError("Игрок не найден.");
                }
                QuestManager.reset(id, null);
                toast("Прогресс " + v.trim() + " сброшен.");
            }).picker(Ui.PICK_PLAYER).max(32);
            ui.toggle("Общий прогресс команды", "Квесты выполняются на всю команду (команды миров / FTB)", QuestManager.book().teamProgress, on -> {
                QuestManager.book().teamProgress = on;
                QuestManager.saveBook();
            });
        }
    }

    private static <T> void move(List<T> list, T item, int dir) {
        int i = list.indexOf(item);
        int j = i + dir;
        if (i < 0 || j < 0 || j >= list.size()) {
            return;
        }
        list.set(i, list.get(j));
        list.set(j, item);
        QuestManager.saveBook();
    }

    private void listField(Ui ui, String label, String desc, final List<String> list) {
        ui.field(label, desc, String.join(", ", list), v -> {
            list.clear();
            list.addAll(UiUtil.splitList(v));
            QuestManager.saveBook();
        }).clearable().max(400);
    }

    // --- редактор: квест ----------------------------------------------------------------------------------

    private void buildQuestEdit(Ui ui, final QuestBook.Chapter c, final QuestBook.Quest q) {
        ui.back(() -> {
            quest = null;
            editTask = editReward = -1;
        });
        ui.subtitle("&eквест " + q.id);
        if (editTask >= 0 && editTask < q.tasks.size()) {
            buildTaskEdit(ui, q, q.tasks.get(editTask));
            return;
        }
        if (editReward >= 0 && editReward < q.rewards.size()) {
            buildRewardEdit(ui, q, q.rewards.get(editReward));
            return;
        }
        editTask = editReward = -1;
        ui.field("Название", "", q.title, v -> {
            q.title = v.trim().isEmpty() ? q.id : v.trim();
            QuestManager.saveBook();
        }).max(48);
        ui.field("Описание", "", q.description, v -> {
            q.description = v.trim();
            QuestManager.saveBook();
        }).clearable().max(300);
        ui.field("Иконка", "Предмет", q.icon, v -> {
            String id = UiUtil.parseItemId(v);
            if (id == null) {
                throw new UiError("Не нашёл такой предмет.");
            }
            q.icon = id;
            QuestManager.saveBook();
        }).picker(Ui.PICK_ITEM);
        listField(ui, "После квестов", "id через запятую (зависимости)", q.dependencies);
        ui.toggle("Любое одно условие", "Иначе нужны все", q.anyTask, on -> {
            q.anyTask = on;
            QuestManager.saveBook();
        });
        ui.toggle("Необязательный", "Не нужен для прохождения главы", q.optional, on -> {
            q.optional = on;
            QuestManager.saveBook();
        });
        ui.toggle("Скрыт, пока закрыт", "", q.hidden, on -> {
            q.hidden = on;
            QuestManager.saveBook();
        });
        ui.toggle("Награда сама", "Без кнопки «Забрать»", q.autoClaim, on -> {
            q.autoClaim = on;
            QuestManager.saveBook();
        });
        ui.number("Повтор через (мин)", "0 — один раз", q.repeatMinutes, 0, 525600, v -> {
            q.repeatMinutes = (int) v;
            QuestManager.saveBook();
        });
        ui.header("Условия", q.tasks.size() + "");
        for (int i = 0; i < q.tasks.size(); i++) {
            final int idx = i;
            QuestBook.Task t = q.tasks.get(i);
            ui.row(QuestTypes.icon(t), "&f" + QuestTypes.describe(t)).line("&8" + t.type)
                    .onClick(() -> editTask = idx)
                    .button("✕", Ui.RED, () -> {
                        q.tasks.remove(idx);
                        QuestManager.saveBook();
                    });
        }
        Ui.Select add = ui.select("Добавить условие", "", "", v -> {
            QuestBook.Task t = new QuestBook.Task();
            t.type = v;
            t.normalize();
            q.tasks.add(t);
            editTask = q.tasks.size() - 1;
            QuestManager.saveBook();
        });
        for (QuestTypes.TaskType tt : QuestTypes.tasks()) {
            add.option(tt.id, tt.title, tt.targetHint, UiUtil.icon(tt.icon));
        }
        ui.header("Награды", q.rewards.size() + "");
        for (int i = 0; i < q.rewards.size(); i++) {
            final int idx = i;
            QuestBook.Reward r = q.rewards.get(i);
            ui.row(QuestTypes.icon(r), "&e" + QuestTypes.describe(r)).line("&8" + r.type)
                    .onClick(() -> editReward = idx)
                    .button("✕", Ui.RED, () -> {
                        q.rewards.remove(idx);
                        QuestManager.saveBook();
                    });
        }
        Ui.Select addR = ui.select("Добавить награду", "", "", v -> {
            QuestBook.Reward r = new QuestBook.Reward();
            r.type = v;
            r.normalize();
            q.rewards.add(r);
            editReward = q.rewards.size() - 1;
            QuestManager.saveBook();
        });
        for (QuestTypes.RewardType rt : QuestTypes.rewards()) {
            addR.option(rt.id, rt.title, rt.targetHint, UiUtil.icon(rt.icon));
        }
        ui.header("Ещё");
        Ui.Select move = ui.select("Перенести в главу", "", c.id, v -> {
            QuestBook.Chapter to = QuestManager.chapter(v);
            if (to != null && to != c) {
                c.quests.remove(q);
                to.quests.add(q);
                chapter = to.id;
                QuestManager.saveBook();
            }
        });
        for (QuestBook.Chapter o : QuestManager.chapters()) {
            move.option(o.id, o.title);
        }
        ui.buttons().add("Посмотреть как игрок", Ui.BLUE, () -> edit = false);
        ui.buttons().add("Удалить квест", Ui.RED, () -> {
            QuestManager.deleteQuest(q);
            quest = null;
            toast("Квест удалён.");
        }).confirm("Удалить квест «" + q.title + "»?");
    }

    private String pickerFor(String type) {
        switch (type) {
            case "item":
            case "craft":
            case "mine":
            case "place":
                return Ui.PICK_ITEM;
            default:
                return Ui.PICK_NONE;
        }
    }

    private void buildTaskEdit(Ui ui, final QuestBook.Quest q, final QuestBook.Task t) {
        QuestTypes.TaskType tt = QuestTypes.task(t.type);
        ui.header("Условие: " + (tt == null ? t.type : tt.title));
        Ui.Select type = ui.select("Вид", "", t.type, v -> {
            t.type = v;
            QuestManager.saveBook();
        });
        for (QuestTypes.TaskType x : QuestTypes.tasks()) {
            type.option(x.id, x.title, x.targetHint, UiUtil.icon(x.icon));
        }
        if ("talk".equals(t.type)) {
            // 2.0: NPC — выбором из списка
            Ui.Select npcSel = ui.select("NPC", "С кем поговорить", t.target, v -> {
                t.target = v;
                QuestManager.saveBook();
            });
            for (com.ecoserv.npc.Npc n : com.ecoserv.npc.NpcManager.all()) {
                npcSel.option(n.id, UiUtil.plain(n.name), n.id, UiUtil.icon(com.ecoserv.npc.MobTypes.icon(n.type)));
            }
            if (!t.target.isEmpty() && com.ecoserv.npc.NpcManager.get(t.target) == null) {
                npcSel.option(t.target, t.target, "нет такого NPC");
            }
        } else {
            Ui.Field target = ui.field("Цель", tt == null ? "" : tt.targetHint, t.target, v -> {
                t.target = v.trim();
                QuestManager.saveBook();
            }).clearable().max(128);
            String pick = pickerFor(t.type);
            if (!pick.isEmpty()) {
                target.picker(pick);
            }
        }
        ui.number("Количество", "Сколько нужно (сумма, уровень…)", t.count, 1, Long.MAX_VALUE / 4, v -> {
            t.count = v;
            QuestManager.saveBook();
        });
        if (tt != null && tt.submit) {
            ui.toggle("Забирать при сдаче", "Иначе достаточно иметь", t.consume, on -> {
                t.consume = on;
                QuestManager.saveBook();
            });
        }
        if ("visit".equals(t.type)) {
            ui.field("Место", "x y z радиус (пусто — любой точкой в мире)", t.extra, v -> {
                t.extra = v.trim();
                QuestManager.saveBook();
            }).clearable().max(64);
            ui.buttons().add("Место = где я стою", Ui.BLUE, () -> {
                t.target = com.ecoserv.world.WorldNames.nameOf(player.dimension);
                t.extra = (int) Math.floor(player.posX) + " " + (int) Math.floor(player.posY) + " " + (int) Math.floor(player.posZ) + " 5";
                QuestManager.saveBook();
            });
        }
        ui.field("Свой текст", "Пусто — автоматический", t.text, v -> {
            t.text = v.trim();
            QuestManager.saveBook();
        }).clearable().max(120);
        ui.row(QuestTypes.icon(t), "&f" + QuestTypes.describe(t)).line("&8так увидит игрок");
        String err = QuestTypes.validate(t);
        if (err != null) {
            ui.note(Ui.RED, "Ошибка: " + err);
        }
        ui.footer("Готово", Ui.GREEN, () -> editTask = -1);
    }

    private void buildRewardEdit(Ui ui, final QuestBook.Quest q, final QuestBook.Reward r) {
        QuestTypes.RewardType rt = QuestTypes.reward(r.type);
        ui.header("Награда: " + (rt == null ? r.type : rt.title));
        Ui.Select type = ui.select("Вид", "", r.type, v -> {
            r.type = v;
            QuestManager.saveBook();
        });
        for (QuestTypes.RewardType x : QuestTypes.rewards()) {
            type.option(x.id, x.title, x.targetHint, UiUtil.icon(x.icon));
        }
        Ui.Field target = ui.field("Что", rt == null ? "" : rt.targetHint, r.target, v -> {
            r.target = v.trim();
            QuestManager.saveBook();
        }).clearable().max(256);
        if ("item".equals(r.type)) {
            target.picker(Ui.PICK_ITEM);
            ui.buttons().add("Предмет из руки (с NBT)", Ui.BLUE, () -> {
                ItemStack hand = player.getHeldItemMainhand();
                if (hand.isEmpty()) {
                    throw new UiError("В руке ничего нет.");
                }
                r.target = com.ecoserv.util.ItemKey.of(hand, true, true).serialize();
                r.count = hand.getCount();
                QuestManager.saveBook();
            });
        }
        ui.number("Количество", "Штук / сумма / очков / дней", r.count, 0, Long.MAX_VALUE / 4, v -> {
            r.count = v;
            QuestManager.saveBook();
        });
        ui.field("Свой текст", "Пусто — автоматический", r.text, v -> {
            r.text = v.trim();
            QuestManager.saveBook();
        }).clearable().max(120);
        ui.row(QuestTypes.icon(r), "&e" + QuestTypes.describe(r)).line("&8так увидит игрок");
        String err = QuestTypes.validate(r);
        if (err != null) {
            ui.note(Ui.RED, "Ошибка: " + err);
        }
        ui.footer("Готово", Ui.GREEN, () -> editReward = -1);
        ui.footer("Выдать себе (проверить)", Ui.GREY, () -> {
            QuestTypes.give(player, r, q);
            toast("Выдано.");
        });
    }

    static ItemStack bookIcon() {
        return new ItemStack(Items.WRITABLE_BOOK);
    }
}
