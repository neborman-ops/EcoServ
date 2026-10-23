package com.ecoserv.util;

import com.ecoserv.EcoServ;
import com.ecoserv.Ref;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;

/**
 * Права. Работает и с чистым Forge (проверка по уровню оператора),
 * и с системами прав через Forge PermissionAPI (FTB Utilities, LuckPerms-мосты и т.п.).
 */
public final class Perms {

    public static final String ADMIN = Ref.MODID + ".admin";
    public static final String SHOP_EDIT = Ref.MODID + ".shop.edit";
    public static final String SHOP_USE = Ref.MODID + ".shop.use";
    public static final String TRADE = Ref.MODID + ".trade";
    public static final String MARKET_PLACE = Ref.MODID + ".market.place";
    public static final String BACKUP = Ref.MODID + ".backup";
    public static final String WORLD = Ref.MODID + ".world";

    private Perms() {
    }

    public static void register() {
        try {
            PermissionAPI.registerNode(ADMIN, DefaultPermissionLevel.OP, "Полный доступ к EcoServ");
            PermissionAPI.registerNode(SHOP_EDIT, DefaultPermissionLevel.OP, "Редактирование прайс-листа магазина");
            PermissionAPI.registerNode(SHOP_USE, DefaultPermissionLevel.ALL, "Доступ к магазину и балансу");
            PermissionAPI.registerNode(TRADE, DefaultPermissionLevel.ALL, "Торговля между игроками");
            PermissionAPI.registerNode(MARKET_PLACE, DefaultPermissionLevel.ALL, "Установка блока-скупщика");
            PermissionAPI.registerNode(BACKUP, DefaultPermissionLevel.OP, "Управление бэкапами");
            PermissionAPI.registerNode(WORLD, DefaultPermissionLevel.ALL, "Телепорт между мирами командой /world");
        } catch (Throwable t) {
            EcoServ.LOG.warn("PermissionAPI недоступен, используется проверка по уровню оператора: {}", t.toString());
        }
    }

    public static boolean has(EntityPlayer player, String node) {
        if (player == null) {
            return false;
        }
        // права, выданные ролями (/role perm <роль> add <право>)
        if (!player.world.isRemote && com.ecoserv.role.RoleManager.hasPermission(player.getUniqueID(), node)) {
            return true;
        }
        if (player instanceof EntityPlayerMP) {
            try {
                if (PermissionAPI.hasPermission((EntityPlayerMP) player, node)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // упадём на проверку оператора ниже
            }
        }
        return player.canUseCommand(2, node);
    }

    public static boolean isAdmin(EntityPlayer player) {
        return player != null && has(player, ADMIN);
    }

    public static boolean isAdmin(ICommandSender sender) {
        if (sender instanceof EntityPlayer) {
            return isAdmin((EntityPlayer) sender);
        }
        // консоль / командный блок с достаточным уровнем
        return sender != null && sender.canUseCommand(2, ADMIN);
    }
}
