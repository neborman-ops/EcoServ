package com.ecoserv.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public final class PlayerUtil {

    private PlayerUtil() {
    }

    @Nullable
    public static MinecraftServer server() {
        return FMLCommonHandler.instance().getMinecraftServerInstance();
    }

    @Nullable
    public static EntityPlayerMP online(@Nullable UUID uuid) {
        MinecraftServer server = server();
        if (server == null || uuid == null) {
            return null;
        }
        return server.getPlayerList().getPlayerByUUID(uuid);
    }

    @Nullable
    public static EntityPlayerMP online(String name) {
        MinecraftServer server = server();
        if (server == null || name == null || name.isEmpty()) {
            return null;
        }
        return server.getPlayerList().getPlayerByUsername(name);
    }

    /**
     * UUID игрока, который уже заходил на сервер (онлайн или кеш ников Forge), — без запроса в сеть.
     * Для действий игроков: опечатка в нике не должна подвешивать сервер на запрос к Mojang.
     */
    @Nullable
    public static UUID knownUuid(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        EntityPlayerMP player = online(name);
        if (player != null) {
            return player.getUniqueID();
        }
        try {
            for (java.util.Map.Entry<UUID, String> e : net.minecraftforge.common.UsernameCache.getMap().entrySet()) {
                if (name.equalsIgnoreCase(e.getValue())) {
                    return e.getKey();
                }
            }
        } catch (Throwable ignored) {
            // кеш ников недоступен
        }
        return null;
    }

    /** UUID по нику: сначала онлайн-игроки и кеш ников, потом кеш профилей сервера (может спросить Mojang). */
    @Nullable
    public static UUID uuidOf(String name) {
        UUID known = knownUuid(name);
        if (known != null) {
            return known;
        }
        MinecraftServer server = server();
        if (server == null) {
            return null;
        }
        try {
            GameProfile profile = server.getPlayerProfileCache().getGameProfileForUsername(name);
            return profile == null ? null : profile.getId();
        } catch (Exception e) {
            return null;
        }
    }

    public static String nameOf(@Nullable UUID uuid) {
        if (uuid == null) {
            return "?";
        }
        EntityPlayerMP player = online(uuid);
        if (player != null) {
            return player.getName();
        }
        MinecraftServer server = server();
        if (server != null) {
            try {
                GameProfile profile = server.getPlayerProfileCache().getProfileByUUID(uuid);
                if (profile != null && profile.getName() != null) {
                    return profile.getName();
                }
            } catch (Exception ignored) {
                // кеш недоступен
            }
        }
        return uuid.toString().substring(0, 8);
    }

    /** Кладёт предмет в инвентарь, остаток бросает под ноги. Никогда не теряет предметы. */
    public static void giveOrDrop(EntityPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        ItemStack copy = stack.copy();
        if (!player.inventory.addItemStackToInventory(copy)) {
            if (!copy.isEmpty()) {
                player.dropItem(copy, false);
            }
        }
        player.inventoryContainer.detectAndSendChanges();
    }

    /** Блок, на который смотрит игрок (для админских команд). */
    @Nullable
    public static net.minecraft.util.math.BlockPos lookingAt(EntityPlayerMP player, double distance) {
        if (player == null) {
            return null;
        }
        net.minecraft.util.math.Vec3d eyes = player.getPositionEyes(1.0F);
        net.minecraft.util.math.Vec3d look = player.getLookVec();
        net.minecraft.util.math.Vec3d end = eyes.add(look.x * distance, look.y * distance, look.z * distance);
        net.minecraft.util.math.RayTraceResult result = player.world.rayTraceBlocks(eyes, end, false, false, false);
        if (result != null && result.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
            return result.getBlockPos();
        }
        return null;
    }

    public static List<EntityPlayerMP> allOnline() {
        MinecraftServer server = server();
        if (server == null) {
            return java.util.Collections.emptyList();
        }
        return server.getPlayerList().getPlayers();
    }
}
