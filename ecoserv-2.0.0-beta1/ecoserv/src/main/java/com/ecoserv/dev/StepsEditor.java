package com.ecoserv.dev;

import com.ecoserv.script.ScriptManager;
import com.ecoserv.script.ScriptSteps;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Редактор шагов сценария прямо в окне (2.0): список шагов с проверкой, ↑ ↓ ✕, правка по клику, добавление.
 * Используется зонами, блоками-программами, NPC, меню и сценариями.
 */
public final class StepsEditor {

    /** Состояние редактора в окне (какой шаг правится, открыта ли справка). */
    public static final class State {
        int editing = -1;
        boolean help;
    }

    private StepsEditor() {
    }

    public static void build(Ui ui, final State st, String title, final List<String> steps, final Runnable save) {
        ui.header(title, steps.size() + " шаг.");
        for (int i = 0; i < steps.size(); i++) {
            final int idx = i;
            String line = steps.get(i);
            String err = line.trim().startsWith("#") ? null : ScriptManager.validate(line);
            if (st.editing == i) {
                ui.field("Шаг " + (i + 1), "Enter — сохранить", line, v -> {
                    String e = v.trim().isEmpty() || v.trim().startsWith("#") ? null : ScriptManager.validate(v);
                    if (e != null) {
                        throw new UiError("Шаг " + (idx + 1) + ": " + e);
                    }
                    if (v.trim().isEmpty()) {
                        steps.remove(idx);
                    } else {
                        steps.set(idx, v.trim());
                    }
                    st.editing = -1;
                    save.run();
                }).max(400);
                continue;
            }
            Ui.Row r = ui.row(new ItemStack(err == null ? Items.PAPER : Items.REDSTONE), (err == null ? "&f" : "&c") + (i + 1) + ". " + line);
            if (err != null) {
                r.line("&c" + err);
            }
            r.onClick(() -> st.editing = idx);
            r.button("↑", Ui.GREY, () -> {
                if (idx > 0) {
                    steps.set(idx, steps.set(idx - 1, steps.get(idx)));
                    save.run();
                }
            }).button("↓", Ui.GREY, () -> {
                if (idx < steps.size() - 1) {
                    steps.set(idx, steps.set(idx + 1, steps.get(idx)));
                    save.run();
                }
            }).button("✕", Ui.RED, () -> {
                steps.remove(idx);
                st.editing = -1;
                save.run();
            });
        }
        ui.field("Добавить шаг", "Например: msg &aПривет, %player%! — Enter", "", v -> {
            if (v.trim().isEmpty()) {
                return;
            }
            String e = v.trim().startsWith("#") ? null : ScriptManager.validate(v);
            if (e != null) {
                throw new UiError(e);
            }
            steps.add(v.trim());
            save.run();
        }).placeholder("msg &aПривет, %player%!").max(400);
        ui.buttons().add(st.help ? "Скрыть справку" : "Справка по шагам", Ui.GREY, () -> st.help = !st.help);
        if (st.help) {
            ui.text("&7Основные: &fmsg&7, &ftitle заголовок|подзаголовок&7, &factionbar&7, &fsound id&7, &fwait 2s&7, "
                    + "&fcmd команда&7, &fpcmd&7 (от игрока), &ftp x y z&7, &ftpworld мир&7, &factivate имя&7, "
                    + "&frequire role id&7, &frun сценарий&7, &fstop&7. Подстановки: %player%, %money%, %var:имя%.");
            for (String h : ScriptSteps.help()) {
                ui.text("&8• &f" + h);
            }
        }
    }
}
