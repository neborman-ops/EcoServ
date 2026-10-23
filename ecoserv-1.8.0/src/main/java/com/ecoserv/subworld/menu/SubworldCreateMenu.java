package com.ecoserv.subworld.menu;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.dates.AgeFilter;
import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketSubworldClient;
import com.ecoserv.role.RoleManager;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldManager;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldUploads;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Мастер создания подмира (админ): тип мира (обычный и все типы генерации, ад, край, плоский, пустота,
 * свой загруженный до 2 ГБ), способ доступа, локальные миры, сохранения, название, свои квесты.
 */
public class SubworldCreateMenu extends SubMenu {

    /** Черновики админов (живут до перезапуска сервера). */
    private static final Map<UUID, Subworld> DRAFTS = new HashMap<UUID, Subworld>();

    private final boolean uploads;
    private int page;

    public SubworldCreateMenu(EntityPlayerMP player, boolean uploads, int page) {
        super(player, uploads ? "Свой мир: загрузки" : "Создать подмир", 6);
        this.uploads = uploads;
        this.page = page;
    }

    public static void open(EntityPlayerMP player) {
        if (Perms.isAdmin(player)) {
            new SubworldCreateMenu(player, false, 0).open();
        }
    }

    @Override
    protected ChestMenu reopen(EntityPlayerMP who) {
        return new SubworldCreateMenu(who, uploads, page);
    }

    private Subworld draft() {
        Subworld d = DRAFTS.get(player.getUniqueID());
        if (d == null) {
            d = new Subworld();
            d.name = "";
            DRAFTS.put(player.getUniqueID(), d);
        }
        return d;
    }

    /** Типы генерации обычного мира (ванильные и из модов). */
    static List<String> worldTypes() {
        List<String> out = new ArrayList<String>();
        // canBeCreated() есть только в клиенте игры — на выделенном сервере отбираем по списку
        for (WorldType t : WorldType.WORLD_TYPES) {
            if (t == null || t == WorldType.FLAT || t == WorldType.DEBUG_ALL_BLOCK_STATES
                    || t == WorldType.DEFAULT_1_1) {
                continue;
            }
            out.add(t.getName());
        }
        if (out.isEmpty()) {
            out.add("default");
        }
        return out;
    }

    /** id из названия: строчные буквы, цифры, _. */
    static String idFrom(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : ChatUtil.strip(ChatUtil.color(name)).trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(c);
            } else if (c == ' ' || c == '-') {
                sb.append('_');
            }
        }
        String s = sb.toString();
        return s.length() > 32 ? s.substring(0, 32) : s;
    }

    // --- отрисовка ---------------------------------------------------------------------------

    @Override
    protected void buildButtons() {
        for (int i = 0; i < topSize; i++) {
            setButton(i, ItemStack.EMPTY);
        }
        fillRow(0, filler());
        fillRow(5, filler());
        if (uploads) {
            buildUploads();
        } else {
            buildDraft();
        }
        setButton(53, Icons.close());
    }

    private void buildDraft() {
        Subworld d = draft();
        setButton(4, Icons.of(icon(d.icon), "&6Новый подмир: &f" + (d.name.isEmpty() ? "без названия" : d.name),
                "&7id: &f" + (d.id.isEmpty() ? "—" : d.id),
                "&7Тип: &f" + d.typeTitle(),
                "&7Доступ: &f" + ChatUtil.strip(d.accessTitle()),
                "&7Локальные миры: &f" + (d.perOwner() ? "да" : "нет")));

        setButton(10, Icons.of(Items.NAME_TAG, "&fНазвание", line(d.name), "&7& — цвета", "&8ЛКМ — ввести"));
        setButton(11, Icons.of(Items.PAPER, "&fИмя для команд (id)", line(d.id),
                "&7Для /world, запретов и групп инвентаря", "&7буквы, цифры, _", "&8ЛКМ — ввести"));
        setButton(12, Icons.of(typeIcon(d.type), "&fТип мира: &e" + d.typeTitle(),
                "&7обычный → ад → край → плоский →", "&7пустота → свой загруженный",
                "&8ЛКМ — сменить"));
        if (Subworld.NORMAL.equals(d.type)) {
            setButton(13, Icons.of(new ItemStack(Blocks.SAPLING), "&fГенерация: &e" + d.worldType,
                    "&7Все типы: &f" + String.join(", ", worldTypes()), "&8ЛКМ — следующий"));
        } else if (Subworld.FLAT.equals(d.type)) {
            setButton(13, Icons.of(new ItemStack(Blocks.GRASS), "&fПресет плоского мира",
                    line(d.generatorOptions.isEmpty() ? "по умолчанию (трава)" : d.generatorOptions),
                    "&7Строка как в настройках плоского мира", "&8ЛКМ — ввести, Shift+ЛКМ — по умолчанию"));
        } else if (Subworld.CUSTOM.equals(d.type)) {
            setButton(13, Icons.of(Items.FILLED_MAP, "&fЗагруженный мир: &e" + (d.template.isEmpty() ? "не выбран" : d.template),
                    "&7Папка " + EcoConfig.subworldsUploadsFolder + " на сервере", "&7или загрузка из игры (до "
                            + EcoConfig.subworldsMaxUploadMb + " МБ)", "&8ЛКМ — выбрать"));
        } else if (Subworld.VOID.equals(d.type)) {
            setButton(13, Icons.of(new ItemStack(Blocks.GLASS), "&fПустота",
                    "&7Пустой мир: всё строят админы в шаблоне", "&7(у точки входа появится площадка)"));
        }
        setButton(14, Icons.of(Items.WHEAT_SEEDS, "&fЗерно мира", line(d.seed == 0 ? "случайное" : Long.toString(d.seed)),
                "&8ЛКМ — ввести (число или текст), Shift+ЛКМ — случайное"));
        setButton(15, toggle(d.structures, "Постройки (деревни, крепости)"));

        setButton(19, Icons.of(Items.GOLD_INGOT, "&fДоступ: &e" + ChatUtil.strip(d.accessTitle()),
                "&7всегда → за оплату → за достижение →", "&7наградой в квестах → по роли", "&8ЛКМ — сменить"));
        setButton(20, accessValueIcon(d));
        AgeFilter age = AgeFilter.parse(d.age);
        setButton(21, Icons.of(new ItemStack(Blocks.CAKE), "&fПроверка возраста", age == null ? "&8нет" : "&f" + age.describe(),
                "&7Фильтр: 18, >=16, <=12, 14-17", "&8ЛКМ — ввести, Shift+ЛКМ — убрать"));

        setButton(28, toggle(d.localWorlds || d.glitch, "Локальные миры",
                "&7У каждой команды своя копия мира", "&7(как skyblock), админы правят шаблон"));
        setButton(29, Icons.of(Items.BOOK, "&fСохранений у команды: &e" + d.maxSaves,
                "&7Сколько локальных миров может", "&7завести одна команда", "&8ЛКМ — ввести"));
        setButton(30, Icons.of(Items.GOLD_NUGGET, "&fЦена сохранения: &e" + (d.savePrice > 0 ? Fmt.money(d.savePrice) : "бесплатно"),
                "&7Первый мир бесплатный, дальше — по цене", "&8ЛКМ — ввести"));
        setButton(31, Icons.of(new ItemStack(Blocks.IRON_BARS), "&fБарьер: &e" + (d.borderStart > 0
                        ? d.borderStart + " / +" + d.borderStep + (d.borderMax > 0 ? " / до " + d.borderMax : "") : "нет"),
                "&7Начальный радиус, шаг расширения, предел", "&7(расширяет награда квеста «Расширение мира»)",
                "&8ЛКМ — ввести «радиус шаг предел», Shift+ЛКМ — без барьера"));
        setButton(32, toggle(d.ownQuests, "Свои квесты и магазин",
                "&7В подмире видны только его главы квестов,", "&7свой прайс магазина и продажи"));
        setButton(33, toggle(d.glitch, "Пометка GLITCH",
                "&7Мир у каждого ИГРОКА свой, одно сохранение;", "&7в нём ставят ГЛИТЧ-сундуки"));
        setButton(34, Icons.of(icon(d.icon), "&fИконка", "&7Предмет в руке станет иконкой", "&8ЛКМ — взять из руки"));

        setButton(45, Icons.back());
        setButton(47, Icons.of(Items.MILK_BUCKET, "&fСбросить черновик", "&8Shift+ЛКМ"));
        String problem = problem(d);
        setButton(49, problem == null
                ? Icons.green("&a&lСоздать подмир", "&7Подмир с локальными мирами создаётся", "&7выключенным: подготовь шаблон,",
                "&7опубликуй и включи в настройках")
                : Icons.red("&cЕщё не готово", "&7" + problem));
    }

    private static net.minecraft.item.Item typeIcon(String type) {
        switch (type) {
            case Subworld.NETHER:
                return block(Blocks.NETHERRACK);
            case Subworld.END:
                return block(Blocks.END_STONE);
            case Subworld.FLAT:
                return block(Blocks.GRASS);
            case Subworld.VOID:
                return block(Blocks.GLASS);
            case Subworld.CUSTOM:
                return Items.FILLED_MAP;
            default:
                return block(Blocks.GRASS);
        }
    }

    private ItemStack accessValueIcon(Subworld d) {
        switch (d.access) {
            case Subworld.ACCESS_PRICE:
                return Icons.of(Items.GOLD_NUGGET, "&fЦена доступа: &e" + Fmt.money(d.price), "&8ЛКМ — ввести");
            case Subworld.ACCESS_ADVANCEMENT:
                return Icons.of(Items.EXPERIENCE_BOTTLE, "&fДостижение: " + line(d.advancement),
                        "&7id, например minecraft:story/enter_the_nether", "&8ЛКМ — ввести");
            case Subworld.ACCESS_ROLE:
                return Icons.of(Items.NAME_TAG, "&fРоли: " + line(String.join(", ", d.roles)),
                        "&7Есть: &f" + String.join(", ", RoleManager.ids()), "&8ЛКМ — ввести через запятую");
            case Subworld.ACCESS_QUEST:
                return Icons.of(Items.WRITABLE_BOOK, "&fНаградой в квестах",
                        "&7В FTB Quests добавь награду «Доступ к подмиру»", "&7и выбери этот подмир");
            default:
                return Icons.of(Items.EMERALD, "&fДоступен всем");
        }
    }

    /** Чего не хватает для создания (null — всё готово). */
    private static String problem(Subworld d) {
        if (d.name.trim().isEmpty()) {
            return "Введи название";
        }
        String id = d.id.isEmpty() ? idFrom(d.name) : d.id;
        if (!SubworldRegistry.idAvailable(id)) {
            return "id «" + id + "» занят или некорректен";
        }
        if (Subworld.CUSTOM.equals(d.type) && d.template.isEmpty()) {
            return "Выбери загруженный мир";
        }
        if (Subworld.ACCESS_ADVANCEMENT.equals(d.access) && d.advancement.isEmpty()) {
            return "Укажи достижение";
        }
        if (Subworld.ACCESS_ROLE.equals(d.access) && d.roles.isEmpty()) {
            return "Укажи роли";
        }
        return null;
    }

    private void buildUploads() {
        List<SubworldUploads.Upload> list = SubworldUploads.list();
        page = Math.min(page, pages(list.size(), PER_PAGE) - 1);
        Subworld d = draft();
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < list.size(); i++) {
            SubworldUploads.Upload u = list.get(page * PER_PAGE + i);
            boolean sel = u.name.equals(d.template);
            ItemStack icon = Icons.of(u.error == null ? Items.FILLED_MAP : Items.MAP, (sel ? "&a✔ " : u.error == null ? "&e" : "&7") + u.name,
                    "&7" + (u.zip ? "архив .zip" : "папка") + ", " + Fmt.bytes(u.size) + ", регионов: " + u.regions,
                    u.levelName.isEmpty() ? "" : "&7Название в игре: &f" + u.levelName,
                    u.modded ? "&7Мир с модами — блоки пересчитаются" : "&7Ванильный мир",
                    u.error == null ? "&8ЛКМ — выбрать" : "&cНе подходит: " + u.error);
            setButton(9 + i, sel ? Icons.glint(icon) : icon);
        }
        setButton(4, Icons.of(Items.BOOK, "&6Свои миры", "&7Положи папку мира (или .zip) в папку",
                "&f" + EcoConfig.subworldsUploadsFolder + " &7рядом с миром сервера,", "&7или загрузи прямо из игры.",
                "&7Мир должен быть из 1.12.2 или старше,", "&7до " + EcoConfig.subworldsMaxUploadMb + " МБ."));
        if (EcoConfig.subworldsClientUpload) {
            setButton(47, Icons.of(Items.ENDER_EYE, "&bЗагрузить из игры", "&7Выбрать мир из своих одиночных",
                    "&7сохранений и отправить на сервер", "&8ЛКМ — открыть"));
        }
        setButton(51, Icons.of(Items.COMPASS, "&fОбновить список", "&8ЛКМ"));
        if (page > 0) {
            setButton(48, Icons.arrowLeft("&eНазад"));
        }
        if (page < pages(list.size(), PER_PAGE) - 1) {
            setButton(50, Icons.arrowRight("&eДальше"));
        }
        setButton(45, Icons.back());
    }

    // --- клики ------------------------------------------------------------------------------------

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
        if (uploads) {
            clickUploads(slot);
        } else {
            clickDraft(slot, shift);
        }
    }

    private void clickUploads(int slot) {
        if (slot == 45) {
            new SubworldCreateMenu(player, false, 0).open();
            return;
        }
        if (slot == 47 && EcoConfig.subworldsClientUpload) {
            player.closeScreen();
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("max", SubworldUploads.maxBytes());
            PacketHandler.sendTo(new PacketSubworldClient(PacketSubworldClient.OPEN_UPLOAD, tag), player);
            return;
        }
        if (slot == 48 && page > 0) {
            page--;
            return;
        }
        if (slot == 50) {
            page++;
            return;
        }
        if (slot == 51) {
            return;
        }
        List<SubworldUploads.Upload> list = SubworldUploads.list();
        int index = page * PER_PAGE + slot - 9;
        if (slot >= 9 && slot < 45 && index >= 0 && index < list.size()) {
            SubworldUploads.Upload u = list.get(index);
            if (u.error != null) {
                ChatUtil.send(player, "&cМир «" + u.name + "» не подходит: " + u.error);
                return;
            }
            draft().template = u.name;
            new SubworldCreateMenu(player, false, 0).open();
        }
    }

    /**
     * Ввод в чат для черновика: применяется, только если черновик ещё не стал подмиром
     * (иначе запоздалый ввод поменял бы настройки уже созданного подмира).
     */
    private void ask(String question, final Subworld d, final Input input) {
        prompt(question, new Input() {
            @Override
            public void accept(EntityPlayerMP who, String text) {
                if (DRAFTS.get(who.getUniqueID()) == d && Perms.isAdmin(who)) {
                    input.accept(who, text);
                }
            }
        });
    }

    private void clickDraft(int slot, boolean shift) {
        final Subworld d = draft();
        switch (slot) {
            case 45:
                new WorldsMenu(player, WorldsMenu.TAB_ALL, 0).open();
                return;
            case 47:
                if (shift) {
                    DRAFTS.remove(player.getUniqueID());
                }
                return;
            case 10:
                ask("&eНазвание подмира (& — цвета):", d, new Input() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        d.name = text.trim();
                        if (d.id.isEmpty()) {
                            d.id = idFrom(d.name);
                        }
                    }
                });
                return;
            case 11:
                ask("&eid подмира (буквы, цифры, _):", d, new Input() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        d.id = idFrom(text);
                    }
                });
                return;
            case 12:
                d.type = nextType(d.type);
                return;
            case 13:
                if (Subworld.NORMAL.equals(d.type)) {
                    List<String> types = worldTypes();
                    int i = types.indexOf(d.worldType);
                    d.worldType = types.get((i + 1) % types.size());
                } else if (Subworld.FLAT.equals(d.type)) {
                    if (shift) {
                        d.generatorOptions = "";
                        return;
                    }
                    ask("&eПресет плоского мира, например &f3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;village", d, new Input() {
                                @Override
                                public void accept(EntityPlayerMP who, String text) {
                                    d.generatorOptions = text.trim();
                                }
                            });
                } else if (Subworld.CUSTOM.equals(d.type)) {
                    new SubworldCreateMenu(player, true, 0).open();
                }
                return;
            case 14:
                if (shift) {
                    d.seed = 0L;
                    return;
                }
                ask("&eЗерно мира (число или любой текст):", d, new Input() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        String t = text.trim();
                        try {
                            d.seed = Long.parseLong(t);
                        } catch (NumberFormatException e) {
                            d.seed = t.hashCode();
                        }
                    }
                });
                return;
            case 15:
                d.structures = !d.structures;
                return;
            case 19:
                d.access = nextAccess(d.access);
                return;
            case 20:
                accessValue(d);
                return;
            case 21:
                if (shift) {
                    d.age = "";
                    return;
                }
                ask("&eФильтр возраста: 18 (ровно), >=16, <=12, 14-17", d, new Input() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        if (AgeFilter.parse(text) == null) {
                            ChatUtil.send(who, "&cНе понял фильтр.");
                            return;
                        }
                        d.age = text.trim();
                    }
                });
                return;
            case 28:
                d.localWorlds = !d.localWorlds;
                if (!d.localWorlds) {
                    d.glitch = false;
                }
                return;
            case 29:
                ask("&eСколько сохранений (локальных миров) у одной команды, 1–64:", d, new Input() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        Long v = number(who, text, 1, 64);
                        if (v != null) {
                            d.maxSaves = v.intValue();
                        }
                    }
                });
                return;
            case 30:
                ask("&eЦена каждого сохранения после первого (0 — бесплатно):", d, new Input() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        Long v = number(who, text, 0, Long.MAX_VALUE / 4);
                        if (v != null) {
                            d.savePrice = v;
                        }
                    }
                });
                return;
            case 31:
                if (shift) {
                    d.borderStart = 0;
                    return;
                }
                ask("&eБарьер: &fрадиус шаг предел &e(например &f32 16 256&e; предел 0 — без предела)", d, new Input() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        border(who, d, text);
                    }
                });
                return;
            case 32:
                d.ownQuests = !d.ownQuests;
                return;
            case 33:
                d.glitch = !d.glitch;
                if (d.glitch) {
                    d.localWorlds = true;
                    d.maxSaves = 1;
                }
                return;
            case 34: {
                ItemStack held = player.getHeldItemMainhand();
                if (held.isEmpty()) {
                    ChatUtil.send(player, "&eВозьми предмет в руку.");
                    return;
                }
                d.icon = ItemKey.of(held, false, true).serialize();
                return;
            }
            case 49:
                create(d);
                return;
            default:
        }
    }

    static void border(EntityPlayerMP who, Subworld d, String text) {
        String[] parts = text.trim().split("[\\s,;/]+");
        try {
            int start = Integer.parseInt(parts[0]);
            int step = parts.length > 1 ? Integer.parseInt(parts[1]) : d.borderStep;
            int max = parts.length > 2 ? Integer.parseInt(parts[2]) : d.borderMax;
            if (start < 0 || start > 1000000 || step < 1 || step > 100000 || max < 0 || max > 1000000) {
                ChatUtil.send(who, "&cЧисла вне допустимых пределов.");
                return;
            }
            d.borderStart = start;
            d.borderStep = step;
            d.borderMax = max;
        } catch (NumberFormatException e) {
            ChatUtil.send(who, "&cНужно три числа: радиус шаг предел.");
        }
    }

    private void accessValue(final Subworld d) {
        switch (d.access) {
            case Subworld.ACCESS_PRICE:
                ask("&eЦена доступа (разово, для каждого игрока):", d, new Input() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        Long v = number(who, text, 0, Long.MAX_VALUE / 4);
                        if (v != null) {
                            d.price = v;
                        }
                    }
                });
                return;
            case Subworld.ACCESS_ADVANCEMENT:
                ask("&eid достижения (например &fminecraft:story/enter_the_nether&e):", d, new Input() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        d.advancement = text.trim();
                    }
                });
                return;
            case Subworld.ACCESS_ROLE:
                ask("&eРоли через запятую (&f" + String.join(", ", RoleManager.ids()) + "&e):", d, new Input() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        d.roles.clear();
                        for (String part : text.split(",")) {
                            if (!part.trim().isEmpty()) {
                                d.roles.add(part.trim().toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                });
                return;
            default:
        }
    }

    static String nextType(String type) {
        switch (type) {
            case Subworld.NORMAL:
                return Subworld.NETHER;
            case Subworld.NETHER:
                return Subworld.END;
            case Subworld.END:
                return Subworld.FLAT;
            case Subworld.FLAT:
                return Subworld.VOID;
            case Subworld.VOID:
                return Subworld.CUSTOM;
            default:
                return Subworld.NORMAL;
        }
    }

    static String nextAccess(String access) {
        switch (access) {
            case Subworld.ACCESS_ALWAYS:
                return Subworld.ACCESS_PRICE;
            case Subworld.ACCESS_PRICE:
                return Subworld.ACCESS_ADVANCEMENT;
            case Subworld.ACCESS_ADVANCEMENT:
                return Subworld.ACCESS_QUEST;
            case Subworld.ACCESS_QUEST:
                return Subworld.ACCESS_ROLE;
            default:
                return Subworld.ACCESS_ALWAYS;
        }
    }

    private void create(Subworld d) {
        com.ecoserv.menu.ChatPrompt.cancel(player);
        String problem = problem(d);
        if (problem != null) {
            ChatUtil.send(player, "&c" + problem + ".");
            return;
        }
        if (d.id.isEmpty()) {
            d.id = idFrom(d.name);
        }
        String error = SubworldManager.create(d, player);
        if (error != null) {
            ChatUtil.send(player, error);
            return;
        }
        DRAFTS.remove(player.getUniqueID());
        Subworld created = SubworldRegistry.get(d.id);
        ChatUtil.send(player, "&aПодмир &f" + d.name + " &aсоздан (&f/world " + d.id + "&a).");
        if (created != null && created.perOwner()) {
            ChatUtil.send(player, "&7Он пока выключен: зайди в шаблон (ПКМ по подмиру в меню), построй, "
                    + "нажми «Опубликовать» в настройках и включи подмир.");
        }
        if (created != null) {
            new SubworldEditMenu(player, created.id, SubworldEditMenu.Page.MAIN).open();
        }
    }
}
