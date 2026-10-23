package com.ecoserv.quest;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** События для условий квестов (2.0): убийства, добыча, крафт, постройка, кнопки. */
public class QuestEvents implements com.ecoserv.activator.ActivationListener {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        Entity src = event.getSource().getTrueSource();
        if (!(src instanceof EntityPlayerMP) || event.getEntity().world.isRemote) {
            return;
        }
        Entity dead = event.getEntity();
        String key;
        if (dead instanceof EntityPlayer) {
            key = "player";
        } else {
            ResourceLocation rl = EntityList.getKey(dead);
            key = rl == null ? "?" : rl.toString();
        }
        QuestManager.event((EntityPlayerMP) src, "kill", key, 1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        IBlockState st = event.getState();
        if (st.getBlock().getRegistryName() == null) {
            return;
        }
        QuestManager.event((EntityPlayerMP) event.getPlayer(), "mine",
                st.getBlock().getRegistryName() + "@" + st.getBlock().getMetaFromState(st), 1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        IBlockState st = event.getPlacedBlock();
        if (st.getBlock().getRegistryName() == null) {
            return;
        }
        QuestManager.event((EntityPlayerMP) event.getPlayer(), "place",
                st.getBlock().getRegistryName() + "@" + st.getBlock().getMetaFromState(st), 1);
    }

    @SubscribeEvent
    public void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        ItemStack s = event.crafting;
        if (s == null || s.isEmpty() || s.getItem().getRegistryName() == null) {
            return;
        }
        QuestManager.event((EntityPlayerMP) event.player, "craft", s.getItem().getRegistryName() + "@" + s.getMetadata(),
                Math.max(1, s.getCount()));
    }

    @Override
    public void onActivated(EntityPlayerMP player, String name) {
        QuestManager.event(player, "activation", name, 1);
    }
}
