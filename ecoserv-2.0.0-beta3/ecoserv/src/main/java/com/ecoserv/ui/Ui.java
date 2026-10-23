package com.ecoserv.ui;

import com.ecoserv.util.ChatUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Описание окна, которое собирает серверный экран (UiScreen.build) и которое клиент рисует настоящим
 * окном: заголовок, вкладки слева, список строк, переключатели, поля ввода, выпадающие списки, кнопки.
 * Обработчики действий (лямбды) остаются на сервере — клиенту уходят только их номера.
 * Тексты понимают цветовые коды через &amp;.
 */
public final class Ui {

    // --- цвета кнопок и плашек ---------------------------------------------------------------
    public static final int GREY = 0;
    public static final int BLUE = 1;
    public static final int GREEN = 2;
    public static final int RED = 3;
    public static final int YELLOW = 4;
    public static final int PURPLE = 5;

    // --- что умеет выбирать поле ---------------------------------------------------------------
    public static final String PICK_NONE = "";
    public static final String PICK_SOUND = "sound";
    public static final String PICK_ITEM = "item";
    public static final String PICK_PLAYER = "player";
    public static final String PICK_COLOR = "color";

    /** Событие от клиента: нажатие (кнопка мыши, Shift) или новое значение. */
    public static final class Event {
        public final int button;
        public final boolean shift;
        public final String value;

        Event(int button, boolean shift, String value) {
            this.button = button;
            this.shift = shift;
            this.value = value == null ? "" : value;
        }

        public boolean right() {
            return button == 1;
        }
    }

    public interface Click {
        void run();
    }

    public interface ClickEx {
        void run(Event e);
    }

    public interface Toggle {
        void set(boolean value);
    }

    public interface Text {
        void set(String value);
    }

    public interface Number {
        void set(long value);
    }

    /** Обработчики по номерам; сервер помнит их для нескольких последних версий окна. */
    final List<ClickEx> handlers = new ArrayList<ClickEx>();
    private final NBTTagCompound root = new NBTTagCompound();
    private final NBTTagList tabs = new NBTTagList();
    private final NBTTagList side = new NBTTagList();
    private final NBTTagList body = new NBTTagList();
    private final NBTTagList foot = new NBTTagList();
    private boolean search;
    /** Ошибка проверки значения, найденная во время сборки (показывается уведомлением). */
    String pendingToast;

    Ui() {
    }

    private static final java.util.regex.Pattern CODE = java.util.regex.Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    /** Цветовые коды через &; одиночный & (например, «A & B») остаётся как есть. */
    static String c(@Nullable String text) {
        return text == null ? "" : CODE.matcher(text).replaceAll("\u00a7$1");
    }

    private String register(String sig, ClickEx action) {
        String id = Integer.toString(handlers.size());
        handlers.add(action);
        return id;
    }

    private static NBTTagCompound stack(ItemStack icon) {
        NBTTagCompound tag = new NBTTagCompound();
        if (icon != null && !icon.isEmpty()) {
            icon.writeToNBT(tag);
        }
        return tag;
    }

    private static NBTTagList lines(List<String> lines) {
        NBTTagList list = new NBTTagList();
        for (String s : lines) {
            if (s != null) {
                list.appendTag(new NBTTagString(c(s)));
            }
        }
        return list;
    }

    // --- шапка ---------------------------------------------------------------------------------

    public Ui title(String title) {
        root.setString("title", c(title));
        return this;
    }

    public Ui subtitle(String subtitle) {
        root.setString("sub", c(subtitle));
        return this;
    }

    public Ui icon(ItemStack icon) {
        root.setTag("icon", stack(icon));
        return this;
    }

    /** Стрелка «назад» в заголовке. */
    public Ui back(Click action) {
        root.setString("back", register("back", e -> action.run()));
        return this;
    }

    /** Строка подсказки внизу окна. */
    public Ui status(String text) {
        root.setString("status", c(text));
        return this;
    }

    /** Поиск по строкам списка (фильтрует клиент). */
    public Ui search(String hint) {
        search = true;
        root.setString("search", c(hint));
        return this;
    }

    /** Широкие строки без боковой панели, даже если вкладок нет. */
    public Ui compact() {
        root.setBoolean("compact", true);
        return this;
    }

    // --- боковая панель ------------------------------------------------------------------------

    public Ui tab(String label, ItemStack icon, boolean selected, Click action) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("label", c(label));
        t.setTag("icon", stack(icon));
        t.setBoolean("sel", selected);
        t.setString("id", register("tab:" + ChatUtil.strip(c(label)), e -> action.run()));
        tabs.appendTag(t);
        return this;
    }

    /** Кнопка под вкладками (действия админа и т.п.). */
    public Side sideButton(String label, ItemStack icon, int color, Click action) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("label", c(label));
        t.setTag("icon", stack(icon));
        t.setInteger("color", color);
        t.setString("id", register("side:" + ChatUtil.strip(c(label)), e -> action.run()));
        side.appendTag(t);
        return new Side(t);
    }

    /** Кнопка боковой панели: можно добавить подтверждение и подсказку. */
    public static final class Side {
        private final NBTTagCompound tag;

        Side(NBTTagCompound tag) {
            this.tag = tag;
        }

        public Side confirm(String question) {
            tag.setString("confirm", c(question));
            return this;
        }

        public Side tip(String... lines) {
            List<String> list = new ArrayList<String>();
            for (String l : lines) {
                list.add(l);
            }
            tag.setTag("tip", Ui.lines(list));
            return this;
        }
    }

    /** Подпись-разделитель в боковой панели. */
    public Ui sideLabel(String text) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("label", c(text));
        t.setBoolean("caption", true);
        side.appendTag(t);
        return this;
    }

    // --- подвал ----------------------------------------------------------------------------------

    public Button footer(String label, int color, Click action) {
        Button b = new Button(label, color, e -> action.run());
        foot.appendTag(b.tag);
        return b;
    }

    // --- содержимое ------------------------------------------------------------------------------

    public Ui header(String text) {
        return header(text, "");
    }

    public Ui header(String text, String right) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("t", "h");
        t.setString("text", c(text));
        t.setString("right", c(right));
        body.appendTag(t);
        return this;
    }

    /** Абзац пояснения (переносится по ширине окна). */
    public Ui text(String text) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("t", "p");
        t.setString("text", c(text));
        body.appendTag(t);
        return this;
    }

    /** Цветная рамка-заметка: подсказка (BLUE), предупреждение (YELLOW), опасность (RED), успех (GREEN). */
    public Ui note(int color, String text) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("t", "note");
        t.setString("text", c(text));
        t.setInteger("color", color);
        body.appendTag(t);
        return this;
    }

    public Ui spacer() {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("t", "sp");
        body.appendTag(t);
        return this;
    }

    public Row row(ItemStack icon, String title) {
        Row r = new Row(icon, title);
        body.appendTag(r.tag);
        return r;
    }

    public Prop toggle(String label, String desc, boolean value, Toggle action) {
        return toggle(label, desc, value, false, action);
    }

    public Prop toggle(String label, String desc, boolean value, boolean disabled, Toggle action) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("t", "tog");
        t.setString("label", c(label));
        t.setString("desc", c(desc));
        t.setBoolean("val", value);
        t.setBoolean("dis", disabled);
        t.setString("id", register("tog:" + ChatUtil.strip(c(label)), e -> action.set("1".equals(e.value))));
        body.appendTag(t);
        return new Prop(t);
    }

    /** Переключатель (и другие строки-настройки): кнопка «вернуть по умолчанию», подсказка. */
    public final class Prop {
        final NBTTagCompound tag;

        Prop(NBTTagCompound tag) {
            this.tag = tag;
        }

        /** Маленькая кнопка ↺ рядом с названием. */
        public Prop reset(String tip, Click action) {
            tag.setString("reset", register("reset", e -> action.run()));
            tag.setString("resetTip", c(tip));
            return this;
        }

        public Prop tip(String... lines) {
            List<String> list = new ArrayList<String>();
            for (String l : lines) {
                list.add(l);
            }
            tag.setTag("tip", Ui.lines(list));
            return this;
        }
    }

    public Field field(String label, String desc, String value, Text action) {
        Field f = new Field(label, desc, value, "text", action);
        body.appendTag(f.tag);
        return f;
    }

    /** 2.0: поле пароля (на экране — звёздочки; значение не возвращается в окно). */
    public Field password(String label, String desc, Text action) {
        Field f = new Field(label, desc, "", "pass", action);
        body.appendTag(f.tag);
        return f;
    }

    /** Целое число в пределах min..max (ошибки ввода показываются уведомлением). */
    public Field number(String label, String desc, long value, final long min, final long max, final Number action) {
        final String plain = ChatUtil.strip(c(label));
        Field f = new Field(label, desc, Long.toString(value), "int", text -> {
            Long v = parseLong(text);
            if (v == null) {
                throw new UiError("«" + plain + "»: нужно целое число.");
            }
            if (v < min || v > max) {
                throw new UiError("«" + plain + "»: от " + min + " до " + max + ".");
            }
            action.set(v);
        });
        body.appendTag(f.tag);
        return f;
    }

    /** 2.0 beta3: дробное число для ползунка. */
    public interface Decimal {
        void set(double v);
    }

    /** 2.0 beta3: ползунок (тянуть мышью). */
    public Ui slider(String label, String desc, double value, double min, double max, double step, String suffix, final Decimal action) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("t", "sld");
        t.setString("label", c(label));
        t.setString("desc", c(desc));
        t.setDouble("val", value);
        t.setDouble("min", min);
        t.setDouble("max", max);
        t.setDouble("step", step);
        t.setString("suf", suffix == null ? "" : suffix);
        final double lo = min;
        final double hi = max;
        t.setString("id", register("sld:" + ChatUtil.strip(c(label)), e -> {
            try {
                double v = Double.parseDouble(e.value.trim().replace(',', '.'));
                action.set(Math.max(lo, Math.min(hi, v)));
            } catch (NumberFormatException ex) {
                throw new UiError("Нужно число.");
            }
        }));
        body.appendTag(t);
        return this;
    }

    /** 2.0 beta3: выбор цвета (0xRRGGBB). */
    public Ui color(String label, String desc, int rgb, final Number action) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("t", "clr");
        t.setString("label", c(label));
        t.setString("desc", c(desc));
        t.setInteger("val", rgb & 0xFFFFFF);
        t.setString("id", register("clr:" + ChatUtil.strip(c(label)), e -> {
            try {
                action.set(Long.parseLong(e.value.trim()) & 0xFFFFFFL);
            } catch (NumberFormatException ex) {
                throw new UiError("Нужен цвет.");
            }
        }));
        body.appendTag(t);
        return this;
    }

    /** Сумма денег: понимает 58k / 1.5m и пробелы. */
    public Field money(String label, String desc, long value, final Number action) {
        final String plain = ChatUtil.strip(c(label));
        Field f = new Field(label, desc, Long.toString(value), "money", text -> {
            Long v = parseMoney(text);
            if (v == null || v < 0) {
                throw new UiError("«" + plain + "»: нужна сумма, например 500 или 1.5k.");
            }
            action.set(v);
        });
        body.appendTag(f.tag);
        return f;
    }

    public Select select(String label, String desc, String value, Text action) {
        Select s = new Select(label, desc, value, action);
        body.appendTag(s.tag);
        return s;
    }

    public Buttons buttons() {
        Buttons b = new Buttons();
        body.appendTag(b.tag);
        return b;
    }

    public Grid grid() {
        Grid g = new Grid();
        body.appendTag(g.tag);
        return g;
    }

    /** Полоса заполнения (радиус барьера, прогресс). */
    public Ui bar(String label, double value, double max, int color) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("t", "bar");
        t.setString("label", c(label));
        t.setDouble("val", value);
        t.setDouble("max", max);
        t.setInteger("color", color);
        body.appendTag(t);
        return this;
    }

    // --- элементы с настройками ---------------------------------------------------------------------

    /** Кнопка: в подвале, в строке или в ряду кнопок. */
    public final class Button {
        final NBTTagCompound tag = new NBTTagCompound();
        @Nullable
        Row row;
        @Nullable
        Buttons group;

        Button(String label, int color, ClickEx action) {
            tag.setString("label", c(label));
            tag.setInteger("color", color);
            tag.setString("id", register("btn", action));
        }

        /** Следующая кнопка в той же строке списка. */
        public Button button(String label, int color, Click action) {
            if (row == null) {
                throw new IllegalStateException("кнопка не в строке списка");
            }
            return row.button(label, color, action);
        }

        /** 2.0: следующая кнопка в том же ряду кнопок. */
        public Button add(String label, int color, Click action) {
            if (group == null) {
                throw new IllegalStateException("кнопка не в ряду кнопок");
            }
            return group.add(label, color, action);
        }

        /** Перед действием клиент спросит подтверждение с этим текстом. */
        public Button confirm(String question) {
            tag.setString("confirm", c(question));
            return this;
        }

        public Button disabled(boolean disabled) {
            tag.setBoolean("dis", disabled);
            return this;
        }

        public Button tip(String... lines) {
            List<String> list = new ArrayList<String>();
            for (String l : lines) {
                list.add(l);
            }
            tag.setTag("tip", Ui.lines(list));
            return this;
        }
    }

    /** Строка списка: иконка, заголовок, строки описания, плашки и кнопки справа. */
    public final class Row {
        final NBTTagCompound tag = new NBTTagCompound();
        private final NBTTagList lineList = new NBTTagList();
        private final NBTTagList chips = new NBTTagList();
        private final NBTTagList buttons = new NBTTagList();
        private final String sigBase;

        Row(ItemStack icon, String title) {
            tag.setString("t", "row");
            tag.setTag("icon", stack(icon));
            tag.setString("title", c(title));
            tag.setTag("lines", lineList);
            tag.setTag("chips", chips);
            tag.setTag("btns", buttons);
            sigBase = "row:" + ChatUtil.strip(c(title));
        }

        public Row line(String text) {
            if (text != null && !text.isEmpty()) {
                lineList.appendTag(new NBTTagString(c(text)));
            }
            return this;
        }

        public Row tip(String... lines) {
            List<String> list = new ArrayList<String>();
            for (String l : lines) {
                if (l != null && !l.isEmpty()) {
                    list.add(l);
                }
            }
            tag.setTag("tip", Ui.lines(list));
            return this;
        }

        public Row tip(List<String> lines) {
            tag.setTag("tip", Ui.lines(lines));
            return this;
        }

        /** Плашка справа: «доступен», «за 500», «закрыт». */
        public Row chip(String text, int color) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("text", c(text));
            t.setInteger("color", color);
            chips.appendTag(t);
            return this;
        }

        public Button button(String label, int color, Click action) {
            Button b = new Button(label, color, e -> action.run());
            b.row = this;
            buttons.appendTag(b.tag);
            return b;
        }

        /** Нажатие на саму строку (ЛКМ). */
        public Row onClick(Click action) {
            tag.setString("id", register(sigBase, e -> action.run()));
            return this;
        }

        /** Нажатие с подробностями: ПКМ, Shift. */
        public Row onClickEx(ClickEx action) {
            tag.setString("id", register(sigBase, action));
            return this;
        }

        /** Подтверждение перед нажатием на строку. */
        public Row confirm(String question) {
            tag.setString("confirm", c(question));
            return this;
        }

        /** Выделить (текущий мир, выбранный элемент). */
        public Row highlight(boolean on) {
            tag.setBoolean("hl", on);
            return this;
        }

        public Row dim(boolean on) {
            tag.setBoolean("dim", on);
            return this;
        }
    }

    /** Поле ввода: применяется по Enter или когда фокус уходит с поля. */
    public final class Field {
        final NBTTagCompound tag = new NBTTagCompound();

        Field(String label, String desc, String value, String kind, final Text action) {
            tag.setString("t", "fld");
            tag.setString("label", c(label));
            tag.setString("desc", c(desc));
            tag.setString("val", value == null ? "" : value);
            tag.setString("kind", kind);
            tag.setInteger("max", 256);
            tag.setString("id", register("fld:" + ChatUtil.strip(c(label)), e -> action.set(e.value)));
        }

        public Field placeholder(String text) {
            tag.setString("ph", c(text));
            return this;
        }

        public Field max(int length) {
            tag.setInteger("max", length);
            return this;
        }

        /** Кнопка выбора рядом с полем: звук, предмет, игрок, цвет. */
        public Field picker(String kind) {
            tag.setString("pick", kind);
            return this;
        }

        public Field disabled(boolean disabled) {
            tag.setBoolean("dis", disabled);
            return this;
        }

        /** Кнопка «очистить» рядом с полем (отправляет пустое значение). */
        public Field clearable() {
            tag.setBoolean("clear", true);
            return this;
        }

        /** Enter в поле: сначала значение, потом это действие (например, «Готово» в окне ввода). */
        public Field onEnter(Click action) {
            tag.setString("enter", register("enter", e -> action.run()));
            return this;
        }

        /** Маленькая кнопка ↺ рядом с названием: вернуть значение по умолчанию. */
        public Field reset(String tip, Click action) {
            tag.setString("reset", register("reset", e -> action.run()));
            tag.setString("resetTip", c(tip));
            return this;
        }

        public Field tip(String... lines) {
            List<String> list = new ArrayList<String>();
            for (String l : lines) {
                list.add(l);
            }
            tag.setTag("tip", Ui.lines(list));
            return this;
        }
    }

    /** Выпадающий список. */
    public final class Select {
        final NBTTagCompound tag = new NBTTagCompound();
        private final NBTTagList options = new NBTTagList();

        Select(String label, String desc, String value, final Text action) {
            tag.setString("t", "sel");
            tag.setString("label", c(label));
            tag.setString("desc", c(desc));
            tag.setString("val", value == null ? "" : value);
            tag.setTag("opts", options);
            tag.setString("id", register("sel:" + ChatUtil.strip(c(label)), e -> action.set(e.value)));
        }

        public Select option(String key, String label) {
            return option(key, label, "", ItemStack.EMPTY);
        }

        public Select option(String key, String label, String desc) {
            return option(key, label, desc, ItemStack.EMPTY);
        }

        public Select option(String key, String label, String desc, ItemStack icon) {
            NBTTagCompound o = new NBTTagCompound();
            o.setString("k", key);
            o.setString("l", c(label));
            o.setString("d", c(desc));
            if (icon != null && !icon.isEmpty()) {
                o.setTag("icon", stack(icon));
            }
            options.appendTag(o);
            return this;
        }

        public Select disabled(boolean disabled) {
            tag.setBoolean("dis", disabled);
            return this;
        }

        /** Маленькая кнопка ↺ рядом с названием: вернуть значение по умолчанию. */
        public Select reset(String tip, Click action) {
            tag.setString("reset", register("reset", e -> action.run()));
            tag.setString("resetTip", c(tip));
            return this;
        }
    }

    /** Ряд кнопок во всю ширину. */
    public final class Buttons {
        final NBTTagCompound tag = new NBTTagCompound();
        private final NBTTagList list = new NBTTagList();

        Buttons() {
            tag.setString("t", "btns");
            tag.setTag("b", list);
        }

        public Button add(String label, int color, Click action) {
            Button b = new Button(label, color, e -> action.run());
            b.group = this;
            list.appendTag(b.tag);
            return b;
        }
    }

    /** Сетка предметов (настоящие вещи с их подсказками). */
    public final class Grid {
        final NBTTagCompound tag = new NBTTagCompound();
        private final NBTTagList cells = new NBTTagList();

        Grid() {
            tag.setString("t", "grid");
            tag.setTag("cells", cells);
        }

        /** 1.9.0: ровно столько столбцов (карта чанков и т.п.); 0 — сколько влезет. */
        public Grid columns(int cols) {
            tag.setInteger("cols", cols);
            return this;
        }

        public Grid cell(ItemStack item, boolean selected, @Nullable ClickEx action, String... extraTip) {
            NBTTagCompound t = new NBTTagCompound();
            NBTTagCompound it = new NBTTagCompound();
            if (item != null && !item.isEmpty()) {
                item.writeToNBT(it);
                // количество больше 64 (хранилище) — отдельно, ItemStack в NBT держит только байт
                t.setInteger("count", item.getCount());
            }
            t.setTag("item", it);
            t.setBoolean("sel", selected);
            List<String> tip = new ArrayList<String>();
            for (String s : extraTip) {
                if (s != null && !s.isEmpty()) {
                    tip.add(s);
                }
            }
            t.setTag("tip", Ui.lines(tip));
            if (action != null) {
                t.setString("id", register("cell:" + cells.tagCount() + ":" + (item == null ? "" : item.getItem().getRegistryName()), action));
            }
            cells.appendTag(t);
            return this;
        }
    }

    // --- числа -------------------------------------------------------------------------------------

    @Nullable
    public static Long parseLong(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim().replace(" ", "").replace("_", "").replace(" ", "");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    public static Long parseMoney(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "").replace(" ", "")
                .replace("_", "").replace(',', '.');
        if (s.isEmpty()) {
            return null;
        }
        double mult = 1.0D;
        char last = s.charAt(s.length() - 1);
        if (last == 'k' || last == 'к') {
            mult = 1_000.0D;
        } else if (last == 'm' || last == 'м') {
            mult = 1_000_000.0D;
        } else if (last == 'b' || last == 'б') {
            mult = 1_000_000_000.0D;
        }
        if (mult > 1.0D) {
            s = s.substring(0, s.length() - 1);
        }
        try {
            double v = Double.parseDouble(s) * mult;
            if (Double.isNaN(v) || Double.isInfinite(v) || v > Long.MAX_VALUE / 4.0D) {
                return null;
            }
            return Math.round(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Цвет #RRGGBB или RRGGBB; null — не цвет. */
    @Nullable
    public static Integer parseColor(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String hex(int color) {
        return color < 0 ? "" : String.format("#%06X", color & 0xFFFFFF);
    }

    // --- сборка ------------------------------------------------------------------------------------

    NBTTagCompound finish(int windowId, int revision, @Nullable String toast, boolean toastError) {
        root.setInteger("win", windowId);
        root.setInteger("rev", revision);
        root.setTag("tabs", tabs);
        root.setTag("side", side);
        root.setTag("body", body);
        root.setTag("foot", foot);
        if (!search) {
            root.removeTag("search");
        }
        String t = toast != null ? toast : pendingToast;
        if (t != null && !t.isEmpty()) {
            root.setString("toast", c(t));
            root.setBoolean("toastErr", toastError || (toast == null && pendingToast != null));
        }
        return root;
    }
}
