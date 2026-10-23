package com.ecoserv.games;

import net.minecraft.entity.player.EntityPlayerMP;

/** Мини-игра, где важно, кто кого ударил (урона на арене нет — удар только передаётся игре). */
public interface HitGame {
    void onHit(EntityPlayerMP attacker, EntityPlayerMP victim);
}
