package com.ecoserv.games;

import com.ecoserv.init.ModSounds;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * «Лабиринт» (2.0): каждый раз новый лабиринт (рекурсивный обход с возвратом). Найди выход — золотой пол в
 * дальнем углу. В проходах — ворота, которые открываются на пару секунд. На всё — ограниченное время.
 */
public class MazeGame extends ArenaGame {

    public static final class Kind extends GameKind {
        public Kind() {
            super("maze", "&2Лабиринт", "Новый лабиринт каждую игру. Найди золотой выход раньше всех — "
                    + "ворота в проходах открываются ненадолго.", "minecraft:leaves", true);
            param("size", "Размер", "Комнат по стороне", 12, 6, 30);
            param("time", "Время (сек)", "Не нашёл выход — проиграл", 300, 60, 1200);
            param("gates", "Ворот", "Сколько ворот на таймере", 6, 0, 40);
            flag("dark", "Темнота", "Видно только рядом (слепота)", false);
        }

        @Override
        public Game create(GameManager.Session session) {
            return new MazeGame(session);
        }

        @Override
        public GamePreset defaults() {
            GamePreset p = super.defaults();
            p.maxPlayers = 12;
            return p;
        }
    }

    private static final int TILE = 2;
    private static final int HEIGHT = 3;
    private final Random rnd = new Random();
    private final int size;
    private final int limit;
    private final int gateCount;
    private final boolean dark;
    private boolean[][] open;
    private int tiles;
    private final List<int[]> gates = new ArrayList<int[]>();
    private boolean gatesClosed;
    private BlockPos exit;
    /** Последняя точка не в воротах. */
    private final java.util.Map<UUID, double[]> safe = new java.util.HashMap<UUID, double[]>();

    public MazeGame(GameManager.Session session) {
        super(session);
        size = param("size", 12);
        limit = param("time", 300) * 20;
        gateCount = param("gates", 6);
        dark = param("dark", 0) != 0;
    }

    @Override
    protected void build() {
        tiles = size * 2 + 1;
        open = new boolean[tiles][tiles];
        carve();
        IBlockState wall = Blocks.STONEBRICK.getDefaultState();
        IBlockState floor = Blocks.GRASS.getDefaultState();
        IBlockState hedge = Blocks.LEAVES.getDefaultState();
        int w = tiles * TILE;
        fill(0, -1, 0, w - 1, -1, w - 1, floor);
        for (int tx = 0; tx < tiles; tx++) {
            for (int tz = 0; tz < tiles; tz++) {
                if (!open[tx][tz]) {
                    fill(tx * TILE, 0, tz * TILE, tx * TILE + TILE - 1, HEIGHT - 1, tz * TILE + TILE - 1,
                            (tx + tz) % 5 == 0 ? hedge : wall);
                }
            }
        }
        // крыша из стекла — нельзя пройти поверху
        fill(0, HEIGHT, 0, w - 1, HEIGHT, w - 1, Blocks.GLASS.getDefaultState());
        // выход
        int ex = (tiles - 2) * TILE;
        fill(ex, -1, ex, ex + TILE - 1, -1, ex + TILE - 1, Blocks.GOLD_BLOCK.getDefaultState());
        exit = rel(ex, 0, ex);
        // ворота на таймере: проходы между комнатами
        List<int[]> passages = new ArrayList<int[]>();
        for (int tx = 1; tx < tiles - 1; tx++) {
            for (int tz = 1; tz < tiles - 1; tz++) {
                if (open[tx][tz] && (tx % 2 == 0) != (tz % 2 == 0) && tx + tz > 4) {
                    passages.add(new int[]{tx, tz});
                }
            }
        }
        Collections.shuffle(passages, rnd);
        for (int i = 0; i < Math.min(gateCount, passages.size()); i++) {
            gates.add(passages.get(i));
        }
        // ложа
        fill(w / 2 - 3, HEIGHT + 8, w / 2 - 3, w / 2 + 3, HEIGHT + 8, w / 2 + 3, Blocks.GLASS.getDefaultState());
        bounds = new int[]{origin.getX() - 2, origin.getY() - 3, origin.getZ() - 2,
                origin.getX() + w + 2, origin.getY() + HEIGHT + 10, origin.getZ() + w + 2};
    }

    /** Лабиринт: обход в глубину по комнатам (нечётные клетки). */
    private void carve() {
        Deque<int[]> stack = new ArrayDeque<int[]>();
        open[1][1] = true;
        stack.push(new int[]{1, 1});
        int[][] dirs = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        while (!stack.isEmpty()) {
            int[] c = stack.peek();
            List<int[]> next = new ArrayList<int[]>();
            for (int[] d : dirs) {
                int nx = c[0] + d[0];
                int nz = c[1] + d[1];
                if (nx > 0 && nz > 0 && nx < tiles - 1 && nz < tiles - 1 && !open[nx][nz]) {
                    next.add(new int[]{nx, nz, d[0] / 2, d[1] / 2});
                }
            }
            if (next.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] n = next.get(rnd.nextInt(next.size()));
            open[c[0] + n[2]][c[1] + n[3]] = true;
            open[n[0]][n[1]] = true;
            stack.push(new int[]{n[0], n[1]});
        }
        // пара лишних проходов — чтобы было несколько путей
        for (int i = 0; i < size; i++) {
            int x = 1 + rnd.nextInt(tiles - 2);
            int z = 1 + rnd.nextInt(tiles - 2);
            if ((x % 2 == 0) != (z % 2 == 0)) {
                open[x][z] = true;
            }
        }
    }

    @Override
    protected BlockPos start(UUID player, int index) {
        return rel(TILE + (index % 2), 0, TILE + (index / 2) % 2);
    }

    @Override
    public void begin() {
        super.begin();
        tell("&2Найди золотой выход в дальнем углу! &7Время: " + time(limit));
    }

    @Override
    public void tick() {
        ticks++;
        // ворота: 3 с открыты, 4 с закрыты
        boolean closed = (ticks / 20) % 7 >= 3;
        if (closed != gatesClosed) {
            gatesClosed = closed;
            IBlockState st = closed ? Blocks.IRON_BARS.getDefaultState() : air();
            for (int[] g : gates) {
                fill(g[0] * TILE, 0, g[1] * TILE, g[0] * TILE + TILE - 1, HEIGHT - 1, g[1] * TILE + TILE - 1, st);
            }
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
            if (dark && ticks % 60 == 1) {
                p.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 80, 0, true, false));
            }
            double dx = p.posX - exit.getX() - 1;
            double dz = p.posZ - exit.getZ() - 1;
            r.score = -Math.sqrt(dx * dx + dz * dz);
            if (p.posX >= exit.getX() && p.posX < exit.getX() + TILE && p.posZ >= exit.getZ() && p.posZ < exit.getZ() + TILE) {
                finish(r.id, time(ticks));
                p.removePotionEffect(MobEffects.BLINDNESS);
                lodge(p);
                continue;
            }
            // застрял в воротах — назад, где стоял
            boolean inBars = world.getBlockState(new BlockPos(p.posX, p.posY + 0.5D, p.posZ)).getBlock() == Blocks.IRON_BARS;
            if (inBars) {
                double[] back = safe.get(r.id);
                if (back != null) {
                    p.connection.setPlayerLocation(back[0], back[1], back[2], p.rotationYaw, p.rotationPitch);
                } else {
                    respawn(p, "Зажало воротами");
                }
                ModSounds.play(p, ModSounds.TRAP, 0.6F, 1.0F);
            } else if (ticks % 10 == 0 && p.onGround && !isGate(p)) {
                safe.put(r.id, new double[]{p.posX, p.posY, p.posZ});
            }
        }
        if (ticks >= limit) {
            for (Game.Result r : results.values()) {
                if (r.place == 0 && !r.out) {
                    out(r.id, "время вышло");
                }
            }
            end();
        }
        if (ticks % 20 == 0) {
            bar("&2Лабиринт &8· &7осталось " + time(Math.max(0, limit - ticks)) + (gatesClosed ? " &8· &cворота закрыты" : " &8· &aворота открыты"));
        }
    }

    private boolean isGate(EntityPlayerMP p) {
        int tx = (int) Math.floor((p.posX - origin.getX()) / TILE);
        int tz = (int) Math.floor((p.posZ - origin.getZ()) / TILE);
        for (int[] g : gates) {
            if (Math.abs(g[0] - tx) <= 0 && Math.abs(g[1] - tz) <= 0) {
                return true;
            }
        }
        return false;
    }

    private void lodge(EntityPlayerMP p) {
        int w = tiles * TILE;
        p.connection.setPlayerLocation(origin.getX() + w / 2.0D, origin.getY() + HEIGHT + 9.0D, origin.getZ() + w / 2.0D, 0.0F, 70.0F);
    }

    @Override
    public String status() {
        return "идёт " + time(ticks) + " из " + time(limit);
    }
}
