package com.ecoserv.subworld.menu;

import com.ecoserv.subworld.SubworldAbyss;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldRules;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.world.WorldBorders;
import com.ecoserv.world.WorldNames;
import com.ecoserv.world.WorldSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraft.world.border.WorldBorder;
import net.minecraftforge.common.DimensionManager;

/** Настройки обычного мира (основной, ад, край, миры модов): правила, доп. правила, барьер, ГЛИТЧ-бездна. */
public class MainWorldScreen extends UiScreen {

    private enum Tab {
        MAIN, ACCESS, RULES, EXTRAS, BORDER, SKY, ABYSS, MODS, QUESTS
    }

    private final int dim;
    private Tab tab = Tab.MAIN;

    public MainWorldScreen(EntityPlayerMP player, int dim) {
        super(player);
        this.dim = dim;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    private void changed() {
        WorldSettings.changed();
    }

    @Override
    protected void build(Ui ui) {
        final WorldSettings.Settings s = WorldSettings.get(dim);
        ui.title("Мир: " + WorldNames.nameOf(dim)).subtitle("измерение " + dim).icon(MainWorldsPanel.dimIcon(dim));
        ui.back(() -> new WorldsScreen(player, WorldsScreen.Tab.MAIN).open());
        ui.tab("Основное", UiIcons.book(), tab == Tab.MAIN, () -> tab = Tab.MAIN);
        ui.tab("Доступ", UiIcons.key(), tab == Tab.ACCESS, () -> tab = Tab.ACCESS);
        ui.tab("Правила", UiIcons.hourglass(), tab == Tab.RULES, () -> tab = Tab.RULES);
        ui.tab("Доп. правила", UiIcons.feather(), tab == Tab.EXTRAS, () -> tab = Tab.EXTRAS);
        ui.tab("Барьер", UiIcons.expand(), tab == Tab.BORDER, () -> tab = Tab.BORDER);
        ui.tab("Небо", UiIcons.sky(), tab == Tab.SKY, () -> tab = Tab.SKY);
        ui.tab("ГЛИТЧ-бездна", UiIcons.glitch(), tab == Tab.ABYSS, () -> tab = Tab.ABYSS);
        ui.tab("Моды", UiIcons.gear(), tab == Tab.MODS, () -> tab = Tab.MODS);
        ui.tab("Квесты и магазин", UiIcons.questBook(), tab == Tab.QUESTS, () -> tab = Tab.QUESTS);
        if (player.dimension != dim) {
            ui.sideButton("Перейти туда", UiIcons.portal(), Ui.GREEN, () -> {
                close();
                player.getServer().getCommandManager().executeCommand(player, "world " + WorldNames.nameOf(dim));
            });
        }
        if (SubworldRegistry.isOurs(dim)) {
            ui.note(Ui.YELLOW, "Это измерение подмира — его настраивают в настройках подмира.");
            return;
        }
        ui.status("&7Изменения применяются сразу и сохраняются в world/ecoserv/world_settings.json");
        switch (tab) {
            case MAIN:
                buildMain(ui, s);
                break;
            case ACCESS:
                buildAccess(ui, s);
                break;
            case SKY:
                buildSky(ui, s);
                break;
            case MODS:
                WorldParts.mods(ui, player, WorldNames.nameOf(dim), "мир «" + WorldNames.nameOf(dim) + "»");
                break;
            case QUESTS:
                buildQuests(ui, s);
                break;
            case RULES:
                buildRules(ui, s);
                break;
            case EXTRAS:
                WorldParts.extras(ui, s.extras, this::changed);
                break;
            case BORDER:
                buildBorder(ui, s);
                break;
            case ABYSS:
                WorldParts.abyss(ui, s.abyss, this::changed, () -> {
                    close();
                    SubworldAbyss.show(player, s.abyss);
                });
                break;
            default:
                buildMain(ui, s);
        }
    }

    // --- 1.8.0: как у подмиров -------------------------------------------------------------------------

    private void buildMain(Ui ui, final WorldSettings.Settings s) {
        WorldServer w = DimensionManager.getWorld(dim);
        Ui.Row info = ui.row(MainWorldsPanel.dimIcon(dim), "&6" + WorldNames.displayName(dim) + " &7(измерение " + dim + ")")
                .line("&7" + (w == null ? "не загружен" : "загружен · игроков: " + w.playerEntities.size()))
                .line("&7Доступ: &f" + UiUtil.plain(s.accessView().accessTitle()))
                .chip(WorldNames.allowedForPlayers(dim) ? "в /world" : "только админам", WorldNames.allowedForPlayers(dim) ? Ui.GREEN : Ui.GREY);
        if (s.noMenuTeleport) {
            info.chip("без входа из меню", Ui.GREY);
        }
        ui.toggle("Запретить вход через меню", "В меню миров у игроков не будет кнопки «Перейти». Порталы, обычный путь "
                + "(портал в ад и т.п.) и /world у админов работают", s.noMenuTeleport, on -> {
            s.noMenuTeleport = on;
            changed();
        });
        ui.field("Приветствие на экране", "При входе в мир: «заголовок|подзаголовок», & — цвета. Пусто — не показывать",
                s.welcome, v -> {
                    s.welcome = v.trim();
                    changed();
                }).placeholder("&6Ад|&7здесь жарко").clearable().max(160);
        ui.text("&8Кто вообще может заходить командой /world в этот мир — настройка worlds → player_worlds в "
                + "настройках мода (или роли с доступом к миру).");
    }

    private void buildAccess(Ui ui, final WorldSettings.Settings s) {
        final com.ecoserv.subworld.Subworld view = s.accessView();
        Runnable save = () -> {
            s.copyAccess(view);
            changed();
        };
        CreatePanel.accessSelect(ui, view.access, v -> {
            view.access = v;
            save.run();
        });
        CreatePanel.accessValue(ui, view, save);
        CreatePanel.ageField(ui, view, save);
        ui.note(Ui.BLUE, "Проверяется при входе через /world и меню миров. Обычный путь (портал в ад, край) "
                + "не закрывается — для этого есть запреты и барьеры.");
        ui.header("Игрокам вручную");
        ui.field("Открыть доступ игроку", "Ник — без оплаты и условий", "", v -> {
            java.util.UUID uuid = com.ecoserv.util.PlayerUtil.uuidOf(v.trim());
            if (uuid == null) {
                throw new UiError("Игрок " + v.trim() + " не найден.");
            }
            SubworldRegistry.unlock(uuid.toString(), com.ecoserv.world.WorldAccess.key(dim));
            toast("Доступ открыт: " + com.ecoserv.util.PlayerUtil.nameOf(uuid));
        }).picker(Ui.PICK_PLAYER).placeholder("ник").max(16);
        ui.field("Закрыть доступ игроку", "Ник — убрать открытый вручную или купленный доступ", "", v -> {
            java.util.UUID uuid = com.ecoserv.util.PlayerUtil.uuidOf(v.trim());
            if (uuid == null) {
                throw new UiError("Игрок " + v.trim() + " не найден.");
            }
            toast(SubworldRegistry.lock(uuid.toString(), com.ecoserv.world.WorldAccess.key(dim))
                    ? "Доступ закрыт." : "У игрока не было открытого доступа.");
        }).picker(Ui.PICK_PLAYER).placeholder("ник").max(16);
    }

    private void buildSky(Ui ui, final WorldSettings.Settings s) {
        String skyValue = s.sky.startsWith("dim:") ? "dim" : s.sky;
        ui.select("Небо", "Как выглядит небо в этом мире", skyValue, v -> {
            s.sky = "dim".equals(v) ? "dim:7" : v;
            changed();
        }).option("default", "Как обычно", "небо этого мира", new ItemStack(Blocks.GRASS))
                .option("overworld", "Обычное", "солнце, луна, облака (в аду не работает — там сплошной цвет)", new ItemStack(Blocks.DOUBLE_PLANT))
                .option("nether", "Как в аду", "", new ItemStack(Blocks.NETHERRACK))
                .option("end", "Как в крае", "", new ItemStack(Blocks.END_STONE))
                .option("color", "Сплошной цвет", "цвет ниже", new ItemStack(Items.DYE, 1, 4))
                .option("dim", "Небо другого измерения", "по номеру", new ItemStack(Items.ENDER_EYE));
        if (s.sky.startsWith("dim:")) {
            long cur;
            try {
                cur = Long.parseLong(s.sky.substring(4));
            } catch (NumberFormatException e) {
                cur = 0;
            }
            ui.number("Номер измерения", "Чьё небо взять", cur, Integer.MIN_VALUE, Integer.MAX_VALUE, v -> {
                s.sky = "dim:" + v;
                changed();
            });
        }
        ui.field("Цвет неба", "Для неба «сплошной цвет», #RRGGBB", Ui.hex(s.skyColor), v -> {
            Integer c = Ui.parseColor(v);
            if (c == null) {
                throw new UiError("Нужен цвет вида #3A5FCD.");
            }
            s.skyColor = c;
            if (!"color".equals(s.sky)) {
                s.sky = "color";
            }
            changed();
        }).picker(Ui.PICK_COLOR).max(7);
        ui.field("Цвет тумана", "#RRGGBB; пусто — как у неба", Ui.hex(s.fogColor), v -> {
            if (v.trim().isEmpty()) {
                s.fogColor = -1;
            } else {
                Integer c = Ui.parseColor(v);
                if (c == null) {
                    throw new UiError("Нужен цвет вида #3A5FCD.");
                }
                s.fogColor = c;
            }
            changed();
        }).picker(Ui.PICK_COLOR).clearable().placeholder("как у неба").max(7);
    }

    private void buildQuests(Ui ui, final WorldSettings.Settings s) {
        final String scope = com.ecoserv.world.WorldAccess.key(dim);
        ui.toggle("Свои квесты и магазин", "В этом мире видны только выбранные главы FTB Quests, свой прайс магазина и продажи",
                s.ownQuests, on -> {
                    s.ownQuests = on;
                    changed();
                    resyncShop();
                });
        if (!s.ownQuests) {
            ui.note(Ui.GREY, "Включи, чтобы выбрать главы квестов и свой магазин.");
            return;
        }
        com.ecoserv.shop.ShopList own = com.ecoserv.shop.ShopList.scope(scope);
        ui.header("Магазин мира", own.size() + " позиций");
        ui.text("&7Правь магазин как обычно, находясь в этом мире (/shop → «Цены скупщика»).");
        ui.buttons().add("Скопировать общий прайс", Ui.BLUE, () -> {
            com.ecoserv.shop.ShopList list = com.ecoserv.shop.ShopList.scope(scope);
            list.copyFrom(com.ecoserv.shop.ShopList.get());
            list.save();
            resyncShop();
            toast("Скопировано позиций: " + list.size());
        }).confirm("Скопировать все позиции общего магазина в магазин этого мира?");
        ui.header("Главы квестов", s.questChapters.size() + " выбрано");
        if (!com.ecoserv.subworld.SubworldQuests.available()) {
            ui.note(Ui.YELLOW, "FTB Quests не найден (или мод собран без него) — главы выбрать нельзя.");
            return;
        }
        ui.search("поиск главы");
        for (final String[] ch : com.ecoserv.subworld.SubworldQuests.chapters()) {
            ui.toggle(ch[1], "&8id " + ch[0], s.questChapters.contains(ch[0]), on -> {
                s.questChapters.remove(ch[0]);
                if (on) {
                    s.questChapters.add(ch[0]);
                }
                changed();
            });
        }
    }

    private void resyncShop() {
        WorldServer w = DimensionManager.getWorld(dim);
        if (w != null) {
            for (EntityPlayer p : w.playerEntities) {
                if (p instanceof EntityPlayerMP) {
                    com.ecoserv.network.PacketHandler.sendShopList((EntityPlayerMP) p);
                }
            }
        }
    }

    private void buildRules(Ui ui, final WorldSettings.Settings s) {
        ui.select("PvP", "Могут ли игроки бить друг друга в этом мире", s.pvp, v -> {
            s.pvp = v;
            changed();
        }).option(WorldSettings.DEFAULT, "Как обычно", "по настройке сервера (server.properties)", new ItemStack(Items.MAP))
                .option(WorldSettings.ON, "Можно", "", new ItemStack(Items.IRON_SWORD))
                .option(WorldSettings.OFF, "Нельзя", "игроки не наносят урон друг другу", new ItemStack(Items.SHIELD));
        WorldParts.gamemodeSelect(ui, "Режим игры при входе", "При выходе вернётся прежний режим (админам не меняется)",
                s.gamemode, v -> {
                    s.gamemode = v;
                    changed();
                    // тем, кто уже в мире, — сразу
                    WorldServer w = DimensionManager.getWorld(dim);
                    if (w != null) {
                        for (EntityPlayer p : w.playerEntities) {
                            if (p instanceof EntityPlayerMP) {
                                SubworldRules.reconcile((EntityPlayerMP) p);
                            }
                        }
                    }
                });
        ui.select("Сохранение инвентаря", "Что с вещами и опытом при смерти в этом мире", s.keepInventory, v -> {
            s.keepInventory = v;
            changed();
        }).option(WorldSettings.DEFAULT, "Как правило игры", "/gamerule keepInventory", new ItemStack(Items.MAP))
                .option(WorldSettings.ON, "Вещи остаются", "не выпадают при смерти", new ItemStack(Blocks.CHEST))
                .option(WorldSettings.OFF, "Вещи выпадают", "", new ItemStack(Items.BONE));
        ui.select("Мобы", "Кто появляется сам (спавнеры и яйца работают всегда)", s.mobs, v -> {
            s.mobs = v;
            changed();
        }).option("all", "Все", "как обычно", new ItemStack(Items.ROTTEN_FLESH))
                .option("peaceful", "Без враждебных", "только животные", new ItemStack(Items.WHEAT))
                .option("none", "Никто", "мобы сами не появляются", new ItemStack(Blocks.BARRIER));
    }

    private void buildBorder(Ui ui, final WorldSettings.Settings s) {
        final WorldServer w = DimensionManager.getWorld(dim);
        if (!WorldBorders.separate() && dim != 0) {
            ui.note(Ui.YELLOW, "Сейчас у всех миров один барьер (основного мира). Включи в настройках мода "
                    + "worlds → separate_borders, чтобы у этого мира был свой.");
        } else if (dim == 0) {
            ui.note(Ui.BLUE, WorldBorders.separate() ? "Барьер основного мира. У остальных миров — свои."
                    : "Барьер основного мира — сейчас он же у всех миров.");
        }
        if (w == null) {
            ui.note(Ui.GREY, "Мир сейчас не загружен — значения ниже из файла настроек, применятся при загрузке.");
        }
        WorldBorder b = w == null ? null : w.getWorldBorder();
        double cx = b != null ? b.getCenterX() : s.border != null ? s.border.centerX : 0;
        double cz = b != null ? b.getCenterZ() : s.border != null ? s.border.centerZ : 0;
        double size = b != null ? b.getTargetSize() : s.border != null ? s.border.size : 6.0E7D;
        // ваниль даёт мирам «бесконечный» барьер 29 999 984 (или 60 000 000)
        int radius = size >= 2.9E7D ? 0 : (int) Math.round(size / 2.0D);
        ui.bar("Радиус: " + (radius == 0 ? "без барьера" : radius + " блоков"), radius == 0 ? 1 : radius,
                radius == 0 ? 1 : Math.max(radius, 1000), radius == 0 ? Ui.GREY : Ui.BLUE);
        ui.number("Радиус барьера", "В блоках от центра; 0 — без барьера. Команда: /worldborder set <диаметр>",
                radius, 0, 29999984, v -> setBorder(s, w, cx, cz, (int) v));
        ui.number("Центр X", "", (long) Math.floor(cx), -29999984, 29999984, v -> setBorder(s, w, v + 0.5D, cz, radius));
        ui.number("Центр Z", "", (long) Math.floor(cz), -29999984, 29999984, v -> setBorder(s, w, cx, v + 0.5D, radius));
        Ui.Buttons bs = ui.buttons();
        bs.add("Центр — где я стою", Ui.BLUE, () -> {
            if (player.dimension != dim) {
                throw new UiError("Ты не в этом мире.");
            }
            setBorder(s, w, Math.floor(player.posX) + 0.5D, Math.floor(player.posZ) + 0.5D, radius);
        }).disabled(player.dimension != dim);
        bs.add("Убрать барьер", Ui.RED, () -> setBorder(s, w, 0.0D, 0.0D, 0)).disabled(radius == 0);
        if (b != null) {
            ui.field("Урон за барьером", "Сердечек за блок за барьером в секунду (ваниль: 0.2)",
                    Double.toString(b.getDamageAmount()), v -> {
                        try {
                            b.setDamageAmount(Math.max(0.0D, Double.parseDouble(v.trim().replace(',', '.'))));
                        } catch (NumberFormatException e) {
                            throw new UiError("Нужно число, например 0.2");
                        }
                    });
            ui.number("Предупреждение за (блоков)", "Экран краснеет на таком расстоянии от барьера", b.getWarningDistance(),
                    0, 1000, v -> b.setWarningDistance((int) v));
        }
    }

    private void setBorder(WorldSettings.Settings s, WorldServer w, double cx, double cz, int radius) {
        if (w != null) {
            WorldBorders.setRadius(w, cx, cz, radius);
        } else {
            if (s.border == null) {
                s.border = new WorldSettings.BorderData();
            }
            s.border.centerX = cx;
            s.border.centerZ = cz;
            s.border.size = radius <= 0 ? 6.0E7D : radius * 2.0D;
            changed();
        }
        toast(radius <= 0 ? "Барьер убран." : "Барьер: радиус " + radius + ", центр " + (int) Math.floor(cx) + " " + (int) Math.floor(cz));
    }
}
