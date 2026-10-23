package com.ecoserv.network.packet;

import com.ecoserv.network.NetUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Клиент -> сервер (1.9.0): клиентские настройки, которые важны серверу (кто может присылать запросы). */
public class PacketClientPrefs implements IMessage {

    public String trade = "all";
    public String money = "all";
    public String invites = "all";
    public boolean computers = true;
    public boolean flashes = true;
    /** Язык текстов мода (язык игры или выбранный в клиентских настройках). */
    public String lang = "";

    public PacketClientPrefs() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        trade = NetUtil.readString(buf);
        money = NetUtil.readString(buf);
        invites = NetUtil.readString(buf);
        computers = buf.readBoolean();
        flashes = buf.readBoolean();
        lang = buf.isReadable() ? NetUtil.readString(buf) : "";
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetUtil.writeString(buf, trade);
        NetUtil.writeString(buf, money);
        NetUtil.writeString(buf, invites);
        buf.writeBoolean(computers);
        buf.writeBoolean(flashes);
        NetUtil.writeString(buf, lang == null ? "" : lang);
    }

    public static class Handler implements IMessageHandler<PacketClientPrefs, IMessage> {
        @Override
        public IMessage onMessage(final PacketClientPrefs message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> com.ecoserv.prefs.PlayerPrefs.set(player, message));
            return null;
        }
    }
}
