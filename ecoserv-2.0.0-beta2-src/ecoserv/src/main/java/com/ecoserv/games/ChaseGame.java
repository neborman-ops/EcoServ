package com.ecoserv.games;

import com.ecoserv.init.ModSounds;
import com.ecoserv.util.ChatUtil;
import net.minecraft.block.BlockColored;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * «Погоня» (1.9.0): сзади по коридору ползёт стена тьмы и всё ускоряется. Впереди — головоломки на тайминг:
 * ворота, которые открываются на пару секунд, прессы, лазерные полосы, двери с плитой (кто-то должен держать),
 * провалы. Ошибся — отбросило назад, ближе к тьме. Догнала — выбыл.
 */
public class ChaseGame extends ArenaGame {

    public static final class Kind extends GameKind {
        public Kind() {
            super("chase", "&8Погоня", "За тобой ползёт тьма. Впереди ворота, прессы, лазеры и двери с плитой — лови "
                    + "момент, иначе отбросит назад.", "minecraft:obsidian", true);
            param("difficulty", "Сложность", "Скорость тьмы и время открытых ворот", 3, 1, 5);
            param("length", "Длина коридора", "блоков", 120, 60, 400);
        }

        @Override
        public Game create(GameManager.Session session) {
            return new ChaseGame(session);
        }

        @Override
        public GamePreset defaults() {
            GamePreset p = super.defaults();
            p.maxPlayers = 10;
            return p;
        }
    }

    private enum Type {
        GATE, CRUSHER, LASER, PLATE, GAP
    }

    private static final class Obstacle {
        Type type;
        int z;
        int x;
        int period;
        int phase;
        boolean state;
        int openUntil;
        final List<BlockPos> blocks = new ArrayList<BlockPos>();
        final List<BlockPos> floor = new ArrayList<BlockPos>();
        BlockPos plate;
    }

    private static final IBlockState WALL = Blocks.STONEBRICK.getDefaultState();
    private static final IBlockState FLOOR = Blocks.STONEBRICK.getDefaultState();
    private static final IBlockState DARK = Blocks.CONCRETE.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.BLACK);

    private final List<Obstacle> obstacles = new ArrayList<Obstacle>();
    private final Random rnd = new Random();
    private int difficulty;
    private int length;
    private int finishZ;
    private double wallZ = -10.0D;
    private int filledZ = -11;
    private double speed;

    public ChaseGame(GameManager.Session session) {
        super(session);
        difficulty = param("difficulty", 3);
        length = param("length", 120);
        speed = 1.6D + difficulty * 0.35D;
    }

    @Override
    protected void build() {
        int start = -12;
        int end = length + 12;
        // коридор
        fill(-3, 0, start, 3, 0, end, FLOOR);
        fill(-3, 1, start, -3, 4, end, WALL);
        fill(3, 1, start, 3, 4, end, WALL);
        fill(-3, 5, start, 3, 5, end, Blocks.GLASS.getDefaultState());
        fill(-2, 1, start, 2, 4, start, DARK);
        // светильники по стенам
        for (int z = start; z <= end; z += 6) {
            set(-3, 3, z, Blocks.SEA_LANTERN.getDefaultState());
            set(3, 3, z, Blocks.SEA_LANTERN.getDefaultState());
        }
        // препятствия
        int z = 14;
        Type[] all = Type.values();
        while (z < length - 6) {
            Obstacle o = new Obstacle();
            o.type = all[rnd.nextInt(all.length)];
            o.z = z;
            makeObstacle(o);
            obstacles.add(o);
            z += 9 + rnd.nextInt(5);
        }
        // финиш
        fill(-2, 0, length, 2, 0, length + 6, Blocks.GOLD_BLOCK.getDefaultState());
        finishZ = length;
        // ложа для выбывших — над коридором у старта
        fill(-2, 9, -4, 2, 9, 4, Blocks.GLASS.getDefaultState());
        bounds = new int[]{origin.getX() - 6, origin.getY() - 2, origin.getZ() + start - 2,
                origin.getX() + 6, origin.getY() + 11, origin.getZ() + end + 4};
    }

    private void makeObstacle(Obstacle o) {
        int d = difficulty;
        switch (o.type) {
            case GATE: {
                // стена с проёмом 2×2, проём закрывается железом
                fill(-2, 1, o.z, 2, 4, o.z, WALL);
                o.x = rnd.nextInt(4) - 2;
                for (int x = o.x; x <= o.x + 1; x++) {
                    for (int y = 1; y <= 2; y++) {
                        o.blocks.add(rel(x, y, o.z));
                        set(x, y, o.z, air());
                    }
                }
                o.period = Math.max(30, 80 - d * 8);
                o.phase = rnd.nextInt(o.period);
                return;
            }
            case CRUSHER: {
                for (int x = -2; x <= 2; x++) {
                    for (int dz = 0; dz < 2; dz++) {
                        for (int y = 1; y <= 4; y++) {
                            o.blocks.add(rel(x, y, o.z + dz));
                        }
                    }
                }
                o.period = Math.max(26, 60 - d * 6);
                o.phase = rnd.nextInt(o.period);
                return;
            }
            case LASER: {
                for (int x = -2; x <= 2; x++) {
                    o.floor.add(rel(x, 0, o.z));
                    for (int y = 1; y <= 3; y++) {
                        o.blocks.add(rel(x, y, o.z));
                    }
                }
                o.period = Math.max(24, 56 - d * 6);
                o.phase = rnd.nextInt(o.period);
                return;
            }
            case PLATE: {
                // глухая дверь, рядом плита: пока на ней кто-то стоит (и ещё 3 с) — дверь открыта
                fill(-2, 1, o.z, 2, 4, o.z, WALL);
                for (int x = -1; x <= 0; x++) {
                    for (int y = 1; y <= 2; y++) {
                        o.blocks.add(rel(x, y, o.z));
                        set(x, y, o.z, Blocks.IRON_BLOCK.getDefaultState());
                    }
                }
                int side = rnd.nextBoolean() ? 2 : -2;
                o.plate = rel(side, 1, o.z - 3);
                set(side, 0, o.z - 3, Blocks.REDSTONE_BLOCK.getDefaultState());
                setAbs(o.plate, Blocks.STONE_PRESSURE_PLATE.getDefaultState());
                o.state = false;
                return;
            }
            default: {
                int len = 2 + (d >= 3 ? 1 : 0);
                for (int dz = 0; dz < len; dz++) {
                    fill(-2, 0, o.z + dz, 2, 0, o.z + dz, air());
                }
            }
        }
    }

    @Override
    protected BlockPos start(UUID player, int index) {
        return rel((index % 5) - 2, 1, (index / 5) % 3);
    }

    @Override
    public void begin() {
        super.begin();
        tell("&8Тьма проснулась… &7Беги к золотому полу!");
    }

    // --- ход -------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        ticks++;
        // тьма ползёт (первые 3 секунды — фора)
        if (ticks > 60) {
            speed += 0.0009D;
            wallZ += speed / 20.0D;
            while (filledZ < (int) Math.floor(wallZ) && filledZ < length + 10) {
                filledZ++;
                fill(-2, 1, filledZ, 2, 4, filledZ, DARK);
                BlockPos c = rel(0, 2, filledZ);
                world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, c.getX() + 0.5D, c.getY(), c.getZ() + 0.5D, 10, 1.5D, 1.0D, 0.2D, 0.01D);
            }
        }
        for (Obstacle o : obstacles) {
            tickObstacle(o);
        }
        for (Game.Result r : results.values()) {
            if (r.place != 0 || r.out) {
                continue;
            }
            EntityPlayerMP p = here(r.id);
            if (p == null) {
                continue;
            }
            keepAlive(p);
            double z = p.posZ - origin.getZ();
            r.score = z;
            if (z >= finishZ) {
                finish(r.id, time(ticks));
                lodge(p);
                continue;
            }
            if (z < wallZ + 0.8D) {
                out(r.id, "догнала тьма на " + Math.max(0, (int) (z * 100 / Math.max(1, finishZ))) + "%");
                lodge(p);
                continue;
            }
            // сердце стучит чаще, когда тьма близко
            double gap = z - wallZ;
            int beat = gap < 4 ? 8 : gap < 8 ? 14 : gap < 14 ? 22 : 0;
            if (beat > 0 && ticks % beat == 0) {
                ModSounds.play(p, ModSounds.HEARTBEAT, (float) Math.min(1.0D, 1.6D - gap / 10.0D), 1.0F);
            }
            if (gap < 6 && ticks % 40 == 0) {
                ModSounds.play(p, ModSounds.CHASER, 0.7F, 0.8F + rnd.nextFloat() * 0.3F);
            }
            if (p.posY < origin.getY() - 3) {
                stun(p, "Провалился");
                continue;
            }
            BlockPos feet = new BlockPos(p.posX, p.posY, p.posZ);
            for (Obstacle o : obstacles) {
                if ((o.type == Type.CRUSHER || o.type == Type.LASER) && o.state
                        && (o.blocks.contains(feet) || o.blocks.contains(feet.up()))) {
                    stun(p, o.type == Type.CRUSHER ? "Пресс!" : "Лазер!");
                    break;
                }
            }
        }
        if (ticks % 10 == 0) {
            for (EntityPlayerMP p : active()) {
                double gap = p.posZ - origin.getZ() - wallZ;
                ChatUtil.actionBar(p, (gap < 6 ? "&4" : gap < 12 ? "&c" : "&7") + "Тьма в " + (int) Math.max(0, gap)
                        + " бл. &8· &7до финиша " + (int) Math.max(0, finishZ - (p.posZ - origin.getZ())));
            }
        }
    }

    private void tickObstacle(Obstacle o) {
        switch (o.type) {
            case GATE: {
                int t = (ticks + o.phase) % o.period;
                int open = Math.max(14, 40 - difficulty * 5);
                boolean closed = t >= open;
                if (closed != o.state) {
                    o.state = closed;
                    for (BlockPos b : o.blocks) {
                        setAbs(b, closed ? Blocks.IRON_BLOCK.getDefaultState() : air());
                    }
                }
                return;
            }
            case CRUSHER: {
                int t = (ticks + o.phase) % o.period;
                boolean down = t < 12;
                if (down != o.state) {
                    o.state = down;
                    for (BlockPos b : o.blocks) {
                        setAbs(b, down ? Blocks.OBSIDIAN.getDefaultState() : air());
                    }
                    if (down) {
                        BlockPos b = o.blocks.get(0);
                        ModSounds.playAt(world, b.getX(), b.getY(), b.getZ(), ModSounds.TRAP, 0.5F, 0.6F);
                    }
                }
                return;
            }
            case LASER: {
                int t = (ticks + o.phase) % o.period;
                boolean on = t < o.period / 2;
                if (on != o.state) {
                    o.state = on;
                    IBlockState glass = Blocks.STAINED_GLASS_PANE.getDefaultState().withProperty(net.minecraft.block.BlockStainedGlassPane.COLOR, EnumDyeColor.RED);
                    for (BlockPos b : o.blocks) {
                        setAbs(b, on ? glass : air());
                    }
                    for (BlockPos b : o.floor) {
                        setAbs(b, on ? Blocks.MAGMA.getDefaultState() : FLOOR);
                    }
                }
                return;
            }
            case PLATE: {
                boolean pressed = false;
                for (EntityPlayerMP p : active()) {
                    if (new BlockPos(p.posX, p.posY, p.posZ).equals(o.plate)) {
                        pressed = true;
                    }
                }
                if (pressed) {
                    o.openUntil = ticks + 60;
                }
                boolean open = ticks < o.openUntil;
                if (open != o.state) {
                    o.state = open;
                    for (BlockPos b : o.blocks) {
                        setAbs(b, open ? air() : Blocks.IRON_BLOCK.getDefaultState());
                    }
                    BlockPos b = o.blocks.get(0);
                    ModSounds.playAt(world, b.getX(), b.getY(), b.getZ(), ModSounds.VALVE, 0.7F, open ? 1.2F : 0.8F);
                }
                return;
            }
            default:
        }
    }

    /** Ошибка — отбросило назад, ближе к тьме. */
    private void stun(EntityPlayerMP p, String why) {
        double z = Math.max(wallZ + 2.0D, p.posZ - origin.getZ() - 4.0D);
        p.connection.setPlayerLocation(origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + z, 0.0F, 0.0F);
        p.motionX = 0;
        p.motionY = 0;
        p.motionZ = 0;
        p.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 30, 1, true, false));
        ChatUtil.actionBar(p, "&c" + why + " &7— отбросило назад");
        ModSounds.play(p, ModSounds.TRAP, 0.9F, 0.7F);
        falls.merge(p.getUniqueID(), 1, Integer::sum);
    }

    /** В ложу над коридором — смотреть за остальными. */
    private void lodge(EntityPlayerMP p) {
        p.connection.setPlayerLocation(origin.getX() + 0.5D, origin.getY() + 10.0D, origin.getZ() + 0.5D, 0.0F, 30.0F);
    }

    @Override
    public String status() {
        return "идёт " + time(ticks) + " · тьма на " + Math.max(0, (int) (wallZ * 100 / Math.max(1, finishZ))) + "% коридора";
    }
}
