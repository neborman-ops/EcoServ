package com.ecoserv.content;

import com.ecoserv.server.ServerTasks;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Чекпоинты: где умер и возрождение на чекпоинте; палочка заполнения — остановка при выходе. */
public class ContentEvents {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntityLiving() instanceof EntityPlayerMP) {
            Checkpoints.onDeath((EntityPlayerMP) event.getEntityLiving());
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        final EntityPlayerMP player = (EntityPlayerMP) event.player;
        // на следующем тике: игрок уже полностью возродился и добавлен в мир
        ServerTasks.later(1, new Runnable() {
            @Override
            public void run() {
                if (player.connection != null && player.isEntityAlive()) {
                    Checkpoints.onRespawn(player);
                }
            }
        });
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FillJobs.cancel(event.player.getUniqueID());
    }
}
