package com.ecoserv.subworld;

import com.ecoserv.EcoServ;
import com.ecoserv.util.NbtFiles;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Random;

/**
 * Свой WorldInfo у каждого измерения подмира (как в JustEnoughDimensions): своё зерно, тип генерации,
 * время, погода, правила игры и точка появления. Хранится в &lt;папка мира&gt;/ecoserv_level.dat.
 */
public final class SubworldInfo {

    public static final String FILE = "ecoserv_level.dat";
    private static Field worldInfoField;

    private SubworldInfo() {
    }

    private static Field field() {
        if (worldInfoField == null) {
            worldInfoField = ObfuscationReflectionHelper.findField(World.class, "field_72986_A");
        }
        return worldInfoField;
    }

    /** Поставить миру свой WorldInfo (вызывается из WorldProviderSubworld.init на сервере). */
    public static boolean install(World world, WorldInfo info) {
        try {
            field().set(world, info);
            return true;
        } catch (Exception e) {
            EcoServ.LOG.error("Не удалось поставить свой WorldInfo измерению подмира — будет общий с основным миром", e);
            return false;
        }
    }

    public static WorldType worldType(Subworld sw) {
        if (Subworld.FLAT.equals(sw.type)) {
            return WorldType.FLAT;
        }
        if (!Subworld.NORMAL.equals(sw.type)) {
            return WorldType.DEFAULT;
        }
        WorldType type = WorldType.byName(sw.worldType);
        return type == null ? WorldType.DEFAULT : type;
    }

    /** Строка пресета плоского мира; для пустоты — один слой воздуха. */
    public static String generatorOptions(Subworld sw) {
        if (Subworld.FLAT.equals(sw.type)) {
            return sw.generatorOptions == null || sw.generatorOptions.trim().isEmpty()
                    ? "3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;village" : sw.generatorOptions.trim();
        }
        return sw.generatorOptions == null ? "" : sw.generatorOptions;
    }

    /** Прочитать сохранённый или создать новый WorldInfo для измерения. */
    public static WorldInfo loadOrCreate(Subworld sw, SubworldDim d) {
        File file = new File(SubworldRegistry.folderOf(d), FILE);
        NBTTagCompound nbt = NbtFiles.read(file);
        if (nbt != null) {
            return new WorldInfo(nbt);
        }
        // локальный мир команды — копия настроек шаблона (то же зерно, тот же спавн)
        if (d.isInstance()) {
            SubworldDim template = SubworldRegistry.find(sw.id, SubworldDim.TEMPLATE);
            if (template != null) {
                NBTTagCompound tpl = NbtFiles.read(new File(SubworldRegistry.folderOf(template), FILE));
                if (tpl != null) {
                    // правила игры админов-строителей (randomTickSpeed 0, doTileDrops false, мирный режим...)
                    // игрокам не передаём: берём правила основного мира и правила подмира
                    tpl.removeTag("GameRules");
                    WorldInfo copy = new WorldInfo(tpl);
                    copy.setWorldName(sw.id);
                    WorldInfo overworld = overworldInfo();
                    if (overworld != null) {
                        GameRules base = overworld.getGameRulesInstance();
                        GameRules rules = copy.getGameRulesInstance();
                        for (String rule : base.getRules()) {
                            rules.setOrCreateGameRule(rule, base.getString(rule));
                        }
                        copy.setDifficulty(overworld.getDifficulty());
                        copy.setGameType(overworld.getGameType());
                    }
                    applyRules(copy, sw.rules);
                    return copy;
                }
            }
        }
        return create(sw);
    }

    public static WorldInfo create(Subworld sw) {
        if (sw.seed == 0L) {
            sw.seed = new Random().nextLong();
            SubworldRegistry.markDirty();
        }
        WorldInfo overworld = overworldInfo();
        boolean hardcore = overworld != null && overworld.isHardcoreModeEnabled();
        WorldSettings settings = new WorldSettings(sw.seed, GameType.SURVIVAL, sw.structures, hardcore, worldType(sw));
        settings.setGeneratorOptions(generatorOptions(sw));
        WorldInfo info = new WorldInfo(settings, sw.id);
        info.setSpawn(new net.minecraft.util.math.BlockPos(0, Subworld.NETHER.equals(sw.type) ? 64 : 80, 0));
        if (overworld != null) {
            info.setDifficulty(overworld.getDifficulty());
        }
        applyRules(info, sw.rules);
        return info;
    }

    @Nullable
    private static WorldInfo overworldInfo() {
        World overworld = DimensionManager.getWorld(0);
        return overworld == null ? null : overworld.getWorldInfo();
    }

    /** Правила подмира в WorldInfo: время, погода, keepInventory, мобы, огонь, грифинг. */
    public static void applyRules(WorldInfo info, Subworld.Rules rules) {
        GameRules g = info.getGameRulesInstance();
        long fixed = fixedTime(rules.time);
        g.setOrCreateGameRule("doDaylightCycle", fixed < 0 ? "true" : "false");
        if (fixed >= 0) {
            info.setWorldTime(fixed);
        }
        String weather = rules.weather == null ? "cycle" : rules.weather;
        g.setOrCreateGameRule("doWeatherCycle", "cycle".equals(weather) ? "true" : "false");
        if (!"cycle".equals(weather)) {
            boolean rain = "rain".equals(weather) || "thunder".equals(weather);
            info.setRaining(rain);
            info.setThundering("thunder".equals(weather));
            info.setRainTime(rain ? 1000000 : 0);
            info.setThunderTime("thunder".equals(weather) ? 1000000 : 0);
            info.setCleanWeatherTime(rain ? 0 : 1000000);
        }
        g.setOrCreateGameRule("keepInventory", Boolean.toString(rules.keepInventory));
        g.setOrCreateGameRule("doMobSpawning", Boolean.toString(rules.mobs));
        g.setOrCreateGameRule("doFireTick", Boolean.toString(rules.fireSpread));
        g.setOrCreateGameRule("mobGriefing", Boolean.toString(rules.mobGriefing));
    }

    /** Фиксированное время в тиках или -1 для обычной смены дня и ночи. */
    public static long fixedTime(String time) {
        String t = time == null ? "cycle" : time.trim().toLowerCase(Locale.ROOT);
        switch (t) {
            case "day":
                return 1000L;
            case "noon":
                return 6000L;
            case "sunset":
                return 12000L;
            case "night":
                return 14000L;
            case "midnight":
                return 18000L;
            case "cycle":
            case "":
                return -1L;
            default:
                try {
                    return Math.floorMod(Long.parseLong(t), 24000L);
                } catch (NumberFormatException e) {
                    return -1L;
                }
        }
    }

    public static void save(World world, SubworldDim d) {
        if (world.getWorldInfo() instanceof net.minecraft.world.storage.DerivedWorldInfo) {
            // свой WorldInfo поставить не удалось — копию основного мира не сохраняем как «свою»
            return;
        }
        File dir = SubworldRegistry.folderOf(d);
        NbtFiles.write(new File(dir, FILE), world.getWorldInfo().cloneNBTCompound(null));
    }
}
