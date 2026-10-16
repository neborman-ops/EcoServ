package com.ecoserv.subworld;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.ChunkGeneratorEnd;
import net.minecraft.world.gen.ChunkGeneratorFlat;
import net.minecraft.world.gen.ChunkGeneratorHell;
import net.minecraft.world.gen.IChunkGenerator;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Генераторы чанков подмиров. */
public final class SubworldGen {

    private SubworldGen() {
    }

    /** Генератор для измерения: базовый по типу; если у подмира есть опубликованные слои — сначала копия из них. */
    public static IChunkGenerator create(World world, Subworld sw, SubworldDim d) {
        IChunkGenerator base = base(world, sw);
        // загруженный мир — всегда через копию (слой 0 мог ещё копироваться, когда мир открылся)
        if (SubworldLayers.hasAny(sw.id) || d.isInstance() || Subworld.CUSTOM.equals(sw.type)) {
            return new LazyCopyGenerator(world, base, sw.id);
        }
        return base;
    }

    public static IChunkGenerator base(World world, Subworld sw) {
        long seed = world.getSeed();
        boolean features = sw.structures;
        switch (sw.type) {
            case Subworld.NETHER:
                return new ChunkGeneratorHell(world, features, seed);
            case Subworld.END:
                return new ChunkGeneratorEnd(world, features, seed, new BlockPos(100, 50, 0));
            case Subworld.FLAT:
                return new ChunkGeneratorFlat(world, seed, features, SubworldInfo.generatorOptions(sw));
            case Subworld.VOID:
            case Subworld.CUSTOM:
                return new VoidGenerator(world, Subworld.VOID.equals(sw.type) ? Biomes.VOID : Biomes.PLAINS);
            default:
                return SubworldInfo.worldType(sw).getChunkGenerator(world, SubworldInfo.generatorOptions(sw));
        }
    }

    /** Пустота: пустые чанки, без украшений и структур. */
    public static class VoidGenerator implements IChunkGenerator {
        private final World world;
        private final Biome biome;

        public VoidGenerator(World world, Biome biome) {
            this.world = world;
            this.biome = biome;
        }

        @Override
        public Chunk generateChunk(int x, int z) {
            Chunk chunk = new Chunk(world, new ChunkPrimer(), x, z);
            byte[] biomes = chunk.getBiomeArray();
            Arrays.fill(biomes, (byte) Biome.getIdForBiome(biome));
            chunk.generateSkylightMap();
            return chunk;
        }

        @Override
        public void populate(int x, int z) {
        }

        @Override
        public boolean generateStructures(Chunk chunk, int x, int z) {
            return false;
        }

        /** Мобы — по биому места (у загруженного мира биомы свои; у пустоты — никого). */
        @Override
        public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, BlockPos pos) {
            Biome b = world.getBiome(pos);
            return b == null ? Collections.<Biome.SpawnListEntry>emptyList() : b.getSpawnableList(type);
        }

        @Nullable
        @Override
        public BlockPos getNearestStructurePos(World world, String name, BlockPos pos, boolean findUnexplored) {
            return null;
        }

        @Override
        public void recreateStructures(Chunk chunk, int x, int z) {
        }

        @Override
        public boolean isInsideStructure(World world, String name, BlockPos pos) {
            return false;
        }
    }

    /**
     * Чанка ещё нет в папке мира — берём его из самого нового опубликованного слоя шаблона
     * (те же координаты, никаких пересчётов), иначе — обычная генерация тем же зерном.
     * После первого сохранения чанк живёт уже в папке этого мира.
     */
    public static class LazyCopyGenerator implements IChunkGenerator {
        private final World world;
        private final IChunkGenerator base;
        private final String subworld;
        private final TemplateChunkLoader loader;

        public LazyCopyGenerator(World world, IChunkGenerator base, String subworld) {
            this.world = world;
            this.base = base;
            this.subworld = subworld;
            this.loader = new TemplateChunkLoader(SubworldLayers.root(subworld),
                    ((WorldServer) world).getMinecraftServer().getDataFixer());
        }

        @Override
        public Chunk generateChunk(int x, int z) {
            Subworld sw = SubworldRegistry.get(subworld);
            if (sw != null) {
                // версию отмечаем до загрузки: событие загрузки чанка увидит, что он уже свежий
                TemplateEdits.markVersion(world, x, z, sw.templateVersion);
                int version = SubworldLayers.find(subworld, x, z, sw.templateVersion);
                if (version >= 0) {
                    Chunk chunk = loader.load(world, subworld, version, x, z);
                    if (chunk != null) {
                        return chunk;
                    }
                }
            }
            return base.generateChunk(x, z);
        }

        @Override
        public void populate(int x, int z) {
            base.populate(x, z);
        }

        @Override
        public boolean generateStructures(Chunk chunk, int x, int z) {
            return base.generateStructures(chunk, x, z);
        }

        @Override
        public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, BlockPos pos) {
            return base.getPossibleCreatures(type, pos);
        }

        @Nullable
        @Override
        public BlockPos getNearestStructurePos(World w, String name, BlockPos pos, boolean findUnexplored) {
            return base.getNearestStructurePos(w, name, pos, findUnexplored);
        }

        @Override
        public void recreateStructures(Chunk chunk, int x, int z) {
            base.recreateStructures(chunk, x, z);
        }

        @Override
        public boolean isInsideStructure(World w, String name, BlockPos pos) {
            return base.isInsideStructure(w, name, pos);
        }
    }
}
