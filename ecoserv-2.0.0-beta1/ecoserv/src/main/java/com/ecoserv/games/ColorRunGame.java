package com.ecoserv.games;

import com.ecoserv.init.ModSounds;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * «Цветной пол» (2.0): пол из цветных квадратов. Называется цвет — успей встать на него, потом все остальные
 * квадраты исчезают. С каждым раундом времени меньше, а цветов больше. Не успел — упал.
 */
public class ColorRunGame extends ArenaGame {

    public static final class Kind extends GameKind {
        public Kind() {
            super("colors", "&dЦветной пол", "Называется цвет — беги на него, остальной пол исчезнет. Раунды всё быстрее.",
                    "minecraft:wool@10", true);
            param("rounds", "Раундов", "Сколько раундов (выжившие после последнего — победители)", 15, 3, 50);
            param("cells", "Квадратов в ряд", "Размер поля в квадратах 3×3", 8, 4, 14);
            param("start", "Время в 1 раунде (сек)", "Потом уменьшается", 5, 2, 10);
        }

        @Override
        public Game create(GameManager.Session session) {
            return new ColorRunGame(session);
        }

        @Override
        public GamePreset defaults() {
            GamePreset p = super.defaults();
            p.maxPlayers = 16;
            return p;
        }
    }

    private static final int CELL = 3;
    private final Random rnd = new Random();
    private final int rounds;
    private final int cells;
    private final int startTicks;
    private int[][] grid;
    private int round;
    /** 0 — показ цвета, 1 — пол исчез, 2 — пауза. */
    private int phase = 2;
    private int phaseEnd = 60;
    private int color;
    private int colorsInPlay = 3;
    private int startPlayers;

    public ColorRunGame(GameManager.Session session) {
        super(session);
        rounds = param("rounds", 15);
        cells = param("cells", 8);
        startTicks = param("start", 5) * 20;
    }

    private int half() {
        return cells * CELL / 2;
    }

    @Override
    protected void build() {
        grid = new int[cells][cells];
        paint(false);
        int h = half();
        net.minecraft.block.state.IBlockState glass = Blocks.GLASS.getDefaultState();
        fill(-h - 1, -1, -h - 1, h + 1, 5, -h - 1, glass);
        fill(-h - 1, -1, h + 1, h + 1, 5, h + 1, glass);
        fill(-h - 1, -1, -h - 1, -h - 1, 5, h + 1, glass);
        fill(h + 1, -1, -h - 1, h + 1, 5, h + 1, glass);
        fill(-3, 12, -3, 3, 12, 3, glass);
        bounds = new int[]{origin.getX() - h - 3, origin.getY() - 8, origin.getZ() - h - 3,
                origin.getX() + h + 3, origin.getY() + 14, origin.getZ() + h + 3};
    }

    /** Новый узор (only — оставить только квадраты цвета color). */
    private void paint(boolean only) {
        int h = half();
        if (!only) {
            for (int cx = 0; cx < cells; cx++) {
                for (int cz = 0; cz < cells; cz++) {
                    grid[cx][cz] = rnd.nextInt(colorsInPlay);
                }
            }
            // хотя бы один квадрат нужного цвета на каждые 8
            int need = Math.max(1, cells * cells / 8);
            for (int i = 0; i < need; i++) {
                grid[rnd.nextInt(cells)][rnd.nextInt(cells)] = color;
            }
        }
        for (int cx = 0; cx < cells; cx++) {
            for (int cz = 0; cz < cells; cz++) {
                boolean keep = !only || grid[cx][cz] == color;
                int x0 = -h + cx * CELL;
                int z0 = -h + cz * CELL;
                fill(x0, 0, z0, x0 + CELL - 1, 0, z0 + CELL - 1, keep ? GameColors.concrete(grid[cx][cz]) : air());
            }
        }
    }

    @Override
    protected BlockPos start(UUID player, int index) {
        return rel((index % 4) * 2 - 3, 1, (index / 4) * 2 - 3);
    }

    @Override
    public void begin() {
        super.begin();
        startPlayers = active().size();
        tell("&dСмотри на цвет и беги на него!");
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks >= phaseEnd) {
            nextPhase();
        }
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
            r.score = round;
            if (p.posY < origin.getY() - 5) {
                out(r.id, "раунд " + round);
                p.connection.setPlayerLocation(origin.getX() + 0.5D, origin.getY() + 13.0D, origin.getZ() + 0.5D, 0.0F, 60.0F);
                continue;
            }
            alive.add(r);
        }
        if (startPlayers > 1 && alive.size() == 1) {
            finish(alive.get(0).id, "раунд " + round);
        }
        if (phase == 0 && ticks % 5 == 0) {
            int left = Math.max(0, phaseEnd - ticks);
            bar(GameColors.title(color) + " &f— на этот цвет! &7" + (left / 20 + 1) + " с");
        }
    }

    private void nextPhase() {
        if (phase == 2) {
            // новый раунд
            round++;
            if (round > rounds) {
                for (Game.Result r : results.values()) {
                    if (r.place == 0 && !r.out) {
                        finish(r.id, "прошёл все раунды");
                    }
                }
                end();
                return;
            }
            colorsInPlay = Math.min(GameColors.META.length, 3 + round / 2);
            color = rnd.nextInt(colorsInPlay);
            paint(false);
            phase = 0;
            int t = Math.max(30, startTicks - (round - 1) * 6);
            phaseEnd = ticks + t;
            for (EntityPlayerMP p : active()) {
                Game.title(p, GameColors.title(color), "&7раунд " + round + " из " + rounds);
                // цвет в руке — чтобы видеть без заголовка
                p.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Blocks.WOOL, 1, GameColors.META[color]));
                ModSounds.play(p, ModSounds.GAME_COUNTDOWN, 0.8F, 1.0F + round * 0.03F);
            }
        } else if (phase == 0) {
            paint(true);
            phase = 1;
            phaseEnd = ticks + 50;
            for (EntityPlayerMP p : active()) {
                ModSounds.play(p, ModSounds.TRAP, 0.7F, 1.3F);
            }
        } else {
            phase = 2;
            phaseEnd = ticks + 30;
        }
    }

    @Override
    public void cleanup() {
        for (EntityPlayerMP p : online()) {
            p.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        super.cleanup();
    }

    @Override
    public String status() {
        return "раунд " + round + " из " + rounds + " · на поле " + active().size();
    }
}
