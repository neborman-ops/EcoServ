package com.ecoserv.subworld.menu;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.region.Region;
import com.ecoserv.region.RegionManager;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldAccess;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldManager;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldTeams;
import com.ecoserv.subworld.SubworldTeleport;
import com.ecoserv.subworld.WorldCrews;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldNames;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Главное окно миров (кнопка-планета в инвентаре, /world menu): подмиры и обычные миры, своя команда
 * и локальные миры, а для админов — создание подмиров, порталы, настройки основных миров и админ-панель.
 */
public class WorldsScreen extends UiScreen {

    public enum Tab {
        WORLDS, TEAM, MYWORLDS, CREATE, PORTALS, MAIN, ADMIN
    }

    Tab tab;
    /** 1.9.0: черновик своего мира (вкладка «Мои миры»). */
    final PlayerWorldsPanel.Draft draft = new PlayerWorldsPanel.Draft();

    /** Уведомление из панелей (toast защищён в UiScreen). */
    void toastText(String text) {
        toast(text);
    }

    public WorldsScreen(EntityPlayerMP player, Tab tab) {
        super(player);
        this.tab = tab;
    }

    public static void open(EntityPlayerMP player) {
        open(player, Tab.WORLDS);
    }

    public static void open(EntityPlayerMP player, Tab tab) {
        // подмиры могут быть выключены — обычные миры и команда всё равно показываются
        new WorldsScreen(player, tab).open();
    }

    boolean admin() {
        return Perms.isAdmin(player);
    }

    @Override
    protected int refreshSeconds() {
        // онлайн в мирах и приглашения меняются сами
        return tab == Tab.WORLDS || tab == Tab.TEAM ? 5 : 0;
    }

    @Override
    protected void build(Ui ui) {
        if (!admin() && tab.ordinal() > Tab.MYWORLDS.ordinal()) {
            tab = Tab.WORLDS;
        }
        if (tab == Tab.MYWORLDS && !com.ecoserv.extra.ExtraConfig.playerWorlds) {
            tab = Tab.WORLDS;
        }
        ui.title("Миры").icon(UiIcons.planet());
        ui.tab("Подмиры и миры", UiIcons.planet(), tab == Tab.WORLDS, () -> tab = Tab.WORLDS);
        int invites = WorldCrews.invitesFor(player.getUniqueID()).size();
        ui.tab("Команда" + (invites > 0 ? " &e(" + invites + ")" : ""), UiIcons.team(),
                tab == Tab.TEAM, () -> tab = Tab.TEAM);
        if (com.ecoserv.extra.ExtraConfig.playerWorlds) {
            ui.tab("Мои миры", UiIcons.create(), tab == Tab.MYWORLDS, () -> tab = Tab.MYWORLDS);
        }
        if (admin()) {
            ui.tab("Создать подмир", UiIcons.create(), tab == Tab.CREATE, () -> tab = Tab.CREATE);
            ui.tab("Порталы и зоны", UiIcons.portal(), tab == Tab.PORTALS, () -> tab = Tab.PORTALS);
            ui.tab("Основные миры", UiIcons.compass(), tab == Tab.MAIN, () -> tab = Tab.MAIN);
            ui.tab("Админ-панель", UiIcons.admin(), tab == Tab.ADMIN, () -> tab = Tab.ADMIN);
            ui.sideLabel("&8Админу");
            ui.sideButton("Настройки мода", UiIcons.gear(), Ui.GREY,
                    () -> com.ecoserv.config.ConfigScreen.open(player));
            ui.sideButton("Роли и теги", UiIcons.role(), Ui.GREY,
                    () -> com.ecoserv.role.RolesScreen.open(player));
        }
        ui.sideLabel("&8Ещё");
        ui.sideButton("Квестовая гонка", UiIcons.race(), Ui.YELLOW, () -> com.ecoserv.minigame.RaceScreen.open(player))
                .tip("&7Мини-игра: кто быстрее пройдёт цепочку квестов", "&8/race");
        ui.sideButton("Варпы", UiIcons.compass(), Ui.BLUE, () -> com.ecoserv.warp.WarpScreen.open(player))
                .tip("&7Точки быстрого перехода", "&8/warp");
        if (com.ecoserv.extra.ExtraConfig.claimsEnabled) {
            ui.sideButton("Приваты", UiIcons.map(), Ui.GREEN, () -> com.ecoserv.claim.ClaimScreen.open(player))
                    .tip("&7Занять территорию по чанкам", "&8/claim");
        }
        if (com.ecoserv.extra.ExtraConfig.gamesEnabled) {
            ui.sideButton("Мини-игры", UiIcons.race(), Ui.PURPLE, () -> com.ecoserv.games.GamesScreen.open(player))
                    .tip("&7Дезран, давление, погоня и другие", "&8/games");
        }
        ui.sideButton("Магазин", UiIcons.auction(), Ui.GREY,
                () -> com.ecoserv.shop.ShopScreen.open(player, com.ecoserv.shop.ShopScreen.Tab.AUCTION));
        ui.status("&7Баланс: &6" + Fmt.money(com.ecoserv.economy.EconomyManager.get(player.getUniqueID())));
        switch (tab) {
            case TEAM:
                ui.subtitle("твоя команда, локальные миры и гости");
                buildTeam(ui);
                break;
            case MYWORLDS:
                ui.subtitle("свои миры: создать, пригласить друзей");
                PlayerWorldsPanel.build(ui, this, draft);
                break;
            case CREATE:
                ui.subtitle("мастер нового подмира");
                CreatePanel.build(ui, this);
                break;
            case PORTALS:
                ui.subtitle("куда ведут порталы, что делают зоны");
                PortalsPanel.build(ui, this);
                break;
            case MAIN:
                ui.subtitle("правила, барьер и бездна обычных миров");
                MainWorldsPanel.build(ui, this);
                break;
            case ADMIN:
                ui.subtitle("все инструменты админа");
                AdminPanel.build(ui, this);
                break;
            default:
                ui.subtitle("куда можно пойти");
                buildWorlds(ui);
        }
    }

    // --- подмиры и обычные миры --------------------------------------------------------------

    private int playersIn(Subworld sw) {
        int n = 0;
        for (SubworldDim d : SubworldRegistry.dimsOf(sw.id)) {
            WorldServer w = DimensionManager.getWorld(d.dim);
            if (w != null) {
                n += w.playerEntities.size();
            }
        }
        return n;
    }

    private void buildWorlds(Ui ui) {
        boolean admin = admin();
        Subworld here = SubworldRegistry.subworldOf(player.dimension);
        if (here != null) {
            SubworldDim d = SubworldRegistry.dim(player.dimension);
            String where = "";
            if (d != null && d.isTemplate()) {
                where = " &d(шаблон)";
            } else if (d != null && d.isInstance()) {
                where = " &7— " + d.saveName + (SubworldTeams.isMember(player.getUniqueID(), d.owner) ? ""
                        : " &b(в гостях у " + SubworldTeams.ownerName(d.owner) + ")");
            }
            ui.row(UiIcons.compass(), "&fТы в подмире &e" + here.name + where)
                    .line("&7Выход ведёт в: &f" + WorldNames.displayName(SubworldTeleport.returnDim(player)))
                    .highlight(true)
                    .button("Вернуться", Ui.BLUE, () -> {
                        close();
                        SubworldTeleport.leave(player);
                    });
        } else {
            ui.row(UiIcons.planet(), "&fТы в мире &e" + WorldNames.displayName(player.dimension))
                    .line("&7Выбери подмир или мир ниже").highlight(true);
        }
        if (admin && SubworldRegistry.loadFailed()) {
            ui.note(Ui.RED, SubworldRegistry.BROKEN_NOTICE);
        }
        String blocked = admin ? SubworldManager.blockedNotice() : null;
        if (blocked != null) {
            ui.note(Ui.YELLOW, blocked);
        }
        List<Subworld> list = SubworldRegistry.all();
        list.sort((a, b) -> UiUtil.plain(a.name).compareToIgnoreCase(UiUtil.plain(b.name)));
        int shown = 0;
        if (EcoConfig.subworldsEnabled || admin) {
            ui.header("Подмиры", admin && !EcoConfig.subworldsEnabled ? "&cвыключены в настройках" : "");
            for (final Subworld sw : list) {
                if (!sw.enabled && !admin) {
                    continue;
                }
                // 1.9.0: личные миры игроков видят только хозяин, приглашённые и админы
                if (sw.playerOwned() && Subworld.ACCESS_PRIVATE.equals(sw.access) && !admin
                        && !sw.memberOf(player.getUniqueID())) {
                    continue;
                }
                shown++;
                subworldRow(ui, sw, admin);
            }
            if (shown == 0) {
                ui.note(Ui.BLUE, admin ? "Подмиров пока нет. Создай первый во вкладке «Создать подмир»."
                        : "Подмиров пока нет — их создают админы сервера.");
            }
        }
        buildPlainWorlds(ui, admin);
        ui.search("поиск по названию");
    }

    private void subworldRow(Ui ui, final Subworld sw, boolean admin) {
        final SubworldAccess.Result access = SubworldAccess.check(player, sw);
        Ui.Row row = ui.row(UiUtil.icon(sw.icon), (sw.enabled ? "&e" : "&7") + sw.name);
        if (!sw.description.isEmpty()) {
            row.line("&7" + sw.description);
        }
        StringBuilder info = new StringBuilder("&8" + sw.typeTitle());
        if (sw.perOwner()) {
            info.append(sw.perPlayer() ? " · свой мир у каждого" : " · свой мир у каждой команды");
        }
        int online = playersIn(sw);
        if (online > 0) {
            info.append(" · играют: ").append(online);
        }
        row.line(info.toString());
        if (!access.ok && access.reason != null && !access.canBuy) {
            row.line("&c" + UiUtil.plain(access.reason));
        }
        if (!sw.enabled) {
            row.chip("выключен", Ui.GREY);
        }
        if (sw.glitch) {
            row.chip("GLITCH", Ui.PURPLE);
        }
        if (sw.playerOwned()) {
            row.chip("мир " + sw.ownerName, Ui.BLUE);
        }
        if (sw.minigame) {
            row.chip("МИНИ-ИГРА", Ui.YELLOW);
            row.button("Гонка", Ui.YELLOW, () -> com.ecoserv.minigame.RaceScreen.open(player))
                    .tip("Арена «Квестовой гонки»: попасть сюда можно только игрой (/race)");
        } else if (sw.noMenuTeleport && !admin) {
            row.chip("вход не из меню", Ui.GREY);
            row.line("&8Сюда ведут порталы, а не меню");
        } else if (access.ok) {
            row.chip("открыт", Ui.GREEN);
        } else if (access.canBuy) {
            row.chip(Fmt.money(sw.price), Ui.YELLOW);
        } else {
            row.chip("закрыт", Ui.RED);
        }
        List<String> tip = new ArrayList<String>();
        tip.add("&e" + sw.name);
        if (!sw.description.isEmpty()) {
            tip.add("&7" + sw.description);
        }
        tip.add("&7Тип: &f" + sw.typeTitle());
        tip.add("&7Доступ: " + SubworldAccess.status(player, sw) + " &8(" + UiUtil.plain(sw.accessTitle()) + ")");
        if (sw.perOwner()) {
            tip.add("&7Сохранений: до &f" + sw.maxSaves + (sw.savePrice > 0 ? " &7(следующие по &6"
                    + Fmt.money(sw.savePrice) + "&7)" : ""));
            if (sw.borderStart > 0) {
                tip.add("&7Барьер: &f" + sw.borderStart + " &7блоков, растёт за задания");
            }
        }
        if (sw.ownQuests) {
            tip.add("&dСвои квесты и свой магазин");
        }
        row.tip(tip);
        boolean menuEntry = !sw.minigame && (!sw.noMenuTeleport || admin);
        if (menuEntry && access.ok) {
            row.button("Войти", Ui.GREEN, () -> {
                close();
                SubworldTeleport.enter(player, sw, false);
            });
            row.onClick(() -> {
                close();
                SubworldTeleport.enter(player, sw, false);
            });
        } else if (!sw.minigame && !access.ok && access.canBuy) {
            row.button("Купить", Ui.YELLOW, () -> buy(sw))
                    .confirm("Купить доступ в «" + sw.name + "&f» за &6" + Fmt.money(sw.price) + "&f?");
        }
        if (admin) {
            row.button("⚙", Ui.GREY, () -> new SubworldScreen(player, sw.id).open()).tip("Настройки подмира");
            row.button(sw.perOwner() ? "Шаблон" : "Вход", Ui.PURPLE, () -> {
                SubworldDim d = SubworldRegistry.find(sw.id, sw.perOwner() ? SubworldDim.TEMPLATE : SubworldDim.SHARED);
                if (d == null) {
                    throw new UiError("У подмира нет мира для входа.");
                }
                close();
                SubworldTeleport.teleportTo(player, d, false, null);
            }).tip(sw.perOwner() ? "Войти в шаблон (его правят админы, потом «Опубликовать»)" : "Войти как админ");
        }
    }

    private void buy(Subworld sw) {
        if (SubworldAccess.buy(player, sw)) {
            toast("Доступ в «" + sw.name + "» куплен!");
        } else if (!SubworldRegistry.isCurrent(sw)) {
            throw new UiError("Этого подмира уже нет.");
        } else {
            throw new UiError("Не хватает денег: нужно " + Fmt.money(sw.price));
        }
    }

    private void buildPlainWorlds(Ui ui, boolean admin) {
        if (!EcoConfig.worldsEnabled && !admin) {
            return;
        }
        List<Integer> dims = new ArrayList<Integer>();
        for (Integer dim : WorldNames.allDims()) {
            if (SubworldRegistry.isOurs(dim)) {
                continue;
            }
            boolean forPlayers = WorldNames.allowedForPlayers(dim)
                    || com.ecoserv.role.RoleManager.worldAllowed(player.getUniqueID(), dim);
            if (admin || forPlayers) {
                dims.add(dim);
            }
        }
        if (dims.isEmpty()) {
            return;
        }
        ui.header("Обычные миры", "&8/world <мир>");
        for (final Integer dim : dims) {
            final String name = WorldNames.nameOf(dim);
            boolean forPlayers = WorldNames.allowedForPlayers(dim)
                    || com.ecoserv.role.RoleManager.worldAllowed(player.getUniqueID(), dim);
            Ui.Row row = ui.row(MainWorldsPanel.dimIcon(dim), "&f" + name)
                    .line("&8измерение " + dim + (DimensionManager.getWorld(dim) != null ? " · загружен" : ""));
            if (player.dimension == dim) {
                row.chip("ты здесь", Ui.BLUE);
            }
            if (!forPlayers) {
                row.chip("только админы", Ui.GREY);
            }
            com.ecoserv.world.WorldSettings.Settings ws = com.ecoserv.world.WorldSettings.peek(dim);
            boolean noMenu = ws != null && ws.noMenuTeleport && !admin;
            com.ecoserv.subworld.SubworldAccess.Result access = com.ecoserv.world.WorldAccess.check(player, dim);
            if (noMenu) {
                row.chip("вход не из меню", Ui.GREY);
            } else if (!access.ok && access.canBuy) {
                row.chip(Fmt.money(ws.price), Ui.YELLOW);
                row.button("Купить", Ui.YELLOW, () -> {
                    if (!com.ecoserv.world.WorldAccess.buy(player, dim)) {
                        throw new UiError("Не хватает денег: нужно " + Fmt.money(ws.price));
                    }
                    toast("Доступ в мир «" + name + "» куплен!");
                }).confirm("Купить доступ в мир &f" + name + " &fза &6" + Fmt.money(ws.price) + "&f?");
            } else if (!access.ok) {
                row.chip("закрыт", Ui.RED);
                row.line("&c" + UiUtil.plain(access.reason == null ? "нет доступа" : access.reason));
            } else {
                row.button(player.dimension == dim ? "На спавн" : "Перейти", Ui.GREEN, () -> {
                    close();
                    player.getServer().getCommandManager().executeCommand(player, "world " + name);
                });
            }
            if (admin) {
                row.button("⚙", Ui.GREY, () -> new MainWorldScreen(player, dim).open()).tip("Настройки мира");
            }
        }
    }

    // --- команда и локальные миры ------------------------------------------------------------------

    private void buildTeam(Ui ui) {
        final UUID me = player.getUniqueID();
        long now = System.currentTimeMillis();
        for (final WorldCrews.Invite inv : WorldCrews.invitesFor(me)) {
            ui.row(new ItemStack(Items.PAPER), "&bПриглашение в команду от &f" + inv.leaderName)
                    .line("&7Будете играть в общих локальных мирах подмиров")
                    .line("&8осталось " + Fmt.duration(inv.until - now))
                    .highlight(true)
                    .button("Принять", Ui.GREEN, () -> {
                        String err = WorldCrews.accept(player, inv.leaderName);
                        if (err != null) {
                            throw new UiError(UiUtil.plain(err));
                        }
                        toast("Ты в команде " + inv.leaderName + "!");
                    })
                    .button("Отказаться", Ui.RED, () -> {
                        WorldCrews.deny(player, inv.leaderName);
                        toast("Приглашение отклонено.");
                    });
        }
        final String key = WorldCrews.manageKey(player);
        boolean canManage = WorldCrews.canManage(player, key);
        String ftbTeam = SubworldTeams.teamKey(me);
        ui.header("Твоя команда", key.startsWith("team:") ? "&8команда FTB" : "");
        if (WorldCrews.isExtraMember(me, key)) {
            String leader = WorldCrews.leaderName(key);
            ui.note(Ui.BLUE, "Ты в команде игрока &f" + (leader == null ? SubworldTeams.ownerName(key) : leader)
                    + "&f: его локальные миры — твои. Выйти можно кнопкой внизу.");
        } else if (ftbTeam != null && ftbTeam.equals(key)) {
            ui.note(Ui.BLUE, "Ты в команде FTB «" + SubworldTeams.ownerName(key) + "». Её участники и игроки, "
                    + "добавленные здесь, играют в одних локальных мирах.");
        } else {
            ui.note(Ui.BLUE, "Добавь друзей — и у вас будут общие локальные миры подмиров. Введи ник в поле ниже: "
                    + "игроку придёт приглашение. Гостей (посмотреть или помочь построить в одном мире) зовут "
                    + "в настройках мира — кнопка ⚙ у мира ниже.");
        }
        List<UUID> members = SubworldTeams.members(key);
        List<UUID> ftb = SubworldTeams.ftbMembers(key);
        for (final UUID m : members) {
            boolean online = PlayerUtil.online(m) != null;
            boolean extra = WorldCrews.isExtraMember(m, key);
            Ui.Row row = ui.row(new ItemStack(Items.SKULL, 1, 3), (online ? "&f" : "&7") + PlayerUtil.nameOf(m));
            if (m.equals(me)) {
                row.chip("это ты", Ui.BLUE);
            }
            if (SubworldTeams.isLeader(m, key)) {
                row.chip("глава", Ui.YELLOW);
            }
            if (ftb.contains(m)) {
                row.chip("FTB", Ui.PURPLE);
            } else if (extra) {
                row.chip("добавлен", Ui.GREY);
            }
            row.chip(online ? "в сети" : "не в сети", online ? Ui.GREEN : Ui.GREY);
            if (canManage && extra && !m.equals(me)) {
                row.button("Выгнать", Ui.RED, () -> {
                    String err = WorldCrews.kick(player, key, m);
                    if (err != null) {
                        throw new UiError(UiUtil.plain(err));
                    }
                    toast(PlayerUtil.nameOf(m) + " больше не в команде.");
                }).confirm("Убрать &f" + PlayerUtil.nameOf(m) + " &fиз команды? Он потеряет доступ к вашим локальным мирам.");
            }
        }
        if (canManage) {
            ui.field("Добавить в команду", "Ник игрока (он должен хотя бы раз зайти на сервер). Ему придёт "
                    + "приглашение — то же самое делает команда &f/world add <ник>&7.", "", value -> {
                String name = value.trim();
                if (name.isEmpty()) {
                    return;
                }
                UUID target = PlayerUtil.knownUuid(name);
                if (target == null) {
                    throw new UiError("Игрок " + name + " не найден (он должен хотя бы раз зайти на сервер).");
                }
                String err = WorldCrews.invite(player, target);
                if (err != null) {
                    throw new UiError(UiUtil.plain(err));
                }
                toast("Приглашение отправлено: " + PlayerUtil.nameOf(target));
            }).picker(Ui.PICK_PLAYER).placeholder("ник игрока").max(16);
        }
        if (WorldCrews.isExtraMember(me, key)) {
            ui.buttons().add("Выйти из команды", Ui.RED, () -> {
                String err = WorldCrews.leave(player);
                if (err != null) {
                    throw new UiError(UiUtil.plain(err));
                }
                toast("Ты вышел из команды.");
            }).confirm("Выйти из команды? Её локальные миры станут недоступны (свои прежние миры вернутся).");
        } else if (WorldCrews.isCrewLeader(me, key) && !WorldCrews.extraMembers(key).isEmpty()) {
            ui.buttons().add("Распустить команду", Ui.RED, () -> {
                WorldCrews.disband(player);
                toast("Команда распущена.");
            }).confirm("Распустить команду? Все добавленные игроки потеряют доступ к вашим локальным мирам.");
        }

        // локальные миры
        boolean anyLocal = false;
        ui.header("Локальные миры", "&8свой мир в подмире");
        for (final Subworld sw : SubworldRegistry.all()) {
            if (!sw.perOwner() || sw.minigame || (!sw.enabled && !admin())) {
                continue;
            }
            anyLocal = true;
            final String owner = SubworldTeams.ownerKey(player, sw);
            List<SubworldDim> saves = SubworldRegistry.instances(sw.id, owner);
            SubworldDim active = SubworldRegistry.activeInstance(sw.id, owner);
            for (final SubworldDim d : saves) {
                boolean isActive = active != null && active.dim == d.dim;
                Ui.Row row = ui.row(UiUtil.icon(sw.icon), "&e" + d.saveName)
                        .line("&7" + UiUtil.plain(sw.name) + (sw.perPlayer() ? " &5(личный)" : ""))
                        .line("&8" + (d.border > 0 ? "барьер " + d.border + (sw.borderMax > 0 ? "/" + sw.borderMax : "") + " · " : "")
                                + "гостей: " + d.guests.size() + " · создан " + Fmt.timestamp(d.created));
                if (isActive) {
                    row.chip("активный", Ui.GREEN);
                }
                if (player.dimension == d.dim) {
                    row.chip("ты здесь", Ui.BLUE);
                }
                row.tip("&e" + d.saveName, "&7Активный мир — тот, куда ведёт вход в подмир и порталы.",
                        "&7⚙ — переименовать, гости, удалить.");
                row.button("Войти", Ui.GREEN, () -> {
                    SubworldAccess.Result access = SubworldAccess.check(player, sw);
                    if (!access.ok && !(Perms.isAdmin(player))) {
                        throw new UiError(access.reason == null ? "Нет доступа к подмиру." : UiUtil.plain(access.reason));
                    }
                    SubworldRegistry.setActive(sw.id, owner, d.dim);
                    close();
                    SubworldTeleport.teleportTo(player, d, false, null);
                });
                row.button("⚙", Ui.GREY, () -> new SaveScreen(player, d.dim).open()).tip("Управление миром и гости");
            }
            int left = SubworldManager.savesLeft(sw, owner);
            if (left > 0) {
                final long price = SubworldManager.nextSavePrice(sw, owner);
                final SubworldAccess.Result access = SubworldAccess.check(player, sw);
                Ui.Row row = ui.row(new ItemStack(Items.MAP), "&a+ " + (saves.isEmpty() ? "Создать свой мир" : "Новое сохранение")
                        + " &7— " + sw.name)
                        .line("&7Свободно сохранений: &f" + left + (price > 0 ? " &7· цена &6" + Fmt.money(price) : " &7· бесплатно"));
                if (!access.ok) {
                    row.line("&cСначала открой доступ во вкладке «Подмиры и миры»");
                }
                Ui.Button create = row.button("Создать", Ui.GREEN, () -> {
                    if (!SubworldAccess.check(player, sw).ok && !Perms.isAdmin(player)) {
                        throw new UiError("Сначала открой доступ к подмиру «" + UiUtil.plain(sw.name) + "».");
                    }
                    String ownerKey = SubworldTeams.ownerKey(player, sw);
                    SubworldDim created = SubworldManager.createInstance(sw, ownerKey, SubworldTeams.ownerName(ownerKey), "", player);
                    if (created == null) {
                        throw new UiError("Не получилось создать мир (подробности в чате).");
                    }
                    close();
                    ChatUtil.send(player, "&aСоздан мир &f" + created.saveName + " &7(переименовать: меню миров → Команда → ⚙)");
                    SubworldTeleport.teleportTo(player, created, true, null);
                }).disabled(!access.ok && !admin());
                if (price > 0) {
                    create.confirm("Новое сохранение в «" + sw.name + "&f» стоит &6" + Fmt.money(price) + "&f. Создать?");
                }
            }
        }
        if (!anyLocal) {
            ui.text("&8Подмиров с локальными мирами пока нет.");
        }

        // в гостях
        String uuid = me.toString();
        boolean anyGuest = false;
        for (final SubworldDim d : SubworldRegistry.dims()) {
            if (!d.isInstance() || d.guest(uuid) == null) {
                continue;
            }
            Subworld sw = SubworldRegistry.get(d.subworld);
            if (sw == null) {
                continue;
            }
            if (!anyGuest) {
                ui.header("Тебя позвали в гости");
                anyGuest = true;
            }
            SubworldDim.Guest g = d.guest(uuid);
            ui.row(new ItemStack(Items.ENDER_PEARL), "&b" + SubworldTeams.ownerName(d.owner))
                    .line("&7" + UiUtil.plain(sw.name) + " — " + d.saveName)
                    .chip(g.build ? "можно строить" : "только смотреть", g.build ? Ui.GREEN : Ui.GREY)
                    .button("Войти", Ui.BLUE, () -> {
                        close();
                        SubworldTeleport.visit(player, d);
                    });
        }
        ui.note(Ui.GREY, "&7Команды: &f/world add <ник>&7 — в команду, &f/world invite <ник> [build]&7 — гостем "
                + "в мир, где стоишь, &f/world guests&7 — гости, &f/world team&7 — состав команды, &f/world help&7 — всё.");
    }

    // --- для панелей --------------------------------------------------------------------------------

    void setToast(String text) {
        toast(text);
    }

    void setError(String text) {
        error(text);
    }

    /** Список порталов и зон для вкладки «Порталы и зоны». */
    static List<Region> regions(String kind) {
        List<Region> list = RegionManager.all(kind);
        list.sort((a, b) -> a.name.compareTo(b.name));
        return list;
    }
}
