package com.ecoserv.restrict;

import com.ecoserv.config.EcoConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** События для частичных запретов предметов по мирам. */
public class RestrictionEvents {

    // --- ношение и буфер ----------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            RestrictionService.scan((EntityPlayerMP) event.player, true);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RestrictionService.forget(event.player.getUniqueID());
        // данные игрока пишутся на диск при выходе — буфер сохраняем тогда же
        Stash.saveIfDirty();
    }

    /** Сохраняем буфер вместе с миром, чтобы при падении сервера он не разошёлся с инвентарями. */
    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        if (!event.getWorld().isRemote && event.getWorld().provider.getDimension() == 0) {
            Stash.saveIfDirty();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            RestrictionService.scan((EntityPlayerMP) event.player, true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            RestrictionService.scan((EntityPlayerMP) event.player, true);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (ItemRules.hasCraftRules()) {
            // слот результата, из которого нельзя забрать запрещённое (верстак и крафт 2x2)
            GuardedSlotCrafting.guard(player.inventoryContainer, player);
            if (player.openContainer != player.inventoryContainer) {
                GuardedSlotCrafting.guard(player.openContainer, player);
            }
            // и чтобы запрещённый результат вообще не маячил в окне (верстаки модов тоже)
            RestrictionService.clearCraftResults(player);
        }
        // раз в секунду (у каждого игрока в свой тик, чтобы не нагружать сервер разом)
        if ((player.ticksExisted + player.getEntityId()) % 20 == 0
                && (ItemRules.hasCarryRules() || Stash.hasItems(player.getUniqueID()))) {
            RestrictionService.scan(player, false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPickup(EntityItemPickupEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player == null || event.getItem() == null) {
            return;
        }
        if (ItemRules.denies(player, event.getItem().getItem(), RestrictAction.CARRY)) {
            event.setCanceled(true);
            RestrictionService.warn(player, EcoConfig.restrictMessageCarry);
        }
    }

    // --- использование -------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (ItemRules.denies(event.getEntityPlayer(), event.getItemStack(), RestrictAction.USE)) {
            event.setCanceled(true);
            RestrictionService.warn(event.getEntityPlayer(), EcoConfig.restrictMessageUse);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        EntityPlayer player = event.getEntityPlayer();
        // мод выключен в этом мире: его машины и сундуки не открываются
        if (ItemRules.deniesBlockUse(player, event.getWorld().getBlockState(event.getPos()).getBlock())) {
            event.setUseBlock(Event.Result.DENY);
            RestrictionService.warn(player, EcoConfig.restrictMessageUse);
        }
        if (ItemRules.denies(player, stack, RestrictAction.USE)) {
            // сам блок (дверь, сундук) открыть можно, но предмет не применяется
            event.setUseItem(Event.Result.DENY);
            RestrictionService.warn(player, EcoConfig.restrictMessageUse);
        } else if (ItemRules.denies(player, stack, RestrictAction.PLACE)) {
            event.setUseItem(Event.Result.DENY);
            RestrictionService.warn(player, EcoConfig.restrictMessagePlace);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (ItemRules.denies(event.getEntityPlayer(), event.getItemStack(), RestrictAction.USE)) {
            event.setCanceled(true);
            RestrictionService.warn(event.getEntityPlayer(), EcoConfig.restrictMessageUse);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(AttackEntityEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player != null && ItemRules.denies(player, player.getHeldItemMainhand(), RestrictAction.USE)) {
            event.setCanceled(true);
            RestrictionService.warn(player, EcoConfig.restrictMessageUse);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (ItemRules.denies(event.getEntityPlayer(), event.getItemStack(), RestrictAction.USE)) {
            event.setUseItem(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreak(BlockEvent.BreakEvent event) {
        EntityPlayer player = event.getPlayer();
        if (player != null && ItemRules.denies(player, player.getHeldItemMainhand(), RestrictAction.USE)) {
            event.setCanceled(true);
            RestrictionService.warn(player, EcoConfig.restrictMessageUse);
        }
    }

    // --- установка -----------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (ItemRules.denies(event.getPlayer(), event.getItemInHand(), RestrictAction.PLACE)) {
            event.setCanceled(true);
            RestrictionService.warn(event.getPlayer(), EcoConfig.restrictMessagePlace);
        }
    }
}
