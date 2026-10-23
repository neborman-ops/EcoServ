package com.ecoserv.region;

import com.ecoserv.EcoServ;
import com.ecoserv.activator.ActivatorManager;
import com.ecoserv.content.Actions;
import com.ecoserv.dates.AgeFilter;
import com.ecoserv.dates.DatesManager;
import com.ecoserv.role.RoleManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.world.WorldNames;
import com.ecoserv.world.WorldTeleport;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.io.File;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/** Зоны-триггеры и порталы: хранение, проверка входа игроков, действия. */
public final class RegionManager {

    /** Обработчик порталов (подмиры подключают свой: доступ, локальные миры команд). */
    public interface PortalHandler {
        /** true — портал обработан (телепорт выполнен или отказано с сообщением). */
        boolean handle(EntityPlayerMP player, Region portal);
    }

    /**
     * Чьи области действуют в измерении: локальный мир команды — копия шаблона подмира, поэтому в нём
     * работают зоны и порталы, поставленные в шаблоне.
     */
    public interface DimAlias {
        int regionsOf(int dim);
    }

    @Nullable
    private static DimAlias alias;

    public static void setDimAlias(@Nullable DimAlias a) {
        alias = a;
    }

    /** Измерение, чьи области действуют в dim. */
    public static int regionDim(int dim) {
        DimAlias a = alias;
        if (a == null) {
            return dim;
        }
        try {
            return a.regionsOf(dim);
        } catch (Throwable t) {
            return dim;
        }
    }

    private static final Type LIST = new TypeToken<ArrayList<Region>>() {
    }.getType();
    private static final Type STATE = new TypeToken<HashMap<String, HashMap<String, Long>>>() {
    }.getType();
    /** Латиница, кириллица, цифры, _ и -. */
    public static final Pattern NAME = Pattern.compile("^[\\p{L}0-9_\\-]{1,48}$");

    private static final Map<String, Region> BY_NAME = new LinkedHashMap<String, Region>();
    private static final Map<Integer, List<Region>> BY_DIM = new HashMap<Integer, List<Region>>();
    private static final Map<UUID, Set<String>> INSIDE = new HashMap<UUID, Set<String>>();
    private static final Map<UUID, Long> SUPPRESS = new HashMap<UUID, Long>();
    private static final Map<String, Long> DENY_NOTICE = new HashMap<String, Long>();
    private static final List<PortalHandler> PORTAL_HANDLERS = new CopyOnWriteArrayList<PortalHandler>();
    private static Map<String, HashMap<String, Long>> state = new HashMap<String, HashMap<String, Long>>();
    private static boolean dirty;
    private static boolean stateDirty;

    private RegionManager() {
    }

    public static void addPortalHandler(PortalHandler handler) {
        PORTAL_HANDLERS.add(handler);
    }

    private static File file() {
        return DataStore.file("regions.json");
    }

    private static File stateFile() {
        return DataStore.file("region_state.json");
    }

    // --- загрузка / сохранение ------------------------------------------------

    public static void load() {
        version++;
        BY_NAME.clear();
        List<Region> loaded = JsonUtil.readType(file(), LIST, new ArrayList<Region>());
        if (loaded != null) {
            for (Region r : loaded) {
                if (r != null && r.name != null && !r.name.trim().isEmpty()) {
                    r.name = key(r.name);
                    r.normalize();
                    BY_NAME.put(r.name, r);
                }
            }
        }
        Map<String, HashMap<String, Long>> s = JsonUtil.readType(stateFile(), STATE,
                new HashMap<String, HashMap<String, Long>>());
        state = s == null ? new HashMap<String, HashMap<String, Long>>() : s;
        INSIDE.clear();
        reindex();
        dirty = false;
        stateDirty = false;
    }

    private static void reindex() {
        BY_DIM.clear();
        for (Region r : BY_NAME.values()) {
            List<Region> list = BY_DIM.get(r.dim);
            if (list == null) {
                list = new ArrayList<Region>();
                BY_DIM.put(r.dim, list);
            }
            list.add(r);
        }
    }

    public static void save() {
        if (JsonUtil.write(file(), new ArrayList<Region>(BY_NAME.values()))) {
            dirty = false;
        }
    }

    public static void saveIfDirty() {
        if (dirty) {
            save();
        }
        if (stateDirty && JsonUtil.write(stateFile(), state)) {
            stateDirty = false;
        }
    }

    /** После правки области: переиндексировать и записать. */
    /** Растёт при каждом изменении областей (клиентам заново шлются «Чёрные дыры»). */
    public static volatile int version;

    public static void changed() {
        version++;
        BlackHoles.invalidate(null);
        for (Region r : BY_NAME.values()) {
            r.normalize();
        }
        reindex();
        save();
    }

    // --- доступ ---------------------------------------------------------------

    public static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    @Nullable
    public static Region get(String name) {
        return BY_NAME.get(key(name));
    }

    public static List<Region> all() {
        return new ArrayList<Region>(BY_NAME.values());
    }

    public static List<Region> all(String kind) {
        List<Region> out = new ArrayList<Region>();
        for (Region r : BY_NAME.values()) {
            if (kind == null || kind.equals(r.kind)) {
                out.add(r);
            }
        }
        return out;
    }

    public static List<Region> inDim(int dim) {
        List<Region> list = BY_DIM.get(dim);
        if (list == null) {
            list = BY_DIM.get(regionDim(dim));
        }
        return list == null ? new ArrayList<Region>() : list;
    }

    public static List<Region> at(int dim, BlockPos pos) {
        List<Region> out = new ArrayList<Region>();
        for (Region r : inDim(dim)) {
            if (r.contains(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)) {
                out.add(r);
            }
        }
        return out;
    }

    /** Новая область. null — имя некорректно или занято. */
    @Nullable
    public static Region create(String name, String kind, int dim, BlockPos a, BlockPos b, String creator) {
        String k = key(name);
        if (!NAME.matcher(k).matches() || BY_NAME.containsKey(k)) {
            return null;
        }
        Region r = new Region();
        r.name = k;
        r.kind = kind;
        r.setArea(dim, a, b);
        r.creator = creator == null ? "" : creator;
        r.created = System.currentTimeMillis();
        if (r.isPortal()) {
            r.activation = false;
            r.mode = "enter";
        }
        r.normalize();
        BY_NAME.put(k, r);
        changed();
        return r;
    }

    public static boolean remove(String name) {
        Region r = BY_NAME.remove(key(name));
        if (r == null) {
            return false;
        }
        for (Map<String, Long> map : state.values()) {
            if (map.remove(r.name) != null) {
                stateDirty = true;
            }
        }
        changed();
        return true;
    }

    /** Переименовать (состояние «уже срабатывало» переносится). */
    public static boolean rename(String oldName, String newName) {
        Region r = get(oldName);
        String k = key(newName);
        if (r == null || !NAME.matcher(k).matches() || BY_NAME.containsKey(k)) {
            return false;
        }
        BY_NAME.remove(r.name);
        for (Map<String, Long> map : state.values()) {
            Long v = map.remove(r.name);
            if (v != null) {
                map.put(k, v);
                stateDirty = true;
            }
        }
        for (Region other : BY_NAME.values()) {
            if (key(other.targetPortal).equals(r.name)) {
                other.targetPortal = k;
            }
        }
        r.name = k;
        BY_NAME.put(k, r);
        changed();
        return true;
    }

    /** Сбросить «уже срабатывало» у игрока (или у всех, если player == null). */
    public static int resetState(String name, @Nullable UUID player) {
        String k = key(name);
        int n = 0;
        for (Map.Entry<String, HashMap<String, Long>> e : state.entrySet()) {
            if (player != null && !e.getKey().equals(player.toString())) {
                continue;
            }
            if (e.getValue().remove(k) != null) {
                n++;
                stateDirty = true;
            }
        }
        return n;
    }

    // --- проверка игроков -----------------------------------------------------

    public static void forget(UUID player) {
        BlackHoles.forget(player);
        INSIDE.remove(player);
        SUPPRESS.remove(player);
        PortalGuard.forget(player);
    }

    /** После телепорта игрок не должен сразу сработать в области, куда попал. */
    public static void suppress(EntityPlayerMP player, long millis) {
        SUPPRESS.put(player.getUniqueID(), System.currentTimeMillis() + millis);
        // любой телепорт мода зовёт suppress() перед переходом — заодно включаем паузу порталов
        if (millis >= 1000L) {
            PortalGuard.start(player);
        }
    }

    public static void tickPlayer(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        List<Region> regions = BY_DIM.get(player.dimension);
        if (regions == null) {
            regions = BY_DIM.get(regionDim(player.dimension));
        }
        Set<String> previous = INSIDE.get(id);
        if ((regions == null || regions.isEmpty()) && (previous == null || previous.isEmpty())) {
            return;
        }
        Set<String> now = new HashSet<String>();
        if (regions != null) {
            for (Region r : regions) {
                if (r.enabled && r.containsPlayer(player.posX, player.posY, player.posZ)) {
                    now.add(r.name);
                }
            }
        }
        INSIDE.put(id, now);
        PortalGuard.observe(player, now);
        Long until = SUPPRESS.get(id);
        if (until != null) {
            if (System.currentTimeMillis() < until) {
                return;
            }
            SUPPRESS.remove(id);
        }
        if (previous == null) {
            previous = new HashSet<String>();
        }
        Set<String> due = PortalGuard.due(player);
        for (String name : now) {
            if (!previous.contains(name) || due.contains(name)) {
                Region r = BY_NAME.get(name);
                if (r != null) {
                    // пауза после телепорта / игрок появился прямо в портале
                    if (PortalGuard.blocks(player, r)) {
                        continue;
                    }
                    fire(player, r, false);
                }
                if (player.isDead || SUPPRESS.containsKey(id)) {
                    // портал перенёс игрока — остальные области проверим уже на новом месте
                    INSIDE.remove(id);
                    return;
                }
            }
        }
    }

    /** Может ли игрок пройти условия области (роли, возраст). */
    public static boolean allowed(EntityPlayerMP player, Region r) {
        if (Perms.isAdmin(player)) {
            return true;
        }
        if (!r.roles.isEmpty() && !RoleManager.hasAny(player, r.roles)) {
            return false;
        }
        AgeFilter filter = AgeFilter.parse(r.age);
        return DatesManager.checkAge(player.getUniqueID(), filter);
    }

    /** Сработать для игрока. force — без проверки режима (для /trigger test). */
    public static void fire(EntityPlayerMP player, Region r, boolean force) {
        if (!allowed(player, r)) {
            if (!r.denyMessage.isEmpty()) {
                String k = player.getUniqueID() + "|" + r.name;
                Long last = DENY_NOTICE.get(k);
                long now = System.currentTimeMillis();
                if (last == null || now - last > 3000L) {
                    DENY_NOTICE.put(k, now);
                    Actions.message(player, r.denyMessage, r.name);
                }
            }
            return;
        }
        if (!force && !"enter".equals(r.mode)) {
            long now = System.currentTimeMillis();
            Long last = lastTrigger(player.getUniqueID(), r.name);
            if ("once".equals(r.mode) && last != null) {
                return;
            }
            if ("cooldown".equals(r.mode) && last != null && now - last < r.cooldownSeconds * 1000L) {
                return;
            }
            setLast(player.getUniqueID(), r.name, now);
        }
        Actions.message(player, r.message, r.name);
        Actions.title(player, r.title, r.name, 10, 60, 20);
        Actions.sound(player, r.sound, 1.0F, 1.0F);
        for (String command : r.commands) {
            Actions.serverCommand(command, player, r.name);
        }
        if (!r.script.isEmpty()) {
            com.ecoserv.script.ScriptRunner.start(player, r.script, r.name);
        }
        if (r.activation) {
            ActivatorManager.notifyListeners(player, r.name);
        }
        if (r.isPortal()) {
            portal(player, r);
        }
    }

    private static void portal(EntityPlayerMP player, Region r) {
        // 1.8.0: «Чёрная дыра» — выходишь с той стороны, куда шёл, с тем же взглядом и скоростью
        BlackHoles.entering(player, r);
        try {
            portalInner(player, r);
        } finally {
            BlackHoles.entered(player, r);
        }
    }

    private static void portalInner(EntityPlayerMP player, Region r) {
        for (PortalHandler handler : PORTAL_HANDLERS) {
            try {
                if (handler.handle(player, r)) {
                    return;
                }
            } catch (Throwable t) {
                EcoServ.LOG.error("Ошибка обработчика портала «{}»", r.name, t);
            }
        }
        WorldTeleport.Target target = "portal".equals(r.targetMode) ? linkedTarget(player, r) : defaultTarget(player, r);
        target = BlackHoles.keepLook(player, r, target);
        if (target == null) {
            if (Perms.isAdmin(player)) {
                ChatUtil.actionBar(player, "&cПортал «" + r.name + "» никуда не ведёт — настрой: /portal menu " + r.name);
            }
            return;
        }
        suppress(player, 1500L);
        PortalGuard.start(player);
        WorldTeleport.rememberPosition(player);
        if (!WorldTeleport.teleport(player, target)) {
            ChatUtil.send(player, "&cНе удалось перейти через портал «" + r.name + "».");
        }
    }

    /** Точка назначения обычного портала (в измерение по имени/номеру). */
    @Nullable
    public static WorldTeleport.Target defaultTarget(EntityPlayerMP player, Region r) {
        if (r.target == null || r.target.trim().isEmpty()) {
            return null;
        }
        Integer dim = WorldNames.resolve(r.target);
        if (dim == null || !WorldNames.exists(dim)) {
            return null;
        }
        if ("point".equals(r.targetMode)) {
            return new WorldTeleport.Target(dim, r.tx, r.ty, r.tz, r.tyaw, r.tpitch);
        }
        return WorldTeleport.pickTarget(player, dim, !"last".equals(r.targetMode));
    }

    /**
     * Точка выхода у портала: заданная админом или перед порталом (снаружи области, по центру, у нижней
     * границы) — чтобы пришедший не стоял внутри и мог шагнуть обратно. {x, y, z, yaw, pitch}.
     */
    public static double[] exitPoint(Region r) {
        double[] hole = BlackHoles.exitFor(r);
        if (hole != null) {
            return hole;
        }
        if (r.exitSet) {
            return new double[]{r.ex, r.ey, r.ez, r.eyaw, r.epitch};
        }
        double cx = (r.minX + r.maxX + 1) / 2.0D;
        double cz = (r.minZ + r.maxZ + 1) / 2.0D;
        int sx = r.maxX - r.minX + 1;
        int sz = r.maxZ - r.minZ + 1;
        if (sx <= sz) {
            // плоскость портала вдоль Z — выходим на восток
            return new double[]{r.maxX + 1.5D, r.minY, cz, -90.0D, 0.0D};
        }
        return new double[]{cx, r.minY, r.maxZ + 1.5D, 0.0D, 0.0D};
    }

    /** Портал ведёт к другому порталу в обычном мире (порталы в подмиры обрабатывают подмиры). */
    @Nullable
    public static WorldTeleport.Target linkedTarget(EntityPlayerMP player, Region r) {
        Region dest = get(r.targetPortal);
        if (dest == null || dest == r) {
            return null;
        }
        double[] p = exitPoint(dest);
        int dim = dest.dim;
        if (!WorldNames.exists(dim)) {
            return null;
        }
        net.minecraft.world.WorldServer world = player.getServer() == null ? null : player.getServer().getWorld(dim);
        if (world == null) {
            return null;
        }
        BlockPos pos = new BlockPos(p[0], p[1], p[2]);
        if (BlackHoles.exactExit(dest) || WorldTeleport.isSafe(world, pos)) {
            return new WorldTeleport.Target(dim, p[0], p[1], p[2], (float) p[3], (float) p[4]);
        }
        return WorldTeleport.near(player, dim, pos);
    }

    /** Порталы, ведущие к этому (для подсказок в меню). */
    public static List<Region> linkedTo(String name) {
        List<Region> out = new ArrayList<Region>();
        String k = key(name);
        for (Region r : BY_NAME.values()) {
            if (r.isPortal() && "portal".equals(r.targetMode) && key(r.targetPortal).equals(k)) {
                out.add(r);
            }
        }
        return out;
    }

    @Nullable
    private static Long lastTrigger(UUID player, String name) {
        Map<String, Long> map = state.get(player.toString());
        return map == null ? null : map.get(name);
    }

    private static void setLast(UUID player, String name, long time) {
        HashMap<String, Long> map = state.get(player.toString());
        if (map == null) {
            map = new HashMap<String, Long>();
            state.put(player.toString(), map);
        }
        map.put(name, time);
        stateDirty = true;
    }
}
