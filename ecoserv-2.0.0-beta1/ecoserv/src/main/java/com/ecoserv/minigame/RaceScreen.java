package com.ecoserv.minigame;

import com.ecoserv.economy.EconomyManager;
import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Окно «Квестовой гонки»: поиск и создание лобби (с фильтрами и паролями), само лобби (игроки, ставки, голоса,
 * «Готов» / «Играть», настройки создателя), ход игры (шаги, сдача, таблица) и правила с выплатами.
 */
public class RaceScreen extends UiScreen {

    private enum Tab {
        LOBBIES, MINE, GAME, RULES
    }

    private Tab tab;
    // фильтры поиска
    private String fMode = "";
    private int fDiff;
    private String fPass = "";
    private boolean fFree = true;
    private boolean fAfford;
    /** Лобби с паролем, в которое собираемся войти. */
    private int joining;
    private String password = "";

    public RaceScreen(EntityPlayerMP player) {
        super(player);
        Lobby l = RaceManager.of(player.getUniqueID());
        tab = l == null ? Tab.LOBBIES : l.state == Lobby.State.WAITING ? Tab.MINE : Tab.GAME;
    }

    public static void open(EntityPlayerMP player) {
        new RaceScreen(player).open();
    }

    @Override
    protected int refreshSeconds() {
        return tab == Tab.RULES ? 0 : 2;
    }

    private static void run(Runnable r) {
        try {
            r.run();
        } catch (IllegalStateException e) {
            throw new UiError(e.getMessage());
        }
    }

    @Override
    protected void build(Ui ui) {
        Lobby mine = RaceManager.of(player.getUniqueID());
        if (mine == null && (tab == Tab.MINE || tab == Tab.GAME)) {
            tab = Tab.LOBBIES;
        }
        if (mine != null && tab == Tab.MINE && mine.state != Lobby.State.WAITING) {
            tab = Tab.GAME;
        }
        if (mine != null && tab == Tab.GAME && mine.state == Lobby.State.WAITING) {
            tab = Tab.MINE;
        }
        ui.title("Квестовая гонка").icon(UiIcons.race());
        ui.tab("Найти лобби", UiIcons.players(), tab == Tab.LOBBIES, () -> tab = Tab.LOBBIES);
        if (mine != null) {
            if (mine.state == Lobby.State.WAITING) {
                ui.tab("Моё лобби &7(" + mine.members.size() + ")", UiIcons.team(), tab == Tab.MINE, () -> tab = Tab.MINE);
            } else {
                ui.tab("&6Игра идёт", UiIcons.hourglass(), tab == Tab.GAME, () -> tab = Tab.GAME);
            }
        }
        ui.tab("Правила и выплаты", UiIcons.book(), tab == Tab.RULES, () -> tab = Tab.RULES);
        ui.sideLabel("&8Ещё");
        ui.sideButton("Магазин", UiIcons.auction(), Ui.GREY,
                () -> com.ecoserv.shop.ShopScreen.open(player, com.ecoserv.shop.ShopScreen.Tab.AUCTION));
        ui.sideButton("Миры", UiIcons.planet(), Ui.GREY, () -> com.ecoserv.subworld.menu.WorldsScreen.open(player));
        ui.status("&7Баланс: &6" + Fmt.money(EconomyManager.get(player.getUniqueID())));
        if (!ExtraConfig.minigameEnabled) {
            ui.note(Ui.RED, "Мини-игра отключена на сервере.");
            return;
        }
        switch (tab) {
            case MINE:
                ui.subtitle("игроки, ставки и настройки");
                buildLobby(ui, mine);
                break;
            case GAME:
                ui.subtitle("кто быстрее пройдёт цепочку");
                buildGame(ui, mine);
                break;
            case RULES:
                ui.subtitle("как играть и сколько можно выиграть");
                buildRules(ui);
                break;
            default:
                ui.subtitle("найди игру или создай свою");
                buildBrowse(ui, mine);
        }
    }

    // --- поиск --------------------------------------------------------------------------------------

    private void buildBrowse(Ui ui, Lobby mine) {
        if (RaceArena.arenas().isEmpty()) {
            ui.note(Ui.YELLOW, "На сервере нет арены. Админ: создай подмир со «Своим миром у каждой команды», "
                    + "построй арену в шаблоне, опубликуй, включи подмир и поставь пометку «МИНИ-ИГРА».");
        }
        if (joining != 0) {
            final Lobby l = RaceManager.byId(joining);
            if (l == null) {
                joining = 0;
            } else {
                ui.header("Вход в «" + l.name + "»", "&eпароль");
                ui.field("Пароль", "Его знает создатель лобби", password, v -> password = v).max(32).onEnter(() -> join(l));
                Ui.Buttons b = ui.buttons();
                b.add("Войти", Ui.GREEN, () -> join(l));
                b.add("Отмена", Ui.GREY, () -> joining = 0);
                return;
            }
        }
        if (mine == null) {
            ui.buttons().add("Создать лобби", Ui.GREEN, () -> run(() -> {
                RaceManager.create(player);
                tab = Tab.MINE;
            }));
        }
        ui.header("Фильтры");
        ui.select("Режим", "", fMode, v -> fMode = v)
                .option("", "Любой", "", new ItemStack(Items.MAP))
                .option("NORMAL", "Обычный", "кто первый — тот и победил", UiIcons.race())
                .option("TIMED", "На время", "таймер; не успел — места по прогрессу", UiIcons.hourglass());
        ui.select("Сложность", "", Integer.toString(fDiff), v -> fDiff = parseInt(v))
                .option("0", "Любая", "")
                .option("1", "★ 1", "один шаг")
                .option("2", "★★ 2", "")
                .option("3", "★★★ 3", "")
                .option("4", "★★★★ 4", "")
                .option("5", "★★★★★ 5", "длинная цепочка");
        ui.select("Пароль", "", fPass, v -> fPass = v)
                .option("", "Все лобби", "")
                .option("open", "Только открытые", "без пароля")
                .option("locked", "Только с паролем", "");
        ui.toggle("Только где есть места", "", fFree, on -> fFree = on);
        ui.toggle("Только по карману", "Минимальная ставка не больше моего баланса", fAfford, on -> fAfford = on);
        ui.search("поиск по названию или создателю");
        List<Lobby> all = RaceManager.lobbies();
        long balance = EconomyManager.get(player.getUniqueID());
        int shown = 0;
        ui.header("Лобби", Integer.toString(all.size()));
        for (final Lobby l : all) {
            if (!fMode.isEmpty() && !l.mode.name().equals(fMode)) {
                continue;
            }
            if (fDiff > 0 && l.effectiveDifficulty() != fDiff) {
                continue;
            }
            if ("open".equals(fPass) && l.hasPassword() || "locked".equals(fPass) && !l.hasPassword()) {
                continue;
            }
            if (fFree && (l.full() || l.state != Lobby.State.WAITING)) {
                continue;
            }
            if (fAfford && l.minStake > balance) {
                continue;
            }
            shown++;
            String hostName = l.members.isEmpty() ? "?" : nameOf(l, l.host);
            Ui.Row row = ui.row(l.hasPassword() ? UiIcons.key() : UiIcons.race(), "&f" + l.name)
                    .line("&7Создатель: &e" + hostName + " &8· &7" + l.mode.title
                            + (l.mode == Lobby.Mode.TIMED ? (l.timeVote ? " (время — голосованием)" : " (время — авто)") : ""))
                    .line("&7Сложность: &f" + stars(l.effectiveDifficulty()) + (l.difficulty == 0 ? " &8(голосование)" : "")
                            + " &8· &7ставка &6" + Fmt.money(l.minStake) + "–" + Fmt.money(l.maxStake))
                    .chip(l.members.size() + "/" + l.maxPlayers, l.full() ? Ui.RED : Ui.BLUE)
                    .highlight(mine == l);
            if (l.hasPassword()) {
                row.chip("пароль", Ui.YELLOW);
            }
            if (l.state != Lobby.State.WAITING) {
                row.chip("идёт игра", Ui.PURPLE);
            } else if (mine == null) {
                row.button("Войти", Ui.GREEN, () -> {
                    if (l.hasPassword()) {
                        joining = l.id;
                        password = "";
                    } else {
                        join(l);
                    }
                }).disabled(l.full());
            }
        }
        if (shown == 0) {
            ui.text("&8Подходящих лобби нет — создай своё.");
        }
    }

    private void join(Lobby l) {
        run(() -> RaceManager.join(player, l.id, password));
        joining = 0;
        password = "";
        tab = Tab.MINE;
        toast("Ты в лобби «" + UiUtil.plain(l.name) + "».");
    }

    private static int parseInt(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String stars(int d) {
        StringBuilder sb = new StringBuilder("&6");
        for (int i = 1; i <= 5; i++) {
            sb.append(i <= d ? "★" : "&8☆");
        }
        return sb.toString();
    }

    private static String nameOf(Lobby l, java.util.UUID id) {
        Lobby.Member m = l.member(id);
        return m == null ? "?" : m.name;
    }

    // --- лобби --------------------------------------------------------------------------------------

    private void buildLobby(Ui ui, final Lobby l) {
        final boolean host = l.isHost(player.getUniqueID());
        final Lobby.Member me = l.member(player.getUniqueID());
        if (me == null) {
            return;
        }
        ui.row(l.hasPassword() ? UiIcons.key() : UiIcons.race(), "&6" + l.name)
                .line("&7" + l.mode.title + " · сложность " + stars(l.effectiveDifficulty())
                        + (l.difficulty == 0 ? " &8(по голосам)" : ""))
                .line("&7Ставка: &6" + Fmt.money(l.minStake) + "–" + Fmt.money(l.maxStake)
                        + " &8· &7банк сейчас: &6" + Fmt.money(pot(l)))
                .chip(l.members.size() + "/" + l.maxPlayers, Ui.BLUE)
                .highlight(true);

        // превью задания: только оценка, шаги откроются на старте
        RaceTask t = l.task;
        ui.header("Задание", t == null ? "" : stars(l.taskDifficulty));
        if (t == null) {
            ui.note(Ui.YELLOW, "Задание не подобрано — попробуй другую сложность.");
        } else {
            ui.row(new ItemStack(Items.WRITABLE_BOOK), "&f" + (t.source.equals("quest") ? "Цепочка квестов" : "Цепочка крафта")
                    + " &7— " + t.steps.size() + " шаг.")
                    .line("&7Сырьё: &f" + t.estimate.rawTotal() + " шт &8(" + t.estimate.rawSummary(4) + ")")
                    .line("&7Плавок: &f" + t.estimate.smelts + " &8· &7крафтов: &f" + t.estimate.crafts
                            + " &8· &7оценка: &f" + t.estimate.timeText() + (t.estimate.partial ? " &8(примерно)" : ""))
                    .tip("&7Что именно делать — откроется на старте.",
                            "&7Оценка — по рецептам: добыча, плавка (2 печи) и крафт.");
            if (host) {
                ui.buttons().add("Другое задание", Ui.GREY, () -> {
                    RaceManager.refreshPreview(l);
                    toast("Подобрано другое задание.");
                }).tip("&7Перебросить задание той же сложности");
            }
        }

        // свои ставка и голоса
        ui.header("Твоя ставка и голос");
        ui.money("Ставка", "От " + Fmt.money(l.minStake) + " до " + Fmt.money(l.maxStake)
                + ". Списывается на старте; призовые места делят банк", me.stake, v -> run(() -> RaceManager.setStake(player, v)));
        if (l.difficulty == 0) {
            ui.select("Сложность (голос)", "Чем выше — тем длиннее цепочка квестов/крафта",
                    Integer.toString(me.diffVote), v -> run(() -> RaceManager.voteDifficulty(player, parseInt(v))))
                    .option("1", "★ 1", "").option("2", "★★ 2", "").option("3", "★★★ 3", "")
                    .option("4", "★★★★ 4", "").option("5", "★★★★★ 5", "");
        }
        if (l.mode == Lobby.Mode.TIMED && l.timeVote) {
            Ui.Select sel = ui.select("Время (голос)", "Итог — медиана голосов", Integer.toString(me.timeVote),
                    v -> run(() -> RaceManager.voteTime(player, parseInt(v))))
                    .option("0", "Авто", "по оценке: " + RaceManager.autoMinutes(l) + " мин");
            for (int m : ExtraConfig.timeOptions) {
                sel.option(Integer.toString(m), m + " мин", "");
            }
        }
        if (l.mode == Lobby.Mode.TIMED) {
            ui.text("&7Таймер: &f" + l.effectiveMinutes(RaceManager.autoMinutes(l)) + " мин"
                    + (l.timeVote ? " &8(по голосам)" : " &8(авто по оценке)"));
        }

        // игроки
        ui.header("Игроки", l.members.size() + "/" + l.maxPlayers);
        for (final Lobby.Member m : l.members) {
            boolean isHost = l.isHost(m.id);
            Ui.Row row = ui.row(new ItemStack(Items.SKULL, 1, 3), (isHost ? "&6♛ " : "&f") + m.name)
                    .line("&7Ставка: &6" + Fmt.money(m.stake)
                            + (l.difficulty == 0 ? " &8· &7сложность " + m.diffVote : "")
                            + (l.mode == Lobby.Mode.TIMED && l.timeVote ? " &8· &7время " + (m.timeVote == 0 ? "авто" : m.timeVote + " мин") : ""))
                    .highlight(m.id.equals(player.getUniqueID()));
            if (isHost) {
                row.chip("создатель", Ui.YELLOW);
            } else {
                row.chip(m.ready ? "готов" : "не готов", m.ready ? Ui.GREEN : Ui.GREY);
            }
            if (host && !isHost) {
                row.button("Выгнать", Ui.RED, () -> run(() -> RaceManager.kick(player, m.id)))
                        .confirm("Выгнать &f" + m.name + "&f из лобби?");
            }
        }

        if (host) {
            buildSettings(ui, l);
        }

        // низ окна: выход слева, «Готов» / «Играть» справа
        ui.footer("Выйти", Ui.RED, () -> {
            RaceManager.leave(player);
            tab = Tab.LOBBIES;
            toast("Ты вышел из лобби.");
        }).confirm(host && l.members.size() > 1 ? "Выйти? Лобби перейдёт к другому игроку." : "Выйти из лобби?");
        if (host) {
            String why = RaceManager.whyNotStart(l);
            Ui.Button play = ui.footer("Играть", Ui.GREEN, () -> run(() -> {
                RaceManager.start(player);
                tab = Tab.GAME;
            })).disabled(why != null);
            if (why != null) {
                play.tip("&e" + why);
            } else {
                play.confirm("Начать гонку? Ставки спишутся, все перенесутся на арену с пустым инвентарём.");
            }
        } else {
            ui.footer(me.ready ? "Не готов" : "Готов", me.ready ? Ui.GREY : Ui.GREEN,
                    () -> run(() -> RaceManager.setReady(player, !me.ready)));
        }
    }

    private static long pot(Lobby l) {
        long p = 0;
        for (Lobby.Member m : l.members) {
            p += m.stake;
        }
        return l.members.size() > 1 ? p : 0;
    }

    private void buildSettings(Ui ui, final Lobby l) {
        ui.header("Настройки лобби", "&8только у создателя");
        ui.field("Название", "", l.name, v -> {
            if (v.trim().isEmpty()) {
                throw new UiError("Название не может быть пустым.");
            }
            l.name = v.trim();
            RaceManager.settingsChanged(l, false);
        }).max(32);
        ui.field("Пароль", "Пусто — лобби открыто всем", l.password, v -> {
            l.password = v.trim();
            RaceManager.settingsChanged(l, false);
        }).placeholder("без пароля").clearable().max(32);
        ui.select("Режим", "", l.mode.name(), v -> {
            l.mode = "TIMED".equals(v) ? Lobby.Mode.TIMED : Lobby.Mode.NORMAL;
            RaceManager.settingsChanged(l, false);
        }).option("NORMAL", "Обычный", "игра идёт, пока не займут все призовые места (не дольше "
                + ExtraConfig.minigameMaxMinutes + " мин)", UiIcons.race())
                .option("TIMED", "На время", "таймер; кто не успел — места по пройденным шагам", UiIcons.hourglass());
        if (l.mode == Lobby.Mode.TIMED) {
            ui.select("Время", "", l.timeVote ? "vote" : "auto", v -> {
                l.timeVote = "vote".equals(v);
                RaceManager.settingsChanged(l, false);
            }).option("auto", "Авто", "по оценке задания × 1.5").option("vote", "Выбирают игроки", "медиана голосов");
        }
        ui.select("Сложность", "", Integer.toString(l.difficulty), v -> {
            l.difficulty = Math.max(0, Math.min(5, parseInt(v)));
            RaceManager.settingsChanged(l, true);
        }).option("0", "Голосование", "каждый голосует, берётся среднее")
                .option("1", "★ 1", "").option("2", "★★ 2", "").option("3", "★★★ 3", "")
                .option("4", "★★★★ 4", "").option("5", "★★★★★ 5", "");
        if (RaceTasks.questsAvailable()) {
            ui.toggle("Задания из FTB Quests", "Случайный квест и все его зависимости. Выключено — цепочки крафта",
                    l.useQuests, on -> {
                        l.useQuests = on;
                        RaceManager.settingsChanged(l, true);
                    });
        }
        ui.money("Минимальная ставка", "Не меньше " + Fmt.money(ExtraConfig.stakeMin), l.minStake, v -> {
            if (v < ExtraConfig.stakeMin || v > ExtraConfig.stakeMax) {
                throw new UiError("От " + Fmt.money(ExtraConfig.stakeMin) + " до " + Fmt.money(ExtraConfig.stakeMax) + ".");
            }
            l.minStake = v;
            l.maxStake = Math.max(l.maxStake, v);
            RaceManager.settingsChanged(l, false);
        });
        ui.money("Максимальная ставка", "Не больше " + Fmt.money(ExtraConfig.stakeMax) + ". Мин = макс — у всех одна ставка",
                l.maxStake, v -> {
                    if (v < l.minStake || v > ExtraConfig.stakeMax) {
                        throw new UiError("От " + Fmt.money(l.minStake) + " до " + Fmt.money(ExtraConfig.stakeMax) + ".");
                    }
                    l.maxStake = v;
                    RaceManager.settingsChanged(l, false);
                });
        ui.number("Игроков максимум", "", l.maxPlayers, Math.max(2, l.members.size()), ExtraConfig.minigameMaxPlayers, v -> {
            l.maxPlayers = (int) v;
            RaceManager.settingsChanged(l, false);
        });
    }

    // --- игра ---------------------------------------------------------------------------------------

    private void buildGame(Ui ui, final Lobby l) {
        final Lobby.Member me = l.member(player.getUniqueID());
        if (me == null || l.task == null) {
            return;
        }
        long now = System.currentTimeMillis();
        RaceTask t = l.task;
        if (l.state == Lobby.State.RUNNING && now < l.startAt) {
            ui.note(Ui.YELLOW, "Старт через " + ((l.startAt - now) / 1000 + 1) + " с…");
        }
        if (l.state == Lobby.State.FINISHED) {
            ui.note(Ui.BLUE, "Игра окончена. Через несколько секунд всех вернёт обратно.");
        }
        ui.row(UiIcons.race(), "&6" + t.title)
                .line("&7" + (t.source.equals("quest") ? "Цепочка квестов" : "Цепочка крафта") + " · " + stars(t.difficulty)
                        + " · банк &6" + Fmt.money(l.pot))
                .line("&7Оценка: &f" + t.estimate.timeText() + " &8· &7сырьё: &f" + t.estimate.rawSummary(5));
        if (l.mode == Lobby.Mode.TIMED && l.state == Lobby.State.RUNNING) {
            long left = Math.max(0L, l.endAt - Math.max(now, l.startAt));
            ui.bar("Осталось " + Fmt.duration(left), left, Math.max(1L, l.endAt - l.startAt), left < 60_000L ? Ui.RED : Ui.YELLOW);
        }

        boolean onArena = player.dimension == l.arenaDim;
        if (l.state == Lobby.State.RUNNING && !me.gaveUp && !me.finished()) {
            if (!onArena) {
                ui.note(Ui.YELLOW, "Ты не на арене.");
                ui.buttons().add("Вернуться на арену", Ui.BLUE, () -> run(() -> RaceManager.backToArena(player)));
            }
            ui.header("Шаги", me.step + "/" + t.steps.size());
            for (int i = 0; i < t.steps.size(); i++) {
                RaceTask.Step s = t.steps.get(i);
                boolean done = i < me.step;
                boolean cur = i == me.step;
                if (i > me.step) {
                    ui.row(new ItemStack(Items.PAPER), "&8" + (i + 1) + ". ???").line("&8откроется после шага " + (i + 1 - 1)).dim(true);
                    continue;
                }
                RaceTask.Need first = s.needs.isEmpty() ? null : s.needs.get(0);
                Ui.Row row = ui.row(first == null ? new ItemStack(Items.PAPER) : first.icon(),
                        (done ? "&a✔ " : "&e") + (i + 1) + ". " + s.title)
                        .highlight(cur).dim(done);
                boolean all = true;
                for (RaceTask.Need need : s.needs) {
                    int have = done ? need.count : Math.min(need.count, RaceManager.have(player, need));
                    all &= have >= need.count;
                    row.line((have >= need.count ? "&a" : "&7") + need.name + " &f" + have + "/" + need.count);
                }
                if (cur) {
                    row.button("Сдать", all ? Ui.GREEN : Ui.GREY, () -> run(() -> {
                        RaceManager.submit(player);
                        toast("Шаг сдан!");
                    })).disabled(!all || !onArena || !RaceManager.started(l));
                }
            }
        } else if (me.finished()) {
            ui.note(Ui.GREEN, "Ты выполнил задание! Место: " + me.place + ". Ждём остальных.");
        } else if (me.gaveUp) {
            ui.note(Ui.GREY, "Ты сдался — смотри, как играют остальные.");
        }

        ui.header("Таблица");
        int place = 1;
        for (Lobby.Member m : RaceManager.ranking(l)) {
            String state = m.finished() ? "&aготово за " + Fmt.duration(m.finishedAt - l.startAt)
                    : m.gaveUp ? "&7сдался" : "&fшаг " + m.step + "/" + t.steps.size();
            Ui.Row row = ui.row(new ItemStack(Items.SKULL, 1, 3), "&e" + place + ". &f" + m.name)
                    .line(state)
                    .highlight(m.id.equals(player.getUniqueID()));
            if (l.state == Lobby.State.FINISHED) {
                row.chip(m.won > 0 ? "+" + Fmt.money(m.won) : m.paid > 0 ? "-" + Fmt.money(m.paid) : "0",
                        m.won > m.paid ? Ui.GREEN : m.won > 0 ? Ui.YELLOW : Ui.GREY);
            }
            place++;
        }
        if (l.state == Lobby.State.RUNNING && !me.gaveUp && !me.finished()) {
            ui.footer("Сдаться", Ui.RED, () -> {
                RaceManager.leave(player);
                toast("Ты сдался.");
            }).confirm("Сдаться? Ставка не вернётся, вещи с арены не сохранятся.");
        }
    }

    // --- правила --------------------------------------------------------------------------------------

    private void buildRules(Ui ui) {
        ui.text("&7Найди или создай лобби, поставь монеты и нажми &a«Готов»&7. Когда все готовы, создатель нажимает "
                + "&a«Играть»&7: всех переносит на арену — отдельный мир с пометкой «МИНИ-ИГРА», где у каждого "
                + "&fсвой пустой инвентарь&7 (вещи с собой не пронести и с арены не вынести, эндер-сундук закрыт).");
        ui.text("&7Задание — случайная цепочка: квест FTB Quests со всеми зависимостями или цепочка крафта. Чем выше "
                + "сложность, тем она длиннее. Шаги открываются по одному; вещи шага сдаются кнопкой «Сдать» "
                + "(или &f/race сдать&7) и забираются.");
        ui.text("&7&fОбычный&7 режим: игра идёт, пока не разберут призовые места. &fНа время&7: таймер (авто по "
                + "оценке или голосованием), кто не успел — места по пройденным шагам.");
        ui.text("&7Ставки защищают от «просто поиграть и бросить»: проигравшие теряют ставку. Выигрыш одного игрока — "
                + "не больше его ставки × число игроков.");
        if (ExtraConfig.minigameCutPercent > 0) {
            ui.note(Ui.YELLOW, "Комиссия сервера: " + Fmt.price(ExtraConfig.minigameCutPercent) + "% банка.");
        }
        ui.header("Выплаты", "% от банка");
        for (int n : new int[]{2, 3, 4, 10, 12, 15, 20}) {
            if (n > ExtraConfig.minigameMaxPlayers) {
                continue;
            }
            double[] p = RacePayout.percents(n);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < p.length; i++) {
                if (i > 0) {
                    sb.append("&8 · ");
                }
                sb.append(i == 0 ? "&6" : "&f").append(i + 1).append(": ")
                        .append(String.format(Locale.ROOT, "%.0f%%", p[i]));
            }
            ui.row(new ItemStack(Items.GOLD_NUGGET), "&f" + n + " игроков &7— мест: " + p.length).line(sb.toString());
        }
        if (Perms.isAdmin(player)) {
            ui.header("Для админа");
            ui.text("&7Настройки — раздел «minigame» в настройках мода: ставки, игроки, время, комиссия, арена.");
            for (com.ecoserv.subworld.Subworld sw : RaceArena.arenas()) {
                ui.row(UiUtil.icon(sw.icon), "&eАрена: " + sw.name).line("&8" + sw.id);
            }
        }
    }
}
