package com.ecoserv.dev;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Настройка блока-программы (2.0) — по шагам, как конструктор: «Обзор», «Когда», «Условия», «Действия»,
 * «Для кого», «Выход и пауза», «Журнал». Всё выбирается карточками и списками.
 */
public class LogicScreen extends UiScreen {

    private enum Tab {
        OVERVIEW("Обзор", "minecraft:book"),
        WHEN("Когда", "minecraft:clock"),
        IF("Условия", "minecraft:comparator"),
        DO("Действия", "minecraft:writable_book"),
        WHO("Для кого", "minecraft:skull@3"),
        OUT("Выход и пауза", "minecraft:redstone_torch"),
        LOG("Журнал", "minecraft:paper");

        final String title;
        final String icon;

        Tab(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private final BlockPos pos;
    private Tab tab = Tab.OVERVIEW;
    private final StepsEditor.State steps = new StepsEditor.State();
    /** Новое условие в работе. */
    private final Map<String, String> newCond = new HashMap<String, String>();

    public LogicScreen(EntityPlayerMP player, BlockPos pos) {
        super(player);
        this.pos = pos;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected int refreshSeconds() {
        return tab == Tab.LOG ? 1 : 0;
    }

    @Override
    protected void build(Ui ui) {
        TileEntity te = player.world.getTileEntity(pos);
        if (!(te instanceof TileLogic)) {
            ui.title("Блок-программа").note(Ui.RED, "Блока здесь больше нет.");
            return;
        }
        final TileLogic t = (TileLogic) te;
        if (StepsEditor.modal(ui, player, steps)) {
            return;
        }
        ui.title("Блок-программа").subtitle(t.name.isEmpty() ? "&8без имени" : "&f" + t.name)
                .icon(new ItemStack(com.ecoserv.v2.ModRegistry2.LOGIC));
        for (Tab x : Tab.values()) {
            final Tab tx = x;
            String badge = "";
            if (x == Tab.IF && !t.conditions.isEmpty()) {
                badge = " &8(" + t.conditions.size() + ")";
            } else if (x == Tab.DO) {
                badge = " &8(" + t.steps.size() + ")";
            }
            ui.tab(x.title + badge, UiUtil.icon(x.icon), tab == x, () -> tab = tx);
        }
        ui.sideLabel("&8Проверка");
        ui.sideButton("Запустить на себе", new ItemStack(Items.ENDER_EYE), Ui.GREEN, () -> {
            t.fire(player, "проверка");
            tab = Tab.LOG;
        }).tip("&7Как будто сработал триггер — с проверкой условий");
        ui.sideButton(t.enabled ? "Выключить" : "Включить", new ItemStack(t.enabled ? Blocks.REDSTONE_TORCH : Blocks.LEVER),
                t.enabled ? Ui.YELLOW : Ui.GREEN, () -> {
                    t.enabled = !t.enabled;
                    t.changed();
                });
        switch (tab) {
            case WHEN:
                when(ui, t);
                break;
            case IF:
                conditions(ui, t);
                break;
            case DO:
                StepsEditor.build(ui, steps, "Что делает", t.steps, t::changed);
                break;
            case WHO:
                who(ui, t);
                break;
            case OUT:
                output(ui, t);
                break;
            case LOG:
                ui.header("Последние срабатывания");
                if (t.log.isEmpty()) {
                    ui.text("&8Пока не срабатывал. Нажми «Запустить на себе».");
                }
                for (String l : t.log) {
                    ui.text(l);
                }
                break;
            default:
                overview(ui, t);
        }
    }

    private void overview(Ui ui, final TileLogic t) {
        ui.note(t.enabled ? (t.global() ? Ui.RED : Ui.BLUE) : Ui.GREY, (t.enabled ? "" : "Выключен. ") + t.summary());
        ui.field("Имя блока", "Для событий logic:имя, квестов и поиска", t.name, v -> {
            t.name = v.trim().replace(' ', '_');
            t.changed();
        }).placeholder("дверь_замка").max(48);
        ui.header("По шагам");
        step(ui, "1. Когда сработать", describeTrigger(t), Tab.WHEN, "minecraft:clock");
        step(ui, "2. Условия", t.conditions.isEmpty() ? "без условий — всегда" : t.conditions.size() + " условие(й)", Tab.IF,
                "minecraft:comparator");
        step(ui, "3. Действия", t.steps.isEmpty() ? "&cничего не делает" : t.steps.size() + " действ.", Tab.DO, "minecraft:writable_book");
        step(ui, "4. Для кого", t.global() ? "&cдля всех в мире" : TileLogic.WHO_NEAR.equals(t.who) ? "&9для всех рядом"
                : "&9для того, кто запустил", Tab.WHO, "minecraft:skull@3");
        step(ui, "5. Выход и пауза", (t.pulseTicks > 0 ? "сигнал " + t.pulseTicks + " тиков" : "без сигнала")
                + ", пауза " + t.cooldown + " тиков", Tab.OUT, "minecraft:redstone_torch");
        ui.note(Ui.BLUE, "Цвет в режиме разработчика: &cкрасный&f — глобальный (для всех в мире), &9синий&f — локальный.");
    }

    private void step(Ui ui, String title, String line, final Tab to, String icon) {
        ui.row(UiUtil.icon(icon), "&f" + title).line("&7" + line).onClick(() -> tab = to).button("Настроить", Ui.BLUE, () -> tab = to);
    }

    private static String describeTrigger(TileLogic t) {
        switch (t.trigger) {
            case "redstone":
                return "сигнал редстоуна";
            case "near":
                return "игрок ближе " + t.radius + " бл.";
            case "chat":
                return "слово «" + t.chatWord + "» в чате рядом";
            case "timer":
                return "каждые " + t.interval + " тиков";
            case "event":
                return "событие «" + t.eventName + "»";
            default:
                return "ПКМ по блоку" + (t.keyItem.isEmpty() ? "" : " с ключом");
        }
    }

    // --- когда -----------------------------------------------------------------------------------------

    private void when(Ui ui, final TileLogic t) {
        ui.header("Когда срабатывает — выбери карточку");
        String[][] cards = {
                {"click", "ПКМ по блоку", "minecraft:stone_button", "Игрок нажимает на блок (можно — только с ключом в руке)"},
                {"redstone", "Сигнал редстоуна", "minecraft:redstone", "Кнопка, рычаг, плита, датчик рядом с блоком"},
                {"near", "Игрок рядом", "minecraft:compass", "Подошёл ближе радиуса (один раз или пока рядом)"},
                {"chat", "Слово в чате", "minecraft:writable_book", "Игрок рядом написал слово-пароль"},
                {"timer", "Таймер", "minecraft:clock", "Сам, через равные промежутки"},
                {"event", "Событие", "minecraft:observer", "Вход игрока, квест, зона, своё событие…"}};
        Ui.Grid g = ui.grid();
        for (final String[] c : cards) {
            ItemStack icon = UiUtil.icon(c[2]).copy();
            icon.setStackDisplayName((c[0].equals(t.trigger) ? "§a✔ " : "§f") + c[1]);
            g.cell(icon, c[0].equals(t.trigger), e -> {
                t.trigger = c[0];
                t.changed();
            }, "§7" + c[3]);
        }
        ui.header("Настройки: " + describeTrigger(t));
        switch (t.trigger) {
            case "click":
                ui.field("Ключ в руке", "Пусто — не нужен. Кнопка … — выбрать предмет", t.keyItem, v -> {
                    String id = UiUtil.parseItemId(v);
                    t.keyItem = id == null ? "" : id;
                    t.changed();
                }).picker(Ui.PICK_ITEM).clearable();
                if (!t.keyItem.isEmpty()) {
                    ui.toggle("Забирать ключ", "Предмет исчезнет из руки (одноразовый ключ)", t.takeKey, on -> {
                        t.takeKey = on;
                        t.changed();
                    });
                }
                break;
            case "near":
                radius(ui, t);
                ui.toggle("Пока рядом — повторять", "Иначе — только когда игрок подошёл", t.repeatNear, on -> {
                    t.repeatNear = on;
                    t.changed();
                });
                if (t.repeatNear) {
                    interval(ui, t, "Повторять каждые");
                }
                break;
            case "chat":
                ui.field("Слово", "Если сообщение содержит это слово (без учёта регистра)", t.chatWord, v -> {
                    t.chatWord = v.trim();
                    t.changed();
                }).placeholder("сезам").max(48);
                radius(ui, t);
                break;
            case "timer":
                interval(ui, t, "Каждые");
                break;
            case "event": {
                Ui.Select s = ui.select("Событие", "Выбери из списка или впиши своё ниже", t.eventName, v -> {
                    t.eventName = v;
                    t.changed();
                });
                for (Map.Entry<String, String> e : DevManager.BUILTIN.entrySet()) {
                    s.option(e.getKey(), e.getKey(), e.getValue());
                }
                ui.field("Своё / с уточнением", "Например zone_enter:арена, quest_done:iron, дверь_открыта", t.eventName, v -> {
                    t.eventName = v.trim();
                    t.changed();
                }).max(64);
                break;
            }
            default:
                ui.text("&7Достаточно сигнала на любую сторону блока.");
        }
    }

    private static void radius(Ui ui, final TileLogic t) {
        Ui.Select s = ui.select("Радиус", "блоков", Integer.toString(t.radius), v -> {
            t.radius = Integer.parseInt(v);
            t.changed();
        });
        for (int r : new int[]{1, 2, 3, 4, 6, 8, 12, 16, 24, 32}) {
            s.option(Integer.toString(r), r + " бл.");
        }
    }

    private static void interval(Ui ui, final TileLogic t, String label) {
        Ui.Select s = ui.select(label, "", Integer.toString(t.interval), v -> {
            t.interval = Integer.parseInt(v);
            t.changed();
        });
        int[][] opts = {{10, 0}, {20, 1}, {40, 2}, {100, 5}, {200, 10}, {600, 30}, {1200, 60}, {6000, 300}};
        for (int[] o : opts) {
            s.option(Integer.toString(o[0]), o[1] == 0 ? "0.5 сек" : o[1] < 60 ? o[1] + " сек" : (o[1] / 60) + " мин");
        }
        ui.number(label + " (тиков)", "20 тиков = 1 секунда", t.interval, 5, 72000, v -> {
            t.interval = (int) v;
            t.changed();
        });
    }

    // --- условия ----------------------------------------------------------------------------------------

    private void conditions(Ui ui, final TileLogic t) {
        ui.header("Условия", t.conditions.isEmpty() ? "&8нет — всегда" : "все должны выполняться");
        for (int i = 0; i < t.conditions.size(); i++) {
            final int idx = i;
            String c = t.conditions.get(i);
            ConditionCatalog.Def d = ConditionCatalog.get(ConditionCatalog.parse(c).get("type"));
            ui.row(d == null ? new ItemStack(Items.PAPER) : UiUtil.icon(d.icon), "&f" + ConditionCatalog.describe(c))
                    .line("&8" + c)
                    .button("✕", Ui.RED, () -> {
                        t.conditions.remove(idx);
                        t.changed();
                    });
        }
        ui.header("Добавить условие");
        ParamForm.condition(ui, player, "c", newCond);
        ui.buttons().add("+ Добавить это условие", Ui.GREEN, () -> {
            String c = newCond.get("c");
            if (c == null || c.isEmpty() || c.contains("?")) {
                throw new UiError("Заполни поля условия.");
            }
            t.conditions.add(c);
            newCond.clear();
            t.changed();
        });
        ui.header("Если не выполнено");
        ui.field("Текст игроку", "Над хотбаром, когда условие не выполнено. Пусто — молча", t.denyText, v -> {
            t.denyText = v.trim();
            t.changed();
        }).placeholder("&cДверь заперта").clearable().max(120);
        ui.field("Только роли", "id через запятую (пусто — всем)", UiUtil.join(t.roles), v -> {
            t.roles.clear();
            t.roles.addAll(UiUtil.splitList(v));
            t.changed();
        }).clearable().max(200);
    }

    // --- для кого ----------------------------------------------------------------------------------------

    private void who(Ui ui, final TileLogic t) {
        ui.header("Для кого выполнять действия");
        String[][] cards = {
                {TileLogic.WHO_SELF, "Тот, кто запустил", "minecraft:skull@3", "§9Локальный§7: нажал — ему сообщение, телепорт, награда"},
                {TileLogic.WHO_NEAR, "Все рядом", "minecraft:skull@2", "§9Локальный§7: все игроки в радиусе блока"},
                {TileLogic.WHO_WORLD, "Все в мире", "minecraft:nether_star", "§cГлобальный§7: все в этом мире и его копиях (локальные миры команд)"}};
        Ui.Grid g = ui.grid();
        for (final String[] c : cards) {
            ItemStack icon = UiUtil.icon(c[2]).copy();
            icon.setStackDisplayName((c[0].equals(t.who) ? "§a✔ " : "§f") + c[1]);
            g.cell(icon, c[0].equals(t.who), e -> {
                t.who = c[0];
                t.changed();
            }, c[3]);
        }
        if (TileLogic.WHO_NEAR.equals(t.who)) {
            radius(ui, t);
        }
        ui.note(t.global() ? Ui.RED : Ui.BLUE, t.global() ? "Глобальный — в режиме разработчика блок обведён красным."
                : "Локальный — в режиме разработчика блок обведён синим.");
    }

    // --- выход ------------------------------------------------------------------------------------------

    private void output(Ui ui, final TileLogic t) {
        ui.header("Сигнал редстоуна");
        Ui.Select s = ui.select("Длина сигнала", "Блок светится и даёт сигнал соседям", Integer.toString(t.pulseTicks), v -> {
            t.pulseTicks = Integer.parseInt(v);
            t.changed();
        });
        s.option("0", "Без сигнала");
        for (int[] o : new int[][]{{2, 0}, {10, 0}, {20, 1}, {40, 2}, {100, 5}, {200, 10}}) {
            s.option(Integer.toString(o[0]), o[1] == 0 ? o[0] + " тиков" : o[1] + " сек");
        }
        ui.header("Пауза между срабатываниями");
        Ui.Select c = ui.select("Пауза", "Чтобы не срабатывал слишком часто", Integer.toString(t.cooldown), v -> {
            t.cooldown = Integer.parseInt(v);
            t.changed();
        });
        for (int[] o : new int[][]{{0, 0}, {10, 0}, {20, 1}, {60, 3}, {100, 5}, {200, 10}, {600, 30}, {1200, 60}, {6000, 300}, {72000, 3600}}) {
            c.option(Integer.toString(o[0]), o[0] == 0 ? "без паузы" : o[1] == 0 ? o[0] + " тиков" : o[1] < 60 ? o[1] + " сек"
                    : o[1] < 3600 ? (o[1] / 60) + " мин" : "1 час");
        }
        ui.toggle("Пауза у каждого игрока своя", "Иначе один сработал — остальные ждут", t.cooldownPerPlayer, on -> {
            t.cooldownPerPlayer = on;
            t.changed();
        });
        ui.toggle("Искры при срабатывании", "Красные частицы над блоком", t.particles, on -> {
            t.particles = on;
            t.changed();
        });
    }
}
