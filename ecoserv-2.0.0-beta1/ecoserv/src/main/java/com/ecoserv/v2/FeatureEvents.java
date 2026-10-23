package com.ecoserv.v2;

import com.ecoserv.dev.DevEvents;
import com.ecoserv.dev.DevManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** События систем 2.0: вход/выход, смерть, возрождение, смена мира → события разработчика. */
public class FeatureEvents {

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        // вход по паролю — «join» сработает после него
        if (!com.ecoserv.auth.AuthManager.isPending(p)) {
            DevManager.onJoin(p);
        }
        Features.onPlayerJoin(p);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            DevEvents.fire("leave", (EntityPlayerMP) event.player, null);
            DevManager.forget(event.player.getUniqueID());
            Features.onPlayerLeave((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            DevEvents.fire("respawn", (EntityPlayerMP) event.player, null);
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityPlayerMP && !event.getEntity().world.isRemote) {
            DevEvents.fire("death", (EntityPlayerMP) event.getEntity(), null);
        }
    }

    @SubscribeEvent
    public void onDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            DevEvents.fire("world_enter", (EntityPlayerMP) event.player, com.ecoserv.world.WorldNames.nameOf(event.toDim));
            Features.onPlayerChangeDim((EntityPlayerMP) event.player);
        }
    }
}
