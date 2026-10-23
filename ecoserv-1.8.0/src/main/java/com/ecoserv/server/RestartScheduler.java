package com.ecoserv.server;

import com.ecoserv.EcoServ;
import com.ecoserv.backup.BackupManager;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * Автоперезапуск по расписанию с предупреждениями и бэкапом перед выключением.
 * Сервер только корректно выключается — поднять его обратно должен внешний скрипт
 * (в start.sh достаточно обернуть запуск в while true; do java -jar ...; done).
 */
public final class RestartScheduler {

    private static long nextRestartAt;
    private static long manualRestartAt;
    private static final Set<Integer> warned = new HashSet<Integer>();
    private static boolean shuttingDown;

    private RestartScheduler() {
    }

    public static void onServerStart() {
        warned.clear();
        manualRestartAt = 0L;
        shuttingDown = false;
        nextRestartAt = computeNext();
        if (EcoConfig.restartEnabled && nextRestartAt > 0L) {
            EcoServ.LOG.info("Автоперезапуск: следующий в {}", Fmt.timestamp(nextRestartAt));
        }
    }

    /** После правки расписания в редакторе конфига (ручной отсчёт не трогаем). */
    public static void recompute() {
        warned.clear();
        nextRestartAt = computeNext();
    }

    public static long nextRestart() {
        return manualRestartAt > 0L ? manualRestartAt : (EcoConfig.restartEnabled ? nextRestartAt : 0L);
    }

    public static void startManualCountdown(int minutes, @Nullable ICommandSender requester) {
        manualRestartAt = System.currentTimeMillis() + minutes * 60_000L;
        warned.clear();
        ChatUtil.broadcast(String.format(EcoConfig.restartMessage, Fmt.minutes(minutes)));
        if (requester != null) {
            ChatUtil.send(requester, "&aПерезапуск запланирован через " + Fmt.minutes(minutes));
        }
    }

    public static void cancelCountdown(@Nullable ICommandSender requester) {
        if (manualRestartAt <= 0L) {
            if (requester != null) {
                ChatUtil.send(requester, "&eНет запланированного ручного перезапуска.");
            }
            return;
        }
        manualRestartAt = 0L;
        warned.clear();
        ChatUtil.broadcast("&aПерезапуск отменён.");
    }

    public static void tick() {
        if (shuttingDown) {
            return;
        }
        long target = nextRestart();
        if (target <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        long left = target - now;

        if (left <= 0L) {
            performRestart();
            return;
        }

        int minutesLeft = (int) Math.ceil(left / 60_000.0D);
        int[] warnAt = EcoConfig.restartWarnMinutes;
        for (int i = 0; i < warnAt.length; i++) {
            if (warnAt[i] == minutesLeft && !warned.contains(Integer.valueOf(minutesLeft))) {
                warned.add(Integer.valueOf(minutesLeft));
                ChatUtil.broadcast(String.format(EcoConfig.restartMessage, Fmt.minutes(minutesLeft)));
            }
        }
        if (left <= 10_000L && !warned.contains(Integer.valueOf(0))) {
            warned.add(Integer.valueOf(0));
            ChatUtil.broadcast(String.format(EcoConfig.restartMessage, "10 секунд"));
        }
    }

    private static void performRestart() {
        shuttingDown = true;
        manualRestartAt = 0L;
        warned.clear();
        nextRestartAt = computeNext();

        ChatUtil.broadcast("&cСервер перезапускается...");
        ServerServices.saveAll(true);

        if (EcoConfig.restartBackupBefore) {
            EcoServ.LOG.info("Бэкап перед перезапуском...");
            BackupManager.performBlocking("pre-restart");
        }

        MinecraftServer server = ServerServices.server();
        if (server != null) {
            server.initiateShutdown();
        }
    }

    private static long computeNext() {
        if (EcoConfig.restartTimes == null || EcoConfig.restartTimes.length == 0) {
            return 0L;
        }
        long best = Long.MAX_VALUE;
        long now = System.currentTimeMillis();
        for (int i = 0; i < EcoConfig.restartTimes.length; i++) {
            String raw = EcoConfig.restartTimes[i];
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String[] parts = raw.trim().split(":");
            if (parts.length != 2) {
                EcoServ.LOG.warn("Некорректное время перезапуска: {}", raw);
                continue;
            }
            try {
                int hour = Integer.parseInt(parts[0].trim());
                int minute = Integer.parseInt(parts[1].trim());
                Calendar c = Calendar.getInstance();
                c.set(Calendar.HOUR_OF_DAY, hour);
                c.set(Calendar.MINUTE, minute);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                if (c.getTimeInMillis() <= now) {
                    c.add(Calendar.DAY_OF_YEAR, 1);
                }
                if (c.getTimeInMillis() < best) {
                    best = c.getTimeInMillis();
                }
            } catch (NumberFormatException e) {
                EcoServ.LOG.warn("Некорректное время перезапуска: {}", raw);
            }
        }
        return best == Long.MAX_VALUE ? 0L : best;
    }
}
