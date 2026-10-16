package com.ecoserv.backup;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Автоматические бэкапы мира с ротацией старых архивов.
 * <p>
 * Порядок работы: предупреждение -> сохранение мира и отключение автосейва ->
 * фоновое сжатие в zip -> включение автосейва -> удаление лишних архивов.
 * Сервер не «замерзает»: сжатие идёт в отдельном потоке, в главном только сохранение.
 */
public final class BackupManager {

    public static final String PREFIX = "ecoserv-";

    private static final AtomicBoolean running = new AtomicBoolean(false);

    private static MinecraftServer server;
    private static long nextBackupTime;
    private static long scheduledStartAt;
    private static long lastBackupTime;
    private static long lastDurationMillis;
    private static long lastSizeBytes;
    private static String lastError = "";
    private static boolean playersSeenSinceBackup;
    private static ICommandSender pendingRequester;

    private BackupManager() {
    }

    public static void onServerStart(MinecraftServer mcServer) {
        server = mcServer;
        scheduledStartAt = 0L;
        playersSeenSinceBackup = false;
        rescheduleNext();
        File dir = backupDirectory();
        EcoServ.LOG.info("Бэкапы: {} (каждые {} мин, хранить {} шт / {} дней / {} МБ)",
                EcoConfig.backupEnabled ? dir.getAbsolutePath() : "выключены",
                EcoConfig.backupIntervalMinutes, EcoConfig.backupKeepCount,
                EcoConfig.backupMaxAgeDays, EcoConfig.backupMaxTotalSizeMb);
    }

    /** После правки интервала в редакторе конфига. */
    public static void reschedule() {
        rescheduleNext();
    }

    public static void onServerStop() {
        if (EcoConfig.backupOnServerStop && !running.get()) {
            EcoServ.LOG.info("Бэкап при остановке сервера...");
            performBlocking("shutdown");
        }
        server = null;
    }

    public static void markPlayersSeen() {
        playersSeenSinceBackup = true;
    }

    public static boolean isRunning() {
        return running.get();
    }

    public static long nextBackupTime() {
        return nextBackupTime;
    }

    public static long lastBackupTime() {
        return lastBackupTime;
    }

    public static long lastDuration() {
        return lastDurationMillis;
    }

    public static long lastSize() {
        return lastSizeBytes;
    }

    public static String lastError() {
        return lastError;
    }

    private static void rescheduleNext() {
        nextBackupTime = System.currentTimeMillis() + Math.max(1, EcoConfig.backupIntervalMinutes) * 60_000L;
    }

    /** Вызывается раз в секунду из главного потока сервера. */
    public static void tick() {
        if (server == null) {
            return;
        }
        if (!PlayerUtil.allOnline().isEmpty()) {
            playersSeenSinceBackup = true;
        }
        if (!EcoConfig.backupEnabled) {
            return;
        }
        long now = System.currentTimeMillis();

        if (scheduledStartAt > 0L && now >= scheduledStartAt) {
            scheduledStartAt = 0L;
            ICommandSender requester = pendingRequester;
            pendingRequester = null;
            start(requester, "auto");
            return;
        }
        if (scheduledStartAt > 0L || running.get()) {
            return;
        }
        if (now >= nextBackupTime) {
            if (EcoConfig.backupSkipIfNoPlayers && !playersSeenSinceBackup) {
                rescheduleNext();
                return;
            }
            scheduleWithWarning(null);
        }
    }

    /** Предупредить игроков и запустить бэкап через warn_seconds. */
    public static void scheduleWithWarning(@Nullable ICommandSender requester) {
        if (running.get() || scheduledStartAt > 0L) {
            if (requester != null) {
                ChatUtil.send(requester, "&eБэкап уже запущен или запланирован.");
            }
            return;
        }
        pendingRequester = requester;
        int warn = Math.max(0, EcoConfig.backupWarnSeconds);
        if (warn > 0 && EcoConfig.backupAnnounce) {
            announce("&eБэкап мира через " + warn + " сек. Возможен короткий лаг.");
        }
        scheduledStartAt = System.currentTimeMillis() + warn * 1000L;
    }

    /** Запуск немедленно (главный поток сервера). */
    public static boolean start(@Nullable final ICommandSender requester, final String label) {
        if (server == null) {
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            if (requester != null) {
                ChatUtil.send(requester, "&cБэкап уже выполняется.");
            }
            return false;
        }

        final long started = System.currentTimeMillis();
        lastError = "";
        if (EcoConfig.backupAnnounce) {
            announce("&7Сохраняю мир для бэкапа...");
        }

        final java.util.Map<WorldServer, Boolean> savingWasDisabled = saveAndLock();
        final File worldDir = worldDirectory();
        final File target = new File(backupDirectory(), PREFIX + Fmt.fileStamp(started)
                + (label == null || label.isEmpty() || "auto".equals(label) ? "" : "-" + sanitize(label)) + ".zip");

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                long size = 0L;
                String error = null;
                try {
                    size = Zipper.zipWorld(worldDir, target, EcoConfig.backupExclude, EcoConfig.backupCompressionLevel,
                            EcoConfig.backupIncludeConfig, EcoConfig.backupIncludeModList);
                } catch (Throwable t) {
                    error = t.toString();
                    EcoServ.LOG.error("Бэкап не удался", t);
                    if (target.exists() && !target.delete()) {
                        EcoServ.LOG.error("Не удалось удалить битый архив {}", target);
                    }
                }
                final long finalSize = size;
                final String finalError = error;
                final MinecraftServer srv = server;
                if (srv != null) {
                    srv.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            finish(savingWasDisabled, started, finalSize, finalError, requester, target);
                        }
                    });
                } else {
                    finish(savingWasDisabled, started, finalSize, finalError, requester, target);
                }
            }
        }, "EcoServ-Backup");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    /** Синхронный бэкап (используется при остановке сервера). */
    public static void performBlocking(String label) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        long started = System.currentTimeMillis();
        java.util.Map<WorldServer, Boolean> was = saveAndLock();
        File target = new File(backupDirectory(), PREFIX + Fmt.fileStamp(started) + "-" + sanitize(label) + ".zip");
        long size = 0L;
        String error = null;
        try {
            size = Zipper.zipWorld(worldDirectory(), target, EcoConfig.backupExclude, EcoConfig.backupCompressionLevel,
                    EcoConfig.backupIncludeConfig, EcoConfig.backupIncludeModList);
        } catch (Throwable t) {
            error = t.toString();
            EcoServ.LOG.error("Бэкап не удался", t);
        }
        finish(was, started, size, error, null, target);
    }

    private static void finish(java.util.Map<WorldServer, Boolean> savingWasDisabled, long started, long size, @Nullable String error,
                               @Nullable ICommandSender requester, File target) {
        unlockSaving(savingWasDisabled);
        running.set(false);
        lastBackupTime = System.currentTimeMillis();
        lastDurationMillis = lastBackupTime - started;
        playersSeenSinceBackup = false;
        rescheduleNext();

        if (error != null) {
            lastError = error;
            ChatUtil.broadcastAdmins("&cБэкап не удался: &f" + error);
            if (requester != null) {
                ChatUtil.send(requester, "&cБэкап не удался: &f" + error);
            }
            return;
        }

        lastSizeBytes = size;
        int deleted = prune();
        String msg = "&aБэкап готов: &f" + target.getName() + " &7(" + Fmt.bytes(size) + ", "
                + Fmt.duration(lastDurationMillis) + (deleted > 0 ? ", удалено старых: " + deleted : "") + ")";
        if (EcoConfig.backupAnnounce) {
            announce(msg);
        }
        if (requester != null && EcoConfig.backupAnnounceOnlyAdmins) {
            ChatUtil.send(requester, msg);
        }
        EcoServ.LOG.info("Бэкап: {} ({}, {})", target.getName(), Fmt.bytes(size), Fmt.duration(lastDurationMillis));
    }

    private static void announce(String message) {
        if (EcoConfig.backupAnnounceOnlyAdmins) {
            ChatUtil.broadcastAdmins(message);
        } else {
            ChatUtil.broadcast(message);
        }
    }

    /**
     * Сохраняет все миры, дописывает очередь записи чанков на диск и только потом отключает
     * автосохранение (с отключённым сохранением saveAllChunks ничего не пишет). Возвращает прежние
     * состояния — по самим мирам: мир, выгруженный и загруженный заново за время бэкапа, это уже другой
     * объект, и чужое «выключено» ему не достанется.
     */
    private static java.util.Map<WorldServer, Boolean> saveAndLock() {
        java.util.Map<WorldServer, Boolean> was = new java.util.IdentityHashMap<WorldServer, Boolean>();
        for (WorldServer world : DimensionManager.getWorlds()) {
            if (world == null) {
                continue;
            }
            try {
                world.saveAllChunks(true, null);
                world.flushToDisk();
            } catch (Throwable t) {
                EcoServ.LOG.error("Ошибка сохранения мира {}", world.provider.getDimension(), t);
            }
        }
        if (server != null) {
            try {
                server.getPlayerList().saveAllPlayerData();
            } catch (Throwable t) {
                EcoServ.LOG.error("Ошибка сохранения игроков", t);
            }
        }
        try {
            // поток записи мог ещё держать последние чанки — ждём, пока он допишет
            net.minecraft.world.storage.ThreadedFileIOBase.getThreadedIOInstance().waitForFinish();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (WorldServer world : DimensionManager.getWorlds()) {
            if (world != null) {
                was.put(world, world.disableLevelSaving);
                world.disableLevelSaving = true;
            }
        }
        return was;
    }

    private static void unlockSaving(java.util.Map<WorldServer, Boolean> was) {
        for (java.util.Map.Entry<WorldServer, Boolean> e : was.entrySet()) {
            e.getKey().disableLevelSaving = e.getValue();
        }
    }

    // --- файлы -------------------------------------------------------------

    public static File worldDirectory() {
        File dir = null;
        try {
            dir = DimensionManager.getCurrentSaveRootDirectory();
        } catch (Throwable ignored) {
            // ниже запасной путь
        }
        if (dir == null && server != null) {
            dir = server.getFile(server.getFolderName());
        }
        return dir == null ? new File("world") : dir;
    }

    public static File backupDirectory() {
        String path = EcoConfig.backupDir == null || EcoConfig.backupDir.isEmpty() ? "backups" : EcoConfig.backupDir;
        File dir = new File(path);
        if (!dir.isAbsolute()) {
            File base = server == null ? new File(".") : server.getFile(".");
            dir = new File(base, path);
        }
        if (!dir.exists() && !dir.mkdirs()) {
            EcoServ.LOG.error("Не удалось создать папку бэкапов {}", dir);
        }
        // защита от рекурсии: папка бэкапов не должна лежать внутри мира
        try {
            String world = worldDirectory().getCanonicalPath();
            if (dir.getCanonicalPath().startsWith(world)) {
                File safe = new File(worldDirectory().getParentFile(), "backups");
                EcoServ.LOG.warn("Папка бэкапов внутри мира! Использую {}", safe);
                if (!safe.exists() && !safe.mkdirs()) {
                    EcoServ.LOG.error("Не удалось создать {}", safe);
                }
                return safe;
            }
        } catch (IOException ignored) {
            // оставляем как есть
        }
        return dir;
    }

    public static List<File> listBackups() {
        File[] files = backupDirectory().listFiles();
        List<File> list = new ArrayList<File>();
        if (files == null) {
            return list;
        }
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isFile() && f.getName().startsWith(PREFIX) && f.getName().endsWith(".zip")) {
                list.add(f);
            }
        }
        java.util.Collections.sort(list, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return list;
    }

    public static long totalSize() {
        long total = 0L;
        List<File> files = listBackups();
        for (int i = 0; i < files.size(); i++) {
            total += files.get(i).length();
        }
        return total;
    }

    /** Удаление старых архивов по количеству, возрасту и общему объёму. */
    public static int prune() {
        List<File> files = listBackups();
        int deleted = 0;
        long now = System.currentTimeMillis();

        // по возрасту
        if (EcoConfig.backupMaxAgeDays > 0) {
            long maxAge = EcoConfig.backupMaxAgeDays * 86_400_000L;
            for (int i = files.size() - 1; i >= 0; i--) {
                File f = files.get(i);
                if (now - f.lastModified() > maxAge && files.size() - deleted > 1) {
                    if (delete(f)) {
                        deleted++;
                        files.remove(i);
                    }
                }
            }
        }

        // по количеству
        if (EcoConfig.backupKeepCount > 0) {
            while (files.size() > EcoConfig.backupKeepCount) {
                File oldest = files.remove(files.size() - 1);
                if (delete(oldest)) {
                    deleted++;
                }
            }
        }

        // по общему размеру
        if (EcoConfig.backupMaxTotalSizeMb > 0) {
            long limit = EcoConfig.backupMaxTotalSizeMb * 1024L * 1024L;
            long total = 0L;
            for (int i = 0; i < files.size(); i++) {
                total += files.get(i).length();
            }
            while (total > limit && files.size() > 1) {
                File oldest = files.remove(files.size() - 1);
                long size = oldest.length();
                if (delete(oldest)) {
                    deleted++;
                    total -= size;
                }
            }
        }
        return deleted;
    }

    private static boolean delete(File file) {
        if (file.delete()) {
            EcoServ.LOG.info("Удалён старый бэкап {}", file.getName());
            return true;
        }
        EcoServ.LOG.warn("Не удалось удалить {}", file);
        return false;
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    public static List<File> listBackupsSorted() {
        List<File> list = listBackups();
        File[] arr = list.toArray(new File[0]);
        Arrays.sort(arr, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return Arrays.asList(arr);
    }
}
