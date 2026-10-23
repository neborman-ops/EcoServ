package com.ecoserv.world;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Запоминаем, где игрок был в мире, перед любым переходом (порталы, /world, команды модов). */
public class WorldEvents {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTravel(EntityTravelToDimensionEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntity();
        if (player.world.isRemote || player.dimension == event.getDimension()) {
            return;
        }
        WorldTeleport.rememberPosition(player);
    }
}
