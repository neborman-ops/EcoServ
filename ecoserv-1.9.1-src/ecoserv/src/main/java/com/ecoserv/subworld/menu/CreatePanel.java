package com.ecoserv.subworld.menu;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.dates.AgeFilter;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketSubworldClient;
import com.ecoserv.role.Role;
import com.ecoserv.role.RoleManager;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldManager;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldUploads;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Вкладка «Создать подмир»: тип мира (обычный и все типы генерации, ад, край, плоский, пустота,
 * свой загруженный до 2 ГБ), доступ, локальные миры, сохранения, барьер, свои квесты, GLITCH.
 * Черновик у каждого админа свой и живёт до перезапуска сервера.
 */
final class CreatePanel {

    private static final Map<UUID, Subworld> DRAFTS = new HashMap<UUID, Subworld>();

    private CreatePanel() {
    }

    static Subworld draft(EntityPlayerMP player) {
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
            if (t == null || t == WorldType.FLAT || t == WorldType.DEBUG_ALL_BLOCK_STATES || t == WorldType.DEFAULT_1_1) {
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
        for (char c : UiUtil.plain(name).trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(c);
            } else if (c == ' ' || c == '-') {
                sb.append('_');
            }
        }
        String s = sb.toString();
        return s.length() > 32 ? s.substring(0, 32) : s;
    }

    /** Чего не хватает для создания (null — всё готово). */
    @Nullable
    static String problem(Subworld d) {
        if (d.name.trim().isEmpty()) {
            return "Введи название";
        }
        String id = d.id.isEmpty() ? idFrom(d.name) : d.id;
        if (!SubworldRegistry.idAvailable(id)) {
            return "Имя для команд «" + id + "» занято или некорректно";
        }
        if (Subworld.CUSTOM.equals(d.type) && d.template.isEmpty()) {
            return "Выбери загруженный мир";
        }
        if (Subworld.ACCESS_ADVANCEMENT.equals(d.access) && d.advancement.isEmpty()) {
            return "Укажи достижение";
        }
        if (Subworld.ACCESS_ROLE.equals(d.access) && d.roles.isEmpty()) {
            return "Выбери хотя бы одну роль";
        }
        return null;
    }

    static ItemStack typeIcon(String type) {
        switch (type) {
            case Subworld.NETHER:
                return new ItemStack(Blocks.NETHERRACK);
            case Subworld.END:
                return new ItemStack(Blocks.END_STONE);
            case Subworld.FLAT:
                return new ItemStack(Blocks.GRASS_PATH);
            case Subworld.VOID:
                return new ItemStack(Blocks.GLASS);
            case Subworld.CUSTOM:
                return new ItemStack(Items.FILLED_MAP);
            default:
                return new ItemStack(Blocks.GRASS);
        }
    }

    /** Выбор типа мира с описаниями. */
    static void typeSelect(Ui ui, String value, Ui.Text action) {
        ui.select("Тип мира", "Какой мир сгенерируется", value, action)
                .option(Subworld.NORMAL, "Обычный", "как основной мир, любые типы генерации", typeIcon(Subworld.NORMAL))
                .option(Subworld.NETHER, "Ад", "генерация ада", typeIcon(Subworld.NETHER))
                .option(Subworld.END, "Край", "острова края без дракона", typeIcon(Subworld.END))
                .option(Subworld.FLAT, "Плоский", "по строке пресета", typeIcon(Subworld.FLAT))
                .option(Subworld.VOID, "Пустота", "пустой мир, у входа появится площадка", typeIcon(Subworld.VOID))
                .option(Subworld.CUSTOM, "Свой загруженный", "мир из папки загрузок или из игры", typeIcon(Subworld.CUSTOM));
    }

    /** Выбор способа доступа с описаниями. */
    static void accessSelect(Ui ui, String value, Ui.Text action) {
        ui.select("Как попасть", "Кто может войти в подмир", value, action)
                .option(Subworld.ACCESS_ALWAYS, "Всем", "доступен сразу", new ItemStack(Items.EMERALD))
                .option(Subworld.ACCESS_PRICE, "За деньги", "разовая покупка у каждого игрока", new ItemStack(Items.GOLD_INGOT))
                .option(Subworld.ACCESS_ADVANCEMENT, "За достижение", "нужно достижение Minecraft", new ItemStack(Items.EXPERIENCE_BOTTLE))
                .option(Subworld.ACCESS_QUEST, "Наградой квеста", "награда FTB Quests «Доступ к подмиру»", new ItemStack(Items.WRITABLE_BOOK))
                .option(Subworld.ACCESS_ROLE, "По роли", "только игроки с выбранными ролями", new ItemStack(Items.NAME_TAG));
    }

    /** Поля, зависящие от способа доступа (общие для мастера и настроек подмира). */
    static void accessValue(Ui ui, final Subworld d, final Runnable changed) {
        switch (d.access) {
            case Subworld.ACCESS_PRICE:
                ui.money("Цена доступа", "Разово для каждого игрока; понимает 1.5k, 2m", d.price, v -> {
                    d.price = v;
                    changed.run();
                });
                break;
            case Subworld.ACCESS_ADVANCEMENT:
                ui.field("Достижение", "id, например minecraft:story/enter_the_nether", d.advancement, v -> {
                    d.advancement = v.trim();
                    changed.run();
                }).placeholder("minecraft:story/enter_the_nether");
                break;
            case Subworld.ACCESS_QUEST:
                ui.note(Ui.BLUE, "В FTB Quests добавь квесту награду «Доступ к подмиру» и выбери этот подмир. "
                        + "Выдать вручную: &f/subworld unlock <подмир> <ник>");
                break;
            case Subworld.ACCESS_ROLE: {
                List<Role> roles = RoleManager.all();
                if (roles.isEmpty()) {
                    ui.note(Ui.YELLOW, "Ролей пока нет — создай их в окне «Роли и теги».");
                }
                for (final Role role : roles) {
                    ui.toggle("Роль " + role.title(), role.description.isEmpty() ? "&8id: " + role.id : role.description,
                            d.roles.contains(role.id), on -> {
                                d.roles.remove(role.id);
                                if (on) {
                                    d.roles.add(role.id);
                                }
                                changed.run();
                            });
                }
                break;
            }
            default:
        }
    }

    /** Поле фильтра возраста (общее). */
    static void ageField(Ui ui, final Subworld d, final Runnable changed) {
        AgeFilter age = AgeFilter.parse(d.age);
        ui.field("Проверка возраста", "Пусто — без проверки. 18 — ровно, >=16, <=12, 14-17 (по /birthday)"
                + (age != null ? ". Сейчас: &f" + age.describe() : ""), d.age, v -> {
            String t = v.trim();
            if (!t.isEmpty() && AgeFilter.parse(t) == null) {
                throw new UiError("Не понял фильтр возраста: " + t + ". Примеры: 18, >=16, <=12, 14-17");
            }
            d.age = t;
            changed.run();
        }).placeholder("без проверки").clearable();
    }

    static void build(Ui ui, final WorldsScreen s) {
        final EntityPlayerMP player = s.player();
        final Subworld d = draft(player);
        final Runnable none = () -> {
        };
        String problem = problem(d);
        String autoId = d.id.isEmpty() ? idFrom(d.name) : d.id;
        ui.row(UiUtil.icon(d.icon), "&6Новый подмир: &f" + (d.name.isEmpty() ? "без названия" : d.name))
                .line("&7" + d.typeTitle() + " · доступ: " + UiUtil.plain(d.accessTitle()))
                .line("&8вход: /world " + (autoId.isEmpty() ? "<id>" : autoId))
                .chip(problem == null ? "готов" : "не готов", problem == null ? Ui.GREEN : Ui.YELLOW)
                .highlight(true);
        if (problem != null) {
            ui.note(Ui.YELLOW, problem + ".");
        }

        ui.header("Название");
        ui.field("Название", "Как подмир видят игроки; цвета — коды через & (как в чате)", d.name, v -> {
            d.name = v.trim();
        }).placeholder("например &6Острова").max(48);
        ui.field("Имя для команд (id)", "Латиница/кириллица, цифры, _ — для /world, запретов и инвентарей. "
                + "Пусто — из названия.", d.id, v -> d.id = idFrom(v)).placeholder(autoId.isEmpty() ? "из названия" : autoId)
                .clearable().max(32);
        ui.field("Иконка", "Предмет в меню: кнопка … — выбрать из всех предметов", d.icon, v -> {
            String id = UiUtil.parseItemId(v);
            if (id == null) {
                throw new UiError("Такого предмета нет: " + v);
            }
            d.icon = id;
        }).picker(Ui.PICK_ITEM);

        ui.header("Мир");
        typeSelect(ui, d.type, v -> d.type = v);
        if (Subworld.NORMAL.equals(d.type)) {
            Ui.Select gen = ui.select("Генерация", "Типы миров ванили и модов", d.worldType, v -> d.worldType = v);
            for (String t : worldTypes()) {
                gen.option(t, t);
            }
        } else if (Subworld.FLAT.equals(d.type)) {
            ui.field("Пресет плоского мира", "Строка как в настройках плоского мира. Пусто — трава.",
                    d.generatorOptions, v -> d.generatorOptions = v.trim())
                    .placeholder("3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;village").clearable().max(512);
        } else if (Subworld.VOID.equals(d.type)) {
            ui.note(Ui.BLUE, "Пустой мир: всё строят админы в шаблоне. У точки входа появится площадка.");
        } else if (Subworld.CUSTOM.equals(d.type)) {
            uploads(ui, s, d);
        }
        if (!Subworld.CUSTOM.equals(d.type)) {
            ui.field("Зерно мира", "Число или любой текст. Пусто — случайное.", d.seed == 0 ? "" : Long.toString(d.seed), v -> {
                String t = v.trim();
                if (t.isEmpty()) {
                    d.seed = 0L;
                    return;
                }
                try {
                    d.seed = Long.parseLong(t);
                } catch (NumberFormatException e) {
                    d.seed = t.hashCode();
                }
            }).placeholder("случайное").clearable();
            ui.toggle("Постройки", "Деревни, крепости, храмы", d.structures, on -> d.structures = on);
        }

        ui.header("Доступ");
        accessSelect(ui, d.access, v -> d.access = v);
        accessValue(ui, d, none);
        ageField(ui, d, none);

        ui.header("Локальные миры");
        ui.toggle("Свой мир у каждой команды", "Как skyblock: у каждой команды своя копия мира. Админы правят шаблон "
                + "и публикуют изменения во все копии.", d.localWorlds || d.glitch, d.glitch, on -> {
            d.localWorlds = on;
            if (!on) {
                d.glitch = false;
            }
        });
        if (d.localWorlds || d.glitch) {
            ui.number("Сохранений у команды", "Сколько своих миров может завести одна команда (1–64)", d.maxSaves, 1, 64,
                    v -> d.maxSaves = (int) v).disabled(d.glitch);
            ui.money("Цена сохранения", "Первый мир бесплатный, каждый следующий — по этой цене", d.savePrice, v -> d.savePrice = v);
            borderFields(ui, d, none);
        }
        ui.toggle("Пометка GLITCH", "Мир у каждого ИГРОКА свой, одно сохранение; в нём ставят ГЛИТЧ-сундуки",
                d.glitch, on -> {
                    d.glitch = on;
                    if (on) {
                        d.localWorlds = true;
                        d.maxSaves = 1;
                    }
                });
        ui.toggle("Пометка МИНИ-ИГРА", "Арена «Квестовой гонки» (/race): на каждую игру — своя копия шаблона, у игроков "
                        + "там всегда свой пустой инвентарь. Обычным входом туда не попасть",
                d.minigame, on -> {
                    d.minigame = on;
                    if (on) {
                        d.localWorlds = true;
                        d.glitch = false;
                        d.maxSaves = 1;
                    }
                });
        ui.toggle("Свои квесты и магазин", "В подмире видны только его главы FTB Quests, свой прайс магазина и продажи",
                d.ownQuests, on -> d.ownQuests = on);
        ui.toggle("Запретить вход через меню", "Кнопки «Войти» в меню миров не будет (порталы и команды админов работают)",
                d.noMenuTeleport, on -> d.noMenuTeleport = on);

        ui.footer("Сбросить", Ui.GREY, () -> {
            DRAFTS.remove(player.getUniqueID());
            s.setToast("Черновик очищен.");
        }).confirm("Очистить все поля нового подмира?");
        Ui.Button create = ui.footer("Создать подмир", Ui.GREEN, () -> create(s, player, d)).disabled(problem != null);
        if (problem != null) {
            create.tip("&e" + problem);
        } else if (d.perOwner()) {
            create.tip("&7Подмир с локальными мирами создаётся выключенным:", "&7зайди в шаблон, построй, опубликуй и включи.");
        }
    }

    /** Барьер локальных миров: радиус, шаг, предел (общее для мастера и настроек). */
    static void borderFields(Ui ui, final Subworld d, final Runnable changed) {
        ui.number("Барьер: начальный радиус", "0 — без барьера. Радиус в блоках от точки появления", d.borderStart, 0, 1000000,
                v -> {
                    d.borderStart = (int) v;
                    changed.run();
                });
        if (d.borderStart > 0) {
            ui.number("Барьер: шаг расширения", "На сколько растёт за награду квеста «Расширение мира»", d.borderStep, 1, 100000,
                    v -> {
                        d.borderStep = (int) v;
                        changed.run();
                    });
            ui.number("Барьер: предел", "Больше не растёт; 0 — без предела", d.borderMax, 0, 1000000, v -> {
                d.borderMax = (int) v;
                changed.run();
            });
        }
    }

    private static void uploads(Ui ui, final WorldsScreen s, final Subworld d) {
        List<SubworldUploads.Upload> list = SubworldUploads.list();
        ui.text("&7Положи папку мира (или .zip) в &f" + EcoConfig.subworldsUploadsFolder + "&7 рядом с миром сервера "
                + "или загрузи прямо из игры. Мир 1.12.2 или старше, до " + EcoConfig.subworldsMaxUploadMb + " МБ.");
        if (list.isEmpty()) {
            ui.note(Ui.YELLOW, "Загруженных миров пока нет.");
        }
        for (final SubworldUploads.Upload u : list) {
            boolean sel = u.name.equals(d.template);
            Ui.Row row = ui.row(new ItemStack(u.error == null ? Items.FILLED_MAP : Items.MAP), (sel ? "&a" : u.error == null ? "&e" : "&7") + u.name)
                    .line("&7" + (u.zip ? "архив .zip" : "папка") + ", " + Fmt.bytes(u.size) + ", регионов: " + u.regions
                            + (u.levelName.isEmpty() ? "" : " · «" + u.levelName + "»"))
                    .line(u.error == null ? (u.modded ? "&8мир с модами — блоки пересчитаются" : "&8ванильный мир") : "&cне подходит: " + u.error)
                    .highlight(sel);
            if (sel) {
                row.chip("выбран", Ui.GREEN);
            }
            if (u.error == null) {
                row.button(sel ? "Выбран" : "Выбрать", sel ? Ui.GREY : Ui.BLUE, () -> d.template = u.name).disabled(sel);
            }
        }
        Ui.Buttons b = ui.buttons();
        b.add("Обновить список", Ui.GREY, () -> s.setToast("Список обновлён."));
        if (EcoConfig.subworldsClientUpload) {
            b.add("Загрузить из игры", Ui.BLUE, () -> {
                s.close();
                NBTTagCompound tag = new NBTTagCompound();
                tag.setLong("max", SubworldUploads.maxBytes());
                PacketHandler.sendTo(new PacketSubworldClient(PacketSubworldClient.OPEN_UPLOAD, tag), s.player());
            });
        }
    }

    private static void create(WorldsScreen s, EntityPlayerMP player, Subworld d) {
        if (!Perms.isAdmin(player)) {
            throw new UiError("Только для админов.");
        }
        String problem = problem(d);
        if (problem != null) {
            throw new UiError(problem + ".");
        }
        if (d.id.isEmpty()) {
            d.id = idFrom(d.name);
        }
        String error = SubworldManager.create(d, player);
        if (error != null) {
            throw new UiError(UiUtil.plain(error));
        }
        DRAFTS.remove(player.getUniqueID());
        Subworld created = SubworldRegistry.get(d.id);
        ChatUtil.send(player, "&aПодмир &f" + d.name + " &aсоздан (&f/world " + d.id + "&a).");
        if (created != null) {
            new SubworldScreen(player, created.id).withToast(created.perOwner()
                    ? "Подмир создан выключенным: зайди в шаблон, построй, нажми «Опубликовать» и включи."
                    : "Подмир создан! Вход: /world " + created.id).open();
        }
    }
}
