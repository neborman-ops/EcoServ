package com.ecoserv.config;

import com.ecoserv.menu.ChatPrompt;
import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Редактор config/ecoserv/ecoserv.cfg прямо в игре: разделы → параметры → (списки).
 * Переключатели меняются кликом, числа и текст — вводом в чат, Shift+клик — значение по умолчанию.
 * Изменения сразу пишутся в файл и применяются без перезапуска (см. {@link ConfigHooks}).
 */
public class ConfigEditorMenu extends ChestMenu {

    private enum Mode {
        CATEGORIES, PROPERTIES, LIST
    }

    private static final int PER_PAGE = 45;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_ADD = 47;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_DEFAULT = 51;
    private static final int SLOT_CLOSE = 53;

    /** Порядок и названия разделов (неизвестные разделы идут в конце по алфавиту). */
    private static final Map<String, String> TITLES = new LinkedHashMap<String, String>();

    static {
        TITLES.put("general", "Основное");
        TITLES.put("economy", "Экономика");
        TITLES.put("market", "Торговый терминал");
        TITLES.put("vending", "Склад продавца");
        TITLES.put("auction", "Аукцион");
        TITLES.put("trade", "Обмен между игроками");
        TITLES.put("rewards", "Награды за игру и ежедневки");
        TITLES.put("roles", "Роли");
        TITLES.put("worlds", "Миры и /world");
        TITLES.put("subworlds", "Подмиры");
        TITLES.put("inventories", "Раздельные инвентари");
        TITLES.put("restrictions", "Частичные запреты");
        TITLES.put("blacklist", "Чёрный список");
        TITLES.put("zones", "Зоны BLOCK DESTROY");
        TITLES.put("activators", "Палка активации");
        TITLES.put("content", "Блоки для контента");
        TITLES.put("dates", "Даты и дни рождения");
        TITLES.put("glitch", "GLITCH");
        TITLES.put("tools", "Инструменты");
        TITLES.put("backup", "Бэкапы");
        TITLES.put("restart", "Автоперезапуск");
        TITLES.put("announcer", "Объявления");
        TITLES.put("integration", "Интеграции (FTB)");
        TITLES.put("rcon", "RCON и команды");
    }

    private final Mode mode;
    @Nullable
    private final String category;
    @Nullable
    private final String listKey;
    private int page;

    private ConfigEditorMenu(EntityPlayerMP player, Mode mode, @Nullable String category, @Nullable String listKey,
                             int page) {
        super(player, title(mode, category, listKey), 6);
        this.mode = mode;
        this.category = category;
        this.listKey = listKey;
        this.page = Math.max(0, page);
    }

    public static void open(EntityPlayerMP player) {
        if (!Perms.isAdmin(player)) {
            ChatUtil.send(player, "&cНастройки мода может менять только админ.");
            return;
        }
        new ConfigEditorMenu(player, Mode.CATEGORIES, null, null, 0).open();
    }

    private static void reopen(EntityPlayerMP player, Mode mode, @Nullable String category, @Nullable String listKey,
                               int page) {
        new ConfigEditorMenu(player, mode, category, listKey, page).open();
    }

    public static String titleOf(String category) {
        String t = TITLES.get(category);
        return t == null ? category : t;
    }

    private static String title(Mode mode, @Nullable String category, @Nullable String listKey) {
        switch (mode) {
            case PROPERTIES:
                return cut("Настройки: " + titleOf(category));
            case LIST:
                return cut("Список: " + listKey);
            default:
                return "Настройки мода";
        }
    }

    private static String cut(String s) {
        return s.length() > 32 ? s.substring(0, 31) + "…" : s;
    }

    // --- данные -----------------------------------------------------------------

    @Nullable
    private static Configuration cfg() {
        return EcoConfig.raw();
    }

    private static List<String> categories() {
        Configuration cfg = cfg();
        List<String> out = new ArrayList<String>();
        if (cfg == null) {
            return out;
        }
        List<String> rest = new ArrayList<String>();
        for (String name : cfg.getCategoryNames()) {
            if (visibleProperties(name).isEmpty()) {
                continue;
            }
            if (TITLES.containsKey(name)) {
                continue;
            }
            rest.add(name);
        }
        for (String known : TITLES.keySet()) {
            if (cfg.hasCategory(known) && !visibleProperties(known).isEmpty()) {
                out.add(known);
            }
        }
        Collections.sort(rest);
        out.addAll(rest);
        return out;
    }

    private static List<Property> visibleProperties(@Nullable String category) {
        Configuration cfg = cfg();
        List<Property> out = new ArrayList<Property>();
        if (cfg == null || category == null || !cfg.hasCategory(category)) {
            return out;
        }
        ConfigCategory cat = cfg.getCategory(category);
        for (Property p : cat.getOrderedValues()) {
            if (!EcoConfig.isHidden(category, p.getName()) && !EcoConfig.isClientOnly(category, p.getName())) {
                out.add(p);
            }
        }
        return out;
    }

    private List<String> listValues() {
        Property p = EcoConfig.property(category, listKey);
        return p == null ? new ArrayList<String>() : new ArrayList<String>(Arrays.asList(p.getStringList()));
    }

    private int entryCount() {
        switch (mode) {
            case PROPERTIES:
                return visibleProperties(category).size();
            case LIST:
                return listValues().size();
            default:
                return categories().size();
        }
    }

    private int pages() {
        return Math.max(1, (entryCount() + PER_PAGE - 1) / PER_PAGE);
    }

    // --- отрисовка --------------------------------------------------------------

    @Override
    protected void buildButtons() {
        page = Math.min(page, pages() - 1);
        for (int i = 0; i < PER_PAGE; i++) {
            setButton(i, ItemStack.EMPTY);
        }
        fillRow(5, Icons.filler());
        int from = page * PER_PAGE;
        switch (mode) {
            case CATEGORIES: {
                List<String> cats = categories();
                for (int i = 0; i < PER_PAGE && from + i < cats.size(); i++) {
                    String name = cats.get(from + i);
                    setButton(i, Icons.of(Items.BOOK, "&e" + titleOf(name),
                            "&7Раздел: &f" + name,
                            "&7Параметров: &f" + visibleProperties(name).size(),
                            "",
                            "&8ЛКМ — открыть"));
                }
                break;
            }
            case PROPERTIES: {
                List<Property> props = visibleProperties(category);
                for (int i = 0; i < PER_PAGE && from + i < props.size(); i++) {
                    setButton(i, propertyIcon(props.get(from + i)));
                }
                setButton(SLOT_BACK, Icons.back());
                break;
            }
            case LIST: {
                List<String> values = listValues();
                for (int i = 0; i < PER_PAGE && from + i < values.size(); i++) {
                    List<String> lore = new ArrayList<String>(Icons.wrap(values.get(from + i), 40, "&f"));
                    lore.add("");
                    lore.add("&8ЛКМ — изменить");
                    lore.add("&8Shift+ЛКМ — удалить");
                    setButton(i, Icons.of(Items.PAPER, "&e№" + (from + i + 1), lore.toArray(new String[0])));
                }
                setButton(SLOT_BACK, Icons.back());
                setButton(SLOT_ADD, Icons.green("&aДобавить строку"));
                setButton(SLOT_DEFAULT, Icons.red("&cВернуть список по умолчанию", "&7Shift+ЛКМ"));
                break;
            }
            default:
                break;
        }
        if (page > 0) {
            setButton(SLOT_PREV, Icons.arrowLeft("&eНазад &7(" + page + "/" + pages() + ")"));
        }
        if (page < pages() - 1) {
            setButton(SLOT_NEXT, Icons.arrowRight("&eДальше &7(" + (page + 2) + "/" + pages() + ")"));
        }
        setButton(SLOT_INFO, Icons.of(Items.WRITABLE_BOOK, "&6Настройки EcoServ",
                "&7Файл: &fconfig/ecoserv/ecoserv.cfg",
                "&7Переключатели — клик,",
                "&7числа и текст — ввод в чат.",
                "&7Shift+клик — значение по умолчанию.",
                "&7Изменения применяются сразу.",
                "",
                "&8Из консоли/RCON: /ecoadmin config set <раздел> <ключ> <значение>"));
        setButton(SLOT_CLOSE, Icons.close());
    }

    private ItemStack propertyIcon(Property p) {
        List<String> lore = new ArrayList<String>();
        lore.addAll(Icons.wrap(p.getComment() == null ? "" : p.getComment(), 42, "&7"));
        if (!lore.isEmpty()) {
            lore.add("");
        }
        ItemStack icon;
        if (p.isList()) {
            icon = new ItemStack(Items.WRITABLE_BOOK);
            String[] values = p.getStringList();
            lore.add("&7Строк: &f" + values.length);
            for (int i = 0; i < values.length && i < 4; i++) {
                lore.add("&8• &f" + shorten(values[i], 38));
            }
            if (values.length > 4) {
                lore.add("&8…");
            }
            lore.add("");
            lore.add("&8ЛКМ — открыть список");
        } else if (p.getType() == Property.Type.BOOLEAN) {
            boolean on = p.getBoolean();
            icon = new ItemStack(Items.DYE, 1, on ? 10 : 8);
            lore.add("&7Сейчас: " + (on ? "&aвключено" : "&cвыключено"));
            lore.add("&7По умолчанию: &f" + EcoConfig.displayDefault(p));
            lore.add("");
            lore.add("&8ЛКМ — переключить");
        } else {
            boolean number = p.getType() == Property.Type.INTEGER || p.getType() == Property.Type.DOUBLE
                    || EcoConfig.isLongString(p);
            icon = new ItemStack(number ? Items.PAPER : Items.NAME_TAG);
            lore.add("&7Сейчас: &f" + shorten(EcoConfig.display(p), 40));
            lore.add("&7По умолчанию: &f" + shorten(EcoConfig.displayDefault(p), 40));
            String range = range(p);
            if (!range.isEmpty()) {
                lore.add("&7Допустимо: &f" + range);
            }
            lore.add("");
            lore.add("&8ЛКМ — ввести новое значение в чат");
        }
        lore.add("&8Shift+ЛКМ — по умолчанию");
        ItemStack stack = Icons.build(icon, "&f" + p.getName(), lore.toArray(new String[0]));
        if (!p.isDefault()) {
            Icons.glint(stack);
        }
        return stack;
    }

    private static String range(Property p) {
        String[] valid = p.getValidValues();
        if (valid != null && valid.length > 0) {
            return String.join(", ", valid);
        }
        if (p.getType() == Property.Type.INTEGER || p.getType() == Property.Type.DOUBLE) {
            String min = p.getMinValue();
            String max = p.getMaxValue();
            boolean hasMin = min != null && !min.isEmpty() && !min.equals(String.valueOf(Integer.MIN_VALUE))
                    && !min.equals(String.valueOf(-Double.MAX_VALUE));
            boolean hasMax = max != null && !max.isEmpty() && !max.equals(String.valueOf(Integer.MAX_VALUE))
                    && !max.equals(String.valueOf(Double.MAX_VALUE));
            if (hasMin && hasMax) {
                return min + " … " + max;
            }
            if (hasMin) {
                return "от " + min;
            }
            if (hasMax) {
                return "до " + max;
            }
        }
        return "";
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
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
        if (slot == SLOT_PREV && page > 0) {
            page--;
            return;
        }
        if (slot == SLOT_NEXT && page < pages() - 1) {
            page++;
            return;
        }
        if (slot == SLOT_BACK) {
            if (mode == Mode.PROPERTIES) {
                reopen(player, Mode.CATEGORIES, null, null, 0);
            } else if (mode == Mode.LIST) {
                reopen(player, Mode.PROPERTIES, category, null, 0);
            }
            return;
        }
        if (mode == Mode.LIST && slot == SLOT_ADD) {
            askListValue(-1);
            return;
        }
        if (mode == Mode.LIST && slot == SLOT_DEFAULT && shift) {
            EcoConfig.reset(category, listKey);
            ConfigHooks.apply(category);
            ChatUtil.send(player, "&aСписок &f" + category + "." + listKey + " &aвозвращён к значениям по умолчанию.");
            return;
        }
        if (slot >= PER_PAGE) {
            return;
        }
        int index = page * PER_PAGE + slot;
        switch (mode) {
            case CATEGORIES: {
                List<String> cats = categories();
                if (index < cats.size()) {
                    reopen(player, Mode.PROPERTIES, cats.get(index), null, 0);
                }
                return;
            }
            case PROPERTIES: {
                List<Property> props = visibleProperties(category);
                if (index < props.size()) {
                    clickProperty(props.get(index), shift);
                }
                return;
            }
            case LIST: {
                List<String> values = listValues();
                if (index >= values.size()) {
                    return;
                }
                if (shift) {
                    values.remove(index);
                    finishList(values);
                } else {
                    askListValue(index);
                }
                return;
            }
            default:
        }
    }

    private void clickProperty(final Property p, boolean shift) {
        final String cat = category;
        final String key = p.getName();
        final int returnPage = page;
        if (shift) {
            EcoConfig.reset(cat, key);
            ConfigHooks.apply(cat);
            ChatUtil.send(player, "&a" + cat + "." + key + " = &f" + EcoConfig.display(EcoConfig.property(cat, key))
                    + " &7(по умолчанию)");
            return;
        }
        if (p.isList()) {
            reopen(player, Mode.LIST, cat, key, 0);
            return;
        }
        if (p.getType() == Property.Type.BOOLEAN) {
            String error = EcoConfig.setChecked(cat, key, p.getBoolean() ? "false" : "true");
            report(cat, key, error);
            return;
        }
        String hint = range(p);
        ChatPrompt.ask(player, "&eНовое значение &f" + cat + "." + key + "&e (сейчас: &f"
                        + shorten(EcoConfig.display(p), 60) + "&e)" + (hint.isEmpty() ? "" : "&7, допустимо: &f" + hint),
                new ChatPrompt.Callback() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        if (!Perms.isAdmin(who)) {
                            return;
                        }
                        String error = EcoConfig.setChecked(cat, key, text);
                        reportTo(who, cat, key, error);
                        reopen(who, Mode.PROPERTIES, cat, null, returnPage);
                    }
                });
    }

    private void askListValue(final int index) {
        final String cat = category;
        final String key = listKey;
        final int returnPage = page;
        List<String> values = listValues();
        String current = index >= 0 && index < values.size() ? values.get(index) : null;
        ChatPrompt.ask(player, index < 0 ? "&eНовая строка для &f" + cat + "." + key + "&e:"
                        : "&eИзменить строку №" + (index + 1) + " (&f" + shorten(current, 60) + "&e):",
                new ChatPrompt.Callback() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        if (!Perms.isAdmin(who)) {
                            return;
                        }
                        Property p = EcoConfig.property(cat, key);
                        if (p == null) {
                            return;
                        }
                        List<String> list = new ArrayList<String>(Arrays.asList(p.getStringList()));
                        if (index >= 0 && index < list.size()) {
                            list.set(index, text);
                        } else {
                            list.add(text);
                        }
                        String error = EcoConfig.setList(cat, key, list);
                        reportTo(who, cat, key, error);
                        reopen(who, Mode.LIST, cat, key, returnPage);
                    }
                });
    }

    private void finishList(List<String> values) {
        String error = EcoConfig.setList(category, listKey, values);
        if (error == null) {
            ConfigHooks.apply(category);
            ChatUtil.send(player, "&aСтрока удалена.");
        } else {
            ChatUtil.send(player, "&c" + error);
        }
    }

    private void report(String cat, String key, @Nullable String error) {
        reportTo(player, cat, key, error);
    }

    private static void reportTo(EntityPlayerMP who, String cat, String key, @Nullable String error) {
        if (error == null) {
            ConfigHooks.apply(cat);
            ChatUtil.send(who, "&aГотово: &f" + cat + "." + key + " = " + EcoConfig.display(EcoConfig.property(cat, key)));
        } else {
            ChatUtil.send(who, "&cНе записано (" + cat + "." + key + "): " + error);
        }
    }
}
