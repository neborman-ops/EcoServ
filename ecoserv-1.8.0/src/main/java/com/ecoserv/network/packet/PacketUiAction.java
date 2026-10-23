package com.ecoserv.network.packet;

import com.ecoserv.network.NetUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Клиент -> сервер: действие в окне (нажатие, новое значение) или закрытие окна (id = -1). */
public class PacketUiAction implements IMessage {

    public int window;
    public int revision;
    public int id;
    public int button;
    public boolean shift;
    public String value = "";

    public PacketUiAction() {
    }

    public PacketUiAction(int window, int revision, int id, int button, boolean shift, String value) {
        this.window = window;
        this.revision = revision;
        this.id = id;
        this.button = button;
        this.shift = shift;
        this.value = value == null ? "" : value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        window = buf.readInt();
        revision = buf.readInt();
        id = buf.readInt();
        button = buf.readByte();
        shift = buf.readBoolean();
        value = NetUtil.readString(buf);
        if (value.length() > 4096) {
            value = value.substring(0, 4096);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(window);
        buf.writeInt(revision);
        buf.writeInt(id);
        buf.writeByte(button);
        buf.writeBoolean(shift);
        NetUtil.writeString(buf, value);
    }

    public static class Handler implements IMessageHandler<PacketUiAction, IMessage> {
        @Override
        public IMessage onMessage(final PacketUiAction message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    com.ecoserv.ui.UiManager.handle(player, message.window, message.revision, message.id,
                            message.button, message.shift, message.value);
                }
            });
            return null;
        }
    }
}
