package com.ecoserv.subworld;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.server.ServerTasks;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.FileOps;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Создание и удаление подмиров и локальных миров, публикация шаблона, уборка папок. */
public final class SubworldManager {

    /** Подмиры, у которых идёт долгая операция (установка загруженного мира, публикация в фоне). */
    private static final Set<String> BUSY = ConcurrentHashMap.newKeySet();
    /** Подмиры, чью прерванную установку нечем продолжить (мира нет в папке загрузок): закрыты до перезапуска. */
    private static final Set<String> BLOCKED = ConcurrentHashMap.newKeySet();
    /**
     * Измерения, в папки которых (или из которых) сейчас копируют: шаблон — при публикации в фоне, мир —
     * при установке загруженного мира. Флаг задачи копирования: «мир загружали, пока шло копирование».
     * Флаг держит и поток копирования, поэтому он переживает сброс этой таблицы при новом запуске сервера.
     */
    private static final Map<Integer, java.util.concurrent.atomic.AtomicBoolean> COPYING =
            new ConcurrentHashMap<Integer, java.util.concurrent.atomic.AtomicBoolean>();
    /** Миры, которые ждут выгрузки, чтобы стереть записанное во время установки, и что сделать после. */
    private static final Map<Integer, Runnable> PURGE = new ConcurrentHashMap<Integer, Runnable>();
    /**
     * Метка в папке мира: «всё, что мир здесь записал, — мусор, стереть до использования». Ставится на время
     * установки загруженного мира и переживает перезапуск (стирается при следующем запуске сервера).
     */
    static final String PURGE_MARK = "ecoserv_purge";

    private SubworldManager() {
    }

    /** Новый запуск сервера (в одиночной игре — другой мир): прошлые долгие операции не в счёт. */
    public static void reset() {
        BUSY.clear();
        BLOCKED.clear();
        COPYING.clear();
        PURGE.clear();
    }

    /** Подмир занят (идёт установка или публикация) или закрыт до перезапуска: входить в него нельзя. */
    public static boolean isBusy(String subworld) {
        return BUSY.contains(subworld) || BLOCKED.contains(subworld);
    }

    /** Идёт долгая операция (её поток пишет в папку подмира): удалять нельзя. */
    public static boolean isWorking(String subworld) {
        return BUSY.contains(subworld);
    }

    /** Измерение занятого подмира: наши телепорты туда не ведут, пока идёт установка или публикация. */
    public static boolean isBusyDim(int dim) {
        if (BUSY.isEmpty() && BLOCKED.isEmpty()) {
            return false;
        }
        SubworldDim d = SubworldRegistry.dim(dim);
        return d != null && isBusy(d.subworld);
    }

    static void setBusy(String subworld, boolean busy) {
        if (busy) {
            BUSY.add(subworld);
        } else {
            BUSY.remove(subworld);
        }
    }

    /** Текст для админов о подмирах, закрытых до перезапуска, или null. */
    @Nullable
    public static String blockedNotice() {
        if (BLOCKED.isEmpty()) {
            return null;
        }
        return "&cПодмиры " + String.join(", ", BLOCKED) + ": установка своего мира прервалась, а мира больше нет в "
                + "папке загрузок. Верни его в " + SubworldUploads.root().getName() + " и перезапусти сервер (или удали подмир).";
    }

    // --- копирование файлов миров ----------------------------------------------------

    /** Начинается копирование, связанное с измерением: вернуть флаг «мир загружали за это время». */
    static java.util.concurrent.atomic.AtomicBoolean startCopy(int dim) {
        java.util.concurrent.atomic.AtomicBoolean loaded =
                new java.util.concurrent.atomic.AtomicBoolean(DimensionManager.getWorld(dim) != null);
        COPYING.put(dim, loaded);
        return loaded;
    }

    static void endCopy(int dim) {
        COPYING.remove(dim);
    }

    /**
     * Мир загрузился (из WorldEvent.Load). Если в это время идёт копирование его файлов, результат уже
     * нельзя считать целым: публикация отменится, а записанное во время установки будет стёрто.
     */
    static void onWorldLoaded(int dim) {
        java.util.concurrent.atomic.AtomicBoolean loaded = COPYING.get(dim);
        if (loaded != null && !loaded.getAndSet(true)) {
            SubworldDim d = SubworldRegistry.dim(dim);
            if (d != null && isWorking(d.subworld)) {
                ChatUtil.broadcastAdmins(hasPurgeMark(d)
                        ? "&eМир подмира &f" + d.subworld + " &eзагрузили во время установки — всё, что он запишет, "
                        + "сотрётся после её окончания."
                        : "&eШаблон подмира &f" + d.subworld + " &eзагрузили во время публикации — она будет "
                        + "отменена. Опубликуй ещё раз, когда закончится.");
            }
        }
    }

    // --- стирание записанного во время установки ----------------------------------------

    /**
     * В папке мира уже есть записанные чанки — мир обжит. Такой мир никогда не стираем (метку очистки
     * ставим только миру, который до установки был пустым).
     */
    static boolean hasWorldData(SubworldDim d) {
        File[] mca = new File(SubworldRegistry.folderOf(d), "region").listFiles();
        if (mca == null) {
            return false;
        }
        for (File f : mca) {
            if (f.getName().endsWith(".mca") && f.length() > 0L) {
                return true;
            }
        }
        return false;
    }

    static void markPurge(SubworldDim d) {
        File dir = SubworldRegistry.folderOf(d);
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("не создать " + dir);
            }
            java.nio.file.Files.write(new File(dir, PURGE_MARK).toPath(), new byte[0]);
        } catch (IOException e) {
            EcoServ.LOG.warn("Не удалось поставить метку очистки в {}: {}", dir, e.toString());
        }
    }

    static boolean hasPurgeMark(SubworldDim d) {
        return new File(SubworldRegistry.folderOf(d), PURGE_MARK).isFile();
    }

    static void clearPurgeMark(SubworldDim d) {
        File mark = new File(SubworldRegistry.folderOf(d), PURGE_MARK);
        if (mark.isFile() && !mark.delete()) {
            EcoServ.LOG.warn("Не удалось снять метку очистки {}", mark);
        }
    }

    /** Стереть всё, что записал мир (регионы, данные, заявки чанк-лоадеров); точку появления оставить. */
    static void purgeFolder(SubworldDim d) {
        File dir = SubworldRegistry.folderOf(d);
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.getName().equals(SubworldInfo.FILE) || f.getName().equals(PURGE_MARK)) {
                continue;
            }
            if (!FileOps.deleteTree(f)) {
                EcoServ.LOG.warn("Не удалось стереть {}", f);
            }
        }
        EcoServ.LOG.info("Папка мира {} очищена от записанного во время установки", dir);
    }

    /**
     * Мир загружали во время установки: он записал пустоту там, где теперь есть загруженный мир. Выгрузить
     * его (игроков — наружу), стереть записанное, потом выполнить finish. Подмир до этого остаётся занятым.
     */
    static void purgeAfterUnload(SubworldDim d, Runnable finish) {
        PURGE.put(d.dim, finish);
        tryPurge(d.dim);
    }

    private static void tryPurge(int dim) {
        Runnable finish = PURGE.get(dim);
        if (finish == null) {
            return;
        }
        SubworldDim d = SubworldRegistry.dim(dim);
        if (d == null) {
            // подмир удалили — папка сотрётся вместе с ним
            PURGE.remove(dim);
            return;
        }
        WorldServer w = DimensionManager.getWorld(dim);
        if (w != null) {
            for (EntityPlayer p : new ArrayList<EntityPlayer>(w.playerEntities)) {
                if (p instanceof EntityPlayerMP) {
                    SubworldTeleport.evict((EntityPlayerMP) p, "&eМир подмира установлен — зайди в него снова через меню миров.");
                }
            }
            releaseTickets(w);
            DimensionManager.unloadWorld(dim);
            // продолжение — после выгрузки (onWorldUnloaded) или при следующей попытке (retryUnloads)
            return;
        }
        PURGE.remove(dim);
        purgeFolder(d);
        try {
            finish.run();
        } catch (Throwable t) {
            EcoServ.LOG.error("Ошибка после очистки мира {}", dim, t);
        }
    }

    // --- подмиры ------------------------------------------------------------------

    /**
     * Создать подмир. Возвращает текст ошибки или null.
     * Подмир с локальными мирами создаётся выключенным: админы сначала готовят шаблон.
     */
    @Nullable
    public static String create(Subworld sw, @Nullable EntityPlayerMP creator) {
        if (SubworldRegistry.loadFailed()) {
            // новый подмир нельзя было бы сохранить, а номер его измерения мог совпасть со старым
            return SubworldRegistry.BROKEN_NOTICE;
        }
        sw.id = SubworldRegistry.key(sw.id);
        if (!SubworldRegistry.idAvailable(sw.id)) {
            return "&cИмя «" + sw.id + "» занято или некорректно (буквы, цифры, _; до 32 символов).";
        }
        if (Subworld.CUSTOM.equals(sw.type)) {
            SubworldUploads.Upload up = SubworldUploads.find(sw.template);
            if (up == null || up.error != null) {
                return "&cЗагруженный мир «" + sw.template + "» не найден или не подходит"
                        + (up != null && up.error != null ? ": " + up.error : "") + ".";
            }
        }
        sw.creator = creator == null ? "server" : creator.getName();
        sw.created = System.currentTimeMillis();
        sw.normalize();
        if (sw.perOwner()) {
            sw.enabled = false;
        }
        SubworldRegistry.add(sw);
        String role = sw.perOwner() ? SubworldDim.TEMPLATE : SubworldDim.SHARED;
        SubworldDim d = SubworldRegistry.addDim(sw.id, role, role, "", "", "");
        if (!SubworldRegistry.folderOf(d).mkdirs() && !SubworldRegistry.folderOf(d).isDirectory()) {
            EcoServ.LOG.warn("Не удалось создать папку подмира {}", SubworldRegistry.folderOf(d));
        }
        // сначала регистрация и рассылка клиентам, потом установка (она снимет измерение с регистрации
        // на сервере до конца копирования)
        SubworldDims.registerAndSync(d);
        if (Subworld.CUSTOM.equals(sw.type)) {
            SubworldUploads.install(sw, d, creator);
        }
        EcoServ.LOG.info("Создан подмир {} ({}), измерение {}", sw.id, sw.typeTitle(), d.dim);
        return null;
    }

    /**
     * Удалить подмир целиком: все его миры, его прайс и правила модов «только в этом подмире».
     * Возвращает предупреждение для админа (порталы, которые вели в подмир) или пустую строку.
     */
    public static String delete(Subworld sw) {
        if (!SubworldRegistry.isCurrent(sw)) {
            // уже удалён (второй админ, старое меню)
            return "";
        }
        for (SubworldDim d : SubworldRegistry.dimsOf(sw.id)) {
            deleteDim(d, "&eПодмир «" + sw.name + "&e» удалён.");
        }
        SubworldRegistry.remove(sw);
        SubworldLayers.invalidate(sw.id);
        SubworldDims.refreshCache();
        BLOCKED.remove(sw.id);
        // миры, которые не были загружены, уже стёрты — если так со всеми, папка подмира стирается сразу
        finishSubworld(sw.id);
        com.ecoserv.shop.ShopList.dropScope(sw.id);
        int rules = forgetInItemRules(sw.id);
        List<String> portals = new ArrayList<String>();
        for (com.ecoserv.region.Region r : com.ecoserv.region.RegionManager.all(com.ecoserv.region.Region.PORTAL)) {
            if (SubworldRegistry.key(r.target).equals(sw.id)) {
                portals.add(r.name);
            }
        }
        EcoServ.LOG.info("Подмир {} удалён (папка будет стёрта), правил предметов убрано: {}, порталов в него: {}",
                sw.id, rules, portals.size());
        if (portals.isEmpty()) {
            return "";
        }
        return "&eВ него вели порталы: &f" + String.join(", ", portals) + " &e— перенастрой или удали их.";
    }

    /**
     * Убрать удалённый подмир из списков миров в запретах предметов и модов. Правило, которое
     * действовало только в нём, удаляется: с пустым списком миров оно действовало бы везде.
     */
    private static int forgetInItemRules(String id) {
        String name = com.ecoserv.world.WorldNames.normalize(id);
        List<com.ecoserv.restrict.ItemRule> drop = new ArrayList<com.ecoserv.restrict.ItemRule>();
        int changed = 0;
        for (com.ecoserv.restrict.ItemRule rule : com.ecoserv.restrict.ItemRules.all()) {
            boolean inWorlds = rule.worlds.removeIf(n -> com.ecoserv.world.WorldNames.normalize(n).equals(name));
            boolean inExcept = rule.except.removeIf(n -> com.ecoserv.world.WorldNames.normalize(n).equals(name));
            if (inWorlds && rule.worlds.isEmpty()) {
                drop.add(rule);
            }
            if (inWorlds || inExcept) {
                changed++;
            }
        }
        for (com.ecoserv.restrict.ItemRule rule : drop) {
            com.ecoserv.restrict.ItemRules.remove(rule.item);
        }
        if (changed > 0) {
            com.ecoserv.restrict.ItemRules.changed();
        }
        return changed;
    }

    // --- локальные миры ---------------------------------------------------------------

    /** Сколько ещё сохранений может завести владелец. */
    public static int savesLeft(Subworld sw, String owner) {
        return Math.max(0, sw.maxSaves - SubworldRegistry.instances(sw.id, owner).size());
    }

    /** Цена следующего сохранения (первое — бесплатно). */
    public static long nextSavePrice(Subworld sw, String owner) {
        return SubworldRegistry.instances(sw.id, owner).isEmpty() ? 0L : sw.savePrice;
    }

    /**
     * Новый локальный мир владельца. by — кто создаёт (платит за сохранение сверх первого).
     * null — нельзя (сообщение уже отправлено игроку).
     */
    @Nullable
    public static SubworldDim createInstance(Subworld sw, String owner, String ownerName, String saveName,
                                             @Nullable EntityPlayerMP by) {
        if (!SubworldRegistry.isCurrent(sw)) {
            tell(by, "&cЭтого подмира уже нет (или его пересоздали) — открой меню заново.");
            return null;
        }
        if (!sw.perOwner()) {
            return null;
        }
        if (isBusy(sw.id)) {
            tell(by, "&eПодмир сейчас готовится (установка мира или публикация) — подожди немного.");
            return null;
        }
        List<SubworldDim> existing = SubworldRegistry.instances(sw.id, owner);
        if (existing.size() >= sw.maxSaves) {
            tell(by, "&cУже " + existing.size() + " из " + sw.maxSaves + " сохранений — удали лишнее.");
            return null;
        }
        int total = 0;
        for (SubworldDim d : SubworldRegistry.dims()) {
            if (d.isInstance()) {
                total++;
            }
        }
        if (total >= EcoConfig.subworldsMaxInstances) {
            tell(by, "&cНа сервере уже слишком много локальных миров — сообщи админу.");
            return null;
        }
        long price = existing.isEmpty() ? 0L : sw.savePrice;
        if (price > 0 && by != null && !Perms.isAdmin(by)) {
            if (!EconomyManager.take(by.getUniqueID(), price)) {
                tell(by, "&cНовое сохранение стоит &6" + Fmt.money(price) + "&c — не хватает денег.");
                return null;
            }
            tell(by, "&7Списано &6" + Fmt.money(price) + " &7за новое сохранение.");
        }
        String name = saveName == null || saveName.trim().isEmpty() ? "Мир " + (existing.size() + 1) : saveName.trim();
        SubworldDim d = SubworldRegistry.addDim(sw.id, SubworldDim.INSTANCE, "", owner, ownerName, name);
        d.templateVersion = sw.templateVersion;
        SubworldRegistry.setActive(sw.id, owner, d.dim);
        SubworldRegistry.save();
        SubworldDims.registerAndSync(d);
        EcoServ.LOG.info("Локальный мир {} подмира {} для {} (измерение {})", name, sw.id, owner, d.dim);
        return d;
    }

    private static void tell(@Nullable EntityPlayerMP player, String text) {
        if (player != null) {
            ChatUtil.send(player, text);
        }
    }

    // --- удаление -----------------------------------------------------------------------

    /** Удалить измерение: игроков — наружу, мир — выгрузить, папку — стереть. */
    public static void deleteDim(SubworldDim d, String evictMessage) {
        if (d.deleted || SubworldRegistry.dim(d.dim) != d) {
            // уже удалено: не трогаем измерение с тем же номером
            return;
        }
        WorldServer w = DimensionManager.getWorld(d.dim);
        if (w != null) {
            for (EntityPlayer p : new ArrayList<EntityPlayer>(w.playerEntities)) {
                if (p instanceof EntityPlayerMP) {
                    SubworldTeleport.evict((EntityPlayerMP) p, evictMessage);
                }
            }
        }
        SubworldRegistry.deleteDim(d);
        SubworldDims.refreshCache();
        if (w != null) {
            releaseTickets(w);
            DimensionManager.unloadWorld(d.dim);
            // остальное — после выгрузки (WorldEvent.Unload)
        } else {
            cleanupNow(d);
        }
    }

    /** Мир выгружен (из события): удалённое измерение — стереть на следующем тике, после записи файлов. */
    static void onWorldUnloaded(final int dim) {
        if (PURGE.containsKey(dim)) {
            ServerTasks.later(2, new Runnable() {
                @Override
                public void run() {
                    if (DimensionManager.getWorld(dim) == null) {
                        tryPurge(dim);
                    }
                }
            });
        }
        final SubworldDim d = deletedDim(dim);
        if (d != null) {
            ServerTasks.later(2, new Runnable() {
                @Override
                public void run() {
                    if (DimensionManager.getWorld(dim) == null) {
                        cleanupNow(d);
                    }
                }
            });
        }
    }

    /**
     * Раз в 10 секунд: удалённые миры, которые так и не выгрузились (их держат чанк-лоадеры модов или
     * игрок, которого не удалось выгнать), — отпустить принудительную загрузку чанков и выгрузить снова.
     */
    static void retryUnloads() {
        for (SubworldDim d : SubworldRegistry.deletedDims()) {
            WorldServer w = DimensionManager.getWorld(d.dim);
            if (w == null) {
                continue;
            }
            for (EntityPlayer p : new ArrayList<EntityPlayer>(w.playerEntities)) {
                if (p instanceof EntityPlayerMP) {
                    SubworldTeleport.evict((EntityPlayerMP) p, "&eЭтот мир удалён.");
                }
            }
            releaseTickets(w);
            DimensionManager.unloadWorld(d.dim);
        }
        for (Integer dim : new ArrayList<Integer>(PURGE.keySet())) {
            tryPurge(dim);
        }
    }

    /** Отпустить принудительную загрузку чанков (чанк-лоадеры модов): иначе Forge не выгрузит мир. */
    private static void releaseTickets(WorldServer w) {
        Set<net.minecraftforge.common.ForgeChunkManager.Ticket> tickets =
                new java.util.LinkedHashSet<net.minecraftforge.common.ForgeChunkManager.Ticket>(
                        net.minecraftforge.common.ForgeChunkManager.getPersistentChunksFor(w).values());
        for (net.minecraftforge.common.ForgeChunkManager.Ticket t : tickets) {
            try {
                net.minecraftforge.common.ForgeChunkManager.releaseTicket(t);
            } catch (Throwable ignored) {
                // уже отпущен
            }
        }
    }

    @Nullable
    private static SubworldDim deletedDim(int dim) {
        for (SubworldDim d : SubworldRegistry.deletedDims()) {
            if (d.dim == dim) {
                return d;
            }
        }
        return null;
    }

    private static void cleanupNow(SubworldDim d) {
        if (DimensionManager.getWorld(d.dim) != null) {
            return;
        }
        if (DimensionManager.isDimensionRegistered(d.dim) && DimensionManager.getProviderType(d.dim) == SubworldDims.TYPE) {
            try {
                DimensionManager.unregisterDimension(d.dim);
                SubworldDims.forget(d.dim);
            } catch (Exception ignored) {
                // уже снято
            }
        }
        File folder = SubworldRegistry.folderOf(d);
        if (FileOps.deleteTree(folder)) {
            SubworldRegistry.forgetDeleted(d);
        } else {
            EcoServ.LOG.warn("Папку {} не удалось стереть сейчас — попробую при следующем запуске", folder);
        }
        SubworldRegistry.saveIfDirty();
        finishSubworld(d.subworld);
    }

    /**
     * Подмир удалён и все его миры уже стёрты: стереть его папку (слои шаблона и т.п.) сразу, а не при
     * следующем запуске, и освободить его имя.
     */
    static void finishSubworld(String id) {
        if (SubworldRegistry.get(id) != null || SubworldRegistry.hasDimRecords(id)
                || !SubworldRegistry.deletedSubworlds().contains(id)) {
            return;
        }
        File dir = SubworldRegistry.subworldDir(id);
        LayerRegions.closeUnder(dir);
        if (!dir.exists() || FileOps.deleteTree(dir)) {
            SubworldRegistry.forgetDeletedSubworld(id);
            SubworldRegistry.saveIfDirty();
            EcoServ.LOG.info("Подмир {} удалён полностью, папка стёрта", id);
            ChatUtil.broadcastAdmins("&7Подмир &f" + id + " &7удалён полностью: папка стёрта, имя свободно.");
        } else {
            EcoServ.LOG.warn("Папку подмира {} не удалось стереть сейчас — попробую при следующем запуске", dir);
        }
    }

    /** При запуске сервера: стереть папки удалённых миров и подмиров, недописанные слои. */
    public static void cleanupOnStart() {
        SubworldUploads.cleanIncoming();
        if (SubworldRegistry.loadFailed()) {
            return;
        }
        for (SubworldDim d : new ArrayList<SubworldDim>(SubworldRegistry.deletedDims())) {
            if (FileOps.deleteTree(SubworldRegistry.folderOf(d))) {
                SubworldRegistry.forgetDeleted(d);
            }
        }
        // папки удалённых подмиров целиком (только тех, что удалили через мод — чужие папки не трогаем)
        for (String id : SubworldRegistry.deletedSubworlds()) {
            if (SubworldRegistry.get(id) != null) {
                SubworldRegistry.forgetDeletedSubworld(id);
                continue;
            }
            if (SubworldRegistry.hasDimRecords(id)) {
                continue;
            }
            java.io.File dir = SubworldRegistry.subworldDir(id);
            LayerRegions.closeUnder(dir);
            if (FileOps.deleteTree(dir)) {
                SubworldRegistry.forgetDeletedSubworld(id);
                EcoServ.LOG.info("Стёрта папка удалённого подмира {}", id);
            }
        }
        // миры, которые загружали во время установки (или сервер остановился раньше, чем их очистили):
        // записанное ими — пустота поверх загруженного мира, стираем до регистрации измерений
        for (SubworldDim d : SubworldRegistry.dims()) {
            if (hasPurgeMark(d)) {
                purgeFolder(d);
                clearPurgeMark(d);
                Subworld owner = SubworldRegistry.get(d.subworld);
                SubworldLayers.Manifest v0 = owner == null ? null : SubworldLayers.manifest(owner.id, 0);
                if (owner != null && Subworld.CUSTOM.equals(owner.type) && v0 != null && v0.complete) {
                    // копирование закончилось, а сервер остановился раньше, чем записалась точка появления
                    SubworldUploads.writeSpawnFromLayer(owner, d);
                }
            }
        }
        for (Subworld sw : SubworldRegistry.all()) {
            File[] layers = SubworldLayers.root(sw.id).listFiles();
            if (layers == null) {
                continue;
            }
            SubworldLayers.invalidate(sw.id);
            int latest = SubworldLayers.latest(sw.id);
            if (latest > sw.templateVersion && sw.perOwner()) {
                // публикация в фоне докопировала слой, но сервер остановился раньше, чем версия записалась
                sw.templateVersion = latest;
                SubworldRegistry.markDirty();
            }
            for (File layer : layers) {
                int v;
                try {
                    v = Integer.parseInt(layer.getName());
                } catch (NumberFormatException e) {
                    continue;
                }
                SubworldLayers.Manifest m = SubworldLayers.manifest(sw.id, v);
                if (m == null || !m.complete) {
                    if (v == 0 && Subworld.CUSTOM.equals(sw.type)) {
                        // установка загруженного мира прервалась — начать заново
                        SubworldDim target = SubworldRegistry.find(sw.id, sw.perOwner() ? SubworldDim.TEMPLATE : SubworldDim.SHARED);
                        SubworldUploads.Upload up = SubworldUploads.find(sw.template);
                        if (up == null || up.error != null) {
                            // исходного мира уже нет: подмир закрыт, а его мир не сохраняется до перезапуска —
                            // иначе пустота вместо недокопированного мира записалась бы навсегда
                            EcoServ.LOG.error("Подмир {}: установка мира «{}» прервалась, а в папке загрузок его больше "
                                    + "нет{}. Верни мир в {} и перезапусти сервер (или удали подмир).", sw.id, sw.template,
                                    up != null ? " (" + up.error + ")" : "", SubworldUploads.root());
                            BLOCKED.add(sw.id);
                            if (target != null && !hasWorldData(target)) {
                                // что пустой мир запишет, если его всё же загрузят, сотрётся при следующем запуске
                                markPurge(target);
                            }
                            continue;
                        }
                        if (target != null) {
                            FileOps.deleteTree(layer);
                            SubworldUploads.install(sw, target, null);
                        }
                        continue;
                    }
                    FileOps.deleteTree(layer);
                    EcoServ.LOG.info("Стёрт недописанный слой {} подмира {}", v, sw.id);
                }
            }
        }
        SubworldLayers.invalidateAll();
        SubworldRegistry.saveIfDirty();
    }

    // --- публикация шаблона ---------------------------------------------------------------

    /**
     * Опубликовать шаблон: изменённые файлы регионов → новый слой. Локальные миры берут из него
     * ещё не посещённые чанки сразу, а посещённые — обновляются (глобальное редактирование).
     * Возвращает сообщение для админа.
     */
    public static String publish(final Subworld sw, @Nullable final EntityPlayerMP by) {
        if (!SubworldRegistry.isCurrent(sw)) {
            return "&cЭтого подмира уже нет — открой меню заново.";
        }
        if (!sw.perOwner()) {
            return "&eУ подмира нет локальных миров — все и так играют в одном мире, публиковать нечего.";
        }
        if (isBusy(sw.id)) {
            return "&eПодмир занят (идёт установка или публикация) — подожди.";
        }
        final SubworldDim tpl = SubworldRegistry.find(sw.id, SubworldDim.TEMPLATE);
        if (tpl == null) {
            return "&cУ подмира нет шаблона.";
        }
        WorldServer w = DimensionManager.getWorld(tpl.dim);
        if (w != null && w.disableLevelSaving) {
            // идёт бэкап (или save-off): мир не сохранится, и публикация молча взяла бы старые файлы
            return "&eСохранение мира сейчас выключено (идёт бэкап или save-off) — опубликуй, когда оно закончится, "
                    + "иначе последние правки не попадут в публикацию.";
        }
        if (w != null) {
            try {
                w.saveAllChunks(true, null);
            } catch (MinecraftException e) {
                return "&cНе удалось сохранить шаблон: " + e.getMessage();
            }
            SubworldInfo.save(w, tpl);
        }
        try {
            ThreadedFileIOBase.getThreadedIOInstance().waitForFinish();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        File tplDir = SubworldRegistry.folderOf(tpl);
        final Map<String, long[]> now = SubworldLayers.regionFiles(tplDir);
        SubworldLayers.Manifest prev = lastPublish(sw);
        final List<String> changed = new ArrayList<String>();
        // регионы, в которые мир сохранял чанки после прошлой публикации (не зависит от того,
        // обновила ли ОС время изменения открытого файла — из-за этого раньше приходилось жать дважды)
        final java.util.Set<String> touched = PublishTracker.touched(sw.id);
        long bytes = 0L;
        for (Map.Entry<String, long[]> e : now.entrySet()) {
            long[] old = prev == null ? null : prev.files.get(e.getKey());
            boolean diff = old == null || old.length < 2 || old[0] != e.getValue()[0] || old[1] != e.getValue()[1];
            if (!diff && prev != null && old[1] >= prev.time - 2500L) {
                // файл менялся в ту же секунду, что и прошлая публикация — сверяем содержимое
                try {
                    diff = old.length < 3 || old[2] != FileOps.crc(new File(new File(tplDir, "region"), e.getKey()));
                } catch (IOException ex) {
                    diff = true;
                }
            }
            if (!diff && touched.contains(e.getKey())) {
                diff = true;
            }
            if (diff) {
                changed.add(e.getKey());
                bytes += e.getValue()[0];
            } else if (old != null && old.length >= 3) {
                e.setValue(new long[]{e.getValue()[0], e.getValue()[1], old[2]});
            }
        }
        if (changed.isEmpty()) {
            return "&eВ шаблоне нет изменений с прошлой публикации.";
        }
        final int version = Math.max(sw.templateVersion, SubworldLayers.latest(sw.id)) + 1;
        final File layerDir = SubworldLayers.layerDir(sw.id, version);
        final String byName = by == null ? "server" : by.getName();
        long limit = EcoConfig.subworldsPublishSyncMb * 1024L * 1024L;
        if (bytes <= limit) {
            try {
                copyLayer(layerDir, tplDir, version, changed, now, byName, null);
            } catch (IOException e) {
                FileOps.deleteTree(layerDir);
                return "&cОшибка копирования: " + e.getMessage();
            }
            finishPublish(sw, version, by);
            PublishTracker.published(sw.id, touched);
            return "&aОпубликована версия &f" + version + " &a(" + changed.size() + " файлов регионов, "
                    + Fmt.bytes(bytes) + ").";
        }
        if (w != null) {
            return "&eИзменений много (" + Fmt.bytes(bytes) + ") — для публикации в фоне шаблон должен быть выгружен. "
                    + "Выйдите из шаблона все и нажмите ещё раз через минуту.";
        }
        setBusy(sw.id, true);
        // если шаблон загрузят, пока копируются файлы, слой не станет «готовым» (мог записаться наполовину)
        final java.util.concurrent.atomic.AtomicBoolean loadedDuringCopy = startCopy(tpl.dim);
        final File dir = tplDir;
        final long total = bytes;
        final net.minecraft.server.MinecraftServer server = PlayerUtil.server();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    copyLayer(layerDir, dir, version, changed, now, byName, loadedDuringCopy);
                } catch (Throwable e) {
                    error = e.toString();
                    FileOps.deleteTree(layerDir);
                }
                final String err = error;
                if (server == null || PlayerUtil.server() != server) {
                    // сервер остановлен (или в одиночной игре открыт другой мир) — слой готов, версию
                    // поднимут при следующей публикации
                    return;
                }
                server.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        setBusy(sw.id, false);
                        endCopy(tpl.dim);
                        if (err != null) {
                            ChatUtil.broadcastAdmins("&cПубликация подмира " + sw.id + " не удалась: " + err);
                            return;
                        }
                        if (!SubworldRegistry.isCurrent(sw)) {
                            return;
                        }
                        finishPublish(sw, version, by);
                        PublishTracker.published(sw.id, touched);
                        ChatUtil.broadcastAdmins("&aПодмир &f" + sw.id + "&a: опубликована версия " + version
                                + " (" + Fmt.bytes(total) + ").");
                    }
                });
            }
        }, "EcoServ publish " + sw.id);
        t.setDaemon(true);
        t.start();
        return "&eПубликация " + Fmt.bytes(bytes) + " идёт в фоне, шаблон закрыт до окончания.";
    }

    /** Последний слой-публикация (или слой загруженного мира). */
    @Nullable
    private static SubworldLayers.Manifest lastPublish(Subworld sw) {
        int latest = SubworldLayers.latest(sw.id);
        if (latest < 0) {
            return null;
        }
        SubworldLayers.Manifest m = SubworldLayers.manifest(sw.id, latest);
        // у слоя загруженного мира в files — файлы загрузки, а не шаблона: сравнивать не с чем
        return m != null && m.source.startsWith("upload") ? null : m;
    }

    private static void copyLayer(File layerDir, File tplDir, int version, List<String> changed,
                                  Map<String, long[]> now, String by,
                                  @Nullable java.util.concurrent.atomic.AtomicBoolean loadedDuringCopy) throws IOException {
        File out = new File(layerDir, "region");
        if (!out.isDirectory() && !out.mkdirs()) {
            throw new IOException("не создать " + out);
        }
        SubworldLayers.Manifest m = new SubworldLayers.Manifest();
        m.version = version;
        m.time = System.currentTimeMillis();
        m.by = by;
        m.source = "publish";
        long bytes = 0L;
        for (String name : changed) {
            File src = new File(new File(tplDir, "region"), name);
            long crc = FileOps.copy(src, new File(out, name));
            long[] info = now.get(name);
            now.put(name, new long[]{info[0], info[1], crc});
            bytes += info[0];
        }
        m.files.putAll(now);
        m.bytes = bytes;
        if (loadedDuringCopy != null && loadedDuringCopy.get()) {
            // шаблон загружали во время копирования: файлы могли записаться наполовину — слой не готов
            throw new IOException("шаблон загрузили во время публикации — опубликуй ещё раз");
        }
        m.complete = true;
        SubworldLayers.writeManifest(layerDir, m);
    }

    private static void finishPublish(Subworld sw, int version, @Nullable EntityPlayerMP by) {
        if (!SubworldRegistry.isCurrent(sw)) {
            // подмир удалили, пока копировался слой: версию поднимать некому
            return;
        }
        sw.templateVersion = version;
        SubworldRegistry.save();
        SubworldLayers.invalidate(sw.id);
        TemplateEdits.onPublished(sw, version);
        EcoServ.LOG.info("Подмир {}: опубликована версия {} ({})", sw.id, version, by == null ? "server" : by.getName());
    }
}
