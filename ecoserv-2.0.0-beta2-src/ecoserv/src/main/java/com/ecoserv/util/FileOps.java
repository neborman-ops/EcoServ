package com.ecoserv.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.CRC32;

/** Файловые операции для подмиров: удаление папок, копирование с контрольной суммой. */
public final class FileOps {

    private FileOps() {
    }

    /** Удалить файл или папку целиком. true — удалено (или не было). */
    public static boolean deleteTree(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        boolean ok = true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) {
                    ok &= deleteTree(c);
                }
            }
        }
        return file.delete() && ok;
    }

    /** Скопировать файл, вернуть CRC32 содержимого. */
    public static long copy(File from, File to) throws IOException {
        File parent = to.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Не удалось создать папку " + parent);
        }
        CRC32 crc = new CRC32();
        byte[] buf = new byte[65536];
        InputStream in = new FileInputStream(from);
        try {
            OutputStream out = new FileOutputStream(to);
            try {
                int n;
                while ((n = in.read(buf)) > 0) {
                    crc.update(buf, 0, n);
                    out.write(buf, 0, n);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
        return crc.getValue();
    }

    public static long crc(File file) throws IOException {
        CRC32 crc = new CRC32();
        byte[] buf = new byte[65536];
        InputStream in = new FileInputStream(file);
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                crc.update(buf, 0, n);
            }
        } finally {
            in.close();
        }
        return crc.getValue();
    }

    /** Безопасное имя файла/папки: латиница, цифры, _ - . (без путей). */
    public static String safeName(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : name.trim().toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append('_');
            }
        }
        String s = sb.toString();
        while (s.startsWith(".")) {
            s = s.substring(1);
        }
        return s.length() > 64 ? s.substring(0, 64) : s;
    }
}
