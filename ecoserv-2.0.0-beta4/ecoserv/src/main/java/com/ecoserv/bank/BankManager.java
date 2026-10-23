package com.ecoserv.bank;

import com.ecoserv.EcoServ;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.ui.UiError;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Валюты, кошельки, банки, счета и кредиты (2.0).
 * <ul>
 * <li>world/ecoserv/currencies.json — валюты (основная «money» — баланс мода);</li>
 * <li>wallets.json — кошельки игроков в дополнительных валютах;</li>
 * <li>banks.json — банки: сервера (проценты и кредиты платит сервер) и игроков (из своего резерва);</li>
 * <li>bank_accounts.json — счета, loans.json — кредиты, bank_settings.json — общие правила.</li>
 * </ul>
 */
public final class BankManager {

    public static final Pattern ID = Pattern.compile("^[a-z0-9_]{1,24}$");
    public static final String SERVER_BANK = "server";
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Type CURRENCIES = new TypeToken<ArrayList<Currency>>() {
    }.getType();
    private static final Type WALLETS = new TypeToken<LinkedHashMap<String, LinkedHashMap<String, Long>>>() {
    }.getType();
    private static final Type ACCOUNTS = new TypeToken<ArrayList<BankAccount>>() {
    }.getType();
    private static final Type BANKS = new TypeToken<ArrayList<Bank>>() {
    }.getType();
    private static final Type LOANS = new TypeToken<ArrayList<Loan>>() {
    }.getType();
    private static final Type SEEN = new TypeToken<HashMap<String, Long>>() {
    }.getType();

    private static final Map<String, Currency> CUR = new LinkedHashMap<String, Currency>();
    private static Map<String, LinkedHashMap<String, Long>> wallets = new LinkedHashMap<String, LinkedHashMap<String, Long>>();
    private static final Map<String, BankAccount> ACC = new LinkedHashMap<String, BankAccount>();
    private static final Map<String, Bank> BANK = new LinkedHashMap<String, Bank>();
    private static final Map<String, Loan> LOAN = new LinkedHashMap<String, Loan>();
    private static Map<String, Long> seen = new HashMap<String, Long>();
    private static BankSettings settings = new BankSettings();
    private static boolean dirty;
    private static int tick;
    private static final Random RANDOM = new Random();

    private BankManager() {
    }

    // --- загрузка -------------------------------------------------------------------------------------

    public static void load() {
        CUR.clear();
        ACC.clear();
        BANK.clear();
        LOAN.clear();
        List<Currency> list = JsonUtil.readType(DataStore.file("currencies.json"), CURRENCIES, null);
        if (list == null) {
            list = defaults();
        }
        Currency main = new Currency();
        main.id = Currency.MAIN;
        main.name = "Монеты";
        main.symbol = "";
        main.color = "&6";
        main.icon = "minecraft:gold_nugget";
        main.rate = 1.0D;
        main.description = "Основной баланс сервера";
        CUR.put(main.id, main);
        for (Currency c : list) {
            if (c == null || c.id == null) {
                continue;
            }
            c.id = c.id.trim().toLowerCase(Locale.ROOT);
            c.normalize();
            if (c.main()) {
                main.name = c.name;
                main.icon = c.icon;
                main.description = c.description;
                continue;
            }
            if (ID.matcher(c.id).matches()) {
                CUR.put(c.id, c);
            }
        }
        Map<String, LinkedHashMap<String, Long>> w = JsonUtil.readType(DataStore.file("wallets.json"), WALLETS, null);
        wallets = w == null ? new LinkedHashMap<String, LinkedHashMap<String, Long>>() : w;
        BankSettings s = JsonUtil.read(DataStore.file("bank_settings.json"), BankSettings.class, null);
        settings = s == null ? new BankSettings() : s;
        List<Bank> banks = JsonUtil.readType(DataStore.file("banks.json"), BANKS, null);
        if (banks != null) {
            for (Bank b : banks) {
                if (b != null && b.id != null && !b.id.isEmpty()) {
                    b.normalize();
                    BANK.put(b.id, b);
                }
            }
        }
        if (!BANK.containsKey(SERVER_BANK)) {
            // «Центральный банк» — из старых общих настроек (бета 1)
            Bank c = new Bank();
            c.id = SERVER_BANK;
            c.name = "&6Центральный банк";
            c.description = "Банк сервера: вклады под процент, кредиты";
            c.icon = "minecraft:gold_block";
            c.loans = true;
            if (settings.interestPercent != null) {
                c.interestPercent = settings.interestPercent;
            }
            if (settings.interestCap != null) {
                c.interestCap = settings.interestCap;
            }
            if (settings.interestOnlyOnline != null) {
                c.interestOnlyOnline = settings.interestOnlyOnline;
            }
            if (settings.transferFee != null) {
                c.transferFee = settings.transferFee;
            }
            if (settings.maxAccounts != null) {
                c.maxAccounts = settings.maxAccounts;
            }
            if (settings.accountPrice != null) {
                c.accountPrice = settings.accountPrice;
            }
            if (settings.maxMembers != null) {
                c.maxMembers = settings.maxMembers;
            }
            c.created = System.currentTimeMillis();
            c.normalize();
            BANK.put(c.id, c);
        }
        settings.interestPercent = null;
        settings.interestCap = null;
        settings.interestOnlyOnline = null;
        settings.transferFee = null;
        settings.maxAccounts = null;
        settings.accountPrice = null;
        settings.maxMembers = null;
        List<BankAccount> accounts = JsonUtil.readType(DataStore.file("bank_accounts.json"), ACCOUNTS, null);
        if (accounts != null) {
            for (BankAccount a : accounts) {
                if (a != null && a.code != null && !a.code.isEmpty()) {
                    a.normalize();
                    if (!BANK.containsKey(a.bank)) {
                        a.bank = SERVER_BANK;
                    }
                    ACC.put(a.code, a);
                }
            }
        }
        List<Loan> loans = JsonUtil.readType(DataStore.file("loans.json"), LOANS, null);
        if (loans != null) {
            for (Loan l : loans) {
                if (l != null && l.id != null && !l.id.isEmpty()) {
                    LOAN.put(l.id, l);
                }
            }
        }
        Map<String, Long> sn = JsonUtil.readType(DataStore.file("bank_seen.json"), SEEN, null);
        seen = sn == null ? new HashMap<String, Long>() : sn;
        dirty = false;
        saveAll();
    }

    private static List<Currency> defaults() {
        List<Currency> out = new ArrayList<Currency>();
        Currency gems = new Currency();
        gems.id = "gems";
        gems.name = "Кристаллы";
        gems.symbol = "♦";
        gems.color = "&b";
        gems.icon = "minecraft:diamond";
        gems.rate = 100.0D;
        gems.description = "Пример второй валюты: 1 кристалл = 100 монет";
        out.add(gems);
        Currency tokens = new Currency();
        tokens.id = "tokens";
        tokens.name = "Жетоны";
        tokens.symbol = "т";
        tokens.color = "&d";
        tokens.icon = "minecraft:prismarine_crystals";
        tokens.rate = 0.0D;
        tokens.exchangeable = false;
        tokens.description = "Пример ивент-валюты: не меняется на монеты, выдаётся за мини-игры и квесты";
        out.add(tokens);
        return out;
    }

    public static void saveAll() {
        JsonUtil.write(DataStore.file("currencies.json"), new ArrayList<Currency>(CUR.values()));
        JsonUtil.write(DataStore.file("wallets.json"), wallets);
        JsonUtil.write(DataStore.file("banks.json"), new ArrayList<Bank>(BANK.values()));
        JsonUtil.write(DataStore.file("bank_accounts.json"), new ArrayList<BankAccount>(ACC.values()));
        JsonUtil.write(DataStore.file("loans.json"), new ArrayList<Loan>(LOAN.values()));
        JsonUtil.write(DataStore.file("bank_settings.json"), settings);
        JsonUtil.write(DataStore.file("bank_seen.json"), seen);
        dirty = false;
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void tick() {
        tick++;
        if (tick % 600 == 0) {
            long day = today();
            for (EntityPlayerMP p : PlayerUtil.allOnline()) {
                Long was = seen.put(p.getUniqueID().toString(), day);
                if (was == null || was != day) {
                    dirty = true;
                }
            }
            payInterest();
            collectLoans();
        }
        if (dirty && tick % 600 == 300) {
            saveAll();
        }
    }

    public static void onStop() {
        if (dirty) {
            saveAll();
        }
    }

    public static BankSettings settings() {
        return settings;
    }

    // --- валюты ---------------------------------------------------------------------------------------

    public static List<Currency> currencies() {
        return new ArrayList<Currency>(CUR.values());
    }

    public static List<Currency> enabledCurrencies() {
        List<Currency> out = new ArrayList<Currency>();
        for (Currency c : CUR.values()) {
            if (c.enabled || c.main()) {
                out.add(c);
            }
        }
        return out;
    }

    @Nullable
    public static Currency currency(String id) {
        return id == null ? null : CUR.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public static Currency main() {
        return CUR.get(Currency.MAIN);
    }

    /** Новая валюта; null — id занят или неверный. */
    @Nullable
    public static Currency createCurrency(String id, String name) {
        String k = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(k).matches() || CUR.containsKey(k)) {
            return null;
        }
        Currency c = new Currency();
        c.id = k;
        c.name = name == null || name.trim().isEmpty() ? k : name.trim();
        c.normalize();
        CUR.put(k, c);
        saveAll();
        return c;
    }

    /** Удалить валюту: кошельки и счета в ней обнуляются. */
    public static boolean deleteCurrency(String id) {
        Currency c = currency(id);
        if (c == null || c.main()) {
            return false;
        }
        CUR.remove(c.id);
        for (Map<String, Long> w : wallets.values()) {
            w.remove(c.id);
        }
        for (BankAccount a : ACC.values()) {
            a.balances.remove(c.id);
        }
        saveAll();
        return true;
    }

    // --- кошельки -------------------------------------------------------------------------------------

    public static long balance(UUID player, String currency) {
        if (Currency.MAIN.equals(currency)) {
            return EconomyManager.get(player);
        }
        Map<String, Long> w = wallets.get(player.toString());
        if (w == null) {
            return 0L;
        }
        Long v = w.get(currency);
        return v == null ? 0L : v;
    }

    public static void give(UUID player, String currency, long amount) {
        if (amount <= 0L) {
            return;
        }
        if (Currency.MAIN.equals(currency)) {
            EconomyManager.give(player, amount);
            return;
        }
        LinkedHashMap<String, Long> w = wallets.get(player.toString());
        if (w == null) {
            w = new LinkedHashMap<String, Long>();
            wallets.put(player.toString(), w);
        }
        long now = Math.max(0L, (w.containsKey(currency) ? w.get(currency) : 0L)) + amount;
        w.put(currency, now);
        dirty = true;
    }

    public static boolean take(UUID player, String currency, long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (Currency.MAIN.equals(currency)) {
            return EconomyManager.take(player, amount);
        }
        LinkedHashMap<String, Long> w = wallets.get(player.toString());
        long have = w == null || !w.containsKey(currency) ? 0L : w.get(currency);
        if (have < amount) {
            return false;
        }
        if (have == amount) {
            w.remove(currency);
        } else {
            w.put(currency, have - amount);
        }
        dirty = true;
        return true;
    }

    public static void set(UUID player, String currency, long amount) {
        if (Currency.MAIN.equals(currency)) {
            EconomyManager.set(player, Math.max(0L, amount));
            return;
        }
        LinkedHashMap<String, Long> w = wallets.get(player.toString());
        if (w == null) {
            w = new LinkedHashMap<String, Long>();
            wallets.put(player.toString(), w);
        }
        if (amount <= 0L) {
            w.remove(currency);
        } else {
            w.put(currency, amount);
        }
        dirty = true;
    }

    /** Перевод из кошелька в кошелёк. null — получилось, иначе причина. */
    @Nullable
    public static String pay(EntityPlayerMP from, UUID to, String currency, long amount) {
        Currency c = currency(currency);
        if (c == null || (!c.enabled && !c.main())) {
            return "&cНет такой валюты.";
        }
        if (!c.transferable && !c.main()) {
            return "&cЭту валюту нельзя переводить.";
        }
        if (amount <= 0L) {
            return "&cСумма должна быть больше нуля.";
        }
        if (to.equals(from.getUniqueID())) {
            return "&cСебе переводить незачем.";
        }
        if (!take(from.getUniqueID(), c.id, amount)) {
            return "&cНе хватает: у тебя " + c.format(balance(from.getUniqueID(), c.id));
        }
        give(to, c.id, amount);
        EntityPlayerMP target = PlayerUtil.online(to);
        if (target != null) {
            com.ecoserv.util.ChatUtil.send(target, "&a" + from.getName() + " перевёл тебе " + c.format(amount));
        }
        return null;
    }

    // --- обмен ----------------------------------------------------------------------------------------

    /** Сколько получится при обмене (с комиссией). -1 — обмен невозможен. */
    public static long quote(String from, String to, long amount) {
        Currency a = currency(from);
        Currency b = currency(to);
        if (a == null || b == null || a == b || amount <= 0L) {
            return -1L;
        }
        if (!a.main() && (!a.exchangeable || a.rate <= 0.0D || !a.enabled)) {
            return -1L;
        }
        if (!b.main() && (!b.exchangeable || b.rate <= 0.0D || !b.enabled)) {
            return -1L;
        }
        double value = amount * (a.main() ? 1.0D : a.rate);
        value *= 1.0D - Math.max(0.0D, Math.min(100.0D, settings.exchangeFee)) / 100.0D;
        double out = value / (b.main() ? 1.0D : b.rate);
        if (Double.isNaN(out) || out > Long.MAX_VALUE / 4.0D) {
            return -1L;
        }
        return (long) Math.floor(out);
    }

    @Nullable
    public static String exchange(EntityPlayerMP p, String from, String to, long amount) {
        long got = quote(from, to, amount);
        if (got < 0L) {
            return "&cЭти валюты не меняются друг на друга.";
        }
        if (got == 0L) {
            return "&cСлишком мало для обмена.";
        }
        Currency a = currency(from);
        Currency b = currency(to);
        if (!take(p.getUniqueID(), a.id, amount)) {
            return "&cНе хватает: у тебя " + a.format(balance(p.getUniqueID(), a.id));
        }
        give(p.getUniqueID(), b.id, got);
        return null;
    }

    // --- банки ----------------------------------------------------------------------------------------

    public static List<Bank> banks() {
        List<Bank> out = new ArrayList<Bank>(BANK.values());
        out.sort((a, b) -> {
            if (a.server() != b.server()) {
                return a.server() ? -1 : 1;
            }
            return com.ecoserv.util.ChatUtil.strip(com.ecoserv.util.ChatUtil.color(a.name))
                    .compareToIgnoreCase(com.ecoserv.util.ChatUtil.strip(com.ecoserv.util.ChatUtil.color(b.name)));
        });
        return out;
    }

    @Nullable
    public static Bank bank(String id) {
        return id == null ? null : BANK.get(id);
    }

    public static Bank serverBank() {
        Bank b = BANK.get(SERVER_BANK);
        if (b == null) {
            b = new Bank();
            b.id = SERVER_BANK;
            b.name = "&6Центральный банк";
            b.normalize();
            BANK.put(b.id, b);
        }
        return b;
    }

    public static List<Bank> ownedBanks(UUID player) {
        List<Bank> out = new ArrayList<Bank>();
        for (Bank b : BANK.values()) {
            if (player.toString().equals(b.owner)) {
                out.add(b);
            }
        }
        return out;
    }

    public static boolean canManage(EntityPlayerMP p, Bank b) {
        return Perms.isAdmin(p) || (!b.server() && p.getUniqueID().toString().equals(b.owner));
    }

    /** Работает ли банк для клиентов. null — работает, иначе почему нет. */
    @Nullable
    public static String bankBlocked(Bank b) {
        if (!settings.enabled) {
            return "банки выключены на сервере";
        }
        if (b.frozen) {
            return "банк заморожен администрацией";
        }
        if (!b.approved) {
            return "банк ждёт одобрения администрации";
        }
        if (!b.server() && b.reserve < settings.minReserve) {
            return "у банка мало резерва (нужно " + Fmt.money(settings.minReserve) + ")";
        }
        return null;
    }

    private static String newBankId(String name) {
        String base = ChatUtil.strip(ChatUtil.color(name)).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9а-яё]+", "_");
        if (base.isEmpty()) {
            base = "bank";
        }
        String id = base;
        int n = 2;
        while (BANK.containsKey(id)) {
            id = base + "_" + n++;
        }
        return id;
    }

    /** Банк сервера (админ). */
    public static Bank createServerBank(String name) {
        Bank b = new Bank();
        b.id = newBankId(name);
        b.name = name.trim();
        b.created = System.currentTimeMillis();
        b.normalize();
        BANK.put(b.id, b);
        saveAll();
        return b;
    }

    /** Банк игрока: лицензия, лимит, одобрение. */
    public static Bank createPlayerBank(EntityPlayerMP p, String name) {
        boolean admin = Perms.isAdmin(p);
        if (!settings.playerBanks && !admin) {
            throw new UiError("Свои банки на сервере запрещены.");
        }
        if (name == null || name.trim().length() < 3) {
            throw new UiError("Название — хотя бы 3 символа.");
        }
        if (ownedBanks(p.getUniqueID()).size() >= settings.maxBanksPerPlayer && !admin) {
            throw new UiError("Банков у одного игрока: не больше " + settings.maxBanksPerPlayer + ".");
        }
        if (settings.bankLicense > 0 && !admin && !EconomyManager.take(p.getUniqueID(), settings.bankLicense)) {
            throw new UiError("Лицензия банка стоит " + Fmt.money(settings.bankLicense) + ".");
        }
        Bank b = new Bank();
        b.id = newBankId(name);
        b.name = UiCut.cut(name.trim(), 32);
        b.owner = p.getUniqueID().toString();
        b.ownerName = p.getName();
        b.icon = "minecraft:emerald_block";
        b.interestPercent = 0.3D;
        b.approved = !settings.needApproval || admin;
        b.created = System.currentTimeMillis();
        b.normalize();
        BANK.put(b.id, b);
        saveAll();
        if (!b.approved) {
            ChatUtil.broadcastAdmins("&eНовый банк игрока " + p.getName() + " «" + name + "» ждёт одобрения: /bank → Админ.");
        }
        return b;
    }

    /** Положить деньги в резерв своего банка. */
    public static void addReserve(EntityPlayerMP p, Bank b, long amount) {
        if (!canManage(p, b) || b.server()) {
            throw new UiError("Это не твой банк.");
        }
        if (amount <= 0 || !EconomyManager.take(p.getUniqueID(), amount)) {
            throw new UiError("Не хватает денег.");
        }
        b.reserve += amount;
        saveAll();
    }

    /** Снять из резерва (вклады клиентов не трогаются — они на их счетах). */
    public static void takeReserve(EntityPlayerMP p, Bank b, long amount) {
        if (!canManage(p, b) || b.server()) {
            throw new UiError("Это не твой банк.");
        }
        if (amount <= 0 || amount > b.reserve) {
            throw new UiError("В резерве столько нет.");
        }
        b.reserve -= amount;
        EconomyManager.give(p.getUniqueID(), amount);
        saveAll();
    }

    /** Закрыть банк: все счета — владельцам в кошельки, кредиты списываются, резерв — владельцу банка. */
    public static void closeBank(EntityPlayerMP p, Bank b) {
        if (!canManage(p, b)) {
            throw new UiError("Нет прав.");
        }
        if (SERVER_BANK.equals(b.id)) {
            throw new UiError("Центральный банк закрыть нельзя.");
        }
        for (BankAccount a : new ArrayList<BankAccount>(ACC.values())) {
            if (a.bank.equals(b.id)) {
                payOut(a);
                ACC.remove(a.code);
            }
        }
        LOAN.values().removeIf(l -> l.bank.equals(b.id));
        if (!b.server() && b.reserve > 0) {
            try {
                EconomyManager.give(UUID.fromString(b.owner), b.reserve);
            } catch (IllegalArgumentException ignored) {
                // владелец потерян
            }
        }
        BANK.remove(b.id);
        saveAll();
    }

    private static void payOut(BankAccount a) {
        try {
            UUID owner = UUID.fromString(a.owner);
            for (Map.Entry<String, Long> e : a.balances.entrySet()) {
                if (e.getValue() > 0L) {
                    give(owner, e.getKey(), e.getValue());
                }
            }
        } catch (IllegalArgumentException ex) {
            EcoServ.LOG.warn("Счёт {}: владелец {} не распознан", a.code, a.owner);
        }
    }

    /** Клиенты банка (счета). */
    public static List<BankAccount> accountsIn(Bank b) {
        List<BankAccount> out = new ArrayList<BankAccount>();
        for (BankAccount a : ACC.values()) {
            if (a.bank.equals(b.id)) {
                out.add(a);
            }
        }
        return out;
    }

    /** Сумма вкладов в основной валюте. */
    public static long deposits(Bank b) {
        long n = 0;
        for (BankAccount a : ACC.values()) {
            if (a.bank.equals(b.id)) {
                n += a.balance(Currency.MAIN);
            }
        }
        return n;
    }

    // --- счета ----------------------------------------------------------------------------------------

    public static List<BankAccount> accountsOf(UUID player) {
        String me = player.toString();
        List<BankAccount> out = new ArrayList<BankAccount>();
        for (BankAccount a : ACC.values()) {
            if (a.level(me) > 0) {
                out.add(a);
            }
        }
        return out;
    }

    public static List<BankAccount> allAccounts() {
        return new ArrayList<BankAccount>(ACC.values());
    }

    public static int ownedIn(UUID player, Bank b) {
        int n = 0;
        for (BankAccount a : ACC.values()) {
            if (player.toString().equals(a.owner) && a.bank.equals(b.id)) {
                n++;
            }
        }
        return n;
    }

    @Nullable
    public static BankAccount account(String code) {
        return code == null ? null : ACC.get(code.trim().toUpperCase(Locale.ROOT));
    }

    private static String newCode() {
        for (int tries = 0; tries < 1000; tries++) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                b.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            if (!ACC.containsKey(b.toString())) {
                return b.toString();
            }
        }
        return Long.toString(System.nanoTime(), 36).toUpperCase(Locale.ROOT);
    }

    /** Открыть счёт в банке. */
    public static BankAccount open(EntityPlayerMP p, Bank bank, String name, boolean shared) {
        boolean admin = Perms.isAdmin(p);
        String blocked = bankBlocked(bank);
        if (blocked != null && !admin) {
            throw new UiError("Нельзя: " + blocked + ".");
        }
        if (!bank.open && !admin && !p.getUniqueID().toString().equals(bank.owner)) {
            throw new UiError("Банк не принимает новых клиентов.");
        }
        int have = ownedIn(p.getUniqueID(), bank);
        if (!admin && have >= bank.maxAccounts) {
            throw new UiError("В этом банке больше " + bank.maxAccounts + " счетов открыть нельзя.");
        }
        if (!admin && bank.accountPrice > 0L) {
            if (!EconomyManager.take(p.getUniqueID(), bank.accountPrice)) {
                throw new UiError("Открытие счёта стоит " + Fmt.money(bank.accountPrice));
            }
            fee(bank, bank.accountPrice);
        }
        BankAccount a = new BankAccount();
        a.code = newCode();
        a.bank = bank.id;
        a.name = name == null || name.trim().isEmpty() ? (shared ? "Общий счёт" : "Мой счёт") : UiCut.cut(name.trim(), 32);
        a.owner = p.getUniqueID().toString();
        a.ownerName = p.getName();
        a.shared = shared;
        a.created = System.currentTimeMillis();
        a.interestDay = today();
        a.normalize();
        a.log(p.getName(), "open", Currency.MAIN, 0L, "счёт открыт");
        ACC.put(a.code, a);
        saveAll();
        return a;
    }

    /** Комиссия: банку игрока — в резерв. */
    private static void fee(Bank b, long amount) {
        if (amount > 0 && !b.server()) {
            b.reserve += amount;
            b.earned += amount;
        }
    }

    private static void need(BankAccount a, EntityPlayerMP p, int level, String what) {
        if (Perms.isAdmin(p)) {
            return;
        }
        if (a.level(p.getUniqueID().toString()) < level) {
            throw new UiError("Нет права: " + what + ".");
        }
        if (a.frozen) {
            throw new UiError("Счёт заморожен администрацией.");
        }
        Bank b = bank(a.bank);
        if (b != null && b.frozen) {
            throw new UiError("Банк заморожен администрацией.");
        }
    }

    private static Currency bankable(String id) {
        Currency c = currency(id);
        if (c == null || (!c.main() && (!c.enabled || !c.bankable))) {
            throw new UiError("Эту валюту нельзя держать в банке.");
        }
        return c;
    }

    public static void deposit(EntityPlayerMP p, BankAccount a, String currency, long amount) {
        need(a, p, 2, "класть на счёт");
        Currency c = bankable(currency);
        if (amount <= 0L) {
            throw new UiError("Сумма должна быть больше нуля.");
        }
        if (!take(p.getUniqueID(), c.id, amount)) {
            throw new UiError("Не хватает в кошельке.");
        }
        a.add(c.id, amount);
        a.log(p.getName(), "deposit", c.id, amount, "");
        saveAll();
    }

    public static void withdraw(EntityPlayerMP p, BankAccount a, String currency, long amount) {
        need(a, p, 3, "снимать со счёта");
        Currency c = bankable(currency);
        if (amount <= 0L) {
            throw new UiError("Сумма должна быть больше нуля.");
        }
        if (a.balance(c.id) < amount) {
            throw new UiError("На счёте столько нет.");
        }
        Bank b = bank(a.bank);
        long fee = b == null || !c.main() ? 0L : (long) Math.floor(amount * Math.max(0.0D, b.withdrawFee) / 100.0D);
        a.add(c.id, -amount);
        give(p.getUniqueID(), c.id, amount - fee);
        if (b != null) {
            fee(b, fee);
        }
        a.log(p.getName(), "withdraw", c.id, amount, fee > 0 ? "комиссия " + fee : "");
        saveAll();
    }

    /** Перевод со счёта на другой счёт по коду (в любом банке). */
    public static void transfer(EntityPlayerMP p, BankAccount a, String toCode, String currency, long amount) {
        need(a, p, 3, "переводить со счёта");
        Currency c = bankable(currency);
        BankAccount to = account(toCode);
        if (to == null) {
            throw new UiError("Нет счёта с кодом " + toCode + ".");
        }
        if (to == a) {
            throw new UiError("Это тот же счёт.");
        }
        if (to.frozen) {
            throw new UiError("Счёт получателя заморожен.");
        }
        if (amount <= 0L || a.balance(c.id) < amount) {
            throw new UiError("На счёте столько нет.");
        }
        Bank b = bank(a.bank);
        long fee = b == null ? 0L : (long) Math.floor(amount * Math.max(0.0D, b.transferFee) / 100.0D);
        a.add(c.id, -amount);
        to.add(c.id, amount - fee);
        if (b != null && c.main()) {
            fee(b, fee);
        }
        a.log(p.getName(), "out", c.id, amount, "→ " + to.code + (fee > 0 ? ", комиссия " + fee : ""));
        to.log(p.getName(), "in", c.id, amount - fee, "← " + a.code);
        saveAll();
    }

    public static void setMember(EntityPlayerMP p, BankAccount a, UUID who, String name, @Nullable String right) {
        need(a, p, 4, "управлять участниками");
        if (who.toString().equals(a.owner)) {
            throw new UiError("Это владелец счёта.");
        }
        Bank b = bank(a.bank);
        int max = b == null ? 10 : b.maxMembers;
        if (right == null || BankAccount.levelOf(right) == 0) {
            a.members.remove(who.toString());
            a.memberNames.remove(who.toString());
        } else {
            if (!a.members.containsKey(who.toString()) && a.members.size() >= max && !Perms.isAdmin(p)) {
                throw new UiError("Больше " + max + " участников нельзя.");
            }
            a.members.put(who.toString(), right);
            a.memberNames.put(who.toString(), name);
            a.shared = true;
        }
        saveAll();
    }

    /** Закрыть счёт: всё на нём уходит владельцу в кошелёк. */
    public static void close(EntityPlayerMP p, BankAccount a) {
        if (!Perms.isAdmin(p) && !p.getUniqueID().toString().equals(a.owner)) {
            throw new UiError("Закрыть счёт может только владелец.");
        }
        payOut(a);
        ACC.remove(a.code);
        saveAll();
    }

    // --- кредиты --------------------------------------------------------------------------------------

    public static List<Loan> loansOf(UUID player) {
        List<Loan> out = new ArrayList<Loan>();
        for (Loan l : LOAN.values()) {
            if (l.borrower.equals(player.toString())) {
                out.add(l);
            }
        }
        return out;
    }

    public static List<Loan> loansOf(Bank b) {
        List<Loan> out = new ArrayList<Loan>();
        for (Loan l : LOAN.values()) {
            if (l.bank.equals(b.id)) {
                out.add(l);
            }
        }
        return out;
    }

    public static boolean isDebtor(UUID player) {
        for (Loan l : LOAN.values()) {
            if (l.borrower.equals(player.toString()) && l.defaulted) {
                return true;
            }
        }
        return false;
    }

    /** Взять кредит. */
    public static Loan borrow(EntityPlayerMP p, Bank b, long amount, int days) {
        if (!b.loans) {
            throw new UiError("Этот банк не даёт кредиты.");
        }
        String blocked = bankBlocked(b);
        if (blocked != null) {
            throw new UiError("Нельзя: " + blocked + ".");
        }
        if (amount <= 0 || amount > b.loanMax) {
            throw new UiError("Сумма — от 1 до " + Fmt.money(b.loanMax) + ".");
        }
        if (days < 1 || days > b.loanDays) {
            throw new UiError("Срок — от 1 до " + b.loanDays + " дн.");
        }
        if (settings.blockDebtors && isDebtor(p.getUniqueID())) {
            throw new UiError("У тебя просроченный кредит — сначала верни его.");
        }
        for (Loan l : loansOf(p.getUniqueID())) {
            if (l.bank.equals(b.id)) {
                throw new UiError("В этом банке уже есть кредит — сначала верни его.");
            }
        }
        if (!b.server()) {
            if (b.reserve < amount) {
                throw new UiError("У банка нет столько денег в резерве.");
            }
            b.reserve -= amount;
        }
        Loan l = new Loan();
        l.id = Long.toString(System.currentTimeMillis(), 36) + RANDOM.nextInt(1000);
        l.bank = b.id;
        l.borrower = p.getUniqueID().toString();
        l.borrowerName = p.getName();
        l.amount = amount;
        l.remaining = amount;
        l.rate = b.loanRate;
        l.created = System.currentTimeMillis();
        l.lastDay = today();
        l.dueDay = today() + days;
        LOAN.put(l.id, l);
        EconomyManager.give(p.getUniqueID(), amount);
        saveAll();
        return l;
    }

    /** Вернуть часть или весь долг. */
    public static void repay(EntityPlayerMP p, Loan l, long amount) {
        long pay = Math.min(amount, l.remaining);
        if (pay <= 0) {
            throw new UiError("Нечего возвращать.");
        }
        if (!EconomyManager.take(p.getUniqueID(), pay)) {
            throw new UiError("Не хватает денег.");
        }
        applyPayment(l, pay);
        saveAll();
    }

    private static void applyPayment(Loan l, long pay) {
        l.remaining -= pay;
        l.paid += pay;
        Bank b = bank(l.bank);
        if (b != null && !b.server()) {
            b.reserve += pay;
            if (l.paid > l.amount) {
                b.earned += Math.min(pay, l.paid - l.amount);
            }
        }
        if (l.remaining <= 0) {
            LOAN.remove(l.id);
            EntityPlayerMP p = PlayerUtil.online(UUID.fromString(l.borrower));
            if (p != null) {
                ChatUtil.send(p, "&aКредит полностью погашен.");
            }
        }
    }

    /** Раз в сутки: процент на остаток и автосписание части долга. */
    private static void collectLoans() {
        long day = today();
        for (Loan l : new ArrayList<Loan>(LOAN.values())) {
            if (l.lastDay >= day) {
                continue;
            }
            long days = day - l.lastDay;
            l.lastDay = day;
            for (int i = 0; i < days; i++) {
                l.remaining += (long) Math.ceil(l.remaining * l.rate / 100.0D);
            }
            long left = Math.max(1, l.dueDay - day);
            long part = l.remaining / left + (l.remaining % left > 0 ? 1 : 0);
            UUID who;
            try {
                who = UUID.fromString(l.borrower);
            } catch (IllegalArgumentException e) {
                continue;
            }
            long can = Math.min(part, EconomyManager.get(who));
            if (can > 0 && EconomyManager.take(who, can)) {
                applyPayment(l, can);
            }
            if (LOAN.containsKey(l.id) && can < part) {
                l.missed++;
                if (day >= l.dueDay) {
                    l.defaulted = true;
                }
                EntityPlayerMP p = PlayerUtil.online(who);
                if (p != null) {
                    ChatUtil.send(p, "&cНе хватило денег на платёж по кредиту. Долг: " + Fmt.money(l.remaining)
                            + (l.defaulted ? " &4(просрочен)" : ""));
                }
            }
            dirty = true;
        }
    }

    // --- проценты -------------------------------------------------------------------------------------

    public static long today() {
        return java.time.LocalDate.now().toEpochDay();
    }

    private static void payInterest() {
        if (!settings.enabled) {
            return;
        }
        long day = today();
        boolean any = false;
        for (BankAccount a : ACC.values()) {
            if (a.interestDay >= day) {
                continue;
            }
            a.interestDay = day;
            any = true;
            Bank b = bank(a.bank);
            if (a.frozen || b == null || b.frozen || b.interestPercent <= 0.0D) {
                continue;
            }
            if (b.interestOnlyOnline) {
                Long last = seen.get(a.owner);
                if (last == null || last < day - 1) {
                    continue;
                }
            }
            long base = a.balance(Currency.MAIN);
            if (b.interestCap > 0L) {
                base = Math.min(base, b.interestCap);
            }
            long pay = (long) Math.floor(base * b.interestPercent / 100.0D);
            if (!b.server()) {
                pay = Math.min(pay, b.reserve);
                b.reserve -= pay;
                b.paidInterest += pay;
            }
            if (pay > 0L) {
                a.add(Currency.MAIN, pay);
                a.interestTotal += pay;
                a.log("банк", "interest", Currency.MAIN, pay, b.interestPercent + "%");
            }
        }
        if (any) {
            dirty = true;
        }
    }

    public static String kindTitle(String kind) {
        switch (kind == null ? "" : kind) {
            case "deposit":
                return "&aвнесено";
            case "withdraw":
                return "&eснято";
            case "in":
                return "&aперевод";
            case "out":
                return "&cперевод";
            case "interest":
                return "&bпроценты";
            case "open":
                return "&7открыт";
            default:
                return "&7" + kind;
        }
    }

    /** Мелочь: обрезать строку. */
    static final class UiCut {
        static String cut(String s, int n) {
            return s.length() > n ? s.substring(0, n) : s;
        }
    }
}
