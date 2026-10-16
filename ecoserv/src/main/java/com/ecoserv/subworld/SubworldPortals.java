package com.ecoserv.subworld;

import com.ecoserv.region.Region;
import com.ecoserv.region.RegionManager;
import com.ecoserv.util.ChatUtil;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Порталы в подмиры (п. 4): портал, созданный палочкой порталов, настраивается в меню миров →
 * «Порталы» и ведёт в подмир по имени — в общий мир или в локальный мир команды игрока, либо к другому
 * порталу (в том числе к порталу внутри подмира). Порталы и зоны, поставленные в шаблоне подмира,
 * работают во всех локальных мирах команд.
 */
public final class SubworldPortals {

    private static boolean installed;

    private SubworldPortals() {
    }

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        // локальный мир команды — копия шаблона: в нём действуют области шаблона
        RegionManager.setDimAlias(new RegionManager.DimAlias() {
            @Override
            public int regionsOf(int dim) {
                SubworldDim d = SubworldRegistry.dim(dim);
                if (d == null || !d.isInstance()) {
                    return dim;
                }
                SubworldDim tpl = SubworldRegistry.find(d.subworld, SubworldDim.TEMPLATE);
                return tpl == null ? dim : tpl.dim;
            }
        });
        RegionManager.addPortalHandler(new RegionManager.PortalHandler() {
            @Override
            public boolean handle(EntityPlayerMP player, Region portal) {
                if ("portal".equals(portal.targetMode)) {
                    return toPortal(player, portal);
                }
                Subworld sw = SubworldRegistry.get(portal.target);
                if (sw == null) {
                    return false;
                }
                double[] point = "point".equals(portal.targetMode)
                        ? new double[]{portal.tx, portal.ty, portal.tz, portal.tyaw, portal.tpitch} : null;
                SubworldDim here = SubworldRegistry.dim(player.dimension);
                if (point != null && here != null && here.subworld.equals(sw.id)) {
                    // портал внутри того же подмира: остаёмся в этом же мире (свой или в гостях)
                    SubworldTeleport.teleportTo(player, here, false, point);
                    return true;
                }
                SubworldTeleport.enter(player, sw, !"last".equals(portal.targetMode), point);
                return true;
            }
        });
    }

    /** Портал ведёт к другому порталу; если тот в подмире — в общий мир или в свой локальный мир. */
    private static boolean toPortal(EntityPlayerMP player, Region portal) {
        Region dest = RegionManager.get(portal.targetPortal);
        if (dest == null || dest == portal) {
            return false;
        }
        Subworld sw = SubworldRegistry.subworldOf(dest.dim);
        if (sw == null) {
            // обычный мир — обычный обработчик
            return false;
        }
        double[] point = RegionManager.exitPoint(dest);
        SubworldDim here = SubworldRegistry.dim(player.dimension);
        if (here != null && here.subworld.equals(sw.id)) {
            SubworldTeleport.teleportTo(player, here, false, point);
            return true;
        }
        if (!SubworldTeleport.enter(player, sw, false, point)) {
            ChatUtil.actionBar(player, "&cПортал «" + portal.name + "» ведёт в подмир «" + sw.name + "&c», куда тебе пока нельзя.");
        }
        return true;
    }
}
