package com.ecoserv.v2;

import com.ecoserv.EcoServ;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketV2;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import java.util.HashMap;
import java.util.Map;

/** 2.0: обработчики действий клиента (PacketV2Action) по типу и отправка PacketV2. */
public final class V2Actions {

    public interface Handler {
        void handle(EntityPlayerMP player, NBTTagCompound data);
    }

    private static final Map<String, Handler> HANDLERS = new HashMap<String, Handler>();

    private V2Actions() {
    }

    public static void on(String type, Handler h) {
        HANDLERS.put(type, h);
    }

    public static void handle(EntityPlayerMP player, String type, NBTTagCompound data) {
        Handler h = HANDLERS.get(type);
        if (h == null || player == null) {
            return;
        }
        try {
            h.handle(player, data);
        } catch (Throwable t) {
            EcoServ.LOG.error("EcoServ 2.0: действие клиента {} упало", type, t);
        }
    }

    public static void send(EntityPlayerMP player, String type, NBTTagCompound data) {
        if (player != null && player.connection != null && com.ecoserv.ui.UiManager.clientHasMod(player)) {
            PacketHandler.sendTo(new PacketV2(type, data), player);
        }
    }

    public static void sendAll(String type, NBTTagCompound data) {
        for (EntityPlayerMP p : com.ecoserv.util.PlayerUtil.allOnline()) {
            send(p, type, data);
        }
    }
}
