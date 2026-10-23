package com.ecoserv.dates;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.ItemCodec;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.PlayerUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Значимые даты (п. 24) и дни рождения игроков.
 * <ul>
 * <li>config/ecoserv/dates.json — даты и предметы в подарок на день рождения;</li>
 * <li>world/ecoserv/birthdays.json — дни рождения (видят только сам игрок и админы);</li>
 * <li>world/ecoserv/date_claims.json — кто в каком году уже получил награду.</li>
 * </ul>
 */
public final class DatesManager {

    /** Файл с датами. */
    public static class Config {
        public List<SpecialDate> dates = new ArrayList<SpecialDate>();
        public List<String> birthdayItems = new ArrayList<String>();
    }

    public static class Birthday {
        /** ГГГГ-ММ-ДД */
        public String date = "";
        public String setBy = "";
        public long setAt;
        public int lastRewardYear;
    }

    private static final Type BIRTHDAYS = new TypeToken<HashMap<String, Birthday>>() {
    }.getType();
    private static final Type CLAIMS = new TypeToken<HashMap<String, HashMap<String, Integer>>>() {
    }.getType();
    /** Ключ в claims для объявлений в чат (общий на сервер). */
    private static final String SERVER = "server";

    private static Config config = new Config();
    private static Map<String, Birthday> birthdays = new HashMap<String, Birthday>();
    private static Map<String, HashMap<String, Integer>> claims = new HashMap<String, HashMap<String, Integer>>();
    private static boolean dirty;
    private static int tick;

    private DatesManager() {
    }

    private static File configFile() {
        return new File(EcoConfig.getConfigDir(), "dates.json");
    }

    // --- загрузка / сохранение ------------------------------------------------

    public static void load() {
        File file = configFile();
        Config loaded = JsonUtil.read(file, Config.class, null);
        if (loaded == null) {
            loaded = new Config();
            SpecialDate ny = new SpecialDate();
            ny.id = "new_year";
            ny.name = "Новый год";
            ny.day = 1;
            ny.month = 1;
            ny.days = 3;
            ny.money = 1000L;
            ny.message = "&6🎄 С Новым годом! &7Подарок: %reward%";
            ny.enabled = false;
            loaded.dates.add(ny);
            JsonUtil.write(file, loaded);
        }
        if (loaded.dates == null) {
            loaded.dates = new ArrayList<SpecialDate>();
        }
        if (loaded.birthdayItems == null) {
            loaded.birthdayItems = new ArrayList<String>();
        }
        List<SpecialDate> clean = new ArrayList<SpecialDate>();
        for (SpecialDate d : loaded.dates) {
            if (d != null && d.id != null && !d.id.trim().isEmpty()) {
                d.id = d.id.trim().toLowerCase(Locale.ROOT);
                d.normalize();
                clean.add(d);
            }
        }
        loaded.dates = clean;
        config = loaded;
        Map<String, Birthday> b = JsonUtil.readType(DataStore.file("birthdays.json"), BIRTHDAYS,
                new HashMap<String, Birthday>());
        birthdays = b == null ? new HashMap<String, Birthday>() : b;
        Map<String, HashMap<String, Integer>> c = JsonUtil.readType(DataStore.file("date_claims.json"), CLAIMS,
                new HashMap<String, HashMap<String, Integer>>());
        claims = c == null ? new HashMap<String, HashMap<String, Integer>>() : c;
        dirty = false;
    }

    public static void saveConfig() {
        JsonUtil.write(configFile(), config);
    }

    public static void saveIfDirty() {
        if (!dirty) {
            return;
        }
        boolean ok = JsonUtil.write(DataStore.file("birthdays.json"), birthdays);
        ok &= JsonUtil.write(DataStore.file("date_claims.json"), claims);
        if (ok) {
            dirty = false;
        }
    }

    // --- даты -----------------------------------------------------------------

    public static List<SpecialDate> dates() {
        return config.dates;
    }

    @Nullable
    public static SpecialDate get(String id) {
        String key = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (SpecialDate d : config.dates) {
            if (d.id.equals(key)) {
                return d;
            }
        }
        return null;
    }

    /** Новая дата из «дд.мм» и названия. null — дата не распознана. */
    @Nullable
    public static SpecialDate create(String dayMonth, String name) {
        int[] dm = parseDayMonth(dayMonth);
        if (dm == null) {
            return null;
        }
        SpecialDate d = new SpecialDate();
        d.day = dm[0];
        d.month = dm[1];
        d.name = name == null || name.trim().isEmpty() ? dayMonth : name.trim();
        String base = slug(d.name);
        String id = base;
        int n = 2;
        while (get(id) != null) {
            id = base + "_" + n++;
        }
        d.id = id;
        d.normalize();
        config.dates.add(d);
        saveConfig();
        return d;
    }

    public static boolean remove(String id) {
        SpecialDate d = get(id);
        if (d == null) {
            return false;
        }
        config.dates.remove(d);
        saveConfig();
        return true;
    }

    /** «дд.мм» → {день, месяц}; null — нет такой даты. */
    @Nullable
    public static int[] parseDayMonth(String text) {
        String s = text == null ? "" : text.trim().replace('/', '.').replace('-', '.');
        String[] parts = s.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            int day = Integer.parseInt(parts[0].trim());
            int month = Integer.parseInt(parts[1].trim());
            MonthDay.of(month, day);
            return new int[]{day, month};
        } catch (Exception e) {
            return null;
        }
    }

    private static String slug(String name) {
        StringBuilder sb = new StringBuilder();
        String translit = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя";
        String[] latin = {"a", "b", "v", "g", "d", "e", "e", "zh", "z", "i", "y", "k", "l", "m", "n", "o", "p", "r",
                "s", "t", "u", "f", "h", "c", "ch", "sh", "sch", "", "y", "", "e", "yu", "ya"};
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            int i = translit.indexOf(c);
            if (i >= 0) {
                sb.append(latin[i]);
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
                sb.append('_');
            }
        }
        String s = sb.toString().replaceAll("^_+|_+$", "");
        if (s.isEmpty()) {
            s = "date";
        }
        return s.length() > 24 ? s.substring(0, 24) : s;
    }

    public static List<ItemStack> birthdayItems() {
        return ItemCodec.decodeAll(config.birthdayItems);
    }

    public static void addBirthdayItem(ItemStack stack) {
        config.birthdayItems.add(ItemCodec.encode(stack));
        saveConfig();
    }

    public static void clearBirthdayItems() {
        config.birthdayItems.clear();
        saveConfig();
    }

    public static void changed() {
        for (SpecialDate d : config.dates) {
            d.normalize();
        }
        saveConfig();
    }

    // --- дни рождения ---------------------------------------------------------

    public static ZoneId zone() {
        String z = EcoConfig.datesTimezone == null ? "" : EcoConfig.datesTimezone.trim();
        if (!z.isEmpty()) {
            try {
                return ZoneId.of(z);
            } catch (Exception e) {
                // неверный пояс — пояс сервера
            }
        }
        return ZoneId.systemDefault();
    }

    public static LocalDate today() {
        return LocalDate.now(zone());
    }

    /** «дд.мм.гггг» → дата; null — не распознано или неправдоподобно. */
    @Nullable
    public static LocalDate parseBirthday(String text) {
        String s = text == null ? "" : text.trim().replace('/', '.').replace('-', '.');
        String[] p = s.split("\\.");
        if (p.length != 3) {
            return null;
        }
        try {
            int a = Integer.parseInt(p[0].trim());
            int b = Integer.parseInt(p[1].trim());
            int c = Integer.parseInt(p[2].trim());
            LocalDate date = a > 31 ? LocalDate.of(a, b, c) : LocalDate.of(c, b, a);
            LocalDate today = today();
            if (date.isAfter(today) || date.getYear() < 1900) {
                return null;
            }
            return date;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static LocalDate birthday(UUID player) {
        Birthday b = player == null ? null : birthdays.get(player.toString());
        if (b == null || b.date == null || b.date.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(b.date);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static boolean hasBirthday(UUID player) {
        return birthday(player) != null;
    }

    public static void setBirthday(UUID player, LocalDate date, String by) {
        Birthday b = birthdays.get(player.toString());
        if (b == null) {
            b = new Birthday();
            birthdays.put(player.toString(), b);
        }
        b.date = date.toString();
        b.setBy = by == null ? "" : by;
        b.setAt = System.currentTimeMillis();
        dirty = true;
        saveIfDirty();
    }

    public static boolean clearBirthday(UUID player) {
        boolean removed = birthdays.remove(player.toString()) != null;
        if (removed) {
            dirty = true;
            saveIfDirty();
        }
        return removed;
    }

    /** Возраст полных лет; -1 — день рождения не указан (или ввод выключен). */
    public static int age(@Nullable UUID player) {
        if (!EcoConfig.birthdaysEnabled || player == null) {
            return -1;
        }
        LocalDate b = birthday(player);
        return b == null ? -1 : Period.between(b, today()).getYears();
    }

    /**
     * Проверка возраста: подходит ли игрок под фильтр. Если ввод дня рождения выключен,
     * проверка не применяется (true); если не указан — false.
     */
    public static boolean checkAge(@Nullable UUID player, @Nullable AgeFilter filter) {
        if (filter == null || !EcoConfig.birthdaysEnabled) {
            return true;
        }
        return filter.matches(age(player));
    }

    public static boolean isBirthdayToday(UUID player) {
        LocalDate b = birthday(player);
        if (b == null) {
            return false;
        }
        LocalDate today = today();
        return MonthDay.from(b).atYear(today.getYear()).equals(today);
    }

    // --- выдача наград --------------------------------------------------------

    /** Раз в секунду из ServerServices; реальная проверка — раз в минуту. */
    public static void tick() {
        if (++tick % 60 != 0) {
            return;
        }
        LocalDate today = today();
        if (EcoConfig.datesEnabled) {
            for (SpecialDate d : config.dates) {
                int year = d.enabled ? d.activeYear(today) : -1;
                if (year > 0 && d.broadcast && claimYear(SERVER, d.id) != year) {
                    setClaim(SERVER, d.id, year);
                    ChatUtil.broadcast("&6✦ Сегодня: &f" + d.name + "&6! &7Зайди в игру и получи подарок.");
                }
            }
        }
        for (EntityPlayerMP player : PlayerUtil.allOnline()) {
            check(player);
        }
        saveIfDirty();
    }

    /** Проверить даты и день рождения игрока (при входе и раз в минуту). */
    public static void check(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        LocalDate today = today();
        String uuid = player.getUniqueID().toString();
        if (EcoConfig.datesEnabled) {
            for (SpecialDate d : config.dates) {
                if (!d.enabled) {
                    continue;
                }
                int year = d.activeYear(today);
                if (year > 0 && claimYear(uuid, d.id) != year) {
                    setClaim(uuid, d.id, year);
                    String reward = give(player, d.money, ItemCodec.decodeAll(d.items));
                    String msg = d.message == null || d.message.isEmpty()
                            ? "&6✦ " + d.name + "! &7Подарок: %reward%" : d.message;
                    ChatUtil.send(player, msg.replace("%player%", player.getName()).replace("%reward%", reward)
                            .replace("%name%", d.name));
                }
            }
        }
        if (EcoConfig.birthdaysEnabled && isBirthdayToday(player.getUniqueID())) {
            Birthday b = birthdays.get(uuid);
            if (b != null && b.lastRewardYear != today.getYear()) {
                b.lastRewardYear = today.getYear();
                dirty = true;
                String reward = give(player, EcoConfig.birthdayMoney, birthdayItems());
                ChatUtil.send(player, EcoConfig.birthdayMessage.replace("%player%", player.getName())
                        .replace("%reward%", reward));
                if (EcoConfig.birthdayBroadcast) {
                    ChatUtil.broadcast("&d🎂 Сегодня день рождения у &f" + player.getName() + "&d! Поздравим!");
                }
            }
        }
    }

    private static String give(EntityPlayerMP player, long money, List<ItemStack> items) {
        StringBuilder sb = new StringBuilder();
        if (money > 0L) {
            EconomyManager.give(player.getUniqueID(), money);
            sb.append("&e").append(Fmt.money(money));
        }
        for (ItemStack stack : items) {
            PlayerUtil.giveOrDrop(player, stack.copy());
        }
        if (!items.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" &7+ ");
            }
            sb.append("&f").append(ItemCodec.describe(items, 4));
        }
        if (money > 0L) {
            com.ecoserv.network.PacketHandler.sendPlayerState(player);
        }
        return sb.length() == 0 ? "&7поздравление" : sb.toString();
    }

    private static int claimYear(String who, String dateId) {
        Map<String, Integer> map = claims.get(who);
        Integer year = map == null ? null : map.get(dateId);
        return year == null ? -1 : year;
    }

    private static void setClaim(String who, String dateId, int year) {
        HashMap<String, Integer> map = claims.get(who);
        if (map == null) {
            map = new HashMap<String, Integer>();
            claims.put(who, map);
        }
        map.put(dateId, year);
        dirty = true;
    }

    /** Для отладки/поддержки: сбросить получение даты игроком (снова получит сегодня). */
    public static void resetClaim(UUID player, String dateId) {
        Map<String, Integer> map = claims.get(player.toString());
        if (map != null && map.remove(dateId) != null) {
            dirty = true;
        }
    }

    public static void logSummary() {
        EcoServ.LOG.info("Даты: {} шт., дни рождения: {} ({})", config.dates.size(), birthdays.size(),
                EcoConfig.birthdaysEnabled ? "ввод включён" : "ввод выключен");
    }
}
