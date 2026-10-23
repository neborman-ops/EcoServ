package com.ecoserv.subworld;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketUpload;
import com.ecoserv.network.packet.PacketUploadReply;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.FileOps;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Приём мира, который админ загружает из своей игры (одиночное сохранение → сервер).
 * Данные приходят кусками по 30 КБ в сетевом потоке и сразу пишутся в файл; подтверждения — каждые
 * 16 кусков, клиент держит не больше 64 неподтверждённых. Готовый мир появляется в ecoserv_uploads.
 */
public final class UploadReceiver {

    private static final int ACK_EVERY = 16;
    private static final long TIMEOUT_MS = 60_000L;

    private static final class Session {
        final UUID player;
        final String name;
        final long total;
        final File dir;
        long received;
        int nextSeq;
        String currentPath = "";
        long currentOffset;
        OutputStream out;
        volatile long lastActivity = System.currentTimeMillis();
        boolean failed;

        Session(UUID player, String name, long total, File dir) {
            this.player = player;
            this.name = name;
            this.total = total;
            this.dir = dir;
        }

        void closeFile() {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // закрываем в любом случае
                }
                out = null;
            }
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<UUID, Session>();

    private UploadReceiver() {
    }

    private static void reply(EntityPlayerMP player, int type, int seq, String message) {
        if (player.connection != null) {
            player.connection.sendPacket(PacketHandler.raw(new PacketUploadReply(type, seq, message),
                    PacketHandler.ID_UPLOAD_REPLY));
        }
    }

    /** Из сетевого потока. */
    public static void onPacket(final EntityPlayerMP player, final PacketUpload msg) {
        switch (msg.type) {
            case PacketUpload.START:
                // права и имя проверяем в главном потоке
                MinecraftServer server = player.getServerWorld().getMinecraftServer();
                if (server != null) {
                    server.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            start(player, msg.name, msg.total);
                        }
                    });
                }
                return;
            case PacketUpload.DATA:
                data(player, msg);
                return;
            case PacketUpload.END:
                MinecraftServer srv = player.getServerWorld().getMinecraftServer();
                if (srv != null) {
                    srv.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            end(player, msg.seq);
                        }
                    });
                }
                return;
            case PacketUpload.CANCEL:
                abort(player.getUniqueID(), null);
                return;
            default:
        }
    }

    private static void start(EntityPlayerMP player, String rawName, long total) {
        if (!Perms.isAdmin(player)) {
            reply(player, PacketUploadReply.ERROR, 0, "Загружать миры могут только админы.");
            return;
        }
        if (!EcoConfig.subworldsClientUpload) {
            reply(player, PacketUploadReply.ERROR, 0, "Загрузка миров из игры выключена в конфиге (subworlds.client_upload).");
            return;
        }
        if (total <= 0 || total > SubworldUploads.maxBytes()) {
            reply(player, PacketUploadReply.ERROR, 0, "Мир больше " + EcoConfig.subworldsMaxUploadMb + " МБ ("
                    + Fmt.bytes(total) + ").");
            return;
        }
        String name = FileOps.safeName(rawName);
        if (name.isEmpty() || !SubworldUploads.nameFree(name)) {
            reply(player, PacketUploadReply.ERROR, 0, "Имя «" + name + "» уже занято в папке загрузок — переименуй мир.");
            return;
        }
        for (Session s : SESSIONS.values()) {
            if (s.name.equalsIgnoreCase(name)) {
                reply(player, PacketUploadReply.ERROR, 0, "Мир с таким именем уже загружается.");
                return;
            }
        }
        abort(player.getUniqueID(), null);
        // своя папка на каждую загрузку: прошлая, прерванная, может ещё стираться в фоне
        File dir = SubworldUploads.incomingDir(name + "-" + System.nanoTime());
        if (!dir.mkdirs()) {
            reply(player, PacketUploadReply.ERROR, 0, "Сервер не смог создать папку для загрузки.");
            return;
        }
        SESSIONS.put(player.getUniqueID(), new Session(player.getUniqueID(), name, total, dir));
        EcoServ.LOG.info("{} загружает мир «{}» ({})", player.getName(), name, Fmt.bytes(total));
        reply(player, PacketUploadReply.READY, 0, name);
    }

    private static void data(EntityPlayerMP player, PacketUpload msg) {
        Session s = SESSIONS.get(player.getUniqueID());
        if (s == null) {
            return;
        }
        synchronized (s) {
            dataLocked(player, s, msg);
        }
    }

    private static void dataLocked(EntityPlayerMP player, Session s, PacketUpload msg) {
        if (s.failed) {
            return;
        }
        s.lastActivity = System.currentTimeMillis();
        String err = null;
        if (msg.seq != s.nextSeq) {
            err = "потеря данных (ожидался кусок " + s.nextSeq + ", пришёл " + msg.seq + ")";
        } else if (!SubworldUploads.allowedPath(msg.path)) {
            err = "недопустимый файл " + msg.path;
        } else if (s.received + msg.data.length > s.total || s.received + msg.data.length > SubworldUploads.maxBytes()) {
            err = "данных больше, чем объявлено";
        }
        if (err == null) {
            try {
                if (!msg.path.equals(s.currentPath)) {
                    s.closeFile();
                    if (msg.offset != 0) {
                        throw new IOException("файл " + msg.path + " начат не с начала");
                    }
                    File f = new File(s.dir, msg.path.replace('\\', '/'));
                    File parent = f.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("не создать " + parent);
                    }
                    s.out = new FileOutputStream(f);
                    s.currentPath = msg.path;
                    s.currentOffset = 0L;
                }
                if (msg.offset != s.currentOffset) {
                    throw new IOException("разрыв в файле " + msg.path);
                }
                s.out.write(msg.data);
                s.currentOffset += msg.data.length;
                s.received += msg.data.length;
                s.nextSeq++;
            } catch (IOException e) {
                err = e.getMessage();
            }
        }
        if (err != null) {
            s.failed = true;
            reply(player, PacketUploadReply.ERROR, msg.seq, "Загрузка прервана: " + err);
            abort(player.getUniqueID(), null);
            return;
        }
        if (s.nextSeq % ACK_EVERY == 0) {
            reply(player, PacketUploadReply.ACK, s.nextSeq, "");
        }
    }

    private static void end(EntityPlayerMP player, int seq) {
        Session s = SESSIONS.remove(player.getUniqueID());
        if (s == null) {
            return;
        }
        boolean ok;
        synchronized (s) {
            s.closeFile();
            ok = !s.failed && seq == s.nextSeq && s.received == s.total;
            s.failed = true;
        }
        if (!ok) {
            deleteLater(s.dir);
            reply(player, PacketUploadReply.ERROR, seq, "Загрузка не сошлась по размеру — попробуй ещё раз.");
            return;
        }
        File target = new File(SubworldUploads.root(), s.name);
        if (target.exists() || !s.dir.renameTo(target)) {
            deleteLater(s.dir);
            reply(player, PacketUploadReply.ERROR, seq, "Не удалось сохранить мир на сервере.");
            return;
        }
        SubworldUploads.Upload up = SubworldUploads.find(s.name);
        String note = up == null ? "" : up.error == null ? "" : " — но он не подходит: " + up.error;
        EcoServ.LOG.info("Мир «{}» загружен ({})", s.name, Fmt.bytes(s.received));
        reply(player, PacketUploadReply.DONE, seq, s.name);
        ChatUtil.send(player, "&aМир &f" + s.name + " &aзагружен на сервер (" + Fmt.bytes(s.received) + ")" + note
                + (up != null && up.error == null ? "&a. Теперь его можно выбрать при создании подмира." : "&c."));
    }

    /** Прервать загрузку игрока (выход, отмена, ошибка). */
    public static void abort(UUID player, String reason) {
        Session s = SESSIONS.remove(player);
        if (s != null) {
            synchronized (s) {
                s.failed = true;
                s.closeFile();
            }
            deleteLater(s.dir);
            if (reason != null) {
                EcoServ.LOG.info("Загрузка мира «{}» прервана: {}", s.name, reason);
            }
        }
    }

    /** Стереть недогруженное в фоне (там могут быть гигабайты — не в сетевом и не в главном потоке). */
    private static void deleteLater(final File dir) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                FileOps.deleteTree(dir);
            }
        }, "EcoServ upload cleanup");
        t.setDaemon(true);
        t.start();
    }

    public static void onLogout(UUID player) {
        abort(player, "игрок вышел");
    }

    /** Раз в секунду: брошенные загрузки. */
    public static void tick() {
        long now = System.currentTimeMillis();
        for (Session s : SESSIONS.values()) {
            if (now - s.lastActivity > TIMEOUT_MS) {
                abort(s.player, "нет данных больше минуты");
            }
        }
    }

    /** Остановка сервера. */
    public static void shutdown() {
        for (UUID id : SESSIONS.keySet()) {
            abort(id, "остановка сервера");
        }
    }
}
