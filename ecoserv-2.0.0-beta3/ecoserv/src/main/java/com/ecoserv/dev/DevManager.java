package com.ecoserv.dev;

import com.ecoserv.EcoServ;
import com.ecoserv.region.Region;
import com.ecoserv.region.RegionManager;
import com.ecoserv.script.ScriptRunner;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.v2.V2Actions;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

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
import java.util.regex.Pattern;

/**
 * Режим разработчика (2.0): программируемые зоны, привязки событий к сценариям (PlayerJoin() и т.п.),
 * видимость скрытого (маркеры, зоны, провода) для админов в режиме разработчика и для очков разработчика.
 */
public final class DevManager {

    public static final Pattern NAME = Pattern.compile("^[\\p{L}0-9_\\-]{1,48}$");
    private static final Type ZONES = new TypeToken<ArrayList<DevZone>>() {
    }.getType();
    private static final Type EVENTS = new TypeToken<LinkedHashMap<String, ArrayList<String>>>() {
    }.getType();
    private static final Type SEEN = new TypeToken<HashSet<String>>() {
    }.getType();

    /** Встроенные события: имя -> описание. */
    public static final Map<String, String> BUILTIN = new LinkedHashMap<String, String>();

    static {
        BUILTIN.put("join", "PlayerJoin() — игрок зашёл (после входа по паролю)");
        BUILTIN.put("first_join", "Первый вход игрока на сервер");
        BUILTIN.put("leave", "PlayerLeave() — игрок вышел");
        BUILTIN.put("respawn", "PlayerRespawn() — возродился");
        BUILTIN.put("death", "PlayerDeath() — погиб");
        BUILTIN.put("world_enter", "Вошёл в мир (контекст — имя мира: world_enter:nether)");
        BUILTIN.put("zone_enter", "Вошёл в зону разработчика (zone_enter:имя)");
        BUILTIN.put("zone_leave", "Вышел из зоны (zone_leave:имя)");
        BUILTIN.put("quest_done", "Выполнил квест (quest_done:id)");
        BUILTIN.put("game_win", "Победил в мини-игре (game_win:id)");
        BUILTIN.put("npc_talk", "Поговорил с NPC (npc_talk:id)");
        BUILTIN.put("menu", "Нажал кнопку своего меню (menu:id)");
        BUILTIN.put("logic", "Сработал блок-программа (logic:имя)");
    }

    private static final List<DevZone> ZONES_LIST = new ArrayList<DevZone>();
    private static Map<String, ArrayList<String>> events = new LinkedHashMap<String, ArrayList<String>>();
    private static Set<String> seen = new HashSet<String>();
    private static final Set<UUID> DEV = new HashSet<UUID>();
    /** Игрок -> зоны, в которых он сейчас. */
    private static final Map<UUID, Set<String>> INSIDE = new HashMap<UUID, Set<String>>();
    private static final Map<UUID, Integer> LAST_SYNC_HASH = new HashMap<UUID, Integer>();
    private static int tick;
    private static boolean seenDirty;

    private DevManager() {
    }

    // --- загрузка ------------------------------------------------------------------------------------

    public static void load() {
        ZONES_LIST.clear();
        List<DevZone> z = JsonUtil.readType(DataStore.file("dev_zones.json"), ZONES, null);
        if (z != null) {
            for (DevZone d : z) {
                if (d != null && d.name != null && !d.name.isEmpty()) {
                    d.normalize();
                    ZONES_LIST.add(d);
                }
            }
        }
        Map<String, ArrayList<String>> e = JsonUtil.readType(DataStore.file("dev_events.json"), EVENTS, null);
        events = e == null ? new LinkedHashMap<String, ArrayList<String>>() : e;
        Set<String> s = JsonUtil.readType(DataStore.file("dev_seen.json"), SEEN, null);
        seen = s == null ? new HashSet<String>() : s;
        DEV.clear();
        INSIDE.clear();
        LAST_SYNC_HASH.clear();
        com.ecoserv.script.ScriptVars.load();
    }

    public static void saveZones() {
        JsonUtil.write(DataStore.file("dev_zones.json"), ZONES_LIST);
        LAST_SYNC_HASH.clear();
    }

    public static void saveEvents() {
        JsonUtil.write(DataStore.file("dev_events.json"), events);
    }

    public static void onStop() {
        com.ecoserv.script.ScriptVars.saveIfDirty();
        if (seenDirty) {
            JsonUtil.write(DataStore.file("dev_seen.json"), seen);
        }
    }

    // --- режим разработчика --------------------------------------------------------------------------

    public static boolean isDev(EntityPlayerMP p) {
        return DEV.contains(p.getUniqueID());
    }

    public static void setDev(EntityPlayerMP p, boolean on) {
        if (on && !Perms.isAdmin(p)) {
            return;
        }
        if (on) {
            DEV.add(p.getUniqueID());
        } else {
            DEV.remove(p.getUniqueID());
        }
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("on", on);
        V2Actions.send(p, "dev", t);
        LAST_SYNC_HASH.remove(p.getUniqueID());
        sync(p);
    }

    /** Видит ли игрок скрытое: режим разработчика или очки на голове. */
    public static boolean sees(EntityPlayerMP p) {
        return DEV.contains(p.getUniqueID())
                || p.getItemStackFromSlot(EntityEquipmentSlot.HEAD).getItem() == com.ecoserv.v2.ModRegistry2.DEV_GLASSES;
    }

    // --- зоны ----------------------------------------------------------------------------------------

    public static List<DevZone> zones() {
        return new ArrayList<DevZone>(ZONES_LIST);
    }

    @Nullable
    public static DevZone zone(String name) {
        for (DevZone z : ZONES_LIST) {
            if (z.name.equalsIgnoreCase(name)) {
                return z;
            }
        }
        return null;
    }

    @Nullable
    public static DevZone createZone(String name, int dim, net.minecraft.util.math.BlockPos a, net.minecraft.util.math.BlockPos b) {
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!NAME.matcher(n).matches() || zone(n) != null) {
            return null;
        }
        DevZone z = new DevZone();
        z.name = n;
        z.dim = RegionManager.regionDim(dim);
        z.minX = a.getX();
        z.minY = a.getY();
        z.minZ = a.getZ();
        z.maxX = b.getX();
        z.maxY = b.getY();
        z.maxZ = b.getZ();
        z.normalize();
        ZONES_LIST.add(z);
        saveZones();
        return z;
    }

    public static void removeZone(DevZone z) {
        ZONES_LIST.remove(z);
        saveZones();
    }

    /** Применяется ли зона в мире игрока: глобальная — во всех копиях, локальная — только в своём мире. */
    static boolean applies(DevZone z, int dim) {
        return z.global() ? RegionManager.regionDim(dim) == z.dim || dim == z.dim : dim == z.dim;
    }

    // --- события ---------------------------------------------------------------------------------------

    public static Map<String, ArrayList<String>> bindings() {
        return events;
    }

    public static List<String> bound(String event) {
        List<String> l = events.get(event);
        return l == null ? new ArrayList<String>() : l;
    }

    public static void bind(String event, String script) {
        ArrayList<String> l = events.get(event);
        if (l == null) {
            l = new ArrayList<String>();
            events.put(event, l);
        }
        if (!l.contains(script)) {
            l.add(script);
        }
        saveEvents();
    }

    public static void unbind(String event, String script) {
        ArrayList<String> l = events.get(event);
        if (l != null) {
            l.remove(script);
            if (l.isEmpty()) {
                events.remove(event);
            }
        }
        saveEvents();
    }

    /** Событие: сценарии, привязанные к name и к name:context, и блоки-программы, слушающие его. */
    static void fire(String name, @Nullable EntityPlayerMP p, @Nullable String context) {
        if (name == null || name.isEmpty()) {
            return;
        }
        List<String> run = new ArrayList<String>(bound(name));
        if (context != null && !context.isEmpty()) {
            run.addAll(bound(name + ":" + context.toLowerCase(Locale.ROOT)));
        }
        if (p != null) {
            for (String script : run) {
                ScriptRunner.start(p, script, context);
            }
        }
        TileLogic.onEvent(name, context, p);
    }

    public static void onJoin(EntityPlayerMP p) {
        if (seen.add(p.getUniqueID().toString())) {
            seenDirty = true;
            DevEvents.fire("first_join", p, null);
        }
        DevEvents.fire("join", p, null);
    }

    // --- тик ------------------------------------------------------------------------------------------

    public static void tick() {
        tick++;
        if (tick % 5 == 0 && !ZONES_LIST.isEmpty()) {
            for (EntityPlayerMP p : PlayerUtil.allOnline()) {
                try {
                    zonesFor(p);
                } catch (Throwable t) {
                    EcoServ.LOG.error("Зоны разработчика: ошибка для {}", p.getName(), t);
                }
            }
        }
        if (tick % 40 == 0) {
            for (EntityPlayerMP p : PlayerUtil.allOnline()) {
                if (sees(p)) {
                    sync(p);
                } else if (LAST_SYNC_HASH.containsKey(p.getUniqueID())) {
                    LAST_SYNC_HASH.remove(p.getUniqueID());
                    V2Actions.send(p, "dev_zones", new NBTTagCompound());
                }
            }
        }
        if (tick % 1200 == 0) {
            com.ecoserv.script.ScriptVars.saveIfDirty();
            if (seenDirty) {
                JsonUtil.write(DataStore.file("dev_seen.json"), seen);
                seenDirty = false;
            }
        }
    }

    private static void zonesFor(EntityPlayerMP p) {
        Set<String> was = INSIDE.get(p.getUniqueID());
        if (was == null) {
            was = new HashSet<String>();
            INSIDE.put(p.getUniqueID(), was);
        }
        for (DevZone z : ZONES_LIST) {
            boolean in = z.enabled && applies(z, p.dimension) && z.contains(p.posX, p.posY + 0.1D, p.posZ)
                    && (z.roles.isEmpty() || Perms.isAdmin(p) || com.ecoserv.role.RoleManager.hasAny(p, z.roles));
            boolean had = was.contains(z.name);
            if (in && !had) {
                was.add(z.name);
                ScriptRunner.startSteps(p, "zone_" + z.name, z.onEnter, z.name);
                DevEvents.fire("zone_enter", p, z.name);
            } else if (!in && had) {
                was.remove(z.name);
                ScriptRunner.startSteps(p, "zone_" + z.name, z.onLeave, z.name);
                DevEvents.fire("zone_leave", p, z.name);
            } else if (in && !z.onStay.isEmpty() && tick % z.stayTicks < 5) {
                ScriptRunner.startSteps(p, "zone_" + z.name, z.onStay, z.name);
            }
        }
    }

    public static void forget(UUID player) {
        INSIDE.remove(player);
        DEV.remove(player);
        LAST_SYNC_HASH.remove(player);
    }

    // --- что видит разработчик (рамки зон) -----------------------------------------------------------

    /** Рамки: зоны разработчика (красные — глобальные, синие — локальные) и порталы/триггеры. */
    /** 2.0 beta3: обновить рамки у всех, кто видит скрытое. */
    public static void syncAll() {
        for (EntityPlayerMP p : PlayerUtil.allOnline()) {
            if (sees(p)) {
                LAST_SYNC_HASH.remove(p.getUniqueID());
                sync(p);
            }
        }
    }

    static void sync(EntityPlayerMP p) {
        NBTTagCompound t = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        int rd = RegionManager.regionDim(p.dimension);
        for (DevZone z : ZONES_LIST) {
            if (!applies(z, p.dimension) || !near(p, z.minX, z.minZ, z.maxX, z.maxZ)) {
                continue;
            }
            list.appendTag(box(z.name, z.global() ? 0xFF3030 : 0x3070FF, z.minX, z.minY, z.minZ, z.maxX, z.maxY, z.maxZ,
                    (z.global() ? "глобальная" : "локальная") + (z.enabled ? "" : ", выкл")));
        }
        for (Region r : RegionManager.inDim(rd)) {
            if (!near(p, r.minX, r.minZ, r.maxX, r.maxZ)) {
                continue;
            }
            boolean portal = Region.PORTAL.equals(r.kind);
            list.appendTag(box(r.name, portal ? 0xB050FF : 0xFFA020, r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ,
                    portal ? "портал" : "триггер"));
        }
        for (com.ecoserv.claim.ClaimManager.ClaimView c : com.ecoserv.claim.ClaimManager.near(p, 6)) {
            list.appendTag(box(c.owner, 0x40D060, c.x * 16, 0, c.z * 16, c.x * 16 + 15, 255, c.z * 16 + 15, "приват"));
        }
        t.setTag("boxes", list);
        int hash = t.hashCode();
        Integer last = LAST_SYNC_HASH.get(p.getUniqueID());
        if (last != null && last == hash) {
            return;
        }
        LAST_SYNC_HASH.put(p.getUniqueID(), hash);
        V2Actions.send(p, "dev_zones", t);
    }

    private static boolean near(EntityPlayerMP p, int x1, int z1, int x2, int z2) {
        double cx = Math.max(x1, Math.min(p.posX, x2));
        double cz = Math.max(z1, Math.min(p.posZ, z2));
        double dx = cx - p.posX;
        double dz = cz - p.posZ;
        return dx * dx + dz * dz < 128.0D * 128.0D;
    }

    private static NBTTagCompound box(String name, int color, int x1, int y1, int z1, int x2, int y2, int z2, String kind) {
        NBTTagCompound b = new NBTTagCompound();
        b.setString("n", name);
        b.setString("k", kind);
        b.setInteger("c", color);
        b.setIntArray("b", new int[]{x1, y1, z1, x2 + 1, y2 + 1, z2 + 1});
        return b;
    }
}
