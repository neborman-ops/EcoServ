package com.ecoserv.npc;

import javax.annotation.Nullable;

/** Заготовка — заменяется полной реализацией NPC. */
public final class NpcManager {
    private NpcManager() {
    }

    @Nullable
    public static Object get(String id) {
        return null;
    }

    public static String titleOf(String id) {
        return id;
    }
}
