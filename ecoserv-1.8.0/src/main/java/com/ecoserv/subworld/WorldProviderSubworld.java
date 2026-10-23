package com.ecoserv.subworld;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.gen.FlatGeneratorInfo;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * Провайдер всех измерений подмиров. Тип мира (обычный/ад/край/плоский/пустота/свой), небо и свет
 * берутся из настроек подмира: на сервере — из реестра, на клиенте — из пакета синхронизации.
 */
public class WorldProviderSubworld extends WorldProvider {

    private String kind = Subworld.NORMAL;
    /** Свои копии типа генерации и настроек (у WorldProvider эти поля закрыты). */
    private net.minecraft.world.WorldType myType = net.minecraft.world.WorldType.DEFAULT;
    private String myOptions = "";

    public WorldProviderSubworld() {
    }

    @Nullable
    private SubworldDim record() {
        return SubworldRegistry.dim(getDimension());
    }

    @Nullable
    private Subworld subworld() {
        SubworldDim d = record();
        return d == null ? null : SubworldRegistry.get(d.subworld);
    }

    @Override
    protected void init() {
        if (!world.isRemote) {
            SubworldDim d = record();
            Subworld sw = subworld();
            if (d != null && sw != null) {
                kind = sw.type;
                WorldInfo info = SubworldInfo.loadOrCreate(sw, d);
                if (SubworldInfo.install(world, info)) {
                    myType = info.getTerrainType();
                    myOptions = info.getGeneratorOptions();
                } else {
                    myType = SubworldInfo.worldType(sw);
                    myOptions = SubworldInfo.generatorOptions(sw);
                }
            }
        } else {
            SubworldView view = SubworldDims.clientView(getDimension());
            kind = view == null ? Subworld.NORMAL : view.type;
            myType = Subworld.FLAT.equals(kind) ? net.minecraft.world.WorldType.FLAT : world.getWorldInfo().getTerrainType();
        }
        hasSkyLight = !Subworld.NETHER.equals(kind) && !Subworld.END.equals(kind);
        nether = Subworld.NETHER.equals(kind);
        doesWaterVaporize = nether;
        biomeProvider = createBiomes();
    }

    private BiomeProvider createBiomes() {
        switch (kind) {
            case Subworld.NETHER:
                return new BiomeProviderSingle(Biomes.HELL);
            case Subworld.END:
                return new BiomeProviderSingle(Biomes.SKY);
            case Subworld.VOID:
                return new BiomeProviderSingle(Biomes.VOID);
            case Subworld.FLAT: {
                String options = myOptions == null || myOptions.isEmpty()
                        ? "3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;village" : myOptions;
                FlatGeneratorInfo flat = FlatGeneratorInfo.createFlatGeneratorFromString(options);
                return new BiomeProviderSingle(Biome.getBiome(flat.getBiome(), Biomes.PLAINS));
            }
            default:
                // тип генерации из мода (Biomes O' Plenty и т.п.) даёт свои биомы
                try {
                    return myType.getBiomeProvider(world);
                } catch (Exception e) {
                    return new BiomeProvider(world.getWorldInfo());
                }
        }
    }

    /** Свет в аду — с «подсветкой», как у ванильного ада. */
    @Override
    protected void generateLightBrightnessTable() {
        float ambient = Subworld.NETHER.equals(kind) ? 0.1F : 0.0F;
        for (int i = 0; i <= 15; ++i) {
            float f1 = 1.0F - i / 15.0F;
            lightBrightnessTable[i] = (1.0F - f1) / (f1 * 3.0F + 1.0F) * (1.0F - ambient) + ambient;
        }
    }

    @Override
    public IChunkGenerator createChunkGenerator() {
        SubworldDim d = record();
        Subworld sw = subworld();
        if (d == null || sw == null) {
            return new SubworldGen.VoidGenerator(world, Biomes.VOID);
        }
        return SubworldGen.create(world, sw, d);
    }

    @Override
    public DimensionType getDimensionType() {
        return SubworldDims.TYPE;
    }

    @Override
    public boolean isSurfaceWorld() {
        if (world != null && world.isRemote) {
            // на клиенте это решает, рисовать ли обычное небо и облака
            SubworldView v = SubworldDims.clientView(getDimension());
            if (v != null && !"default".equals(v.sky)) {
                return "overworld".equals(v.sky);
            }
        }
        return !Subworld.NETHER.equals(kind) && !Subworld.END.equals(kind);
    }

    @Override
    public boolean canRespawnHere() {
        if (world != null && world.isRemote) {
            return true;
        }
        Subworld sw = subworld();
        return sw == null || sw.respawnInside;
    }

    @Override
    public int getRespawnDimension(EntityPlayerMP player) {
        return 0;
    }

    @Nullable
    @Override
    public String getSaveFolder() {
        String folder = SubworldRegistry.saveFolder(getDimension());
        return folder != null ? folder : super.getSaveFolder();
    }

    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks) {
        if (Subworld.END.equals(kind) || Subworld.NETHER.equals(kind)) {
            return 0.5F;
        }
        return super.calculateCelestialAngle(worldTime, partialTicks);
    }

    @Override
    public int getAverageGroundLevel() {
        return Subworld.FLAT.equals(kind) ? 4 : Subworld.VOID.equals(kind) ? 64 : super.getAverageGroundLevel();
    }

    @Override
    public double getHorizon() {
        return Subworld.FLAT.equals(kind) || Subworld.VOID.equals(kind) ? 0.0D : 63.0D;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public double getVoidFogYFactor() {
        return Subworld.FLAT.equals(kind) || Subworld.VOID.equals(kind) ? 1.0D : 0.03125D;
    }

    @Override
    public boolean canDropChunk(int x, int z) {
        return true;
    }

    /** Возрождение без кровати: безопасная точка у спавна (в пустоте — площадка), а не случайная. */
    @Override
    public BlockPos getRandomizedSpawnPoint() {
        if (world instanceof WorldServer && record() != null) {
            return SubworldTeleport.safeSpawn((WorldServer) world);
        }
        return super.getRandomizedSpawnPoint();
    }

    @Override
    public BlockPos getSpawnCoordinate() {
        return Subworld.END.equals(kind) ? new BlockPos(100, 50, 0) : null;
    }

    /** Сохраняем свой WorldInfo вместе с миром. */
    @Override
    public void onWorldSave() {
        // мир, чьи файлы сейчас копируются (или бэкап), на диск не пишет
        if (world instanceof WorldServer && !((WorldServer) world).disableLevelSaving) {
            SubworldDim d = record();
            if (d != null) {
                SubworldInfo.save(world, d);
            }
        }
    }

    // --- клиент: небо и туман -------------------------------------------------------

    @Override
    @SideOnly(Side.CLIENT)
    public net.minecraftforge.client.IRenderHandler getSkyRenderer() {
        net.minecraftforge.client.IRenderHandler custom = com.ecoserv.client.SubworldSky.renderer(getDimension(), kind);
        return custom != null ? custom : super.getSkyRenderer();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Vec3d getFogColor(float celestialAngle, float partialTicks) {
        Vec3d custom = com.ecoserv.client.SubworldSky.fogColor(getDimension(), kind);
        return custom != null ? custom : super.getFogColor(celestialAngle, partialTicks);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean doesXZShowFog(int x, int z) {
        return Subworld.NETHER.equals(kind) || com.ecoserv.client.SubworldSky.netherFog(getDimension());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public float getCloudHeight() {
        return com.ecoserv.client.SubworldSky.hideClouds(getDimension(), kind) ? -1000.0F : super.getCloudHeight();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean isSkyColored() {
        return !Subworld.NETHER.equals(kind) && !Subworld.END.equals(kind);
    }

    @Nullable
    @Override
    @SideOnly(Side.CLIENT)
    public float[] calcSunriseSunsetColors(float celestialAngle, float partialTicks) {
        if (Subworld.END.equals(kind) || Subworld.NETHER.equals(kind) || com.ecoserv.client.SubworldSky.solid(getDimension())) {
            return null;
        }
        return super.calcSunriseSunsetColors(celestialAngle, partialTicks);
    }
}
