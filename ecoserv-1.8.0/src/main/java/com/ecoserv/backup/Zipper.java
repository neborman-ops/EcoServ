package com.ecoserv.backup;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Упаковка мира в zip. */
public final class Zipper {

    private static final int BUFFER = 64 * 1024;

    private Zipper() {
    }

    public static long zipWorld(File worldDir, File target, String[] exclude, int level,
                                boolean includeConfig, boolean includeModList) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Не удалось создать папку " + parent);
        }

        ZipOutputStream zip = null;
        try {
            zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target), BUFFER),
                    StandardCharsets.UTF_8);
            zip.setLevel(clamp(level));
            if (clamp(level) == 0) {
                zip.setMethod(ZipOutputStream.DEFLATED);
                zip.setLevel(Deflater.NO_COMPRESSION);
            }

            String rootName = worldDir.getName();
            addDirectory(zip, worldDir, rootName, exclude, target);

            if (includeConfig) {
                File configDir = EcoConfig.getConfigDir() == null ? null : EcoConfig.getConfigDir().getParentFile();
                if (configDir != null && configDir.isDirectory()) {
                    addDirectory(zip, configDir, "config", new String[0], target);
                }
            }

            if (includeModList) {
                writeModList(zip);
            }
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException e) {
                    EcoServ.LOG.error("Ошибка закрытия архива", e);
                }
            }
        }
        return target.length();
    }

    private static int clamp(int level) {
        if (level < 0) {
            return 0;
        }
        return Math.min(level, 9);
    }

    private static void addDirectory(ZipOutputStream zip, File dir, String prefix, String[] exclude, File target)
            throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            String path = prefix + "/" + file.getName();
            if (isExcluded(path, exclude)) {
                continue;
            }
            if (file.isDirectory()) {
                addDirectory(zip, file, path, exclude, target);
            } else {
                if (sameFile(file, target)) {
                    continue;
                }
                addFile(zip, file, path);
            }
        }
    }

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isExcluded(String path, String[] exclude) {
        if (exclude == null) {
            return false;
        }
        String normalized = path.toLowerCase();
        for (int i = 0; i < exclude.length; i++) {
            String e = exclude[i];
            if (e == null || e.isEmpty()) {
                continue;
            }
            String pattern = e.replace('\\', '/').toLowerCase();
            if (normalized.endsWith("/" + pattern) || normalized.contains("/" + pattern + "/")
                    || normalized.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static void addFile(ZipOutputStream zip, File file, String path) {
        InputStream in = null;
        try {
            ZipEntry entry = new ZipEntry(path);
            entry.setTime(file.lastModified());
            zip.putNextEntry(entry);
            in = new BufferedInputStream(new FileInputStream(file), BUFFER);
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = in.read(buffer)) > 0) {
                zip.write(buffer, 0, read);
            }
            zip.closeEntry();
        } catch (IOException e) {
            // файл мог быть удалён или занят — не валим весь бэкап
            EcoServ.LOG.warn("Пропущен файл {}: {}", path, e.toString());
            try {
                zip.closeEntry();
            } catch (IOException ignored) {
                // запись уже закрыта
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // ничего
                }
            }
        }
    }

    private static void writeModList(ZipOutputStream zip) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Список модов на момент бэкапа\r\n");
            sb.append("=============================\r\n");
            List<ModContainer> mods = Loader.instance().getActiveModList();
            for (int i = 0; i < mods.size(); i++) {
                ModContainer mod = mods.get(i);
                sb.append(mod.getModId()).append(" | ").append(mod.getName())
                        .append(" | ").append(mod.getVersion()).append("\r\n");
            }
            ZipEntry entry = new ZipEntry("modlist.txt");
            entry.setTime(System.currentTimeMillis());
            zip.putNextEntry(entry);
            zip.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (Exception e) {
            EcoServ.LOG.warn("Не удалось записать список модов: {}", e.toString());
        }
    }
}
