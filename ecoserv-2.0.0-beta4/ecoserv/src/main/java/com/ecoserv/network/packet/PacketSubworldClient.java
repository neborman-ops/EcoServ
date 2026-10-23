package com.ecoserv.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Сервер -> клиент: открыть окно загрузки мира, эффекты ГЛИТЧ-бездны. */
public class PacketSubworldClient implements IMessage {

    public static final int OPEN_UPLOAD = 0;
    public static final int ABYSS = 1;
    public static final int ABYSS_CLEAR = 2;

    public int action;
    public NBTTagCompound data = new NBTTagCompound();

    public PacketSubworldClient() {
    }

    public PacketSubworldClient(int action, NBTTagCompound data) {
        this.action = action;
        this.data = data == null ? new NBTTagCompound() : data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        data = tag == null ? new NBTTagCompound() : tag;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        ByteBufUtils.writeTag(buf, data);
    }

    public static class Handler implements IMessageHandler<PacketSubworldClient, IMessage> {
        @Override
        public IMessage onMessage(PacketSubworldClient message, MessageContext ctx) {
            com.ecoserv.client.ClientHooks.onSubworldClient(message);
            return null;
        }
    }
}
