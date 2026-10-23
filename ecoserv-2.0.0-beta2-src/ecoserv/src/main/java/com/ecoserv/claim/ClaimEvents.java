package com.ecoserv.claim;

import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.util.ChatUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.CapabilityItemHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Защита приватов (1.9.0): ломать, ставить, открывать, бить мирных — только хозяину, команде и доверенным. */
public class ClaimEvents {

    private final Map<UUID, String> lastClaim = new HashMap<UUID, String>();

    private static boolean active() {
        return ExtraConfig.claimsEnabled;
    }

    /** Запрещено ли игроку трогать блок здесь (и сообщить ему). */
    private static boolean denied(EntityPlayer p, World w, BlockPos pos) {
        if (!active() || w.isRemote || p == null) {
            return false;
        }
        ClaimManager.Claim c = ClaimManager.at(w.provider.getDimension(), pos);
        if (c == null || ClaimManager.trusted(p, c)) {
            return false;
        }
        ChatUtil.actionBar(p, "&cЭто территория &f" + c.ownerName + "&c.");
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (denied(event.getPlayer(), event.getWorld(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (denied(event.getPlayer(), event.getWorld(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBucket(FillBucketEvent event) {
        if (event.getTarget() != null && event.getTarget().getBlockPos() != null
                && denied(event.getEntityPlayer(), event.getWorld(), event.getTarget().getBlockPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent.RightClickBlock event) {
        World w = event.getWorld();
        if (!active() || w.isRemote) {
            return;
        }
        ClaimManager.Claim c = ClaimManager.at(w.provider.getDimension(), event.getPos());
        if (c == null || ClaimManager.trusted(event.getEntityPlayer(), c)) {
            return;
        }
        ClaimManager.Owner o = ClaimManager.owner(c.owner);
        Block b = w.getBlockState(event.getPos()).getBlock();
        boolean door = b instanceof BlockDoor || b instanceof BlockTrapDoor || b instanceof BlockFenceGate
                || b instanceof BlockButton || b instanceof BlockLever;
        if (door && o.publicDoors) {
            return;
        }
        TileEntity te = w.getTileEntity(event.getPos());
        boolean container = te instanceof IInventory
                || (te != null && te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null));
        if (container && o.publicContainers) {
            return;
        }
        if (!door && !container && event.getItemStack().isEmpty()) {
            // просто клик пустой рукой по обычному блоку — пусть (верстак, кровать...)
            return;
        }
        event.setCanceled(true);
        ChatUtil.actionBar(event.getEntityPlayer(), "&cЭто территория &f" + c.ownerName + "&c.");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityUse(PlayerInteractEvent.EntityInteract event) {
        Entity t = event.getTarget();
        if ((t instanceof EntityHanging || t instanceof EntityArmorStand)
                && denied(event.getEntityPlayer(), event.getWorld(), t.getPosition())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(AttackEntityEvent event) {
        Entity t = event.getTarget();
        if ((t instanceof EntityHanging || t instanceof EntityArmorStand || t instanceof EntityAnimal)
                && denied(event.getEntityPlayer(), t.world, t.getPosition())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPvp(LivingAttackEvent event) {
        if (!active() || !(event.getEntityLiving() instanceof EntityPlayerMP)
                || !(event.getSource().getTrueSource() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP victim = (EntityPlayerMP) event.getEntityLiving();
        ClaimManager.Claim c = ClaimManager.at(victim.dimension, victim.getPosition());
        if (c != null && !ClaimManager.owner(c.owner).pvp && event.getSource().getTrueSource() != victim) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onExplode(ExplosionEvent.Detonate event) {
        if (!active() || ExtraConfig.claimsExplosions || event.getWorld().isRemote) {
            return;
        }
        final int dim = event.getWorld().provider.getDimension();
        event.getAffectedBlocks().removeIf(pos -> ClaimManager.at(dim, pos) != null);
    }

    /** Над хотбаром: чья территория, когда переходишь границу. */
    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote || !active()
                || !ExtraConfig.claimsShowEnter || event.player.ticksExisted % 10 != 0) {
            return;
        }
        EntityPlayer p = event.player;
        ClaimManager.Claim c = ClaimManager.at(p.dimension, p.getPosition());
        String now = c == null ? "" : c.owner;
        String was = lastClaim.put(p.getUniqueID(), now);
        if (was == null || was.equals(now)) {
            return;
        }
        if (c == null) {
            ChatUtil.actionBar(p, "&7Свободная земля");
        } else if (p.getUniqueID().toString().equals(c.owner)) {
            ChatUtil.actionBar(p, "&aТвоя территория");
        } else {
            ChatUtil.actionBar(p, "&eТерритория &f" + c.ownerName + (ClaimManager.trusted(p, c) ? " &a(тебе можно)" : ""));
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        lastClaim.remove(event.player.getUniqueID());
    }
}
