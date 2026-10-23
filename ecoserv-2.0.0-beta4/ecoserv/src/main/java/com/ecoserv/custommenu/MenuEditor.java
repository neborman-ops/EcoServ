package com.ecoserv.custommenu;

import com.ecoserv.dev.ConditionCatalog;
import com.ecoserv.dev.ParamForm;
import com.ecoserv.dev.StepsEditor;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Конструктор своих меню (2.0): окно, элементы карточками, как открыть. */
public class MenuEditor extends UiScreen {

    /** Виды элементов: id, название, описание, иконка. */
    static final String[][] KINDS = {
            {"header", "Заголовок", "Разделитель с подписью", "minecraft:sign"},
            {"text", "Текст", "Абзац текста; \\n — новая строка", "minecraft:paper"},
            {"note", "Заметка", "Цветная плашка", "minecraft:map"},
            {"row", "Строка", "Иконка, название, описание — нажимается", "minecraft:item_frame"},
            {"button", "Кнопка", "Цветная кнопка", "minecraft:stone_button"},
            {"item", "Предмет в сетке", "Ячейка с предметом — нажимается", "minecraft:chest"},
            {"side", "Боковая кнопка", "Кнопка слева, рядом с вкладками", "minecraft:lever"},
    };

    static final String[][] COLORS = {{"blue", "Синий"}, {"green", "Зелёный"}, {"yellow", "Жёлтый"}, {"red", "Красный"},
            {"grey", "Серый"}};

    private enum Tab {
        WINDOW, ELEMENTS, OPEN
    }

    @Nullable
    private String menu;
    private Tab tab = Tab.ELEMENTS;
    private int element = -1;
    private boolean choosing;
    private final StepsEditor.State steps = new StepsEditor.State();
    private final Map<String, String> cond = new HashMap<String, String>();
    private String newId = "";

    public MenuEditor(EntityPlayerMP player, @Nullable String menu) {
        super(player);
        this.menu = menu;
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

    static String kindIcon(String k) {
        for (String[] x : KINDS) {
            if (x[0].equals(k)) {
                return x[3];
            }
        }
        return "minecraft:paper";
    }

    @Override
    protected void build(Ui ui) {
        if (StepsEditor.modal(ui, player, steps)) {
            return;
        }
        CustomMenu m = menu == null ? null : MenuManager.get(menu);
        if (m == null) {
            menu = null;
            list(ui);
            return;
        }
        ui.back(() -> {
            if (element >= 0 || choosing) {
                element = -1;
                choosing = false;
            } else {
                menu = null;
            }
        });
        ui.title("Меню: " + UiUtil.plain(m.title)).subtitle("&8" + m.id).icon(UiUtil.icon(m.icon));
        ui.tab("Элементы &8(" + m.elements.size() + ")", new ItemStack(Items.ITEM_FRAME), tab == Tab.ELEMENTS, () -> tab = Tab.ELEMENTS);
        ui.tab("Окно", new ItemStack(Items.PAINTING), tab == Tab.WINDOW, () -> tab = Tab.WINDOW);
        ui.tab("Как открыть", new ItemStack(Blocks.LEVER), tab == Tab.OPEN, () -> tab = Tab.OPEN);
        ui.sideLabel("&8Проверка");
        ui.sideButton("Открыть как игрок", new ItemStack(Items.ENDER_EYE), Ui.GREEN, () -> new MenuScreen(player, m.id).open());
        switch (tab) {
            case WINDOW:
                window(ui, m);
                break;
            case OPEN:
                howToOpen(ui, m);
                break;
            default:
                if (choosing) {
                    chooser(ui, m);
                } else if (element >= 0 && element < m.elements.size()) {
                    elementEditor(ui, m, m.elements.get(element));
                } else {
                    element = -1;
                    elements(ui, m);
                }
        }
    }

    private void list(Ui ui) {
        ui.title("Свои меню").icon(new ItemStack(Items.ITEM_FRAME));
        ui.text("&7Собери своё окно: заголовки, текст, кнопки, строки и предметы. Кнопки выполняют действия"
                + " (дать предмет, телепорт, открыть другое меню…). Открывается командой, предметом, блоком-программой,"
                + " NPC или кнопкой в меню паузы (ESC).");
        for (final CustomMenu m : MenuManager.all()) {
            Ui.Row r = ui.row(UiUtil.icon(m.icon), m.title + " &8" + m.id)
                    .line("&7элементов: " + m.elements.size() + (m.escButton ? " · &bв меню ESC" : ""));
            r.onClick(() -> menu = m.id);
            r.button("✎", Ui.BLUE, () -> menu = m.id).button("▶", Ui.GREEN, () -> new MenuScreen(player, m.id).open()).tip("Открыть");
        }
        ui.field("Новое меню", "id — Enter", newId, v -> {
            CustomMenu m = MenuManager.create(v);
            menu = m.id;
            tab = Tab.ELEMENTS;
            choosing = true;
        }).placeholder("donate").max(32);
    }

    private void window(Ui ui, final CustomMenu m) {
        ui.field("Заголовок", "& — цвета, %player%, %money%, %var:имя%", m.title, v -> {
            m.title = v.trim().isEmpty() ? m.id : v;
            MenuManager.save();
            MenuManager.syncEsc();
        }).max(64);
        ui.field("Подзаголовок", "", m.subtitle, v -> {
            m.subtitle = v;
            MenuManager.save();
        }).clearable().max(120);
        ui.field("Иконка", "", m.icon, v -> {
            String id = UiUtil.parseItemId(v);
            if (id == null) {
                throw new UiError("Не нашёл такой предмет.");
            }
            m.icon = id;
            MenuManager.save();
        }).picker(Ui.PICK_ITEM);
        ui.select("Обновлять", "Чтобы баланс и переменные менялись на глазах", Integer.toString(m.refresh), v -> {
            m.refresh = Integer.parseInt(v);
            MenuManager.save();
        }).option("0", "Нет").option("1", "Каждую секунду").option("5", "Раз в 5 секунд");
        ui.toggle("Только при условии", "Кто не подходит — не откроет меню и не увидит кнопку в ESC", !m.condition.isEmpty(), on -> {
            m.condition = on ? "role vip" : "";
            MenuManager.save();
            MenuManager.syncEsc();
        });
        if (!m.condition.isEmpty()) {
            conditionForm(ui, m.condition, c -> {
                m.condition = c;
                MenuManager.save();
                MenuManager.syncEsc();
            });
        }
        ui.buttons().add("Удалить меню", Ui.RED, () -> {
            MenuManager.delete(m);
            menu = null;
        }).confirm("Удалить меню «" + m.id + "»?");
    }

    private interface CondSet {
        void set(String c);
    }

    private void conditionForm(Ui ui, String current, final CondSet set) {
        cond.put("c", current);
        ParamForm.condition(ui, player, "c", cond);
        if (!cond.get("c").equals(current)) {
            set.set(cond.get("c"));
        }
        ui.note(Ui.BLUE, "Условие: " + ConditionCatalog.describe(cond.get("c")));
    }

    private void howToOpen(Ui ui, final CustomMenu m) {
        ui.header("Кнопка в меню паузы (ESC)");
        ui.toggle("Показывать кнопку в ESC", "Слева вверху в меню паузы у всех игроков с модом", m.escButton, on -> {
            m.escButton = on;
            MenuManager.save();
            MenuManager.syncEsc();
        });
        if (m.escButton) {
            ui.field("Надпись на кнопке", "пусто — заголовок меню", m.escLabel, v -> {
                m.escLabel = v.trim();
                MenuManager.save();
                MenuManager.syncEsc();
            }).max(32);
        }
        ui.header("Другие способы");
        ui.row(new ItemStack(Blocks.COMMAND_BLOCK), "&fКоманда").line("&7/menu " + m.id);
        ui.row(new ItemStack(Items.WRITABLE_BOOK), "&fШаг сценария").line("&7menu " + m.id + " &8— в блоке-программе, NPC, зоне, другом меню");
        ui.row(UiUtil.icon(m.icon), "&fПредмет-ключ").line("&7ПКМ этим предметом — открыть меню")
                .button("Взять", Ui.GREEN, () -> PlayerUtil.giveOrDrop(player, MenuManager.keyItem(m)));
        ui.row(new ItemStack(Blocks.STONE_BUTTON), "&fМеню блока").line("&7Поставь блок-программу: «Когда» → нажатие,"
                + " «Действия» → «Открыть меню». Блок можно спрятать под любой текстурой рядом.");
    }

    // --- элементы ---------------------------------------------------------------------------------

    private void elements(Ui ui, final CustomMenu m) {
        if (m.elements.isEmpty()) {
            ui.text("&8Меню пустое — добавь первый элемент.");
        }
        for (int i = 0; i < m.elements.size(); i++) {
            final int idx = i;
            CustomMenu.Element e = m.elements.get(i);
            Ui.Row r = ui.row(UiUtil.icon("row".equals(e.kind) || "item".equals(e.kind) || "side".equals(e.kind) ? e.icon : kindIcon(e.kind)),
                    "&f" + (i + 1) + ". " + kindTitle(e.kind) + " &7" + UiUtil.cut(UiUtil.plain(e.text).replace("\\n", " "), 40));
            StringBuilder b = new StringBuilder();
            if (!e.steps.isEmpty()) {
                b.append("действий: ").append(e.steps.size()).append(" ");
            }
            if ("close".equals(e.then)) {
                b.append("· закрывает ");
            } else if (e.then.startsWith("menu:")) {
                b.append("· → меню ").append(e.then.substring(5)).append(' ');
            }
            if (!e.condition.isEmpty()) {
                b.append("&9· если ").append(ConditionCatalog.describe(e.condition));
            }
            if (b.length() > 0) {
                r.line("&8" + b);
            }
            r.onClick(() -> element = idx);
            r.button("✎", Ui.BLUE, () -> element = idx)
                    .button("↑", Ui.GREY, () -> move(m, idx, -1))
                    .button("↓", Ui.GREY, () -> move(m, idx, 1))
                    .button("⧉", Ui.GREY, () -> {
                        CustomMenu.Element c = com.ecoserv.util.JsonUtil.GSON.fromJson(com.ecoserv.util.JsonUtil.GSON.toJson(m.elements.get(idx)), CustomMenu.Element.class);
                        m.elements.add(idx + 1, c);
                        MenuManager.save();
                    }).tip("Копия")
                    .button("✕", Ui.RED, () -> {
                        m.elements.remove(idx);
                        MenuManager.save();
                    });
        }
        ui.buttons().add("Добавить элемент", Ui.GREEN, () -> choosing = true);
    }

    private static void move(CustomMenu m, int i, int d) {
        int j = i + d;
        if (j >= 0 && j < m.elements.size()) {
            m.elements.add(j, m.elements.remove(i));
            MenuManager.save();
        }
    }

    private void chooser(Ui ui, final CustomMenu m) {
        ui.header("Что добавить?");
        for (final String[] k : KINDS) {
            ui.row(UiUtil.icon(k[3]), "&f" + k[1]).line("&7" + k[2]).onClick(() -> {
                CustomMenu.Element e = new CustomMenu.Element();
                e.kind = k[0];
                e.text = "header".equals(k[0]) ? "Раздел" : "text".equals(k[0]) ? "&7Текст" : "note".equals(k[0]) ? "Заметка"
                        : "Нажми меня";
                e.icon = "item".equals(k[0]) ? "minecraft:diamond" : "minecraft:paper";
                m.elements.add(e);
                MenuManager.save();
                choosing = false;
                element = m.elements.size() - 1;
            });
        }
    }

    private void elementEditor(Ui ui, final CustomMenu m, final CustomMenu.Element e) {
        ui.header(kindTitle(e.kind) + " " + (element + 1));
        Ui.Select kind = ui.select("Вид", "", e.kind, v -> {
            e.kind = v;
            MenuManager.save();
        });
        for (String[] k : KINDS) {
            kind.option(k[0], k[1], k[2], UiUtil.icon(k[3]));
        }
        boolean clickable = "row".equals(e.kind) || "button".equals(e.kind) || "item".equals(e.kind) || "side".equals(e.kind);
        ui.field("text".equals(e.kind) ? "Текст" : "Надпись", "& — цвета, %player%, %money%, %var:имя%, \\n — строка", e.text, v -> {
            e.text = v;
            MenuManager.save();
        }).max(400);
        if ("row".equals(e.kind) || "item".equals(e.kind) || "header".equals(e.kind)) {
            ui.field("header".equals(e.kind) ? "Справа" : "Описание", "", e.desc, v -> {
                e.desc = v;
                MenuManager.save();
            }).clearable().max(300);
        }
        if ("row".equals(e.kind) || "item".equals(e.kind) || "side".equals(e.kind)) {
            ui.field("Иконка", "", e.icon, v -> {
                String id = UiUtil.parseItemId(v);
                if (id == null) {
                    throw new UiError("Не нашёл такой предмет.");
                }
                e.icon = id;
                MenuManager.save();
            }).picker(Ui.PICK_ITEM);
        }
        if ("button".equals(e.kind) || "note".equals(e.kind) || "side".equals(e.kind)) {
            Ui.Select c = ui.select("Цвет", "", e.color, v -> {
                e.color = v;
                MenuManager.save();
            });
            for (String[] x : COLORS) {
                c.option(x[0], x[1]);
            }
        }
        ui.toggle("Показывать при условии", "Иначе элемент скрыт", !e.condition.isEmpty(), on -> {
            e.condition = on ? "role vip" : "";
            MenuManager.save();
        });
        if (!e.condition.isEmpty()) {
            conditionForm(ui, e.condition, c -> {
                e.condition = c;
                MenuManager.save();
            });
        }
        if (clickable) {
            Ui.Select then = ui.select("После нажатия", "", e.then, v -> {
                e.then = v;
                MenuManager.save();
            });
            then.option("", "Остаться в меню", "", new ItemStack(Items.ITEM_FRAME));
            then.option("close", "Закрыть меню", "", new ItemStack(Blocks.OAK_DOOR));
            for (CustomMenu o : MenuManager.all()) {
                if (!o.id.equals(m.id)) {
                    then.option("menu:" + o.id, "Открыть «" + UiUtil.plain(o.title) + "»", o.id, UiUtil.icon(o.icon));
                }
            }
            ui.field("Спросить подтверждение", "пусто — без вопроса", e.confirm, v -> {
                e.confirm = v.trim();
                MenuManager.save();
            }).clearable().max(120);
            StepsEditor.build(ui, steps, "Действия при нажатии", e.steps, MenuManager::save);
        }
    }
}
