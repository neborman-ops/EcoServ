package com.ecoserv.subworld;

import com.ecoserv.EcoServ;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * «Природное» состояние чанков шаблона (для глобального редактирования, п. 16). Копия чанка шаблона
 * откладывается в published/base, когда чанк окончательно «украшен»: заполнен он сам и его соседи
 * с запада, севера и северо-запада (деревья и руды соседей заходят и в него). По этой копии потом
 * видно, что именно админы поменяли в месте, которое игроки уже успели посетить до первой публикации.
 * У пустоты и загруженного мира исходное состояние известно и так (пусто / слой 0).
 * <p>
 * Руками админ может менять только чанки рядом с собой, а они к этому времени уже украшены, поэтому
 * в снимок правки админов не попадают.
 */
public final class TemplateBase {

    /** Очередь записи чанков на диск у AnvilChunkLoader (там чанк лежит, пока его не записали). */
    private static Field pendingField;
    private static boolean pendingFieldFailed;

    private TemplateBase() {
    }

    static File dir(String subworld) {
        return new File(SubworldLayers.root(subworld), "base");
    }

    /** Типы, у которых исходное состояние места берётся из снимка. */
    static boolean needsSnapshot(Subworld sw) {
        return !Subworld.VOID.equals(sw.type) && !Subworld.CUSTOM.equals(sw.type);
    }

    /** Исходный вид места уже известен: есть снимок или чанк есть в опубликованном слое. */
    private static boolean known(Subworld sw, int cx, int cz) {
        return LayerRegions.exists(dir(sw.id), cx, cz) || SubworldLayers.find(sw.id, cx, cz, Integer.MAX_VALUE) >= 0;
    }

    /**
     * Сохранение чанка шаблона: снимок пишется один раз — когда чанк уже украшен целиком, а его ещё
     * нет ни в снимках, ни в опубликованных слоях.
     */
    static void onTemplateChunkSave(World world, SubworldDim d, Chunk chunk, NBTTagCompound nbt) {
        Subworld sw = SubworldRegistry.get(d.subworld);
        if (sw == null || !sw.perOwner() || !needsSnapshot(sw) || !(world instanceof WorldServer)) {
            return;
        }
        if (known(sw, chunk.x, chunk.z) || !fullyDecorated((WorldServer) world, chunk)) {
            return;
        }
        try {
            LayerRegions.write(dir(sw.id), chunk.x, chunk.z, nbt.copy());
        } catch (Exception e) {
            EcoServ.LOG.warn("Не удалось сохранить исходный вид чанка {} {} шаблона {}: {}", chunk.x, chunk.z, sw.id,
                    e.toString());
        }
    }

    /**
     * После заполнения чанка (cx, cz) окончательно украшенными могли стать он сам и соседи с востока,
     * юга и юго-востока (заполнение задевает квадрат 2×2 чанка). Сохраняем их сразу — это и запишет снимок.
     */
    static void afterPopulate(World world, int cx, int cz) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                saveNow(world, cx + dx, cz + dz);
            }
        }
    }

    /** Сохранить чанк шаблона сейчас, если для него пора писать снимок. */
    static void saveNow(World world, int cx, int cz) {
        if (!(world instanceof WorldServer) || DimensionManager.getWorld(world.provider.getDimension()) != world) {
            return;
        }
        WorldServer ws = (WorldServer) world;
        if (ws.disableLevelSaving) {
            // сохранение выключено (бэкап, копирование шаблона) — снимок запишется при следующем сохранении
            return;
        }
        ChunkProviderServer provider = ws.getChunkProvider();
        Chunk chunk = provider.getLoadedChunk(cx, cz);
        if (chunk == null) {
            return;
        }
        SubworldDim d = SubworldRegistry.dim(world.provider.getDimension());
        Subworld sw = d == null ? null : SubworldRegistry.get(d.subworld);
        if (sw == null || !d.isTemplate() || !sw.perOwner() || !needsSnapshot(sw) || known(sw, cx, cz)
                || !fullyDecorated(ws, chunk)) {
            return;
        }
        try {
            provider.chunkLoader.saveChunk(world, chunk);
        } catch (Exception e) {
            EcoServ.LOG.debug("Не удалось сохранить чанк шаблона {} {}: {}", cx, cz, e.toString());
        }
    }

    /** Чанк украшен целиком: заполнены он сам и соседи с запада, севера и северо-запада. */
    static boolean fullyDecorated(WorldServer world, Chunk chunk) {
        return chunk.isTerrainPopulated()
                && populated(world, chunk.x - 1, chunk.z)
                && populated(world, chunk.x, chunk.z - 1)
                && populated(world, chunk.x - 1, chunk.z - 1);
    }

    private static boolean populated(WorldServer world, int x, int z) {
        Chunk loaded = world.getChunkProvider().getLoadedChunk(x, z);
        if (loaded != null) {
            return loaded.isTerrainPopulated();
        }
        NBTTagCompound stored = storedNbt(world, x, z);
        return stored != null && stored.getCompoundTag("Level").getBoolean("TerrainPopulated");
    }

    /** NBT выгруженного чанка: из очереди записи или из файла региона (без загрузки чанка в мир). */
    @SuppressWarnings("unchecked")
    @Nullable
    private static NBTTagCompound storedNbt(WorldServer world, int x, int z) {
        IChunkLoader loader = world.getChunkProvider().chunkLoader;
        if (!(loader instanceof AnvilChunkLoader)) {
            return null;
        }
        AnvilChunkLoader anvil = (AnvilChunkLoader) loader;
        if (!pendingFieldFailed) {
            try {
                if (pendingField == null) {
                    pendingField = ObfuscationReflectionHelper.findField(AnvilChunkLoader.class, "field_75828_a");
                }
                NBTTagCompound pending = ((Map<ChunkPos, NBTTagCompound>) pendingField.get(anvil)).get(new ChunkPos(x, z));
                if (pending != null) {
                    return pending;
                }
            } catch (Throwable t) {
                pendingFieldFailed = true;
                EcoServ.LOG.debug("Очередь записи чанков недоступна: {}", t.toString());
            }
        }
        if (!RegionFileCache.chunkExists(anvil.chunkSaveLocation, x, z)) {
            return null;
        }
        DataInputStream in = null;
        try {
            in = RegionFileCache.getChunkInputStream(anvil.chunkSaveLocation, x, z);
            return in == null ? null : CompressedStreamTools.read(in);
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // только чтение
                }
            }
        }
    }

    /** Исходный вид чанка (снимок) или null. */
    @Nullable
    static NBTTagCompound read(String subworld, int x, int z) {
        try {
            return LayerRegions.read(dir(subworld), x, z);
        } catch (Exception e) {
            return null;
        }
    }
}
