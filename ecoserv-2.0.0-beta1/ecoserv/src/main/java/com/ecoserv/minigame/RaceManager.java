package com.ecoserv.minigame;

import com.ecoserv.EcoServ;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.extra.MoneyNote;
import com.ecoserv.inventory.InventoryGroups;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldTeleport;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.PlayerUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * «Квестовая гонка» (1.8.0): лобби, старт, сдача шагов, итоги и выплаты.
 * <p>
 * Ставки списываются при старте и, пока идёт игра, записаны в world/ecoserv/minigame.json — если сервер упадёт
 * посреди игры, при следующем запуске всем вернутся их ставки, а копии арен удалятся.
 */
public final class RaceManager {

    private static final Map<Integer, Lobby> LOBBIES = new LinkedHashMap<Integer, Lobby>();
    private static final Map<UUID, Integer> PLAYER_LOBBY = new HashMap<UUID, Integer>();
    private static final Random RND = new Random();
    private static final Type ESCROW_TYPE = new TypeToken<HashMap<String, HashMap<String, Long>>>() {
    }.getType();
    private static int nextId = 1;
    private static int tick;
    private static boolean dirty;

    /** Сколько секунд обратного отсчёта перед стартом. */
    private static final int COUNTDOWN = 5;
    /** Сколько секунд показывать итоги на арене, прежде чем всех вернуть. */
    private static final int RESULTS_SECONDS = 15;

    private RaceManager() {
    }

    // --- данные -------------------------------------------------------------------------------------

    public static synchronized List<Lobby> lobbies() {
        return new ArrayList<Lobby>(LOBBIES.values());
    }

    @Nullable
    public static synchronized Lobby of(UUID player) {
        Integer id = PLAYER_LOBBY.get(player);
        return id == null ? null : LOBBIES.get(id);
    }

    @Nullable
    public static synchronized Lobby byId(int id) {
        return LOBBIES.get(id);
    }

    // --- лобби --------------------------------------------------------------------------------------

    public static synchronized Lobby create(EntityPlayerMP host) {
        check();
        if (of(host.getUniqueID()) != null) {
            throw new IllegalStateException("Ты уже в лобби — сначала выйди из него.");
        }
        Lobby l = new Lobby(nextId++);
        l.name = "Гонка " + host.getName();
        l.host = host.getUniqueID();
        l.minStake = ExtraConfig.stakeMin;
        l.maxStake = Math.max(l.minStake, Math.min(ExtraConfig.stakeMax, Math.max(l.minStake, ExtraConfig.stakeMin * 10)));
        l.maxPlayers = Math.min(10, ExtraConfig.minigameMaxPlayers);
        LOBBIES.put(l.id, l);
        addMember(l, host);
        refreshPreview(l);
        return l;
    }

    private static void check() {
        if (!ExtraConfig.minigameEnabled) {
            throw new IllegalStateException("Мини-игра отключена на сервере.");
        }
    }

    private static void addMember(Lobby l, EntityPlayerMP p) {
        Lobby.Member m = new Lobby.Member();
        m.id = p.getUniqueID();
        m.name = p.getName();
        m.stake = l.minStake;
        l.members.add(m);
        PLAYER_LOBBY.put(m.id, l.id);
        l.touch();
    }

    public static synchronized void join(EntityPlayerMP p, int id, String password) {
        check();
        Lobby l = LOBBIES.get(id);
        if (l == null) {
            throw new IllegalStateException("Лобби уже нет.");
        }
        Lobby cur = of(p.getUniqueID());
        if (cur == l) {
            return;
        }
        if (cur != null) {
            throw new IllegalStateException("Ты уже в другом лобби — сначала выйди из него.");
        }
        if (l.state != Lobby.State.WAITING) {
            throw new IllegalStateException("Игра в этом лобби уже идёт.");
        }
        if (l.full()) {
            throw new IllegalStateException("Лобби заполнено.");
        }
        if (l.hasPassword() && !l.password.equals(password == null ? "" : password.trim())) {
            throw new IllegalStateException("Неверный пароль.");
        }
        if (EconomyManager.get(p.getUniqueID()) < l.minStake && ExtraConfig.minigameMinPlayers > 1) {
            throw new IllegalStateException("Минимальная ставка здесь " + Fmt.money(l.minStake) + " — у тебя столько нет.");
        }
        addMember(l, p);
        tell(l, "&a" + p.getName() + " &7зашёл в лобби.");
    }

    public static synchronized void leave(EntityPlayerMP p) {
        Lobby l = of(p.getUniqueID());
        if (l == null) {
            return;
        }
        Lobby.Member m = l.member(p.getUniqueID());
        if (l.state == Lobby.State.RUNNING && m != null) {
            giveUp(l, m);
            return;
        }
        remove(l, p.getUniqueID(), "&7" + p.getName() + " вышел из лобби.");
    }

    private static void remove(Lobby l, UUID id, String message) {
        Lobby.Member m = l.member(id);
        if (m == null) {
            return;
        }
        l.members.remove(m);
        PLAYER_LOBBY.remove(id);
        if (l.state == Lobby.State.WAITING) {
            if (l.members.isEmpty()) {
                LOBBIES.remove(l.id);
                return;
            }
            if (l.isHost(id)) {
                l.host = l.members.get(0).id;
                l.members.get(0).ready = false;
                tell(l, "&eТеперь лобби ведёт &f" + l.members.get(0).name + "&e.");
            }
        }
        tell(l, message);
        l.touch();
    }

    public static synchronized void kick(EntityPlayerMP host, UUID target) {
        Lobby l = hostLobby(host);
        if (l.isHost(target)) {
            return;
        }
        Lobby.Member m = l.member(target);
        if (m == null) {
            return;
        }
        remove(l, target, "&c" + m.name + " исключён из лобби.");
        EntityPlayerMP p = PlayerUtil.online(target);
        if (p != null) {
            ChatUtil.send(p, "&cТебя исключили из лобби «" + l.name + "&c».");
        }
    }

    static Lobby hostLobby(EntityPlayerMP host) {
        Lobby l = of(host.getUniqueID());
        if (l == null || !l.isHost(host.getUniqueID())) {
            throw new IllegalStateException("Это может только создатель лобби.");
        }
        if (l.state != Lobby.State.WAITING) {
            throw new IllegalStateException("Игра уже идёт.");
        }
        return l;
    }

    /** Настройки поменялись: готовность сбрасывается, ставки поджимаются, превью задания — заново. */
    public static synchronized void settingsChanged(Lobby l, boolean taskChanged) {
        l.unready();
        l.clampStakes();
        if (taskChanged) {
            refreshPreview(l);
        }
        l.touch();
    }

    /** Превью задания для текущей сложности (видна только оценка). */
    public static synchronized void refreshPreview(Lobby l) {
        int d = l.effectiveDifficulty();
        try {
            l.task = RaceTasks.generate(d, l.useQuests, RND);
        } catch (Throwable t) {
            EcoServ.LOG.warn("Квестовая гонка: не удалось подобрать задание: {}", t.toString());
            l.task = null;
        }
        l.taskDifficulty = d;
    }

    public static synchronized void setReady(EntityPlayerMP p, boolean ready) {
        Lobby l = of(p.getUniqueID());
        Lobby.Member m = l == null ? null : l.member(p.getUniqueID());
        if (m == null || l.state != Lobby.State.WAITING) {
            return;
        }
        if (ready && EconomyManager.get(p.getUniqueID()) < m.stake && needsStakes(l)) {
            throw new IllegalStateException("Не хватает денег на ставку " + Fmt.money(m.stake) + ".");
        }
        m.ready = ready;
        l.touch();
    }

    public static synchronized void setStake(EntityPlayerMP p, long stake) {
        Lobby l = of(p.getUniqueID());
        Lobby.Member m = l == null ? null : l.member(p.getUniqueID());
        if (m == null || l.state != Lobby.State.WAITING) {
            return;
        }
        if (stake < l.minStake || stake > l.maxStake) {
            throw new IllegalStateException("Ставка здесь — от " + Fmt.money(l.minStake) + " до " + Fmt.money(l.maxStake) + ".");
        }
        m.stake = stake;
        m.ready = false;
        l.touch();
    }

    public static synchronized void voteDifficulty(EntityPlayerMP p, int d) {
        Lobby l = of(p.getUniqueID());
        Lobby.Member m = l == null ? null : l.member(p.getUniqueID());
        if (m == null || l.state != Lobby.State.WAITING) {
            return;
        }
        int before = l.effectiveDifficulty();
        m.diffVote = Math.max(1, Math.min(5, d));
        if (l.effectiveDifficulty() != before) {
            refreshPreview(l);
            l.unready();
        }
        l.touch();
    }

    public static synchronized void voteTime(EntityPlayerMP p, int minutes) {
        Lobby l = of(p.getUniqueID());
        Lobby.Member m = l == null ? null : l.member(p.getUniqueID());
        if (m == null || l.state != Lobby.State.WAITING) {
            return;
        }
        m.timeVote = Math.max(0, Math.min(ExtraConfig.minigameMaxMinutes, minutes));
        l.touch();
    }

    /** Одиночная проверка (min_players = 1) — без ставок, чтобы не «выигрывать» у самого себя. */
    static boolean needsStakes(Lobby l) {
        return l.members.size() > 1;
    }

    public static int autoMinutes(Lobby l) {
        return l.task == null ? 30 : l.task.estimate.autoMinutes(ExtraConfig.minigameMaxMinutes);
    }

    // --- старт --------------------------------------------------------------------------------------

    /** Почему нельзя начать (null — можно). */
    @Nullable
    public static synchronized String whyNotStart(Lobby l) {
        if (l.members.size() < Math.max(1, ExtraConfig.minigameMinPlayers)) {
            return "Нужно хотя бы " + ExtraConfig.minigameMinPlayers + " игрока.";
        }
        if (!l.allReady()) {
            return "Ещё не все нажали «Готов».";
        }
        if (RaceArena.pick() == null) {
            return "Нет свободной арены: админ должен включить подмир с пометкой «Мини-игра».";
        }
        if (l.task == null) {
            return "Не удалось подобрать задание — попробуй другую сложность.";
        }
        return null;
    }

    public static synchronized void start(EntityPlayerMP host) {
        check();
        Lobby l = hostLobby(host);
        String why = whyNotStart(l);
        if (why != null) {
            throw new IllegalStateException(why);
        }
        List<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>();
        for (Lobby.Member m : l.members) {
            EntityPlayerMP p = PlayerUtil.online(m.id);
            if (p == null) {
                throw new IllegalStateException(m.name + " не в сети.");
            }
            if (RaceArena.isArena(p.dimension)) {
                throw new IllegalStateException(m.name + " ещё на другой арене.");
            }
            players.add(p);
        }
        // задание — заново (превью видели все, но шаги до старта не раскрываются)
        int d = l.effectiveDifficulty();
        RaceTask task = RaceTasks.generate(d, l.useQuests, RND);
        if (task == null) {
            throw new IllegalStateException("Не удалось подобрать задание — попробуй другую сложность.");
        }
        Subworld sw = RaceArena.pick();
        if (sw == null) {
            throw new IllegalStateException("Нет свободной арены.");
        }
        // ставки
        boolean stakes = needsStakes(l);
        List<Lobby.Member> paid = new ArrayList<Lobby.Member>();
        if (stakes) {
            for (Lobby.Member m : l.members) {
                MoneyNote.set("Квестовая гонка: ставка");
                boolean ok;
                try {
                    ok = EconomyManager.take(m.id, m.stake);
                } finally {
                    MoneyNote.clear();
                }
                if (!ok) {
                    refund(paid, "Квестовая гонка: игра не началась");
                    m.ready = false;
                    throw new IllegalStateException("У " + m.name + " не хватает денег на ставку " + Fmt.money(m.stake) + ".");
                }
                m.paid = m.stake;
                paid.add(m);
            }
        }
        SubworldDim arena = RaceArena.create(l, sw);
        if (arena == null) {
            refund(paid, "Квестовая гонка: арена не создалась");
            throw new IllegalStateException("Не удалось создать копию арены (слишком много миров?) — сообщи админу.");
        }
        l.task = task;
        l.taskDifficulty = d;
        l.arenaDim = arena.dim;
        l.arenaSubworld = sw.id;
        l.pot = 0;
        for (Lobby.Member m : l.members) {
            l.pot += m.paid;
            m.step = 0;
            m.lastStepAt = 0;
            m.finishedAt = 0;
            m.gaveUp = false;
            m.place = 0;
            m.won = 0;
        }
        long now = System.currentTimeMillis();
        l.minutes = l.effectiveMinutes(task.estimate.autoMinutes(ExtraConfig.minigameMaxMinutes));
        l.startAt = now + COUNTDOWN * 1000L;
        l.endAt = l.mode == Lobby.Mode.TIMED ? l.startAt + l.minutes * 60_000L
                : l.startAt + ExtraConfig.minigameMaxMinutes * 60_000L;
        l.state = Lobby.State.RUNNING;
        dirty = true;
        saveIfDirty();
        for (EntityPlayerMP p : players) {
            // на арену — с пустым инвентарём (снимок прошлой игры забываем)
            InventoryGroups.dropGroup(p.getUniqueID(), InventoryGroups.RACE);
            p.closeScreen();
            if (!SubworldTeleport.teleportTo(p, arena, true, null)) {
                ChatUtil.send(p, "&cНе удалось перенести на арену — нажми «Вернуться на арену» в /race.");
            }
            PacketHandler.sendPlayerState(p);
        }
        tell(l, "&6Квестовая гонка началась! &7Задание: &f" + task.title + " &7(" + task.steps.size() + " шаг.)"
                + (l.pot > 0 ? " &7Банк: &6" + Fmt.money(l.pot) : ""));
        EcoServ.LOG.info("Квестовая гонка #{}: {} игроков, задание «{}», сложность {}, банк {}", l.id, l.members.size(),
                task.title, d, l.pot);
    }

    private static void refund(List<Lobby.Member> paid, String note) {
        for (Lobby.Member m : paid) {
            if (m.paid > 0) {
                MoneyNote.set(note);
                try {
                    EconomyManager.give(m.id, m.paid);
                } finally {
                    MoneyNote.clear();
                }
                m.paid = 0;
            }
        }
    }

    // --- игра ---------------------------------------------------------------------------------------

    public static boolean started(Lobby l) {
        return l.state == Lobby.State.RUNNING && System.currentTimeMillis() >= l.startAt;
    }

    /** Сколько подходящих вещей у игрока для требования. */
    public static int have(EntityPlayerMP p, RaceTask.Need need) {
        int n = 0;
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (RaceTasks.matches(need, s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Сдать текущий шаг: все вещи шага должны быть в инвентаре. */
    public static synchronized void submit(EntityPlayerMP p) {
        Lobby l = of(p.getUniqueID());
        Lobby.Member m = l == null ? null : l.member(p.getUniqueID());
        if (m == null || l.state != Lobby.State.RUNNING || l.task == null) {
            throw new IllegalStateException("Ты сейчас не в игре.");
        }
        if (!started(l)) {
            throw new IllegalStateException("Игра ещё не началась.");
        }
        if (m.finished() || m.gaveUp) {
            throw new IllegalStateException("Ты уже закончил.");
        }
        if (!RaceArena.isArena(p.dimension) || p.dimension != l.arenaDim) {
            throw new IllegalStateException("Сдавать можно только на арене.");
        }
        RaceTask.Step step = l.task.steps.get(m.step);
        for (RaceTask.Need need : step.needs) {
            int have = have(p, need);
            if (have < need.count) {
                throw new IllegalStateException("Не хватает: " + need.name + " (" + have + "/" + need.count + ").");
            }
        }
        for (RaceTask.Need need : step.needs) {
            int left = need.count;
            for (int i = 0; i < p.inventory.getSizeInventory() && left > 0; i++) {
                ItemStack s = p.inventory.getStackInSlot(i);
                if (RaceTasks.matches(need, s)) {
                    left -= p.inventory.decrStackSize(i, Math.min(left, s.getCount())).getCount();
                }
            }
        }
        p.inventoryContainer.detectAndSendChanges();
        m.step++;
        m.lastStepAt = System.currentTimeMillis();
        if (m.step >= l.task.steps.size()) {
            m.finishedAt = m.lastStepAt;
            int place = 1;
            for (Lobby.Member o : l.members) {
                if (o != m && o.finished()) {
                    place++;
                }
            }
            m.place = place;
            tell(l, "&6" + m.name + " &aвыполнил задание! &7Место: &f" + place + " &8(" + Fmt.duration(m.finishedAt - l.startAt) + ")");
            title(p, "&6" + place + " место!", "&7задание выполнено");
            checkEnd(l);
        } else {
            tell(l, "&e" + m.name + " &7— шаг &f" + m.step + "/" + l.task.steps.size());
        }
        l.touch();
    }

    public static synchronized void giveUp(Lobby l, Lobby.Member m) {
        if (m.gaveUp || m.finished()) {
            return;
        }
        m.gaveUp = true;
        tell(l, "&7" + m.name + " сдался.");
        EntityPlayerMP p = PlayerUtil.online(m.id);
        if (p != null && RaceArena.isArena(p.dimension)) {
            SubworldTeleport.evict(p, "&7Ты покинул гонку.");
        }
        checkEnd(l);
    }

    /** Вернуться на арену (умер и возродился снаружи, вышел случайно). */
    public static synchronized void backToArena(EntityPlayerMP p) {
        Lobby l = of(p.getUniqueID());
        Lobby.Member m = l == null ? null : l.member(p.getUniqueID());
        if (m == null || l.state != Lobby.State.RUNNING || m.gaveUp) {
            throw new IllegalStateException("Ты сейчас не в игре.");
        }
        SubworldDim d = SubworldRegistry.dim(l.arenaDim);
        if (d == null) {
            throw new IllegalStateException("Арены уже нет.");
        }
        SubworldTeleport.teleportTo(p, d, false, null);
    }

    /** Все призовые места заняты или играть больше некому — конец. */
    private static void checkEnd(Lobby l) {
        int active = 0;
        int finished = 0;
        for (Lobby.Member m : l.members) {
            if (m.finished()) {
                finished++;
            } else if (!m.gaveUp) {
                active++;
            }
        }
        int places = Math.min(RacePayout.places(l.members.size()), l.members.size());
        if (active == 0 || (l.mode == Lobby.Mode.NORMAL && finished >= places && l.members.size() > 1)) {
            finish(l, "");
        }
    }

    /** Итоги: места, выплаты, через 15 секунд — всех домой и арену удалить. */
    private static void finish(Lobby l, String reason) {
        if (l.state != Lobby.State.RUNNING) {
            return;
        }
        List<Lobby.Member> order = ranking(l);
        long[] stakes = new long[order.size()];
        boolean[] eligible = new boolean[order.size()];
        for (int i = 0; i < order.size(); i++) {
            Lobby.Member m = order.get(i);
            m.place = i + 1;
            stakes[i] = m.paid;
            eligible[i] = !m.gaveUp && (m.finished() || m.step > 0);
        }
        long[] pay = RacePayout.split(stakes, eligible, ExtraConfig.minigameCutPercent);
        StringBuilder sb = new StringBuilder("&6Итоги гонки «" + l.task.title + "&6»" + (reason.isEmpty() ? "" : " &7(" + reason + ")") + ":");
        for (int i = 0; i < order.size(); i++) {
            Lobby.Member m = order.get(i);
            m.won = pay[i];
            if (pay[i] > 0) {
                MoneyNote.set("Квестовая гонка: " + m.place + " место");
                try {
                    EconomyManager.give(m.id, pay[i]);
                } finally {
                    MoneyNote.clear();
                }
            }
            sb.append("\n&f").append(m.place).append(". &e").append(m.name).append(" &7— ")
                    .append(m.finished() ? "выполнил за " + Fmt.duration(m.finishedAt - l.startAt)
                            : m.gaveUp ? "сдался" : "шагов " + m.step + "/" + l.task.steps.size())
                    .append(pay[i] > 0 ? " &a+" + Fmt.money(pay[i]) : m.paid > 0 ? " &c-" + Fmt.money(m.paid) : "");
        }
        tell(l, sb.toString());
        for (Lobby.Member m : order) {
            EntityPlayerMP p = PlayerUtil.online(m.id);
            if (p != null) {
                title(p, m.won > m.paid ? "&a" + m.place + " место!" : "&7" + m.place + " место",
                        m.won > 0 ? "&a+" + Fmt.money(m.won) : m.paid > 0 ? "&c-" + Fmt.money(m.paid) : "");
                PacketHandler.sendPlayerState(p);
            }
        }
        l.state = Lobby.State.FINISHED;
        l.finishedAt = System.currentTimeMillis();
        l.cleanupAt = l.finishedAt + RESULTS_SECONDS * 1000L;
        dirty = true;
        saveIfDirty();
    }

    /** Места: выполнившие — по времени; остальные — по пройденным шагам и времени последнего шага; сдавшиеся — в конце. */
    public static List<Lobby.Member> ranking(Lobby l) {
        List<Lobby.Member> order = new ArrayList<Lobby.Member>(l.members);
        Collections.sort(order, (a, b) -> {
            if (a.gaveUp != b.gaveUp) {
                return a.gaveUp ? 1 : -1;
            }
            if (a.finished() != b.finished()) {
                return a.finished() ? -1 : 1;
            }
            if (a.finished()) {
                return Long.compare(a.finishedAt, b.finishedAt);
            }
            if (a.step != b.step) {
                return Integer.compare(b.step, a.step);
            }
            return Long.compare(a.lastStepAt == 0 ? Long.MAX_VALUE : a.lastStepAt, b.lastStepAt == 0 ? Long.MAX_VALUE : b.lastStepAt);
        });
        return order;
    }

    private static void close(Lobby l) {
        if (l.arenaDim != 0) {
            RaceArena.delete(l.arenaDim, "&7Гонка окончена — ты вернулся туда, откуда пришёл.");
        }
        for (Lobby.Member m : l.members) {
            // вещи с арены не выносятся: снимок её инвентаря забываем
            InventoryGroups.dropGroup(m.id, InventoryGroups.RACE);
        }
        // лобби снова ждёт игроков — можно сыграть ещё раз тем же составом
        l.state = Lobby.State.WAITING;
        l.arenaDim = 0;
        l.pot = 0;
        for (Lobby.Member m : new ArrayList<Lobby.Member>(l.members)) {
            m.ready = false;
            m.paid = 0;
            if (PlayerUtil.online(m.id) == null) {
                l.members.remove(m);
                PLAYER_LOBBY.remove(m.id);
            }
        }
        if (l.members.isEmpty()) {
            LOBBIES.remove(l.id);
            dirty = true;
            saveIfDirty();
            return;
        }
        if (l.member(l.host) == null) {
            l.host = l.members.get(0).id;
        }
        refreshPreview(l);
        l.touch();
        dirty = true;
        saveIfDirty();
    }

    // --- тик ----------------------------------------------------------------------------------------

    public static synchronized void tick() {
        if (++tick % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Lobby l : new ArrayList<Lobby>(LOBBIES.values())) {
            try {
                tickLobby(l, now);
            } catch (Throwable t) {
                EcoServ.LOG.error("Квестовая гонка #" + l.id + ": ошибка", t);
            }
        }
    }

    private static void tickLobby(Lobby l, long now) {
        switch (l.state) {
            case WAITING:
                if (now - l.lastActivity > ExtraConfig.lobbyIdleMinutes * 60_000L) {
                    tell(l, "&7Лобби «" + l.name + "&7» закрыто: долго никто ничего не делал.");
                    for (Lobby.Member m : l.members) {
                        PLAYER_LOBBY.remove(m.id);
                    }
                    LOBBIES.remove(l.id);
                }
                break;
            case RUNNING:
                if (now < l.startAt) {
                    int left = (int) Math.ceil((l.startAt - now) / 1000.0D);
                    for (Lobby.Member m : l.members) {
                        EntityPlayerMP p = PlayerUtil.online(m.id);
                        if (p != null) {
                            title(p, "&e" + left, "&7приготовься…");
                        }
                    }
                    return;
                }
                if (SubworldRegistry.dim(l.arenaDim) == null) {
                    finish(l, "арену удалили");
                    return;
                }
                if (now >= l.endAt) {
                    finish(l, l.mode == Lobby.Mode.TIMED ? "время вышло" : "слишком долго");
                    return;
                }
                if (l.task != null) {
                    for (Lobby.Member m : l.members) {
                        EntityPlayerMP p = PlayerUtil.online(m.id);
                        if (p == null || m.gaveUp || m.finished()) {
                            continue;
                        }
                        RaceTask.Step step = l.task.steps.get(Math.min(m.step, l.task.steps.size() - 1));
                        StringBuilder sb = new StringBuilder("&6Шаг " + (m.step + 1) + "/" + l.task.steps.size() + ": &f");
                        boolean all = true;
                        for (RaceTask.Need need : step.needs) {
                            int have = Math.min(need.count, have(p, need));
                            all &= have >= need.count;
                            sb.append(have >= need.count ? "&a" : "&f").append(need.name).append(" ").append(have)
                                    .append("/").append(need.count).append("  ");
                        }
                        if (l.mode == Lobby.Mode.TIMED) {
                            sb.append("&7⏱ ").append(Fmt.duration(Math.max(0L, l.endAt - now)));
                        }
                        if (all) {
                            sb.append(" &a→ /race сдать");
                        }
                        ChatUtil.actionBar(p, sb.toString());
                    }
                }
                break;
            case FINISHED:
                if (now >= l.cleanupAt) {
                    close(l);
                }
                break;
            default:
        }
    }

    public static synchronized void onLogout(EntityPlayerMP p) {
        Lobby l = of(p.getUniqueID());
        if (l != null && l.state == Lobby.State.WAITING) {
            remove(l, p.getUniqueID(), "&7" + p.getName() + " вышел с сервера.");
        }
        // во время игры остаётся: зайдёт снова и продолжит (или сдастся)
    }

    // --- сообщения ----------------------------------------------------------------------------------

    private static void tell(Lobby l, String text) {
        for (Lobby.Member m : l.members) {
            EntityPlayerMP p = PlayerUtil.online(m.id);
            if (p != null) {
                ChatUtil.send(p, text);
            }
        }
    }

    private static void title(EntityPlayerMP p, String title, String sub) {
        try {
            p.connection.sendPacket(new SPacketTitle(0, 30, 10));
            p.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.SUBTITLE, new TextComponentString(ChatUtil.color(sub))));
            p.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TITLE, new TextComponentString(ChatUtil.color(title))));
        } catch (Throwable ignored) {
            // нет соединения
        }
    }

    // --- ставки на случай падения сервера --------------------------------------------------------------

    private static File file() {
        return DataStore.file("minigame.json");
    }

    public static synchronized void saveIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        HashMap<String, HashMap<String, Long>> out = new HashMap<String, HashMap<String, Long>>();
        for (Lobby l : LOBBIES.values()) {
            if (l.state != Lobby.State.RUNNING) {
                continue;
            }
            HashMap<String, Long> stakes = new HashMap<String, Long>();
            for (Lobby.Member m : l.members) {
                if (m.paid > 0) {
                    stakes.put(m.id.toString(), m.paid);
                }
            }
            out.put(Integer.toString(l.id), stakes);
        }
        JsonUtil.write(file(), out);
    }

    /** Запуск сервера: игры прошлого запуска прерваны — ставки вернуть, копии арен удалить. */
    public static synchronized void onServerStart() {
        LOBBIES.clear();
        PLAYER_LOBBY.clear();
        RecipeBook.reset();
        try {
            HashMap<String, HashMap<String, Long>> raw = JsonUtil.readType(file(), ESCROW_TYPE,
                    new HashMap<String, HashMap<String, Long>>());
            int n = 0;
            for (HashMap<String, Long> stakes : raw.values()) {
                for (Map.Entry<String, Long> e : stakes.entrySet()) {
                    try {
                        MoneyNote.set("Квестовая гонка: возврат ставки (сервер перезапустился)");
                        EconomyManager.give(UUID.fromString(e.getKey()), e.getValue());
                        n++;
                    } catch (IllegalArgumentException ignored) {
                        // мусор
                    } finally {
                        MoneyNote.clear();
                    }
                }
            }
            if (n > 0) {
                EcoServ.LOG.info("Квестовая гонка: прерванные игры — возвращено ставок: {}", n);
            }
            int arenas = RaceArena.cleanupStale();
            if (arenas > 0) {
                EcoServ.LOG.info("Квестовая гонка: удалено копий арен прошлых игр: {}", arenas);
            }
        } catch (Throwable t) {
            EcoServ.LOG.error("Квестовая гонка: не удалось вернуть ставки прерванных игр", t);
        }
        dirty = true;
        saveIfDirty();
    }

    /** Остановка сервера: идущие игры прерываются, ставки возвращаются сразу. */
    public static synchronized void onServerStop() {
        for (Lobby l : LOBBIES.values()) {
            if (l.state == Lobby.State.RUNNING) {
                refund(l.members, "Квестовая гонка: возврат ставки (сервер выключается)");
            }
        }
        LOBBIES.clear();
        PLAYER_LOBBY.clear();
        dirty = true;
        saveIfDirty();
    }
}
