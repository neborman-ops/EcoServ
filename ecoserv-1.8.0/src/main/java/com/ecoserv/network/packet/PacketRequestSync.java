package com.ecoserv.network.packet;

import com.ecoserv.network.PacketHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Клиент -> сервер: «пришли мне актуальные данные». */
public class PacketRequestSync implements IMessage {

    public boolean withShop = true;

    public PacketRequestSync() {
    }

    public PacketRequestSync(boolean withShop) {
        this.withShop = withShop;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        withShop = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(withShop);
    }

    public static class Handler implements IMessageHandler<PacketRequestSync, IMessage> {
        @Override
        public IMessage onMessage(final PacketRequestSync message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    PacketHandler.sendPlayerState(player);
                    if (message.withShop) {
                        PacketHandler.sendShopList(player);
                        PacketHandler.sendAuction(player);
                    }
                }
            });
            return null;
        }
    }
}
