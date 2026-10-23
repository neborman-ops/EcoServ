package com.ecoserv.games;

import com.ecoserv.init.ModSounds;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * «Царь горы» (2.0): золотая вершина в центре. Стоишь на ней один — копишь очки. Толкай соперников ударом
 * (без урона). Первый, кто наберёт нужные очки, побеждает; по таймеру — у кого больше.
 */
public class KingGame extends ArenaGame implements HitGame {

    public static final class Kind extends GameKind {
        public Kind() {
            super("king", "&6Царь горы", "Стой на золотой вершине один и копи очки. Удар отталкивает соперников.",
                    "minecraft:gold_block", true);
            param("points", "Очков до победы", "Секунд на вершине", 60, 15, 300);
            param("time", "Время (сек)", "", 300, 60, 1200);
            param("push", "Сила толчка", "%", 100, 30, 300);
        }

        @Override
        public Game create(GameManager.Session session) {
            return new KingGame(session);
        }

        @Override
        public GamePreset defaults() {
            GamePreset p = super.defaults();
            p.minPlayers = 2;
            p.maxPlayers = 12;
            return p;
        }
    }

    private final Random rnd = new Random();
    private final int goal;
    private final int limit;
    private final double push;
    private final Map<UUID, Integer> points = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> lastHit = new HashMap<UUID, Integer>();

    public KingGame(GameManager.Session session) {
        super(session);
        goal = param("points", 60);
        limit = param("time", 300) * 20;
        push = param("push", 100) / 100.0D;
    }

    @Override
    protected void build() {
        int r = 14;
        // холм ступенями
        for (int y = 0; y <= 4; y++) {
            int s = r - y * 3;
            fill(-s, y, -s, s, y, s, y == 4 ? Blocks.GOLD_BLOCK.getDefaultState()
                    : y % 2 == 0 ? Blocks.QUARTZ_BLOCK.getDefaultState() : Blocks.SANDSTONE.getDefaultState());
        }
        for (int i = 0; i < 8; i++) {
            set(rnd.nextInt(r * 2) - r, 1, rnd.nextInt(r * 2) - r, Blocks.SLIME_BLOCK.getDefaultState());
        }
        fill(-r - 4, -1, -r - 4, r + 4, -1, r + 4, Blocks.WATER.getDefaultState());
        fill(-3, 16, -3, 3, 16, 3, Blocks.GLASS.getDefaultState());
        bounds = new int[]{origin.getX() - r - 6, origin.getY() - 4, origin.getZ() - r - 6,
                origin.getX() + r + 6, origin.getY() + 18, origin.getZ() + r + 6};
    }

    @Override
    protected BlockPos start(UUID player, int index) {
        double a = index * Math.PI * 2 / 8;
        return rel((int) Math.round(Math.cos(a) * 12), 1, (int) Math.round(Math.sin(a) * 12));
    }

    @Override
    public void begin() {
        super.begin();
        tell("&6Царь горы: &7стой на золоте один, толкай остальных!");
    }

    @Override
    public void onHit(EntityPlayerMP attacker, EntityPlayerMP victim) {
        Integer last = lastHit.get(attacker.getUniqueID());
        if (last != null && ticks - last < 8) {
            return;
        }
        lastHit.put(attacker.getUniqueID(), ticks);
        double dx = victim.posX - attacker.posX;
        double dz = victim.posZ - attacker.posZ;
        double l = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
        victim.motionX += dx / l * 0.9D * push;
        victim.motionZ += dz / l * 0.9D * push;
        victim.motionY = 0.4D;
        victim.velocityChanged = true;
        ModSounds.playAt(world, victim.posX, victim.posY, victim.posZ, ModSounds.UI_CLICK, 0.8F, 0.6F);
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
            if ((p.isInWater() && p.posY < origin.getY() + 0.5D) || p.posY < origin.getY() - 6) {
                respawn(p, "В воду!");
                continue;
            }
            if (under(p).getBlock() == Blocks.GOLD_BLOCK) {
                onTop++;
                king = r.id;
            }
        }
        if (onTop == 1 && ticks % 20 == 0) {
            int pts = points.merge(king, 1, Integer::sum);
            Game.Result kr = results.get(king);
            kr.score = pts;
            EntityPlayerMP kp = here(king);
            if (kp != null) {
                world.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, kp.posX, kp.posY + 2.2D, kp.posZ, 3, 0.3D, 0.2D, 0.3D, 0.0D);
                ModSounds.play(kp, ModSounds.COIN, 0.4F, 1.0F + pts / (float) goal);
            }
            if (pts >= goal) {
                finish(king, pts + " очков");
                for (Game.Result r : results.values()) {
                    if (r.place == 0 && !r.out) {
                        r.out = true;
                        r.note = (points.containsKey(r.id) ? points.get(r.id) : 0) + " очков";
                    }
                }
                end();
                return;
            }
        }
        if (ticks >= limit) {
            UUID best = null;
            int bp = -1;
            for (Map.Entry<UUID, Integer> e : points.entrySet()) {
                if (e.getValue() > bp) {
                    bp = e.getValue();
                    best = e.getKey();
                }
            }
            if (best != null) {
                finish(best, bp + " очков");
            }
            end();
            return;
        }
        if (ticks % 10 == 0) {
            StringBuilder b = new StringBuilder("&6Вершина: ");
            EntityPlayerMP kp = king == null ? null : here(king);
            b.append(onTop == 1 && kp != null ? "&f" + kp.getName() : onTop > 1 ? "&cспорная" : "&7пусто");
            for (EntityPlayerMP p : active()) {
                Integer my = points.get(p.getUniqueID());
                com.ecoserv.util.ChatUtil.actionBar(p, b + " &8· &7твои очки &e" + (my == null ? 0 : my) + "&7/" + goal
                        + " &8· " + time(Math.max(0, limit - ticks)));
            }
        }
    }

    @Override
    public String status() {
        return "идёт " + time(ticks);
    }
}
