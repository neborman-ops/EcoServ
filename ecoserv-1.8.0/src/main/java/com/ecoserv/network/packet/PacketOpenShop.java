package com.ecoserv.network.packet;

import com.ecoserv.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Сервер -> клиент: открыть экран магазина на нужной вкладке (команда /shop). */
public class PacketOpenShop implements IMessage {

    public int tab;

    public PacketOpenShop() {
    }

    public PacketOpenShop(int tab) {
        this.tab = tab;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tab = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(tab);
    }

    public static class Handler implements IMessageHandler<PacketOpenShop, IMessage> {
        @Override
        public IMessage onMessage(PacketOpenShop message, MessageContext ctx) {
            ClientHooks.onOpenShop(message);
            return null;
        }
    }
}
