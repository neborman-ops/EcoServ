package com.ecoserv.dev;

import net.minecraft.entity.player.EntityPlayerMP;

import javax.annotation.Nullable;

/** Привязки событий к сценариям (заполняется в DevManager). */
final class DevHooks {

    private DevHooks() {
    }

    static void fire(String name, @Nullable EntityPlayerMP player, @Nullable String context) {
        DevManager.fire(name, player, context);
    }
}
