package com.ecoserv.server;

import com.ecoserv.EcoServ;

import java.util.ArrayList;
import java.util.List;

/** Отложенные задачи главного потока сервера (через N тиков). */
public final class ServerTasks {

    private static final class Task {
        final Runnable runnable;
        int ticks;

        Task(Runnable runnable, int ticks) {
            this.runnable = runnable;
            this.ticks = ticks;
        }
    }

    private static final List<Task> TASKS = new ArrayList<Task>();

    private ServerTasks() {
    }

    /** Выполнить через ticks тиков (0 — в конце текущего/следующего тика). Только из главного потока. */
    public static void later(int ticks, Runnable runnable) {
        TASKS.add(new Task(runnable, Math.max(0, ticks)));
    }

    public static void tick() {
        if (TASKS.isEmpty()) {
            return;
        }
        List<Task> due = new ArrayList<Task>();
        for (int i = TASKS.size() - 1; i >= 0; i--) {
            Task t = TASKS.get(i);
            if (t.ticks-- <= 0) {
                TASKS.remove(i);
                due.add(0, t);
            }
        }
        for (Task t : due) {
            try {
                t.runnable.run();
            } catch (Throwable e) {
                EcoServ.LOG.error("Ошибка отложенной задачи", e);
            }
        }
    }

    public static void clear() {
        TASKS.clear();
    }
}
