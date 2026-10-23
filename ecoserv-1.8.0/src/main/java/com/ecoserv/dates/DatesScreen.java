package com.ecoserv.dates;

import com.ecoserv.config.ConfigHooks;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.ItemCodec;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.UUID;

/** Значимые даты и дни рождения (/ecoadmin dates): подарки, сообщения, проверка возраста. */
public class DatesScreen extends UiScreen {

    /** «» — список дат, «#birthday» — дни рождения, иначе id даты. */
    private String view = "";

    public DatesScreen(EntityPlayerMP player) {
        super(player);
    }

    public static void open(EntityPlayerMP player) {
        if (Perms.isAdmin(player)) {
            new DatesScreen(player).open();
        }
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected void build(Ui ui) {
        ui.title("Даты и дни рождения").icon(UiIcons.cake());
        ui.back(() -> com.ecoserv.subworld.menu.WorldsScreen.open(player, com.ecoserv.subworld.menu.WorldsScreen.Tab.ADMIN));
        ui.tab("Значимые даты", UiIcons.hourglass(), view.isEmpty(), () -> view = "");
        ui.tab("Дни рождения", UiIcons.cake(), "#birthday".equals(view), () -> view = "#birthday");
        for (final SpecialDate d : DatesManager.dates()) {
            ui.tab((d.enabled ? "&f" : "&7") + d.name, new ItemStack(d.enabled ? Items.PAPER : Items.MAP), d.id.equals(view),
                    () -> view = d.id);
        }
        ui.status("&7Даты: config/ecoserv/dates.json · из консоли: /ecoadmin dates");
        if ("#birthday".equals(view)) {
            buildBirthday(ui);
            return;
        }
        SpecialDate d = view.isEmpty() ? null : DatesManager.get(view);
        if (d != null) {
            buildDate(ui, d);
            return;
        }
        view = "";
        buildList(ui);
    }

    private void buildList(Ui ui) {
        ui.toggle("Значимые даты включены", "В такой день каждый игрок один раз за год получает подарок (при входе или "
                + "сразу, если он в сети)", EcoConfig.datesEnabled, on -> cfg("enabled", on ? "true" : "false"));
        List<SpecialDate> dates = DatesManager.dates();
        ui.header("Даты", Integer.toString(dates.size()));
        for (final SpecialDate d : dates) {
            ui.row(new ItemStack(d.enabled ? Items.PAPER : Items.MAP), (d.enabled ? "&e" : "&7") + d.name)
                    .line("&7" + d.dateText() + (d.days > 1 ? " (" + d.days + " дн.)" : "") + " · монет: &6"
                            + Fmt.money(d.money) + "&7 · предметов: " + d.items.size())
                    .chip(d.enabled ? "включена" : "выключена", d.enabled ? Ui.GREEN : Ui.GREY)
                    .onClick(() -> view = d.id)
                    .button("Настроить", Ui.BLUE, () -> view = d.id);
        }
        ui.field("Новая дата", "«дд.мм Название», например «09.05 День Победы»", "", v -> {
            String t = v.trim();
            if (t.isEmpty()) {
                return;
            }
            int space = t.indexOf(' ');
            String dm = space > 0 ? t.substring(0, space) : t;
            String name = space > 0 ? t.substring(space + 1) : "";
            SpecialDate d = DatesManager.create(dm, name);
            if (d == null) {
                throw new UiError("Не понял дату «" + dm + "». Формат: дд.мм");
            }
            view = d.id;
            toast("Дата создана: " + d.name + " (" + d.dateText() + ")");
        }).placeholder("31.12 Новый год").max(64);
    }

    private void buildDate(Ui ui, final SpecialDate d) {
        ui.sideLabel("&8Дата");
        ui.sideButton("Удалить дату", new ItemStack(Blocks.TNT), Ui.RED, () -> {
            DatesManager.remove(d.id);
            view = "";
            toast("Дата удалена.");
        }).confirm("Удалить дату «" + d.name + "»?");
        ui.toggle("Включена", "", d.enabled, on -> {
            d.enabled = on;
            DatesManager.changed();
        });
        ui.field("Название", "", d.name, v -> {
            if (v.trim().isEmpty()) {
                throw new UiError("Название не может быть пустым.");
            }
            d.name = v.trim();
            DatesManager.changed();
        }).max(64);
        ui.field("Дата", "дд.мм", d.dateText(), v -> {
            int[] dm = DatesManager.parseDayMonth(v);
            if (dm == null) {
                throw new UiError("Не понял дату, формат дд.мм");
            }
            d.day = dm[0];
            d.month = dm[1];
            DatesManager.changed();
        }).max(10);
        ui.number("Длится дней", "Подарок можно получить в любой из этих дней", d.days, 1, 366, v -> {
            d.days = (int) v;
            DatesManager.changed();
        });
        ui.money("Монет в подарок", "", d.money, v -> {
            d.money = v;
            DatesManager.changed();
        });
        ui.header("Предметы в подарок", Integer.toString(d.items.size()));
        List<ItemStack> items = ItemCodec.decodeAll(d.items);
        if (!items.isEmpty()) {
            Ui.Grid g = ui.grid();
            for (int i = 0; i < items.size(); i++) {
                final int index = i;
                g.cell(items.get(i), false, e -> {
                    if (e.right() && index < d.items.size()) {
                        d.items.remove(index);
                        DatesManager.changed();
                    }
                }, "&8ПКМ — убрать");
            }
        }
        Ui.Buttons b = ui.buttons();
        b.add("Добавить из руки", Ui.BLUE, () -> {
            ItemStack held = player.getHeldItemMainhand();
            if (held.isEmpty()) {
                throw new UiError("Возьми предмет в руку (количество тоже учитывается).");
            }
            d.items.add(ItemCodec.encode(held.copy()));
            DatesManager.changed();
            toast("Добавлено: " + held.getDisplayName() + " x" + held.getCount());
        });
        b.add("Очистить", Ui.GREY, () -> {
            d.items.clear();
            DatesManager.changed();
        }).disabled(d.items.isEmpty());
        ui.field("Сообщение игроку", "%player% — ник, %reward% — подарок, & — цвета. Пусто — стандартное", d.message, v -> {
            d.message = v;
            DatesManager.changed();
        }).placeholder("стандартное").clearable().max(256);
        ui.toggle("Объявлять в чат", "Всем — что игрок получил подарок", d.broadcast, on -> {
            d.broadcast = on;
            DatesManager.changed();
        });
    }

    private void buildBirthday(Ui ui) {
        ui.note(Ui.BLUE, "Игрок вводит свою дату: &f/birthday дд.мм.гггг&f. В этот день он получает подарок, а по дате "
                + "считается возраст для подмиров, порталов, зон и квестов. Админ: /birthday set|clear|info|check|list.");
        ui.toggle("Ввод дня рождения", "Подарок в день рождения и проверка возраста", EcoConfig.birthdaysEnabled,
                on -> cfg("birthdays_enabled", on ? "true" : "false"));
        ui.toggle("Игрок может сам изменить дату", "", EcoConfig.birthdayPlayersCanChange,
                on -> cfg("players_can_change", on ? "true" : "false"));
        ui.money("Монет в подарок", "", EcoConfig.birthdayMoney, v -> cfg("birthday_money", Long.toString(v)));
        ui.toggle("Объявлять в чат", "«Сегодня день рождения у …» (без возраста)", EcoConfig.birthdayBroadcast,
                on -> cfg("birthday_broadcast", on ? "true" : "false"));
        ui.field("Поздравление", "%player% — ник, %reward% — подарок, & — цвета", EcoConfig.birthdayMessage,
                v -> cfg("birthday_message", v)).max(256);
        List<ItemStack> items = DatesManager.birthdayItems();
        ui.header("Предметы в подарок", Integer.toString(items.size()));
        if (!items.isEmpty()) {
            Ui.Grid g = ui.grid();
            for (ItemStack s : items) {
                g.cell(s, false, null);
            }
        }
        Ui.Buttons b = ui.buttons();
        b.add("Добавить из руки", Ui.BLUE, () -> {
            ItemStack held = player.getHeldItemMainhand();
            if (held.isEmpty()) {
                throw new UiError("Возьми предмет в руку.");
            }
            DatesManager.addBirthdayItem(held.copy());
            toast("Добавлено: " + held.getDisplayName() + " x" + held.getCount());
        });
        b.add("Очистить", Ui.GREY, DatesManager::clearBirthdayItems).disabled(items.isEmpty());
        ui.header("Проверка возраста");
        ui.field("Проверить игрока", "«ник фильтр», например «Steve >=16» (18 — ровно, >=16, <=12, 14-17)", "", v -> {
            String[] parts = v.trim().split("\\s+");
            if (parts.length < 2) {
                throw new UiError("Нужно: ник и фильтр.");
            }
            UUID id = PlayerUtil.uuidOf(parts[0]);
            AgeFilter filter = AgeFilter.parse(parts[1]);
            if (id == null) {
                throw new UiError("Игрок не найден.");
            }
            if (filter == null) {
                throw new UiError("Не понял фильтр.");
            }
            int age = DatesManager.age(id);
            toast(age < 0 ? parts[0] + ": день рождения не указан — не проходит."
                    : parts[0] + ": " + age + " лет, «" + filter.describe() + "» — " + (filter.matches(age) ? "проходит" : "не проходит"));
        }).placeholder("Steve >=16").max(48);
    }

    private void cfg(String key, String value) {
        String error = EcoConfig.setChecked(EcoConfig.CAT_DATES, key, value);
        if (error != null) {
            throw new UiError("Не записано: " + error);
        }
        ConfigHooks.apply(EcoConfig.CAT_DATES);
    }
}
