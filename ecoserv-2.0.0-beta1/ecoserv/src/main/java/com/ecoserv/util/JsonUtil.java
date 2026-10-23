package com.ecoserv.util;

import com.ecoserv.EcoServ;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public final class JsonUtil {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonUtil() {
    }

    /** Файл или, если запись прервалась до подмены, его .tmp. */
    private static File existing(File file) {
        if (file == null) {
            return null;
        }
        if (file.exists()) {
            return file;
        }
        File tmp = new File(file.getAbsolutePath() + ".tmp");
        return tmp.isFile() ? tmp : null;
    }

    public static <T> T read(File original, Class<T> type, T fallback) {
        File file = existing(original);
        if (file == null) {
            return fallback;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            T value = GSON.fromJson(reader, type);
            return value == null ? fallback : value;
        } catch (Exception e) {
            EcoServ.LOG.error("Не удалось прочитать {}: {}", file, e.toString());
            backupBroken(file);
            return fallback;
        } finally {
            close(reader);
        }
    }

    /** Чтение с generic-типом (Map, List и т.п.) через TypeToken. */
    @SuppressWarnings("unchecked")
    public static <T> T readType(File original, java.lang.reflect.Type type, T fallback) {
        File file = existing(original);
        if (file == null) {
            return fallback;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            Object value = GSON.fromJson(reader, type);
            return value == null ? fallback : (T) value;
        } catch (Exception e) {
            EcoServ.LOG.error("Не удалось прочитать {}: {}", file, e.toString());
            backupBroken(file);
            return fallback;
        } finally {
            close(reader);
        }
    }

    public static JsonElement readElement(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            return new JsonParser().parse(reader);
        } catch (Exception e) {
            EcoServ.LOG.error("Не удалось прочитать {}: {}", file, e.toString());
            return null;
        } finally {
            close(reader);
        }
    }

    /** Атомарная запись: сначала во временный файл, потом подмена. */
    public static boolean write(File file, Object value) {
        if (file == null) {
            return false;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            EcoServ.LOG.error("Не удалось создать папку {}", parent);
            return false;
        }
        File tmp = new File(file.getAbsolutePath() + ".tmp");
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8);
            GSON.toJson(value, writer);
            writer.flush();
            close(writer);
            writer = null;
            try {
                // подмена одним действием: старый файл не пропадает ни на миг
                java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                return true;
            } catch (Exception atomicFailed) {
                // файловая система без атомарной подмены — по-старому
            }
            if (file.exists() && !file.delete()) {
                EcoServ.LOG.warn("Не удалось удалить старый файл {}", file);
            }
            if (!tmp.renameTo(file)) {
                EcoServ.LOG.error("Не удалось переименовать {} -> {}", tmp, file);
                return false;
            }
            return true;
        } catch (Exception e) {
            EcoServ.LOG.error("Не удалось записать {}: {}", file, e.toString());
            return false;
        } finally {
            close(writer);
        }
    }

    private static void backupBroken(File file) {
        try {
            File broken = new File(file.getAbsolutePath() + ".broken-" + System.currentTimeMillis());
            if (file.renameTo(broken)) {
                EcoServ.LOG.error("Повреждённый файл сохранён как {}", broken.getName());
            }
        } catch (Exception ignored) {
            // ничего не делаем
        }
    }

    private static void close(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
                // игнорируем
            }
        }
    }
}
