package com.ecoserv.games;

import com.ecoserv.init.ModSounds;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Идущая мини-игра (1.9.0). Конкретные игры — DeathrunGame, ChaseGame, PressureGame. */
public abstract class Game {

    /** Итог игрока. */
    public static final class Result {
        public final UUID id;
        public final String name;
        /** 1, 2, 3… (0 — не занял места / проиграл). */
        public int place;
        /** Очки/прогресс для сортировки, если места не определились. */
        public double score;
        public boolean out;
        public String note = "";

        Result(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    protected final GameManager.Session session;
    protected final GamePreset preset;
    protected final Map<UUID, Result> results = new LinkedHashMap<UUID, Result>();
    protected int ticks;
    protected boolean over;
    private int nextPlace = 1;

    protected Game(GameManager.Session session) {
        this.session = session;
        this.preset = session.preset;
        for (UUID id : session.players) {
            results.put(id, new Result(id, PlayerUtil.nameOf(id)));
        }
    }

    /** Подготовить игру (построить трассу). false — не получилось (игра отменяется). */
    public abstract boolean prepare();

    /** Перенести игроков к старту (после отсчёта). */
    public abstract void begin();

    /** Каждый тик сервера. */
    public abstract void tick();

    /** Игрок вышел (отключился, /games leave). */
    public void leave(UUID player) {
        Result r = results.get(player);
        if (r != null && r.place == 0) {
            r.out = true;
            r.note = "вышел";
        }
        checkEnd();
    }

    /** Убрать за собой (трасса, предметы). */
    public abstract void cleanup();

    /** Строка состояния для меню. */
    public abstract String status();

    /** Окно игры (для игр в окне) — null, если игра идёт в мире. */
    @Nullable
    public com.ecoserv.ui.UiScreen screen(EntityPlayerMP player) {
        return null;
    }

    // --- общее ----------------------------------------------------------------------------------------

    public boolean isOver() {
        return over;
    }

    public List<Result> results() {
        return new ArrayList<Result>(results.values());
    }

    protected List<EntityPlayerMP> online() {
        List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
        for (UUID id : results.keySet()) {
            EntityPlayerMP p = PlayerUtil.online(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    protected List<EntityPlayerMP> active() {
        List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
        for (Result r : results.values()) {
            if (r.place == 0 && !r.out) {
                EntityPlayerMP p = PlayerUtil.online(r.id);
                if (p != null) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** Игрок финишировал — следующее место. */
    protected void finish(UUID id, String note) {
        Result r = results.get(id);
        if (r == null || r.place != 0 || r.out) {
            return;
        }
        r.place = nextPlace++;
        r.note = note;
        EntityPlayerMP p = PlayerUtil.online(id);
        tell("&a" + r.name + " &fзанял &e" + r.place + " место" + (note.isEmpty() ? "" : " &7(" + note + ")"));
        if (p != null) {
            ModSounds.play(p, ModSounds.GAME_WIN, 1.0F, 1.0F);
            title(p, "&a" + r.place + " место!", note.isEmpty() ? "&7Отлично" : "&7" + note);
        }
        checkEnd();
    }

    /** Игрок выбыл (пойман, взорвался бак…). */
    protected void out(UUID id, String note) {
        Result r = results.get(id);
        if (r == null || r.place != 0 || r.out) {
            return;
        }
        r.out = true;
        r.note = note;
        EntityPlayerMP p = PlayerUtil.online(id);
        tell("&c" + r.name + " выбыл &7(" + note + ")");
        if (p != null) {
            ModSounds.play(p, ModSounds.GAME_LOSE, 1.0F, 1.0F);
            title(p, "&cВыбыл", "&7" + note);
        }
        checkEnd();
    }

    /** Все финишировали или выбыли — конец. */
    protected void checkEnd() {
        for (Result r : results.values()) {
            if (r.place == 0 && !r.out) {
                return;
            }
        }
        over = true;
    }

    protected void end() {
        over = true;
    }

    protected void tell(String text) {
        for (EntityPlayerMP p : online()) {
            ChatUtil.send(p, text);
        }
    }

    protected void bar(String text) {
        for (EntityPlayerMP p : online()) {
            ChatUtil.actionBar(p, text);
        }
    }

    public static void title(EntityPlayerMP p, String title, String sub) {
        p.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TIMES, null, 5, 40, 10));
        p.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.SUBTITLE, new TextComponentString(ChatUtil.color(ChatUtil.local(p, sub)))));
        p.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TITLE, new TextComponentString(ChatUtil.color(ChatUtil.local(p, title)))));
    }

    protected int param(String key, int def) {
        return preset.param(key, def);
    }

    protected static String time(int ticks) {
        int s = Math.max(0, ticks / 20);
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
