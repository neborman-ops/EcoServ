package com.ecoserv.dates;

import com.ecoserv.config.ConfigHooks;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.menu.ChatPrompt;
import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.ItemCodec;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Меню значимых дат и дней рождения (/ecoadmin dates). */
public class DatesMenu extends ChestMenu {

    private enum View {
        LIST, DATE, BIRTHDAY
    }

    private static final int SLOT_BACK = 45;
    private static final int SLOT_CREATE = 47;
    private static final int SLOT_HELP = 49;
    private static final int SLOT_DELETE = 51;
    private static final int SLOT_CLOSE = 53;

    private final View view;
    @Nullable
    private final String dateId;

    private DatesMenu(EntityPlayerMP player, View view, @Nullable String dateId) {
        super(player, view == View.BIRTHDAY ? "Дни рождения" : view == View.DATE ? "Дата" : "Значимые даты", 6);
        this.view = view;
        this.dateId = dateId;
    }

    public static void open(EntityPlayerMP player) {
        if (Perms.isAdmin(player)) {
            new DatesMenu(player, View.LIST, null).open();
        }
    }

    private static void reopen(EntityPlayerMP player, View view, @Nullable String id) {
        new DatesMenu(player, view, id).open();
    }

    @Nullable
    private SpecialDate date() {
        return dateId == null ? null : DatesManager.get(dateId);
    }

    @Override
    protected void buildButtons() {
        for (int i = 0; i < topSize; i++) {
            setButton(i, ItemStack.EMPTY);
        }
        fillRow(5, Icons.filler());
        switch (view) {
            case DATE:
                buildDate();
                break;
            case BIRTHDAY:
                buildBirthday();
                break;
            default:
                buildList();
        }
        setButton(SLOT_CLOSE, Icons.close());
    }

    private void buildList() {
        setButton(0, Icons.of(Item.getItemFromBlock(Blocks.CAKE), "&dДни рождения",
                "&7Ввод дня рождения: " + (EcoConfig.birthdaysEnabled ? "&aвключён" : "&cвыключен"),
                "&7Подарок: &e" + Fmt.money(EcoConfig.birthdayMoney) + " &7+ предметов: &f" + DatesManager.birthdayItems().size(),
                "&7Проверка возраста — в подмирах и квестах",
                "",
                "&8ЛКМ — настроить"));
        setButton(1, Icons.of(Items.CLOCK, "&6Значимые даты: " + (EcoConfig.datesEnabled ? "&aвключены" : "&cвыключены"),
                "&7В этот день каждый игрок один раз",
                "&7за год получает подарок.",
                "",
                "&8ЛКМ — включить/выключить"));
        List<SpecialDate> dates = DatesManager.dates();
        for (int i = 0; i < dates.size() && i < 36; i++) {
            SpecialDate d = dates.get(i);
            ItemStack icon = Icons.of(d.enabled ? Items.PAPER : Items.MAP, (d.enabled ? "&e" : "&7") + d.name,
                    "&7Дата: &f" + d.dateText() + (d.days > 1 ? " &7(" + d.days + " дн.)" : ""),
                    "&7Монет: &e" + Fmt.money(d.money) + " &7| предметов: &f" + d.items.size(),
                    d.enabled ? "&aвключена" : "&cвыключена",
                    "",
                    "&8ЛКМ — изменить");
            setButton(9 + i, icon);
        }
        setButton(SLOT_CREATE, Icons.green("&aНовая дата", "&7Введёшь в чат: «дд.мм Название»"));
        setButton(SLOT_HELP, Icons.of(Items.BOOK, "&6Подсказка",
                "&7Подарок выдаётся при входе в игру",
                "&7(или сразу, если игрок в сети).",
                "&7Даты: config/ecoserv/dates.json",
                "&7Из консоли: /ecoadmin dates ..."));
    }

    private void buildDate() {
        SpecialDate d = date();
        if (d == null) {
            setButton(22, Icons.red("&cДата удалена"));
            setButton(SLOT_BACK, Icons.back());
            return;
        }
        setButton(4, Icons.of(Items.PAPER, "&e" + d.name, "&7id: &f" + d.id, "&7" + d.dateText()
                + (d.days > 1 ? ", " + d.days + " дн." : "")));
        setButton(10, Icons.of(Items.NAME_TAG, "&fНазвание: &e" + d.name, "&8ЛКМ — изменить"));
        setButton(12, Icons.of(Items.CLOCK, "&fДата: &e" + d.dateText(), "&8ЛКМ — изменить (дд.мм)"));
        setButton(14, Icons.of(Items.MAP, "&fДлится дней: &e" + d.days,
                "&7Подарок можно получить в любой из этих дней", "&8ЛКМ — изменить"));
        setButton(16, Icons.of(Items.DYE, d.enabled ? 10 : 8, d.enabled ? "&aВключена" : "&cВыключена", "&8ЛКМ — переключить"));
        setButton(28, Icons.of(Items.GOLD_INGOT, "&fМонет: &e" + Fmt.money(d.money), "&8ЛКМ — изменить"));
        setButton(30, Icons.of(Item.getItemFromBlock(Blocks.CHEST), "&fПредметы: &e" + d.items.size(),
                "&7" + ItemCodec.describe(ItemCodec.decodeAll(d.items), 4),
                "",
                "&8ЛКМ — добавить предмет из руки",
                "&8Shift+ЛКМ — очистить"));
        setButton(32, Icons.of(Items.WRITABLE_BOOK, "&fСообщение игроку",
                "&7" + (d.message.isEmpty() ? "(стандартное)" : d.message.length() > 40 ? d.message.substring(0, 39) + "…" : d.message),
                "&7%player% — ник, %reward% — подарок",
                "&8ЛКМ — изменить, Shift+ЛКМ — стандартное"));
        setButton(34, Icons.of(Items.DYE, d.broadcast ? 10 : 8, "&fОбъявлять в чат: " + (d.broadcast ? "&aда" : "&cнет"),
                "&8ЛКМ — переключить"));
        setButton(SLOT_BACK, Icons.back());
        setButton(SLOT_DELETE, Icons.of(Item.getItemFromBlock(Blocks.TNT), "&cУдалить дату", "&7Shift+ЛКМ"));
    }

    private void buildBirthday() {
        setButton(4, Icons.of(Item.getItemFromBlock(Blocks.CAKE), "&dДни рождения",
                "&7Игрок вводит: &f/birthday дд.мм.гггг",
                "&7Админ: &f/birthday set|clear|info|check|list"));
        setButton(11, Icons.of(Items.DYE, EcoConfig.birthdaysEnabled ? 10 : 8,
                "&fВвод дня рождения: " + (EcoConfig.birthdaysEnabled ? "&aвключён" : "&cвыключен"),
                "&7Когда включён: подарок в день рождения",
                "&7и проверка возраста.", "&8ЛКМ — переключить"));
        setButton(13, Icons.of(Items.GOLD_INGOT, "&fМонет в подарок: &e" + Fmt.money(EcoConfig.birthdayMoney), "&8ЛКМ — изменить"));
        List<ItemStack> items = DatesManager.birthdayItems();
        setButton(15, Icons.of(Item.getItemFromBlock(Blocks.CHEST), "&fПредметы в подарок: &e" + items.size(),
                "&7" + ItemCodec.describe(items, 4),
                "",
                "&8ЛКМ — добавить предмет из руки",
                "&8Shift+ЛКМ — очистить"));
        setButton(29, Icons.of(Items.DYE, EcoConfig.birthdayBroadcast ? 10 : 8,
                "&fОбъявлять в чат: " + (EcoConfig.birthdayBroadcast ? "&aда" : "&cнет"),
                "&7«Сегодня день рождения у …» (без возраста)", "&8ЛКМ — переключить"));
        setButton(31, Icons.of(Items.WRITABLE_BOOK, "&fПоздравление", "&7" + EcoConfig.birthdayMessage,
                "&8ЛКМ — изменить"));
        setButton(33, Icons.of(Items.COMPASS, "&fПроверка возраста",
                "&7Фильтр: число лет и сравнение:",
                "&f18&7 — ровно, &f>=16&7, &f<=12&7, &f14-17",
                "&7Используется: доступ к подмирам,",
                "&7условие «Возраст» в квестах.",
                "",
                "&8ЛКМ — проверить игрока"));
        setButton(35, Icons.of(Items.DYE, EcoConfig.birthdayPlayersCanChange ? 10 : 8,
                "&fИгрок может сам изменить дату: " + (EcoConfig.birthdayPlayersCanChange ? "&aда" : "&cнет"),
                "&8ЛКМ — переключить"));
        setButton(SLOT_BACK, Icons.back());
    }

    // --- клики ------------------------------------------------------------------

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
        if (!Perms.isAdmin(player)) {
            player.closeScreen();
            return;
        }
        boolean shift = type == ClickType.QUICK_MOVE;
        if (slot == SLOT_CLOSE) {
            player.closeScreen();
            return;
        }
        if (slot == SLOT_BACK && view != View.LIST) {
            reopen(player, View.LIST, null);
            return;
        }
        switch (view) {
            case DATE:
                clickDate(slot, shift);
                break;
            case BIRTHDAY:
                clickBirthday(slot, shift);
                break;
            default:
                clickList(slot);
        }
    }

    private void clickList(int slot) {
        if (slot == 0) {
            reopen(player, View.BIRTHDAY, null);
            return;
        }
        if (slot == 1) {
            setCfg("enabled", EcoConfig.datesEnabled ? "false" : "true");
            return;
        }
        if (slot == SLOT_CREATE) {
            ChatPrompt.ask(player, "&eНовая дата: &fдд.мм Название &7(например «09.05 День Победы»)", new ChatPrompt.Callback() {
                @Override
                public void accept(EntityPlayerMP who, String text) {
                    String t = text.trim();
                    int space = t.indexOf(' ');
                    String dm = space > 0 ? t.substring(0, space) : t;
                    String name = space > 0 ? t.substring(space + 1) : "";
                    SpecialDate d = DatesManager.create(dm, name);
                    if (d == null) {
                        ChatUtil.send(who, "&cНе понял дату &f" + dm + "&c. Формат: дд.мм");
                        reopen(who, View.LIST, null);
                        return;
                    }
                    ChatUtil.send(who, "&aДата создана: &f" + d.name + " (" + d.dateText() + ")");
                    reopen(who, View.DATE, d.id);
                }
            });
            return;
        }
        List<SpecialDate> dates = DatesManager.dates();
        int index = slot - 9;
        if (index >= 0 && index < dates.size() && index < 36) {
            reopen(player, View.DATE, dates.get(index).id);
        }
    }

    private void clickDate(int slot, boolean shift) {
        final SpecialDate d = date();
        if (d == null) {
            return;
        }
        final String id = d.id;
        switch (slot) {
            case 10:
                askText("&eНовое название для &f" + d.name + "&e:", new Setter() {
                    @Override
                    public String set(SpecialDate date, String text) {
                        date.name = text.trim();
                        return null;
                    }
                });
                return;
            case 12:
                askText("&eНовая дата (дд.мм) для &f" + d.name + "&e:", new Setter() {
                    @Override
                    public String set(SpecialDate date, String text) {
                        int[] dm = DatesManager.parseDayMonth(text);
                        if (dm == null) {
                            return "не понял дату, формат дд.мм";
                        }
                        date.day = dm[0];
                        date.month = dm[1];
                        return null;
                    }
                });
                return;
            case 14:
                askText("&eСколько дней длится &f" + d.name + "&e (1–366):", new Setter() {
                    @Override
                    public String set(SpecialDate date, String text) {
                        try {
                            date.days = Math.max(1, Math.min(366, Integer.parseInt(text.trim())));
                            return null;
                        } catch (NumberFormatException e) {
                            return "нужно число";
                        }
                    }
                });
                return;
            case 16:
                d.enabled = !d.enabled;
                DatesManager.changed();
                return;
            case 28:
                askText("&eСколько монет дарить в «" + d.name + "»:", new Setter() {
                    @Override
                    public String set(SpecialDate date, String text) {
                        try {
                            date.money = Math.max(0L, Long.parseLong(text.trim().replace(" ", "").replace("_", "")));
                            return null;
                        } catch (NumberFormatException e) {
                            return "нужно целое число";
                        }
                    }
                });
                return;
            case 30:
                if (shift) {
                    d.items.clear();
                } else {
                    ItemStack held = player.getHeldItemMainhand();
                    if (held.isEmpty()) {
                        ChatUtil.send(player, "&cВозьми предмет в руку (количество тоже учитывается).");
                        return;
                    }
                    d.items.add(ItemCodec.encode(held.copy()));
                    ChatUtil.send(player, "&aДобавлено: &f" + held.getDisplayName() + " x" + held.getCount());
                }
                DatesManager.changed();
                return;
            case 32:
                if (shift) {
                    d.message = "";
                    DatesManager.changed();
                    return;
                }
                askText("&eСообщение игроку (%player% — ник, %reward% — подарок, & — цвета):", new Setter() {
                    @Override
                    public String set(SpecialDate date, String text) {
                        date.message = text;
                        return null;
                    }
                });
                return;
            case 34:
                d.broadcast = !d.broadcast;
                DatesManager.changed();
                return;
            case SLOT_DELETE:
                if (shift) {
                    DatesManager.remove(id);
                    ChatUtil.send(player, "&aДата &f" + d.name + " &aудалена.");
                    reopen(player, View.LIST, null);
                }
                return;
            default:
        }
    }

    /** Изменение даты из ввода в чат; возвращает текст ошибки или null. */
    private interface Setter {
        @Nullable
        String set(SpecialDate date, String text);
    }

    private void askText(String question, final Setter setter) {
        final String id = dateId;
        ChatPrompt.ask(player, question, new ChatPrompt.Callback() {
            @Override
            public void accept(EntityPlayerMP who, String text) {
                SpecialDate d = DatesManager.get(id);
                if (d == null || !Perms.isAdmin(who)) {
                    return;
                }
                String error = setter.set(d, text);
                if (error != null) {
                    ChatUtil.send(who, "&c" + error);
                } else {
                    DatesManager.changed();
                    ChatUtil.send(who, "&aСохранено.");
                }
                reopen(who, View.DATE, id);
            }
        });
    }

    private void clickBirthday(int slot, boolean shift) {
        switch (slot) {
            case 11:
                setCfg("birthdays_enabled", EcoConfig.birthdaysEnabled ? "false" : "true");
                return;
            case 13:
                ChatPrompt.ask(player, "&eСколько монет дарить на день рождения:", new ChatPrompt.Callback() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        report(who, EcoConfig.setChecked(EcoConfig.CAT_DATES, "birthday_money", text));
                        reopen(who, View.BIRTHDAY, null);
                    }
                });
                return;
            case 15:
                if (shift) {
                    DatesManager.clearBirthdayItems();
                } else {
                    ItemStack held = player.getHeldItemMainhand();
                    if (held.isEmpty()) {
                        ChatUtil.send(player, "&cВозьми предмет в руку.");
                        return;
                    }
                    DatesManager.addBirthdayItem(held.copy());
                    ChatUtil.send(player, "&aДобавлено: &f" + held.getDisplayName() + " x" + held.getCount());
                }
                return;
            case 29:
                setCfg("birthday_broadcast", EcoConfig.birthdayBroadcast ? "false" : "true");
                return;
            case 31:
                ChatPrompt.ask(player, "&eПоздравление (%player% — ник, %reward% — подарок, & — цвета):", new ChatPrompt.Callback() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        report(who, EcoConfig.setChecked(EcoConfig.CAT_DATES, "birthday_message", text));
                        reopen(who, View.BIRTHDAY, null);
                    }
                });
                return;
            case 33:
                ChatPrompt.ask(player, "&eПроверка возраста: &fник фильтр &7(например «Steve >=16»)", new ChatPrompt.Callback() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        String[] parts = text.trim().split("\\s+");
                        if (parts.length < 2) {
                            ChatUtil.send(who, "&cНужно: ник и фильтр.");
                            return;
                        }
                        UUID id = PlayerUtil.uuidOf(parts[0]);
                        AgeFilter filter = AgeFilter.parse(parts[1]);
                        if (id == null || filter == null) {
                            ChatUtil.send(who, id == null ? "&cИгрок не найден." : "&cНе понял фильтр.");
                            return;
                        }
                        int age = DatesManager.age(id);
                        ChatUtil.send(who, age < 0 ? "&7У &f" + parts[0] + " &7день рождения не указан — &cне проходит."
                                : "&7" + parts[0] + ": " + age + " лет, «" + filter.describe() + "» — "
                                + (filter.matches(age) ? "&aпроходит" : "&cне проходит"));
                    }
                });
                return;
            case 35:
                setCfg("players_can_change", EcoConfig.birthdayPlayersCanChange ? "false" : "true");
                return;
            default:
        }
    }

    private void setCfg(String key, String value) {
        report(player, EcoConfig.setChecked(EcoConfig.CAT_DATES, key, value));
    }

    private static void report(EntityPlayerMP who, @Nullable String error) {
        if (error == null) {
            ConfigHooks.apply(EcoConfig.CAT_DATES);
        } else {
            ChatUtil.send(who, "&cНе записано: " + error);
        }
    }
}
