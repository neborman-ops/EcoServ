package com.ecoserv.bank;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Окно банка (2.0): кошелёк во всех валютах, обмен, счета (личные и общие), админ — валюты и настройки. */
public class BankScreen extends UiScreen {

    private enum Tab {
        WALLET, ACCOUNTS, EXCHANGE, CURRENCIES, SETTINGS
    }

    private Tab tab = Tab.WALLET;
    @Nullable
    private String account;
    @Nullable
    private String editCurrency;
    /** Банкомат, через который открыто окно (null — команда /bank). */
    @Nullable
    private final BlockPos atm;

    // ввод
    private String cur = Currency.MAIN;
    private long amount;
    private String exFrom = Currency.MAIN;
    private String exTo = "gems";
    private long exAmount;
    private String toCode = "";
    private String payTo = "";

    public BankScreen(EntityPlayerMP player, @Nullable BlockPos atm) {
        super(player);
        this.atm = atm;
    }

    public static void open(EntityPlayerMP player, @Nullable BlockPos atm) {
        new BankScreen(player, atm).open();
    }

    private boolean admin() {
        return Perms.isAdmin(player);
    }

    /** Операции со счетами: нужен банкомат, если так настроено. */
    private boolean atmOk() {
        return admin() || !BankManager.settings().needAtm || (atm != null && player.getDistanceSq(atm) < 64.0D);
    }

    @Override
    protected void build(Ui ui) {
        // валюту могли удалить — вернуться к основной
        if (BankManager.currency(cur) == null) {
            cur = Currency.MAIN;
        }
        if (BankManager.currency(exFrom) == null) {
            exFrom = Currency.MAIN;
        }
        if (BankManager.currency(exTo) == null) {
            exTo = Currency.MAIN;
        }
        if (account != null) {
            BankAccount a = BankManager.account(account);
            if (a != null && (a.level(player.getUniqueID().toString()) > 0 || admin())) {
                buildAccount(ui, a);
                return;
            }
            account = null;
        }
        if (editCurrency != null && admin()) {
            Currency c = BankManager.currency(editCurrency);
            if (c != null) {
                buildCurrency(ui, c);
                return;
            }
            editCurrency = null;
        }
        ui.title(atm != null ? "Банкомат" : "Банк").icon(UiIcons.bank());
        ui.subtitle("&7Баланс: " + BankManager.main().format(BankManager.balance(player.getUniqueID(), Currency.MAIN)));
        ui.tab("Кошелёк", UiIcons.money(), tab == Tab.WALLET, () -> tab = Tab.WALLET);
        ui.tab("Счета", UiIcons.bank(), tab == Tab.ACCOUNTS, () -> tab = Tab.ACCOUNTS);
        ui.tab("Обмен", UiIcons.trade(), tab == Tab.EXCHANGE, () -> tab = Tab.EXCHANGE);
        if (admin()) {
            ui.sideLabel("&8Админ");
            ui.tab("Валюты", new ItemStack(Items.DIAMOND), tab == Tab.CURRENCIES, () -> tab = Tab.CURRENCIES);
            ui.tab("Настройки банка", UiIcons.gear(), tab == Tab.SETTINGS, () -> tab = Tab.SETTINGS);
        }
        if (!BankManager.settings().enabled) {
            ui.note(Ui.YELLOW, admin() ? "Банк выключен (видишь, потому что админ)." : "Банк выключен на сервере.");
        }
        switch (tab) {
            case ACCOUNTS:
                buildAccounts(ui);
                break;
            case EXCHANGE:
                buildExchange(ui);
                break;
            case CURRENCIES:
                if (admin()) {
                    buildCurrencies(ui);
                    break;
                }
                // fallthrough
            case SETTINGS:
                if (admin()) {
                    buildSettings(ui);
                    break;
                }
                // fallthrough
            default:
                buildWallet(ui);
        }
    }

    private void currencySelect(Ui ui, String label, String value, boolean bankOnly, Ui.Text set) {
        Ui.Select s = ui.select(label, "", value, set);
        for (Currency c : BankManager.enabledCurrencies()) {
            if (bankOnly && !c.main() && !c.bankable) {
                continue;
            }
            s.option(c.id, c.name, c.description, UiUtil.icon(c.icon));
        }
    }

    // --- кошелёк ------------------------------------------------------------------------------------------

    private void buildWallet(Ui ui) {
        ui.header("Кошелёк", "&8наличные");
        for (Currency c : BankManager.enabledCurrencies()) {
            long v = BankManager.balance(player.getUniqueID(), c.id);
            Ui.Row r = ui.row(UiUtil.icon(c.icon), c.color + c.name).line(c.format(v));
            if (!c.description.isEmpty()) {
                r.line("&8" + c.description);
            }
            if (!c.main() && c.rate > 0.0D && c.exchangeable) {
                r.chip("1 = " + Fmt.money((long) Math.round(c.rate)), Ui.GREY);
            }
            if (!c.transferable && !c.main()) {
                r.chip("не переводится", Ui.GREY);
            }
        }
        ui.header("Перевести игроку");
        currencySelect(ui, "Валюта", cur, false, v -> cur = v);
        ui.money("Сумма", "", amount, v -> amount = v);
        ui.field("Кому", "Ник игрока (должен хоть раз зайти)", payTo, v -> payTo = v.trim()).picker(Ui.PICK_PLAYER).max(32);
        ui.buttons().add("Перевести", Ui.GREEN, () -> {
            UUID to = PlayerUtil.uuidOf(payTo);
            if (to == null) {
                throw new UiError("Игрок «" + payTo + "» не найден.");
            }
            String err = BankManager.pay(player, to, cur, amount);
            if (err != null) {
                throw new UiError(UiUtil.plain(err));
            }
            toast("Переведено: " + UiUtil.plain(BankManager.currency(cur).format(amount)) + " → " + payTo);
            amount = 0;
        });
    }

    // --- обмен -------------------------------------------------------------------------------------------

    private void buildExchange(Ui ui) {
        ui.header("Обмен валют", "&8комиссия " + BankManager.settings().exchangeFee + "%");
        ui.text("&7Курс задаёт админ: сколько монет стоит 1 единица валюты. Ивент-валюты могут не меняться.");
        for (Currency c : BankManager.enabledCurrencies()) {
            if (c.main()) {
                continue;
            }
            ui.row(UiUtil.icon(c.icon), c.color + c.name)
                    .line(c.exchangeable && c.rate > 0.0D ? "&71 " + c.name + " = &6" + Fmt.money((long) Math.round(c.rate))
                            : "&8не меняется");
        }
        currencySelect(ui, "Отдаю", exFrom, false, v -> exFrom = v);
        currencySelect(ui, "Получаю", exTo, false, v -> exTo = v);
        ui.money("Сколько отдаю", "", exAmount, v -> exAmount = v);
        long q = BankManager.quote(exFrom, exTo, exAmount);
        Currency to = BankManager.currency(exTo);
        if (exAmount > 0L) {
            ui.note(q > 0L ? Ui.BLUE : Ui.YELLOW, q > 0L && to != null ? "Получишь: " + to.format(q)
                    : "Эти валюты не меняются друг на друга (или сумма слишком мала).");
        }
        ui.buttons().add("Обменять", Ui.GREEN, () -> {
            String err = BankManager.exchange(player, exFrom, exTo, exAmount);
            if (err != null) {
                throw new UiError(UiUtil.plain(err));
            }
            toast("Обмен выполнен.");
        }).disabled(q <= 0L);
    }

    // --- счета ---------------------------------------------------------------------------------------------

    private void buildAccounts(Ui ui) {
        List<BankAccount> mine = BankManager.accountsOf(player.getUniqueID());
        ui.header("Мои счета", mine.size() + "");
        if (mine.isEmpty()) {
            ui.text("&7Счёт — это сейф для денег: деньги на нём не потратятся случайно, на них может капать процент,"
                    + " а общий счёт можно вести вместе с командой.");
        }
        for (final BankAccount a : mine) {
            Ui.Row r = ui.row(new ItemStack(a.shared ? Blocks.ENDER_CHEST : Blocks.CHEST), "&e" + a.name)
                    .line("&7код &f" + a.code + (a.shared ? " &8· общий, владелец " + a.ownerName : ""));
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, Long> e : a.balances.entrySet()) {
                Currency c = BankManager.currency(e.getKey());
                if (c != null && e.getValue() != 0L) {
                    b.append(b.length() > 0 ? "&7, " : "").append(c.format(e.getValue()));
                }
            }
            r.line(b.length() == 0 ? "&8пусто" : b.toString());
            if (a.frozen) {
                r.chip("заморожен", Ui.RED);
            }
            r.chip(BankAccount.rightTitle(a.level(player.getUniqueID().toString()) >= 4 ? BankAccount.MANAGE
                    : a.members.get(player.getUniqueID().toString())), Ui.GREY);
            r.onClick(() -> account = a.code);
            r.button("Открыть", Ui.BLUE, () -> account = a.code);
        }
        BankSettings s = BankManager.settings();
        int owned = BankManager.ownedBy(player.getUniqueID());
        ui.header("Открыть счёт", admin() ? "&8без ограничений" : owned + " / " + s.maxAccounts);
        if (owned > 0 && s.accountPrice > 0) {
            ui.text("&7Первый счёт бесплатно, следующий — &6" + Fmt.money(s.accountPrice));
        }
        if (s.interestPercent > 0) {
            ui.text("&7Проценты: &b" + s.interestPercent + "% в сутки&7 на монеты"
                    + (s.interestCap > 0 ? " (до " + Fmt.money(s.interestCap) + ")" : "")
                    + (s.interestOnlyOnline ? ", если владелец заходил на сервер" : "") + ".");
        }
        ui.field("Личный счёт", "Название — Enter, чтобы открыть", "", v -> {
            BankAccount a = BankManager.open(player, v, false);
            account = a.code;
            toast("Счёт открыт, код " + a.code);
        }).placeholder("Сбережения").max(32);
        ui.field("Общий счёт", "Для команды, клана, компании — Enter, чтобы открыть", "", v -> {
            BankAccount a = BankManager.open(player, v, true);
            account = a.code;
            toast("Общий счёт открыт, код " + a.code + ". Добавь участников.");
        }).placeholder("Казна клана").max(32);
        if (admin()) {
            List<BankAccount> all = BankManager.allAccounts();
            ui.header("Все счета (админ)", all.size() + "");
            ui.search("поиск по названию, коду, владельцу");
            for (final BankAccount a : all) {
                ui.row(new ItemStack(Blocks.CHEST), "&f" + a.name + " &8" + a.code)
                        .line("&7" + a.ownerName + " · " + BankManager.main().format(a.balance(Currency.MAIN)))
                        .onClick(() -> account = a.code);
            }
        }
    }

    private void buildAccount(Ui ui, final BankAccount a) {
        final String me = player.getUniqueID().toString();
        int level = admin() ? 4 : a.level(me);
        ui.back(() -> account = null);
        ui.title(a.name).subtitle("&7код &f" + a.code + " &8· владелец " + a.ownerName).icon(new ItemStack(a.shared ? Blocks.ENDER_CHEST : Blocks.CHEST));
        if (a.frozen) {
            ui.note(Ui.RED, "Счёт заморожен администрацией: операции недоступны.");
        }
        if (!atmOk()) {
            ui.note(Ui.YELLOW, "Операции со счётом — только у банкомата (так настроено на сервере).");
        }
        ui.header("На счёте");
        boolean any = false;
        for (Currency c : BankManager.enabledCurrencies()) {
            long v = a.balance(c.id);
            if (v != 0L) {
                any = true;
                ui.row(UiUtil.icon(c.icon), c.color + c.name).line(c.format(v));
            }
        }
        if (!any) {
            ui.text("&8Пока пусто.");
        }
        if (a.interestTotal > 0) {
            ui.text("&7Проценты за всё время: " + BankManager.main().format(a.interestTotal));
        }
        if (level >= 2 && atmOk()) {
            ui.header("Внести / снять");
            currencySelect(ui, "Валюта", cur, true, v -> cur = v);
            ui.money("Сумма", "В кошельке: " + BankManager.currency(cur).format(BankManager.balance(player.getUniqueID(), cur)),
                    amount, v -> amount = v);
            Ui.Buttons bs = ui.buttons();
            bs.add("Внести", Ui.GREEN, () -> {
                BankManager.deposit(player, a, cur, amount);
                toast("Внесено.");
            });
            if (level >= 3) {
                bs.add("Снять", Ui.YELLOW, () -> {
                    BankManager.withdraw(player, a, cur, amount);
                    toast("Снято.");
                });
                bs.add("Внести всё", Ui.GREY, () -> {
                    long all = BankManager.balance(player.getUniqueID(), cur);
                    BankManager.deposit(player, a, cur, all);
                    toast("Внесено всё.");
                });
            }
            if (level >= 3) {
                ui.header("Перевод на другой счёт", BankManager.settings().transferFee > 0 ? "&8комиссия " + BankManager.settings().transferFee + "%" : "");
                ui.field("Код счёта", "6 символов, например AB12CD", toCode, v -> toCode = v.trim()).max(8);
                ui.buttons().add("Перевести сумму выше", Ui.BLUE, () -> {
                    BankManager.transfer(player, a, toCode, cur, amount);
                    toast("Переведено на " + toCode.toUpperCase(java.util.Locale.ROOT) + ".");
                });
            }
        }
        if (a.shared || level >= 4) {
            ui.header("Участники", a.members.size() + "");
            ui.row(UiIcons.profile(), "&f" + a.ownerName).line("&7владелец");
            for (final Map.Entry<String, String> m : a.members.entrySet()) {
                final String name = a.memberNames.containsKey(m.getKey()) ? a.memberNames.get(m.getKey()) : m.getKey();
                Ui.Row r = ui.row(UiIcons.profile(), "&f" + name).line("&7" + BankAccount.rightTitle(m.getValue()));
                if (level >= 4) {
                    final UUID who = UUID.fromString(m.getKey());
                    r.button("Права", Ui.GREY, () -> {
                        String next = nextRight(m.getValue());
                        BankManager.setMember(player, a, who, name, next);
                    }).tip("Сменить право по кругу: смотрит → кладёт → снимает → управляет");
                    r.button("✕", Ui.RED, () -> BankManager.setMember(player, a, who, name, null))
                            .confirm("Убрать " + name + " со счёта?");
                }
            }
            if (level >= 4) {
                ui.field("Добавить участника", "Ник — сможет класть деньги (права меняются кнопкой «Права»)", "", v -> {
                    UUID who = PlayerUtil.uuidOf(v.trim());
                    if (who == null) {
                        throw new UiError("Игрок «" + v.trim() + "» не найден.");
                    }
                    BankManager.setMember(player, a, who, PlayerUtil.nameOf(who), BankAccount.DEPOSIT);
                    toast("Добавлен: " + v.trim());
                }).picker(Ui.PICK_PLAYER).max(32);
            }
        }
        if (level >= 4) {
            ui.header("Настройки счёта");
            ui.field("Название", "", a.name, v -> {
                if (!v.trim().isEmpty()) {
                    a.name = UiUtil.cut(v.trim(), 32);
                    BankManager.saveAll();
                }
            }).max(32);
            if (admin()) {
                ui.toggle("Заморожен", "Никто, кроме админов, не может снимать и класть", a.frozen, on -> {
                    a.frozen = on;
                    BankManager.saveAll();
                });
            }
            ui.buttons().add("Закрыть счёт", Ui.RED, () -> {
                BankManager.close(player, a);
                account = null;
                toast("Счёт закрыт, деньги — в кошельке владельца.");
            }).confirm("Закрыть счёт «" + a.name + "»? Всё, что на нём, уйдёт в кошелёк владельца.");
        }
        ui.header("История", a.history.size() + "");
        int n = 0;
        for (BankAccount.Op op : a.history) {
            if (n++ >= 25) {
                break;
            }
            Currency c = BankManager.currency(op.currency);
            ui.row(new ItemStack(Items.PAPER), BankManager.kindTitle(op.kind) + " &f" + (c == null ? op.amount : c.format(op.amount)))
                    .line("&8" + Fmt.timestamp(op.time) + " · " + op.who + (op.note.isEmpty() ? "" : " · " + op.note));
        }
    }

    private static String nextRight(String r) {
        switch (r == null ? "" : r) {
            case BankAccount.VIEW:
                return BankAccount.DEPOSIT;
            case BankAccount.DEPOSIT:
                return BankAccount.WITHDRAW;
            case BankAccount.WITHDRAW:
                return BankAccount.MANAGE;
            default:
                return BankAccount.VIEW;
        }
    }

    // --- админ: валюты ----------------------------------------------------------------------------------

    private void buildCurrencies(Ui ui) {
        ui.header("Валюты", BankManager.currencies().size() + "");
        ui.text("&7Основная валюта — баланс мода. Остальные — свои: курс к монетам, можно ли менять, переводить и"
                + " держать в банке. Выдать валюту: &f/bank give <ник> <валюта> <сумма>&7, в сценариях — шаг &fcurrency&7.");
        for (final Currency c : BankManager.currencies()) {
            Ui.Row r = ui.row(UiUtil.icon(c.icon), c.color + c.name + " &8" + c.id)
                    .line(c.main() ? "&7основной баланс" : c.rate > 0 ? "&71 = " + Fmt.money((long) Math.round(c.rate)) : "&7без курса");
            if (!c.enabled && !c.main()) {
                r.chip("выключена", Ui.GREY);
            }
            r.onClick(() -> editCurrency = c.id);
            r.button("⚙", Ui.GREY, () -> editCurrency = c.id);
        }
        ui.field("Новая валюта", "id: латиница, цифры, _ (например gems) — Enter", "", v -> {
            Currency c = BankManager.createCurrency(v, v);
            if (c == null) {
                throw new UiError("id занят или неверный (латиница, цифры, _).");
            }
            editCurrency = c.id;
        }).placeholder("gems").max(24);
    }

    private void buildCurrency(Ui ui, final Currency c) {
        ui.back(() -> editCurrency = null);
        ui.title("Валюта " + c.name).subtitle("&8" + c.id).icon(UiUtil.icon(c.icon));
        ui.field("Название", "", c.name, v -> {
            c.name = v.trim().isEmpty() ? c.id : v.trim();
            BankManager.saveAll();
        }).max(32);
        ui.field("Иконка", "Предмет", c.icon, v -> {
            String id = UiUtil.parseItemId(v);
            if (id == null) {
                throw new UiError("Не нашёл такой предмет.");
            }
            c.icon = id;
            BankManager.saveAll();
        }).picker(Ui.PICK_ITEM);
        ui.field("Описание", "", c.description, v -> {
            c.description = v.trim();
            BankManager.saveAll();
        }).clearable().max(120);
        if (c.main()) {
            ui.note(Ui.BLUE, "Основную валюту нельзя выключить или удалить — это баланс игроков.");
            return;
        }
        ui.field("Знак", "Короткий символ после суммы", c.symbol, v -> {
            c.symbol = UiUtil.cut(v.trim(), 4);
            BankManager.saveAll();
        }).max(4);
        ui.field("Цвет", "Код цвета, например &b", c.color, v -> {
            c.color = v.trim().isEmpty() ? "&b" : UiUtil.cut(v.trim(), 4);
            BankManager.saveAll();
        }).max(4);
        ui.field("Курс", "Сколько монет стоит 1 единица (0 — не меняется)", Double.toString(c.rate), v -> {
            try {
                c.rate = Math.max(0.0D, Double.parseDouble(v.trim().replace(',', '.')));
            } catch (NumberFormatException e) {
                throw new UiError("Нужно число, например 100 или 0.5");
            }
            BankManager.saveAll();
        });
        ui.toggle("Включена", "Выключенная не видна игрокам", c.enabled, on -> {
            c.enabled = on;
            BankManager.saveAll();
        });
        ui.toggle("Меняется в банке", "", c.exchangeable, on -> {
            c.exchangeable = on;
            BankManager.saveAll();
        });
        ui.toggle("Переводится игрокам", "", c.transferable, on -> {
            c.transferable = on;
            BankManager.saveAll();
        });
        ui.toggle("Можно держать на счетах", "", c.bankable, on -> {
            c.bankable = on;
            BankManager.saveAll();
        });
        ui.buttons().add("Удалить валюту", Ui.RED, () -> {
            BankManager.deleteCurrency(c.id);
            editCurrency = null;
            toast("Валюта удалена.");
        }).confirm("Удалить «" + c.name + "»? Она пропадёт из всех кошельков и счетов.");
    }

    // --- админ: настройки ----------------------------------------------------------------------------------

    private void buildSettings(Ui ui) {
        final BankSettings s = BankManager.settings();
        ui.header("Настройки банка");
        ui.toggle("Банк включён", "", s.enabled, on -> {
            s.enabled = on;
            BankManager.saveAll();
        });
        ui.field("Процент в сутки", "На монеты на счетах, 0 — без процентов", Double.toString(s.interestPercent), v -> {
            s.interestPercent = parseD(v, 0, 50);
            BankManager.saveAll();
        });
        ui.money("Процент только до суммы", "0 — на всю сумму", s.interestCap, v -> {
            s.interestCap = v;
            BankManager.saveAll();
        });
        ui.toggle("Процент только активным", "Если владелец заходил вчера или сегодня", s.interestOnlyOnline, on -> {
            s.interestOnlyOnline = on;
            BankManager.saveAll();
        });
        ui.field("Комиссия обмена, %", "", Double.toString(s.exchangeFee), v -> {
            s.exchangeFee = parseD(v, 0, 100);
            BankManager.saveAll();
        });
        ui.field("Комиссия перевода между счетами, %", "", Double.toString(s.transferFee), v -> {
            s.transferFee = parseD(v, 0, 100);
            BankManager.saveAll();
        });
        ui.number("Счетов у игрока", "", s.maxAccounts, 0, 50, v -> {
            s.maxAccounts = (int) v;
            BankManager.saveAll();
        });
        ui.money("Цена нового счёта", "Первый бесплатно", s.accountPrice, v -> {
            s.accountPrice = v;
            BankManager.saveAll();
        });
        ui.number("Участников общего счёта", "", s.maxMembers, 1, 100, v -> {
            s.maxMembers = (int) v;
            BankManager.saveAll();
        });
        ui.toggle("Счета только у банкомата", "Иначе /bank работает везде", s.needAtm, on -> {
            s.needAtm = on;
            BankManager.saveAll();
        });
        ui.toggle("Игроки ставят свои банкоматы", "Иначе ставят только админы", s.playerAtms, on -> {
            s.playerAtms = on;
            BankManager.saveAll();
        });
    }

    private static double parseD(String v, double min, double max) {
        try {
            double d = Double.parseDouble(v.trim().replace(',', '.'));
            if (d < min || d > max || Double.isNaN(d)) {
                throw new UiError("От " + min + " до " + max + ".");
            }
            return d;
        } catch (NumberFormatException e) {
            throw new UiError("Нужно число.");
        }
    }
}
