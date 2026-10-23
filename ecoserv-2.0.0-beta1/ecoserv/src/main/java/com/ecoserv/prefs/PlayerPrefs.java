package com.ecoserv.prefs;

import com.ecoserv.network.packet.PacketClientPrefs;
import net.minecraft.entity.player.EntityPlayerMP;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Клиентские настройки игроков, важные серверу (1.9.0): кто может присылать обмен, запросы денег,
 * приглашения и запросы компьютеров. Живут, пока игрок в сети (клиент присылает их при входе и при смене).
 */
public final class PlayerPrefs {

    public enum Kind {
        TRADE, MONEY, INVITE
    }

    private static final Map<UUID, PacketClientPrefs> PREFS = new HashMap<UUID, PacketClientPrefs>();

    private PlayerPrefs() {
    }

    public static void set(EntityPlayerMP player, PacketClientPrefs prefs) {
        PREFS.put(player.getUniqueID(), prefs);
    }

    public static void forget(UUID player) {
        PREFS.remove(player);
    }

    @Nullable
    public static PacketClientPrefs get(UUID player) {
        return PREFS.get(player);
    }

    /**
     * Можно ли from прислать игроку to запрос такого вида. Админам — всегда. «team» — только из своей команды
     * (одна команда подмиров) или друзья по роли нет — только команда.
     */
    public static boolean accepts(UUID to, @Nullable EntityPlayerMP from, Kind kind) {
        PacketClientPrefs p = PREFS.get(to);
        if (p == null || from == null) {
            return true;
        }
        if (com.ecoserv.util.Perms.isAdmin(from)) {
            return true;
        }
        String mode = kind == Kind.TRADE ? p.trade : kind == Kind.MONEY ? p.money : p.invites;
        if ("none".equals(mode)) {
            return false;
        }
        if ("team".equals(mode)) {
            return sameTeam(to, from.getUniqueID());
        }
        return true;
    }

    public static boolean computers(UUID player) {
        PacketClientPrefs p = PREFS.get(player);
        return p == null || p.computers;
    }

    /** Язык клиента (en_us, ru_ru…), null — неизвестен. */
    @Nullable
    public static String locale(UUID player) {
        PacketClientPrefs p = PREFS.get(player);
        return p == null || p.lang == null || p.lang.isEmpty() ? null : p.lang;
    }

    public static boolean flashes(UUID player) {
        PacketClientPrefs p = PREFS.get(player);
        return p == null || p.flashes;
    }

    private static boolean sameTeam(UUID a, UUID b) {
        try {
            String ka = com.ecoserv.subworld.WorldCrews.keyOf(a);
            return ka != null && ka.equals(com.ecoserv.subworld.WorldCrews.keyOf(b));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Текст отказа для того, кто пытался прислать запрос. */
    public static String refusal(String name, Kind kind) {
        String what = kind == Kind.TRADE ? "обмен" : kind == Kind.MONEY ? "денежные запросы" : "приглашения";
        return "&c" + name + " не принимает " + what + " (клиентские настройки игрока).";
    }
}
