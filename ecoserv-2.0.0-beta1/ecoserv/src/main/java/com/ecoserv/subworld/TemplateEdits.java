package com.ecoserv.subworld;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Глобальное редактирование подмиров (п. 16). У каждого чанка локального мира в NBT записаны:
 * версия шаблона, с которой он сверен, и какие блоки в нём поставили игроки. После публикации новой
 * версии изменённые места переносятся в чанки локальных миров (TemplatePatcher), а блоки игроков,
 * оказавшиеся на пути новых блоков шаблона, уходят в хранилище ГЛИТЧ.
 */
public final class TemplateEdits {

    /** Ключи в NBT чанка (Level). */
    public static final String TAG = "ecoserv_tpl";
    public static final String PLACED_TAG = "ecoserv_placed";

    /** «измерение:x:z» -> версия шаблона загруженного чанка локального мира. */
    static final Map<String, Integer> VERSIONS = new ConcurrentHashMap<String, Integer>();
    /** «измерение:x:z» -> блоки, поставленные игроками (индекс y<<8 | z<<4 | x). */
    static final Map<String, BitSet> PLACED = new ConcurrentHashMap<String, BitSet>();

    private TemplateEdits() {
    }

    static String key(int dim, int x, int z) {
        return dim + ":" + x + ":" + z;
    }

    static int index(BlockPos pos) {
        return (pos.getY() & 255) << 8 | (pos.getZ() & 15) << 4 | (pos.getX() & 15);
    }

    /** Чанк локального мира только что скопирован из слоя шаблона (или создан генератором) с этой версией. */
    public static void markVersion(World world, int cx, int cz, int version) {
        if (instanceDim(world)) {
            VERSIONS.put(key(world.provider.getDimension(), cx, cz), version);
        }
    }

    static void setVersion(int dim, int cx, int cz, int version) {
        VERSIONS.put(key(dim, cx, cz), version);
    }

    public static int versionOf(int dim, int cx, int cz, int fallback) {
        Integer v = VERSIONS.get(key(dim, cx, cz));
        return v == null ? fallback : v;
    }

    /** Поставил ли этот блок игрок. */
    static boolean placed(int dim, BlockPos pos) {
        BitSet bits = PLACED.get(key(dim, pos.getX() >> 4, pos.getZ() >> 4));
        return bits != null && bits.get(index(pos));
    }

    static void clearPlaced(int dim, BlockPos pos) {
        BitSet bits = PLACED.get(key(dim, pos.getX() >> 4, pos.getZ() >> 4));
        if (bits != null) {
            bits.clear(index(pos));
        }
    }

    private static void markPlaced(int dim, BlockPos pos, boolean value) {
        String k = key(dim, pos.getX() >> 4, pos.getZ() >> 4);
        BitSet bits = PLACED.get(k);
        if (bits == null) {
            if (!value) {
                return;
            }
            bits = new BitSet(65536);
            PLACED.put(k, bits);
        }
        bits.set(index(pos), value);
    }

    /** Новая версия опубликована — обновить загруженные чанки локальных миров. */
    public static void onPublished(Subworld sw, int version) {
        TemplatePatcher.queueLoaded(sw, version);
    }

    public static void onWorldUnload(int dim) {
        String prefix = dim + ":";
        VERSIONS.keySet().removeIf(k -> k.startsWith(prefix));
        PLACED.keySet().removeIf(k -> k.startsWith(prefix));
        TemplatePatcher.forgetDim(dim);
    }

    public static void tick() {
        TemplatePatcher.tick();
    }

    private static boolean instanceDim(World world) {
        if (world == null || world.isRemote) {
            return false;
        }
        SubworldDim d = SubworldRegistry.dim(world.provider.getDimension());
        return d != null && d.isInstance();
    }

    /** Обработчик событий чанков и блоков (регистрируется вместе с событиями подмиров). */
    public static class Events {

        @SubscribeEvent
        public void onChunkLoad(ChunkDataEvent.Load event) {
            World world = event.getWorld();
            if (!instanceDim(world)) {
                return;
            }
            int dim = world.provider.getDimension();
            SubworldDim d = SubworldRegistry.dim(dim);
            Chunk chunk = event.getChunk();
            NBTTagCompound level = event.getData().getCompoundTag("Level");
            String k = key(dim, chunk.x, chunk.z);
            if (level.hasKey(TAG)) {
                VERSIONS.put(k, level.getInteger(TAG));
            } else if (!VERSIONS.containsKey(k)) {
                // чанк из прошлой версии мода: считаем, что он сверен с версией, с которой создан мир
                VERSIONS.put(k, d.templateVersion);
            }
            if (level.hasKey(PLACED_TAG, 7)) {
                BitSet bits = BitSet.valueOf(level.getByteArray(PLACED_TAG));
                if (!bits.isEmpty()) {
                    PLACED.put(k, bits);
                }
            } else {
                PLACED.remove(k);
            }
            Subworld sw = SubworldRegistry.get(d.subworld);
            if (sw != null && VERSIONS.get(k) < sw.templateVersion) {
                TemplatePatcher.queue(dim, chunk.x, chunk.z);
            }
        }

        @SubscribeEvent
        public void onChunkSave(ChunkDataEvent.Save event) {
            World world = event.getWorld();
            if (world == null || world.isRemote) {
                return;
            }
            int dim = world.provider.getDimension();
            SubworldDim d = SubworldRegistry.dim(dim);
            if (d == null) {
                return;
            }
            Chunk chunk = event.getChunk();
            if (d.isInstance()) {
                String k = key(dim, chunk.x, chunk.z);
                NBTTagCompound level = event.getData().getCompoundTag("Level");
                Integer v = VERSIONS.get(k);
                if (v != null) {
                    level.setInteger(TAG, v);
                }
                BitSet bits = PLACED.get(k);
                if (bits != null && !bits.isEmpty()) {
                    level.setByteArray(PLACED_TAG, bits.toByteArray());
                }
                if (!chunk.isLoaded()) {
                    // последнее сохранение перед выгрузкой чанка
                    VERSIONS.remove(k);
                    PLACED.remove(k);
                }
            } else if (d.isTemplate()) {
                TemplateBase.onTemplateChunkSave(world, d, chunk, event.getData());
            }
        }

        /**
         * Шаблон: чанк только что «украсили» (деревья, руды). Он и соседи с востока и юга могли стать
         * украшенными целиком — сохранить их сразу, чтобы снимок исходного вида места (TemplateBase)
         * получился до правок админов. Через тик: моды добавляют свои руды уже после этого события.
         */
        @SubscribeEvent
        public void onPopulate(net.minecraftforge.event.terraingen.PopulateChunkEvent.Post event) {
            final World world = event.getWorld();
            if (world == null || world.isRemote) {
                return;
            }
            SubworldDim d = SubworldRegistry.dim(world.provider.getDimension());
            Subworld sw = d == null ? null : SubworldRegistry.get(d.subworld);
            if (d == null || sw == null || !d.isTemplate() || !TemplateBase.needsSnapshot(sw)) {
                return;
            }
            final int cx = event.getChunkX();
            final int cz = event.getChunkZ();
            com.ecoserv.server.ServerTasks.later(1, new Runnable() {
                @Override
                public void run() {
                    TemplateBase.afterPopulate(world, cx, cz);
                }
            });
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onPlace(BlockEvent.PlaceEvent event) {
            if (event.isCanceled() || event.getPlayer() == null || !instanceDim(event.getWorld())) {
                return;
            }
            int dim = event.getWorld().provider.getDimension();
            if (event instanceof BlockEvent.MultiPlaceEvent) {
                for (BlockSnapshot s : ((BlockEvent.MultiPlaceEvent) event).getReplacedBlockSnapshots()) {
                    markPlaced(dim, s.getPos(), true);
                }
            } else {
                markPlaced(dim, event.getPos(), true);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onBreak(BlockEvent.BreakEvent event) {
            if (!event.isCanceled() && instanceDim(event.getWorld())) {
                markPlaced(event.getWorld().provider.getDimension(), event.getPos(), false);
            }
        }
    }
}
