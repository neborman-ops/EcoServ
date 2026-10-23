package com.ecoserv.network.packet;

import com.ecoserv.network.NetUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Клиент -> сервер: кнопка-планета (меню миров), «ОК» в окне ENGINE ERROR. */
public class PacketSubworldAction implements IMessage {

    public static final int OPEN_MENU = 0;
    public static final int ABYSS_OK = 1;
    /** Открыть окно магазина; arg — вкладка (AUCTION, PLAYERS, …). */
    public static final int OPEN_SHOP = 2;

    public int action;
    public String arg = "";

    public PacketSubworldAction() {
    }

    public PacketSubworldAction(int action, String arg) {
        this.action = action;
        this.arg = arg == null ? "" : arg;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        arg = NetUtil.readString(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        NetUtil.writeString(buf, arg);
    }

    public static class Handler implements IMessageHandler<PacketSubworldAction, IMessage> {
        @Override
        public IMessage onMessage(final PacketSubworldAction message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    com.ecoserv.subworld.SubworldActions.handle(player, message.action, message.arg);
                }
            });
            return null;
        }
    }
}
