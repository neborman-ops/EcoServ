package com.ecoserv.region;

import com.ecoserv.item.ItemRegionWand;
import com.ecoserv.script.ScriptRunner;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Outline;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.util.Selection;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;

/** Проверка входа в зоны/порталы, выполнение сценариев и подсветка областей админам с палочкой. */
public class RegionEvents {

    private int tick;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tick++;
        ScriptRunner.tick();
        if (tick % 5 == 0) {
            for (EntityPlayerMP player : PlayerUtil.allOnline()) {
                RegionManager.tickPlayer(player);
            }
        }
        if (tick % 20 == 0) {
            for (EntityPlayerMP player : PlayerUtil.allOnline()) {
                ItemRegionWand wand = wandOf(player);
                if (wand != null && Perms.isAdmin(player)) {
                    outline(player, wand);
                }
            }
        }
    }

    private static ItemRegionWand wandOf(EntityPlayerMP player) {
        ItemStack main = player.getHeldItemMainhand();
        if (main.getItem() instanceof ItemRegionWand) {
            return (ItemRegionWand) main.getItem();
        }
        ItemStack off = player.getHeldItemOffhand();
        return off.getItem() instanceof ItemRegionWand ? (ItemRegionWand) off.getItem() : null;
    }

    private static void outline(EntityPlayerMP player, ItemRegionWand wand) {
        int budget = 900;
        for (Region r : RegionManager.inDim(player.dimension)) {
            if (budget <= 0) {
                break;
            }
            double cx = (r.minX + r.maxX) / 2.0D;
            double cz = (r.minZ + r.maxZ) / 2.0D;
            if (Math.abs(cx - player.posX) > 96 || Math.abs(cz - player.posZ) > 96) {
                continue;
            }
            float red = r.isPortal() ? 0.7F : 1.0F;
            float green = r.isPortal() ? 0.2F : 0.9F;
            float blue = r.isPortal() ? 1.0F : 0.0F;
            if (!r.enabled) {
                red = 0.4F;
                green = 0.4F;
                blue = 0.4F;
            }
            budget -= Outline.blocks(player, r.min(), r.max(), red, green, blue, Math.min(budget, 300));
        }
        Selection.Sel sel = Selection.get(player.getUniqueID(), wand.tool());
        if (sel != null && sel.dim == player.dimension) {
            if (sel.complete()) {
                Outline.blocks(player, sel.min(), sel.max(), 0.2F, 1.0F, 0.2F, 300);
            } else {
                Outline.block(player, sel.first, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    /** ЛКМ палочкой по блоку — какие здесь зоны и порталы. */
    @SubscribeEvent
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getWorld().isRemote || !(event.getEntityPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        if (!(event.getItemStack().getItem() instanceof ItemRegionWand)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        event.setCanceled(true);
        List<Region> here = RegionManager.at(player.dimension, event.getPos());
        if (here.isEmpty()) {
            ChatUtil.actionBar(player, "&7Здесь нет зон и порталов");
            return;
        }
        for (Region r : here) {
            ChatUtil.send(player, ChatUtil.link("&e" + (r.isPortal() ? "Портал " : "Зона ") + "&f" + r.name
                    + " &7" + r.describeArea() + (r.isPortal() ? " → " + (r.target.isEmpty() ? "?" : r.target) : "")
                    + " &e[настроить]", "/" + (r.isPortal() ? "portal" : "trigger") + " menu " + r.name, null));
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RegionManager.forget(event.player.getUniqueID());
        com.ecoserv.prefs.PlayerPrefs.forget(event.player.getUniqueID());
    }
}
