package com.ecoserv.role;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketRoles;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.PlayerUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Роли игроков. Описания ролей — config/ecoserv/roles.json (общие для всех миров сервера),
 * кому какая роль выдана — world/ecoserv/player_roles.json (можно с датой окончания).
 */
public final class RoleManager {

    /** Выдача роли игроку. until = 0 — навсегда. */
    public static class Assignment {
        public String role = "";
        public long until;
        public String by = "";
        public long at;
    }

    private static final Type ROLE_LIST = new TypeToken<ArrayList<Role>>() {
    }.getType();
    private static final Type ASSIGN_MAP = new TypeToken<HashMap<String, ArrayList<Assignment>>>() {
    }.getType();
    public static final Pattern ID = Pattern.compile("^[a-z0-9_]{1,32}$");

    private static final Map<String, Role> ROLES = new LinkedHashMap<String, Role>();
    private static final Map<UUID, List<Assignment>> ASSIGNED = new HashMap<UUID, List<Assignment>>();
    private static boolean dirty;
    private static int tick;

    /** Роли своего игрока на клиенте (для барьера ролей). Заполняется пакетом с сервера. */
    public static final Set<String> CLIENT_ROLES = Collections.synchronizedSet(new HashSet<String>());
    /** Свой игрок — админ (на клиенте; приходит пакетом состояния). */
    public static volatile boolean clientAdmin;

    private RoleManager() {
    }

    private static File rolesFile() {
        return new File(EcoConfig.getConfigDir(), "roles.json");
    }

    private static File assignFile() {
        return DataStore.file("player_roles.json");
    }

    // --- загрузка / сохранение ------------------------------------------------

    public static void load() {
        ROLES.clear();
        File file = rolesFile();
        if (!file.exists()) {
            List<Role> defaults = new ArrayList<Role>();
            Role vip = new Role("vip", "VIP");
            vip.color = "&6";
            vip.prefix = "&6[VIP]";
            vip.priority = 10;
            vip.xaeroTp = true;
            vip.rewardMultiplier = 1.5D;
            vip.description = "Пример роли: тег в чате, телепорт по карте, x1.5 к наградам";
            defaults.add(vip);
            JsonUtil.write(file, defaults);
        }
        ArrayList<Role> loaded = JsonUtil.readType(file, ROLE_LIST, new ArrayList<Role>());
        for (Role role : loaded) {
            if (role == null || role.id == null) {
                continue;
            }
            role.id = role.id.trim().toLowerCase(Locale.ROOT);
            if (!ID.matcher(role.id).matches()) {
                EcoServ.LOG.warn("Роль с некорректным id пропущена: {}", role.id);
                continue;
            }
            role.normalize();
            ROLES.put(role.id, role);
        }
        loadAssignments();
        EcoServ.LOG.info("Роли: {} описано, выдано {} игрокам", ROLES.size(), ASSIGNED.size());
    }

    private static void loadAssignments() {
        ASSIGNED.clear();
        HashMap<String, ArrayList<Assignment>> raw = JsonUtil.readType(assignFile(), ASSIGN_MAP,
                new HashMap<String, ArrayList<Assignment>>());
        for (Map.Entry<String, ArrayList<Assignment>> e : raw.entrySet()) {
            try {
                UUID id = UUID.fromString(e.getKey());
                List<Assignment> list = new ArrayList<Assignment>();
                if (e.getValue() != null) {
                    for (Assignment a : e.getValue()) {
                        if (a != null && a.role != null && !a.role.isEmpty()) {
                            a.role = a.role.toLowerCase(Locale.ROOT);
                            list.add(a);
                        }
                    }
                }
                if (!list.isEmpty()) {
                    ASSIGNED.put(id, list);
                }
            } catch (IllegalArgumentException ignored) {
                // мусорная запись
            }
        }
        dirty = false;
    }

    public static void saveRoles() {
        JsonUtil.write(rolesFile(), new ArrayList<Role>(ROLES.values()));
    }

    public static void saveIfDirty() {
        if (!dirty) {
            return;
        }
        HashMap<String, List<Assignment>> raw = new HashMap<String, List<Assignment>>();
        for (Map.Entry<UUID, List<Assignment>> e : ASSIGNED.entrySet()) {
            if (!e.getValue().isEmpty()) {
                raw.put(e.getKey().toString(), e.getValue());
            }
        }
        if (JsonUtil.write(assignFile(), raw)) {
            dirty = false;
        }
    }

    /** После правки описаний ролей: сохранить и разослать онлайн-игрокам. */
    public static void changed() {
        saveRoles();
        syncAll();
    }

    // --- описания ролей -------------------------------------------------------

    public static List<Role> all() {
        List<Role> list = new ArrayList<Role>(ROLES.values());
        Collections.sort(list, BY_PRIORITY);
        return list;
    }

    private static final Comparator<Role> BY_PRIORITY = new Comparator<Role>() {
        @Override
        public int compare(Role a, Role b) {
            int c = Integer.compare(b.priority, a.priority);
            return c != 0 ? c : a.id.compareTo(b.id);
        }
    };

    @Nullable
    public static Role get(String id) {
        return id == null ? null : ROLES.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public static List<String> ids() {
        return new ArrayList<String>(ROLES.keySet());
    }

    /** Новая роль. null — id занят или некорректен. */
    @Nullable
    public static Role create(String id, String name) {
        String key = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(key).matches() || ROLES.containsKey(key)) {
            return null;
        }
        Role role = new Role(key, name == null || name.trim().isEmpty() ? key : name.trim());
        role.normalize();
        ROLES.put(key, role);
        changed();
        return role;
    }

    public static boolean delete(String id) {
        Role role = get(id);
        if (role == null) {
            return false;
        }
        ROLES.remove(role.id);
        for (List<Assignment> list : ASSIGNED.values()) {
            Iterator<Assignment> it = list.iterator();
            while (it.hasNext()) {
                if (it.next().role.equals(role.id)) {
                    it.remove();
                    dirty = true;
                }
            }
        }
        changed();
        saveIfDirty();
        return true;
    }

    // --- выдача ---------------------------------------------------------------

    /** Выдать роль (повторная выдача продлевает/меняет срок). until = 0 — навсегда. */
    public static boolean give(UUID player, String roleId, long until, String by) {
        Role role = get(roleId);
        if (role == null || player == null) {
            return false;
        }
        List<Assignment> list = ASSIGNED.get(player);
        if (list == null) {
            list = new ArrayList<Assignment>();
            ASSIGNED.put(player, list);
        }
        Assignment found = null;
        for (Assignment a : list) {
            if (a.role.equals(role.id)) {
                found = a;
            }
        }
        if (found == null) {
            found = new Assignment();
            found.role = role.id;
            list.add(found);
        }
        found.until = until;
        found.by = by == null ? "" : by;
        found.at = System.currentTimeMillis();
        dirty = true;
        saveIfDirty();
        sync(PlayerUtil.online(player));
        return true;
    }

    public static boolean take(UUID player, String roleId) {
        List<Assignment> list = ASSIGNED.get(player);
        if (list == null || roleId == null) {
            return false;
        }
        String key = roleId.trim().toLowerCase(Locale.ROOT);
        boolean removed = false;
        Iterator<Assignment> it = list.iterator();
        while (it.hasNext()) {
            if (it.next().role.equals(key)) {
                it.remove();
                removed = true;
            }
        }
        if (list.isEmpty()) {
            ASSIGNED.remove(player);
        }
        if (removed) {
            dirty = true;
            saveIfDirty();
            sync(PlayerUtil.online(player));
        }
        return removed;
    }

    private static boolean active(Assignment a, long now) {
        return a.until <= 0L || a.until > now;
    }

    /** Действующие выдачи игрока (для /role player). */
    public static List<Assignment> assignments(UUID player) {
        List<Assignment> list = ASSIGNED.get(player);
        List<Assignment> out = new ArrayList<Assignment>();
        if (list == null) {
            return out;
        }
        long now = System.currentTimeMillis();
        for (Assignment a : list) {
            if (active(a, now) && ROLES.containsKey(a.role)) {
                out.add(a);
            }
        }
        return out;
    }

    /** Кто владеет ролью (для /role info). */
    public static List<UUID> holders(String roleId) {
        List<UUID> out = new ArrayList<UUID>();
        String key = roleId == null ? "" : roleId.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, List<Assignment>> e : ASSIGNED.entrySet()) {
            for (Assignment a : e.getValue()) {
                if (a.role.equals(key) && active(a, now)) {
                    out.add(e.getKey());
                    break;
                }
            }
        }
        return out;
    }

    /** Действующие роли игрока, по убыванию приоритета. */
    public static List<Role> rolesOf(@Nullable UUID player) {
        List<Role> out = new ArrayList<Role>();
        if (player == null) {
            return out;
        }
        List<Assignment> list = ASSIGNED.get(player);
        if (list == null || list.isEmpty()) {
            return out;
        }
        long now = System.currentTimeMillis();
        for (Assignment a : list) {
            Role role = ROLES.get(a.role);
            if (role != null && active(a, now) && !out.contains(role)) {
                out.add(role);
            }
        }
        Collections.sort(out, BY_PRIORITY);
        return out;
    }

    public static boolean has(@Nullable UUID player, String roleId) {
        if (player == null || roleId == null) {
            return false;
        }
        String key = roleId.trim().toLowerCase(Locale.ROOT);
        for (Role role : rolesOf(player)) {
            if (role.id.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** Работает и на клиенте (для своего игрока — по присланному списку ролей). */
    public static boolean has(@Nullable EntityPlayer player, String roleId) {
        if (player == null || roleId == null) {
            return false;
        }
        if (player.world != null && player.world.isRemote) {
            return CLIENT_ROLES.contains(roleId.trim().toLowerCase(Locale.ROOT));
        }
        return has(player.getUniqueID(), roleId);
    }

    /** Есть ли у игрока хотя бы одна роль из списка. */
    public static boolean hasAny(@Nullable EntityPlayer player, List<String> roleIds) {
        if (player == null || roleIds == null) {
            return false;
        }
        for (String id : roleIds) {
            if (has(player, id)) {
                return true;
            }
        }
        return false;
    }

    // --- что дают роли --------------------------------------------------------

    public static boolean allowsCommand(@Nullable UUID player, String command) {
        if (player == null || command == null || ROLES.isEmpty()) {
            return false;
        }
        String key = command.toLowerCase(Locale.ROOT);
        for (Role role : rolesOf(player)) {
            if (role.commands.contains(key)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasPermission(@Nullable UUID player, String node) {
        if (player == null || node == null || ROLES.isEmpty()) {
            return false;
        }
        for (Role role : rolesOf(player)) {
            for (String p : role.permissions) {
                if (p == null) {
                    continue;
                }
                if (p.equals(node) || p.equals("*")) {
                    return true;
                }
                if (p.endsWith(".*") && node.startsWith(p.substring(0, p.length() - 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean xaeroTp(@Nullable UUID player) {
        for (Role role : rolesOf(player)) {
            if (role.xaeroTp) {
                return true;
            }
        }
        return false;
    }

    /** Наибольший множитель наград среди ролей (без ролей — 1.0). */
    public static double rewardMultiplier(@Nullable UUID player) {
        double best = 1.0D;
        boolean any = false;
        for (Role role : rolesOf(player)) {
            if (!any || role.rewardMultiplier > best) {
                best = role.rewardMultiplier;
                any = true;
            }
        }
        return any ? Math.max(0.0D, best) : 1.0D;
    }

    /** Тег для чата: у самой приоритетной роли, где он задан. */
    public static String chatPrefix(@Nullable UUID player) {
        for (Role role : rolesOf(player)) {
            if (role.prefix != null && !role.prefix.trim().isEmpty()) {
                return role.prefix.trim();
            }
        }
        return "";
    }

    /** Мир из списка ролей для /world. */
    public static boolean worldAllowed(@Nullable UUID player, int dim) {
        for (Role role : rolesOf(player)) {
            for (String w : role.worlds) {
                if (w == null) {
                    continue;
                }
                if (w.trim().equals("*")) {
                    return true;
                }
                Integer resolved = com.ecoserv.world.WorldNames.resolve(w);
                if (resolved != null && resolved == dim) {
                    return true;
                }
            }
        }
        return false;
    }

    // --- синхронизация и сроки ------------------------------------------------

    public static void sync(@Nullable EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        List<String> ids = new ArrayList<String>();
        List<String> titles = new ArrayList<String>();
        for (Role role : rolesOf(player.getUniqueID())) {
            ids.add(role.id);
            titles.add(role.title());
        }
        PacketHandler.sendTo(new PacketRoles(ids, titles), player);
    }

    public static void syncAll() {
        for (EntityPlayerMP p : PlayerUtil.allOnline()) {
            sync(p);
        }
    }

    /** Раз в секунду из ServerServices: раз в минуту снимает истёкшие роли. */
    public static void tick() {
        if (++tick % 60 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, List<Assignment>>> it = ASSIGNED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, List<Assignment>> e = it.next();
            boolean changed = false;
            Iterator<Assignment> ai = e.getValue().iterator();
            while (ai.hasNext()) {
                Assignment a = ai.next();
                if (!active(a, now)) {
                    ai.remove();
                    changed = true;
                    EntityPlayerMP online = PlayerUtil.online(e.getKey());
                    Role role = ROLES.get(a.role);
                    if (online != null) {
                        ChatUtil.send(online, "&eСрок роли &f" + (role == null ? a.role : role.title())
                                + " &eзакончился.");
                    }
                }
            }
            if (changed) {
                dirty = true;
                sync(PlayerUtil.online(e.getKey()));
            }
            if (e.getValue().isEmpty()) {
                it.remove();
            }
        }
        saveIfDirty();
    }
}
