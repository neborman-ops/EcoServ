package com.ecoserv.cutscene;

import com.ecoserv.dev.StepsEditor;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Locale;

/** Редактор катсцен: точки камеры («встань и посмотри — добавь точку»), время, текст, действия. */
public class CutsceneScreen extends UiScreen {

    private static final String[][] TIMES = {{"20", "1 сек"}, {"40", "2 сек"}, {"60", "3 сек"}, {"100", "5 сек"},
            {"160", "8 сек"}, {"240", "12 сек"}, {"400", "20 сек"}};
    private static final String[][] HOLDS = {{"0", "не стоять"}, {"20", "1 сек"}, {"40", "2 сек"}, {"60", "3 сек"},
            {"100", "5 сек"}};

    @Nullable
    private String cut;
    private int point = -1;
    private boolean end;
    private final StepsEditor.State steps = new StepsEditor.State();

    public CutsceneScreen(EntityPlayerMP player, @Nullable String cut) {
        super(player);
        this.cut = cut;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    private static String sec(int ticks) {
        return String.format(Locale.ROOT, "%.1f с", ticks / 20.0D);
    }

    @Override
    protected void build(Ui ui) {
        if (StepsEditor.modal(ui, player, steps)) {
            return;
        }
        final Cutscene c = cut == null ? null : CutsceneManager.get(cut);
        if (c == null) {
            cut = null;
            list(ui);
            return;
        }
        ui.back(() -> {
            if (point >= 0 || end) {
                point = -1;
                end = false;
            } else {
                cut = null;
            }
        });
        ui.title("Катсцена " + c.id).subtitle("&7" + c.points.size() + " точек · " + sec(c.length())).icon(new ItemStack(Items.ENDER_EYE));
        ui.sideButton("Проиграть", new ItemStack(Items.ENDER_EYE), Ui.GREEN, () -> {
            close();
            CutsceneManager.play(player, c, null);
        });
        if (end) {
            StepsEditor.build(ui, steps, "Действия в конце", c.endSteps, CutsceneManager::save);
            return;
        }
        if (point >= 0 && point < c.points.size()) {
            pointEditor(ui, c, c.points.get(point));
            return;
        }
        ui.text("&7Встань туда, где должна быть камера, посмотри куда нужно и нажми «+ точка здесь». Камера пролетит"
                + " по точкам по порядку. Игрок во время катсцены не двигается" + (c.skippable ? ", Shift — пропустить." : "."));
        ui.header("Точки камеры", c.points.size() + "");
        for (int i = 0; i < c.points.size(); i++) {
            final int idx = i;
            Cutscene.Point p = c.points.get(i);
            ui.row(new ItemStack(i == 0 ? Items.ENDER_EYE : Items.ENDER_PEARL), "&f" + (i + 1) + ". "
                    + (p.text.isEmpty() ? "&7без текста" : p.text.split("\\|")[0]))
                    .line("&7" + (i == 0 ? "старт" : "полёт " + sec(p.ticks)) + (p.hold > 0 ? ", стоит " + sec(p.hold) : "")
                            + (p.steps.isEmpty() ? "" : " · действий: " + p.steps.size()))
                    .line("&8" + String.format(Locale.ROOT, "%.1f %.1f %.1f · %.0f° %.0f°", p.x, p.y, p.z, p.yaw, p.pitch))
                    .onClick(() -> point = idx)
                    .button("✎", Ui.BLUE, () -> point = idx)
                    .button("⌖", Ui.GREY, () -> player.connection.setPlayerLocation(p.x, p.y - 1.62D, p.z, p.yaw, p.pitch)).tip("Встать в точку")
                    .button("✕", Ui.RED, () -> {
                        c.points.remove(idx);
                        CutsceneManager.save();
                    });
        }
        ui.buttons().add("+ точка здесь", Ui.GREEN, () -> {
            Cutscene.Point p = CutsceneManager.here(player);
            if (c.points.isEmpty()) {
                c.dim = player.dimension;
            }
            c.points.add(p);
            CutsceneManager.save();
            toast("Точка " + c.points.size() + " добавлена.");
        }).add("Действия в конце", Ui.BLUE, () -> end = true);
        ui.header("Настройки");
        ui.toggle("Плавная кривая", "Камера летит дугой через точки (иначе — прямыми)", c.smooth, on -> {
            c.smooth = on;
            CutsceneManager.save();
        });
        ui.toggle("Разгон и торможение", "Плавно трогается и останавливается на каждой точке", c.ease, on -> {
            c.ease = on;
            CutsceneManager.save();
        });
        ui.toggle("Скрыть интерфейс", "Без хотбара и прицела", c.hideHud, on -> {
            c.hideHud = on;
            CutsceneManager.save();
        });
        ui.toggle("Кинополосы", "Чёрные полосы сверху и снизу", c.bars, on -> {
            c.bars = on;
            CutsceneManager.save();
        });
        ui.toggle("Можно пропустить", "Shift — пропустить", c.skippable, on -> {
            c.skippable = on;
            CutsceneManager.save();
        });
        ui.toggle("Переносить игрока за камерой", "Если точки дальше прорисовки — игрок невидимо летит рядом и"
                + " потом возвращается", c.movePlayer, on -> {
            c.movePlayer = on;
            CutsceneManager.save();
        });
        ui.note(Ui.BLUE, "Запуск: шаг сценария «Катсцена», команда &f/cutscene " + c.id + "&9, событие &fcutscene_end&9 в конце.");
        ui.buttons().add("Удалить катсцену", Ui.RED, () -> {
            CutsceneManager.delete(c);
            cut = null;
        }).confirm("Удалить катсцену «" + c.id + "»?");
    }

    private void list(Ui ui) {
        ui.title("Катсцены").icon(new ItemStack(Items.ENDER_EYE));
        ui.text("&7Полёт камеры по точкам с текстом на экране и действиями: заставка при входе, показ новой локации,"
                + " финал квеста.");
        for (final Cutscene c : CutsceneManager.all()) {
            ui.row(new ItemStack(Items.ENDER_EYE), "&f" + c.id).line("&7" + c.points.size() + " точек · " + sec(c.length()))
                    .onClick(() -> cut = c.id)
                    .button("✎", Ui.BLUE, () -> cut = c.id)
                    .button("▶", Ui.GREEN, () -> {
                        close();
                        CutsceneManager.play(player, c, null);
                    });
        }
        ui.field("Новая катсцена", "id — Enter", "", v -> {
            Cutscene c = CutsceneManager.create(player, v);
            cut = c.id;
        }).placeholder("intro").max(32);
    }

    private void pointEditor(Ui ui, final Cutscene c, final Cutscene.Point p) {
        ui.header("Точка " + (point + 1));
        if (point > 0) {
            Ui.Select t = ui.select("Полёт до неё", "", Integer.toString(p.ticks), v -> {
                p.ticks = Integer.parseInt(v);
                CutsceneManager.save();
            });
            boolean has = false;
            for (String[] x : TIMES) {
                t.option(x[0], x[1]);
                has |= x[0].equals(Integer.toString(p.ticks));
            }
            if (!has) {
                t.option(Integer.toString(p.ticks), sec(p.ticks));
            }
        }
        Ui.Select h = ui.select("Постоять на точке", "", Integer.toString(p.hold), v -> {
            p.hold = Integer.parseInt(v);
            CutsceneManager.save();
        });
        boolean has = false;
        for (String[] x : HOLDS) {
            h.option(x[0], x[1]);
            has |= x[0].equals(Integer.toString(p.hold));
        }
        if (!has) {
            h.option(Integer.toString(p.hold), sec(p.hold));
        }
        ui.field("Текст на экране", "Заголовок|подзаголовок; пусто — без текста", p.text, v -> {
            p.text = v.trim();
            CutsceneManager.save();
        }).clearable().max(120);
        ui.buttons().add("Переставить сюда", Ui.BLUE, () -> {
            Cutscene.Point n = CutsceneManager.here(player);
            p.x = n.x;
            p.y = n.y;
            p.z = n.z;
            p.yaw = n.yaw;
            p.pitch = n.pitch;
            CutsceneManager.save();
            toast("Точка переставлена.");
        }).add("Встать в точку", Ui.GREY, () -> player.connection.setPlayerLocation(p.x, p.y - 1.62D, p.z, p.yaw, p.pitch))
                .add("Вставить после", Ui.GREEN, () -> {
                    c.points.add(point + 1, CutsceneManager.here(player));
                    CutsceneManager.save();
                    point++;
                });
        StepsEditor.build(ui, steps, "Действия при прилёте", p.steps, CutsceneManager::save);
    }
}
