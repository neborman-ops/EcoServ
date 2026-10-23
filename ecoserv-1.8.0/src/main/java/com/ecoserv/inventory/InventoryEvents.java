package com.ecoserv.inventory;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * Смена инвентаря при переходе между группами миров. Приоритет HIGHEST: инвентарь нужно
 * поменять раньше, чем запреты предметов проверят, что игрок принёс в новый мир.
 */
public class InventoryEvents {

    public InventoryEvents() {
        // группы поменяли в редакторе конфига — сразу раздать нужные инвентари тем, кто в сети
        com.ecoserv.config.ConfigHooks.onChange(new Runnable() {
            @Override
            public void run() {
                for (EntityPlayerMP p : com.ecoserv.util.PlayerUtil.allOnline()) {
                    InventoryGroups.check(p);
                }
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            InventoryGroups.check((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            InventoryGroups.check((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            InventoryGroups.check((EntityPlayerMP) event.player);
        }
    }
}
