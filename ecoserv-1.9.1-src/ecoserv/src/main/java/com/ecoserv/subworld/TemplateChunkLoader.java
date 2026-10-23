package com.ecoserv.subworld;

import com.ecoserv.EcoServ;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Чтение чанков из опубликованных слоёв шаблона в локальный мир: NBT → обновление формата →
 * пересчёт id загруженного мира → чанк с сущностями и блок-сущностями (как при обычной загрузке).
 */
public class TemplateChunkLoader extends AnvilChunkLoader {

    private final DataFixer fixer;

    public TemplateChunkLoader(File dir, DataFixer fixer) {
        super(dir, fixer);
        this.fixer = fixer;
    }

    /** NBT чанка из слоя (уже обновлённый и пересчитанный) или null. */
    @Nullable
    public NBTTagCompound readNbt(String subworld, int version, int x, int z) {
        File dir = SubworldLayers.layerDir(subworld, version);
        try {
            NBTTagCompound nbt = LayerRegions.read(dir, x, z);
            if (nbt == null) {
                return null;
            }
            nbt = fixer.process(FixTypes.CHUNK, nbt);
            SubworldLayers.Remap remap = SubworldLayers.remap(subworld, version);
            if (remap != null && nbt.hasKey("Level", 10)) {
                SubworldLayers.applyRemap(nbt.getCompoundTag("Level"), remap);
            }
            return nbt;
        } catch (Exception e) {
            EcoServ.LOG.error("Не удалось прочитать чанк {} {} из слоя {} подмира {}", x, z, version, subworld, e);
            return null;
        }
    }

    /** Собрать чанк из NBT для мира world (сущности добавляются при загрузке чанка). null — чанк повреждён. */
    @Nullable
    public Chunk build(World world, int x, int z, NBTTagCompound nbt) {
        Chunk chunk;
        try {
            Object[] data = checkedReadChunkFromNBT__Async(world, x, z, nbt);
            if (data == null) {
                return null;
            }
            chunk = (Chunk) data[0];
            NBTTagCompound level = nbt.getCompoundTag("Level");
            loadEntities(world, level, chunk);
        } catch (Exception e) {
            EcoServ.LOG.error("Повреждённый чанк {} {} в слое шаблона — будет обычная генерация", x, z, e);
            return null;
        }
        MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk, nbt));
        return chunk;
    }

    @Nullable
    public Chunk load(World world, String subworld, int version, int x, int z) {
        NBTTagCompound nbt = readNbt(subworld, version, x, z);
        return nbt == null ? null : build(world, x, z, nbt);
    }
}
