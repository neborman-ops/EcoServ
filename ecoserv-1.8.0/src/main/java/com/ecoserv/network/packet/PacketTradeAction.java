package com.ecoserv.network.packet;

import com.ecoserv.trade.TradeManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Клиент -> сервер: действия в окне обмена. */
public class PacketTradeAction implements IMessage {

    public static final byte SET_MONEY = 0;
    public static final byte CONFIRM = 1;
    public static final byte UNCONFIRM = 2;
    public static final byte CANCEL = 3;

    public byte action;
    public long value;

    public PacketTradeAction() {
    }

    public PacketTradeAction(byte action, long value) {
        this.action = action;
        this.value = value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        value = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        buf.writeLong(value);
    }

    public static class Handler implements IMessageHandler<PacketTradeAction, IMessage> {
        @Override
        public IMessage onMessage(final PacketTradeAction message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    switch (message.action) {
                        case SET_MONEY:
                            TradeManager.setMoney(player, Math.max(0L, message.value));
                            break;
                        case CONFIRM:
                            TradeManager.setConfirm(player, true);
                            break;
                        case UNCONFIRM:
                            TradeManager.setConfirm(player, false);
                            break;
                        case CANCEL:
                            TradeManager.abortFor(player.getUniqueID(), "отменено игроком " + player.getName());
                            break;
                        default:
                            break;
                    }
                }
            });
            return null;
        }
    }
}
