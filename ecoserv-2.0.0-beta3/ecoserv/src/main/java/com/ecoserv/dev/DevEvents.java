package com.ecoserv.dev;

import net.minecraft.entity.player.EntityPlayerMP;

import javax.annotation.Nullable;

/**
 * События для сценариев разработчика (2.0): PlayerJoin(), PlayerRespawn(), EnterZone() и т.п.
 * Каждому событию админ привязывает сценарии в режиме разработчика.
 */
public final class DevEvents {

    private DevEvents() {
    }

    /** Сработало событие name для игрока (context — имя зоны, блока, NPC…). */
    public static void fire(String name, @Nullable EntityPlayerMP player, @Nullable String context) {
        DevHooks.fire(name, player, context);
    }
}
