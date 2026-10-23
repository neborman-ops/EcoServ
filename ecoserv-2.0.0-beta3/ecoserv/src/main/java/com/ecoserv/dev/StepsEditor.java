package com.ecoserv.dev;

import com.ecoserv.script.ScriptManager;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Визуальный редактор действий (2.0). Список шагов с понятными подписями и иконками; «Добавить действие» —
 * карточки по разделам (Чат, Игрок, Экономика, Мир, Логика…), затем форма с выбором (предмет, звук, мир,
 * сценарий, условие…). Опытным — «Текстом».
 * <p>Окно, где стоит редактор, в начале build() вызывает {@link #modal}: пока открыт выбор или форма, они
 * занимают всё окно.</p>
 */
public final class StepsEditor {

    /** Состояние редактора в окне. */
    public static final class State {
        /** 0 — список, 1 — выбор действия, 2 — форма, 3 — текстом. */
        int mode;
        @Nullable
        String def;
        /** Какой шаг правится (-1 — новый). */
        int index = -1;
        final Map<String, String> values = new LinkedHashMap<String, String>();
        String raw = "";
        boolean help;
        List<String> steps;
        Runnable save;
        String title = "";
    }

    private StepsEditor() {
    }

    /** Открыт выбор/форма — нарисовать их на всё окно. true — окно занято редактором. */
    public static boolean modal(Ui ui, EntityPlayerMP player, final State st) {
        if (st.mode == 0 || st.steps == null) {
            return false;
        }
        if (st.mode == 1) {
            chooser(ui, st);
        } else if (st.mode == 2) {
            form(ui, player, st);
        } else {
            rawForm(ui, st);
        }
        return true;
    }

    /** Список шагов (в обычном виде окна). */
    public static void build(Ui ui, final State st, String title, final List<String> steps, final Runnable save) {
        st.steps = steps;
        st.save = save;
        st.title = title;
        ui.header(title, steps.isEmpty() ? "&8пусто" : steps.size() + " шаг.");
        if (steps.isEmpty()) {
            ui.text("&8Пока ничего не делает — нажми «Добавить действие».");
        }
        for (int i = 0; i < steps.size(); i++) {
            final int idx = i;
            String line = steps.get(i);
            String err = line.trim().startsWith("#") ? null : ScriptManager.validate(line);
            StepCatalog.Def d = StepCatalog.of(line);
            ItemStack icon = err != null ? new ItemStack(Items.REDSTONE) : d != null ? d.iconStack() : new ItemStack(Items.PAPER);
            Ui.Row r = ui.row(icon, (err == null ? "&f" : "&c") + (i + 1) + ". " + StepCatalog.describe(line));
            r.line("&8" + line);
            if (err != null) {
                r.line("&c" + err);
            }
            r.onClick(() -> edit(st, idx));
            r.button("✎", Ui.BLUE, () -> edit(st, idx)).tip("Изменить")
                    .button("↑", Ui.GREY, () -> {
                        if (idx > 0) {
                            steps.set(idx, steps.set(idx - 1, steps.get(idx)));
                            save.run();
                        }
                    }).button("↓", Ui.GREY, () -> {
                        if (idx < steps.size() - 1) {
                            steps.set(idx, steps.set(idx + 1, steps.get(idx)));
                            save.run();
                        }
                    }).button("⧉", Ui.GREY, () -> {
                        steps.add(idx + 1, steps.get(idx));
                        save.run();
                    }).tip("Копия шага").button("✕", Ui.RED, () -> {
                        steps.remove(idx);
                        save.run();
                    });
        }
        Ui.Buttons b = ui.buttons();
        b.add("+ Добавить действие", Ui.GREEN, () -> {
            st.mode = 1;
            st.index = -1;
        });
        b.add("Текстом", Ui.GREY, () -> {
            st.mode = 3;
            st.index = -1;
            st.raw = "";
        }).tip("Для опытных: шаг строкой, например msg &aПривет");
    }

    private static void edit(State st, int idx) {
        String line = st.steps.get(idx);
        StepCatalog.Def d = StepCatalog.of(line);
        st.index = idx;
        if (d == null) {
            st.mode = 3;
            st.raw = line;
            return;
        }
        st.def = d.id;
        st.values.clear();
        st.values.putAll(StepCatalog.parse(d, line));
        st.mode = 2;
    }

    // --- выбор действия ------------------------------------------------------------------------------

    private static void chooser(Ui ui, final State st) {
        ui.title("Добавить действие").subtitle(st.title).icon(new ItemStack(Items.WRITABLE_BOOK));
        ui.back(() -> st.mode = 0);
        ui.text("&7Выбери, что сделать. Наведи на иконку — описание.");
        for (String cat : StepCatalog.categories()) {
            List<StepCatalog.Def> defs = StepCatalog.inCategory(cat);
            if (defs.isEmpty()) {
                continue;
            }
            ui.header(cat);
            Ui.Grid g = ui.grid();
            for (final StepCatalog.Def d : defs) {
                ItemStack icon = d.iconStack().copy();
                icon.setStackDisplayName("§f" + d.title);
                g.cell(icon, false, e -> {
                    st.def = d.id;
                    st.values.clear();
                    for (StepCatalog.Param p : d.params) {
                        st.values.put(p.key, p.def);
                    }
                    st.mode = d.params.isEmpty() ? finishNoParams(st, d) : 2;
                }, "§7" + d.desc);
            }
        }
    }

    private static int finishNoParams(State st, StepCatalog.Def d) {
        put(st, d.id);
        return 0;
    }

    private static void put(State st, String line) {
        if (st.index >= 0 && st.index < st.steps.size()) {
            st.steps.set(st.index, line);
        } else {
            st.steps.add(line);
        }
        st.save.run();
        st.index = -1;
    }

    // --- форма --------------------------------------------------------------------------------------

    private static void form(Ui ui, EntityPlayerMP player, final State st) {
        final StepCatalog.Def d = StepCatalog.get(st.def);
        if (d == null) {
            st.mode = 0;
            return;
        }
        ui.title(d.title).subtitle(st.index >= 0 ? "изменить шаг " + (st.index + 1) : "новое действие").icon(d.iconStack());
        ui.back(() -> st.mode = st.index >= 0 ? 0 : 1);
        ui.text("&7" + d.desc);
        for (StepCatalog.Param p : d.params) {
            ParamForm.field(ui, player, p, st.values);
        }
        String line = StepCatalog.build(d, st.values);
        String err = ScriptManager.validate(line);
        ui.note(err == null ? Ui.GREEN : Ui.RED, (err == null ? "Получится: " : "Ошибка: " + err + " — ") + "&f" + line);
        ui.footer("Отмена", Ui.GREY, () -> st.mode = 0);
        ui.footer("Готово", Ui.GREEN, () -> {
            String l = StepCatalog.build(d, st.values);
            String e = ScriptManager.validate(l);
            if (e != null) {
                throw new UiError(e);
            }
            put(st, l);
            st.mode = 0;
        });
    }

    private static void rawForm(Ui ui, final State st) {
        ui.title("Шаг текстом").subtitle(st.title).icon(new ItemStack(Items.PAPER));
        ui.back(() -> st.mode = 0);
        ui.field("Шаг", "Enter — проверить", st.raw, v -> st.raw = v.trim()).max(400);
        String err = st.raw.isEmpty() ? "пусто" : st.raw.startsWith("#") ? null : ScriptManager.validate(st.raw);
        if (!st.raw.isEmpty()) {
            ui.note(err == null ? Ui.GREEN : Ui.RED, err == null ? "Понятно: " + StepCatalog.describe(st.raw) : "Ошибка: " + err);
        }
        ui.buttons().add(st.help ? "Скрыть справку" : "Справка по шагам", Ui.GREY, () -> st.help = !st.help);
        if (st.help) {
            ui.text("&7Основные: &fmsg&7, &ftitle заголовок|подзаголовок&7, &factionbar&7, &fsound id&7, &fwait 2s&7, "
                    + "&fcmd команда&7, &fpcmd&7, &ftp x y z&7, &ftpworld мир&7, &factivate имя&7, &frequire role id&7, "
                    + "&frun сценарий&7, &fstop&7. Подстановки: %player%, %money%, %var:имя%.");
            for (String h : com.ecoserv.script.ScriptSteps.help()) {
                ui.text("&8• &f" + h);
            }
        }
        ui.footer("Отмена", Ui.GREY, () -> st.mode = 0);
        ui.footer("Готово", Ui.GREEN, () -> {
            String e = st.raw.isEmpty() ? "пустой шаг" : st.raw.startsWith("#") ? null : ScriptManager.validate(st.raw);
            if (e != null) {
                throw new UiError(e);
            }
            put(st, st.raw);
            st.mode = 0;
        });
    }
}
