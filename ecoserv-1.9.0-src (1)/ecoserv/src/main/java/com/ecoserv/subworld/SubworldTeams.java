package com.ecoserv.subworld;

import com.ecoserv.EcoServ;
import com.ecoserv.integration.FTBPlayers;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Владельцы локальных миров: команда FTBLib («team:&lt;код&gt;») или сам игрок («player:&lt;uuid&gt;»),
 * если команд нет, игрок не в команде или подмир помечен GLITCH (п. 18 — мир у каждого игрока свой).
 * FTBLib подключается через рефлексию, мод работает и без него.
 */
public final class SubworldTeams {

    private static boolean checked;
    private static Field teamField;
    private static Method hasTeam;
    private static Method getUIDCode;
    private static Method getTitle;
    private static Method getMembers;
    private static Method getId;
    private static Method isOwner;
    private static Method getTeamByString;

    private SubworldTeams() {
    }

    private static void init() {
        if (checked) {
            return;
        }
        checked = true;
        try {
            Class<?> playerClass = Class.forName("com.feed_the_beast.ftblib.lib.data.ForgePlayer");
            Class<?> teamClass = Class.forName("com.feed_the_beast.ftblib.lib.data.ForgeTeam");
            teamField = playerClass.getField("team");
            hasTeam = playerClass.getMethod("hasTeam");
            getId = playerClass.getMethod("getId");
            getUIDCode = teamClass.getMethod("getUIDCode");
            getTitle = teamClass.getMethod("getTitle");
            getMembers = teamClass.getMethod("getMembers");
            isOwner = teamClass.getMethod("isOwner", playerClass);
            Class<?> universeClass = Class.forName("com.feed_the_beast.ftblib.lib.data.Universe");
            getTeamByString = universeClass.getMethod("getTeam", String.class);
        } catch (Throwable t) {
            teamField = null;
            EcoServ.LOG.debug("Команды FTBLib недоступны — локальные миры будут у каждого игрока: {}", t.toString());
        }
    }

    /** Сбросить кеш рефлексии (новый мир в одиночной игре). */
    public static void reset() {
        checked = false;
    }

    @Nullable
    private static Object team(UUID player) {
        init();
        if (teamField == null) {
            return null;
        }
        try {
            Object fp = FTBPlayers.get(player);
            if (fp == null || !(Boolean) hasTeam.invoke(fp)) {
                return null;
            }
            return teamField.get(fp);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Ключ владельца локального мира для игрока в этом подмире: своя команда мода (принят или собрал сам),
     * иначе команда FTB, иначе сам игрок. В подмире GLITCH мир всегда у каждого игрока свой.
     */
    public static String ownerKey(UUID player, Subworld sw) {
        if (!sw.perPlayer()) {
            String crew = WorldCrews.keyOf(player);
            if (crew != null) {
                return crew;
            }
            return baseKey(player);
        }
        return "player:" + player;
    }

    /** Команда FTB или сам игрок — без учёта своей команды мода. */
    public static String baseKey(UUID player) {
        Object team = team(player);
        if (team != null) {
            try {
                return "team:" + getUIDCode.invoke(team);
            } catch (Throwable ignored) {
                // ниже — как для одиночки
            }
        }
        return "player:" + player;
    }

    /** Ключ команды игрока («team:код») или null, если команды нет. */
    @Nullable
    public static String teamKey(UUID player) {
        Object team = team(player);
        if (team == null) {
            return null;
        }
        try {
            return "team:" + getUIDCode.invoke(team);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String ownerKey(EntityPlayerMP player, Subworld sw) {
        return ownerKey(player.getUniqueID(), sw);
    }

    /** Название владельца для людей: название команды или ник. */
    public static String ownerName(String ownerKey) {
        if (ownerKey.startsWith("player:")) {
            try {
                return PlayerUtil.nameOf(UUID.fromString(ownerKey.substring(7)));
            } catch (IllegalArgumentException e) {
                return ownerKey;
            }
        }
        Object team = teamByKey(ownerKey);
        if (team != null) {
            try {
                Object title = getTitle.invoke(team);
                if (title instanceof ITextComponent) {
                    return ((ITextComponent) title).getUnformattedText();
                }
            } catch (Throwable ignored) {
                // ниже
            }
        }
        return ownerKey.startsWith("team:") ? "команда " + ownerKey.substring(5) : ownerKey;
    }

    @Nullable
    private static Object teamByKey(String ownerKey) {
        init();
        if (!ownerKey.startsWith("team:") || getTeamByString == null) {
            return null;
        }
        try {
            Class<?> universeClass = Class.forName("com.feed_the_beast.ftblib.lib.data.Universe");
            Object universe = universeClass.getMethod("get").invoke(null);
            if (universe == null) {
                return null;
            }
            Object team = getTeamByString.invoke(universe, ownerKey.substring(5));
            if (team == null) {
                return null;
            }
            // «нет команды» у FTBLib — отдельный объект с кодом 0000
            Object code = getUIDCode.invoke(team);
            return ownerKey.substring(5).equals(code) ? team : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Участники владельца (uuid): игрок или команда FTB, плюс добавленные в свою команду мода. */
    public static List<UUID> members(String ownerKey) {
        List<UUID> out = new ArrayList<UUID>();
        if (ownerKey.startsWith("player:")) {
            try {
                out.add(UUID.fromString(ownerKey.substring(7)));
            } catch (IllegalArgumentException e) {
                // мусорный ключ
            }
        } else {
            Object team = teamByKey(ownerKey);
            if (team != null) {
                try {
                    for (Object member : (List<?>) getMembers.invoke(team)) {
                        out.add((UUID) getId.invoke(member));
                    }
                } catch (Throwable ignored) {
                    // пусто
                }
            }
        }
        for (UUID extra : WorldCrews.extraMembers(ownerKey)) {
            if (!out.contains(extra)) {
                out.add(extra);
            }
        }
        return out;
    }

    /** Участники команды FTB (без добавленных модом). */
    public static List<UUID> ftbMembers(String ownerKey) {
        List<UUID> out = new ArrayList<UUID>();
        Object team = teamByKey(ownerKey);
        if (team != null) {
            try {
                for (Object member : (List<?>) getMembers.invoke(team)) {
                    out.add((UUID) getId.invoke(member));
                }
            } catch (Throwable ignored) {
                // пусто
            }
        }
        return out;
    }

    /** Игрок — участник владельца локального мира. */
    public static boolean isMember(UUID player, String ownerKey) {
        if (ownerKey.equals("player:" + player) || WorldCrews.isExtraMember(player, ownerKey)) {
            return true;
        }
        return ownerKey.startsWith("team:") && ftbMembers(ownerKey).contains(player);
    }

    /** Глава команды (для одиночки — он сам). Без FTBLib — любой участник. */
    public static boolean isLeader(UUID player, String ownerKey) {
        if (ownerKey.equals("player:" + player) || WorldCrews.isCrewLeader(player, ownerKey)) {
            return true;
        }
        Object team = teamByKey(ownerKey);
        if (team == null) {
            return false;
        }
        try {
            Object fp = FTBPlayers.get(player);
            return fp != null && (Boolean) isOwner.invoke(team, fp);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Есть ли у сервера команды FTBLib. */
    public static boolean available() {
        init();
        return teamField != null && FTBPlayers.isAvailable();
    }
}
