package com.ecoserv.subworld;

import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Своя команда мода для локальных миров — работает и без FTB Utilities, и вместе с ней.
 * Глава добавляет игрока (/world add &lt;ник&gt; или кнопка в меню миров), тот соглашается — и они играют
 * в одних и тех же локальных мирах. Если глава состоит в команде FTB, добавленные игроки присоединяются
 * к мирам этой команды. Файл world/ecoserv/crews.json.
 */
public final class WorldCrews {

    /** Сколько ждёт приглашение. */
    private static final long INVITE_MS = 5 * 60 * 1000L;
    /** Дополнительных участников у одной команды. */
    public static final int MAX_MEMBERS = 24;

    public static class Member {
        public String uuid = "";
        public String name = "";
        public long since;
    }

    public static class Crew {
        /** Владелец локальных миров: «player:&lt;uuid&gt;» или «team:&lt;код FTB&gt;». */
        public String key = "";
        /** Кто собрал команду (может добавлять и выгонять). */
        public String leader = "";
        public String leaderName = "";
        public List<Member> members = new ArrayList<Member>();

        @Nullable
        Member member(UUID id) {
            String s = id.toString();
            for (Member m : members) {
                if (m.uuid.equals(s)) {
                    return m;
                }
            }
            return null;
        }
    }

    /** Ожидающее приглашение. */
    public static final class Invite {
        public final String key;
        public final UUID leader;
        public final String leaderName;
        public final long until;

        Invite(String key, UUID leader, String leaderName) {
            this.key = key;
            this.leader = leader;
            this.leaderName = leaderName;
            this.until = System.currentTimeMillis() + INVITE_MS;
        }
    }

    private static final Type LIST = new TypeToken<ArrayList<Crew>>() {
    }.getType();

    private static List<Crew> crews = new ArrayList<Crew>();
    /** Кого пригласили: игрок -> приглашения (от разных глав). */
    private static final Map<UUID, List<Invite>> INVITES = new HashMap<UUID, List<Invite>>();
    private static boolean loaded;

    private WorldCrews() {
    }

    private static File file() {
        return DataStore.file("crews.json");
    }

    public static void load() {
        crews = JsonUtil.readType(file(), LIST, new ArrayList<Crew>());
        if (crews == null) {
            crews = new ArrayList<Crew>();
        }
        Iterator<Crew> it = crews.iterator();
        while (it.hasNext()) {
            Crew c = it.next();
            if (c == null || c.key == null || c.key.isEmpty()) {
                it.remove();
                continue;
            }
            if (c.members == null) {
                c.members = new ArrayList<Member>();
            }
            if (c.leader == null) {
                c.leader = "";
            }
            if (c.leaderName == null) {
                c.leaderName = "";
            }
        }
        INVITES.clear();
        loaded = true;
    }

    public static void save() {
        if (loaded) {
            JsonUtil.write(file(), crews);
        }
    }

    // --- кто в какой команде --------------------------------------------------------------

    @Nullable
    private static Crew byKey(String key) {
        for (Crew c : crews) {
            if (c.key.equals(key)) {
                return c;
            }
        }
        return null;
    }

    @Nullable
    private static Crew memberCrew(UUID player) {
        for (Crew c : crews) {
            if (c.member(player) != null) {
                return c;
            }
        }
        return null;
    }

    @Nullable
    private static Crew ledBy(UUID player) {
        String s = player.toString();
        for (Crew c : crews) {
            if (c.leader.equals(s)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Ключ владельца локальных миров по своей команде мода: игрок принят в чью-то команду или сам собрал
     * команду. null — своей команды нет (дальше — команда FTB или сам игрок).
     */
    @Nullable
    public static String keyOf(UUID player) {
        Crew c = memberCrew(player);
        if (c == null) {
            c = ledBy(player);
        }
        return c == null ? null : c.key;
    }

    /** Добавленные участники (без участников команды FTB и без главы-одиночки). */
    public static List<UUID> extraMembers(String key) {
        List<UUID> out = new ArrayList<UUID>();
        Crew c = byKey(key);
        if (c != null) {
            for (Member m : c.members) {
                try {
                    out.add(UUID.fromString(m.uuid));
                } catch (IllegalArgumentException ignored) {
                    // мусорная запись
                }
            }
        }
        return out;
    }

    public static boolean isExtraMember(UUID player, String key) {
        Crew c = byKey(key);
        return c != null && c.member(player) != null;
    }

    /** Глава своей команды мода для этого ключа. */
    public static boolean isCrewLeader(UUID player, String key) {
        Crew c = byKey(key);
        return c != null && c.leader.equals(player.toString());
    }

    @Nullable
    public static String leaderName(String key) {
        Crew c = byKey(key);
        return c == null || c.leaderName.isEmpty() ? null : c.leaderName;
    }

    /** Может ли игрок добавлять и выгонять участников этого владельца. */
    public static boolean canManage(EntityPlayerMP player, String key) {
        if (Perms.isAdmin(player)) {
            return true;
        }
        UUID id = player.getUniqueID();
        if (key.equals("player:" + id) || isCrewLeader(id, key)) {
            return true;
        }
        return key.startsWith("team:") && SubworldTeams.isLeader(id, key);
    }

    /** Ключ, к которому игрок будет добавлять людей: его нынешние локальные миры. */
    public static String manageKey(EntityPlayerMP player) {
        String own = keyOf(player.getUniqueID());
        return own != null ? own : SubworldTeams.baseKey(player.getUniqueID());
    }

    // --- приглашения ----------------------------------------------------------------------------

    public static List<Invite> invitesFor(UUID player) {
        List<Invite> list = INVITES.get(player);
        List<Invite> out = new ArrayList<Invite>();
        if (list == null) {
            return out;
        }
        long now = System.currentTimeMillis();
        list.removeIf(i -> i.until < now);
        out.addAll(list);
        return out;
    }

    /** Пригласить игрока в свою команду. Возвращает ошибку или null. */
    @Nullable
    public static String invite(EntityPlayerMP leader, UUID target) {
        String key = manageKey(leader);
        if (!canManage(leader, key)) {
            Crew mine = memberCrew(leader.getUniqueID());
            return "&cДобавлять людей может только глава команды" + (mine != null && !mine.leaderName.isEmpty()
                    ? " (&f" + mine.leaderName + "&c)" : "") + ".";
        }
        if (target.equals(leader.getUniqueID())) {
            return "&eСебя добавлять не нужно.";
        }
        if (!com.ecoserv.prefs.PlayerPrefs.accepts(target, leader, com.ecoserv.prefs.PlayerPrefs.Kind.INVITE)) {
            return com.ecoserv.prefs.PlayerPrefs.refusal(PlayerUtil.nameOf(target), com.ecoserv.prefs.PlayerPrefs.Kind.INVITE);
        }
        if (SubworldTeams.isMember(target, key)) {
            return "&e" + PlayerUtil.nameOf(target) + " уже в твоей команде.";
        }
        Crew c = byKey(key);
        if (c != null && c.members.size() >= MAX_MEMBERS) {
            return "&cВ команде уже " + MAX_MEMBERS + " добавленных игроков.";
        }
        List<Invite> list = INVITES.get(target);
        if (list == null) {
            list = new ArrayList<Invite>();
            INVITES.put(target, list);
        }
        list.removeIf(i -> i.key.equals(key));
        list.add(new Invite(key, leader.getUniqueID(), leader.getName()));
        EntityPlayerMP online = PlayerUtil.online(target);
        if (online != null) {
            ChatUtil.send(online, "&b" + leader.getName() + " &aзовёт тебя в свою команду: общие локальные миры подмиров.");
            ITextComponent line = new TextComponentString("");
            line.appendSibling(ChatUtil.link("&a[Принять]", "/world accept " + leader.getName(),
                    "&aВступить в команду " + leader.getName()));
            line.appendSibling(ChatUtil.component(" "));
            line.appendSibling(ChatUtil.link("&c[Отказаться]", "/world deny " + leader.getName(), "&cОтказаться"));
            line.appendSibling(ChatUtil.component(" &7(5 минут; или меню миров → «Команда»)"));
            ChatUtil.send(online, line);
            com.ecoserv.ui.UiManager.refreshAll(com.ecoserv.subworld.menu.WorldsScreen.class);
        }
        return null;
    }

    /** Принять приглашение (from — ник главы; null — единственное или последнее). */
    @Nullable
    public static String accept(EntityPlayerMP player, @Nullable String from) {
        Invite inv = findInvite(player.getUniqueID(), from);
        if (inv == null) {
            return from == null ? "&eПриглашений нет." : "&eПриглашения от &f" + from + " &eнет (или оно истекло).";
        }
        UUID id = player.getUniqueID();
        Crew led = ledBy(id);
        if (led != null && !led.members.isEmpty() && !led.key.equals(inv.key)) {
            return "&cУ тебя своя команда (" + led.members.size() + " чел.) — сначала распусти её: &f/world disband";
        }
        Crew old = memberCrew(id);
        if (old != null && !old.key.equals(inv.key)) {
            removeMember(old, id);
            EntityPlayerMP oldLeader = PlayerUtil.online(safeUuid(old.leader));
            if (oldLeader != null) {
                ChatUtil.send(oldLeader, "&e" + player.getName() + " ушёл из твоей команды.");
            }
        }
        if (led != null && led.members.isEmpty()) {
            crews.remove(led);
        }
        Crew c = byKey(inv.key);
        if (c == null) {
            c = new Crew();
            c.key = inv.key;
            c.leader = inv.leader.toString();
            c.leaderName = inv.leaderName;
            crews.add(c);
        }
        if (c.member(id) == null && !SubworldTeams.isMember(id, inv.key)) {
            Member m = new Member();
            m.uuid = id.toString();
            m.name = player.getName();
            m.since = System.currentTimeMillis();
            c.members.add(m);
        }
        List<Invite> list = INVITES.get(id);
        if (list != null) {
            list.remove(inv);
        }
        save();
        EntityPlayerMP leader = PlayerUtil.online(inv.leader);
        if (leader != null) {
            ChatUtil.send(leader, "&a" + player.getName() + " теперь в твоей команде.");
        }
        com.ecoserv.ui.UiManager.refreshAll(com.ecoserv.subworld.menu.WorldsScreen.class);
        return null;
    }

    @Nullable
    public static String deny(EntityPlayerMP player, @Nullable String from) {
        Invite inv = findInvite(player.getUniqueID(), from);
        if (inv == null) {
            return "&eПриглашений нет.";
        }
        INVITES.get(player.getUniqueID()).remove(inv);
        EntityPlayerMP leader = PlayerUtil.online(inv.leader);
        if (leader != null) {
            ChatUtil.send(leader, "&e" + player.getName() + " отказался вступать в команду.");
        }
        return null;
    }

    @Nullable
    private static Invite findInvite(UUID player, @Nullable String from) {
        List<Invite> list = invitesFor(player);
        if (list.isEmpty()) {
            return null;
        }
        if (from == null || from.isEmpty()) {
            return list.get(list.size() - 1);
        }
        for (Invite i : list) {
            if (i.leaderName.equalsIgnoreCase(from)) {
                return i;
            }
        }
        return null;
    }

    /** Выгнать добавленного участника. Возвращает ошибку или null. */
    @Nullable
    public static String kick(EntityPlayerMP by, String key, UUID target) {
        if (!canManage(by, key)) {
            return "&cВыгонять может только глава команды.";
        }
        Crew c = byKey(key);
        if (c == null || c.member(target) == null) {
            if (SubworldTeams.isMember(target, key) && key.startsWith("team:")) {
                return "&e" + PlayerUtil.nameOf(target) + " в команде FTB — убрать его можно в меню команд FTB.";
            }
            return "&e" + PlayerUtil.nameOf(target) + " не в твоей команде.";
        }
        removeMember(c, target);
        save();
        EntityPlayerMP online = PlayerUtil.online(target);
        if (online != null) {
            ChatUtil.send(online, "&eТебя убрали из команды " + (c.leaderName.isEmpty() ? "" : c.leaderName) + ".");
            evictFrom(online, key);
        }
        com.ecoserv.ui.UiManager.refreshAll(com.ecoserv.subworld.menu.WorldsScreen.class);
        return null;
    }

    /** Уйти из чужой команды. */
    @Nullable
    public static String leave(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        Crew c = memberCrew(id);
        if (c == null) {
            return "&eТы не состоишь в чужой команде мода." + (SubworldTeams.teamKey(id) != null
                    ? " Из команды FTB выходят в меню команд FTB." : "");
        }
        removeMember(c, id);
        save();
        EntityPlayerMP leader = PlayerUtil.online(safeUuid(c.leader));
        if (leader != null) {
            ChatUtil.send(leader, "&e" + player.getName() + " ушёл из твоей команды.");
        }
        evictFrom(player, c.key);
        com.ecoserv.ui.UiManager.refreshAll(com.ecoserv.subworld.menu.WorldsScreen.class);
        return null;
    }

    /** Распустить свою команду (все добавленные выходят). */
    @Nullable
    public static String disband(EntityPlayerMP player) {
        Crew c = ledBy(player.getUniqueID());
        if (c == null) {
            return "&eТы не собирал команду.";
        }
        crews.remove(c);
        save();
        for (Member m : c.members) {
            EntityPlayerMP online = PlayerUtil.online(safeUuid(m.uuid));
            if (online != null) {
                ChatUtil.send(online, "&e" + player.getName() + " распустил команду.");
                evictFrom(online, c.key);
            }
        }
        com.ecoserv.ui.UiManager.refreshAll(com.ecoserv.subworld.menu.WorldsScreen.class);
        return null;
    }

    private static void removeMember(Crew c, UUID id) {
        c.members.removeIf(m -> m.uuid.equals(id.toString()));
        if (c.members.isEmpty()) {
            crews.remove(c);
        }
    }

    /** Игрок больше не участник: если он стоит в локальном мире бывшей команды (и не гость) — наружу. */
    private static void evictFrom(EntityPlayerMP player, String key) {
        SubworldDim d = SubworldRegistry.dim(player.dimension);
        if (d != null && d.isInstance() && d.owner.equals(key) && !Perms.isAdmin(player)
                && !SubworldTeams.isMember(player.getUniqueID(), key)
                && d.guest(player.getUniqueID().toString()) == null) {
            SubworldTeleport.evict(player, "&eТы больше не участник этого локального мира.");
        }
    }

    @Nullable
    private static UUID safeUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
