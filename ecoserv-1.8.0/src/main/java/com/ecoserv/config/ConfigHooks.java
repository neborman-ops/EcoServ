package com.ecoserv.config;

import com.ecoserv.EcoServ;

/**
 * Что пересчитать после правки конфига из игры, чтобы изменения работали сразу,
 * без /ecoadmin reload и перезапуска.
 */
public final class ConfigHooks {

    private ConfigHooks() {
    }

    public static void apply(String category) {
        try {
            if (EcoConfig.CAT_RESTART.equals(category)) {
                com.ecoserv.server.RestartScheduler.recompute();
            } else if (EcoConfig.CAT_ANNOUNCER.equals(category)) {
                com.ecoserv.server.Announcer.onServerStart();
            } else if (EcoConfig.CAT_BACKUP.equals(category)) {
                com.ecoserv.backup.BackupManager.reschedule();
            } else if (EcoConfig.CAT_WORLDS.equals(category)) {
                com.ecoserv.world.WorldNames.reload();
                com.ecoserv.restrict.ItemRules.reindex();
            } else if (EcoConfig.CAT_RESTRICT.equals(category)) {
                com.ecoserv.restrict.ItemRules.reindex();
            } else if (EcoConfig.CAT_INTEGRATION.equals(category)) {
                com.ecoserv.integration.Integrations.selectEconomy();
                com.ecoserv.integration.FTBQuestsBridge.refresh();
            }
            for (Runnable hook : EXTRA) {
                hook.run();
            }
            // налог, интервал продажи и т.п. показываются в окнах мода
            com.ecoserv.network.PacketHandler.sendPlayerStateToAll();
        } catch (Throwable t) {
            EcoServ.LOG.error("Ошибка применения настроек раздела {}", category, t);
        }
    }

    private static final java.util.List<Runnable> EXTRA = new java.util.concurrent.CopyOnWriteArrayList<Runnable>();

    /** Другие подсистемы могут подписаться на любые изменения конфига. */
    public static void onChange(Runnable hook) {
        EXTRA.add(hook);
    }
}
