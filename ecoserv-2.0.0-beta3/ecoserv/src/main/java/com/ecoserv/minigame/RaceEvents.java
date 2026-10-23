package com.ecoserv.minigame;

import com.ecoserv.util.ChatUtil;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Арена гонки: эндер-сундук закрыт (через него можно было бы пронести вещи), выход с сервера — из лобби. */
public class RaceEvents {

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isRemote || !RaceArena.isArena(event.getWorld().provider.getDimension())) {
            return;
        }
        if (event.getWorld().getBlockState(event.getPos()).getBlock() instanceof BlockEnderChest) {
            event.setCanceled(true);
            if (event.getEntityPlayer() instanceof EntityPlayerMP) {
                ChatUtil.actionBar(event.getEntityPlayer(), "&cНа арене эндер-сундук закрыт");
            }
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            RaceManager.onLogout((EntityPlayerMP) event.player);
        }
    }
}
