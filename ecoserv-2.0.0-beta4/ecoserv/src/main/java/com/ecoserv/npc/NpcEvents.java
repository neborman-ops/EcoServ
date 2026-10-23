package com.ecoserv.npc;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Нажатие на NPC, защита NPC/голограмм/спутников от урона и чужих действий. */
public class NpcEvents {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        handle(event, event.getTarget());
    }

    /** Стойки получают нажатие сюда (иначе игрок снимет с них броню). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        handle(event, event.getTarget());
    }

    private static void handle(PlayerInteractEvent event, Entity target) {
        if (target == null || !NpcManager.isOurs(target)) {
            return;
        }
        event.setCanceled(true);
        if (event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND
                || !(event.getEntityPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        Npc n = NpcManager.npcOf(target);
        if (n != null) {
            NpcDialogScreen.click((EntityPlayerMP) event.getEntityPlayer(), n);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(LivingAttackEvent event) {
        if (NpcManager.isOurs(event.getEntity())) {
            event.setCanceled(true);
            if (event.getSource().getTrueSource() instanceof EntityPlayerMP) {
                Npc n = NpcManager.npcOf(event.getEntity());
                if (n != null) {
                    // удар — тоже «поговорить» (многие так и делают)
                    NpcDialogScreen.click((EntityPlayerMP) event.getSource().getTrueSource(), n);
                }
            }
        }
    }
}
