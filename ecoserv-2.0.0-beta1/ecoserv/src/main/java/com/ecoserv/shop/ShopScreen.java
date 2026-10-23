package com.ecoserv.shop;

import com.ecoserv.auction.AuctionManager;
import com.ecoserv.auction.Listing;
import com.ecoserv.backup.BackupManager;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.economy.MoneyRequestsScreen;
import com.ecoserv.economy.PlayerStats;
import com.ecoserv.economy.StatsStore;
import com.ecoserv.economy.Orders;
import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.extra.ExtraStore;
import com.ecoserv.extra.Ranks;
import com.ecoserv.extra.Salary;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.role.Role;
import com.ecoserv.role.RoleManager;
import com.ecoserv.server.ItemBlacklist;
import com.ecoserv.server.LagReport;
import com.ecoserv.server.PlaytimeRewards;
import com.ecoserv.server.RestartScheduler;
import com.ecoserv.trade.TradeManager;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Окно магазина в новом стиле (1.8.0): то же серверное окно, что и меню подмиров, вместо старого GuiShop.
 * Вкладки: аукцион, профиль, банк, история, игроки, а для админов — цены скупщика и сервер.
 * Все проверки идут на сервере в момент нажатия; клиент только рисует.
 */
public class ShopScreen extends UiScreen {

    public enum Tab {
        AUCTION, PROFILE, ORDERS, HISTORY, PLAYERS, PRICES, SERVER
    }

    /** Сколько лотов/позиций показываем за раз (дальше — поиском). */
    private static final int MAX_ROWS = 150;
    /** Покупка дороже этого спрашивает подтверждение. */
    private static final long CONFIRM_FROM = 1000L;

    private Tab tab;
    private boolean onlyMine;
    private int orderCount = 64;
    private long orderPrice;
    private boolean ordersMine;
    private long payAmount;
    private String announce = "";
    /** Редактируемая позиция прайса (копия) и признак «новая». */
    private ShopEntry draft;
    private boolean draftNew;

    public ShopScreen(EntityPlayerMP player, Tab tab) {
        super(player);
        this.tab = tab == null ? Tab.AUCTION : tab;
    }

    public static void open(EntityPlayerMP player, Tab tab) {
        // подсказки цен в инвентаре и баланс в HUD берутся из этих пакетов — как раньше при открытии GuiShop
        PacketHandler.sendShopList(player);
        PacketHandler.sendPlayerState(player);
        new ShopScreen(player, tab).open();
    }

    /** Открыть по имени вкладки (из пакета клиента или старого номера вкладки). */
    public static void open(EntityPlayerMP player, String tabName) {
        open(player, parseTab(tabName));
    }

    public static Tab parseTab(String name) {
        if (name == null || name.isEmpty()) {
            return Tab.AUCTION;
        }
        // старые номера вкладок GuiShop: 0 аукцион, 1 профиль, 2 игроки, 3 цены, 4 сервер
        switch (name) {
            case "0":
                return Tab.AUCTION;
            case "1":
                return Tab.PROFILE;
            case "2":
                return Tab.PLAYERS;
            case "3":
                return Tab.PRICES;
            case "4":
                return Tab.SERVER;
            default:
        }
        try {
            return Tab.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Tab.AUCTION;
        }
    }

    private boolean admin() {
        return Perms.isAdmin(player);
    }

    private boolean canEditPrices() {
        return admin() || Perms.has(player, Perms.SHOP_EDIT);
    }

    private UUID me() {
        return player.getUniqueID();
    }

    @Override
    protected int refreshSeconds() {
        switch (tab) {
            case AUCTION:
            case PLAYERS:
            case ORDERS:
                return 5;
            case SERVER:
                return 10;
            default:
                return 0;
        }
    }

    @Override
    protected void build(Ui ui) {
        if (tab == Tab.PRICES && !canEditPrices() || tab == Tab.SERVER && !admin()) {
            tab = Tab.AUCTION;
        }
        if (tab != Tab.PRICES) {
            draft = null;
        }
        ui.title("Магазин").icon(UiIcons.auction());
        ui.tab("Аукцион", UiIcons.auction(), tab == Tab.AUCTION, () -> tab = Tab.AUCTION);
        ui.tab("Профиль", UiIcons.profile(), tab == Tab.PROFILE, () -> tab = Tab.PROFILE);
        if (ExtraConfig.ordersEnabled) {
            ui.tab("Заказы", UiIcons.orders(), tab == Tab.ORDERS, () -> tab = Tab.ORDERS);
        }
        ui.tab("История", UiIcons.history(), tab == Tab.HISTORY, () -> tab = Tab.HISTORY);
        ui.tab("Игроки", UiIcons.players(), tab == Tab.PLAYERS, () -> tab = Tab.PLAYERS);
        if (canEditPrices()) {
            ui.tab("Цены скупщика", UiIcons.prices(), tab == Tab.PRICES, () -> tab = Tab.PRICES);
        }
        if (admin()) {
            ui.tab("Сервер", UiIcons.server(), tab == Tab.SERVER, () -> tab = Tab.SERVER);
        }
        ui.sideLabel("&8Ещё");
        ui.sideButton("Просьбы о деньгах", UiIcons.money(), Ui.GREY,
                () -> new MoneyRequestsScreen(player).open());
        ui.sideButton("Миры", UiIcons.planet(), Ui.GREY,
                () -> com.ecoserv.subworld.menu.WorldsScreen.open(player));
        ui.sideButton("Квестовая гонка", UiIcons.race(), Ui.YELLOW, () -> com.ecoserv.minigame.RaceScreen.open(player));

        long balance = EconomyManager.get(me());
        String status = "&7Баланс: &6" + Fmt.money(balance);
        int myOrders = Orders.openCount(me());
        if (myOrders > 0) {
            status += " &8· &7заказов: &f" + myOrders;
        }
        ui.status(status);

        switch (tab) {
            case PROFILE:
                ui.subtitle("твоя статистика и награды");
                buildProfile(ui);
                break;
            case ORDERS:
                ui.subtitle("купить вещи у игроков или сдать свои");
                buildOrders(ui);
                break;
            case HISTORY:
                ui.subtitle("последние операции с деньгами");
                buildHistory(ui);
                break;
            case PLAYERS:
                ui.subtitle("обмен и переводы");
                buildPlayers(ui);
                break;
            case PRICES:
                ui.subtitle("что принимает скупщик и почём");
                buildPrices(ui);
                break;
            case SERVER:
                ui.subtitle("инструменты админа");
                buildServer(ui);
                break;
            default:
                ui.subtitle("лоты игроков");
                buildAuction(ui);
        }
    }

    // --- аукцион ---------------------------------------------------------------------------------

    private void buildAuction(Ui ui) {
        if (!EcoConfig.auctionEnabled) {
            ui.note(Ui.RED, "Аукцион отключён на сервере.");
            return;
        }
        String own = me().toString();
        List<Listing> all = new ArrayList<Listing>(AuctionManager.all());
        int mine = 0;
        for (Listing l : all) {
            if (own.equals(l.seller)) {
                mine++;
            }
        }
        ui.sideLabel("&8Аукцион");
        ui.sideButton(onlyMine ? "Все лоты" : "Мои лоты (" + mine + ")", UiIcons.hourglass(),
                onlyMine ? Ui.BLUE : Ui.GREY, () -> onlyMine = !onlyMine);
        ui.search("поиск по предмету или продавцу");
        ui.header(onlyMine ? "Мои лоты" : "Лоты", Integer.toString(onlyMine ? mine : all.size()));
        ui.text("&8Выставить лот — через склад-продавец (блок). Купленное сразу попадает в инвентарь"
                + (EcoConfig.auctionTaxPercent > 0.0D
                ? ", с продавца берётся комиссия " + Fmt.price(EcoConfig.auctionTaxPercent) + "%." : "."));
        int shown = 0;
        boolean isAdmin = admin();
        long balance = EconomyManager.get(me());
        for (final Listing l : all) {
            boolean ownLot = own.equals(l.seller);
            if (onlyMine && !ownLot) {
                continue;
            }
            if (shown++ >= MAX_ROWS) {
                ui.note(Ui.YELLOW, "Показаны первые " + MAX_ROWS + " лотов — уточни поиском.");
                break;
            }
            ItemStack icon = l.stack();
            Ui.Row row = ui.row(icon, "&f" + l.displayName())
                    .line("&7x" + l.count + " &8· &7продаёт &e" + l.sellerName)
                    .chip(Fmt.price(l.pricePerItem) + "/шт", Ui.GREEN)
                    .highlight(ownLot);
            if (ownLot || isAdmin) {
                row.button("Снять", Ui.RED, () -> {
                    if (!AuctionManager.cancel(player, l.id, admin())) {
                        throw new UiError("Лот не найден или он не твой.");
                    }
                    toast("Лот снят, товар вернулся на склад или в почту.");
                }).confirm("Снять лот &f" + l.displayName() + " x" + l.count + "&f?");
            }
            if (!ownLot) {
                addBuy(row, l, 1, balance);
                if (l.count >= 16) {
                    addBuy(row, l, 16, balance);
                }
                int stack = Math.min(l.count, Math.max(1, icon.getMaxStackSize()));
                if (stack > 1 && stack != 16) {
                    addBuy(row, l, stack, balance);
                }
            }
        }
        if (shown == 0) {
            ui.text(onlyMine ? "&8У тебя нет лотов." : "&8Лотов пока нет.");
        }
    }

    private void addBuy(Ui.Row row, final Listing l, final int n, long balance) {
        long total = l.totalPrice(n);
        Ui.Button b = row.button("x" + n, total <= balance ? Ui.GREEN : Ui.GREY, () -> buy(l.id, n))
                .tip("&7Купить &f" + n + " шт", "&7Итого: &6" + Fmt.money(total));
        if (total >= CONFIRM_FROM || n > 1) {
            b.confirm("Купить &f" + l.displayName() + " x" + n + " &fза &6" + Fmt.money(total) + "&f?");
        }
    }

    private void buy(long id, int n) {
        AuctionManager.Result r = AuctionManager.buy(player, id, n);
        PacketHandler.sendPlayerState(player);
        switch (r.status) {
            case OK:
                toast("Куплено: " + r.itemName + " x" + r.count + " за " + Fmt.money(r.price));
                return;
            case NO_MONEY:
                throw new UiError("Не хватает денег: нужно " + Fmt.money(r.price) + ".");
            case OWN_LISTING:
                throw new UiError("Это твой собственный лот.");
            case DISABLED:
                throw new UiError("Аукцион отключён.");
            default:
                throw new UiError("Лот уже разобрали.");
        }
    }

    // --- профиль ---------------------------------------------------------------------------------

    private void buildProfile(Ui ui) {
        UUID id = me();
        PlayerStats stats = StatsStore.get(id);
        long now = System.currentTimeMillis();
        boolean dailyReady = stats.lastDailyDay != now / 86_400_000L;

        ui.header("Деньги");
        ui.row(UiIcons.money(), "&fБаланс: &6" + Fmt.money(EconomyManager.get(id)))
                .line("&7Заработано всего: &a" + Fmt.money(stats.totalEarned))
                .line("&7Потрачено всего: &c" + Fmt.money(stats.totalSpent));
        long pending = EconomyManager.pendingOf(id);
        if (pending > 0L) {
            ui.row(new ItemStack(Items.PAPER), "&fОжидает зачисления: &6" + Fmt.money(pending))
                    .line("&7Деньги, пришедшие, пока ты был не в сети")
                    .button("Забрать", Ui.GREEN, () -> {
                        EconomyManager.flushPending(player);
                        PacketHandler.sendPlayerState(player);
                        toast("Зачислено.");
                    });
        }

        ui.header("Время и награды");
        ui.row(UiIcons.hourglass(), "&fВ игре: &e" + Fmt.duration(stats.playtimeMillis))
                .line("&7Серия ежедневных наград: &f" + stats.dailyStreak)
                .chip(dailyReady ? "награда ждёт" : "получена", dailyReady ? Ui.GREEN : Ui.GREY);
        String next = Ranks.describeNext(id);
        if (next != null) {
            ui.row(UiIcons.role(), "&fСледующее звание: &e" + next);
        }
        long[] salary = Salary.best(id);
        if (salary != null) {
            ui.row(UiIcons.money(), "&fЗарплата: &6" + Fmt.money(salary[0]) + " &7каждые " + Fmt.minutes((int) salary[1]))
                    .line("&7За роль &f" + Salary.bestRole(id) + "&7, до выплаты &f"
                            + Fmt.duration(Math.max(0L, Salary.untilNext(id))))
                    .line("&8Выплачивается на баланс");
        }

        List<Role> roles = RoleManager.rolesOf(id);
        ui.header("Роли", Integer.toString(roles.size()));
        if (roles.isEmpty()) {
            ui.text("&8Ролей нет.");
        }
        for (Role r : roles) {
            ui.row(UiIcons.role(), r.title());
        }

        ui.header("Подсказки");
        ui.text("&7Продать вещи — терминал-скупщик. Выставить на аукцион — склад-продавец. "
                + "Обмен с игроком — вкладка «Игроки».");

        ui.footer(dailyReady ? "Забрать ежедневную награду" : "Награда уже получена",
                dailyReady ? Ui.GREEN : Ui.GREY, () -> {
                    long got = PlaytimeRewards.claimDaily(player);
                    if (got == -2L) {
                        throw new UiError("Ежедневные награды отключены.");
                    }
                    if (got == -1L) {
                        throw new UiError("Сегодня награда уже получена.");
                    }
                    toast(got > 0L ? "Ежедневная награда: " + Fmt.money(got) : "Награда получена.");
                }).disabled(!dailyReady);
    }

    // --- заказы (вместо вклада) --------------------------------------------------------------------

    private void buildOrders(Ui ui) {
        if (!ExtraConfig.ordersEnabled) {
            ui.note(Ui.RED, "Заказы отключены на сервере.");
            return;
        }
        final UUID id = me();
        List<Orders.Order> mine = Orders.of(id);
        ui.sideLabel("&8Заказы");
        ui.sideButton(ordersMine ? "Все заказы" : "Мои заказы (" + mine.size() + ")", UiIcons.history(),
                ordersMine ? Ui.BLUE : Ui.GREY, () -> ordersMine = !ordersMine);
        ui.search("поиск по предмету или заказчику");

        if (!ordersMine) {
            ui.header("Новый заказ");
            ItemStack held = player.getHeldItemMainhand();
            ui.text(held.isEmpty() ? "&7Возьми в руку предмет, который хочешь купить, — он станет образцом."
                    : "&7Образец: &f" + held.getDisplayName() + (held.getMaxDamage() > 0 ? " &8(износ не важен)" : ""));
            ui.number("Сколько штук", "От 1 до " + (64 * 36), orderCount, 1, 64 * 36, v -> orderCount = (int) v);
            ui.money("Цена за штуку", "Деньги за весь заказ спишутся сразу и будут лежать в заказе", orderPrice,
                    v -> orderPrice = Math.max(0L, v));
            long total = orderPrice * (long) Math.max(1, orderCount);
            ui.buttons().add(orderPrice > 0 ? "Заказать за " + Fmt.money(total) : "Заказать", Ui.GREEN, () -> {
                String err = Orders.create(player, player.getHeldItemMainhand(), orderCount, orderPrice);
                if (err != null) {
                    throw new UiError(err);
                }
                orderPrice = 0L;
                toast("Заказ создан. Деньги лежат в заказе, несданное вернётся через "
                        + ExtraConfig.ordersDays + " дн.");
            }).disabled(held.isEmpty() || orderPrice <= 0).confirm("Заказать &f" + orderCount + "× "
                    + (held.isEmpty() ? "?" : held.getDisplayName()) + " &fпо &6" + Fmt.money(orderPrice)
                    + " &f(всего &6" + Fmt.money(total) + "&f)?");
        }

        if (!mine.isEmpty()) {
            ui.header("Мои заказы", Integer.toString(mine.size()));
            for (final Orders.Order o : mine) {
                int ready = o.readyCount();
                Ui.Row row = ui.row(o.icon(), "&f" + o.itemName + " &7× " + o.want)
                        .line("&7Сдано: &f" + o.filled + "/" + o.want + " &8· &7по &6" + Fmt.money(o.price)
                                + " &8· &7в заказе &6" + Fmt.money(o.escrow))
                        .line(o.closed ? "&8закрыт" : "&8закроется через " + Fmt.duration(Math.max(0L, o.expires - System.currentTimeMillis())))
                        .highlight(ready > 0);
                if (ready > 0) {
                    row.chip("забрать " + ready, Ui.GREEN);
                    row.button("Забрать", Ui.GREEN, () -> {
                        int n = Orders.collect(player, o.id);
                        toast("Получено: " + n + "× " + o.itemName);
                    });
                }
                if (!o.closed) {
                    row.button("Закрыть", Ui.RED, () -> {
                        try {
                            Orders.cancel(player, o.id, false);
                        } catch (IllegalStateException e) {
                            throw new UiError(e.getMessage());
                        }
                        PacketHandler.sendPlayerState(player);
                        toast("Заказ закрыт, залог вернулся.");
                    }).confirm("Закрыть заказ? Несданное — &6" + Fmt.money(o.escrow) + " &fвернётся на баланс.");
                }
            }
        }
        if (ordersMine) {
            return;
        }

        List<Orders.Order> all = Orders.open();
        ui.header("Заказы игроков", Integer.toString(all.size()));
        ui.text("&8Сдай вещи из инвентаря — деньги придут сразу. Новых денег заказы не создают: платит заказчик.");
        int shown = 0;
        boolean admin = admin();
        for (final Orders.Order o : all) {
            if (o.owner.equals(id.toString())) {
                continue;
            }
            if (shown++ >= MAX_ROWS) {
                ui.note(Ui.YELLOW, "Показаны первые " + MAX_ROWS + " заказов — уточни поиском.");
                break;
            }
            int have = Orders.countIn(player, o);
            int can = Math.min(have, o.left());
            Ui.Row row = ui.row(o.icon(), "&f" + o.itemName + " &7— нужно ещё &f" + o.left())
                    .line("&7Заказчик: &e" + o.ownerName + " &8· &7у тебя: &f" + have)
                    .chip(Fmt.money(o.price) + "/шт", Ui.GREEN)
                    .highlight(can > 0);
            row.button(can > 0 ? "Сдать " + can + " за " + Fmt.money(o.price * can) : "Сдать", can > 0 ? Ui.GREEN : Ui.GREY,
                    () -> {
                        try {
                            long[] r = Orders.fulfil(player, o.id, Integer.MAX_VALUE);
                            toast("Сдано " + r[0] + "× " + o.itemName + ", получено " + Fmt.money(r[1]));
                        } catch (IllegalStateException e) {
                            throw new UiError(e.getMessage());
                        }
                    }).disabled(can <= 0);
            if (admin) {
                row.button("Снять", Ui.RED, () -> {
                    try {
                        Orders.cancel(player, o.id, true);
                    } catch (IllegalStateException e) {
                        throw new UiError(e.getMessage());
                    }
                    toast("Заказ закрыт, залог вернулся заказчику.");
                }).confirm("Закрыть чужой заказ (залог вернётся заказчику)?");
            }
        }
        if (shown == 0) {
            ui.text("&8Чужих заказов пока нет.");
        }
    }

    // --- история ---------------------------------------------------------------------------------

    private void buildHistory(Ui ui) {
        ExtraStore.Data d = ExtraStore.peek(me());
        List<ExtraStore.Tx> list = new ArrayList<ExtraStore.Tx>();
        if (d != null) {
            synchronized (d) {
                list.addAll(d.history);
            }
        }
        ui.search("поиск по подписи");
        ui.header("Операции", Integer.toString(list.size()));
        if (list.isEmpty()) {
            ui.text("&8Операций пока нет.");
            return;
        }
        long in = 0L;
        long out = 0L;
        for (ExtraStore.Tx t : list) {
            if (t.amount > 0L) {
                in += t.amount;
            } else {
                out -= t.amount;
            }
        }
        ui.text("&7Пришло: &a+" + Fmt.money(in) + " &8· &7ушло: &c-" + Fmt.money(out));
        for (int i = list.size() - 1; i >= 0; i--) {
            ExtraStore.Tx t = list.get(i);
            boolean plus = t.amount > 0L;
            ui.row(new ItemStack(plus ? Items.GOLD_NUGGET : Items.REDSTONE),
                    (plus ? "&a+" : "&c-") + Fmt.money(Math.abs(t.amount)))
                    .line("&f" + (t.note == null || t.note.isEmpty() ? "без подписи" : t.note))
                    .line("&8" + Fmt.timestamp(t.time));
        }
    }

    // --- игроки ----------------------------------------------------------------------------------

    private void buildPlayers(Ui ui) {
        List<EntityPlayerMP> online = PlayerUtil.allOnline();
        boolean canTrade = EcoConfig.tradeEnabled && Perms.has(player, Perms.TRADE);
        ui.search("поиск по нику");
        if (EcoConfig.allowPayCommand) {
            ui.money("Сумма перевода", "Минимум " + Fmt.money(EcoConfig.payMinAmount)
                            + (EcoConfig.payTaxPercent > 0.0D ? ", комиссия " + Fmt.price(EcoConfig.payTaxPercent) + "%" : ""),
                    payAmount, v -> payAmount = Math.max(0L, v));
        }
        ui.header("В сети", Integer.toString(Math.max(0, online.size() - 1)));
        int shown = 0;
        for (final EntityPlayerMP p : online) {
            if (p == player) {
                continue;
            }
            shown++;
            final String name = p.getName();
            final UUID uuid = p.getUniqueID();
            List<Role> roles = RoleManager.rolesOf(uuid);
            StringBuilder rs = new StringBuilder();
            for (Role r : roles) {
                if (rs.length() > 0) {
                    rs.append("&7, ");
                }
                rs.append(r.title());
            }
            Ui.Row row = ui.row(new ItemStack(Items.SKULL, 1, 3), "&f" + name)
                    .line(rs.length() > 0 ? "&7" + rs : "&8без ролей");
            if (canTrade) {
                row.button("Обмен", Ui.BLUE, () -> {
                    EntityPlayerMP target = PlayerUtil.online(uuid);
                    if (target == null) {
                        throw new UiError(name + " уже не в сети.");
                    }
                    TradeManager.sendRequest(player, target);
                    toast("Запрос на обмен отправлен (если игрок рядом) — смотри чат.");
                });
            }
            if (EcoConfig.allowPayCommand) {
                row.button("Перевести", Ui.GREEN, () -> pay(uuid, name))
                        .disabled(payAmount <= 0L)
                        .confirm("Перевести &e" + name + " &6" + Fmt.money(payAmount) + "&f?");
            }
        }
        if (shown == 0) {
            ui.text("&8Кроме тебя никого нет.");
        }
        List<Map.Entry<UUID, Long>> top = EconomyManager.top(5);
        if (!top.isEmpty()) {
            ui.header("Самые богатые");
            int place = 1;
            for (Map.Entry<UUID, Long> e : top) {
                ui.row(new ItemStack(place == 1 ? Items.GOLD_INGOT : Items.GOLD_NUGGET),
                        "&e" + place++ + ". &f" + PlayerUtil.nameOf(e.getKey()))
                        .chip(Fmt.money(e.getValue()), Ui.YELLOW)
                        .highlight(e.getKey().equals(me()));
            }
        }
    }

    private void pay(UUID to, String name) {
        if (!EcoConfig.allowPayCommand) {
            throw new UiError("Переводы отключены на сервере.");
        }
        long amount = payAmount;
        if (amount <= 0L) {
            throw new UiError("Впиши сумму перевода.");
        }
        if (amount < EcoConfig.payMinAmount) {
            throw new UiError("Минимальная сумма перевода: " + Fmt.money(EcoConfig.payMinAmount));
        }
        long received = EconomyManager.transfer(me(), to, amount);
        if (received < 0L) {
            throw new UiError("Не хватает денег: нужно " + Fmt.money(amount));
        }
        EntityPlayerMP target = PlayerUtil.online(to);
        if (target != null) {
            ChatUtil.send(target, "&aПолучено от &f" + player.getName() + "&a: &e" + Fmt.money(received));
            PacketHandler.sendPlayerState(target);
        }
        PacketHandler.sendPlayerState(player);
        payAmount = 0L;
        toast("Переведено " + name + ": " + Fmt.money(received)
                + (received < amount ? " (комиссия " + Fmt.money(amount - received) + ")" : ""));
    }

    // --- цены скупщика ------------------------------------------------------------------------------

    private void buildPrices(Ui ui) {
        final ShopList shop = ShopList.forPlayer(player);
        if (draft != null) {
            buildPriceEdit(ui, shop);
            return;
        }
        ui.search("поиск по предмету или категории");
        if (!shop.isGlobal()) {
            ui.note(Ui.PURPLE, "Прайс этого подмира: " + shop.scopeName());
        }
        Ui.Buttons b = ui.buttons();
        b.add("Добавить из руки", Ui.GREEN, () -> {
            ItemStack held = player.getHeldItemMainhand();
            if (held.isEmpty()) {
                throw new UiError("Возьми предмет в руку.");
            }
            String key = ItemKey.of(held, false, true).serialize();
            ShopEntry existing = shop.byKey(key);
            if (existing != null) {
                draft = existing.copy();
                draftNew = false;
            } else {
                draft = ShopEntry.batch(key, Math.max(1, held.getMaxStackSize()), 0.0D);
                draftNew = true;
            }
        }).tip("&7Предмет в главной руке — в прайс", "&8Если он уже есть, откроется его правка");
        b.add("Сортировать", Ui.GREY, () -> {
            shop.sortByName();
            saveShop(shop);
            toast("Прайс-лист отсортирован.");
        });
        List<ShopEntry> list = shop.entries();
        ui.header("Позиции", Integer.toString(list.size()));
        if (list.isEmpty()) {
            ui.text("&8Прайс пуст.");
        }
        int shown = 0;
        for (final ShopEntry e : list) {
            if (shown++ >= MAX_ROWS) {
                ui.note(Ui.YELLOW, "Показаны первые " + MAX_ROWS + " позиций — уточни поиском.");
                break;
            }
            Ui.Row row = ui.row(e.displayStack(), "&f" + e.displayName())
                    .line(e.category == null || e.category.isEmpty() ? "" : "&8" + e.category)
                    .line(e.canSell()
                            ? "&7Сдают &f" + e.batchSize() + " шт &7→ &6" + Fmt.price(e.getEffectiveSellPrice())
                            : "&cне принимается")
                    .dim(!e.enabled)
                    .onClick(() -> {
                        draft = e.copy();
                        draftNew = false;
                    });
            row.button("Изменить", Ui.BLUE, () -> {
                draft = e.copy();
                draftNew = false;
            });
        }
    }

    private void buildPriceEdit(Ui ui, final ShopList shop) {
        final ShopEntry d = draft;
        ui.back(() -> draft = null);
        ui.header(draftNew ? "Новая позиция" : "Правка позиции");
        ui.row(d.displayStack(), "&f" + d.displayName()).line("&8" + d.key);
        final int maxBatch = com.ecoserv.tile.TileMarket.SIZE * 64;
        ui.number("Приём, шт", "Сколько штук сдают за раз (1…" + maxBatch + ")", d.batch, 1, maxBatch,
                v -> d.batch = (int) v);
        ui.field("Цена за приём", "Сколько платит скупщик за всю партию; 0 — не принимается",
                Fmt.price(d.sellPrice), v -> d.sellPrice = parsePrice(v)).placeholder("0");
        ui.toggle("Принимается", "Выключенная позиция остаётся в прайсе, но скупщик её не берёт",
                d.enabled, v -> d.enabled = v);
        ui.field("Категория", "Группа в прайсе, необязательно", d.category == null ? "" : d.category,
                v -> d.category = v.trim()).max(32);
        if (d.sellPrice > 0.0D) {
            ui.text("&7Сдают &f" + Math.max(1, d.batch) + " шт &7→ получают &6" + Fmt.price(d.sellPrice)
                    + " &8(≈" + Fmt.price(Math.round(d.sellPrice / Math.max(1, d.batch) * 100.0D) / 100.0D)
                    + " за штуку; меньше партии не продаётся)");
        }
        ui.footer("Сохранить", Ui.GREEN, () -> {
            d.batch = Math.max(1, Math.min(maxBatch, d.batch));
            if (Double.isNaN(d.sellPrice) || Double.isInfinite(d.sellPrice) || d.sellPrice < 0.0D) {
                d.sellPrice = 0.0D;
            }
            if (d.category == null) {
                d.category = "";
            }
            if (d.requiredQuest == null) {
                d.requiredQuest = "";
            }
            if (!d.itemKey().isValid()) {
                throw new UiError("Некорректный предмет.");
            }
            shop.addOrReplace(d);
            saveShop(shop);
            draft = null;
            toast("Сохранено: " + UiUtil.plain(d.displayName()));
        });
        if (!draftNew) {
            ui.footer("Удалить", Ui.RED, () -> {
                if (!shop.remove(d.key)) {
                    throw new UiError("Позиция уже удалена.");
                }
                saveShop(shop);
                draft = null;
                toast("Позиция удалена.");
            }).confirm("Удалить &f" + d.displayName() + " &fиз прайса?");
        }
        ui.footer("Отмена", Ui.GREY, () -> draft = null);
    }

    private static double parsePrice(String v) {
        String s = v == null ? "" : v.trim().replace(" ", "").replace(',', '.');
        if (s.isEmpty()) {
            return 0.0D;
        }
        Long m = Ui.parseMoney(s);
        if (m != null) {
            return m;
        }
        try {
            double d = Double.parseDouble(s);
            if (d < 0.0D || Double.isNaN(d) || Double.isInfinite(d)) {
                throw new UiError("Цена не может быть отрицательной.");
            }
            return Math.round(d * 100.0D) / 100.0D;
        } catch (NumberFormatException e) {
            throw new UiError("«" + v + "»: нужна цена, например 64 или 1.5k.");
        }
    }

    private void saveShop(ShopList shop) {
        if (!canEditPrices()) {
            throw new UiError("Нет прав на редактирование магазина.");
        }
        shop.save();
        PacketHandler.sendShopListToScope(shop);
    }

    // --- сервер ----------------------------------------------------------------------------------

    private void buildServer(Ui ui) {
        ui.header("Состояние");
        double tps = LagReport.tps();
        ui.row(new ItemStack(Items.CLOCK), "&fTPS: " + (tps >= 18 ? "&a" : tps >= 12 ? "&e" : "&c")
                + String.format(Locale.ROOT, "%.1f", tps))
                .line("&7Тик: &f" + String.format(Locale.ROOT, "%.1f", LagReport.meanTickMillis()) + " мс")
                .line("&7Игроков в сети: &f" + PlayerUtil.allOnline().size());
        long now = System.currentTimeMillis();
        long nextBackup = BackupManager.nextBackupTime();
        ui.row(new ItemStack(Blocks.CHEST), "&fБэкапы: &e" + BackupManager.listBackups().size()
                + " &7(" + Fmt.bytes(BackupManager.totalSize()) + ")")
                .line(BackupManager.isRunning() ? "&eИдёт бэкап…"
                        : nextBackup > now ? "&7Следующий через &f" + Fmt.duration(nextBackup - now) : "&8Авто-бэкап выключен")
                .line(BackupManager.lastError() == null || BackupManager.lastError().isEmpty() ? ""
                        : "&cОшибка: " + BackupManager.lastError());
        long nextRestart = RestartScheduler.nextRestart();
        ui.row(new ItemStack(Items.COMPARATOR), "&fРестарт")
                .line(nextRestart > now ? "&7Через &f" + Fmt.duration(nextRestart - now) : "&8Не запланирован");

        ui.header("Действия");
        Ui.Buttons b1 = ui.buttons();
        b1.add("Бэкап сейчас", Ui.GREEN, () -> {
            BackupManager.scheduleWithWarning(player);
            toast("Бэкап запланирован.");
        }).disabled(BackupManager.isRunning());
        b1.add("Удалить старые", Ui.GREY, () -> toast("Удалено бэкапов: " + BackupManager.prune()))
                .confirm("Удалить старые бэкапы по правилам хранения?");
        Ui.Buttons b2 = ui.buttons();
        b2.add("Рестарт 5 мин", Ui.YELLOW, () -> {
            RestartScheduler.startManualCountdown(5, player);
            toast("Рестарт через 5 минут.");
        }).confirm("Перезапустить сервер через 5 минут?");
        b2.add("15 мин", Ui.YELLOW, () -> {
            RestartScheduler.startManualCountdown(15, player);
            toast("Рестарт через 15 минут.");
        }).confirm("Перезапустить сервер через 15 минут?");
        b2.add("Отменить", Ui.GREY, () -> {
            RestartScheduler.cancelCountdown(player);
            toast("Отсчёт отменён.");
        });
        Ui.Buttons b3 = ui.buttons();
        b3.add("Перезагрузить конфиг", Ui.BLUE, () -> {
            com.ecoserv.server.ServerServices.reloadAll();
            toast("Конфиг, прайс, чёрный список и запреты перезагружены.");
        });
        b3.add("Сохранить данные", Ui.BLUE, () -> {
            com.ecoserv.server.ServerServices.saveAll(true);
            toast("Данные сохранены.");
        });
        Ui.Buttons b4 = ui.buttons();
        b4.add("Отчёт о лагах (в чат)", Ui.GREY, () -> {
            for (String line : LagReport.build(10)) {
                ChatUtil.sendRaw(player, line);
            }
            toast("Отчёт отправлен в чат.");
        });
        b4.add("В чёрный список из руки", Ui.RED, () -> {
            ItemStack held = player.getHeldItemMainhand();
            if (held.isEmpty()) {
                throw new UiError("Возьми предмет в руку.");
            }
            String key = ItemKey.of(held, false, true).serialize();
            ItemBlacklist.add(key);
            toast("В чёрном списке: " + UiUtil.plain(held.getDisplayName()));
        }).confirm("Запретить предмет в руке на рынке и в магазине?");

        ui.header("Настройки рынка");
        ui.field("Налог сервера с продажи, %", "Сколько процентов скупщик удерживает (сток денег)",
                Fmt.price(EcoConfig.marketTaxPercent), v -> {
                    double p = parsePercent(v);
                    if (!EcoConfig.set("market", "tax_percent", String.valueOf(p))) {
                        throw new UiError("Не удалось применить значение.");
                    }
                    PacketHandler.sendPlayerStateToAll();
                    toast("Налог: " + Fmt.price(p) + "%");
                });

        ui.header("Объявление");
        ui.field("Текст", "Уйдёт в чат всем игрокам; цвета через &", announce, v -> announce = v).max(200);
        ui.buttons().add("Отправить всем", Ui.PURPLE, () -> {
            if (announce.trim().isEmpty()) {
                throw new UiError("Впиши текст объявления.");
            }
            ChatUtil.broadcast(announce);
            announce = "";
            toast("Объявление отправлено.");
        });
    }

    private static double parsePercent(String v) {
        try {
            double d = Double.parseDouble(v.trim().replace(',', '.').replace("%", ""));
            if (d < 0.0D || d > 100.0D || Double.isNaN(d)) {
                throw new UiError("Процент — от 0 до 100.");
            }
            return d;
        } catch (NumberFormatException e) {
            throw new UiError("«" + v + "»: нужно число, например 5 или 2.5.");
        }
    }
}
