package com.ecoserv.client.v2;

import com.ecoserv.EcoServ;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketV2Action;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;

/** 2.0: приём PacketV2 на клиенте (в главном потоке) и отправка действий на сервер. */
@SideOnly(Side.CLIENT)
public final class ClientV2 {

    public interface Receiver {
        void receive(NBTTagCompound data);
    }

    private static final Map<String, Receiver> RECEIVERS = new HashMap<String, Receiver>();

    private ClientV2() {
    }

    public static void on(String type, Receiver r) {
        RECEIVERS.put(type, r);
    }

    public static void receive(final String type, final NBTTagCompound data) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Receiver r = RECEIVERS.get(type);
            if (r == null) {
                return;
            }
            try {
                r.receive(data);
            } catch (Throwable t) {
                EcoServ.LOG.error("EcoServ 2.0: пакет {} не обработан", type, t);
            }
        });
    }

    public static void send(String type, NBTTagCompound data) {
        PacketHandler.sendToServer(new PacketV2Action(type, data));
    }
}
