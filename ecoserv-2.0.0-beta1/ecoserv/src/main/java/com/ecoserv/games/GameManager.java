package com.ecoserv.games;

import com.ecoserv.EcoServ;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.extra.MoneyNote;
import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.init.ModSounds;
import com.ecoserv.inventory.InventoryGroups;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.GameType;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Мини-игры 1.9.0: виды игр, список игр (встроенные + свои), лобби, ход игры, ставки и награды.
 * Игры с ареной идут в мире арен (GameArena), у игроков там свой пустой инвентарь (как у квестовой гонки).
 */
public final class GameManager {

    public enum State {
        LOBBY, COUNTDOWN, RUNNING
    }

    /** Лобби и идущая игра. */
    public static final class Session {
        public final int id;
        public final GamePreset preset;
        public final UUID host;
        public final List<UUID> players = new ArrayList<UUID>();
        public final Set<UUID> ready = new HashSet<UUID>();
        public final Map<UUID, Long> paid = new HashMap<UUID, Long>();
        public State state = State.LOBBY;
        public long startAt;
        public long pot;
        public int slot;
        @Nullable
        public Game game;
        public final long created = System.currentTimeMillis();

        Session(int id, GamePreset preset, UUID host) {
            this.id = id;
            this.preset = preset;
            this.host = host;
        }

        public GameKind kind() {
            return KINDS.get(preset.kind);
        }
    }

    private static final class Data {
        List<GamePreset> presets = new ArrayList<GamePreset>();
        /** Области арен, которые не успели стереть (сервер упал посреди игры). */
        List<int[]> cleanup = new ArrayList<int[]>();
    }

    private static final Type DATA = new TypeToken<Data>() {
    }.getType();
    public static final Map<String, GameKind> KINDS = new LinkedHashMap<String, GameKind>();
    private static final Map<String, GamePreset> PRESETS = new LinkedHashMap<String, GamePreset>();
    private static final Map<Integer, Session> SESSIONS = new LinkedHashMap<Integer, Session>();
    private static final List<int[]> PENDING = new ArrayList<int[]>();
    private static int nextId = 1;
    private static final int COUNTDOWN = 5;

    static {
        kind(new DeathrunGame.Kind());
        kind(new ChaseGame.Kind());
        kind(new PressureGame.Kind());
        // 2.0
        kind(new SpleefGame.Kind());
        kind(new ColorRunGame.Kind());
        kind(new MazeGame.Kind());
        kind(new TntTagGame.Kind());
        kind(new KingGame.Kind());
        kind(new MemoryGame.Kind());
    }

    private GameManager() {
    }

    private static void kind(GameKind k) {
        KINDS.put(k.id, k);
    }

    // --- данные ---------------------------------------------------------------------------------------

    public static void onServerStart() {
        PRESETS.clear();
        SESSIONS.clear();
        PENDING.clear();
        Data d = JsonUtil.readType(DataStore.file("games.json"), DATA, new Data());
        if (d != null && d.presets != null) {
            for (GamePreset p : d.presets) {
                if (p != null && p.id != null && !p.id.isEmpty() && KINDS.containsKey(p.kind)) {
                    p.normalize();
                    PRESETS.put(p.id, p);
                }
            }
        }
        // встроенные — всегда есть
        for (GameKind k : KINDS.values()) {
            if (!PRESETS.containsKey(k.id)) {
                GamePreset p = k.defaults();
                p.normalize();
                PRESETS.put(p.id, p);
            }
        }
        if (d != null && d.cleanup != null) {
            for (int[] b : d.cleanup) {
                if (b != null && b.length == 6) {
                    GameArena.clear(b[0], b[1], b[2], b[3], b[4], b[5]);
                }
            }
        }
        save();
    }

    public static void save() {
        Data d = new Data();
        d.presets.addAll(PRESETS.values());
        d.cleanup.addAll(PENDING);
        JsonUtil.write(DataStore.file("games.json"), d);
    }

    public static void onServerStop() {
        for (Session s : new ArrayList<Session>(SESSIONS.values())) {
            abort(s, "&eСервер останавливается — игра прервана, ставки вернулись.");
        }
    }

    public static List<GamePreset> presets() {
        return new ArrayList<GamePreset>(PRESETS.values());
    }

    @Nullable
    public static GamePreset preset(String id) {
        return PRESETS.get(id);
    }

    public static boolean playable(GamePreset p) {
        return ExtraConfig.gamesEnabled && p.enabled && !ExtraConfig.gameDisabled(p.id) && !ExtraConfig.gameDisabled(p.kind);
    }

    /** Новая своя игра на основе вида. */
    public static GamePreset newPreset(String kind, String title, EntityPlayerMP by) {
        GameKind k = KINDS.get(kind);
        GamePreset p = k.defaults();
        p.builtin = false;
        String base = "custom_" + title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9а-яё_]", "_");
        String id = base;
        int n = 2;
        while (PRESETS.containsKey(id)) {
            id = base + "_" + n++;
        }
        p.id = id;
        p.title = title;
        p.creator = by.getName();
        p.normalize();
        PRESETS.put(id, p);
        save();
        return p;
    }

    public static void removePreset(GamePreset p) {
        if (!p.builtin) {
            PRESETS.remove(p.id);
            save();
        }
    }

    // --- лобби ------------------------------------------------------------------------------------------

    public static List<Session> sessions() {
        return new ArrayList<Session>(SESSIONS.values());
    }

    @Nullable
    public static Session session(int id) {
        return SESSIONS.get(id);
    }

    @Nullable
    public static Session sessionOf(UUID player) {
        for (Session s : SESSIONS.values()) {
            if (s.players.contains(player)) {
                return s;
            }
        }
        return null;
    }

    /** Создать лобби. Ошибка или null. */
    @Nullable
    public static String create(EntityPlayerMP p, GamePreset preset) {
        if (!playable(preset) && !Perms.isAdmin(p)) {
            return "&cЭта мини-игра выключена.";
        }
        if (sessionOf(p.getUniqueID()) != null) {
            return "&eТы уже в игре или лобби — выйди из него.";
        }
        if (com.ecoserv.minigame.RaceManager.of(p.getUniqueID()) != null) {
            return "&eТы в лобби квестовой гонки — выйди из него.";
        }
        Session s = new Session(nextId++, preset, p.getUniqueID());
        SESSIONS.put(s.id, s);
        String err = join(p, s);
        if (err != null) {
            SESSIONS.remove(s.id);
        }
        return err;
    }

    @Nullable
    public static String join(EntityPlayerMP p, Session s) {
        if (s.state != State.LOBBY) {
            return "&eИгра уже началась.";
        }
        if (s.players.contains(p.getUniqueID())) {
            return null;
        }
        if (sessionOf(p.getUniqueID()) != null) {
            return "&eТы уже в другом лобби.";
        }
        int max = Math.min(s.preset.maxPlayers, ExtraConfig.gamesMaxPlayers);
        if (s.players.size() >= max) {
            return "&cМест нет (" + max + ").";
        }
        long stake = Math.min(s.preset.stake, ExtraConfig.gamesStakeMax <= 0 ? 0 : ExtraConfig.gamesStakeMax);
        if (stake > 0) {
            MoneyNote.set("Мини-игра «" + ChatUtil.strip(ChatUtil.color(s.preset.title)) + "»: ставка");
            boolean ok;
            try {
                ok = EconomyManager.take(p.getUniqueID(), stake);
            } finally {
                MoneyNote.clear();
            }
            if (!ok) {
                return "&cСтавка &6" + Fmt.money(stake) + "&c — не хватает денег.";
            }
            s.paid.put(p.getUniqueID(), stake);
            s.pot += stake;
        }
        s.players.add(p.getUniqueID());
        tell(s, "&a" + p.getName() + " &7в лобби «" + s.preset.title + "&7» (" + s.players.size() + ")");
        return null;
    }

    public static void leave(EntityPlayerMP p) {
        leave(p.getUniqueID());
    }

    public static void leave(UUID id) {
        Session s = sessionOf(id);
        if (s == null) {
            return;
        }
        if (s.state == State.LOBBY || s.state == State.COUNTDOWN) {
            s.players.remove(id);
            s.ready.remove(id);
            Long paid = s.paid.remove(id);
            if (paid != null && paid > 0) {
                s.pot -= paid;
                EconomyManager.give(id, paid);
            }
            if (s.state == State.COUNTDOWN) {
                s.state = State.LOBBY;
                tell(s, "&eОтсчёт остановлен: игрок вышел.");
            }
            if (s.players.isEmpty()) {
                SESSIONS.remove(s.id);
            }
            return;
        }
        if (s.game != null) {
            s.game.leave(id);
        }
        s.players.remove(id);
        EntityPlayerMP p = PlayerUtil.online(id);
        if (p != null) {
            exitArena(p);
        }
    }

    /** Хозяин лобби запускает игру. */
    @Nullable
    public static String start(EntityPlayerMP host, Session s) {
        if (!s.host.equals(host.getUniqueID()) && !Perms.isAdmin(host)) {
            return "&cЗапускает тот, кто создал лобби.";
        }
        if (s.state != State.LOBBY) {
            return "&eУже запущено.";
        }
        if (s.players.size() < s.preset.minPlayers) {
            return "&cНужно игроков: " + s.preset.minPlayers;
        }
        GameKind k = s.kind();
        if (k == null) {
            return "&cВид игры не найден.";
        }
        if (k.arena && GameArena.dim(true) == null) {
            return "&cНет мира арен (включи games.auto_world или создай подмир «" + GameArena.SUBWORLD + "»).";
        }
        s.state = State.COUNTDOWN;
        s.startAt = System.currentTimeMillis() + COUNTDOWN * 1000L;
        return null;
    }

    // --- ход игры ---------------------------------------------------------------------------------------

    public static void tick() {
        GameArena.tick();
        if (SESSIONS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Session s : new ArrayList<Session>(SESSIONS.values())) {
            try {
                if (s.state == State.LOBBY) {
                    if (now - s.created > 30 * 60_000L) {
                        abort(s, "&7Лобби закрыто: игра так и не началась.");
                    }
                    continue;
                }
                if (s.state == State.COUNTDOWN) {
                    long left = (s.startAt - now + 999) / 1000;
                    if (left > 0) {
                        if ((s.startAt - now) % 1000 > 950) {
                            for (EntityPlayerMP p : players(s)) {
                                Game.title(p, "&e" + left, "&7" + s.preset.title);
                                ModSounds.play(p, ModSounds.GAME_COUNTDOWN, 1.0F, 1.0F + (5 - left) * 0.1F);
                            }
                        }
                        continue;
                    }
                    launch(s);
                    continue;
                }
                Game g = s.game;
                if (g == null) {
                    abort(s, "&cИгра сломалась.");
                    continue;
                }
                g.tick();
                if (g.isOver()) {
                    finish(s);
                }
            } catch (Throwable t) {
                EcoServ.LOG.error("Мини-игра #{} ({}) упала", s.id, s.preset.id, t);
                abort(s, "&cОшибка в игре — она остановлена, ставки вернулись.");
            }
        }
    }

    private static void launch(Session s) {
        GameKind k = s.kind();
        s.slot = GameArena.takeSlot();
        Game g = k.create(s);
        s.game = g;
        if (!g.prepare()) {
            abort(s, "&cНе удалось подготовить игру.");
            return;
        }
        s.state = State.RUNNING;
        for (EntityPlayerMP p : players(s)) {
            p.closeScreen();
            if (k.arena) {
                // на арену — с пустым инвентарём
                InventoryGroups.dropGroup(p.getUniqueID(), InventoryGroups.RACE);
            }
            ModSounds.play(p, ModSounds.GAME_START, 1.0F, 1.0F);
        }
        g.begin();
        s.preset.played++;
        save();
        tell(s, "&6Игра «" + s.preset.title + "&6» началась!");
    }

    private static void finish(Session s) {
        Game g = s.game;
        List<Game.Result> res = g == null ? new ArrayList<Game.Result>() : g.results();
        res.sort((a, b) -> {
            if ((a.place > 0) != (b.place > 0)) {
                return a.place > 0 ? -1 : 1;
            }
            if (a.place > 0) {
                return Integer.compare(a.place, b.place);
            }
            return Double.compare(b.score, a.score);
        });
        StringBuilder sb = new StringBuilder("&6Итоги «" + s.preset.title + "&6»:");
        int i = 1;
        for (Game.Result r : res) {
            sb.append("\n&e").append(i++).append(". &f").append(r.name)
                    .append(r.place > 0 ? " &a✔" : r.out ? " &c✘" : "").append(r.note.isEmpty() ? "" : " &7" + r.note);
        }
        tell(s, sb.toString());
        payout(s, res);
        // 2.0: условие квестов «победить в мини-игре» и событие для сценариев
        for (Game.Result r : res) {
            EntityPlayerMP p = PlayerUtil.online(r.id);
            if (p != null && r.place == 1) {
                com.ecoserv.quest.QuestManager.event(p, "game", s.preset.kind, 1);
                if (!s.preset.id.equals(s.preset.kind)) {
                    com.ecoserv.quest.QuestManager.event(p, "game", s.preset.id, 1);
                }
                com.ecoserv.dev.DevEvents.fire("game_win", p, s.preset.id);
            }
        }
        close(s);
    }

    /** Банк: 1 место — 60%, 2 — 30%, 3 — 10% (доли пересчитываются на тех, кто занял места). */
    private static void payout(Session s, List<Game.Result> res) {
        List<Game.Result> placed = new ArrayList<Game.Result>();
        int firsts = 0;
        for (Game.Result r : res) {
            if (r.place == 1) {
                firsts++;
            }
        }
        boolean coop = firsts > 1;
        for (Game.Result r : res) {
            if (r.place > 0 && (coop ? r.place == 1 : placed.size() < 3)) {
                placed.add(r);
            }
        }
        if (s.pot > 0) {
            if (placed.isEmpty() || s.paid.size() <= 1) {
                refund(s, "Мини-игра: возврат ставки");
            } else {
                double[] share = coop ? new double[placed.size()] : new double[]{0.6, 0.3, 0.1};
                if (coop) {
                    java.util.Arrays.fill(share, 1.0D);
                }
                double sum = 0;
                for (int i = 0; i < placed.size(); i++) {
                    sum += share[i];
                }
                long left = s.pot;
                for (int i = 0; i < placed.size(); i++) {
                    long win = i == placed.size() - 1 ? left : (long) Math.floor(s.pot * share[i] / sum);
                    left -= win;
                    give(placed.get(i).id, win, "Мини-игра «" + ChatUtil.strip(ChatUtil.color(s.preset.title)) + "»: выигрыш");
                }
                s.pot = 0;
            }
        }
        if (s.preset.reward > 0 && !placed.isEmpty()) {
            // в командной игре приз получает каждый, кто справился
            for (Game.Result r : coop ? placed : placed.subList(0, 1)) {
                give(r.id, s.preset.reward, "Мини-игра «" + ChatUtil.strip(ChatUtil.color(s.preset.title)) + "»: награда");
            }
        }
    }

    private static void give(UUID id, long amount, String note) {
        if (amount <= 0) {
            return;
        }
        MoneyNote.set(note);
        try {
            EconomyManager.give(id, amount);
        } finally {
            MoneyNote.clear();
        }
        EntityPlayerMP p = PlayerUtil.online(id);
        if (p != null) {
            ChatUtil.send(p, "&a+" + Fmt.money(amount) + " &7(" + note + ")");
            ModSounds.play(p, ModSounds.COIN, 1.0F, 1.0F);
        }
    }

    private static void refund(Session s, String note) {
        for (Map.Entry<UUID, Long> e : s.paid.entrySet()) {
            give(e.getKey(), e.getValue(), note);
        }
        s.paid.clear();
        s.pot = 0;
    }

    public static void abort(Session s, String message) {
        tell(s, message);
        refund(s, "Мини-игра: возврат ставки");
        close(s);
    }

    private static void close(Session s) {
        SESSIONS.remove(s.id);
        if (s.game != null) {
            try {
                s.game.cleanup();
            } catch (Throwable t) {
                EcoServ.LOG.warn("Уборка мини-игры #{}: {}", s.id, t.toString());
            }
        }
        for (EntityPlayerMP p : players(s)) {
            exitArena(p);
        }
        if (s.slot > 0) {
            GameArena.freeSlot(s.slot);
        }
    }

    // --- арена: вход и выход -------------------------------------------------------------------------

    /** Запомнить, кем был игрок, и включить приключение. */
    public static void enterMode(EntityPlayerMP p) {
        NBTTagCompound tag = persisted(p);
        if (!tag.hasKey("ecoGamePrevMode")) {
            tag.setInteger("ecoGamePrevMode", p.interactionManager.getGameType().getID());
        }
        p.setGameType(GameType.ADVENTURE);
        p.capabilities.allowFlying = false;
        p.capabilities.isFlying = false;
        p.sendPlayerAbilities();
    }

    /** Вернуть режим игры, если игрок был в мини-игре. */
    public static void restoreMode(EntityPlayerMP p) {
        NBTTagCompound tag = persisted(p);
        if (tag.hasKey("ecoGamePrevMode")) {
            GameType t = GameType.getByID(tag.getInteger("ecoGamePrevMode"));
            tag.removeTag("ecoGamePrevMode");
            p.setGameType(t == GameType.NOT_SET ? GameType.SURVIVAL : t);
            p.sendPlayerAbilities();
        }
    }

    private static NBTTagCompound persisted(EntityPlayerMP p) {
        NBTTagCompound data = p.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    /** Вынести игрока из арены туда, откуда пришёл. */
    public static void exitArena(EntityPlayerMP p) {
        p.closeScreen();
        if (GameArena.isArenaDim(p.dimension)) {
            com.ecoserv.subworld.SubworldTeleport.leave(p);
        }
        restoreMode(p);
        p.extinguish();
        p.setHealth(p.getMaxHealth());
    }

    /** Арена этой игры: запомнить, что стереть, если сервер упадёт. */
    public static void track(int[] bounds) {
        PENDING.add(bounds);
        save();
    }

    public static void untrack(int[] bounds) {
        PENDING.remove(bounds);
        GameArena.clear(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
        save();
    }

    public static List<EntityPlayerMP> players(Session s) {
        List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
        for (UUID id : s.players) {
            EntityPlayerMP p = PlayerUtil.online(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    public static void tell(Session s, String text) {
        for (EntityPlayerMP p : players(s)) {
            ChatUtil.send(p, text);
        }
    }

    /** Играет ли игрок сейчас в арене (для событий). */
    @Nullable
    public static Game running(UUID player) {
        Session s = sessionOf(player);
        return s != null && s.state == State.RUNNING ? s.game : null;
    }
}
