package com.ecoserv.games;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Мини-игры: на арене не умирают и ничего не ломают, пульты, выход при отключении, возврат после сбоя. */
public class GameEvents {

    private static boolean inArena(net.minecraft.entity.Entity e) {
        return e instanceof EntityPlayerMP && GameArena.isArenaDim(e.dimension);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(LivingAttackEvent event) {
        if (inArena(event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHurt(LivingHurtEvent event) {
        if (inArena(event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (inArena(event.getPlayer()) && !com.ecoserv.util.Perms.isAdmin(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (inArena(event.getPlayer()) && !com.ecoserv.util.Perms.isAdmin(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /** Пульт ловушечника (дезран). */
    @SubscribeEvent
    public void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getWorld().isRemote || !(event.getEntityPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        ItemStack s = event.getItemStack();
        if (s.hasTagCompound() && s.getTagCompound().getBoolean("ecoTrapRemote")) {
            event.setCanceled(true);
            Game g = GameManager.running(event.getEntityPlayer().getUniqueID());
            if (g instanceof DeathrunGame) {
                ((DeathrunGame) g).trigger((EntityPlayerMP) event.getEntityPlayer());
            }
        }
    }

    /** Пульт котельной («Давление») — ПКМ по маяку в пультовой. */
    @SubscribeEvent
    public void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isRemote || !inArena(event.getEntityPlayer())) {
            return;
        }
        Game g = GameManager.running(event.getEntityPlayer().getUniqueID());
        if (g instanceof PressureGame && ((PressureGame) g).isConsole(event.getPos())) {
            event.setCanceled(true);
            ((PressureGame) g).open((EntityPlayerMP) event.getEntityPlayer());
        } else if (!com.ecoserv.util.Perms.isAdmin(event.getEntityPlayer())) {
            // на арене ничего не открываем (маяки, сундуки декораций)
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        GameManager.leave(event.player.getUniqueID());
    }

    /** Вошёл, а игры уже нет (сервер упал посреди игры) — вернуть домой и режим игры. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        final EntityPlayerMP p = (EntityPlayerMP) event.player;
        if (GameManager.sessionOf(p.getUniqueID()) == null) {
            com.ecoserv.server.ServerTasks.later(20, () -> {
                if (GameArena.isArenaDim(p.dimension)) {
                    GameManager.exitArena(p);
                } else {
                    GameManager.restoreMode(p);
                }
            });
        }
    }

    /** Ушёл из арены сам (команда /world, портал админа) — вернуть режим игры. */
    @SubscribeEvent
    public void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP && GameArena.isArenaDim(event.fromDim)
                && !GameArena.isArenaDim(event.toDim)) {
            GameManager.restoreMode((EntityPlayerMP) event.player);
            if (GameManager.running(event.player.getUniqueID()) != null) {
                GameManager.leave(event.player.getUniqueID());
            }
        }
    }
}
