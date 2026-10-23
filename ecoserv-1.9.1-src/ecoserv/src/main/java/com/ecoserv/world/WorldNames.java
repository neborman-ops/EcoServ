package com.ecoserv.world;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import net.minecraft.world.DimensionType;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Имена миров (измерений) для /world и запретов предметов.
 * <p>
 * Мир можно назвать: именем из конфига (worlds.aliases, например «nether=-1»),
 * названием измерения из мода («twilight_forest») или просто номером («-1»).
 */
public final class WorldNames {

    private static final Map<String, Integer> ALIASES = new LinkedHashMap<String, Integer>();

    private WorldNames() {
    }

    /** Перечитать псевдонимы из конфига. */
    public static void reload() {
        ALIASES.clear();
        String[] lines = EcoConfig.worldAliases == null ? new String[0] : EcoConfig.worldAliases;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = normalize(line.substring(0, eq));
            try {
                ALIASES.put(name, Integer.parseInt(line.substring(eq + 1).trim()));
            } catch (NumberFormatException e) {
                EcoServ.LOG.warn("worlds.aliases: не число в строке '{}'", line);
            }
        }
    }

    public static String normalize(@Nullable String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /** Номер измерения по имени или null, если такого мира нет. */
    @Nullable
    public static Integer resolve(@Nullable String name) {
        String n = normalize(name);
        if (n.isEmpty()) {
            return null;
        }
        Integer alias = ALIASES.get(n);
        if (alias != null) {
            return alias;
        }
        // номер измерения — всегда (миры модов вроде RFTools могут появиться позже)
        try {
            return Integer.parseInt(n);
        } catch (NumberFormatException ignored) {
            // не число — ищем по названию
        }
        int[] dims = registeredDims();
        for (int i = 0; i < dims.length; i++) {
            if (uniqueTypeName(dims[i], dims).equals(n)) {
                return dims[i];
            }
        }
        if ("overworld".equals(n) || "world".equals(n)) {
            return 0;
        }
        if ("nether".equals(n) || "the_nether".equals(n)) {
            return -1;
        }
        if ("end".equals(n) || "the_end".equals(n)) {
            return 1;
        }
        return null;
    }

    /**
     * Самое удобное имя мира: псевдоним из конфига, иначе название измерения,
     * а если такое название у нескольких измерений (RFTools, Mystcraft) — номер.
     */
    public static String nameOf(int dim) {
        for (Map.Entry<String, Integer> e : ALIASES.entrySet()) {
            if (e.getValue() == dim) {
                return e.getKey();
            }
        }
        String type = uniqueTypeName(dim, registeredDims());
        return type.isEmpty() ? Integer.toString(dim) : type;
    }

    /** Название измерения, только если оно не повторяется у других измерений. */
    private static String uniqueTypeName(int dim, int[] all) {
        String type = typeName(dim);
        if (type.isEmpty()) {
            return "";
        }
        for (int i = 0; i < all.length; i++) {
            if (all[i] != dim && type.equals(typeName(all[i]))) {
                return "";
            }
        }
        return type;
    }

    /** Название измерения из мода (нормализованное), "" если неизвестно. */
    public static String typeName(int dim) {
        try {
            DimensionType type = DimensionManager.getProviderType(dim);
            return type == null ? "" : normalize(type.getName());
        } catch (Throwable t) {
            return "";
        }
    }

    public static int[] registeredDims() {
        Integer[] ids;
        try {
            ids = DimensionManager.getStaticDimensionIDs();
        } catch (Throwable t) {
            return new int[]{0, -1, 1};
        }
        int[] out = new int[ids.length];
        for (int i = 0; i < ids.length; i++) {
            out[i] = ids[i];
        }
        Arrays.sort(out);
        return out;
    }

    public static boolean exists(int dim) {
        try {
            return DimensionManager.isDimensionRegistered(dim);
        } catch (Throwable t) {
            return false;
        }
    }

    // --- группы миров (подмир = шаблон + локальные миры команд под одним именем) ---

    /** Источник групп: например, менеджер подмиров. */
    public interface GroupResolver {
        /** Имя группы, в которую входит измерение, или null. */
        @Nullable
        String groupOf(int dim);

        /** Все имена групп. */
        java.util.Collection<String> groups();
    }

    private static final List<GroupResolver> GROUPS = new java.util.concurrent.CopyOnWriteArrayList<GroupResolver>();

    public static void registerGroups(GroupResolver resolver) {
        GROUPS.add(resolver);
    }

    /** Имя группы измерения (нормализованное) или null. */
    @Nullable
    public static String groupOf(int dim) {
        for (GroupResolver r : GROUPS) {
            String g = r.groupOf(dim);
            if (g != null && !g.isEmpty()) {
                return normalize(g);
            }
        }
        return null;
    }

    public static boolean isGroup(@Nullable String name) {
        String n = normalize(name);
        if (n.isEmpty()) {
            return false;
        }
        for (GroupResolver r : GROUPS) {
            for (String g : r.groups()) {
                if (normalize(g).equals(n)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Входит ли измерение в одну из групп из списка имён. */
    public static boolean inGroups(int dim, @Nullable java.util.Collection<String> names) {
        if (names == null || names.isEmpty() || GROUPS.isEmpty()) {
            return false;
        }
        String g = groupOf(dim);
        if (g == null) {
            return false;
        }
        for (String name : names) {
            if (g.equals(normalize(name))) {
                return true;
            }
        }
        return false;
    }

    /** Подходит ли измерение под имя мира: псевдоним/номер/тип или группа (подмир). */
    public static boolean matches(int dim, @Nullable String name) {
        Integer resolved = resolve(name);
        if (resolved != null && resolved == dim) {
            return true;
        }
        String g = groupOf(dim);
        return g != null && g.equals(normalize(name));
    }

    public static boolean matchesAny(int dim, @Nullable java.util.Collection<String> names) {
        if (names == null) {
            return false;
        }
        for (String name : names) {
            if (matches(dim, name)) {
                return true;
            }
        }
        return false;
    }

    /** Имя мира для людей: у измерений подмира — имя подмира. */
    public static String displayName(int dim) {
        String g = groupOf(dim);
        return g != null ? g : nameOf(dim);
    }

    /**
     * Все варианты для выбора мира в меню: обычные измерения (кроме входящих в группы)
     * и имена групп (подмиров).
     */
    public static List<String> worldChoices() {
        List<String> out = new ArrayList<String>();
        for (Integer dim : allDims()) {
            if (groupOf(dim) == null) {
                out.add(nameOf(dim));
            }
        }
        for (GroupResolver r : GROUPS) {
            for (String g : r.groups()) {
                String n = normalize(g);
                if (!n.isEmpty() && !out.contains(n)) {
                    out.add(n);
                }
            }
        }
        return out;
    }

    /** Все известные миры: сначала псевдонимы из конфига, потом остальные измерения. */
    public static List<Integer> allDims() {
        Set<Integer> set = new LinkedHashSet<Integer>();
        for (Integer dim : ALIASES.values()) {
            if (exists(dim)) {
                set.add(dim);
            }
        }
        int[] dims = registeredDims();
        for (int i = 0; i < dims.length; i++) {
            set.add(dims[i]);
        }
        return new ArrayList<Integer>(set);
    }

    /**
     * Может ли обычный игрок телепортироваться в этот мир командой /world.
     * Админам можно всегда (проверяется в команде).
     */
    public static boolean allowedForPlayers(int dim) {
        String[] list = EcoConfig.worldPlayerWorlds == null ? new String[0] : EcoConfig.worldPlayerWorlds;
        for (int i = 0; i < list.length; i++) {
            String entry = normalize(list[i]);
            if ("*".equals(entry)) {
                return true;
            }
            Integer resolved = resolve(entry);
            if (resolved != null && resolved == dim) {
                return true;
            }
        }
        return false;
    }

    /** Все имена, по которым можно найти мир (для подсказок Tab). */
    public static List<String> namesFor(int dim) {
        List<String> names = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : ALIASES.entrySet()) {
            if (e.getValue() == dim) {
                names.add(e.getKey());
            }
        }
        String type = uniqueTypeName(dim, registeredDims());
        if (!type.isEmpty() && !names.contains(type)) {
            names.add(type);
        }
        if (names.isEmpty()) {
            names.add(Integer.toString(dim));
        }
        return names;
    }
}
