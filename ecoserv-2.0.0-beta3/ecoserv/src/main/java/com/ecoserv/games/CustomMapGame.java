package com.ecoserv.games;

import com.ecoserv.build.BuildManager;
import com.ecoserv.build.Structure;
import com.ecoserv.dev.BlockMarker;
import com.ecoserv.init.ModSounds;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Своя игра на своей карте (2.0, конструктор игр). Карта — структура из библиотеки стройки, правила —
 * маркеры на ней: появление, команды, чекпоинты, финиш, зоны смерти, места предметов. Режимы: гонка,
 * выживание, сбор, царь горы.
 */
public class CustomMapGame extends ArenaGame implements HitGame {

    public static final String[][] MODES = {
            {"race", "Гонка", "Кто первым дойдёт до финиша (маркер «Финиш»). Чекпоинты сохраняют, зоны смерти — назад.",
                    "minecraft:golden_boots"},
            {"survival", "Выживание", "Упал или коснулся зоны смерти — минус жизнь. Последний живой побеждает. Удар толкает.",
                    "minecraft:skull"},
            {"collect", "Сбор", "Собирай точки на маркерах «Место предмета». Кто первым наберёт очки или больше всех к концу.",
                    "minecraft:gold_nugget"},
            {"king", "Царь горы", "Стой у маркера «Финиш» один и копи очки. Удар толкает.", "minecraft:gold_block"},
    };

    public static final class Kind extends GameKind {
        public Kind() {
            super("custom", "&bСвоя карта", "Игра на карте из стройки: правила задают маркеры на карте.",
                    "minecraft:structure_block", true);
            param("time", "Время (сек)", "", 300, 30, 1800);
            param("lives", "Жизней", "Для выживания", 3, 1, 20);
            param("goal", "Очков до победы", "Для сбора и царя горы", 20, 1, 500);
            flag("push", "Удар толкает", "", true);
        }

        @Override
        public Game create(GameManager.Session session) {
            return new CustomMapGame(session);
        }

        @Override
        public GamePreset defaults() {
            GamePreset p = super.defaults();
            p.text.put("mode", "race");
            p.minPlayers = 1;
            p.maxPlayers = 16;
            return p;
        }

        @Override
        public void editor(Ui ui, GamePreset p, EntityPlayerMP viewer) {
            ui.header("Карта и правила");
            Ui.Select map = ui.select("Карта", "Структура из библиотеки стройки (/build → Структуры)", p.text("map", ""), v -> {
                p.text.put("map", v);
                GameManager.save();
            });
            map.option("", "Пример (полоса препятствий)", "Встроенная карта для проверки", UiUtil.icon("minecraft:grass"));
            for (Structure s : BuildManager.library()) {
                map.option(s.name, s.name, s.sx + "×" + s.sy + "×" + s.sz, UiUtil.icon("minecraft:bookshelf"));
            }
            Ui.Select mode = ui.select("Режим", "", p.text("mode", "race"), v -> {
                p.text.put("mode", v);
                GameManager.save();
            });
            for (String[] m : MODES) {
                mode.option(m[0], m[1], m[2], UiUtil.icon(m[3]));
            }
            ui.note(Ui.BLUE, "Как сделать карту: построй, поставь маркеры (/dev → «Маркеры-помощники»): «Точка появления»,"
                    + " «Финиш», «Чекпоинт», «Зона смерти», «Место предмета». Выдели топором стройки и сохрани как структуру.");
        }
    }

    private final String mode;
    private final int limit;
    private final int lives;
    private final int goal;
    private final boolean pushOn;
    private final List<BlockPos> spawns = new ArrayList<BlockPos>();
    private final List<BlockPos> finishes = new ArrayList<BlockPos>();
    private final List<BlockPos> checkpoints = new ArrayList<BlockPos>();
    private final List<BlockPos> kills = new ArrayList<BlockPos>();
    private final List<BlockPos> items = new ArrayList<BlockPos>();
    private final Map<BlockPos, Integer> itemBack = new HashMap<BlockPos, Integer>();
    private final Map<UUID, Integer> score = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> livesLeft = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> lastHit = new HashMap<UUID, Integer>();
    private int floorY;

    public CustomMapGame(GameManager.Session session) {
        super(session);
        mode = preset.text("mode", "race");
        limit = param("time", 300) * 20;
        lives = param("lives", 3);
        goal = param("goal", 20);
        pushOn = param("push", 1) != 0;
    }

    @Override
    protected void build() {
        Structure s = BuildManager.structure(preset.text("map", ""));
        if (s == null) {
            buildExample();
            return;
        }
        for (int y = 0; y < s.sy; y++) {
            for (int z = 0; z < s.sz; z++) {
                for (int x = 0; x < s.sx; x++) {
                    int i = s.index(x, y, z);
                    int pi = s.blocks[i];
                    if (pi < 0 || pi >= s.palette.size()) {
                        continue;
                    }
                    IBlockState st = s.palette.get(pi);
                    BlockPos at = rel(x, y, z);
                    if (st.getBlock() instanceof BlockMarker) {
                        int type = st.getBlock().getMetaFromState(st);
                        if (marker(type, at)) {
                            continue;
                        }
                    }
                    setAbs(at, st);
                    NBTTagCompound t = s.tiles.get(i);
                    if (t != null) {
                        TileEntity te = world.getTileEntity(at);
                        if (te != null) {
                            NBTTagCompound c = t.copy();
                            c.setInteger("x", at.getX());
                            c.setInteger("y", at.getY());
                            c.setInteger("z", at.getZ());
                            te.readFromNBT(c);
                        }
                    }
                }
            }
        }
        floorY = origin.getY();
        bounds = new int[]{origin.getX() - 2, origin.getY() - 8, origin.getZ() - 2, origin.getX() + s.sx + 2,
                origin.getY() + s.sy + 4, origin.getZ() + s.sz + 2};
        if (spawns.isEmpty()) {
            spawns.add(rel(s.sx / 2, s.sy, s.sz / 2));
        }
    }

    /** Маркер карты → правило. true — блок не ставить. */
    private boolean marker(int type, BlockPos at) {
        switch (type) {
            case 0:
            case 1:
            case 2:
                spawns.add(at);
                return true;
            case 3:
                checkpoints.add(at);
                return true;
            case 4:
                finishes.add(at);
                return true;
            case 5:
                kills.add(at);
                return true;
            case 6:
                items.add(at);
                return true;
            default:
                return type != 12 && type != 14 && type != 13;
        }
    }

    /** Встроенная полоса препятствий. */
    private void buildExample() {
        int len = 40;
        fill(-4, 0, 0, 4, 0, 3, Blocks.QUARTZ_BLOCK.getDefaultState());
        for (int z = 5; z < len; z += 3) {
            int x = (z / 3 % 3) - 1;
            set(x * 2, (z / 6) % 3, z, Blocks.STAINED_HARDENED_CLAY.getStateFromMeta(z % 16));
            set(x * 2 + 1, (z / 6) % 3, z, Blocks.STAINED_HARDENED_CLAY.getStateFromMeta(z % 16));
        }
        fill(-4, 0, len, 4, 0, len + 4, Blocks.GOLD_BLOCK.getDefaultState());
        fill(-12, -3, -4, 12, -3, len + 8, Blocks.LAVA.getDefaultState());
        for (int x = -2; x <= 2; x += 2) {
            spawns.add(rel(x, 1, 1));
        }
        for (int x = -3; x <= 3; x++) {
            finishes.add(rel(x, 1, len + 2));
        }
        checkpoints.add(rel(0, 1, len / 2));
        for (int i = 0; i < 6; i++) {
            items.add(rel(((i % 3) - 1) * 2, 2, 8 + i * 5));
        }
        floorY = origin.getY();
        bounds = new int[]{origin.getX() - 14, origin.getY() - 6, origin.getZ() - 6, origin.getX() + 14, origin.getY() + 12,
                origin.getZ() + len + 10};
    }

    @Override
    protected BlockPos start(UUID player, int index) {
        return spawns.get(index % spawns.size());
    }

    @Override
    public void begin() {
        super.begin();
        for (UUID id : results.keySet()) {
            livesLeft.put(id, lives);
        }
        for (String[] m : MODES) {
            if (m[0].equals(mode)) {
                tell("&b" + m[1] + ": &7" + m[2]);
            }
        }
    }

    @Override
    public void onHit(EntityPlayerMP attacker, EntityPlayerMP victim) {
        if (!pushOn || "race".equals(mode)) {
            return;
        }
        Integer last = lastHit.get(attacker.getUniqueID());
        if (last != null && ticks - last < 8) {
            return;
        }
        lastHit.put(attacker.getUniqueID(), ticks);
        double dx = victim.posX - attacker.posX;
        double dz = victim.posZ - attacker.posZ;
        double l = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
        victim.motionX += dx / l * 0.9D;
        victim.motionZ += dz / l * 0.9D;
        victim.motionY = 0.4D;
        victim.velocityChanged = true;
    }

    private static boolean near(EntityPlayerMP p, BlockPos b, double r) {
        return p.getDistanceSq(b.getX() + 0.5D, b.getY() + 0.5D, b.getZ() + 0.5D) <= r * r;
    }

    private void lose(EntityPlayerMP p, String why) {
        if ("survival".equals(mode)) {
            int left = livesLeft.merge(p.getUniqueID(), -1, Integer::sum);
            if (left <= 0) {
                out(p.getUniqueID(), why);
                p.connection.setPlayerLocation(spawns.get(0).getX() + 0.5D, spawns.get(0).getY() + 6, spawns.get(0).getZ() + 0.5D, 0, 0);
                return;
            }
            respawn(p, why + " (жизней: " + left + ")");
        } else {
            respawn(p, why);
        }
    }

    @Override
    public void tick() {
        ticks++;
        UUID king = null;
        int onTop = 0;
        for (Game.Result r : results.values()) {
            if (r.place != 0 || r.out) {
                continue;
            }
            EntityPlayerMP p = here(r.id);
            if (p == null) {
                continue;
            }
            keepAlive(p);
            if (p.posY < floorY - 6 || (p.isInLava())) {
                lose(p, "Упал");
                continue;
            }
            boolean dead = false;
            for (BlockPos k : kills) {
                if (near(p, k, 1.2D)) {
                    dead = true;
                    break;
                }
            }
            if (dead) {
                lose(p, "Зона смерти");
                continue;
            }
            for (BlockPos c : checkpoints) {
                if (near(p, c, 1.6D) && !c.equals(checkpoint.get(p.getUniqueID()))) {
                    checkpoint.put(p.getUniqueID(), c);
                    com.ecoserv.util.ChatUtil.actionBar(p, "&aЧекпоинт!");
                    ModSounds.play(p, ModSounds.COIN, 0.6F, 1.4F);
                }
            }
            if ("race".equals(mode)) {
                for (BlockPos f : finishes) {
                    if (near(p, f, 1.6D)) {
                        finish(p.getUniqueID(), time(ticks));
                        break;
                    }
                }
            } else if ("collect".equals(mode)) {
                for (BlockPos it : items) {
                    if (!itemBack.containsKey(it) && near(p, it, 1.3D)) {
                        itemBack.put(it, ticks + 200);
                        int sc = score.merge(p.getUniqueID(), 1, Integer::sum);
                        r.score = sc;
                        ModSounds.play(p, ModSounds.COIN, 0.6F, 1.0F + sc / (float) goal);
                        if (sc >= goal) {
                            finish(p.getUniqueID(), sc + " очков");
                            endByScore();
                            return;
                        }
                    }
                }
            } else if ("king".equals(mode)) {
                for (BlockPos f : finishes) {
                    if (near(p, f, 2.2D)) {
                        onTop++;
                        king = r.id;
                        break;
                    }
                }
            }
        }
        if ("collect".equals(mode) && ticks % 5 == 0) {
            for (BlockPos it : items) {
                Integer back = itemBack.get(it);
                if (back != null && ticks >= back) {
                    itemBack.remove(it);
                }
                if (!itemBack.containsKey(it)) {
                    world.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, it.getX() + 0.5D, it.getY() + 0.5D, it.getZ() + 0.5D, 3, 0.2D, 0.2D, 0.2D, 0.0D);
                }
            }
        }
        if (ticks % 10 == 0) {
            for (BlockPos f : finishes) {
                world.spawnParticle(EnumParticleTypes.END_ROD, f.getX() + 0.5D, f.getY() + 0.3D, f.getZ() + 0.5D, 1, 0.3D, 0.3D, 0.3D, 0.0D);
            }
        }
        if ("king".equals(mode) && onTop == 1 && ticks % 20 == 0) {
            int sc = score.merge(king, 1, Integer::sum);
            results.get(king).score = sc;
            if (sc >= goal) {
                finish(king, sc + " очков");
                endByScore();
                return;
            }
        }
        if ("survival".equals(mode) && active().size() <= 1 && results.size() > 1) {
            for (EntityPlayerMP p : active()) {
                finish(p.getUniqueID(), "последний живой");
            }
            end();
            return;
        }
        if (ticks >= limit) {
            endByScore();
            return;
        }
        if (ticks % 10 == 0) {
            for (EntityPlayerMP p : active()) {
                String extra = "";
                if ("survival".equals(mode)) {
                    extra = "&c❤ " + livesLeft.get(p.getUniqueID());
                } else if (!"race".equals(mode)) {
                    Integer sc = score.get(p.getUniqueID());
                    extra = "&e" + (sc == null ? 0 : sc) + "&7/" + goal;
                }
                com.ecoserv.util.ChatUtil.actionBar(p, extra + " &8· &7" + time(Math.max(0, limit - ticks)));
            }
        }
    }

    /** Конец по очкам/таймеру: места по очкам. */
    private void endByScore() {
        List<Game.Result> left = new ArrayList<Game.Result>();
        for (Game.Result r : results.values()) {
            if (r.place == 0 && !r.out) {
                left.add(r);
            }
        }
        left.sort((a, b) -> Double.compare(b.score, a.score));
        for (Game.Result r : left) {
            if (r.score > 0 && !"race".equals(mode)) {
                finish(r.id, (int) r.score + " очков");
            } else {
                r.out = true;
                r.note = "время вышло";
            }
        }
        end();
    }

    @Override
    public String status() {
        return "идёт " + time(ticks);
    }
}
