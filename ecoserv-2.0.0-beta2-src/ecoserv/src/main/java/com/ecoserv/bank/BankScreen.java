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

/**
 * Окно банка (2.0): кошелёк, банки сервера и игроков, счета, кредиты, обмен, свой банк; админ — банки, валюты,
 * общие правила.
 */
public class BankScreen extends UiScreen {

    private enum Tab {
        WALLET, BANKS, ACCOUNTS, LOANS, EXCHANGE, MY_BANK, CURRENCIES, SETTINGS
    }

    private Tab tab = Tab.WALLET;
    @Nullable
    private String account;
    @Nullable
    private String editCurrency;
    /** Открыта карточка банка (для клиента). */
    @Nullable
    private String viewBank;
    /** Открыто управление банком (владелец/админ). */
    @Nullable
    private String manageBank;
    /** Банкомат, через который открыто окно (null — команда /bank). */
    @Nullable
    private final BlockPos atm;
    @Nullable
    private final String atmBank;

    // ввод
    private String cur = Currency.MAIN;
    private long amount;
    private String exFrom = Currency.MAIN;
    private String exTo = "gems";
    private long exAmount;
    private String toCode = "";
    private String payTo = "";
    private long loanAmount = 1000L;
    private String loanDays = "7";
    private String accName = "";
    private String accKind = "personal";
    private String newBankName = "";
    private String newBankPreset = "universal";
    private long reserveAmount;

    public BankScreen(EntityPlayerMP player, @Nullable BlockPos atm, @Nullable String atmBank) {
        super(player);
        this.atm = atm;
        this.atmBank = atmBank;
        if (atmBank != null && BankManager.bank(atmBank) != null) {
            tab = Tab.BANKS;
            viewBank = atmBank;
        }
    }

    public static void open(EntityPlayerMP player, @Nullable BlockPos atm) {
        new BankScreen(player, atm, null).open();
    }

    public static void open(EntityPlayerMP player, @Nullable BlockPos atm, @Nullable String bank) {
        new BankScreen(player, atm, bank).open();
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
        if (manageBank != null) {
            Bank b = BankManager.bank(manageBank);
            if (b != null && BankManager.canManage(player, b)) {
                buildManage(ui, b);
                return;
            }
            manageBank = null;
        }
        if (viewBank != null) {
            Bank b = BankManager.bank(viewBank);
            if (b != null) {
                buildBank(ui, b);
                return;
            }
            viewBank = null;
        }
        ui.title(atm != null ? "Банкомат" : "Банк").icon(UiIcons.bank());
        ui.subtitle("&7Кошелёк: " + BankManager.main().format(BankManager.balance(player.getUniqueID(), Currency.MAIN)));
        ui.tab("Кошелёк", UiIcons.money(), tab == Tab.WALLET, () -> tab = Tab.WALLET);
        ui.tab("Банки", new ItemStack(Blocks.GOLD_BLOCK), tab == Tab.BANKS, () -> tab = Tab.BANKS);
        ui.tab("Мои счета", UiIcons.bank(), tab == Tab.ACCOUNTS, () -> tab = Tab.ACCOUNTS);
        ui.tab("Кредиты", new ItemStack(Items.WRITABLE_BOOK), tab == Tab.LOANS, () -> tab = Tab.LOANS);
        ui.tab("Обмен", UiIcons.trade(), tab == Tab.EXCHANGE, () -> tab = Tab.EXCHANGE);
        if (BankManager.settings().playerBanks || admin() || !BankManager.ownedBanks(player.getUniqueID()).isEmpty()) {
            ui.tab("Мой банк", new ItemStack(Blocks.EMERALD_BLOCK), tab == Tab.MY_BANK, () -> tab = Tab.MY_BANK);
        }
        if (admin()) {
            ui.sideLabel("&8Админ");
            ui.tab("Валюты", new ItemStack(Items.DIAMOND), tab == Tab.CURRENCIES, () -> tab = Tab.CURRENCIES);
            ui.tab("Общие правила", UiIcons.gear(), tab == Tab.SETTINGS, () -> tab = Tab.SETTINGS);
        }
        if (!BankManager.settings().enabled) {
            ui.note(Ui.YELLOW, admin() ? "Банки выключены (видишь, потому что админ)." : "Банки выключены на сервере.");
        }
        switch (tab) {
            case BANKS:
                buildBanks(ui);
                break;
            case ACCOUNTS:
                buildAccounts(ui);
                break;
            case LOANS:
                buildLoans(ui);
                break;
            case EXCHANGE:
                buildExchange(ui);
                break;
            case MY_BANK:
                buildMyBank(ui);
                break;
            case CURRENCIES:
                if (admin()) {
                    buildCurrencies(ui);
                    break;
                }
                buildWallet(ui);
                break;
            case SETTINGS:
                if (admin()) {
                    buildSettings(ui);
                    break;
                }
                buildWallet(ui);
                break;
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

    private static String sum(BankAccount a) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Long> e : a.balances.entrySet()) {
            Currency c = BankManager.currency(e.getKey());
            if (c != null && e.getValue() != 0L) {
                b.append(b.length() > 0 ? "&7, " : "").append(c.format(e.getValue()));
            }
        }
        return b.length() == 0 ? "&8пусто" : b.toString();
    }

    /** Строка «условия банка» коротко. */
    private static String terms(Bank b) {
        StringBuilder s = new StringBuilder();
        s.append("&b").append(b.interestPercent).append("%&7/сутки");
        if (b.loans) {
            s.append(" · кредит &e").append(b.loanRate).append("%&7/сутки до ").append(Fmt.money(b.loanMax));
        }
        if (b.transferFee > 0 || b.withdrawFee > 0) {
            s.append(" · комиссия ").append(b.transferFee).append("/").append(b.withdrawFee).append("%");
        }
        return s.toString();
    }

    private ItemStack bankIcon(Bank b) {
        return UiUtil.icon(b.icon);
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

    // --- банки -------------------------------------------------------------------------------------------

    private void buildBanks(Ui ui) {
        List<Bank> list = BankManager.banks();
        ui.header("Банки", list.size() + "");
        ui.text("&7&6Банки сервера&7 платят проценты и дают кредиты из казны сервера. &aБанки игроков&7 — из своего"
                + " резерва: условия бывают выгоднее, но риск на владельце. Открой банк, чтобы завести счёт.");
        ui.search("поиск банка");
        boolean serverHeader = false;
        boolean playerHeader = false;
        for (final Bank b : list) {
            if (b.server() && !serverHeader) {
                ui.header("&6Банки сервера");
                serverHeader = true;
            }
            if (!b.server() && !playerHeader) {
                ui.header("&aБанки игроков");
                playerHeader = true;
            }
            String blocked = BankManager.bankBlocked(b);
            if (blocked != null && !admin() && !BankManager.canManage(player, b)) {
                continue;
            }
            Ui.Row r = ui.row(bankIcon(b), b.name).line(terms(b));
            if (!b.server()) {
                r.line("&8владелец " + b.ownerName + " · резерв " + Fmt.money(b.reserve));
            }
            if (!b.description.isEmpty()) {
                r.line("&8" + b.description);
            }
            int mine = BankManager.ownedIn(player.getUniqueID(), b);
            if (mine > 0) {
                r.chip("мои счета: " + mine, Ui.GREEN);
            }
            if (blocked != null) {
                r.chip(blocked, Ui.RED);
            }
            if (!b.open) {
                r.chip("закрыт для новых", Ui.GREY);
            }
            r.onClick(() -> viewBank = b.id);
            r.button("Открыть", Ui.BLUE, () -> viewBank = b.id);
            if (BankManager.canManage(player, b)) {
                r.button("⚙", Ui.GREY, () -> manageBank = b.id).tip("Управление банком");
            }
        }
        if (admin()) {
            ui.header("Новый банк сервера", "&8админ");
            ui.field("Название", "Enter — создать (условия настроишь дальше)", "", v -> {
                if (v.trim().length() < 3) {
                    throw new UiError("Хотя бы 3 символа.");
                }
                Bank b = BankManager.createServerBank(v);
                manageBank = b.id;
            }).placeholder("Банк Севера").max(32);
        }
    }

    /** Карточка банка для клиента: условия, открыть счёт, взять кредит. */
    private void buildBank(Ui ui, final Bank b) {
        ui.back(() -> viewBank = null);
        ui.title(b.name).icon(bankIcon(b))
                .subtitle(b.server() ? "&6банк сервера" : "&aбанк игрока &f" + b.ownerName);
        String blocked = BankManager.bankBlocked(b);
        if (blocked != null) {
            ui.note(Ui.YELLOW, "Сейчас нельзя: " + blocked + ".");
        }
        if (BankManager.canManage(player, b)) {
            ui.sideButton("Управлять", UiIcons.gear(), Ui.GREY, () -> manageBank = b.id);
        }
        if (!b.description.isEmpty()) {
            ui.text("&7" + b.description);
        }
        ui.header("Условия");
        ui.row(new ItemStack(Items.GOLD_NUGGET), "&bВклады").line("&7" + b.interestPercent + "% в сутки на монеты"
                + (b.interestCap > 0 ? ", до " + Fmt.money(b.interestCap) : "")
                + (b.interestOnlyOnline ? ", если заходишь на сервер" : ""));
        ui.row(new ItemStack(Items.PAPER), "&7Комиссии").line("&7перевод " + b.transferFee + "%, снятие " + b.withdrawFee + "%"
                + (b.accountPrice > 0 ? ", открытие счёта " + Fmt.money(b.accountPrice) : ", счёт бесплатно"));
        ui.row(new ItemStack(Blocks.CHEST), "&7Счета").line("&7до " + b.maxAccounts + " на игрока, до " + b.maxMembers
                + " участников в общем счёте");
        if (!b.server()) {
            ui.row(new ItemStack(Blocks.EMERALD_BLOCK), "&aНадёжность").line("&7резерв " + Fmt.money(b.reserve)
                    + " · вклады клиентов " + Fmt.money(BankManager.deposits(b)))
                    .line("&8Проценты платятся из резерва: если он кончится — процентов не будет. Сами вклады в сохранности.");
        }
        // мои счета здесь
        List<BankAccount> mine = BankManager.accountsOf(player.getUniqueID());
        boolean any = false;
        for (final BankAccount a : mine) {
            if (!a.bank.equals(b.id)) {
                continue;
            }
            if (!any) {
                ui.header("Мои счета здесь");
                any = true;
            }
            ui.row(new ItemStack(a.shared ? Blocks.ENDER_CHEST : Blocks.CHEST), "&e" + a.name)
                    .line("&7код &f" + a.code + " &8· " + sum(a))
                    .onClick(() -> account = a.code)
                    .button("Открыть", Ui.BLUE, () -> account = a.code);
        }
        ui.header("Открыть счёт");
        ui.select("Тип счёта", "", accKind, v -> accKind = v)
                .option("personal", "Личный", "Только твой", new ItemStack(Blocks.CHEST))
                .option("shared", "Общий", "Для команды, клана, компании: участники с правами", new ItemStack(Blocks.ENDER_CHEST));
        ui.field("Название", "", accName, v -> accName = v).placeholder("shared".equals(accKind) ? "Казна клана" : "Сбережения").max(32);
        ui.buttons().add("Открыть счёт" + (b.accountPrice > 0 ? " за " + Fmt.money(b.accountPrice) : ""), Ui.GREEN, () -> {
            BankAccount a = BankManager.open(player, b, accName, "shared".equals(accKind));
            accName = "";
            account = a.code;
            toast("Счёт открыт, код " + a.code);
        }).disabled(blocked != null && !admin());
        if (b.loans) {
            ui.header("Кредит", "&8" + b.loanRate + "% в сутки");
            ui.text("&7Деньги сразу в кошелёк. Каждые сутки на остаток капает процент и списывается часть долга."
                    + " Не хватит денег к сроку — кредит станет просроченным" + (BankManager.settings().blockDebtors
                    ? ", и новых кредитов не дадут нигде." : "."));
            ui.money("Сумма", "До " + Fmt.money(b.loanMax), loanAmount, v -> loanAmount = v);
            Ui.Select days = ui.select("Срок", "", loanDays, v -> loanDays = v);
            for (int d : new int[]{1, 3, 7, 14, 30}) {
                if (d <= b.loanDays) {
                    days.option(Integer.toString(d), d + " дн.", "≈ вернуть " + Fmt.money(estimate(loanAmount, b.loanRate, d)));
                }
            }
            if (b.loanDays > 0 && "0".equals(loanDays)) {
                loanDays = "1";
            }
            ui.buttons().add("Взять кредит", Ui.YELLOW, () -> {
                int d;
                try {
                    d = Integer.parseInt(loanDays);
                } catch (NumberFormatException e) {
                    d = 1;
                }
                BankManager.borrow(player, b, loanAmount, d);
                toast("Кредит выдан: " + Fmt.money(loanAmount));
                tab = Tab.LOANS;
                viewBank = null;
            }).confirm("Взять " + Fmt.money(loanAmount) + " под " + b.loanRate + "% в сутки?").disabled(blocked != null);
        }
    }

    private static long estimate(long amount, double rate, int days) {
        double v = amount;
        for (int i = 0; i < days; i++) {
            v += Math.ceil(v * rate / 100.0D);
        }
        return (long) v;
    }

    // --- счета ---------------------------------------------------------------------------------------------

    private void buildAccounts(Ui ui) {
        List<BankAccount> mine = BankManager.accountsOf(player.getUniqueID());
        ui.header("Мои счета", mine.size() + "");
        if (mine.isEmpty()) {
            ui.text("&7Счёт — это сейф для денег: деньги на нём не потратятся случайно, на них может капать процент,"
                    + " а общий счёт можно вести вместе с командой. Выбери банк во вкладке «Банки».");
            ui.buttons().add("К банкам", Ui.BLUE, () -> tab = Tab.BANKS);
        }
        for (final BankAccount a : mine) {
            Bank b = BankManager.bank(a.bank);
            Ui.Row r = ui.row(new ItemStack(a.shared ? Blocks.ENDER_CHEST : Blocks.CHEST), "&e" + a.name)
                    .line("&7код &f" + a.code + " &8· " + (b == null ? "?" : b.name)
                            + (a.shared ? " &8· общий, владелец " + a.ownerName : ""));
            r.line(sum(a));
            if (a.frozen) {
                r.chip("заморожен", Ui.RED);
            }
            r.chip(BankAccount.rightTitle(a.level(player.getUniqueID().toString()) >= 4 ? BankAccount.MANAGE
                    : a.members.get(player.getUniqueID().toString())), Ui.GREY);
            r.onClick(() -> account = a.code);
            r.button("Открыть", Ui.BLUE, () -> account = a.code);
        }
        if (admin()) {
            List<BankAccount> all = BankManager.allAccounts();
            ui.header("Все счета (админ)", all.size() + "");
            ui.search("поиск по названию, коду, владельцу");
            for (final BankAccount a : all) {
                ui.row(new ItemStack(Blocks.CHEST), "&f" + a.name + " &8" + a.code + " · " + a.bank)
                        .line("&7" + a.ownerName + " · " + sum(a))
                        .onClick(() -> account = a.code);
            }
        }
    }

    // --- кредиты --------------------------------------------------------------------------------------------

    private void buildLoans(Ui ui) {
        List<Loan> list = BankManager.loansOf(player.getUniqueID());
        ui.header("Мои кредиты", list.size() + "");
        if (list.isEmpty()) {
            ui.text("&7Кредитов нет. Взять кредит можно в карточке банка, который их выдаёт.");
        }
        long today = BankManager.today();
        for (final Loan l : list) {
            Bank b = BankManager.bank(l.bank);
            Ui.Row r = ui.row(new ItemStack(Items.WRITABLE_BOOK), "&e" + Fmt.money(l.remaining) + " &7долг")
                    .line("&7" + (b == null ? l.bank : b.name) + " · взято " + Fmt.money(l.amount) + " под " + l.rate + "%/сутки")
                    .line("&7осталось дней: &f" + Math.max(0, l.dueDay - today) + (l.missed > 0 ? " &c· пропущено платежей: " + l.missed : ""));
            if (l.defaulted) {
                r.chip("просрочен", Ui.RED);
            }
            r.button("Погасить всё", Ui.GREEN, () -> {
                BankManager.repay(player, l, l.remaining);
                toast("Кредит погашен.");
            }).confirm("Отдать " + Fmt.money(l.remaining) + "?");
            r.button("Часть…", Ui.GREY, () -> {
                BankManager.repay(player, l, Math.max(1, amount));
                toast("Внесено " + Fmt.money(Math.min(Math.max(1, amount), l.remaining)));
            }).tip("Вносит сумму из поля ниже");
        }
        if (!list.isEmpty()) {
            ui.money("Сумма для «Часть…»", "", amount, v -> amount = v);
        }
    }

    // --- свой банк -----------------------------------------------------------------------------------------

    private void buildMyBank(Ui ui) {
        List<Bank> own = BankManager.ownedBanks(player.getUniqueID());
        BankSettings s = BankManager.settings();
        if (!own.isEmpty()) {
            ui.header("Мои банки", own.size() + "");
            for (final Bank b : own) {
                String blocked = BankManager.bankBlocked(b);
                Ui.Row r = ui.row(bankIcon(b), b.name).line(terms(b))
                        .line("&7резерв &a" + Fmt.money(b.reserve) + "&7 · клиентов " + BankManager.accountsIn(b).size()
                                + " · заработано " + Fmt.money(b.earned));
                if (blocked != null) {
                    r.chip(blocked, Ui.YELLOW);
                }
                r.onClick(() -> manageBank = b.id);
                r.button("Управлять", Ui.BLUE, () -> manageBank = b.id);
            }
        }
        if (!s.playerBanks && !admin()) {
            ui.note(Ui.GREY, "Новые банки игроков на сервере запрещены.");
            return;
        }
        if (own.size() >= s.maxBanksPerPlayer && !admin()) {
            return;
        }
        ui.header("Открыть свой банк", s.bankLicense > 0 ? "&6лицензия " + Fmt.money(s.bankLicense) : "");
        ui.text("&7Свой банк принимает вклады и выдаёт кредиты. Проценты по вкладам ты платишь из &aрезерва&7,"
                + " кредиты тоже выдаются из него, а комиссии и проценты по кредитам в него возвращаются."
                + " Пока резерв меньше " + Fmt.money(s.minReserve) + ", новые клиенты не могут открыть счёт."
                + (s.needApproval ? " &eНовый банк должен одобрить админ." : ""));
        ui.field("Название", "", newBankName, v -> newBankName = v).placeholder("Банк " + player.getName()).max(32);
        ui.select("Направление", "Стартовые условия — потом можно поменять", newBankPreset, v -> newBankPreset = v)
                .option("savings", "Сберегательный", "Высокий процент по вкладам, без кредитов", new ItemStack(Blocks.CHEST))
                .option("credit", "Кредитный", "Низкий процент вкладов, выдаёт кредиты", new ItemStack(Items.WRITABLE_BOOK))
                .option("universal", "Универсальный", "Немного того и другого", new ItemStack(Blocks.GOLD_BLOCK))
                .option("vault", "Хранилище", "Без процентов, нулевые комиссии — просто надёжный сейф", new ItemStack(Blocks.IRON_BLOCK));
        ui.buttons().add("Открыть банк", Ui.GREEN, () -> {
            String name = newBankName.trim().isEmpty() ? "Банк " + player.getName() : newBankName;
            Bank b = BankManager.createPlayerBank(player, name);
            applyPreset(b, newBankPreset, s);
            BankManager.saveAll();
            newBankName = "";
            manageBank = b.id;
            toast("Банк открыт. Пополни резерв, чтобы принимать клиентов.");
        }).confirm(s.bankLicense > 0 && !admin() ? "Заплатить " + Fmt.money(s.bankLicense) + " за лицензию?" : "Открыть банк?");
    }

    private static void applyPreset(Bank b, String preset, BankSettings s) {
        switch (preset) {
            case "savings":
                b.interestPercent = Math.min(s.maxPlayerInterest, 1.0D);
                b.loans = false;
                b.transferFee = 1.0D;
                break;
            case "credit":
                b.interestPercent = 0.1D;
                b.loans = true;
                b.loanRate = Math.min(s.maxPlayerLoanRate, 2.0D);
                b.loanMax = 5000L;
                b.loanDays = 7;
                break;
            case "vault":
                b.interestPercent = 0.0D;
                b.loans = false;
                b.transferFee = 0.0D;
                b.withdrawFee = 0.0D;
                break;
            default:
                b.interestPercent = Math.min(s.maxPlayerInterest, 0.4D);
                b.loans = true;
                b.loanRate = Math.min(s.maxPlayerLoanRate, 1.5D);
                b.loanMax = 3000L;
                b.loanDays = 7;
        }
    }

    /** Управление банком: и для банка сервера (админ), и для банка игрока (владелец). */
    private void buildManage(Ui ui, final Bank b) {
        final BankSettings s = BankManager.settings();
        final boolean adm = admin();
        ui.back(() -> manageBank = null);
        ui.title("Управление: " + UiUtil.plain(b.name)).icon(bankIcon(b))
                .subtitle(b.server() ? "&6банк сервера" : "&aбанк игрока &f" + b.ownerName);
        ui.sideButton("Как видят клиенты", UiIcons.profile(), Ui.BLUE, () -> {
            manageBank = null;
            viewBank = b.id;
        });
        String blocked = BankManager.bankBlocked(b);
        if (blocked != null) {
            ui.note(Ui.YELLOW, "Сейчас клиенты не могут работать: " + blocked + ".");
        }
        if (!b.server()) {
            ui.header("Резерв", Fmt.money(b.reserve));
            ui.text("&7Вклады клиентов: " + Fmt.money(BankManager.deposits(b)) + " · заработано " + Fmt.money(b.earned)
                    + " · выплачено процентов " + Fmt.money(b.paidInterest));
            ui.money("Сумма", "", reserveAmount, v -> reserveAmount = v);
            ui.buttons().add("Пополнить резерв", Ui.GREEN, () -> {
                BankManager.addReserve(player, b, reserveAmount);
                toast("Резерв пополнен.");
            });
            ui.buttons().add("Забрать из резерва", Ui.YELLOW, () -> {
                BankManager.takeReserve(player, b, reserveAmount);
                toast("Снято из резерва.");
            });
        }
        ui.header("Вывеска");
        ui.field("Название", "", b.name, v -> {
            if (v.trim().length() >= 3) {
                b.name = UiUtil.cut(v.trim(), 32);
                BankManager.saveAll();
            }
        }).max(32);
        ui.field("Описание", "", b.description, v -> {
            b.description = UiUtil.cut(v.trim(), 120);
            BankManager.saveAll();
        }).clearable().max(120);
        ui.field("Иконка", "Предмет", b.icon, v -> {
            String id = UiUtil.parseItemId(v);
            if (id == null) {
                throw new UiError("Не нашёл такой предмет.");
            }
            b.icon = id;
            BankManager.saveAll();
        }).picker(Ui.PICK_ITEM);
        ui.toggle("Принимает новых клиентов", "", b.open, on -> {
            b.open = on;
            BankManager.saveAll();
        });

        ui.header("Вклады");
        final double maxI = b.server() || adm ? 50.0D : s.maxPlayerInterest;
        Ui.Select ip = ui.select("Процент в сутки", "Сколько получают клиенты на монеты на счетах (до " + maxI + "%)",
                pct(b.interestPercent), v -> {
                    b.interestPercent = Math.min(maxI, parseD(v, 0, 50));
                    BankManager.saveAll();
                });
        for (String o : new String[]{"0.0", "0.1", "0.25", "0.5", "1.0", "2.0", "3.0", "5.0"}) {
            if (Double.parseDouble(o) <= maxI) {
                ip.option(o, o + "%", "1000 монет → +" + Math.round(10 * Double.parseDouble(o)) + " в сутки");
            }
        }
        if (!containsPct(new String[]{"0.0", "0.1", "0.25", "0.5", "1.0", "2.0", "3.0", "5.0"}, b.interestPercent)) {
            ip.option(pct(b.interestPercent), pct(b.interestPercent) + "%", "своё значение");
        }
        ui.money("Процент только до суммы", "0 — на всю сумму", b.interestCap, v -> {
            b.interestCap = v;
            BankManager.saveAll();
        });
        ui.toggle("Только активным", "Процент — если владелец счёта заходил вчера или сегодня", b.interestOnlyOnline, on -> {
            b.interestOnlyOnline = on;
            BankManager.saveAll();
        });

        ui.header("Комиссии и счета");
        feeSelect(ui, "Комиссия перевода", pct(b.transferFee), v -> b.transferFee = v);
        feeSelect(ui, "Комиссия снятия", pct(b.withdrawFee), v -> b.withdrawFee = v);
        ui.money("Цена открытия счёта", "", b.accountPrice, v -> {
            b.accountPrice = v;
            BankManager.saveAll();
        });
        ui.number("Счетов у одного игрока", "", b.maxAccounts, 1, 50, v -> {
            b.maxAccounts = (int) v;
            BankManager.saveAll();
        });
        ui.number("Участников общего счёта", "", b.maxMembers, 1, 100, v -> {
            b.maxMembers = (int) v;
            BankManager.saveAll();
        });

        ui.header("Кредиты");
        ui.toggle("Выдаёт кредиты", b.server() ? "Из казны сервера" : "Из резерва банка", b.loans, on -> {
            b.loans = on;
            BankManager.saveAll();
        });
        if (b.loans) {
            final double maxL = b.server() || adm ? 50.0D : s.maxPlayerLoanRate;
            Ui.Select lr = ui.select("Процент по кредиту в сутки", "до " + maxL + "%", pct(b.loanRate), v -> {
                b.loanRate = Math.min(maxL, parseD(v, 0, 50));
                BankManager.saveAll();
            });
            for (String o : new String[]{"0.5", "1.0", "1.5", "2.0", "3.0", "5.0", "10.0"}) {
                if (Double.parseDouble(o) <= maxL) {
                    lr.option(o, o + "%", "1000 на 7 дн. → вернуть " + Fmt.money(estimate(1000, Double.parseDouble(o), 7)));
                }
            }
            if (!containsPct(new String[]{"0.5", "1.0", "1.5", "2.0", "3.0", "5.0", "10.0"}, b.loanRate)) {
                lr.option(pct(b.loanRate), pct(b.loanRate) + "%", "своё значение");
            }
            ui.money("Максимальная сумма", "", b.loanMax, v -> {
                b.loanMax = Math.max(1, v);
                BankManager.saveAll();
            });
            ui.number("Максимальный срок, дней", "", b.loanDays, 1, 60, v -> {
                b.loanDays = (int) v;
                BankManager.saveAll();
            });
            List<Loan> loans = BankManager.loansOf(b);
            ui.header("Выданные кредиты", loans.size() + "");
            for (Loan l : loans) {
                Ui.Row r = ui.row(new ItemStack(Items.WRITABLE_BOOK), "&f" + l.borrowerName + " &e" + Fmt.money(l.remaining))
                        .line("&7взял " + Fmt.money(l.amount) + ", вернул " + Fmt.money(l.paid)
                                + ", осталось дней " + Math.max(0, l.dueDay - BankManager.today()));
                if (l.defaulted) {
                    r.chip("просрочен", Ui.RED);
                }
            }
        }

        List<BankAccount> clients = BankManager.accountsIn(b);
        ui.header("Клиенты", clients.size() + "");
        if (clients.size() > 6) {
            ui.search("поиск клиента");
        }
        for (final BankAccount a : clients) {
            Ui.Row r = ui.row(new ItemStack(a.shared ? Blocks.ENDER_CHEST : Blocks.CHEST), "&f" + a.ownerName + " &8" + a.code)
                    .line("&7" + a.name + " · " + sum(a));
            if (adm) {
                r.onClick(() -> account = a.code);
            }
        }

        if (adm) {
            ui.header("Администрация");
            if (!b.server()) {
                ui.toggle("Одобрен", "Без одобрения банк не принимает клиентов", b.approved, on -> {
                    b.approved = on;
                    BankManager.saveAll();
                });
            }
            ui.toggle("Заморожен", "Никаких операций, процентов и кредитов", b.frozen, on -> {
                b.frozen = on;
                BankManager.saveAll();
            });
        }
        if (!BankManager.SERVER_BANK.equals(b.id)) {
            ui.buttons().add("Закрыть банк", Ui.RED, () -> {
                BankManager.closeBank(player, b);
                manageBank = null;
                toast("Банк закрыт, всё вернулось клиентам.");
            }).confirm("Закрыть «" + UiUtil.plain(b.name) + "»? Все счета закроются, деньги уйдут клиентам в кошельки,"
                    + " кредиты будут прощены, резерв вернётся владельцу.");
        }
    }

    private interface DoubleSet {
        void set(double v);
    }

    private void feeSelect(Ui ui, String label, String value, final DoubleSet set) {
        Ui.Select sel = ui.select(label, "", value, v -> {
            set.set(parseD(v, 0, 100));
            BankManager.saveAll();
        });
        String[] opts = {"0.0", "0.5", "1.0", "2.0", "3.0", "5.0", "10.0"};
        for (String o : opts) {
            sel.option(o, o.equals("0.0") ? "нет" : o + "%", "с 1000 → " + Math.round(10 * Double.parseDouble(o)));
        }
        try {
            if (!containsPct(opts, Double.parseDouble(value))) {
                sel.option(value, value + "%", "своё значение");
            }
        } catch (NumberFormatException ignored) {
            // нет
        }
    }

    private static String pct(double v) {
        return Double.toString(v);
    }

    private static boolean containsPct(String[] opts, double v) {
        for (String o : opts) {
            if (Double.parseDouble(o) == v) {
                return true;
            }
        }
        return false;
    }

    private void buildAccount(Ui ui, final BankAccount a) {
        final String me = player.getUniqueID().toString();
        int level = admin() ? 4 : a.level(me);
        ui.back(() -> account = null);
        Bank inBank = BankManager.bank(a.bank);
        ui.title(a.name).subtitle("&7код &f" + a.code + " &8· " + (inBank == null ? "?" : inBank.name) + " &8· владелец " + a.ownerName).icon(new ItemStack(a.shared ? Blocks.ENDER_CHEST : Blocks.CHEST));
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
                ui.header("Перевод на другой счёт", inBank != null && inBank.transferFee > 0 ? "&8комиссия " + inBank.transferFee + "%" : "");
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

    // --- админ: общие правила --------------------------------------------------------------------------

    private void buildSettings(Ui ui) {
        final BankSettings s = BankManager.settings();
        ui.header("Общие правила банков");
        ui.text("&7Условия каждого банка (проценты, комиссии, кредиты) — в его управлении: вкладка «Банки» → ⚙.");
        ui.toggle("Банки включены", "", s.enabled, on -> {
            s.enabled = on;
            BankManager.saveAll();
        });
        ui.field("Комиссия обмена валют, %", "", Double.toString(s.exchangeFee), v -> {
            s.exchangeFee = parseD(v, 0, 100);
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
        ui.toggle("Должникам — без кредитов", "Просрочил кредит — не дадут нигде, пока не вернёт", s.blockDebtors, on -> {
            s.blockDebtors = on;
            BankManager.saveAll();
        });
        ui.header("Банки игроков");
        ui.toggle("Игроки открывают свои банки", "", s.playerBanks, on -> {
            s.playerBanks = on;
            BankManager.saveAll();
        });
        ui.money("Цена лицензии", "Платит игрок при открытии банка", s.bankLicense, v -> {
            s.bankLicense = v;
            BankManager.saveAll();
        });
        ui.number("Банков у одного игрока", "", s.maxBanksPerPlayer, 0, 10, v -> {
            s.maxBanksPerPlayer = (int) v;
            BankManager.saveAll();
        });
        ui.toggle("Нужно одобрение админа", "Новый банк не работает, пока админ не одобрит", s.needApproval, on -> {
            s.needApproval = on;
            BankManager.saveAll();
        });
        ui.money("Минимальный резерв", "Меньше — банк не принимает новых клиентов и не даёт кредиты", s.minReserve, v -> {
            s.minReserve = v;
            BankManager.saveAll();
        });
        ui.field("Максимум % по вкладам у игроков", "", Double.toString(s.maxPlayerInterest), v -> {
            s.maxPlayerInterest = parseD(v, 0, 50);
            BankManager.saveAll();
        });
        ui.field("Максимум % по кредитам у игроков", "", Double.toString(s.maxPlayerLoanRate), v -> {
            s.maxPlayerLoanRate = parseD(v, 0, 50);
            BankManager.saveAll();
        });
        List<Bank> waiting = new java.util.ArrayList<Bank>();
        for (Bank b : BankManager.banks()) {
            if (!b.approved) {
                waiting.add(b);
            }
        }
        if (!waiting.isEmpty()) {
            ui.header("&eЖдут одобрения", waiting.size() + "");
            for (final Bank b : waiting) {
                ui.row(bankIcon(b), b.name).line("&7владелец " + b.ownerName)
                        .button("Одобрить", Ui.GREEN, () -> {
                            b.approved = true;
                            BankManager.saveAll();
                        }).button("⚙", Ui.GREY, () -> manageBank = b.id);
            }
        }
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
