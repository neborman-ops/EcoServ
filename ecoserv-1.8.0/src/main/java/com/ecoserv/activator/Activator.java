package com.ecoserv.activator;

import net.minecraft.util.math.BlockPos;

/** Кнопка или рычаг с названием. Нажатие выполняет условие «Активация» в квестах FTB Quests. */
public class Activator {

    public String name = "";
    public int dim;
    public int x;
    public int y;
    public int z;
    /** Необязательная команда при активации (от имени сервера): %player% — ник, %name% — название. */
    public String command = "";
    public String creator = "";
    public long created;
    public long uses;

    public Activator() {
    }

    public Activator(String name, int dim, BlockPos pos, String creator) {
        this.name = name;
        this.dim = dim;
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.creator = creator == null ? "" : creator;
        this.created = System.currentTimeMillis();
    }

    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }

    public String key() {
        return key(dim, x, y, z);
    }

    public static String key(int dim, int x, int y, int z) {
        return dim + ":" + x + ":" + y + ":" + z;
    }
}
