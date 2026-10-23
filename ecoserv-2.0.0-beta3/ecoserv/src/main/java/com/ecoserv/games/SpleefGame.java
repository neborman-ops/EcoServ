package com.ecoserv.games;

import com.ecoserv.init.ModSounds;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * «Осыпающийся пол» (2.0): несколько этажей из цветных блоков. Блок под ногами исчезает через мгновение —
 * нельзя стоять на месте. Упал ниже последнего этажа — выбыл. Последний оставшийся побеждает
 * (в одиночку — продержаться до конца времени).
 */
public class SpleefGame extends ArenaGame {

    public static final class Kind extends GameKind {
        public Kind() {
            super("spleef", "&bОсыпающийся пол", "Пол исчезает под ногами — беги и не падай. Этажи один под другим, "
                    + "последний оставшийся побеждает.", "minecraft:snow", true);
            param("layers", "Этажей", "Сколько уровней пола", 3, 1, 5);
            param("size", "Размер", "Сторона площадки, блоков", 24, 10, 48);
            param("delay", "Задержка (тики)", "Через сколько исчезает блок под ногами", 8, 3, 30);
            param("time", "Время (сек)", "Сколько длится игра", 180, 30, 900);
        }

        @Override
        public Game create(GameManager.Session session) {
            return new SpleefGame(session);
        }

        @Override
        public GamePreset defaults() {
            GamePreset p = super.defaults();
            p.maxPlayers = 16;
            return p;
        }
    }

    private static final int GAP = 7;
    private final int layers;
    private final int size;
    private final int delay;
    private final int limit;
    /** Блок -> тик исчезновения. */
    private final Map<BlockPos, Integer> doomed = new HashMap<BlockPos, Integer>();
    private int startPlayers;

    public SpleefGame(GameManager.Session session) {
        super(session);
        layers = param("layers", 3);
        size = param("size", 24);
        delay = param("delay", 8);
        limit = param("time", 180) * 20;
    }

    @Override
    protected void build() {
        int h = size / 2;
        for (int l = 0; l < layers; l++) {
            IBlockState st = GameColors.concrete(l * 3 + 4);
            fill(-h, -l * GAP, -h, h, -l * GAP, h, st);
        }
        // стеклянные стены, чтобы не убежать за край
        IBlockState glass = Blocks.GLASS.getDefaultState();
        int bottom = -(layers - 1) * GAP;
        fill(-h - 1, bottom, -h - 1, h + 1, 4, -h - 1, glass);
        fill(-h - 1, bottom, h + 1, h + 1, 4, h + 1, glass);
        fill(-h - 1, bottom, -h - 1, -h - 1, 4, h + 1, glass);
        fill(h + 1, bottom, -h - 1, h + 1, 4, h + 1, glass);
        // ложа над ареной
        fill(-3, 12, -3, 3, 12, 3, glass);
        bounds = new int[]{origin.getX() - h - 3, origin.getY() + bottom - 4, origin.getZ() - h - 3,
                origin.getX() + h + 3, origin.getY() + 14, origin.getZ() + h + 3};
    }

    @Override
    protected BlockPos start(UUID player, int index) {
        double a = index * 2.399963D;
        double r = Math.min(size / 2 - 2, 2 + index * 1.2D);
        return rel((int) Math.round(Math.cos(a) * r), 1, (int) Math.round(Math.sin(a) * r));
    }

    @Override
    public void begin() {
        super.begin();
        startPlayers = active().size();
        tell("&bПол исчезает под ногами — не стой на месте!");
    }

    @Override
    public void tick() {
        ticks++;
        // исчезающие блоки
        Iterator<Map.Entry<BlockPos, Integer>> it = doomed.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> e = it.next();
            if (ticks >= e.getValue()) {
                BlockPos b = e.getKey();
                setAbs(b, air());
                world.spawnParticle(EnumParticleTypes.CLOUD, b.getX() + 0.5D, b.getY() + 0.5D, b.getZ() + 0.5D, 2, 0.2D, 0.1D, 0.2D, 0.01D);
                it.remove();
            }
        }
        int bottomY = origin.getY() - (layers - 1) * GAP - 3;
        List<Game.Result> alive = new ArrayList<Game.Result>();
        for (Game.Result r : results.values()) {
            if (r.place != 0 || r.out) {
                continue;
            }
            EntityPlayerMP p = here(r.id);
            if (p == null) {
                continue;
            }
            keepAlive(p);
            r.score = ticks;
            if (p.posY < bottomY) {
                out(r.id, "упал на " + time(ticks));
                lodge(p);
                continue;
            }
            alive.add(r);
            if (ticks > 40 && p.onGround) {
                // все блоки под «ступнями» (хитбокс 0.6)
                for (double dx = -0.3D; dx <= 0.3D; dx += 0.6D) {
                    for (double dz = -0.3D; dz <= 0.3D; dz += 0.6D) {
                        BlockPos b = new BlockPos(p.posX + dx, p.posY - 0.5D, p.posZ + dz);
                        if (!doomed.containsKey(b) && world.getBlockState(b).getBlock() == Blocks.CONCRETE) {
                            doomed.put(b, ticks + delay);
                        }
                    }
                }
            }
        }
        if (startPlayers > 1 && alive.size() == 1) {
            finish(alive.get(0).id, "последний на полу");
        } else if (ticks >= limit) {
            for (Game.Result r : alive) {
                finish(r.id, "продержался " + time(ticks));
            }
            end();
        }
        if (ticks % 20 == 0) {
            bar("&bОсталось " + alive.size() + " &8· &7" + time(Math.max(0, limit - ticks)));
            if (ticks % 100 == 0 && ticks > 40) {
                for (EntityPlayerMP p : active()) {
                    ModSounds.play(p, ModSounds.HEARTBEAT, 0.3F, 1.2F);
                }
            }
        }
    }

    private void lodge(EntityPlayerMP p) {
        p.connection.setPlayerLocation(origin.getX() + 0.5D, origin.getY() + 13.0D, origin.getZ() + 0.5D, 0.0F, 60.0F);
    }

    @Override
    public String status() {
        return "идёт " + time(ticks) + " · на полу " + active().size();
    }
}
