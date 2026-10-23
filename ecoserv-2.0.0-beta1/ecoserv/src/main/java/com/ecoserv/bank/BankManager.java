package com.ecoserv.bank;

import com.ecoserv.EcoServ;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
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
 * Валюты, кошельки и банковские счета (2.0).
 * <ul>
 * <li>world/ecoserv/currencies.json — список валют (основная «money» — баланс мода, её не удалить);</li>
 * <li>world/ecoserv/wallets.json — кошельки игроков в дополнительных валютах;</li>
 * <li>world/ecoserv/bank_accounts.json — счета;</li>
 * <li>world/ecoserv/bank_settings.json — проценты, комиссии, лимиты.</li>
 * </ul>
 */
public final class BankManager {

    public static final Pattern ID = Pattern.compile("^[a-z0-9_]{1,24}$");
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Type CURRENCIES = new TypeToken<ArrayList<Currency>>() {
    }.getType();
    private static final Type WALLETS = new TypeToken<LinkedHashMap<String, LinkedHashMap<String, Long>>>() {
    }.getType();
    private static final Type ACCOUNTS = new TypeToken<ArrayList<BankAccount>>() {
    }.getType();
    private static final Type SEEN = new TypeToken<HashMap<String, Long>>() {
    }.getType();

    private static final Map<String, Currency> CUR = new LinkedHashMap<String, Currency>();
    private static Map<String, LinkedHashMap<String, Long>> wallets = new LinkedHashMap<String, LinkedHashMap<String, Long>>();
    private static final Map<String, BankAccount> ACC = new LinkedHashMap<String, BankAccount>();
    /** Игрок -> последний день, когда был в сети (для процентов «только активным»). */
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
                // название и иконку основной валюты можно поменять
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
        List<BankAccount> accounts = JsonUtil.readType(DataStore.file("bank_accounts.json"), ACCOUNTS, null);
        if (accounts != null) {
            for (BankAccount a : accounts) {
                if (a != null && a.code != null && !a.code.isEmpty()) {
                    a.normalize();
                    ACC.put(a.code, a);
                }
            }
        }
        BankSettings s = JsonUtil.read(DataStore.file("bank_settings.json"), BankSettings.class, null);
        settings = s == null ? new BankSettings() : s;
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
        List<Currency> list = new ArrayList<Currency>(CUR.values());
        JsonUtil.write(DataStore.file("currencies.json"), list);
        JsonUtil.write(DataStore.file("wallets.json"), wallets);
        JsonUtil.write(DataStore.file("bank_accounts.json"), new ArrayList<BankAccount>(ACC.values()));
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

    public static int ownedBy(UUID player) {
        int n = 0;
        for (BankAccount a : ACC.values()) {
            if (player.toString().equals(a.owner)) {
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

    /** Открыть счёт. Ошибка — в UiError-тексте, иначе счёт. */
    public static BankAccount open(EntityPlayerMP p, String name, boolean shared) {
        boolean admin = com.ecoserv.util.Perms.isAdmin(p);
        if (!settings.enabled && !admin) {
            throw new com.ecoserv.ui.UiError("Банк выключен на сервере.");
        }
        int have = ownedBy(p.getUniqueID());
        if (!admin && have >= settings.maxAccounts) {
            throw new com.ecoserv.ui.UiError("Больше " + settings.maxAccounts + " счетов открыть нельзя.");
        }
        if (!admin && have > 0 && settings.accountPrice > 0L) {
            if (!EconomyManager.take(p.getUniqueID(), settings.accountPrice)) {
                throw new com.ecoserv.ui.UiError("Новый счёт стоит " + com.ecoserv.util.Fmt.money(settings.accountPrice));
            }
        }
        BankAccount a = new BankAccount();
        a.code = newCode();
        a.name = name == null || name.trim().isEmpty() ? (shared ? "Общий счёт" : "Мой счёт") : name.trim();
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

    private static void need(BankAccount a, EntityPlayerMP p, int level, String what) {
        if (com.ecoserv.util.Perms.isAdmin(p)) {
            return;
        }
        if (a.level(p.getUniqueID().toString()) < level) {
            throw new com.ecoserv.ui.UiError("Нет права: " + what + ".");
        }
        if (a.frozen) {
            throw new com.ecoserv.ui.UiError("Счёт заморожен администрацией.");
        }
    }

    private static Currency bankable(String id) {
        Currency c = currency(id);
        if (c == null || (!c.main() && (!c.enabled || !c.bankable))) {
            throw new com.ecoserv.ui.UiError("Эту валюту нельзя держать в банке.");
        }
        return c;
    }

    public static void deposit(EntityPlayerMP p, BankAccount a, String currency, long amount) {
        need(a, p, 2, "класть на счёт");
        Currency c = bankable(currency);
        if (amount <= 0L) {
            throw new com.ecoserv.ui.UiError("Сумма должна быть больше нуля.");
        }
        if (!take(p.getUniqueID(), c.id, amount)) {
            throw new com.ecoserv.ui.UiError("Не хватает: у тебя " + com.ecoserv.ui.UiUtil.plain(com.ecoserv.util.ChatUtil.color(c.format(balance(p.getUniqueID(), c.id)))));
        }
        a.add(c.id, amount);
        a.log(p.getName(), "deposit", c.id, amount, "");
        saveAll();
    }

    public static void withdraw(EntityPlayerMP p, BankAccount a, String currency, long amount) {
        need(a, p, 3, "снимать со счёта");
        Currency c = bankable(currency);
        if (amount <= 0L) {
            throw new com.ecoserv.ui.UiError("Сумма должна быть больше нуля.");
        }
        if (a.balance(c.id) < amount) {
            throw new com.ecoserv.ui.UiError("На счёте столько нет.");
        }
        a.add(c.id, -amount);
        give(p.getUniqueID(), c.id, amount);
        a.log(p.getName(), "withdraw", c.id, amount, "");
        saveAll();
    }

    /** Перевод со счёта на другой счёт по коду. */
    public static void transfer(EntityPlayerMP p, BankAccount a, String toCode, String currency, long amount) {
        need(a, p, 3, "переводить со счёта");
        Currency c = bankable(currency);
        BankAccount to = account(toCode);
        if (to == null) {
            throw new com.ecoserv.ui.UiError("Нет счёта с кодом " + toCode + ".");
        }
        if (to == a) {
            throw new com.ecoserv.ui.UiError("Это тот же счёт.");
        }
        if (to.frozen) {
            throw new com.ecoserv.ui.UiError("Счёт получателя заморожен.");
        }
        if (amount <= 0L || a.balance(c.id) < amount) {
            throw new com.ecoserv.ui.UiError("На счёте столько нет.");
        }
        long fee = (long) Math.floor(amount * Math.max(0.0D, settings.transferFee) / 100.0D);
        a.add(c.id, -amount);
        to.add(c.id, amount - fee);
        a.log(p.getName(), "out", c.id, amount, "→ " + to.code + (fee > 0 ? ", комиссия " + fee : ""));
        to.log(p.getName(), "in", c.id, amount - fee, "← " + a.code);
        saveAll();
    }

    public static void setMember(EntityPlayerMP p, BankAccount a, UUID who, String name, @Nullable String right) {
        need(a, p, 4, "управлять участниками");
        if (who.toString().equals(a.owner)) {
            throw new com.ecoserv.ui.UiError("Это владелец счёта.");
        }
        if (right == null || BankAccount.levelOf(right) == 0) {
            a.members.remove(who.toString());
            a.memberNames.remove(who.toString());
        } else {
            if (!a.members.containsKey(who.toString()) && a.members.size() >= settings.maxMembers
                    && !com.ecoserv.util.Perms.isAdmin(p)) {
                throw new com.ecoserv.ui.UiError("Больше " + settings.maxMembers + " участников нельзя.");
            }
            a.members.put(who.toString(), right);
            a.memberNames.put(who.toString(), name);
            a.shared = true;
        }
        saveAll();
    }

    /** Закрыть счёт: всё на нём уходит владельцу в кошелёк. */
    public static void close(EntityPlayerMP p, BankAccount a) {
        if (!com.ecoserv.util.Perms.isAdmin(p) && !p.getUniqueID().toString().equals(a.owner)) {
            throw new com.ecoserv.ui.UiError("Закрыть счёт может только владелец.");
        }
        try {
            UUID owner = UUID.fromString(a.owner);
            for (Map.Entry<String, Long> e : a.balances.entrySet()) {
                if (e.getValue() > 0L) {
                    give(owner, e.getKey(), e.getValue());
                }
            }
        } catch (IllegalArgumentException ex) {
            EcoServ.LOG.warn("Счёт {}: владелец {} не распознан, деньги пропали", a.code, a.owner);
        }
        ACC.remove(a.code);
        saveAll();
    }

    // --- проценты -------------------------------------------------------------------------------------

    public static long today() {
        return java.time.LocalDate.now().toEpochDay();
    }

    private static void payInterest() {
        if (!settings.enabled || settings.interestPercent <= 0.0D) {
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
            if (a.frozen) {
                continue;
            }
            if (settings.interestOnlyOnline) {
                Long last = seen.get(a.owner);
                if (last == null || last < day - 1) {
                    continue;
                }
            }
            long base = a.balance(Currency.MAIN);
            if (settings.interestCap > 0L) {
                base = Math.min(base, settings.interestCap);
            }
            long pay = (long) Math.floor(base * settings.interestPercent / 100.0D);
            if (pay > 0L) {
                a.add(Currency.MAIN, pay);
                a.interestTotal += pay;
                a.log("банк", "interest", Currency.MAIN, pay, settings.interestPercent + "%");
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
}
