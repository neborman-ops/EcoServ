package com.ecoserv.util;

import com.ecoserv.EcoServ;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Чтение и надёжная запись сжатых NBT-файлов (через временный файл, как делает сам Minecraft). */
public final class NbtFiles {

    private NbtFiles() {
    }

    /** Прочитать файл; null — файла нет или он повреждён (повреждённый откладывается в .broken). */
    @Nullable
    public static NBTTagCompound read(File file) {
        if (file == null || !file.isFile()) {
            File tmp = file == null ? null : new File(file.getPath() + ".tmp");
            // запись прервалась после создания .tmp, но до переименования — берём его
            if (tmp != null && tmp.isFile()) {
                return readRaw(tmp);
            }
            return null;
        }
        return readRaw(file);
    }

    @Nullable
    private static NBTTagCompound readRaw(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return CompressedStreamTools.readCompressed(in);
        } catch (Exception e) {
            EcoServ.LOG.error("Файл {} повреждён: {}", file, e.toString());
            File broken = new File(file.getPath() + ".broken-" + System.currentTimeMillis());
            if (!file.renameTo(broken)) {
                EcoServ.LOG.error("Не удалось отложить повреждённый файл {}", file);
            }
            return null;
        }
    }

    /** Записать через .tmp и переименование. Возвращает true, если файл на месте. */
    public static boolean write(File file, NBTTagCompound root) {
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            EcoServ.LOG.error("Не удалось создать папку {}", dir);
            return false;
        }
        File tmp = new File(file.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            CompressedStreamTools.writeCompressed(root, out);
        } catch (Exception e) {
            EcoServ.LOG.error("Не удалось записать {}", tmp, e);
            return false;
        }
        if (file.exists() && !file.delete()) {
            EcoServ.LOG.error("Не удалось заменить {}", file);
            return false;
        }
        if (!tmp.renameTo(file)) {
            EcoServ.LOG.error("Не удалось переименовать {} в {}", tmp, file);
            return false;
        }
        return true;
    }
}
