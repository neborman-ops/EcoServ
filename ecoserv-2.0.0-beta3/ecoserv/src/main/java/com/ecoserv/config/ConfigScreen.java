package com.ecoserv.config;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
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
 * Настройки мода прямо в игре (config/ecoserv/ecoserv.cfg): разделы слева, параметры справа —
 * переключатели, поля, списки. Изменения сразу пишутся в файл и применяются без перезапуска.
 */
public class ConfigScreen extends UiScreen {

    /** Порядок и названия разделов (неизвестные разделы идут в конце по алфавиту). */
    private static final Map<String, String> TITLES = new LinkedHashMap<String, String>();
    private static final Map<String, ItemStack> ICONS = new LinkedHashMap<String, ItemStack>();

    static {
        add("general", "Основное", UiIcons.gear());
        add("economy", "Экономика", UiIcons.money());
        add("market", "Торговый терминал", UiIcons.market());
        add("vending", "Склад продавца", UiIcons.vending());
        add("auction", "Аукцион", UiIcons.auction());
        add("trade", "Обмен между игроками", UiIcons.trade());
        add("rewards", "Награды за игру", UiIcons.hourglass());
        add("roles", "Роли", UiIcons.role());
        add("worlds", "Миры и /world", UiIcons.compass());
        add("subworlds", "Подмиры", UiIcons.planet());
        add("inventories", "Раздельные инвентари", UiIcons.backpack());
        add("restrictions", "Частичные запреты", UiIcons.ban());
        add("blacklist", "Чёрный список", UiIcons.bomb());
        add("zones", "Зоны BLOCK DESTROY", UiIcons.pickaxe());
        add("activators", "Палка активации", UiIcons.wand());
        add("content", "Блоки для контента", UiIcons.cube());
        add("dates", "Даты и дни рождения", UiIcons.cake());
        add("glitch", "GLITCH", UiIcons.glitch());
        add("tools", "Инструменты", UiIcons.anvil());
        add("backup", "Бэкапы", UiIcons.disk());
        add("restart", "Автоперезапуск", UiIcons.power());
        add("announcer", "Объявления", UiIcons.megaphone());
        add("integration", "Интеграции (FTB)", UiIcons.questBook());
        add("rcon", "RCON и команды", UiIcons.terminal());
        add("portals", "Порталы: защита после телепорта", UiIcons.portal());
        add("money", "Деньги: история и заказы", UiIcons.orders());
        add("minigame", "Мини-игра: квестовая гонка", UiIcons.race());
        add("computercraft", "ComputerCraft", UiIcons.terminal());
        add("warps", "Варпы", UiIcons.compass());
        add("claims", "Приваты территории", UiIcons.map());
        add("player_worlds", "Свои миры игроков", UiIcons.planet());
        add("games", "Мини-игры", UiIcons.race());
    }

    private static void add(String key, String title, ItemStack icon) {
        TITLES.put(key, title);
        ICONS.put(key, icon);
    }

    @Nullable
    private String category;
    /** Открытый список (ключ параметра) или null. */
    @Nullable
    private String listKey;

    public ConfigScreen(EntityPlayerMP player) {
        super(player);
    }

    public static void open(EntityPlayerMP player) {
        if (!Perms.isAdmin(player)) {
            ChatUtil.send(player, "&cНастройки мода может менять только админ.");
            return;
        }
        new ConfigScreen(player).open();
    }

    public static String titleOf(String category) {
        String t = TITLES.get(category);
        return t == null ? category : t;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    // --- данные ------------------------------------------------------------------

    private static List<String> categories() {
        Configuration cfg = EcoConfig.raw();
        List<String> out = new ArrayList<String>();
        if (cfg == null) {
            return out;
        }
        List<String> rest = new ArrayList<String>();
        for (String name : cfg.getCategoryNames()) {
            if (!visible(name).isEmpty() && !TITLES.containsKey(name)) {
                rest.add(name);
            }
        }
        for (String known : TITLES.keySet()) {
            if (cfg.hasCategory(known) && !visible(known).isEmpty()) {
                out.add(known);
            }
        }
        Collections.sort(rest);
        out.addAll(rest);
        return out;
    }

    private static List<Property> visible(@Nullable String category) {
        Configuration cfg = EcoConfig.raw();
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

    // --- окно ------------------------------------------------------------------------

    @Override
    protected void build(Ui ui) {
        List<String> cats = categories();
        if (category == null || !cats.contains(category)) {
            category = cats.isEmpty() ? null : cats.get(0);
            listKey = null;
        }
        ui.title("Настройки мода").subtitle("config/ecoserv/ecoserv.cfg").icon(UiIcons.gear());
        ui.back(() -> com.ecoserv.subworld.menu.WorldsScreen.open(player, com.ecoserv.subworld.menu.WorldsScreen.Tab.ADMIN));
        for (final String c : cats) {
            ItemStack icon = ICONS.containsKey(c) ? ICONS.get(c) : new ItemStack(Items.BOOK);
            ui.tab(titleOf(c), icon, c.equals(category), () -> {
                category = c;
                listKey = null;
            });
        }
        ui.status("&7Применяется сразу · из консоли: /ecoadmin config set <раздел> <ключ> <значение>");
        if (category == null) {
            ui.note(Ui.RED, "Файл настроек не загружен.");
            return;
        }
        if (listKey != null) {
            buildList(ui, category, listKey);
            return;
        }
        ui.header(titleOf(category), "&8[" + category + "]");
        ui.search("поиск параметра");
        for (final Property p : visible(category)) {
            property(ui, category, p);
        }
    }

    private void property(Ui ui, final String cat, final Property p) {
        final String key = p.getName();
        String comment = p.getComment() == null ? "" : p.getComment().replace("\n", " ");
        String label = key + (p.isDefault() ? "" : " &e•");
        String def = EcoConfig.displayDefault(p);
        if (p.isList()) {
            String[] values = p.getStringList();
            Ui.Row row = ui.row(new ItemStack(Items.WRITABLE_BOOK), "&f" + label)
                    .line("&7" + comment)
                    .line("&8строк: " + values.length + (values.length > 0 ? " · " + values[0] + (values.length > 1 ? ", …" : "") : ""));
            row.onClick(() -> listKey = key);
            row.button("Открыть список", Ui.BLUE, () -> listKey = key);
            return;
        }
        if (p.getType() == Property.Type.BOOLEAN) {
            Ui.Prop t = ui.toggle(label, comment, p.getBoolean(), on -> set(cat, key, on ? "true" : "false"));
            if (!p.isDefault()) {
                t.reset("по умолчанию: " + def, () -> reset(cat, key));
            }
            return;
        }
        String[] valid = p.getValidValues();
        if (valid != null && valid.length > 0) {
            Ui.Select sel = ui.select(label, comment, p.getString(), v -> set(cat, key, v));
            for (String v : valid) {
                sel.option(v, v);
            }
            if (!p.isDefault()) {
                sel.reset("по умолчанию: " + def, () -> reset(cat, key));
            }
            return;
        }
        String hint = range(p);
        Ui.Field f = ui.field(label, comment + (hint.isEmpty() ? "" : " &8(" + hint + ")"), EcoConfig.display(p),
                v -> set(cat, key, v)).max(4096);
        f.placeholder("по умолчанию: " + def);
        if (!p.isDefault()) {
            f.reset("по умолчанию: " + def, () -> reset(cat, key));
        }
    }

    private void set(String cat, String key, String value) {
        String error = EcoConfig.setChecked(cat, key, value);
        if (error != null) {
            throw new UiError("Не записано (" + key + "): " + error);
        }
        ConfigHooks.apply(cat);
        toast(key + " = " + EcoConfig.display(EcoConfig.property(cat, key)));
    }

    private void reset(String cat, String key) {
        EcoConfig.reset(cat, key);
        ConfigHooks.apply(cat);
        toast(key + " — по умолчанию: " + EcoConfig.display(EcoConfig.property(cat, key)));
    }

    // --- списки ------------------------------------------------------------------------

    private void buildList(Ui ui, final String cat, final String key) {
        final Property p = EcoConfig.property(cat, key);
        if (p == null) {
            listKey = null;
            ui.note(Ui.RED, "Нет параметра " + key);
            return;
        }
        ui.header(key, "&8[" + cat + "]");
        if (p.getComment() != null) {
            ui.text("&7" + p.getComment().replace("\n", " "));
        }
        ui.buttons().add("← К разделу «" + titleOf(cat) + "»", Ui.GREY, () -> listKey = null);
        final List<String> values = new ArrayList<String>(Arrays.asList(p.getStringList()));
        for (int i = 0; i < values.size(); i++) {
            final int index = i;
            ui.field("№" + (i + 1), "", values.get(i), v -> {
                List<String> list = current(cat, key);
                if (index >= list.size()) {
                    return;
                }
                if (v.trim().isEmpty()) {
                    list.remove(index);
                } else {
                    list.set(index, v);
                }
                saveList(cat, key, list);
            }).clearable().max(4096);
        }
        ui.field("Добавить строку", "Enter — добавить в конец; очистить поле строки — удалить её", "", v -> {
            if (v.trim().isEmpty()) {
                return;
            }
            List<String> list = current(cat, key);
            list.add(v);
            saveList(cat, key, list);
        }).placeholder("новая строка").max(4096);
        ui.buttons().add("Вернуть список по умолчанию", Ui.RED, () -> reset(cat, key))
                .confirm("Заменить весь список «" + key + "» значениями по умолчанию?");
    }

    private static List<String> current(String cat, String key) {
        Property p = EcoConfig.property(cat, key);
        return p == null ? new ArrayList<String>() : new ArrayList<String>(Arrays.asList(p.getStringList()));
    }

    private void saveList(String cat, String key, List<String> list) {
        String error = EcoConfig.setList(cat, key, list);
        if (error != null) {
            throw new UiError("Не записано: " + error);
        }
        ConfigHooks.apply(cat);
        toast("Список «" + key + "» сохранён (" + list.size() + " строк).");
    }
}
