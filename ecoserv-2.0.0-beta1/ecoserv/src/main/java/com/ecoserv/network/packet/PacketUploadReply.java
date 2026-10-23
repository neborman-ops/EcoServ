package com.ecoserv.network.packet;

import com.ecoserv.network.NetUtil;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Сервер -> клиент: ответы на загрузку мира (можно начинать, подтверждение части, готово, ошибка). */
public class PacketUploadReply implements IMessage {

    public static final int READY = 0;
    public static final int ACK = 1;
    public static final int DONE = 2;
    public static final int ERROR = 3;

    public int type;
    public int seq;
    public String message = "";

    public PacketUploadReply() {
    }

    public PacketUploadReply(int type, int seq, String message) {
        this.type = type;
        this.seq = seq;
        this.message = message == null ? "" : message;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        type = buf.readByte();
        seq = buf.readInt();
        message = NetUtil.readString(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(type);
        buf.writeInt(seq);
        NetUtil.writeString(buf, message);
    }

    public static class Handler implements IMessageHandler<PacketUploadReply, IMessage> {
        @Override
        public IMessage onMessage(PacketUploadReply message, MessageContext ctx) {
            com.ecoserv.client.ClientHooks.onUploadReply(message);
            return null;
        }
    }
}
