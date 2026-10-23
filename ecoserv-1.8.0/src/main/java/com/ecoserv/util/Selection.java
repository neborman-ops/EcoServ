package com.ecoserv.util;

import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Выделение области двумя кликами (палочка заполнения, портал, зона-триггер и т.п.).
 * У каждого инструмента своё выделение: ключ = игрок + название инструмента.
 * Первый клик — точка 1, второй — точка 2 (выделение готово), третий начинает заново.
 */
public final class Selection {

    public static final class Sel {
        public final int dim;
        public final BlockPos first;
        @Nullable
        public BlockPos second;
        public long time = System.currentTimeMillis();

        Sel(int dim, BlockPos first) {
            this.dim = dim;
            this.first = first;
        }

        public boolean complete() {
            return second != null;
        }

        public BlockPos min() {
            BlockPos b = second == null ? first : second;
            return new BlockPos(Math.min(first.getX(), b.getX()), Math.min(first.getY(), b.getY()),
                    Math.min(first.getZ(), b.getZ()));
        }

        public BlockPos max() {
            BlockPos b = second == null ? first : second;
            return new BlockPos(Math.max(first.getX(), b.getX()), Math.max(first.getY(), b.getY()),
                    Math.max(first.getZ(), b.getZ()));
        }

        /** Число блоков внутри (включительно). */
        public long volume() {
            BlockPos a = min();
            BlockPos b = max();
            return (long) (b.getX() - a.getX() + 1) * (b.getY() - a.getY() + 1) * (b.getZ() - a.getZ() + 1);
        }

        public String describe() {
            BlockPos a = min();
            BlockPos b = max();
            return a.getX() + " " + a.getY() + " " + a.getZ() + " → " + b.getX() + " " + b.getY() + " " + b.getZ()
                    + " (" + (b.getX() - a.getX() + 1) + "×" + (b.getY() - a.getY() + 1) + "×"
                    + (b.getZ() - a.getZ() + 1) + ")";
        }
    }

    /** Результат клика. */
    public enum Step {
        FIRST, SECOND
    }

    private static final Map<String, Sel> MAP = new HashMap<String, Sel>();
    /** Выделения старше часа выбрасываются. */
    private static final long MAX_AGE = 60L * 60L * 1000L;

    private Selection() {
    }

    private static String key(UUID player, String tool) {
        return player + ":" + tool;
    }

    @Nullable
    public static Sel get(UUID player, String tool) {
        Sel sel = MAP.get(key(player, tool));
        if (sel != null && System.currentTimeMillis() - sel.time > MAX_AGE) {
            MAP.remove(key(player, tool));
            return null;
        }
        return sel;
    }

    /** Готовое выделение (обе точки) или null. */
    @Nullable
    public static Sel complete(UUID player, String tool, int dim) {
        Sel sel = get(player, tool);
        return sel != null && sel.complete() && sel.dim == dim ? sel : null;
    }

    /** Клик по блоку: точка 1 или точка 2. В другом мире выделение начинается заново. */
    public static Step click(UUID player, String tool, int dim, BlockPos pos) {
        Sel sel = get(player, tool);
        if (sel == null || sel.complete() || sel.dim != dim) {
            MAP.put(key(player, tool), new Sel(dim, pos.toImmutable()));
            return Step.FIRST;
        }
        sel.second = pos.toImmutable();
        sel.time = System.currentTimeMillis();
        return Step.SECOND;
    }

    public static void clear(UUID player, String tool) {
        MAP.remove(key(player, tool));
    }

    /** При выходе игрока. */
    public static void clearAll(UUID player) {
        String prefix = player + ":";
        Iterator<String> it = MAP.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) {
                it.remove();
            }
        }
    }
}
