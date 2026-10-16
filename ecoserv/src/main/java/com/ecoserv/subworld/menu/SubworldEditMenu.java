package com.ecoserv.subworld.menu;

import com.ecoserv.dates.AgeFilter;
import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.role.RoleManager;
import com.ecoserv.shop.ShopList;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldBorder;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldDims;
import com.ecoserv.subworld.SubworldInfo;
import com.ecoserv.subworld.SubworldManager;
import com.ecoserv.subworld.SubworldQuests;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldRules;
import com.ecoserv.subworld.SubworldShops;
import com.ecoserv.subworld.SubworldTeleport;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.ItemCodec;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Настройки подмира (админ): основное, правила (своя фишка 1), небо и ГЛИТЧ-бездна. */
public class SubworldEditMenu extends SubMenu {

    public enum Page {
        MAIN, RULES, LOOK
    }

    private final String id;
    private final Page view;
    private long deleteArmed;

    public SubworldEditMenu(EntityPlayerMP player, String id, Page view) {
        super(player, "Подмир: " + id, 6);
        this.id = id;
        this.view = view;
    }

    @Override
    protected ChestMenu reopen(EntityPlayerMP who) {
        return new SubworldEditMenu(who, id, view);
    }

    private Subworld sw() {
        return SubworldRegistry.get(id);
    }

    // --- отрисовка ---------------------------------------------------------------------------

    @Override
    protected void buildButtons() {
        for (int i = 0; i < topSize; i++) {
            setButton(i, ItemStack.EMPTY);
        }
        fillRow(0, filler());
        fillRow(5, filler());
        Subworld sw = sw();
        if (sw == null) {
            setButton(22, Icons.red("&cПодмир удалён"));
            setButton(45, Icons.back());
            return;
        }
        List<SubworldDim> dims = SubworldRegistry.dimsOf(sw.id);
        int instances = 0;
        for (SubworldDim d : dims) {
            if (d.isInstance()) {
                instances++;
            }
        }
        setButton(4, Icons.of(icon(sw.icon), "&6" + sw.name + " &7(" + sw.id + ")",
                "&7Тип: &f" + sw.typeTitle(),
                "&7Доступ: &f" + ChatUtil.strip(sw.accessTitle()),
                sw.perOwner() ? "&7Локальных миров: &f" + instances : "&7Общий мир для всех",
                sw.perOwner() ? "&7Версия шаблона: &f" + sw.templateVersion : "",
                "&7Создал: &f" + sw.creator + " &7" + Fmt.timestamp(sw.created),
                sw.enabled ? "&aвключён" : "&cвыключен"));
        switch (view) {
            case RULES:
                buildRules(sw);
                break;
            case LOOK:
                buildLook(sw);
                break;
            default:
                buildMain(sw, instances);
        }
        setButton(45, Icons.back());
        setButton(46, tab("Основное", Page.MAIN, new ItemStack(Items.BOOK)));
        setButton(47, tab("Правила", Page.RULES, new ItemStack(Items.CLOCK)));
        setButton(48, tab("Небо и бездна", Page.LOOK, new ItemStack(Blocks.GLASS)));
        setButton(49, Icons.close());
        boolean armed = armed(deleteArmed);
        setButton(53, Icons.of(new ItemStack(Blocks.TNT), armed ? "&c&lЕщё раз Shift+ЛКМ — удалить навсегда" : "&cУдалить подмир",
                "&7Все миры подмира и локальные миры", "&7команд будут стёрты", "&8Shift+ЛКМ"));
    }

    private ItemStack tab(String title, Page page, ItemStack stack) {
        ItemStack icon = Icons.of(stack, (view == page ? "&a▶ " : "&f") + title);
        return view == page ? Icons.glint(icon) : icon;
    }

    private void buildMain(Subworld sw, int instances) {
        setButton(10, Icons.of(Items.NAME_TAG, "&fНазвание", line(sw.name), "&8ЛКМ — изменить"));
        setButton(11, Icons.of(Items.PAPER, "&fОписание", line(sw.description), "&8ЛКМ — изменить, Shift+ЛКМ — убрать"));
        setButton(12, Icons.of(icon(sw.icon), "&fИконка", "&8ЛКМ — взять предмет из руки"));
        setButton(13, toggle(sw.enabled, "Включён", "&7Выключенный подмир видят только админы"));
        setButton(14, Icons.of(Items.GOLD_INGOT, "&fДоступ: &e" + ChatUtil.strip(sw.accessTitle()),
                "&7всегда → за оплату → достижение →", "&7наградой в квестах → по роли", "&8ЛКМ — сменить"));
        setButton(15, accessValue(sw));
        AgeFilter age = AgeFilter.parse(sw.age);
        setButton(16, Icons.of(new ItemStack(Blocks.CAKE), "&fПроверка возраста", age == null ? "&8нет" : "&f" + age.describe(),
                "&8ЛКМ — ввести, Shift+ЛКМ — убрать"));
        if (sw.perOwner()) {
            setButton(19, Icons.of(Items.BOOK, "&fСохранений у команды: &e" + sw.maxSaves, "&8ЛКМ — изменить"));
            setButton(20, Icons.of(Items.GOLD_NUGGET, "&fЦена сохранения: &e" + (sw.savePrice > 0 ? Fmt.money(sw.savePrice) : "бесплатно"),
                    "&8ЛКМ — изменить"));
            setButton(21, Icons.of(new ItemStack(Blocks.IRON_BARS), "&fБарьер: &e" + (sw.borderStart > 0
                            ? sw.borderStart + " / +" + sw.borderStep + (sw.borderMax > 0 ? " / до " + sw.borderMax : "") : "нет"),
                    "&7Радиус, шаг, предел", "&8ЛКМ — изменить, Shift+ЛКМ — без барьера"));
            setButton(23, toggle(sw.guests, "Гости", "&7Команды могут приглашать гостей", "&7в свои локальные миры"));
            setButton(31, Icons.of(Items.MAP, "&fЛокальные миры команд (" + instances + ")",
                    "&7Список, телепорт, барьер, удаление", "&8ЛКМ — открыть"));
            setButton(32, Icons.of(Items.WRITABLE_BOOK, "&a&lОпубликовать шаблон",
                    "&7Изменения шаблона попадут во все", "&7локальные миры (новые места — сразу,",
                    "&7посещённые — с заменой блоков; блоки игроков", "&7на пути правки уйдут в ГЛИТЧ-хранилище)",
                    "&7Сейчас версия: &f" + sw.templateVersion, "&8ЛКМ — опубликовать"));
        }
        setButton(22, toggle(sw.respawnInside, "Возрождение внутри", "&7Погибший игрок возрождается в подмире",
                "&7(иначе — в основном мире)"));
        setButton(24, toggle(sw.ownQuests, "Свои квесты и магазин", "&7Видны только главы квестов подмира,",
                "&7свой прайс магазина и продажи"));
        if (sw.ownQuests) {
            setButton(25, Icons.of(Items.ENCHANTED_BOOK, "&dГлавы квестов (" + sw.questChapters.size() + ")",
                    SubworldQuests.available() ? "&8ЛКМ — выбрать главы FTB Quests" : "&cFTB Quests не найден"));
            ShopList own = ShopList.scope(sw.id);
            setButton(28, Icons.of(Items.EMERALD, "&dМагазин подмира: &f" + own.size() + " позиций",
                    "&7Правь магазин как обычно, находясь", "&7в подмире (/shop price, админ-вкладка)",
                    "&8Shift+ЛКМ — скопировать общий прайс"));
        }
        setButton(29, Icons.of(new ItemStack(Blocks.BARRIER), "&fОтключённые моды",
                "&7Предметы мода не переносятся, не ставятся", "&7и не крафтятся в этом подмире", "&8ЛКМ — открыть"));
        setButton(30, toggle(sw.glitch, "Пометка GLITCH", "&7Локальный мир у каждого ИГРОКА,",
                instances > 0 ? "&cнельзя менять: уже есть локальные миры" : "&7одно сохранение"));
        setButton(33, Icons.of(Items.ENDER_PEARL, "&fВойти: " + (sw.perOwner() ? "шаблон" : "общий мир"), "&8ЛКМ"));
    }

    private ItemStack accessValue(Subworld sw) {
        switch (sw.access) {
            case Subworld.ACCESS_PRICE:
                return Icons.of(Items.GOLD_NUGGET, "&fЦена доступа: &e" + Fmt.money(sw.price), "&8ЛКМ — изменить");
            case Subworld.ACCESS_ADVANCEMENT:
                return Icons.of(Items.EXPERIENCE_BOTTLE, "&fДостижение: " + line(sw.advancement), "&8ЛКМ — изменить");
            case Subworld.ACCESS_ROLE:
                return Icons.of(Items.NAME_TAG, "&fРоли: " + line(String.join(", ", sw.roles)),
                        "&7Есть: &f" + String.join(", ", RoleManager.ids()), "&8ЛКМ — изменить");
            case Subworld.ACCESS_QUEST:
                return Icons.of(Items.WRITABLE_BOOK, "&fНаградой в квестах",
                        "&7Награда FTB Quests «Доступ к подмиру»", "&7(выдать вручную: /subworld unlock " + sw.id + " <ник>)");
            default:
                return Icons.of(Items.EMERALD, "&fДоступен всем");
        }
    }

    private void buildRules(Subworld sw) {
        Subworld.Rules r = sw.rules;
        long fixed = SubworldInfo.fixedTime(r.time);
        setButton(10, Icons.of(Items.CLOCK, "&fВремя: &e" + timeTitle(r.time),
                fixed >= 0 ? "&7Всегда " + fixed + " тиков" : "&7Обычная смена дня и ночи",
                "&8ЛКМ — следующее, Shift+ЛКМ — ввести тики"));
        setButton(11, Icons.of(Items.WATER_BUCKET, "&fПогода: &e" + weatherTitle(r.weather), "&8ЛКМ — следующая"));
        setButton(12, toggle(r.pvp, "PvP", "&7Игроки могут бить друг друга"));
        setButton(13, toggle(r.keepInventory, "Сохранение инвентаря", "&7Вещи не выпадают при смерти"));
        setButton(14, toggle(r.mobs, "Мобы появляются"));
        setButton(15, toggle(r.fireSpread, "Огонь распространяется"));
        setButton(16, toggle(r.mobGriefing, "Мобы ломают блоки", "&7Криперы, эндермены..."));
        setButton(19, Icons.of(Items.DIAMOND_SWORD, "&fРежим игры при входе: &e" + SubworldRules.gamemodeTitle(r.gamemode),
                "&7При выходе вернётся прежний режим", "&7(админам режим не меняется)", "&8ЛКМ — следующий"));
        setButton(20, Icons.of(Items.SIGN, "&fПриветствие на экране", line(r.welcome.isEmpty() ? "название подмира" : r.welcome),
                "&7Формат: заголовок|подзаголовок", "&8ЛКМ — изменить, Shift+ЛКМ — по умолчанию"));
        List<String> kit = new ArrayList<String>();
        kit.add("&7" + ItemCodec.describe(ItemCodec.decodeAll(r.starterKit), 6));
        kit.add("&7Выдаётся один раз при первом входе");
        kit.add("&8ЛКМ — взять предметы из своей панели быстрого доступа");
        kit.add("&8Shift+ЛКМ — очистить");
        setButton(21, Icons.of(Items.CHEST_MINECART, "&fСтартовый набор (" + r.starterKit.size() + ")", kit.toArray(new String[0])));
        setButton(22, Icons.of(Items.MILK_BUCKET, "&fВыдать набор ещё раз всем", "&7Сбросить отметку «уже получал»",
                "&8Shift+ЛКМ"));
    }

    private void buildLook(Subworld sw) {
        setButton(10, Icons.of(new ItemStack(Blocks.GLASS), "&fНебо: &e" + skyTitle(sw.sky),
                "&7по типу мира → обычное → ада → края → цвет", "&8ЛКМ — следующее",
                "&8Shift+ЛКМ — небо любого измерения (номер)"));
        setButton(11, Icons.of(Items.DYE, 4, "&fЦвет неба: &e" + hex(sw.skyColor), "&7Для неба «цвет»",
                "&8ЛКМ — ввести #RRGGBB"));
        setButton(12, Icons.of(Items.DYE, 7, "&fЦвет тумана: &e" + hex(sw.fogColor), "&7нет — как у неба",
                "&8ЛКМ — ввести #RRGGBB, Shift+ЛКМ — как у неба"));
        Subworld.Abyss a = sw.abyss;
        setButton(14, Icons.of(new ItemStack(Blocks.END_PORTAL_FRAME), "&5ГЛИТЧ-бездна: " + (a.enabled ? "&aвкл" : "&cвыкл"),
                "&7Падение в бездну → возврат на спавн,", "&7глитч-экран, фильтр эндермена и окно",
                "&7ENGINE ERROR", "&8ЛКМ — настроить"));
    }

    static String timeTitle(String t) {
        switch (t == null ? "cycle" : t) {
            case "day":
                return "утро";
            case "noon":
                return "полдень";
            case "sunset":
                return "закат";
            case "night":
                return "ночь";
            case "midnight":
                return "полночь";
            case "cycle":
                return "смена дня и ночи";
            default:
                return t + " тиков";
        }
    }

    static String weatherTitle(String w) {
        switch (w == null ? "cycle" : w) {
            case "clear":
                return "всегда ясно";
            case "rain":
                return "всегда дождь";
            case "thunder":
                return "всегда гроза";
            default:
                return "обычная";
        }
    }

    static String skyTitle(String sky) {
        if (sky == null) {
            return "по типу мира";
        }
        switch (sky) {
            case "overworld":
                return "обычное";
            case "nether":
                return "ада";
            case "end":
                return "края";
            case "color":
                return "сплошной цвет";
            case "default":
                return "по типу мира";
            default:
                return sky.startsWith("dim:") ? "измерения " + sky.substring(4) : sky;
        }
    }

    // --- клики ------------------------------------------------------------------------------------

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
        if (!Perms.isAdmin(player)) {
            player.closeScreen();
            return;
        }
        boolean shift = type == ClickType.QUICK_MOVE;
        Subworld sw = sw();
        switch (slot) {
            case 45:
                new WorldsMenu(player, WorldsMenu.TAB_ALL, 0).open();
                return;
            case 46:
                new SubworldEditMenu(player, id, Page.MAIN).open();
                return;
            case 47:
                new SubworldEditMenu(player, id, Page.RULES).open();
                return;
            case 48:
                new SubworldEditMenu(player, id, Page.LOOK).open();
                return;
            case 49:
                player.closeScreen();
                return;
            case 53:
                if (sw != null && shift) {
                    if (SubworldManager.isWorking(sw.id)) {
                        ChatUtil.send(player, "&eПодмир сейчас готовится (установка мира или публикация) — удалить можно после окончания.");
                        return;
                    }
                    if (confirmed(deleteArmed)) {
                        String note = SubworldManager.delete(sw);
                        ChatUtil.send(player, "&aПодмир &f" + sw.id + " &aудалён.");
                        if (!note.isEmpty()) {
                            ChatUtil.send(player, note);
                        }
                        new WorldsMenu(player, WorldsMenu.TAB_ALL, 0).open();
                    } else {
                        deleteArmed = arm();
                    }
                }
                return;
            default:
        }
        if (sw == null) {
            return;
        }
        switch (view) {
            case RULES:
                clickRules(sw, slot, shift);
                break;
            case LOOK:
                clickLook(sw, slot, shift);
                break;
            default:
                clickMain(sw, slot, shift);
        }
    }

    /** Сохранить подмир и разослать клиентам то, что им нужно. */
    private static void changed(Subworld sw) {
        sw.normalize();
        SubworldRegistry.save();
        SubworldDims.sync(sw);
    }

    private interface Edit {
        void apply(Subworld sw, EntityPlayerMP who, String text);
    }

    private void edit(String question, final Edit edit) {
        final String sid = id;
        prompt(question, new Input() {
            @Override
            public void accept(EntityPlayerMP who, String text) {
                Subworld s = SubworldRegistry.get(sid);
                if (s != null && Perms.isAdmin(who)) {
                    edit.apply(s, who, text);
                    changed(s);
                }
            }
        });
    }

    private void clickMain(Subworld sw, int slot, boolean shift) {
        switch (slot) {
            case 10:
                edit("&eНазвание (& — цвета):", (s, who, t) -> s.name = t.trim());
                return;
            case 11:
                if (shift) {
                    sw.description = "";
                    changed(sw);
                    return;
                }
                edit("&eОписание:", (s, who, t) -> s.description = t.trim());
                return;
            case 12: {
                ItemStack held = player.getHeldItemMainhand();
                if (!held.isEmpty()) {
                    sw.icon = ItemKey.of(held, false, true).serialize();
                    changed(sw);
                }
                return;
            }
            case 13:
                sw.enabled = !sw.enabled;
                changed(sw);
                return;
            case 14:
                sw.access = SubworldCreateMenu.nextAccess(sw.access);
                changed(sw);
                return;
            case 15:
                accessValue(sw, shift);
                return;
            case 16:
                if (shift) {
                    sw.age = "";
                    changed(sw);
                    return;
                }
                edit("&eФильтр возраста: 18, >=16, <=12, 14-17", (s, who, t) -> {
                    if (AgeFilter.parse(t) == null) {
                        ChatUtil.send(who, "&cНе понял фильтр.");
                    } else {
                        s.age = t.trim();
                    }
                });
                return;
            case 19:
                if (sw.perOwner()) {
                    edit("&eСохранений у команды (1–64):", (s, who, t) -> {
                        Long v = number(who, t, 1, 64);
                        if (v != null) {
                            s.maxSaves = v.intValue();
                        }
                    });
                }
                return;
            case 20:
                if (sw.perOwner()) {
                    edit("&eЦена сохранения после первого:", (s, who, t) -> {
                        Long v = number(who, t, 0, Long.MAX_VALUE / 4);
                        if (v != null) {
                            s.savePrice = v;
                        }
                    });
                }
                return;
            case 21:
                if (!sw.perOwner()) {
                    return;
                }
                if (shift) {
                    sw.borderStart = 0;
                    changed(sw);
                    applyBorders(sw);
                    return;
                }
                edit("&eБарьер: &fрадиус шаг предел &e(предел 0 — без предела)", (s, who, t) -> {
                    SubworldCreateMenu.border(who, s, t);
                    applyBorders(s);
                });
                return;
            case 22:
                sw.respawnInside = !sw.respawnInside;
                changed(sw);
                return;
            case 23:
                if (sw.perOwner()) {
                    sw.guests = !sw.guests;
                    changed(sw);
                }
                return;
            case 24:
                sw.ownQuests = !sw.ownQuests;
                changed(sw);
                SubworldShops.resync(sw);
                return;
            case 25:
                if (sw.ownQuests && SubworldQuests.available()) {
                    new ChapterMenu(player, sw.id, 0).open();
                }
                return;
            case 28:
                if (sw.ownQuests && shift) {
                    int n = SubworldShops.copyGlobal(sw);
                    ChatUtil.send(player, "&aВ прайс подмира скопировано позиций: &f" + n);
                }
                return;
            case 29:
                new ModsMenu(player, sw.id, 0).open();
                return;
            case 30: {
                boolean hasInstances = false;
                for (SubworldDim d : SubworldRegistry.dimsOf(sw.id)) {
                    hasInstances |= d.isInstance();
                }
                if (hasInstances) {
                    ChatUtil.send(player, "&cУ подмира уже есть локальные миры — пометку GLITCH не поменять.");
                    return;
                }
                if (!sw.perOwner() && !sw.glitch) {
                    ChatUtil.send(player, "&cGLITCH бывает только у подмира с локальными мирами (выбирается при создании).");
                    return;
                }
                sw.glitch = !sw.glitch;
                changed(sw);
                return;
            }
            case 31:
                if (sw.perOwner()) {
                    new InstancesMenu(player, sw.id, 0).open();
                }
                return;
            case 32:
                if (sw.perOwner()) {
                    ChatUtil.send(player, SubworldManager.publish(sw, player));
                }
                return;
            case 33: {
                SubworldDim d = SubworldRegistry.find(sw.id, sw.perOwner() ? SubworldDim.TEMPLATE : SubworldDim.SHARED);
                if (d != null) {
                    player.closeScreen();
                    SubworldTeleport.teleportTo(player, d, false, null);
                }
                return;
            }
            default:
        }
    }

    /** Новые радиусы барьера: поднять до начального, опустить до предела. */
    static void applyBorders(Subworld sw) {
        for (SubworldDim d : SubworldRegistry.dimsOf(sw.id)) {
            if (!d.isInstance()) {
                continue;
            }
            int before = d.border;
            if (sw.borderStart <= 0) {
                d.border = 0;
            } else {
                d.border = Math.max(d.border, sw.borderStart);
                if (sw.borderMax > 0) {
                    d.border = Math.min(d.border, sw.borderMax);
                }
            }
            if (before != d.border) {
                SubworldRegistry.markDirty();
                WorldServer w = DimensionManager.getWorld(d.dim);
                if (w != null) {
                    SubworldBorder.apply(w, d, true);
                }
            }
        }
        SubworldRegistry.saveIfDirty();
    }

    private void accessValue(Subworld sw, boolean shift) {
        switch (sw.access) {
            case Subworld.ACCESS_PRICE:
                edit("&eЦена доступа:", (s, who, t) -> {
                    Long v = number(who, t, 0, Long.MAX_VALUE / 4);
                    if (v != null) {
                        s.price = v;
                    }
                });
                return;
            case Subworld.ACCESS_ADVANCEMENT:
                edit("&eid достижения:", (s, who, t) -> s.advancement = t.trim());
                return;
            case Subworld.ACCESS_ROLE:
                edit("&eРоли через запятую:", (s, who, t) -> {
                    s.roles.clear();
                    for (String part : t.split(",")) {
                        if (!part.trim().isEmpty()) {
                            s.roles.add(part.trim().toLowerCase(Locale.ROOT));
                        }
                    }
                });
                return;
            default:
        }
    }

    private void clickRules(Subworld sw, int slot, boolean shift) {
        Subworld.Rules r = sw.rules;
        switch (slot) {
            case 10:
                if (shift) {
                    edit("&eВремя в тиках (0–23999), например 6000 — полдень:", (s, who, t) -> {
                        Long v = number(who, t, 0, 23999);
                        if (v != null) {
                            s.rules.time = Long.toString(v);
                            SubworldRules.applyLoaded(s);
                        }
                    });
                    return;
                }
                r.time = next(r.time, "cycle", "day", "noon", "sunset", "night", "midnight");
                break;
            case 11:
                r.weather = next(r.weather, "cycle", "clear", "rain", "thunder");
                break;
            case 12:
                r.pvp = !r.pvp;
                break;
            case 13:
                r.keepInventory = !r.keepInventory;
                break;
            case 14:
                r.mobs = !r.mobs;
                break;
            case 15:
                r.fireSpread = !r.fireSpread;
                break;
            case 16:
                r.mobGriefing = !r.mobGriefing;
                break;
            case 19:
                r.gamemode = next(r.gamemode, "", "survival", "adventure", "creative", "spectator");
                break;
            case 20:
                if (shift) {
                    r.welcome = "";
                    break;
                }
                edit("&eПриветствие: &fзаголовок|подзаголовок &e(& — цвета):", (s, who, t) -> s.rules.welcome = t.trim());
                return;
            case 21: {
                if (shift) {
                    r.starterKit.clear();
                    break;
                }
                List<ItemStack> hotbar = new ArrayList<ItemStack>();
                for (int i = 0; i < 9; i++) {
                    ItemStack st = player.inventory.getStackInSlot(i);
                    if (!st.isEmpty()) {
                        hotbar.add(st.copy());
                    }
                }
                if (hotbar.isEmpty()) {
                    ChatUtil.send(player, "&eПоложи предметы набора в панель быстрого доступа (1–9).");
                    return;
                }
                r.starterKit = ItemCodec.encodeAll(hotbar);
                ChatUtil.send(player, "&aСтартовый набор: &f" + ItemCodec.describe(hotbar, 9));
                break;
            }
            case 22:
                if (shift) {
                    int n = SubworldRegistry.resetKits(sw.id);
                    ChatUtil.send(player, "&aНабор снова получат игроков: &f" + n);
                }
                return;
            default:
                return;
        }
        changed(sw);
        SubworldRules.applyLoaded(sw);
    }

    private void clickLook(Subworld sw, int slot, boolean shift) {
        switch (slot) {
            case 10:
                if (shift) {
                    edit("&eНомер измерения, чьё небо взять (например 7 — Сумеречный лес):", (s, who, t) -> {
                        Long v = number(who, t, Integer.MIN_VALUE, Integer.MAX_VALUE);
                        if (v != null) {
                            s.sky = "dim:" + v;
                        }
                    });
                    return;
                }
                sw.sky = next(sw.sky.startsWith("dim:") ? "color" : sw.sky, "default", "overworld", "nether", "end", "color");
                changed(sw);
                return;
            case 11:
                edit("&eЦвет неба &f#RRGGBB&e:", (s, who, t) -> {
                    Integer c = color(t);
                    if (c == null || c < 0) {
                        ChatUtil.send(who, "&cНужен цвет вида #3A5FCD.");
                    } else {
                        s.skyColor = c;
                        s.sky = "color";
                    }
                });
                return;
            case 12:
                if (shift) {
                    sw.fogColor = -1;
                    changed(sw);
                    return;
                }
                edit("&eЦвет тумана &f#RRGGBB &e(нет — как у неба):", (s, who, t) -> {
                    Integer c = color(t);
                    if (c == null) {
                        ChatUtil.send(who, "&cНужен цвет вида #3A5FCD.");
                    } else {
                        s.fogColor = c;
                    }
                });
                return;
            case 14:
                new AbyssMenu(player, sw.id).open();
                return;
            default:
        }
    }

    /** Следующее значение по кругу. */
    static String next(String current, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }
}
