package com.ecoserv.subworld;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.world.WorldNames;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Все подмиры и их измерения. Файл world/ecoserv/subworlds.json, миры —
 * в папке &lt;мир&gt;/ecoserv_worlds/&lt;подмир&gt;/.
 */
public final class SubworldRegistry {

    public static class Data {
        public List<Subworld> subworlds = new ArrayList<Subworld>();
        public List<SubworldDim> dims = new ArrayList<SubworldDim>();
        /** uuid игрока -> подмиры, доступ к которым открыт. */
        public Map<String, List<String>> unlocks = new HashMap<String, List<String>>();
        /** «владелец|подмир» -> активное сохранение (номер измерения). */
        public Map<String, Integer> active = new HashMap<String, Integer>();
        /** uuid -> подмиры, где игрок уже получил стартовый набор. */
        public Map<String, List<String>> kits = new HashMap<String, List<String>>();
        /** Удалённые подмиры, чьи папки ещё не стёрты (стираются при запуске сервера). */
        public List<String> deletedSubworlds = new ArrayList<String>();
        public int nextDim;
    }

    public static final Pattern ID = Pattern.compile("^[\\p{L}0-9_]{1,32}$");

    private static Data data = new Data();
    private static final Map<String, Subworld> BY_ID = new LinkedHashMap<String, Subworld>();
    private static final Map<Integer, SubworldDim> BY_DIM = new HashMap<Integer, SubworldDim>();
    private static boolean dirty;
    private static boolean registeredResolver;
    /** Файл реестра был, но не прочитался — ничего не стираем, чтобы не потерять миры. */
    private static boolean loadFailed;

    private SubworldRegistry() {
    }

    private static File file() {
        return DataStore.file("subworlds.json");
    }

    // --- загрузка / сохранение ------------------------------------------------

    public static void load() {
        File f = file();
        File tmp = new File(f.getAbsolutePath() + ".tmp");
        // файл не переименовываем, даже если он битый: пусть остаётся на месте, пока его не починят
        Data loaded = f.exists() ? readData(f) : null;
        if (loaded == null && tmp.isFile()) {
            // запись прервалась до подмены — во временном файле последняя целая версия
            loaded = readData(tmp);
        }
        loadFailed = (f.exists() || tmp.isFile()) && loaded == null;
        if (loadFailed) {
            EcoServ.LOG.error("subworlds.json не прочитался — подмиры не загружены, папки миров не трогаю, "
                    + "создание подмиров закрыто. Исправь файл (world/ecoserv/subworlds.json) или верни его "
                    + "из бэкапа и перезапусти сервер.");
        }
        data = loaded == null ? new Data() : loaded;
        if (data.subworlds == null) {
            data.subworlds = new ArrayList<Subworld>();
        }
        if (data.dims == null) {
            data.dims = new ArrayList<SubworldDim>();
        }
        if (data.unlocks == null) {
            data.unlocks = new HashMap<String, List<String>>();
        }
        if (data.active == null) {
            data.active = new HashMap<String, Integer>();
        }
        if (data.kits == null) {
            data.kits = new HashMap<String, List<String>>();
        }
        if (data.deletedSubworlds == null) {
            data.deletedSubworlds = new ArrayList<String>();
        }
        reindex();
        dirty = false;
        if (!registeredResolver) {
            registeredResolver = true;
            WorldNames.registerGroups(new WorldNames.GroupResolver() {
                @Nullable
                @Override
                public String groupOf(int dim) {
                    SubworldDim d = BY_DIM.get(dim);
                    return d == null ? null : d.subworld;
                }

                @Override
                public Collection<String> groups() {
                    return new ArrayList<String>(BY_ID.keySet());
                }
            });
        }
        if (!data.subworlds.isEmpty()) {
            EcoServ.LOG.info("Подмиры: {} шт., измерений: {}", data.subworlds.size(), BY_DIM.size());
        }
    }

    @Nullable
    private static Data readData(File file) {
        java.io.Reader reader = null;
        try {
            reader = new java.io.InputStreamReader(new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8);
            return JsonUtil.GSON.fromJson(reader, Data.class);
        } catch (Exception e) {
            EcoServ.LOG.error("Не удалось прочитать {}: {}", file, e.toString());
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (java.io.IOException ignored) {
                    // только чтение
                }
            }
        }
    }

    /** Текст для админов, пока реестр не прочитался. */
    public static final String BROKEN_NOTICE = "&cФайл подмиров world/ecoserv/subworlds.json не прочитался: подмиры "
            + "не загружены, создавать новые нельзя. Исправь файл или верни его из бэкапа и перезапусти сервер.";

    public static void reindex() {
        BY_ID.clear();
        BY_DIM.clear();
        for (Subworld sw : data.subworlds) {
            if (sw != null && sw.id != null && !sw.id.isEmpty()) {
                sw.id = key(sw.id);
                sw.normalize();
                BY_ID.put(sw.id, sw);
            }
        }
        for (SubworldDim d : data.dims) {
            if (d != null && !d.deleted && BY_ID.containsKey(d.subworld)) {
                d.normalize();
                BY_DIM.put(d.dim, d);
            }
        }
    }

    public static void markDirty() {
        dirty = true;
    }

    public static boolean loadFailed() {
        return loadFailed;
    }

    public static void save() {
        if (loadFailed) {
            // не затираем непрочитанный файл пустым реестром
            return;
        }
        if (JsonUtil.write(file(), data)) {
            dirty = false;
        }
    }

    public static void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    // --- подмиры --------------------------------------------------------------

    public static String key(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    @Nullable
    public static Subworld get(String id) {
        return BY_ID.get(key(id));
    }

    public static List<Subworld> all() {
        return new ArrayList<Subworld>(BY_ID.values());
    }

    /**
     * Это тот самый подмир, что сейчас в реестре: меню и отложенные действия могли держать объект
     * удалённого подмира (или реестра, прочитанного заново).
     */
    public static boolean isCurrent(@Nullable Subworld sw) {
        return sw != null && BY_ID.get(key(sw.id)) == sw;
    }

    /** То же для измерения: не удалено и его подмир на месте. */
    public static boolean isCurrent(@Nullable SubworldDim d) {
        return d != null && !d.deleted && BY_DIM.get(d.dim) == d && BY_ID.containsKey(d.subworld);
    }

    /** Слова подкоманд /world — подмир с таким именем было бы не вызвать. */
    private static final java.util.Set<String> RESERVED = new java.util.HashSet<String>(java.util.Arrays.asList(
            "list", "leave", "buy", "menu", "help", "team", "add", "accept", "deny", "kick", "leaveteam", "disband",
            "invite", "uninvite", "guests", "visit"));

    /** Имя свободно, не совпадает с обычным миром и не осталось от удалённого подмира. */
    public static boolean idAvailable(String id) {
        String k = key(id);
        return ID.matcher(k).matches() && !BY_ID.containsKey(k) && WorldNames.resolve(k) == null
                && !RESERVED.contains(k) && !data.deletedSubworlds.contains(k) && !subworldDir(k).exists();
    }

    public static void add(Subworld sw) {
        sw.id = key(sw.id);
        sw.normalize();
        data.subworlds.add(sw);
        reindex();
        save();
    }

    /** Удалить подмир: измерения помечаются удалёнными, папка подмира стирается при следующем запуске. */
    public static void remove(Subworld sw) {
        data.subworlds.remove(sw);
        for (SubworldDim d : data.dims) {
            if (d.subworld.equals(sw.id)) {
                d.deleted = true;
            }
        }
        if (!data.deletedSubworlds.contains(sw.id)) {
            data.deletedSubworlds.add(sw.id);
        }
        for (List<String> list : data.unlocks.values()) {
            list.remove(sw.id);
        }
        for (List<String> list : data.kits.values()) {
            list.remove(sw.id);
        }
        data.active.keySet().removeIf(k -> k.endsWith("|" + sw.id));
        reindex();
        save();
    }

    /** Удалённые подмиры, чьи папки ещё ждут стирания. */
    public static List<String> deletedSubworlds() {
        return new ArrayList<String>(data.deletedSubworlds);
    }

    public static void forgetDeletedSubworld(String id) {
        data.deletedSubworlds.remove(id);
        markDirty();
    }

    /** Остались ли записи об измерениях подмира (удалённые, но не стёртые). */
    public static boolean hasDimRecords(String subworld) {
        for (SubworldDim d : data.dims) {
            if (d.subworld.equals(subworld)) {
                return true;
            }
        }
        return false;
    }

    // --- измерения ------------------------------------------------------------

    @Nullable
    public static SubworldDim dim(int dim) {
        return BY_DIM.get(dim);
    }

    public static boolean isOurs(int dim) {
        return BY_DIM.containsKey(dim);
    }

    @Nullable
    public static Subworld subworldOf(int dim) {
        SubworldDim d = BY_DIM.get(dim);
        return d == null ? null : BY_ID.get(d.subworld);
    }

    public static List<SubworldDim> dims() {
        return new ArrayList<SubworldDim>(BY_DIM.values());
    }

    public static List<SubworldDim> dimsOf(String subworld) {
        List<SubworldDim> out = new ArrayList<SubworldDim>();
        for (SubworldDim d : BY_DIM.values()) {
            if (d.subworld.equals(subworld)) {
                out.add(d);
            }
        }
        return out;
    }

    /** Удалённые измерения (для уборки папок при запуске). */
    public static List<SubworldDim> deletedDims() {
        List<SubworldDim> out = new ArrayList<SubworldDim>();
        for (SubworldDim d : data.dims) {
            if (d.deleted) {
                out.add(d);
            }
        }
        return out;
    }

    public static void forgetDeleted(SubworldDim d) {
        data.dims.remove(d);
        markDirty();
    }

    @Nullable
    public static SubworldDim find(String subworld, String role) {
        for (SubworldDim d : BY_DIM.values()) {
            if (d.subworld.equals(subworld) && d.role.equals(role)) {
                return d;
            }
        }
        return null;
    }

    public static List<SubworldDim> instances(String subworld, String owner) {
        List<SubworldDim> out = new ArrayList<SubworldDim>();
        for (SubworldDim d : BY_DIM.values()) {
            if (d.subworld.equals(subworld) && d.isInstance() && d.owner.equals(owner)) {
                out.add(d);
            }
        }
        out.sort((a, b) -> Long.compare(a.created, b.created));
        return out;
    }

    @Nullable
    public static SubworldDim activeInstance(String subworld, String owner) {
        Integer dim = data.active.get(owner + "|" + subworld);
        SubworldDim d = dim == null ? null : BY_DIM.get(dim);
        if (d != null && d.subworld.equals(subworld) && d.owner.equals(owner)) {
            return d;
        }
        List<SubworldDim> list = instances(subworld, owner);
        return list.isEmpty() ? null : list.get(0);
    }

    public static void setActive(String subworld, String owner, int dim) {
        data.active.put(owner + "|" + subworld, dim);
        markDirty();
    }

    /** Новый номер измерения: не занятый ни Forge, ни нами. */
    public static int allocateDim() {
        int id = Math.max(EcoConfig.subworldsDimStart, data.nextDim);
        while (DimensionManager.isDimensionRegistered(id) || containsDim(id)) {
            id++;
        }
        data.nextDim = id + 1;
        markDirty();
        return id;
    }

    private static boolean containsDim(int id) {
        for (SubworldDim d : data.dims) {
            if (d.dim == id) {
                return true;
            }
        }
        return false;
    }

    public static SubworldDim addDim(String subworld, String role, String folder, String owner, String ownerName,
                                     String saveName) {
        SubworldDim d = new SubworldDim();
        d.dim = allocateDim();
        d.subworld = subworld;
        d.role = role;
        d.folder = folder == null || folder.isEmpty() ? "i_" + d.dim : folder;
        d.owner = owner == null ? "" : owner;
        d.ownerName = ownerName == null ? "" : ownerName;
        d.saveName = saveName == null ? "" : saveName;
        d.created = System.currentTimeMillis();
        d.lastUsed = d.created;
        Subworld sw = get(subworld);
        d.border = sw != null && d.isInstance() ? sw.borderStart : 0;
        d.normalize();
        data.dims.add(d);
        BY_DIM.put(d.dim, d);
        save();
        return d;
    }

    /** Пометить измерение удалённым. */
    public static void deleteDim(SubworldDim d) {
        d.deleted = true;
        BY_DIM.remove(d.dim);
        data.active.values().removeIf(v -> v == d.dim);
        save();
    }

    // --- папки ------------------------------------------------------------------

    public static File worldsRoot() {
        return new File(DataStore.worldRoot(), "ecoserv_worlds");
    }

    public static File subworldDir(String subworld) {
        return new File(worldsRoot(), subworld);
    }

    public static File folderOf(SubworldDim d) {
        return new File(subworldDir(d.subworld), d.folder);
    }

    /** Для WorldProvider.getSaveFolder(): путь относительно папки мира. */
    @Nullable
    public static String saveFolder(int dim) {
        SubworldDim d = BY_DIM.get(dim);
        return d == null ? null : "ecoserv_worlds/" + d.subworld + "/" + d.folder;
    }

    // --- доступ игроков ---------------------------------------------------------

    public static boolean unlocked(String uuid, String subworld) {
        List<String> list = data.unlocks.get(uuid);
        return list != null && list.contains(subworld);
    }

    public static void unlock(String uuid, String subworld) {
        List<String> list = data.unlocks.get(uuid);
        if (list == null) {
            list = new ArrayList<String>();
            data.unlocks.put(uuid, list);
        }
        if (!list.contains(subworld)) {
            list.add(subworld);
            save();
        }
    }

    public static boolean lock(String uuid, String subworld) {
        List<String> list = data.unlocks.get(uuid);
        boolean removed = list != null && list.remove(subworld);
        if (removed) {
            save();
        }
        return removed;
    }

    /** Сбросить отметки «получил стартовый набор» у всех игроков для подмира. */
    public static int resetKits(String subworld) {
        int n = 0;
        for (List<String> list : data.kits.values()) {
            if (list.remove(subworld)) {
                n++;
            }
        }
        if (n > 0) {
            save();
        }
        return n;
    }

    /** Отметить, что игрок получил стартовый набор; false — уже получал. */
    public static boolean takeKit(String uuid, String subworld) {
        List<String> list = data.kits.get(uuid);
        if (list == null) {
            list = new ArrayList<String>();
            data.kits.put(uuid, list);
        }
        if (list.contains(subworld)) {
            return false;
        }
        list.add(subworld);
        markDirty();
        return true;
    }
}
