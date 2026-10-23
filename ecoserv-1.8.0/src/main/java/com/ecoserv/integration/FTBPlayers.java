package com.ecoserv.integration;

import com.ecoserv.EcoServ;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Получение объекта ForgePlayer из FTBLib по UUID — некоторые API FTB
 * принимают именно его, а не игрока.
 */
public final class FTBPlayers {

    private static boolean checked;
    private static Object universe;
    private static Method getPlayerByUuid;
    private static Method getPlayerByPlayer;

    private FTBPlayers() {
    }

    private static void init() {
        if (checked) {
            return;
        }
        checked = true;
        try {
            Class<?> universeClass = Class.forName("com.feed_the_beast.ftblib.lib.data.Universe");
            Method get = universeClass.getMethod("get");
            universe = get.invoke(null);
            Method[] methods = universeClass.getMethods();
            for (int i = 0; i < methods.length; i++) {
                Method m = methods[i];
                if (!m.getName().equals("getPlayer") || m.getParameterTypes().length != 1) {
                    continue;
                }
                Class<?> param = m.getParameterTypes()[0];
                if (param == UUID.class) {
                    getPlayerByUuid = m;
                } else if (param.getName().endsWith("EntityPlayer") || param.getName().endsWith("EntityPlayerMP")
                        || param == Object.class) {
                    getPlayerByPlayer = m;
                }
            }
        } catch (Throwable t) {
            EcoServ.LOG.debug("FTBLib Universe недоступен: {}", t.toString());
        }
    }

    /** Сбрасывает кеш (Universe пересоздаётся при перезапуске мира). */
    public static void reset() {
        checked = false;
        universe = null;
        getPlayerByUuid = null;
        getPlayerByPlayer = null;
    }

    @Nullable
    public static Object get(UUID uuid) {
        init();
        if (universe == null || uuid == null) {
            return null;
        }
        try {
            if (getPlayerByUuid != null) {
                Object result = getPlayerByUuid.invoke(universe, uuid);
                if (result != null) {
                    return result;
                }
            }
            if (getPlayerByPlayer != null) {
                EntityPlayerMP player = PlayerUtil.online(uuid);
                if (player != null) {
                    return getPlayerByPlayer.invoke(universe, player);
                }
            }
        } catch (Throwable t) {
            EcoServ.LOG.debug("Не удалось получить ForgePlayer: {}", t.toString());
        }
        return null;
    }

    public static boolean isAvailable() {
        init();
        return universe != null;
    }
}
