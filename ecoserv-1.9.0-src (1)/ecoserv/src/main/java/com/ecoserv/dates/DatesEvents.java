package com.ecoserv.dates;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Подарки на значимые даты и день рождения — при входе в игру. */
public class DatesEvents {

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            DatesManager.check((EntityPlayerMP) event.player);
        }
    }
}
