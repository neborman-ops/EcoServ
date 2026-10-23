package com.ecoserv.server;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.PlayerUtil;

import java.util.Random;

/** Периодические объявления в чат. */
public final class Announcer {

    private static final Random RANDOM = new Random();
    private static long nextAt;
    private static int index;

    private Announcer() {
    }

    public static void onServerStart() {
        index = 0;
        reschedule();
    }

    private static void reschedule() {
        nextAt = System.currentTimeMillis() + Math.max(1, EcoConfig.announcerIntervalMinutes) * 60_000L;
    }

    public static void tick() {
        if (!EcoConfig.announcerEnabled || EcoConfig.announcerMessages == null
                || EcoConfig.announcerMessages.length == 0) {
            return;
        }
        if (System.currentTimeMillis() < nextAt) {
            return;
        }
        reschedule();
        if (PlayerUtil.allOnline().isEmpty()) {
            return;
        }
        String message;
        if (EcoConfig.announcerRandom) {
            message = EcoConfig.announcerMessages[RANDOM.nextInt(EcoConfig.announcerMessages.length)];
        } else {
            if (index >= EcoConfig.announcerMessages.length) {
                index = 0;
            }
            message = EcoConfig.announcerMessages[index++];
        }
        if (message != null && !message.trim().isEmpty()) {
            ChatUtil.broadcast(message);
        }
    }
}
