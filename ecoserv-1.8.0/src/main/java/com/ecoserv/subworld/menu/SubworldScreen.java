package com.ecoserv.subworld.menu;

import com.ecoserv.restrict.ItemRule;
import com.ecoserv.restrict.ItemRules;
import com.ecoserv.restrict.RestrictAction;
import com.ecoserv.shop.ShopList;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldAbyss;
import com.ecoserv.subworld.SubworldBorder;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldDims;
import com.ecoserv.subworld.SubworldInfo;
import com.ecoserv.subworld.SubworldManager;
import com.ecoserv.subworld.SubworldQuests;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldRules;
import com.ecoserv.subworld.SubworldShops;
import com.ecoserv.subworld.SubworldTeams;
import com.ecoserv.subworld.SubworldTeleport;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.ItemCodec;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldNames;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Настройки подмира (админ): основное, доступ, локальные миры, правила, небо, ГЛИТЧ-бездна, моды, квесты. */
public class SubworldScreen extends UiScreen {

    public enum Tab {
        MAIN, ACCESS, LOCAL, RULES, EXTRAS, SKY, ABYSS, MODS, QUESTS
    }

    private static final List<String> SKIP_MODS = Arrays.asList("minecraft", "mcp", "fml", "forge", "ecoserv", "forgelin");

    private final String id;
    private Tab tab;

    public SubworldScreen(EntityPlayerMP player, String id) {
        this(player, id, Tab.MAIN);
    }

    public SubworldScreen(EntityPlayerMP player, String id, Tab tab) {
        super(player);
        this.id = id;
        this.tab = tab;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    private Subworld sw() {
        return SubworldRegistry.get(id);
    }

    /** Сохранить подмир и разослать клиентам то, что им нужно. */
    static void changed(Subworld sw) {
        sw.normalize();
        SubworldRegistry.save();
        SubworldDims.sync(sw);
    }

    @Override
    protected void build(Ui ui) {
        final Subworld sw = sw();
        ui.back(() -> new WorldsScreen(player, WorldsScreen.Tab.WORLDS).open());
        if (sw == null) {
            ui.title("Подмир удалён");
            ui.note(Ui.RED, "Подмира «" + id + "» больше нет.");
            return;
        }
        final Runnable save = () -> changed(sw);
        ui.title(sw.name).subtitle("/world " + sw.id).icon(UiUtil.icon(sw.icon));
        ui.tab("Основное", UiIcons.book(), tab == Tab.MAIN, () -> tab = Tab.MAIN);
        ui.tab("Доступ", UiIcons.key(), tab == Tab.ACCESS, () -> tab = Tab.ACCESS);
        if (sw.perOwner()) {
            ui.tab("Локальные миры", UiIcons.map(), tab == Tab.LOCAL, () -> tab = Tab.LOCAL);
        }
        ui.tab("Правила", UiIcons.hourglass(), tab == Tab.RULES, () -> tab = Tab.RULES);
        ui.tab("Доп. правила", UiIcons.feather(), tab == Tab.EXTRAS, () -> tab = Tab.EXTRAS);
        ui.tab("Небо", UiIcons.sky(), tab == Tab.SKY, () -> tab = Tab.SKY);
        ui.tab("ГЛИТЧ-бездна", UiIcons.glitch(), tab == Tab.ABYSS, () -> tab = Tab.ABYSS);
        ui.tab("Моды", UiIcons.gear(), tab == Tab.MODS, () -> tab = Tab.MODS);
        ui.tab("Квесты и магазин", UiIcons.questBook(), tab == Tab.QUESTS, () -> tab = Tab.QUESTS);

        ui.sideLabel("&8Действия");
        ui.sideButton(sw.perOwner() ? "Войти в шаблон" : "Войти", new ItemStack(Items.ENDER_PEARL), Ui.GREEN, () -> {
            SubworldDim d = SubworldRegistry.find(sw.id, sw.perOwner() ? SubworldDim.TEMPLATE : SubworldDim.SHARED);
            if (d == null) {
                throw new UiError("У подмира нет мира для входа.");
            }
            close();
            SubworldTeleport.teleportTo(player, d, false, null);
        });
        if (sw.perOwner()) {
            ui.sideButton("Опубликовать", new ItemStack(Items.WRITABLE_BOOK), Ui.BLUE, () -> {
                String msg = SubworldManager.publish(sw, player);
                ChatUtil.send(player, msg);
                toast(UiUtil.plain(msg));
            });
        }
        ui.sideButton("Удалить подмир", new ItemStack(Blocks.TNT), Ui.RED, () -> delete(sw));

        if (!tab.equals(Tab.LOCAL) || sw.perOwner()) {
            switch (tab) {
                case ACCESS:
                    buildAccess(ui, sw, save);
                    return;
                case LOCAL:
                    buildLocal(ui, sw, save);
                    return;
                case RULES:
                    buildRules(ui, sw, save);
                    return;
                case EXTRAS:
                    ui.note(Ui.BLUE, "Работают во всех мирах подмира (в шаблоне и локальных мирах тоже).");
                    WorldParts.extras(ui, sw.rules.extras, save);
                    return;
                case SKY:
                    buildSky(ui, sw, save);
                    return;
                case ABYSS:
                    WorldParts.abyss(ui, sw.abyss, save, () -> {
                        close();
                        SubworldAbyss.show(player, sw.abyss);
                    });
                    return;
                case MODS:
                    buildMods(ui, sw);
                    return;
                case QUESTS:
                    buildQuests(ui, sw, save);
                    return;
                default:
            }
        }
        tab = Tab.MAIN;
        buildMain(ui, sw, save);
    }

    // --- основное -------------------------------------------------------------------------------------

    private void buildMain(Ui ui, final Subworld sw, final Runnable save) {
        List<SubworldDim> dims = SubworldRegistry.dimsOf(sw.id);
        int instances = 0;
        int online = 0;
        for (SubworldDim d : dims) {
            if (d.isInstance()) {
                instances++;
            }
            WorldServer w = DimensionManager.getWorld(d.dim);
            if (w != null) {
                online += w.playerEntities.size();
            }
        }
        Ui.Row info = ui.row(UiUtil.icon(sw.icon), "&6" + sw.name + " &7(" + sw.id + ")")
                .line("&7" + sw.typeTitle() + " · доступ: " + UiUtil.plain(sw.accessTitle()))
                .line("&8создал " + sw.creator + " " + Fmt.timestamp(sw.created)
                        + (sw.perOwner() ? " · локальных миров: " + instances + " · версия шаблона: " + sw.templateVersion : "")
                        + (online > 0 ? " · играют: " + online : ""))
                .chip(sw.enabled ? "включён" : "выключен", sw.enabled ? Ui.GREEN : Ui.GREY);
        if (sw.glitch) {
            info.chip("GLITCH", Ui.PURPLE);
        }
        if (SubworldManager.isBusy(sw.id)) {
            ui.note(Ui.YELLOW, "Подмир сейчас готовится (установка мира или публикация) — входить в него нельзя.");
        }
        if (sw.perOwner() && !sw.enabled) {
            ui.note(Ui.BLUE, "Как запустить: «Войти в шаблон» → построй → «Опубликовать» → включи подмир.");
        }
        ui.toggle("Подмир включён", "Выключенный видят только админы", sw.enabled, on -> {
            sw.enabled = on;
            save.run();
        });
        ui.field("Название", "Как подмир видят игроки, & — цвета", sw.name, v -> {
            if (v.trim().isEmpty()) {
                throw new UiError("Название не может быть пустым.");
            }
            sw.name = v.trim();
            save.run();
        }).max(48);
        ui.field("Описание", "Строка под названием в списке миров", sw.description, v -> {
            sw.description = v.trim();
            save.run();
        }).placeholder("нет").clearable().max(160);
        ui.field("Иконка", "Предмет в меню: кнопка … — выбрать из всех предметов", sw.icon, v -> {
            String item = UiUtil.parseItemId(v);
            if (item == null) {
                throw new UiError("Такого предмета нет: " + v);
            }
            sw.icon = item;
            save.run();
        }).picker(Ui.PICK_ITEM);
        ui.toggle("Возрождение внутри", "Погибший игрок возрождается в подмире (иначе — в основном мире)", sw.respawnInside, on -> {
            sw.respawnInside = on;
            save.run();
        });
        boolean hasInstances = instances > 0;
        ui.toggle("Пометка GLITCH", hasInstances ? "&cНельзя менять: у подмира уже есть локальные миры"
                        : "Локальный мир у каждого ИГРОКА, одно сохранение; ГЛИТЧ-сундуки и хранилище",
                sw.glitch, hasInstances || (!sw.perOwner() && !sw.glitch), on -> {
                    sw.glitch = on;
                    save.run();
                });
    }

    private void delete(Subworld sw) {
        if (SubworldManager.isWorking(sw.id)) {
            throw new UiError("Подмир сейчас готовится (установка или публикация) — удалить можно после окончания.");
        }
        // подтверждение — отдельным окном
        new ConfirmDelete(player, sw.id).open();
    }

    /** «Точно удалить?» с перечнем того, что пропадёт. */
    static final class ConfirmDelete extends UiScreen {
        private final String id;

        ConfirmDelete(EntityPlayerMP player, String id) {
            super(player);
            this.id = id;
        }

        @Override
        protected boolean canView() {
            return Perms.isAdmin(player);
        }

        @Override
        protected void build(Ui ui) {
            final Subworld sw = SubworldRegistry.get(id);
            ui.title("Удалить подмир?").icon(new ItemStack(Blocks.TNT)).compact();
            ui.back(() -> new SubworldScreen(player, id).open());
            if (sw == null) {
                ui.note(Ui.GREY, "Подмира уже нет.");
                return;
            }
            int instances = 0;
            for (SubworldDim d : SubworldRegistry.dimsOf(sw.id)) {
                if (d.isInstance()) {
                    instances++;
                }
            }
            ui.note(Ui.RED, "Подмир «" + sw.name + "&f» будет удалён навсегда: его миры" + (instances > 0
                    ? ", " + instances + " локальных миров команд" : "") + ", его прайс магазина и запреты модов «только "
                    + "в этом подмире». Игроков из него вернёт туда, откуда они пришли. Папка мира стирается сразу, "
                    + "как только миры выгрузятся; имя «" + sw.id + "» освободится.");
            ui.footer("Отмена", Ui.GREY, () -> new SubworldScreen(player, id).open());
            ui.footer("Удалить навсегда", Ui.RED, () -> {
                if (SubworldManager.isWorking(sw.id)) {
                    throw new UiError("Подмир сейчас готовится — удалить можно после окончания.");
                }
                String note = SubworldManager.delete(sw);
                ChatUtil.send(player, "&aПодмир &f" + sw.id + " &aудалён." + (note.isEmpty() ? "" : " " + note));
                new WorldsScreen(player, WorldsScreen.Tab.WORLDS).withToast("Подмир «" + UiUtil.plain(sw.name)
                        + "» удалён." + (note.isEmpty() ? "" : " " + UiUtil.plain(note))).open();
            }).confirm("Последний вопрос: удалить «" + sw.name + "&f» со всеми мирами?");
        }
    }

    // --- доступ -----------------------------------------------------------------------------------------

    private void buildAccess(Ui ui, final Subworld sw, final Runnable save) {
        CreatePanel.accessSelect(ui, sw.access, v -> {
            sw.access = v;
            save.run();
        });
        CreatePanel.accessValue(ui, sw, save);
        CreatePanel.ageField(ui, sw, save);
        ui.header("Игрокам вручную");
        ui.field("Открыть доступ игроку", "Ник — доступ откроется без оплаты и условий (/subworld unlock)", "", v -> {
            UUID uuid = player(v);
            if (uuid == null) {
                return;
            }
            SubworldRegistry.unlock(uuid.toString(), sw.id);
            toast("Доступ открыт: " + PlayerUtil.nameOf(uuid));
        }).picker(Ui.PICK_PLAYER).placeholder("ник").max(16);
        ui.field("Закрыть доступ игроку", "Ник — убрать открытый вручную или купленный доступ (/subworld lock)", "", v -> {
            UUID uuid = player(v);
            if (uuid == null) {
                return;
            }
            toast(SubworldRegistry.lock(uuid.toString(), sw.id) ? "Доступ закрыт: " + PlayerUtil.nameOf(uuid)
                    : "У игрока не было открытого доступа.");
        }).picker(Ui.PICK_PLAYER).placeholder("ник").max(16);
    }

    private static UUID player(String name) {
        String n = name.trim();
        if (n.isEmpty()) {
            return null;
        }
        UUID uuid = PlayerUtil.uuidOf(n);
        if (uuid == null) {
            throw new UiError("Игрок " + n + " не найден (он должен хотя бы раз зайти на сервер).");
        }
        return uuid;
    }

    // --- локальные миры ---------------------------------------------------------------------------------

    private void buildLocal(Ui ui, final Subworld sw, final Runnable save) {
        ui.number("Сохранений у команды", sw.glitch ? "У GLITCH — всегда одно" : "Сколько своих миров может завести команда (1–64)",
                sw.maxSaves, 1, 64, v -> {
                    sw.maxSaves = (int) v;
                    save.run();
                }).disabled(sw.glitch);
        ui.money("Цена сохранения", "Первый мир бесплатный, каждый следующий — по этой цене", sw.savePrice, v -> {
            sw.savePrice = v;
            save.run();
        });
        CreatePanel.borderFields(ui, sw, () -> {
            save.run();
            applyBorders(sw);
        });
        ui.toggle("Гости", "Команды могут приглашать гостей в свои локальные миры", sw.guests, on -> {
            sw.guests = on;
            save.run();
        });
        List<SubworldDim> list = new ArrayList<SubworldDim>();
        for (SubworldDim d : SubworldRegistry.dimsOf(sw.id)) {
            if (d.isInstance()) {
                list.add(d);
            }
        }
        list.sort((a, b) -> Long.compare(b.lastUsed, a.lastUsed));
        ui.header("Локальные миры команд", Integer.toString(list.size()));
        ui.search("поиск по владельцу или названию");
        if (list.isEmpty()) {
            ui.text("&8Пока ни у кого нет своего мира.");
        }
        for (final SubworldDim d : list) {
            WorldServer w = DimensionManager.getWorld(d.dim);
            Ui.Row row = ui.row(new ItemStack(w != null ? Items.FILLED_MAP : Items.MAP),
                    "&e" + SubworldTeams.ownerName(d.owner) + " &7— &f" + d.saveName)
                    .line("&7измерение " + d.dim + (d.border > 0 ? " · барьер " + d.border : "") + " · гостей: " + d.guests.size())
                    .line("&8создан " + Fmt.timestamp(d.created) + " · был открыт " + Fmt.timestamp(d.lastUsed)
                            + " · версия шаблона " + d.templateVersion);
            if (w != null) {
                row.chip("загружен" + (w.playerEntities.isEmpty() ? "" : ": " + w.playerEntities.size()), Ui.GREEN);
            }
            row.button("Телепорт", Ui.BLUE, () -> {
                close();
                SubworldTeleport.teleportTo(player, d, false, null);
            });
            if (d.border > 0) {
                row.button("+барьер", Ui.GREY, () -> {
                    int r = SubworldBorder.expand(d, 0);
                    SubworldRegistry.saveIfDirty();
                    toast(r < 0 ? "У этого мира нет барьера." : "Барьер: " + r);
                }).tip("Расширить барьер на шаг (" + sw.borderStep + ")");
            }
            row.button("Удалить", Ui.RED, () -> {
                SubworldManager.deleteDim(d, "&eЭтот локальный мир удалён админом.");
                toast("Локальный мир удалён.");
            }).confirm("Удалить мир «" + d.saveName + "» команды " + SubworldTeams.ownerName(d.owner) + " навсегда?");
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

    // --- правила ------------------------------------------------------------------------------------------

    private void buildRules(Ui ui, final Subworld sw, final Runnable save) {
        final Subworld.Rules r = sw.rules;
        final Runnable apply = () -> {
            save.run();
            SubworldRules.applyLoaded(sw);
        };
        boolean custom = SubworldInfo.fixedTime(r.time) >= 0 && r.time.matches("\\d+");
        ui.select("Время", "Обычная смена дня и ночи или всегда одно время", custom ? "custom" : r.time, v -> {
            if ("custom".equals(v)) {
                r.time = "6000";
            } else {
                r.time = v;
            }
            apply.run();
        }).option("cycle", "Смена дня и ночи", "", new ItemStack(Items.CLOCK))
                .option("day", "Всегда утро", "1000 тиков", new ItemStack(Blocks.DOUBLE_PLANT))
                .option("noon", "Всегда полдень", "6000 тиков", new ItemStack(Blocks.YELLOW_FLOWER))
                .option("sunset", "Всегда закат", "12000 тиков", new ItemStack(Blocks.RED_FLOWER))
                .option("night", "Всегда ночь", "13000 тиков", new ItemStack(Blocks.OBSIDIAN))
                .option("midnight", "Всегда полночь", "18000 тиков", new ItemStack(Blocks.COAL_BLOCK))
                .option("custom", "Своё время", "число тиков 0–23999", new ItemStack(Items.PAPER));
        if (custom) {
            ui.number("Время в тиках", "0–23999 (6000 — полдень, 18000 — полночь)", Long.parseLong(r.time), 0, 23999, v -> {
                r.time = Long.toString(v);
                apply.run();
            });
        }
        ui.select("Погода", "", r.weather, v -> {
            r.weather = v;
            apply.run();
        }).option("cycle", "Обычная", "", new ItemStack(Items.WATER_BUCKET))
                .option("clear", "Всегда ясно", "", new ItemStack(Blocks.DOUBLE_PLANT))
                .option("rain", "Всегда дождь", "", new ItemStack(Items.WATER_BUCKET))
                .option("thunder", "Всегда гроза", "", new ItemStack(Items.BLAZE_ROD));
        ui.toggle("PvP", "Игроки могут бить друг друга", r.pvp, on -> {
            r.pvp = on;
            apply.run();
        });
        ui.toggle("Сохранение инвентаря", "Вещи не выпадают при смерти", r.keepInventory, on -> {
            r.keepInventory = on;
            apply.run();
        });
        ui.toggle("Мобы появляются", "", r.mobs, on -> {
            r.mobs = on;
            apply.run();
        });
        ui.toggle("Огонь распространяется", "", r.fireSpread, on -> {
            r.fireSpread = on;
            apply.run();
        });
        ui.toggle("Мобы ломают блоки", "Криперы, эндермены…", r.mobGriefing, on -> {
            r.mobGriefing = on;
            apply.run();
        });
        WorldParts.gamemodeSelect(ui, "Режим игры при входе", "При выходе вернётся прежний режим (админам не меняется)",
                r.gamemode, v -> {
                    r.gamemode = v;
                    apply.run();
                });
        ui.field("Приветствие на экране", "«заголовок|подзаголовок», & — цвета. Пусто — название подмира",
                r.welcome, v -> {
                    r.welcome = v.trim();
                    apply.run();
                }).placeholder("название подмира").clearable().max(160);
        ui.header("Стартовый набор", r.starterKit.isEmpty() ? "&8пусто" : r.starterKit.size() + " предм.");
        ui.text("&7Выдаётся один раз при первом входе в подмир.");
        List<ItemStack> kit = ItemCodec.decodeAll(r.starterKit);
        if (!kit.isEmpty()) {
            Ui.Grid g = ui.grid();
            for (ItemStack st : kit) {
                g.cell(st, false, null);
            }
        }
        Ui.Buttons b = ui.buttons();
        b.add("Взять из хотбара", Ui.BLUE, () -> {
            List<ItemStack> hotbar = new ArrayList<ItemStack>();
            for (int i = 0; i < 9; i++) {
                ItemStack st = player.inventory.getStackInSlot(i);
                if (!st.isEmpty()) {
                    hotbar.add(st.copy());
                }
            }
            if (hotbar.isEmpty()) {
                throw new UiError("Положи предметы набора в панель быстрого доступа (1–9).");
            }
            r.starterKit = ItemCodec.encodeAll(hotbar);
            save.run();
            toast("Стартовый набор: " + ItemCodec.describe(hotbar, 9));
        }).tip("Предметы из слотов 1–9 станут набором");
        b.add("Очистить", Ui.GREY, () -> {
            r.starterKit.clear();
            save.run();
        }).disabled(r.starterKit.isEmpty());
        b.add("Выдать ещё раз всем", Ui.YELLOW, () -> {
            int n = SubworldRegistry.resetKits(sw.id);
            toast("Набор снова получат игроков: " + n);
        }).confirm("Сбросить отметку «уже получал» — каждый игрок получит набор при следующем входе?");
    }

    // --- небо ------------------------------------------------------------------------------------------------

    private void buildSky(Ui ui, final Subworld sw, final Runnable save) {
        String skyValue = sw.sky.startsWith("dim:") ? "dim" : sw.sky;
        ui.select("Небо", "Как выглядит небо в мирах подмира", skyValue, v -> {
            sw.sky = "dim".equals(v) ? "dim:7" : v;
            save.run();
        }).option("default", "По типу мира", "", new ItemStack(Blocks.GRASS))
                .option("overworld", "Обычное", "солнце, луна, облака", new ItemStack(Blocks.DOUBLE_PLANT))
                .option("nether", "Как в аду", "", new ItemStack(Blocks.NETHERRACK))
                .option("end", "Как в крае", "", new ItemStack(Blocks.END_STONE))
                .option("color", "Сплошной цвет", "цвет ниже", new ItemStack(Items.DYE, 1, 4))
                .option("dim", "Небо другого измерения", "по номеру (например 7 — Сумеречный лес)", new ItemStack(Items.ENDER_EYE));
        if (sw.sky.startsWith("dim:")) {
            long cur;
            try {
                cur = Long.parseLong(sw.sky.substring(4));
            } catch (NumberFormatException e) {
                cur = 0;
            }
            ui.number("Номер измерения", "Чьё небо взять", cur, Integer.MIN_VALUE, Integer.MAX_VALUE, v -> {
                sw.sky = "dim:" + v;
                save.run();
            });
        }
        ui.field("Цвет неба", "Для неба «сплошной цвет», #RRGGBB", Ui.hex(sw.skyColor), v -> {
            Integer c = Ui.parseColor(v);
            if (c == null) {
                throw new UiError("Нужен цвет вида #3A5FCD.");
            }
            sw.skyColor = c;
            if (!"color".equals(sw.sky)) {
                sw.sky = "color";
            }
            save.run();
        }).picker(Ui.PICK_COLOR).max(7);
        ui.field("Цвет тумана", "#RRGGBB; пусто — как у неба", Ui.hex(sw.fogColor), v -> {
            if (v.trim().isEmpty()) {
                sw.fogColor = -1;
            } else {
                Integer c = Ui.parseColor(v);
                if (c == null) {
                    throw new UiError("Нужен цвет вида #3A5FCD.");
                }
                sw.fogColor = c;
            }
            save.run();
        }).picker(Ui.PICK_COLOR).clearable().placeholder("как у неба").max(7);
    }

    // --- моды ---------------------------------------------------------------------------------------------

    /** Действует ли правило мода в этом подмире. */
    static boolean disabledIn(ItemRule rule, String subworld) {
        if (rule == null || !rule.hasAnyDeny()) {
            return false;
        }
        if (rule.worlds.isEmpty()) {
            return !containsName(rule.except, subworld);
        }
        return containsName(rule.worlds, subworld);
    }

    private static boolean containsName(List<String> names, String name) {
        for (String n : names) {
            if (WorldNames.normalize(n).equals(WorldNames.normalize(name))) {
                return true;
            }
        }
        return false;
    }

    private static void removeName(List<String> names, String name) {
        names.removeIf(n -> WorldNames.normalize(n).equals(WorldNames.normalize(name)));
    }

    private void buildMods(Ui ui, final Subworld sw) {
        ui.note(Ui.BLUE, "Отключённый мод: его предметы не переносятся в подмир (ждут в буфере /stash), не ставятся "
                + "и не крафтятся здесь. «Тонко» — какие именно действия запретить.");
        ui.search("поиск мода");
        List<ModContainer> mods = new ArrayList<ModContainer>();
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            if (!SKIP_MODS.contains(mod.getModId().toLowerCase(Locale.ROOT))) {
                mods.add(mod);
            }
        }
        mods.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (final ModContainer mod : mods) {
            final String key = ItemRules.modKey(mod.getModId());
            ItemRule rule = ItemRules.exact(key);
            boolean off = disabledIn(rule, sw.id);
            Ui.Row row = ui.row(off ? new ItemStack(Blocks.BARRIER) : new ItemStack(Items.BOOK), (off ? "&c" : "&f") + mod.getName())
                    .line("&8" + mod.getModId() + (rule != null ? " · правило: " + UiUtil.plain(rule.describeWorlds()) : ""))
                    .chip(off ? "отключён" : "работает", off ? Ui.RED : Ui.GREEN);
            row.button(off ? "Включить" : "Отключить", off ? Ui.GREEN : Ui.RED, () -> toggleMod(sw, key));
            row.button("Тонко", Ui.GREY, () -> {
                if (ItemRules.exact(key) == null) {
                    create(sw, key);
                }
                new com.ecoserv.restrict.RuleScreen(player, key, ItemStack.EMPTY).open();
            }).tip("Настроить правило мода: действия и миры");
        }
    }

    private void toggleMod(Subworld sw, String key) {
        ItemRule rule = ItemRules.exact(key);
        if (disabledIn(rule, sw.id)) {
            if (rule.worlds.isEmpty()) {
                rule.except.add(sw.id);
            } else {
                removeName(rule.worlds, sw.id);
                if (rule.worlds.isEmpty()) {
                    ItemRules.remove(key);
                    return;
                }
            }
        } else if (rule == null) {
            create(sw, key);
            return;
        } else if (rule.worlds.isEmpty()) {
            removeName(rule.except, sw.id);
            if (!rule.hasAnyDeny()) {
                allDenied(rule);
            }
        } else {
            rule.worlds.add(sw.id);
            if (!rule.hasAnyDeny()) {
                allDenied(rule);
            }
        }
        ItemRules.changed();
    }

    private static ItemRule create(Subworld sw, String key) {
        ItemRule rule = ItemRules.getOrCreate(key);
        allDenied(rule);
        rule.worlds.clear();
        rule.worlds.add(sw.id);
        rule.note = "отключён в подмире " + sw.id;
        ItemRules.changed();
        return rule;
    }

    private static void allDenied(ItemRule rule) {
        for (RestrictAction a : RestrictAction.values()) {
            rule.setDenied(a, true);
        }
    }

    // --- квесты и магазин ------------------------------------------------------------------------------------

    private void buildQuests(Ui ui, final Subworld sw, final Runnable save) {
        ui.toggle("Свои квесты и магазин", "В подмире видны только его главы FTB Quests, свой прайс магазина и продажи",
                sw.ownQuests, on -> {
                    sw.ownQuests = on;
                    save.run();
                    SubworldShops.resync(sw);
                });
        if (!sw.ownQuests) {
            ui.note(Ui.GREY, "Включи, чтобы выбрать главы квестов и свой магазин.");
            return;
        }
        ShopList own = ShopList.scope(sw.id);
        ui.header("Магазин подмира", own.size() + " позиций");
        ui.text("&7Правь магазин как обычно, находясь в подмире (/shop → «Цены»).");
        ui.buttons().add("Скопировать общий прайс", Ui.BLUE, () -> {
            int n = SubworldShops.copyGlobal(sw);
            toast("В прайс подмира скопировано позиций: " + n);
        }).confirm("Скопировать все позиции общего магазина в магазин подмира?");
        ui.header("Главы квестов", sw.questChapters.size() + " выбрано");
        if (!SubworldQuests.available()) {
            ui.note(Ui.YELLOW, "FTB Quests не найден (или мод собран без него) — главы выбрать нельзя.");
            return;
        }
        ui.text("&7Отмеченные главы видны только в этом подмире, в остальных мирах скрыты. В режиме редактирования "
                + "FTB Quests видно всё. Новые главы создавай в книге как обычно.");
        ui.search("поиск главы");
        List<String[]> chapters = SubworldQuests.chapters();
        if (chapters.isEmpty()) {
            ui.text("&8В FTB Quests пока нет глав.");
        }
        for (final String[] ch : chapters) {
            List<String> others = new ArrayList<String>();
            for (Subworld o : SubworldRegistry.all()) {
                if (o != sw && o.questChapters.contains(ch[0])) {
                    others.add(UiUtil.plain(o.name));
                }
            }
            ui.toggle(ch[1], "&8id " + ch[0] + (others.isEmpty() ? "" : " · ещё в: " + String.join(", ", others)),
                    sw.questChapters.contains(ch[0]), on -> {
                        sw.questChapters.remove(ch[0]);
                        if (on) {
                            sw.questChapters.add(ch[0]);
                        }
                        SubworldRegistry.save();
                        SubworldDims.sync(sw);
                    });
        }
    }
}
