package com.ecoserv.network.packet;

import com.ecoserv.client.ClientHooks;
import com.ecoserv.network.NetUtil;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Сервер -> клиент: состояние сделки (деньги и подтверждения обеих сторон). */
public class PacketTradeState implements IMessage {

    public int sessionId;
    public long ownMoney;
    public long otherMoney;
    public boolean ownConfirm;
    public boolean otherConfirm;
    public String otherName = "";

    public PacketTradeState() {
    }

    public PacketTradeState(int sessionId, long ownMoney, long otherMoney,
                            boolean ownConfirm, boolean otherConfirm, String otherName) {
        this.sessionId = sessionId;
        this.ownMoney = ownMoney;
        this.otherMoney = otherMoney;
        this.ownConfirm = ownConfirm;
        this.otherConfirm = otherConfirm;
        this.otherName = otherName;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        sessionId = buf.readInt();
        ownMoney = buf.readLong();
        otherMoney = buf.readLong();
        ownConfirm = buf.readBoolean();
        otherConfirm = buf.readBoolean();
        otherName = NetUtil.readString(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(sessionId);
        buf.writeLong(ownMoney);
        buf.writeLong(otherMoney);
        buf.writeBoolean(ownConfirm);
        buf.writeBoolean(otherConfirm);
        NetUtil.writeString(buf, otherName);
    }

    public static class Handler implements IMessageHandler<PacketTradeState, IMessage> {
        @Override
        public IMessage onMessage(PacketTradeState message, MessageContext ctx) {
            ClientHooks.onTradeState(message);
            return null;
        }
    }
}
