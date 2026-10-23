package com.ecoserv.client;

import com.ecoserv.EcoServ;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketUpload;
import com.ecoserv.network.packet.PacketUploadReply;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Отправка своего мира на сервер (п. 3, «загрузить свою»): level.dat и файлы регионов кусками по 30 КБ
 * в отдельном потоке. Сервер подтверждает каждые 16 кусков; больше 64 неподтверждённых не держим.
 */
@SideOnly(Side.CLIENT)
public final class UploadSender implements Runnable {

    private static final int WINDOW = 64;
    private static final long WAIT_MS = 60_000L;

    private static volatile UploadSender current;

    private final File world;
    public final String name;
    private final List<File> files = new ArrayList<File>();
    public final long total;
    public volatile long sent;
    public volatile String status = "";
    public volatile boolean finished;
    public volatile boolean failed;
    private volatile boolean cancelled;
    private volatile boolean ready;
    private volatile int acked;
    private final Object lock = new Object();

    private UploadSender(File world, String name) {
        this.world = world;
        this.name = name;
        File level = new File(world, "level.dat");
        if (level.isFile()) {
            files.add(level);
        }
        File[] regions = new File(world, "region").listFiles();
        if (regions != null) {
            Arrays.sort(regions);
            for (File f : regions) {
                if (f.isFile() && f.length() > 0 && f.getName().matches("r\\.-?\\d+\\.-?\\d+\\.mca")) {
                    files.add(f);
                }
            }
        }
        long sum = 0L;
        for (File f : files) {
            sum += f.length();
        }
        total = sum;
    }

    public static UploadSender current() {
        return current;
    }

    public static boolean busy() {
        UploadSender s = current;
        return s != null && !s.finished;
    }

    /** Начать загрузку мира (папка одиночного сохранения). */
    public static UploadSender start(File world, String name) {
        UploadSender s = new UploadSender(world, name);
        current = s;
        Thread t = new Thread(s, "EcoServ world upload");
        t.setDaemon(true);
        t.start();
        return s;
    }

    public static void cancelCurrent() {
        UploadSender s = current;
        if (s != null && !s.finished) {
            s.cancelled = true;
            synchronized (s.lock) {
                s.lock.notifyAll();
            }
        }
    }

    /** Ответ сервера (из сетевого потока). */
    public static void onReply(PacketUploadReply reply) {
        UploadSender s = current;
        if (s == null) {
            return;
        }
        synchronized (s.lock) {
            switch (reply.type) {
                case PacketUploadReply.READY:
                    s.ready = true;
                    break;
                case PacketUploadReply.ACK:
                    s.acked = Math.max(s.acked, reply.seq);
                    break;
                case PacketUploadReply.DONE:
                    s.status = Lang.get("ecoserv.upload.done") + " " + reply.message;
                    s.finished = true;
                    break;
                case PacketUploadReply.ERROR:
                    s.status = reply.message;
                    s.failed = true;
                    s.finished = true;
                    break;
                default:
            }
            s.lock.notifyAll();
        }
    }

    private static boolean send(IMessage msg) {
        NetHandlerPlayClient conn = Minecraft.getMinecraft().getConnection();
        if (conn == null) {
            return false;
        }
        // собранный вручную пакет можно отправлять из этого потока (общий канал FML — только из главного)
        conn.sendPacket(PacketHandler.raw(msg, PacketHandler.ID_UPLOAD));
        return true;
    }

    private void fail(String message) {
        status = message;
        failed = true;
        finished = true;
    }

    @Override
    public void run() {
        try {
            if (files.isEmpty() || total <= 0) {
                fail(Lang.get("ecoserv.upload.empty"));
                return;
            }
            status = Lang.get("ecoserv.upload.connecting");
            if (!send(PacketUpload.start(name, total, files.size()))) {
                fail(Lang.get("ecoserv.upload.no_connection"));
                return;
            }
            if (!await(() -> ready)) {
                if (!failed && !cancelled) {
                    fail(Lang.get("ecoserv.upload.no_answer"));
                }
                return;
            }
            status = Lang.get("ecoserv.upload.sending");
            int seq = 0;
            byte[] buf = new byte[PacketUpload.CHUNK];
            for (File f : files) {
                String rel = f.getName().equals("level.dat") ? "level.dat" : "region/" + f.getName();
                try (InputStream in = new FileInputStream(f)) {
                    long offset = 0L;
                    int n;
                    while ((n = readChunk(in, buf)) > 0) {
                        final int s = seq;
                        if (!await(() -> s - acked < WINDOW)) {
                            if (!failed && !cancelled) {
                                fail(Lang.get("ecoserv.upload.no_answer"));
                            }
                            return;
                        }
                        if (!send(PacketUpload.data(seq++, rel, offset, Arrays.copyOf(buf, n)))) {
                            fail(Lang.get("ecoserv.upload.no_connection"));
                            return;
                        }
                        offset += n;
                        sent += n;
                    }
                }
            }
            send(PacketUpload.end(seq));
            status = Lang.get("ecoserv.upload.finishing");
            if (!await(() -> finished) && !finished && !cancelled) {
                fail(Lang.get("ecoserv.upload.no_answer"));
            }
        } catch (IOException e) {
            fail(e.getMessage());
            EcoServ.LOG.warn("Загрузка мира прервана", e);
        } finally {
            if (cancelled) {
                send(PacketUpload.cancel());
                fail(Lang.get("ecoserv.upload.cancelled"));
            }
        }
    }

    private interface Condition {
        boolean ok();
    }

    /** Ждать условия; false — отмена, ошибка или тишина больше минуты. */
    private boolean await(Condition c) {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        synchronized (lock) {
            while (!c.ok()) {
                if (cancelled || failed) {
                    return false;
                }
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    return false;
                }
                try {
                    lock.wait(Math.min(left, 500L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return !cancelled;
    }

    private static int readChunk(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) {
                break;
            }
            total += n;
        }
        return total;
    }

    public File world() {
        return world;
    }
}
