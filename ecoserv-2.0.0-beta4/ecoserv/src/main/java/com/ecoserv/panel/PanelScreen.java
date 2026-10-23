package com.ecoserv.panel;

import com.ecoserv.dev.ConditionCatalog;
import com.ecoserv.dev.ParamForm;
import com.ecoserv.dev.StepsEditor;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Редактор 3D-панелей. */
public class PanelScreen extends UiScreen {

    static final String[][] KINDS = {
            {"title", "Заголовок", "Крупный текст", "minecraft:sign"},
            {"text", "Текст", "Строка текста; подстановки %player%, %money%, %var:имя%", "minecraft:paper"},
            {"button", "Кнопка", "Нажимается взглядом + ПКМ", "minecraft:stone_button"},
            {"bar", "Полоса", "Заполнение по переменной игрока", "minecraft:experience_bottle"},
            {"line", "Разделитель", "Тонкая линия", "minecraft:string"},
    };

    private enum Tab {
        ELEMENTS, LOOK, PLACE
    }

    @Nullable
    private String panel;
    private Tab tab = Tab.ELEMENTS;
    private int element = -1;
    private final StepsEditor.State steps = new StepsEditor.State();
    private final Map<String, String> cond = new HashMap<String, String>();

    public PanelScreen(EntityPlayerMP player, @Nullable String panel) {
        super(player);
        this.panel = panel;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    static String kindTitle(String k) {
        for (String[] x : KINDS) {
            if (x[0].equals(k)) {
                return x[1];
            }
        }
        return k;
    }

    @Override
    protected void build(Ui ui) {
        if (StepsEditor.modal(ui, player, steps)) {
            return;
        }
        final Panel p = panel == null ? null : PanelManager.get(panel);
        if (p == null) {
            panel = null;
            ui.title("3D-панели").icon(new ItemStack(Items.ITEM_FRAME));
            ui.text("&7Окно прямо в мире: заголовок, текст с подстановками, кнопки (смотришь на кнопку и жмёшь ПКМ),"
                    + " полосы прогресса. Двигать и поворачивать удобно в редакторе сцены (/editor).");
            for (final Panel x : PanelManager.all()) {
                ui.row(new ItemStack(Items.ITEM_FRAME), "&f" + x.id)
                        .line(String.format("&7%s %.0f %.0f %.0f · %.1f×%.1f", com.ecoserv.world.WorldNames.nameOf(x.dim), x.x, x.y, x.z,
                                x.width, x.height))
                        .onClick(() -> panel = x.id).button("✎", Ui.BLUE, () -> panel = x.id);
            }
            ui.field("Новая панель перед тобой", "id — Enter (пусто — само)", "", v -> panel = PanelManager.create(player, v).id)
                    .placeholder("табло").max(32);
            return;
        }
        ui.back(() -> {
            if (element >= 0) {
                element = -1;
            } else {
                panel = null;
            }
        });
        ui.title("Панель " + p.id).icon(new ItemStack(Items.ITEM_FRAME));
        ui.tab("Элементы", new ItemStack(Items.PAPER), tab == Tab.ELEMENTS, () -> tab = Tab.ELEMENTS);
        ui.tab("Вид", new ItemStack(Items.PAINTING), tab == Tab.LOOK, () -> tab = Tab.LOOK);
        ui.tab("Место", new ItemStack(Items.COMPASS), tab == Tab.PLACE, () -> tab = Tab.PLACE);
        switch (tab) {
            case LOOK:
                look(ui, p);
                break;
            case PLACE:
                place(ui, p);
                break;
            default:
                if (element >= 0 && element < p.elements.size()) {
                    element(ui, p, p.elements.get(element));
                } else {
                    element = -1;
                    elements(ui, p);
                }
        }
    }

    private void elements(Ui ui, final Panel p) {
        for (int i = 0; i < p.elements.size(); i++) {
            final int idx = i;
            Panel.Element e = p.elements.get(i);
            ui.row(UiUtil.icon(icon(e.kind)), "&f" + (i + 1) + ". " + kindTitle(e.kind) + " &7" + UiUtil.cut(UiUtil.plain(e.text), 40))
                    .line(e.steps.isEmpty() ? "" : "&8действий: " + e.steps.size())
                    .onClick(() -> element = idx)
                    .button("✎", Ui.BLUE, () -> element = idx)
                    .button("↑", Ui.GREY, () -> {
                        if (idx > 0) {
                            p.elements.add(idx - 1, p.elements.remove(idx));
                            PanelManager.changed(p);
                        }
                    })
                    .button("✕", Ui.RED, () -> {
                        p.elements.remove(idx);
                        PanelManager.changed(p);
                    });
        }
        Ui.Select add = ui.select("Добавить", "Выбери, что добавить", "", v -> {
            Panel.Element e = new Panel.Element();
            e.kind = v;
            e.text = "button".equals(v) ? "Кнопка" : "title".equals(v) ? "&6Заголовок" : "bar".equals(v) ? "Прогресс" : "Текст";
            p.elements.add(e);
            PanelManager.changed(p);
            element = p.elements.size() - 1;
        });
        for (String[] k : KINDS) {
            add.option(k[0], k[1], k[2], UiUtil.icon(k[3]));
        }
    }

    static String icon(String kind) {
        for (String[] k : KINDS) {
            if (k[0].equals(kind)) {
                return k[3];
            }
        }
        return "minecraft:paper";
    }

    private void element(Ui ui, final Panel p, final Panel.Element e) {
        ui.header(kindTitle(e.kind));
        if (!"line".equals(e.kind)) {
            ui.field("Текст", "& — цвета, %player%, %money%, %online%, %var:имя%", e.text, v -> {
                e.text = v;
                PanelManager.changed(p);
            }).max(200);
        }
        ui.color("Цвет текста", "", e.color, v -> {
            e.color = (int) v;
            PanelManager.changed(p);
        });
        if ("button".equals(e.kind) || "bar".equals(e.kind)) {
            ui.color("button".equals(e.kind) ? "Цвет кнопки" : "Цвет полосы", "", e.bg, v -> {
                e.bg = (int) v;
                PanelManager.changed(p);
            });
        }
        if ("bar".equals(e.kind)) {
            ui.field("Переменная", "Имя переменной игрока (шаги «Задать/Прибавить»)", e.var, v -> {
                e.var = v.trim();
                PanelManager.changed(p);
            }).max(32);
            ui.slider("Максимум", "", e.max, 1, 10000, 1, "", v -> {
                e.max = v;
                PanelManager.changed(p);
            });
        }
        ui.toggle("Показывать при условии", "", !e.condition.isEmpty(), on -> {
            e.condition = on ? "role vip" : "";
            PanelManager.changed(p);
        });
        if (!e.condition.isEmpty()) {
            cond.put("c", e.condition);
            ParamForm.condition(ui, player, "c", cond);
            if (!cond.get("c").equals(e.condition)) {
                e.condition = cond.get("c");
                PanelManager.changed(p);
            }
            ui.note(Ui.BLUE, "Условие: " + ConditionCatalog.describe(e.condition));
        }
        if ("button".equals(e.kind)) {
            StepsEditor.build(ui, steps, "Действия кнопки", e.steps, () -> PanelManager.changed(p));
        }
    }

    private void look(Ui ui, final Panel p) {
        ui.slider("Ширина", "Блоков", p.width, 0.5, 16, 0.1, "", v -> {
            p.width = v;
            PanelManager.changed(p);
        });
        ui.slider("Высота", "Блоков", p.height, 0.5, 16, 0.1, "", v -> {
            p.height = v;
            PanelManager.changed(p);
        });
        ui.slider("Размер текста", "", p.textScale, 0.3, 4, 0.05, "×", v -> {
            p.textScale = v;
            PanelManager.changed(p);
        });
        ui.color("Фон", "", p.bg, v -> {
            p.bg = (int) v;
            PanelManager.changed(p);
        });
        ui.slider("Непрозрачность фона", "", p.bgAlpha, 0, 1, 0.01, "", v -> {
            p.bgAlpha = v;
            PanelManager.changed(p);
        });
        ui.color("Рамка", "", p.border, v -> {
            p.border = (int) v;
            PanelManager.changed(p);
        });
        ui.toggle("Всегда лицом к игроку", "", p.billboard, on -> {
            p.billboard = on;
            PanelManager.changed(p);
        });
        ui.slider("Видно с расстояния", "Блоков", p.viewDistance, 4, 128, 1, "", v -> {
            p.viewDistance = v;
            PanelManager.changed(p);
        });
        ui.toggle("Видна при условии", "", !p.condition.isEmpty(), on -> {
            p.condition = on ? "role vip" : "";
            PanelManager.changed(p);
        });
        if (!p.condition.isEmpty()) {
            cond.put("pc", p.condition);
            ParamForm.condition(ui, player, "pc", cond);
            if (!cond.get("pc").equals(p.condition)) {
                p.condition = cond.get("pc");
                PanelManager.changed(p);
            }
        }
    }

    private void place(Ui ui, final Panel p) {
        ui.text(String.format("&7%s %.2f %.2f %.2f", com.ecoserv.world.WorldNames.nameOf(p.dim), p.x, p.y, p.z));
        ui.buttons().add("Перед собой", Ui.GREEN, () -> {
            net.minecraft.util.math.Vec3d look = player.getLookVec();
            p.dim = player.dimension;
            p.x = player.posX + look.x * 3;
            p.y = player.posY + 1.6D;
            p.z = player.posZ + look.z * 3;
            p.yaw = player.rotationYaw + 180.0F;
            p.pitch = 0;
            PanelManager.changed(p);
        }).add("Выше", Ui.GREY, () -> {
            p.y += 0.25;
            PanelManager.changed(p);
        }).add("Ниже", Ui.GREY, () -> {
            p.y -= 0.25;
            PanelManager.changed(p);
        });
        ui.slider("Поворот", "", ((p.yaw % 360) + 540) % 360 - 180, -180, 180, 1, "°", v -> {
            p.yaw = (float) v;
            PanelManager.changed(p);
        });
        ui.slider("Наклон", "", p.pitch, -90, 90, 1, "°", v -> {
            p.pitch = (float) v;
            PanelManager.changed(p);
        });
        ui.buttons().add("Удалить панель", Ui.RED, () -> {
            PanelManager.delete(p);
            panel = null;
        }).confirm("Удалить панель «" + p.id + "»?");
        ui.row(new ItemStack(Blocks.COMMAND_BLOCK), "&fСобытие").line("&7panel_click:" + p.id + ":номер — нажатие кнопки");
    }
}
