package com.ecoserv.util;

import com.ecoserv.EcoServ;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.DimensionManager;

import java.io.File;

/**
 * Папка данных мода внутри мира: &lt;world&gt;/ecoserv/
 * Всё, что относится к конкретному миру (балансы, статистика, логи), лежит здесь,
 * поэтому переносится вместе с миром и попадает в бэкап.
 */
public final class DataStore {

    private static File worldDir;

    private DataStore() {
    }

    public static void init(MinecraftServer server) {
        File root = null;
        try {
            root = DimensionManager.getCurrentSaveRootDirectory();
        } catch (Throwable t) {
            EcoServ.LOG.warn("DimensionManager недоступен: {}", t.toString());
        }
        if (root == null && server != null) {
            root = server.getFile(server.getFolderName());
        }
        if (root == null) {
            root = new File(".");
        }
        worldDir = new File(root, "ecoserv");
        if (!worldDir.exists() && !worldDir.mkdirs()) {
            EcoServ.LOG.error("Не удалось создать папку данных {}", worldDir);
        }
    }

    public static File dir() {
        if (worldDir == null) {
            init(PlayerUtil.server());
        }
        return worldDir;
    }

    public static File file(String name) {
        return new File(dir(), name);
    }

    /** Корень папки мира (для бэкапа). */
    public static File worldRoot() {
        File d = dir();
        return d == null ? null : d.getParentFile();
    }

    public static void shutdown() {
        worldDir = null;
    }
}
