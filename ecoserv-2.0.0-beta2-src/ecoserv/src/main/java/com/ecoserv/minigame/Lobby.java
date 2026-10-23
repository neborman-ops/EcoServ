package com.ecoserv.minigame;

import com.ecoserv.extra.ExtraConfig;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Лобби «Квестовой гонки»: игроки, их ставки и голоса, настройки создателя, а потом — сама игра. */
public class Lobby {

    public enum Mode {
        NORMAL("Обычный"), TIMED("На время");

        public final String title;

        Mode(String title) {
            this.title = title;
        }
    }

    public enum State {
        WAITING, RUNNING, FINISHED
    }

    public static class Member {
        public UUID id;
        public String name = "";
        public boolean ready;
        public long stake;
        /** Голос за сложность 1–5. */
        public int diffVote = 3;
        /** Голос за время, минут (0 — «авто»). */
        public int timeVote;

        // --- игра ---
        /** Ставка, которую реально списали при старте. */
        public long paid;
        public int step;
        public long lastStepAt;
        public long finishedAt;
        public boolean gaveUp;
        public int place;
        public long won;

        public boolean finished() {
            return finishedAt > 0;
        }
    }

    public final int id;
    public String name = "";
    public UUID host;
    public String password = "";
    public Mode mode = Mode.NORMAL;
    /** Время в режиме «На время»: false — автоматически по оценке, true — голосуют игроки. */
    public boolean timeVote;
    /** 0 — сложность выбирают игроки голосованием, 1–5 — задана создателем. */
    public int difficulty;
    /** Брать задания из FTB Quests (если есть), иначе — цепочки крафта. */
    public boolean useQuests = true;
    public long minStake = ExtraConfig.stakeMin;
    public long maxStake = ExtraConfig.stakeMax;
    public int maxPlayers = Math.min(10, ExtraConfig.minigameMaxPlayers);
    public final List<Member> members = new ArrayList<Member>();
    public State state = State.WAITING;
    public long created = System.currentTimeMillis();
    public long lastActivity = created;

    /** Задание: в лобби — превью (видна только оценка), в игре — настоящее. */
    @Nullable
    public RaceTask task;
    public int taskDifficulty;

    // --- игра ---
    public long startAt;
    /** 0 — без таймера. */
    public long endAt;
    public int minutes;
    public int arenaDim;
    public String arenaSubworld = "";
    public long pot;
    public long finishedAt;
    public long cleanupAt;

    public Lobby(int id) {
        this.id = id;
    }

    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }

    @Nullable
    public Member member(UUID id) {
        for (Member m : members) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    public boolean isHost(UUID id) {
        return host != null && host.equals(id);
    }

    public boolean full() {
        return members.size() >= maxPlayers;
    }

    /** Все, кроме создателя, нажали «Готов». */
    public boolean allReady() {
        for (Member m : members) {
            if (!isHost(m.id) && !m.ready) {
                return false;
            }
        }
        return true;
    }

    /** Сложность по голосам (среднее, округлённое) или заданная. */
    public int effectiveDifficulty() {
        if (difficulty > 0 || members.isEmpty()) {
            return Math.max(1, Math.min(5, difficulty > 0 ? difficulty : 3));
        }
        double sum = 0;
        for (Member m : members) {
            sum += m.diffVote;
        }
        return (int) Math.max(1, Math.min(5, Math.round(sum / members.size())));
    }

    /** Минуты в режиме «На время»: медиана голосов (голос «авто» = оценка). */
    public int effectiveMinutes(int autoMinutes) {
        if (mode != Mode.TIMED) {
            return 0;
        }
        if (!timeVote || members.isEmpty()) {
            return autoMinutes;
        }
        List<Integer> votes = new ArrayList<Integer>();
        for (Member m : members) {
            votes.add(m.timeVote > 0 ? m.timeVote : autoMinutes);
        }
        java.util.Collections.sort(votes);
        return votes.get(votes.size() / 2);
    }

    /** Новые условия — все (кроме создателя) должны снова подтвердить готовность. */
    public void unready() {
        for (Member m : members) {
            if (!isHost(m.id)) {
                m.ready = false;
            }
        }
    }

    public void clampStakes() {
        for (Member m : members) {
            m.stake = Math.max(minStake, Math.min(maxStake, m.stake));
        }
    }

    public void touch() {
        lastActivity = System.currentTimeMillis();
    }
}
