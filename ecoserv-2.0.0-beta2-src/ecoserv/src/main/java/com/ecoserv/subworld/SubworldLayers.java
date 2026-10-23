package com.ecoserv.subworld;

import com.ecoserv.EcoServ;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.NbtFiles;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Опубликованные слои шаблона подмира: ecoserv_worlds/&lt;подмир&gt;/published/&lt;версия&gt;/region/*.mca.
 * <p>
 * Слой 0 — загруженный мир (п. 3, «загрузить свою»), слои 1, 2, ... — публикации шаблона админами
 * (в слой попадают только изменённые файлы регионов). Чанк берётся из самого нового слоя, где он есть.
 * Слои не меняются после записи — локальные миры читают их, пока админы правят шаблон.
 */
public final class SubworldLayers {

    public static final String MANIFEST = "manifest.json";
    /** Таблица id блоков/биомов загруженного мира (если мир с модами). */
    public static final String IDS = "ids.dat";

    /** Описание слоя. */
    public static class Manifest {
        public int version;
        public long time;
        public String by = "";
        /** upload:&lt;имя&gt; или publish. */
        public String source = "";
        /** Копирование завершено. */
        public boolean complete;
        /** Состояние файлов регионов шаблона на момент публикации: имя -> [размер, время изменения]. */
        public Map<String, long[]> files = new LinkedHashMap<String, long[]>();
        public long bytes;
    }

    private static final Map<String, int[]> VERSIONS = new ConcurrentHashMap<String, int[]>();
    private static final Map<String, Remap> REMAPS = new ConcurrentHashMap<String, Remap>();

    private SubworldLayers() {
    }

    public static File root(String subworld) {
        return new File(SubworldRegistry.subworldDir(subworld), "published");
    }

    public static File layerDir(String subworld, int version) {
        return new File(root(subworld), Integer.toString(version));
    }

    public static void invalidate(String subworld) {
        VERSIONS.remove(subworld);
        REMAPS.remove(subworld);
    }

    /** Смена мира сервера (одиночная игра): кеши и открытые файлы слоёв — прочь. */
    public static void invalidateAll() {
        VERSIONS.clear();
        REMAPS.clear();
        LayerRegions.closeAll();
    }

    @Nullable
    public static Manifest manifest(String subworld, int version) {
        File f = new File(layerDir(subworld, version), MANIFEST);
        return f.isFile() ? JsonUtil.read(f, Manifest.class, null) : null;
    }

    public static void writeManifest(String subworld, Manifest m) {
        writeManifest(layerDir(subworld, m.version), m);
    }

    /** Запись в заранее вычисленную папку слоя (фоновые потоки: мир сервера мог смениться). */
    public static void writeManifest(File layerDir, Manifest m) {
        JsonUtil.write(new File(layerDir, MANIFEST), m);
    }

    /** Готовые слои, от нового к старому. */
    public static int[] versions(String subworld) {
        int[] cached = VERSIONS.get(subworld);
        if (cached != null) {
            return cached;
        }
        List<Integer> list = new ArrayList<Integer>();
        File[] dirs = root(subworld).listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                if (!dir.isDirectory()) {
                    continue;
                }
                int v;
                try {
                    v = Integer.parseInt(dir.getName());
                } catch (NumberFormatException e) {
                    continue;
                }
                Manifest m = manifest(subworld, v);
                if (m != null && m.complete) {
                    list.add(v);
                }
            }
        }
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        Arrays.sort(out);
        // от нового к старому
        for (int i = 0; i < out.length / 2; i++) {
            int t = out[i];
            out[i] = out[out.length - 1 - i];
            out[out.length - 1 - i] = t;
        }
        VERSIONS.put(subworld, out);
        return out;
    }

    public static boolean hasAny(String subworld) {
        return versions(subworld).length > 0;
    }

    public static int latest(String subworld) {
        int[] v = versions(subworld);
        return v.length == 0 ? -1 : v[0];
    }

    /** Версия самого нового слоя (не новее maxVersion), где есть чанк; -1 — нигде. */
    public static int find(String subworld, int cx, int cz, int maxVersion) {
        for (int v : versions(subworld)) {
            if (v > maxVersion) {
                continue;
            }
            if (LayerRegions.exists(layerDir(subworld, v), cx, cz)) {
                return v;
            }
        }
        return -1;
    }

    // --- пересчёт id блоков и биомов загруженного мира ------------------------------

    /** Таблица пересчёта: старый id -> id на этом сервере. */
    public static final class Remap {
        public final int[] blocks;
        public final int[] biomes;
        public final boolean identity;

        Remap(int[] blocks, int[] biomes, boolean identity) {
            this.blocks = blocks;
            this.biomes = biomes;
            this.identity = identity;
        }
    }

    /** Пересчёт для слоя (только слой из загруженного мира с модами); null — не нужен. */
    @Nullable
    public static Remap remap(String subworld, int version) {
        if (version != 0) {
            return null;
        }
        Remap cached = REMAPS.get(subworld);
        if (cached != null) {
            return cached.identity ? null : cached;
        }
        Remap built = build(new File(layerDir(subworld, 0), IDS));
        REMAPS.put(subworld, built);
        return built.identity ? null : built;
    }

    private static Remap build(File idsFile) {
        int[] blocks = new int[4096];
        int[] biomes = new int[256];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = i;
        }
        for (int i = 0; i < biomes.length; i++) {
            biomes[i] = i;
        }
        NBTTagCompound ids = NbtFiles.read(idsFile);
        if (ids == null) {
            return new Remap(blocks, biomes, true);
        }
        boolean identity = true;
        int missing = 0;
        NBTTagList blockList = ids.getTagList("blocks", 10);
        for (int i = 0; i < blockList.tagCount(); i++) {
            NBTTagCompound e = blockList.getCompoundTagAt(i);
            int oldId = e.getInteger("V");
            if (oldId < 0 || oldId >= blocks.length) {
                continue;
            }
            Block block = Block.REGISTRY.containsKey(new ResourceLocation(e.getString("K")))
                    ? Block.REGISTRY.getObject(new ResourceLocation(e.getString("K"))) : null;
            int newId = block == null ? 0 : Block.getIdFromBlock(block);
            if (block == null) {
                missing++;
            }
            if (newId != oldId) {
                identity = false;
            }
            blocks[oldId] = newId;
        }
        NBTTagList biomeList = ids.getTagList("biomes", 10);
        for (int i = 0; i < biomeList.tagCount(); i++) {
            NBTTagCompound e = biomeList.getCompoundTagAt(i);
            int oldId = e.getInteger("V");
            if (oldId < 0 || oldId >= biomes.length) {
                continue;
            }
            Biome biome = Biome.REGISTRY.getObject(new ResourceLocation(e.getString("K")));
            int newId = biome == null ? 1 : Biome.getIdForBiome(biome);
            if (newId != oldId) {
                identity = false;
            }
            biomes[oldId] = newId;
        }
        if (missing > 0) {
            EcoServ.LOG.warn("Загруженный мир: {} блоков из его модов нет на сервере — они станут воздухом", missing);
        }
        return new Remap(blocks, biomes, identity);
    }

    /** Достать таблицы id из level.dat загруженного мира (FML → Registries). */
    @Nullable
    public static NBTTagCompound idsFromLevel(NBTTagCompound level) {
        if (level == null || !level.hasKey("FML", 10)) {
            return null;
        }
        NBTTagCompound fml = level.getCompoundTag("FML");
        NBTTagCompound regs = fml.getCompoundTag("Registries");
        NBTTagCompound out = new NBTTagCompound();
        boolean any = false;
        if (regs.hasKey("minecraft:blocks", 10)) {
            out.setTag("blocks", regs.getCompoundTag("minecraft:blocks").getTagList("ids", 10));
            any = true;
        }
        if (regs.hasKey("minecraft:biomes", 10)) {
            out.setTag("biomes", regs.getCompoundTag("minecraft:biomes").getTagList("ids", 10));
            any = true;
        }
        return any ? out : null;
    }

    /** Применить пересчёт к NBT чанка (секции Blocks/Add и Biomes). */
    public static void applyRemap(NBTTagCompound level, Remap remap) {
        NBTTagList sections = level.getTagList("Sections", 10);
        for (int s = 0; s < sections.tagCount(); s++) {
            NBTTagCompound sec = sections.getCompoundTagAt(s);
            byte[] blocks = sec.getByteArray("Blocks");
            if (blocks.length != 4096) {
                continue;
            }
            byte[] add = sec.hasKey("Add", 7) ? sec.getByteArray("Add") : null;
            if (add != null && add.length != 2048) {
                add = null;
            }
            byte[] newAdd = new byte[2048];
            boolean anyAdd = false;
            for (int i = 0; i < 4096; i++) {
                int hi = add == null ? 0 : ((i & 1) == 0 ? add[i >> 1] & 15 : (add[i >> 1] >> 4) & 15);
                int id = (hi << 8) | (blocks[i] & 255);
                int mapped = remap.blocks[id & 4095];
                blocks[i] = (byte) (mapped & 255);
                int newHi = (mapped >> 8) & 15;
                if (newHi != 0) {
                    anyAdd = true;
                    if ((i & 1) == 0) {
                        newAdd[i >> 1] = (byte) ((newAdd[i >> 1] & 0xF0) | newHi);
                    } else {
                        newAdd[i >> 1] = (byte) ((newAdd[i >> 1] & 0x0F) | (newHi << 4));
                    }
                }
            }
            sec.setByteArray("Blocks", blocks);
            if (anyAdd) {
                sec.setByteArray("Add", newAdd);
            } else {
                sec.removeTag("Add");
            }
        }
        if (level.hasKey("Biomes", 7)) {
            byte[] biomes = level.getByteArray("Biomes");
            for (int i = 0; i < biomes.length; i++) {
                biomes[i] = (byte) remap.biomes[biomes[i] & 255];
            }
            level.setByteArray("Biomes", biomes);
        }
    }

    /** Сколько места занимают слои. */
    public static long size(String subworld) {
        return folderSize(root(subworld));
    }

    public static long folderSize(File f) {
        if (f == null || !f.exists()) {
            return 0L;
        }
        if (f.isFile()) {
            return f.length();
        }
        long total = 0L;
        File[] list = f.listFiles();
        if (list != null) {
            for (File c : list) {
                total += folderSize(c);
            }
        }
        return total;
    }

    /** Все файлы регионов папки: имя -> [размер, время]. */
    public static Map<String, long[]> regionFiles(File worldDir) {
        Map<String, long[]> out = new HashMap<String, long[]>();
        File[] list = new File(worldDir, "region").listFiles();
        if (list != null) {
            for (File f : list) {
                if (f.isFile() && f.getName().endsWith(".mca")) {
                    out.put(f.getName(), new long[]{f.length(), f.lastModified()});
                }
            }
        }
        return out;
    }
}
