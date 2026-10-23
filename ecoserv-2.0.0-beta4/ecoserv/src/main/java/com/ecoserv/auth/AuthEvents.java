package com.ecoserv.auth;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Пока игрок не вошёл — заморожен: ни шагу, ни чата, ни команд (кроме входа), ни урона. */
public class AuthEvents {

    private static final Set<String> ALLOWED = new HashSet<String>(Arrays.asList("login", "l", "register", "reg",
            "логин", "войти", "регистрация"));

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            AuthManager.onLogin((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            AuthManager.onLogout((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof EntityPlayerMP && !AuthManager.PENDING.isEmpty()) {
            AuthManager.tick((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChat(ServerChatEvent event) {
        if (AuthManager.isPending(event.getPlayer())) {
            event.setCanceled(true);
            AuthManager.hint(event.getPlayer());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCommand(CommandEvent event) {
        if (event.getSender() instanceof EntityPlayerMP && AuthManager.isPending((EntityPlayerMP) event.getSender())) {
            String name = event.getCommand().getName().toLowerCase(Locale.ROOT);
            if (!ALLOWED.contains(name)) {
                event.setCanceled(true);
                AuthManager.hint((EntityPlayerMP) event.getSender());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.isCancelable() && AuthManager.isPending(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (AuthManager.isPending(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (AuthManager.isPending(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEntityEvent event) {
        if (AuthManager.isPending(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHurt(LivingAttackEvent event) {
        if (AuthManager.isPending(event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onToss(ItemTossEvent event) {
        if (AuthManager.isPending(event.getPlayer())) {
            // отмена выброса уничтожает предмет — возвращаем его в инвентарь
            ItemStack stack = event.getEntityItem().getItem().copy();
            event.setCanceled(true);
            if (!event.getPlayer().inventory.addItemStackToInventory(stack)) {
                event.getPlayer().dropItem(stack, false);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPickup(EntityItemPickupEvent event) {
        if (AuthManager.isPending(event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onContainer(PlayerContainerEvent.Open event) {
        if (AuthManager.isPending(event.getEntityPlayer())) {
            event.getEntityPlayer().closeScreen();
        }
    }
}
