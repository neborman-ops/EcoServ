package com.ecoserv.subworld;

import com.ecoserv.EcoServ;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.chunk.storage.RegionFile;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Свой кеш файлов регионов для слоёв шаблонов и снимков. Общий кеш Minecraft (RegionFileCache) при
 * переполнении закрывает ВСЕ файлы, в том числе те, в которые как раз пишет поток сохранения, —
 * слои подмиров не должны его переполнять.
 */
public final class LayerRegions {

    private static final int MAX_OPEN = 96;

    private static final LinkedHashMap<File, RegionFile> CACHE = new LinkedHashMap<File, RegionFile>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<File, RegionFile> eldest) {
            if (size() > MAX_OPEN) {
                close(eldest.getValue());
                return true;
            }
            return false;
        }
    };

    private LayerRegions() {
    }

    private static File regionFile(File worldDir, int cx, int cz) {
        return new File(new File(worldDir, "region"), "r." + (cx >> 5) + "." + (cz >> 5) + ".mca");
    }

    @Nullable
    private static RegionFile get(File worldDir, int cx, int cz, boolean create) {
        File file = regionFile(worldDir, cx, cz);
        RegionFile rf = CACHE.get(file);
        if (rf != null) {
            return rf;
        }
        if (!create && !file.isFile()) {
            return null;
        }
        File parent = file.getParentFile();
        if (create && !parent.isDirectory() && !parent.mkdirs()) {
            return null;
        }
        rf = new RegionFile(file);
        CACHE.put(file, rf);
        return rf;
    }

    /** Есть ли чанк в папке мира (слоя). */
    public static synchronized boolean exists(File worldDir, int cx, int cz) {
        RegionFile rf = get(worldDir, cx, cz, false);
        return rf != null && rf.isChunkSaved(cx & 31, cz & 31);
    }

    /** NBT чанка или null. */
    @Nullable
    public static synchronized NBTTagCompound read(File worldDir, int cx, int cz) throws IOException {
        RegionFile rf = get(worldDir, cx, cz, false);
        if (rf == null || !rf.isChunkSaved(cx & 31, cz & 31)) {
            return null;
        }
        DataInputStream in = rf.getChunkDataInputStream(cx & 31, cz & 31);
        if (in == null) {
            return null;
        }
        try {
            return CompressedStreamTools.read(in);
        } finally {
            in.close();
        }
    }

    public static synchronized void write(File worldDir, int cx, int cz, NBTTagCompound nbt) throws IOException {
        RegionFile rf = get(worldDir, cx, cz, true);
        if (rf == null) {
            throw new IOException("не создать регион в " + worldDir);
        }
        DataOutputStream out = rf.getChunkDataOutputStream(cx & 31, cz & 31);
        try {
            CompressedStreamTools.write(nbt, out);
        } finally {
            out.close();
        }
    }

    /** Закрыть файлы внутри папки (перед её удалением). */
    public static synchronized void closeUnder(File dir) {
        String prefix = dir.getAbsolutePath();
        Iterator<Map.Entry<File, RegionFile>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<File, RegionFile> e = it.next();
            if (e.getKey().getAbsolutePath().startsWith(prefix)) {
                close(e.getValue());
                it.remove();
            }
        }
    }

    public static synchronized void closeAll() {
        for (RegionFile rf : CACHE.values()) {
            close(rf);
        }
        CACHE.clear();
    }

    private static void close(RegionFile rf) {
        try {
            rf.close();
        } catch (IOException e) {
            EcoServ.LOG.debug("Не удалось закрыть файл региона: {}", e.toString());
        }
    }
}
