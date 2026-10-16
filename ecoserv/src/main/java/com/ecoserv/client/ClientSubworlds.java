package com.ecoserv.client;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Клиентское состояние подмиров, которое сбрасывается при отключении от сервера. */
@SideOnly(Side.CLIENT)
public final class ClientSubworlds {

    private ClientSubworlds() {
    }

    public static void reset() {
        ClientAbyss.stop();
        UploadSender.cancelCurrent();
        SubworldSky.clear();
    }
}
