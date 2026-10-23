package com.ecoserv.zone;

import com.ecoserv.item.ItemPermitWand;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Палочка разрешения в приключении: приключенцу Minecraft не даёт ломать блоки ни на клиенте, ни на сервере.
 * Пока палочка в руке и игрок у разрешённой зоны, ему включается «можно править мир» (allowEdit) — и он ломает
 * как в выживании. Ставить блоки в приключении при этом нельзя (отменяем установку).
 */
public class PermitEvents {

    /** Кому мы включили allowEdit. */
    private static final Set<UUID> GRANTED = new HashSet<UUID>();
    private int tick;

    /** Есть ли рядом с игроком (в 6 блоках) зона, где палочка разрешает ломать. */
    private static boolean nearPermitted(EntityPlayerMP p) {
        BlockPos base = p.getPosition();
        for (int dx = -6; dx <= 6; dx += 3) {
            for (int dy = -3; dy <= 4; dy += 3) {
                for (int dz = -6; dz <= 6; dz += 3) {
                    Zone z = ZoneManager.at(p.dimension, base.add(dx, dy, dz));
                    if (z != null && ItemPermitWand.permits(p, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 5 != 0) {
            return;
        }
        for (EntityPlayerMP p : PlayerUtil.allOnline()) {
            boolean adventure = p.interactionManager.getGameType() == GameType.ADVENTURE;
            boolean want = adventure && ItemPermitWand.holds(p) && !ZoneManager.isEmpty() && nearPermitted(p);
            boolean has = GRANTED.contains(p.getUniqueID());
            if (want && !has) {
                GRANTED.add(p.getUniqueID());
                p.capabilities.allowEdit = true;
                p.sendPlayerAbilities();
            } else if (!want && has) {
                GRANTED.remove(p.getUniqueID());
                if (p.interactionManager.getGameType() == GameType.ADVENTURE) {
                    p.capabilities.allowEdit = false;
                    p.sendPlayerAbilities();
                }
            }
        }
    }

    /** Приключенец с «разрешением» только ломает, ставить не может. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        EntityPlayer p = event.getPlayer();
        if (p != null && GRANTED.contains(p.getUniqueID())) {
            event.setCanceled(true);
        }
    }

    public static void forget(UUID player) {
        GRANTED.remove(player);
    }
}
