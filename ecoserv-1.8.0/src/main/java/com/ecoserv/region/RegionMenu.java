package com.ecoserv.region;

import com.ecoserv.dates.AgeFilter;
import com.ecoserv.menu.ChatPrompt;
import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.role.RoleManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.Selection;
import com.ecoserv.world.WorldNames;
import com.ecoserv.world.WorldTeleport;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Меню зон-триггеров и порталов: список с поиском по названию, настройка одной области,
 * выбор мира, куда ведёт портал.
 */
public class RegionMenu extends ChestMenu {

    private enum View {
        LIST, EDIT, WORLDS
    }

    private static final int PER_PAGE = 45;

    private final View view;
    /** Для списка: какой вид показывать (null — все). */
    @Nullable
    private final String kind;
    @Nullable
    private final String name;
    private final String filter;
    private int page;

    /** Настройка одной области. */
    public RegionMenu(EntityPlayerMP player, String name) {
        this(player, View.EDIT, null, name, "", 0);
    }

    private RegionMenu(EntityPlayerMP player, View view, @Nullable String kind, @Nullable String name, String filter,
                       int page) {
        super(player, title(view, kind, name), 6);
        this.view = view;
        this.kind = kind;
        this.name = name;
        this.filter = filter == null ? "" : filter;
        this.page = page;
    }

    /** Список областей вида kind (Region.PORTAL / Region.TRIGGER / null). */
    public static void openList(EntityPlayerMP player, @Nullable String kind) {
        if (Perms.isAdmin(player)) {
            new RegionMenu(player, View.LIST, kind, null, "", 0).open();
        }
    }

    private static String title(View view, @Nullable String kind, @Nullable String name) {
        if (view == View.LIST) {
            return Region.PORTAL.equals(kind) ? "Настроить порталы" : Region.TRIGGER.equals(kind) ? "Зоны-триггеры" : "Области";
        }
        if (view == View.WORLDS) {
            return "Куда ведёт портал";
        }
        String t = (name == null ? "?" : name);
        return (t.length() > 26 ? t.substring(0, 25) + "…" : t);
    }

    @Nullable
    private Region region() {
        return name == null ? null : RegionManager.get(name);
    }

    // --- отрисовка --------------------------------------------------------------

    private List<Region> listed() {
        List<Region> out = new ArrayList<Region>();
        String f = filter.toLowerCase(Locale.ROOT);
        for (Region r : RegionManager.all(kind)) {
            if (f.isEmpty() || r.name.contains(f)) {
                out.add(r);
            }
        }
        return out;
    }

    private int pages(int count) {
        return Math.max(1, (count + PER_PAGE - 1) / PER_PAGE);
    }

    @Override
    protected void buildButtons() {
        for (int i = 0; i < topSize; i++) {
            setButton(i, ItemStack.EMPTY);
        }
        fillRow(5, Icons.filler());
        switch (view) {
            case EDIT:
                buildEdit();
                break;
            case WORLDS:
                buildWorlds();
                break;
            default:
                buildList();
        }
        setButton(53, Icons.close());
    }

    private void buildList() {
        List<Region> list = listed();
        page = Math.min(page, pages(list.size()) - 1);
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < list.size(); i++) {
            Region r = list.get(page * PER_PAGE + i);
            ItemStack icon = Icons.of(r.isPortal() ? Items.ENDER_EYE : Items.MAP, (r.enabled ? "&e" : "&7") + r.name,
                    "&7" + (r.isPortal() ? "портал → &f" + (r.target.isEmpty() ? "&cне настроен" : r.target) : "зона-триггер"),
                    "&7мир: &f" + WorldNames.displayName(r.dim),
                    "&7" + r.describeArea(),
                    r.enabled ? "" : "&cвыключен" + (r.isPortal() ? "" : "а"),
                    "&8ЛКМ — настроить");
            setButton(i, icon);
        }
        setButton(46, Icons.of(Items.COMPASS, "&eПоиск по названию" + (filter.isEmpty() ? "" : ": &f" + filter),
                "&8ЛКМ — ввести часть названия", "&8Shift+ЛКМ — сбросить поиск"));
        if (page > 0) {
            setButton(48, Icons.arrowLeft("&eНазад"));
        }
        if (page < pages(list.size()) - 1) {
            setButton(50, Icons.arrowRight("&eДальше"));
        }
        setButton(49, Icons.of(Items.BOOK, "&6Как создать",
                Region.PORTAL.equals(kind) ? "&7Палочка порталов: ПКМ — точка 1," : "&7Палочка триггера: ПКМ — точка 1,",
                "&7ПКМ — точка 2, затем название в чат.",
                "&7Всего: &f" + list.size()));
    }

    private void buildEdit() {
        Region r = region();
        if (r == null) {
            setButton(22, Icons.red("&cОбласть удалена"));
            setButton(45, Icons.back());
            return;
        }
        setButton(4, Icons.of(r.isPortal() ? Items.ENDER_EYE : Items.MAP, "&e" + r.name,
                "&7" + (r.isPortal() ? "портал" : "зона-триггер") + " в мире &f" + WorldNames.displayName(r.dim),
                "&7" + r.describeArea(),
                "&7создал: &f" + r.creator));

        setButton(10, Icons.of(Items.WRITABLE_BOOK, "&fСообщение в чат", line(r.message),
                "&8ЛКМ — изменить, Shift+ЛКМ — убрать"));
        setButton(11, Icons.of(Items.PAPER, "&fЗаголовок на экране", line(r.title),
                "&7Формат: заголовок|подзаголовок", "&8ЛКМ — изменить, Shift+ЛКМ — убрать"));
        setButton(12, Icons.of(Item.getItemFromBlock(Blocks.NOTEBLOCK), "&fЗвук", line(r.sound),
                "&7Например minecraft:entity.player.levelup", "&8ЛКМ — изменить, Shift+ЛКМ — убрать"));
        List<String> cmdLore = new ArrayList<String>();
        for (int i = 0; i < r.commands.size() && i < 5; i++) {
            cmdLore.add("&f/" + r.commands.get(i));
        }
        if (cmdLore.isEmpty()) {
            cmdLore.add("&8нет");
        }
        cmdLore.add("&7%player% — ник игрока");
        cmdLore.add("&8ЛКМ — добавить, Shift+ЛКМ — удалить все");
        setButton(13, Icons.of(Item.getItemFromBlock(Blocks.COMMAND_BLOCK), "&fКоманды (" + r.commands.size() + ")",
                cmdLore.toArray(new String[0])));
        setButton(14, Icons.of(Items.BOOK, "&fСценарий", line(r.script), "&7/script — список сценариев",
                "&8ЛКМ — выбрать, Shift+ЛКМ — убрать"));
        setButton(15, toggle(r.activation, "«Активация» для квестов",
                "&7Засчитывает условие «Активация» с названием &f" + r.name));
        setButton(16, toggle(r.enabled, "Включен" + (r.isPortal() ? "" : "а"), ""));

        setButton(19, Icons.of(Items.CLOCK, "&fКогда срабатывает: &e" + r.modeTitle(),
                "&8ЛКМ — сменить: каждый вход → один раз → с паузой"));
        setButton(20, Icons.of(Items.CLOCK, "&fПауза: &e" + r.cooldownSeconds + " с",
                "&7Для режима «с паузой»", "&8ЛКМ — изменить"));
        setButton(21, Icons.of(Items.NAME_TAG, "&fНужна роль", r.roles.isEmpty() ? "&8любой игрок" : "&f" + String.join(", ", r.roles),
                "&7Есть роли: &f" + String.join(", ", RoleManager.ids()),
                "&8ЛКМ — ввести роли через запятую, Shift+ЛКМ — убрать"));
        AgeFilter age = AgeFilter.parse(r.age);
        setButton(22, Icons.of(Item.getItemFromBlock(Blocks.CAKE), "&fПроверка возраста",
                age == null ? "&8нет" : "&f" + age.describe(),
                "&7Фильтр: 18, >=16, <=12, 14-17 (/birthday)",
                "&8ЛКМ — изменить, Shift+ЛКМ — убрать"));
        setButton(23, Icons.of(Item.getItemFromBlock(Blocks.BARRIER), "&fСообщение, если нельзя", line(r.denyMessage),
                "&8ЛКМ — изменить, Shift+ЛКМ — молча"));
        setButton(24, Icons.of(Items.MILK_BUCKET, "&fСбросить «уже срабатывало»",
                "&7Для режимов «один раз» и «с паузой»", "&8Shift+ЛКМ — сбросить у всех"));
        setButton(25, Icons.of(Items.BLAZE_POWDER, "&fПроверить на себе", "&8ЛКМ — сработать сейчас"));

        if (r.isPortal()) {
            setButton(28, Icons.of(Items.COMPASS, "&dКуда ведёт: &f" + (r.target.isEmpty() ? "&cне выбрано" : r.target),
                    "&8ЛКМ — выбрать мир или подмир"));
            String mode = "point".equals(r.targetMode) ? "в точку" : "last".equals(r.targetMode) ? "где игрок был" : "на спавн мира";
            setButton(29, Icons.of(Items.MAP, "&dКуда именно: &f" + mode,
                    "point".equals(r.targetMode) ? "&7" + (int) r.tx + " " + (int) r.ty + " " + (int) r.tz : "",
                    "&8ЛКМ — сменить: спавн → где был → точка"));
            setButton(30, Icons.of(Items.ENDER_PEARL, "&dТочка = где я стою",
                    "&7Для режима «в точку»; мир точки станет целью", "&8ЛКМ — запомнить"));
        }
        setButton(31, Icons.of(Items.ENDER_PEARL, "&fТелепорт к области", "&8ЛКМ"));
        setButton(32, Icons.of(Items.STICK, "&fПеределать границы",
                "&7Берёт текущее выделение палочкой", "&7(" + (r.isPortal() ? "порталов" : "триггера") + ")", "&8ЛКМ"));
        setButton(33, Icons.of(Items.NAME_TAG, "&fПереименовать", "&8ЛКМ"));

        setButton(45, Icons.back());
        setButton(49, Icons.of(Item.getItemFromBlock(Blocks.TNT), "&cУдалить", "&7Shift+ЛКМ"));
    }

    private void buildWorlds() {
        List<String> choices = WorldNames.worldChoices();
        page = Math.min(page, pages(choices.size()) - 1);
        Region r = region();
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < choices.size(); i++) {
            String w = choices.get(page * PER_PAGE + i);
            boolean selected = r != null && WorldNames.normalize(r.target).equals(WorldNames.normalize(w));
            Integer dim = WorldNames.resolve(w);
            ItemStack icon = Icons.of(WorldNames.isGroup(w) ? Items.ENDER_EYE : Items.ENDER_PEARL, (selected ? "&a✔ " : "&f") + w,
                    dim != null ? "&7измерение " + dim : "&7подмир", "&8ЛКМ — выбрать");
            if (selected) {
                Icons.glint(icon);
            }
            setButton(i, icon);
        }
        if (page > 0) {
            setButton(48, Icons.arrowLeft("&eНазад"));
        }
        if (page < pages(choices.size()) - 1) {
            setButton(50, Icons.arrowRight("&eДальше"));
        }
        setButton(45, Icons.back());
    }

    private static String line(String text) {
        if (text == null || text.isEmpty()) {
            return "&8нет";
        }
        return "&f" + (text.length() > 40 ? text.substring(0, 39) + "…" : text);
    }

    private static ItemStack toggle(boolean on, String title, String hint) {
        return Icons.of(Items.DYE, on ? 10 : 8, (on ? "&a" : "&c") + title + ": " + (on ? "да" : "нет"),
                hint, "&8ЛКМ — переключить");
    }

    // --- клики ------------------------------------------------------------------

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
        if (!Perms.isAdmin(player)) {
            player.closeScreen();
            return;
        }
        boolean shift = type == ClickType.QUICK_MOVE;
        if (slot == 53) {
            player.closeScreen();
            return;
        }
        switch (view) {
            case EDIT:
                clickEdit(slot, shift);
                break;
            case WORLDS:
                clickWorlds(slot);
                break;
            default:
                clickList(slot, shift);
        }
    }

    private void clickList(int slot, boolean shift) {
        List<Region> list = listed();
        if (slot == 48 && page > 0) {
            page--;
            return;
        }
        if (slot == 50 && page < pages(list.size()) - 1) {
            page++;
            return;
        }
        if (slot == 46) {
            if (shift) {
                new RegionMenu(player, View.LIST, kind, null, "", 0).open();
                return;
            }
            final String k = kind;
            ChatPrompt.ask(player, "&eЧасть названия для поиска:", new ChatPrompt.Callback() {
                @Override
                public void accept(EntityPlayerMP who, String text) {
                    new RegionMenu(who, View.LIST, k, null, text.trim(), 0).open();
                }
            });
            return;
        }
        int index = page * PER_PAGE + slot;
        if (slot < PER_PAGE && index < list.size()) {
            new RegionMenu(player, list.get(index).name).open();
        }
    }

    private void clickWorlds(int slot) {
        List<String> choices = WorldNames.worldChoices();
        if (slot == 45) {
            new RegionMenu(player, name).open();
            return;
        }
        if (slot == 48 && page > 0) {
            page--;
            return;
        }
        if (slot == 50 && page < pages(choices.size()) - 1) {
            page++;
            return;
        }
        Region r = region();
        int index = page * PER_PAGE + slot;
        if (r != null && slot < PER_PAGE && index < choices.size()) {
            r.target = choices.get(index);
            RegionManager.changed();
            ChatUtil.send(player, "&aПортал &f" + r.name + " &aведёт в &f" + r.target);
            new RegionMenu(player, r.name).open();
        }
    }

    private void clickEdit(int slot, boolean shift) {
        final Region r = region();
        if (slot == 45) {
            openList(player, r == null ? null : r.kind);
            return;
        }
        if (r == null) {
            return;
        }
        switch (slot) {
            case 10:
                text(r, shift, "&eСообщение в чат (& — цвета, %player% — ник):", new Field() {
                    @Override
                    public void set(Region reg, String v) {
                        reg.message = v;
                    }
                });
                return;
            case 11:
                text(r, shift, "&eЗаголовок: &fзаголовок|подзаголовок", new Field() {
                    @Override
                    public void set(Region reg, String v) {
                        reg.title = v;
                    }
                });
                return;
            case 12:
                text(r, shift, "&eid звука (например minecraft:block.note.pling):", new Field() {
                    @Override
                    public void set(Region reg, String v) {
                        reg.sound = v.trim();
                    }
                });
                return;
            case 13:
                if (shift) {
                    r.commands.clear();
                    RegionManager.changed();
                    return;
                }
                ask(r, "&eКоманда от сервера без «/» (%player% — ник):", new Field() {
                    @Override
                    public void set(Region reg, String v) {
                        String c = v.trim();
                        while (c.startsWith("/")) {
                            c = c.substring(1);
                        }
                        if (!c.isEmpty()) {
                            reg.commands.add(c);
                        }
                    }
                });
                return;
            case 14:
                text(r, shift, "&eИмя сценария (&f" + String.join(", ", com.ecoserv.script.ScriptManager.names()) + "&e):", new Field() {
                    @Override
                    public void set(Region reg, String v) {
                        reg.script = com.ecoserv.script.ScriptManager.key(v);
                    }
                });
                return;
            case 15:
                r.activation = !r.activation;
                RegionManager.changed();
                return;
            case 16:
                r.enabled = !r.enabled;
                RegionManager.changed();
                return;
            case 19:
                r.mode = "enter".equals(r.mode) ? "once" : "once".equals(r.mode) ? "cooldown" : "enter";
                RegionManager.changed();
                return;
            case 20:
                ask(r, "&eПауза в секундах:", new Field() {
                    @Override
                    public void set(Region reg, String v) {
                        try {
                            reg.cooldownSeconds = Math.max(0, Integer.parseInt(v.trim()));
                        } catch (NumberFormatException e) {
                            ChatUtil.send(player, "&cНужно число.");
                        }
                    }
                });
                return;
            case 21:
                if (shift) {
                    r.roles.clear();
                    RegionManager.changed();
                    return;
                }
                ask(r, "&eРоли через запятую (&f" + String.join(", ", RoleManager.ids()) + "&e):", new Field() {
                    @Override
                    public void set(Region reg, String v) {
                        reg.roles.clear();
                        for (String part : v.split(",")) {
                            if (!part.trim().isEmpty()) {
                                reg.roles.add(part.trim().toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                });
                return;
            case 22:
                if (shift) {
                    r.age = "";
                    RegionManager.changed();
                    return;
                }
                ask(r, "&eФильтр возраста: 18 (ровно), >=16, <=12, 14-17", new Field() {
                    @Override
                    public void set(Region reg, String v) {
                        if (AgeFilter.parse(v) == null) {
                            ChatUtil.send(player, "&cНе понял фильтр.");
                            return;
                        }
                        reg.age = v.trim();
                    }
                });
                return;
            case 23:
                text(r, shift, "&eСообщение тем, кому нельзя:", new Field() {
                    @Override
                    public void set(Region reg, String v) {
                        reg.denyMessage = v;
                    }
                });
                return;
            case 24:
                if (shift) {
                    int n = RegionManager.resetState(r.name, null);
                    ChatUtil.send(player, "&aСброшено у игроков: &f" + n);
                }
                return;
            case 25:
                player.closeScreen();
                RegionManager.fire(player, r, true);
                return;
            case 28:
                if (r.isPortal()) {
                    new RegionMenu(player, View.WORLDS, null, r.name, "", 0).open();
                }
                return;
            case 29:
                if (r.isPortal()) {
                    r.targetMode = "spawn".equals(r.targetMode) ? "last" : "last".equals(r.targetMode) ? "point" : "spawn";
                    RegionManager.changed();
                }
                return;
            case 30:
                if (r.isPortal()) {
                    r.targetMode = "point";
                    r.target = WorldNames.displayName(player.dimension);
                    r.tx = player.posX;
                    r.ty = player.posY;
                    r.tz = player.posZ;
                    r.tyaw = player.rotationYaw;
                    r.tpitch = player.rotationPitch;
                    RegionManager.changed();
                    ChatUtil.send(player, "&aТочка портала: &f" + r.target + " " + (int) r.tx + " " + (int) r.ty + " " + (int) r.tz);
                }
                return;
            case 31:
                player.closeScreen();
                WorldTeleport.teleport(player, WorldTeleport.near(player, r.dim, r.center()));
                return;
            case 32: {
                String tool = "region_" + r.kind;
                Selection.Sel sel = Selection.complete(player.getUniqueID(), tool, player.dimension);
                if (sel == null) {
                    ChatUtil.send(player, "&cСначала выдели новую область палочкой (обе точки).");
                    return;
                }
                r.setArea(sel.dim, sel.min(), sel.max());
                Selection.clear(player.getUniqueID(), tool);
                RegionManager.changed();
                ChatUtil.send(player, "&aНовые границы: &f" + r.describeArea());
                return;
            }
            case 33: {
                final String old = r.name;
                ChatPrompt.ask(player, "&eНовое название для &f" + old + "&e:", new ChatPrompt.Callback() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        if (RegionManager.rename(old, text)) {
                            ChatUtil.send(who, "&aПереименовано.");
                            new RegionMenu(who, RegionManager.key(text)).open();
                        } else {
                            ChatUtil.send(who, "&cНазвание занято или некорректно.");
                            new RegionMenu(who, old).open();
                        }
                    }
                });
                return;
            }
            case 49:
                if (shift) {
                    RegionManager.remove(r.name);
                    ChatUtil.send(player, "&aУдалено: &f" + r.name);
                    openList(player, r.kind);
                }
                return;
            default:
        }
    }

    private interface Field {
        void set(Region region, String value);
    }

    /** Текстовое поле: Shift+ЛКМ — очистить, ЛКМ — ввести. */
    private void text(Region r, boolean shift, String question, Field field) {
        if (shift) {
            field.set(r, "");
            RegionManager.changed();
            return;
        }
        ask(r, question, field);
    }

    private void ask(Region r, String question, final Field field) {
        final String regionName = r.name;
        ChatPrompt.ask(player, question, new ChatPrompt.Callback() {
            @Override
            public void accept(EntityPlayerMP who, String text) {
                Region current = RegionManager.get(regionName);
                if (current == null || !Perms.isAdmin(who)) {
                    return;
                }
                field.set(current, text);
                RegionManager.changed();
                new RegionMenu(who, regionName).open();
            }
        });
    }
}
