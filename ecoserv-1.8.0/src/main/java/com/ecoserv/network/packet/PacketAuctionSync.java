package com.ecoserv.network.packet;

import com.ecoserv.client.ClientHooks;
import com.ecoserv.network.NetUtil;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Сервер -> клиент: лоты аукциона (по частям, сжатые). */
public class PacketAuctionSync implements IMessage {

    public int part;
    public int total;
    public byte[] data;

    public PacketAuctionSync() {
    }

    public PacketAuctionSync(int part, int total, byte[] data) {
        this.part = part;
        this.total = total;
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        part = buf.readInt();
        total = buf.readInt();
        data = NetUtil.readBytes(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(part);
        buf.writeInt(total);
        NetUtil.writeBytes(buf, data);
    }

    public static class Handler implements IMessageHandler<PacketAuctionSync, IMessage> {
        @Override
        public IMessage onMessage(PacketAuctionSync message, MessageContext ctx) {
            ClientHooks.onAuctionSync(message);
            return null;
        }
    }
}
