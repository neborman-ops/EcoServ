package com.ecoserv.network.packet;

import com.ecoserv.subworld.SubworldView;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/** Сервер -> клиент: измерения подмиров (регистрация на клиенте) и их небо/тип. */
public class PacketSubworldSync implements IMessage {

    public boolean full;
    public List<SubworldView> views = new ArrayList<SubworldView>();

    public PacketSubworldSync() {
    }

    public PacketSubworldSync(boolean full, List<SubworldView> views) {
        this.full = full;
        this.views = views;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        full = buf.readBoolean();
        int n = Math.min(100000, buf.readInt());
        views = new ArrayList<SubworldView>();
        for (int i = 0; i < n; i++) {
            views.add(SubworldView.read(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(full);
        buf.writeInt(views.size());
        for (SubworldView v : views) {
            v.write(buf);
        }
    }

    public static class Handler implements IMessageHandler<PacketSubworldSync, IMessage> {
        @Override
        public IMessage onMessage(PacketSubworldSync message, MessageContext ctx) {
            com.ecoserv.client.ClientHooks.onSubworldSync(message);
            return null;
        }
    }
}
