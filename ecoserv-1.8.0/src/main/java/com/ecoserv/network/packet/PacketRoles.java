package com.ecoserv.network.packet;

import com.ecoserv.network.NetUtil;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/** Сервер -> клиент: роли своего игрока (для барьера ролей и показа в меню). */
public class PacketRoles implements IMessage {

    public List<String> ids = new ArrayList<String>();
    public List<String> titles = new ArrayList<String>();

    public PacketRoles() {
    }

    public PacketRoles(List<String> ids, List<String> titles) {
        this.ids = ids;
        this.titles = titles;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = Math.min(256, buf.readShort());
        ids = new ArrayList<String>();
        titles = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            ids.add(NetUtil.readString(buf));
            titles.add(NetUtil.readString(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int n = Math.min(256, ids.size());
        buf.writeShort(n);
        for (int i = 0; i < n; i++) {
            NetUtil.writeString(buf, ids.get(i));
            NetUtil.writeString(buf, i < titles.size() ? titles.get(i) : ids.get(i));
        }
    }

    public static class Handler implements IMessageHandler<PacketRoles, IMessage> {
        @Override
        public IMessage onMessage(PacketRoles message, MessageContext ctx) {
            com.ecoserv.client.ClientHooks.onRoles(message);
            return null;
        }
    }
}
