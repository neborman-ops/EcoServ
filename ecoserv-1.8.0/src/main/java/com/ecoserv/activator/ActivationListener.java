package com.ecoserv.activator;

import net.minecraft.entity.player.EntityPlayerMP;

/** Кто хочет знать о нажатии именованных кнопок (например, интеграция с FTB Quests). */
public interface ActivationListener {

    void onActivated(EntityPlayerMP player, String name);
}
