package com.ecoserv.subworld;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.FileOps;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.NbtFiles;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.WorldInfo;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Свои миры для подмиров (тип «загрузить свою», до 2 ГБ): папка мира или .zip в папке
 * ecoserv_uploads рядом с миром сервера (туда же складывает загрузка из игры админа).
 * Установка копирует регионы в слой 0 подмира; блоки из модов пересчитываются по таблице id мира.
 */
public final class SubworldUploads {

    /** 1.12.2 — последняя версия со старым форматом чанков. */
    public static final int MAX_DATA_VERSION = 1343;
    private static final Pattern REGION = Pattern.compile("^r\\.-?\\d+\\.-?\\d+\\.mca$");
    public static final String INCOMING = ".incoming";

    /** Найденный мир. */
    public static final class Upload {
        public String name = "";
        public File file;
        public boolean zip;
        /** Для zip: путь к папке мира внутри архива ("" или "папка/"). */
        public String zipPrefix = "";
        public long size;
        public int regions;
        public String levelName = "";
        public int dataVersion;
        public boolean modded;
        @Nullable
        public BlockPos spawn;
        /** Почему не подходит (null — подходит). */
        @Nullable
        public String error;
    }

    private SubworldUploads() {
    }

    public static File root() {
        MinecraftServer server = PlayerUtil.server();
        File base = server == null ? new File(".") : server.getFile(".");
        return new File(base, EcoConfig.subworldsUploadsFolder);
    }

    public static long maxBytes() {
        return EcoConfig.subworldsMaxUploadMb * 1024L * 1024L;
    }

    /** Все миры в папке загрузок. */
    public static List<Upload> list() {
        List<Upload> out = new ArrayList<Upload>();
        File root = root();
        if (!root.isDirectory()) {
            root.mkdirs();
            return out;
        }
        File[] files = root.listFiles();
        if (files == null) {
            return out;
        }
        for (File f : files) {
            if (f.getName().startsWith(".")) {
                continue;
            }
            Upload u = null;
            if (f.isDirectory()) {
                u = scanFolder(f);
            } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                u = scanZip(f);
            }
            if (u != null) {
                out.add(u);
            }
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    @Nullable
    public static Upload find(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (Upload u : list()) {
            if (u.name.equalsIgnoreCase(name)) {
                return u;
            }
        }
        return null;
    }

    private static Upload scanFolder(File dir) {
        Upload u = new Upload();
        u.name = dir.getName();
        u.file = dir;
        File region = new File(dir, "region");
        File[] mca = region.listFiles();
        if (mca != null) {
            for (File f : mca) {
                if (REGION.matcher(f.getName()).matches()) {
                    u.regions++;
                    u.size += f.length();
                }
            }
        }
        File level = new File(dir, "level.dat");
        if (level.isFile()) {
            u.size += level.length();
            try (InputStream in = new FileInputStream(level)) {
                readLevel(u, CompressedStreamTools.readCompressed(in));
            } catch (Exception e) {
                u.error = "level.dat повреждён";
            }
        }
        validate(u);
        return u;
    }

    private static Upload scanZip(File file) {
        Upload u = new Upload();
        String n = file.getName();
        u.name = n.substring(0, n.length() - 4);
        u.file = file;
        u.zip = true;
        try (ZipFile zip = new ZipFile(file)) {
            String prefix = null;
            Enumeration<? extends ZipEntry> en = zip.entries();
            List<ZipEntry> entries = new ArrayList<ZipEntry>();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                entries.add(e);
                String name = e.getName().replace('\\', '/');
                if (name.endsWith("level.dat") && !e.isDirectory()) {
                    String p = name.substring(0, name.length() - "level.dat".length());
                    if (p.isEmpty() || p.endsWith("/")) {
                        if (prefix == null || p.length() < prefix.length()) {
                            prefix = p;
                        }
                    }
                }
            }
            u.zipPrefix = prefix == null ? "" : prefix;
            for (ZipEntry e : entries) {
                String rel = relative(e.getName(), u.zipPrefix);
                if (rel != null && rel.startsWith("region/") && REGION.matcher(rel.substring(7)).matches()) {
                    u.regions++;
                    u.size += Math.max(0L, e.getSize());
                }
            }
            if (prefix != null) {
                ZipEntry level = zip.getEntry(prefix + "level.dat");
                if (level != null) {
                    try (InputStream in = zip.getInputStream(level)) {
                        readLevel(u, CompressedStreamTools.readCompressed(in));
                    } catch (Exception e) {
                        u.error = "level.dat повреждён";
                    }
                }
            }
        } catch (IOException e) {
            u.error = "архив не читается: " + e.getMessage();
        }
        validate(u);
        return u;
    }

    /** Путь внутри мира или null (чужое/опасное имя). */
    @Nullable
    private static String relative(String entry, String prefix) {
        String name = entry.replace('\\', '/');
        if (!name.startsWith(prefix)) {
            return null;
        }
        String rel = name.substring(prefix.length());
        if (rel.contains("..") || rel.startsWith("/")) {
            return null;
        }
        return rel;
    }

    private static void readLevel(Upload u, NBTTagCompound root) {
        NBTTagCompound data = root.getCompoundTag("Data");
        u.levelName = data.getString("LevelName");
        u.dataVersion = data.getInteger("DataVersion");
        if (data.hasKey("SpawnX")) {
            u.spawn = new BlockPos(data.getInteger("SpawnX"), data.getInteger("SpawnY"), data.getInteger("SpawnZ"));
        }
        u.modded = root.hasKey("FML", 10);
    }

    private static void validate(Upload u) {
        if (u.error != null) {
            return;
        }
        if (u.regions == 0) {
            u.error = "нет папки region с файлами .mca";
        } else if (u.dataVersion > MAX_DATA_VERSION) {
            u.error = "мир из версии новее 1.12.2 (формат чанков другой)";
        } else if (u.size > maxBytes()) {
            u.error = "больше " + EcoConfig.subworldsMaxUploadMb + " МБ (" + Fmt.bytes(u.size) + ")";
        }
    }

    // --- установка в подмир --------------------------------------------------------------

    /**
     * Установить загруженный мир в подмир (слой 0) в фоне. target — измерение, куда писать точку появления
     * (шаблон или общий мир). by — кому сообщить об окончании.
     */
    public static void install(final Subworld sw, final SubworldDim target, @Nullable final EntityPlayerMP by) {
        final Upload up = find(sw.template);
        if (up == null || up.error != null) {
            EcoServ.LOG.error("Подмир {}: загруженный мир «{}» не найден или не подходит", sw.id, sw.template);
            return;
        }
        SubworldManager.setBusy(sw.id, true);
        // если мир загрузят, пока копируется слой, он запишет пустоту там, где будет загруженный мир:
        // метка в папке мира велит стереть записанное (и после перезапуска тоже), флаг — что мир загружали.
        // Обжитый мир (переустановка слоя) не стираем никогда.
        final boolean fresh = !SubworldManager.hasWorldData(target);
        if (fresh) {
            SubworldManager.markPurge(target);
        }
        final java.util.concurrent.atomic.AtomicBoolean loadedDuringCopy = SubworldManager.startCopy(target.dim);
        final String byName = by == null ? null : by.getName();
        // пути — сейчас: в одиночной игре к концу копирования может быть открыт уже другой мир
        final File layer = SubworldLayers.layerDir(sw.id, 0);
        final MinecraftServer server = PlayerUtil.server();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                NBTTagCompound level = null;
                try {
                    level = copyToLayer(layer, up);
                } catch (Throwable e) {
                    error = e.toString();
                    EcoServ.LOG.error("Установка мира {} в подмир {} не удалась", up.name, sw.id, e);
                    FileOps.deleteTree(layer);
                }
                final String err = error;
                final NBTTagCompound lvl = level;
                if (server == null || PlayerUtil.server() != server) {
                    return;
                }
                server.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        SubworldManager.endCopy(target.dim);
                        SubworldLayers.invalidate(sw.id);
                        if (!SubworldRegistry.isCurrent(sw) || !SubworldRegistry.isCurrent(target)) {
                            // подмир удалили, пока копировался мир: папка сотрётся при следующем запуске
                            SubworldManager.setBusy(sw.id, false);
                            return;
                        }
                        final EntityPlayerMP who = byName == null ? null : PlayerUtil.online(byName);
                        if (err != null) {
                            // слоя нет — мир подмира просто пустой; стирать в нём нечего
                            SubworldManager.clearPurgeMark(target);
                            SubworldManager.setBusy(sw.id, false);
                            String msg = "&cМир «" + up.name + "» не установился в подмир " + sw.id + ": " + err;
                            if (who != null) {
                                ChatUtil.send(who, msg);
                            }
                            ChatUtil.broadcastAdmins(msg);
                            return;
                        }
                        writeSpawn(sw, target, up, lvl);
                        Runnable finish = new Runnable() {
                            @Override
                            public void run() {
                                SubworldManager.clearPurgeMark(target);
                                SubworldManager.setBusy(sw.id, false);
                                String msg = "&aМир «" + up.name + "» установлен в подмир &f" + sw.id + " &a("
                                        + Fmt.bytes(up.size) + ", регионов: " + up.regions + ").";
                                EntityPlayerMP now = byName == null ? null : PlayerUtil.online(byName);
                                if (now != null) {
                                    ChatUtil.send(now, msg);
                                }
                                ChatUtil.broadcastAdmins(msg);
                            }
                        };
                        if (loadedDuringCopy.get() && fresh) {
                            // мир загружали во время копирования — сначала выгрузить и стереть то, что он записал
                            SubworldManager.purgeAfterUnload(target, finish);
                        } else {
                            finish.run();
                        }
                    }
                });
            }
        }, "EcoServ install " + sw.id);
        t.setDaemon(true);
        t.start();
    }

    /** Скопировать регионы в слой 0; вернуть level.dat загруженного мира. */
    @Nullable
    private static NBTTagCompound copyToLayer(File layer, Upload up) throws IOException {
        FileOps.deleteTree(layer);
        File regionOut = new File(layer, "region");
        if (!regionOut.mkdirs()) {
            throw new IOException("не создать " + regionOut);
        }
        SubworldLayers.Manifest m = new SubworldLayers.Manifest();
        m.version = 0;
        m.time = System.currentTimeMillis();
        m.source = "upload:" + up.name;
        m.complete = false;
        SubworldLayers.writeManifest(layer, m);
        NBTTagCompound level = null;
        long bytes = 0L;
        if (!up.zip) {
            File[] mca = new File(up.file, "region").listFiles();
            if (mca != null) {
                for (File f : mca) {
                    if (REGION.matcher(f.getName()).matches()) {
                        long crc = FileOps.copy(f, new File(regionOut, f.getName()));
                        m.files.put(f.getName(), new long[]{f.length(), f.lastModified(), crc});
                        bytes += f.length();
                    }
                }
            }
            File lv = new File(up.file, "level.dat");
            if (lv.isFile()) {
                try (InputStream in = new FileInputStream(lv)) {
                    level = CompressedStreamTools.readCompressed(in);
                }
            }
        } else {
            try (ZipFile zip = new ZipFile(up.file)) {
                Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String rel = relative(e.getName(), up.zipPrefix);
                    if (rel == null || e.isDirectory()) {
                        continue;
                    }
                    if (rel.startsWith("region/") && REGION.matcher(rel.substring(7)).matches()) {
                        File out = new File(regionOut, rel.substring(7));
                        long[] res = extract(zip, e, out, maxBytes() - bytes);
                        m.files.put(out.getName(), new long[]{res[0], out.lastModified(), res[1]});
                        bytes += res[0];
                    } else if (rel.equals("level.dat")) {
                        try (InputStream in = zip.getInputStream(e)) {
                            level = CompressedStreamTools.readCompressed(in);
                        }
                    }
                }
            }
        }
        if (level != null) {
            NBTTagCompound ids = SubworldLayers.idsFromLevel(level);
            if (ids != null) {
                NbtFiles.write(new File(layer, SubworldLayers.IDS), ids);
            }
            NbtFiles.write(new File(layer, "level.dat"), level);
        }
        m.bytes = bytes;
        m.complete = true;
        SubworldLayers.writeManifest(layer, m);
        return level;
    }

    /** Распаковать одну запись с ограничением размера (защита от «zip-бомб»). [байт, crc]. */
    private static long[] extract(ZipFile zip, ZipEntry e, File out, long budget) throws IOException {
        CRC32 crc = new CRC32();
        long written = 0L;
        byte[] buf = new byte[65536];
        try (InputStream in = new BufferedInputStream(zip.getInputStream(e));
             OutputStream os = new FileOutputStream(out)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                written += n;
                if (written > budget) {
                    throw new IOException("распакованный мир больше " + EcoConfig.subworldsMaxUploadMb + " МБ");
                }
                crc.update(buf, 0, n);
                os.write(buf, 0, n);
            }
        }
        return new long[]{written, crc.getValue()};
    }

    /** Точка появления из загруженного мира → в настройки измерения подмира. */
    /** Точка появления из level.dat, сохранённого в слое 0 (копирование закончилось, запись точки — нет). */
    static void writeSpawnFromLayer(Subworld sw, SubworldDim target) {
        NBTTagCompound level = NbtFiles.read(new File(SubworldLayers.layerDir(sw.id, 0), "level.dat"));
        if (level != null) {
            writeSpawn(sw, target, null, level);
        }
    }

    private static void writeSpawn(Subworld sw, SubworldDim target, @Nullable Upload up, @Nullable NBTTagCompound level) {
        WorldInfo info = SubworldInfo.create(sw);
        BlockPos spawn = up == null ? null : up.spawn;
        if (level != null && level.getCompoundTag("Data").hasKey("SpawnX")) {
            NBTTagCompound data = level.getCompoundTag("Data");
            spawn = new BlockPos(data.getInteger("SpawnX"), data.getInteger("SpawnY"), data.getInteger("SpawnZ"));
        }
        if (spawn != null) {
            info.setSpawn(spawn);
        }
        File dir = SubworldRegistry.folderOf(target);
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        NbtFiles.write(new File(dir, SubworldInfo.FILE), info.cloneNBTCompound(null));
        // если мир уже загружен (его открыл другой мод) — точку ставим и ему, иначе он перезапишет файл при сохранении
        net.minecraft.world.WorldServer live = net.minecraftforge.common.DimensionManager.getWorld(target.dim);
        if (live != null && spawn != null) {
            live.setSpawnPoint(spawn);
        }
        target.spawnChecked = true;
        SubworldRegistry.markDirty();
    }

    // --- приём мира из игры админа ---------------------------------------------------------

    /** Папка, куда пишется мир во время загрузки из игры. */
    public static File incomingDir(String name) {
        return new File(new File(root(), INCOMING), FileOps.safeName(name));
    }

    /** При запуске сервера: остатки прерванных загрузок — стереть в фоне. */
    public static void cleanIncoming() {
        final File dir = new File(root(), INCOMING);
        if (!dir.isDirectory()) {
            return;
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                FileOps.deleteTree(dir);
            }
        }, "EcoServ upload cleanup");
        t.setDaemon(true);
        t.start();
    }

    /** Имя свободно для новой загрузки. */
    public static boolean nameFree(String name) {
        String n = FileOps.safeName(name);
        return !n.isEmpty() && !new File(root(), n).exists() && !new File(root(), n + ".zip").exists();
    }

    /** Допустимый путь файла внутри загружаемого мира. */
    public static boolean allowedPath(String rel) {
        if (rel == null) {
            return false;
        }
        String r = rel.replace('\\', '/');
        return r.equals("level.dat") || (r.startsWith("region/") && REGION.matcher(r.substring(7)).matches());
    }
}
