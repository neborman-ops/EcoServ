package com.ecoserv.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class NetUtil {

    /** Максимальный размер куска данных в одном пакете. */
    public static final int CHUNK = 28_000;

    private NetUtil() {
    }

    public static void writeString(ByteBuf buf, String value) {
        ByteBufUtils.writeUTF8String(buf, value == null ? "" : value);
    }

    public static String readString(ByteBuf buf) {
        return ByteBufUtils.readUTF8String(buf);
    }

    public static void writeBytes(ByteBuf buf, byte[] data) {
        if (data == null) {
            buf.writeInt(0);
            return;
        }
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    public static byte[] readBytes(ByteBuf buf) {
        int length = buf.readInt();
        if (length <= 0) {
            return new byte[0];
        }
        byte[] data = new byte[length];
        buf.readBytes(data);
        return data;
    }

    public static byte[] gzip(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = null;
        try {
            gzip = new GZIPOutputStream(out);
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        } finally {
            close(gzip);
        }
    }

    public static String gunzip(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        GZIPInputStream in = null;
        try {
            in = new GZIPInputStream(new ByteArrayInputStream(data));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        } finally {
            close(in);
        }
    }

    private static void close(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
                // ничего
            }
        }
    }
}
