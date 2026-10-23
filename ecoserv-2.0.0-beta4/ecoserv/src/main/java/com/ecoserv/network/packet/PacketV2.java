package com.ecoserv.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 2.0: общий пакет сервер → клиент для новых систем: тип + данные в NBT (режим разработчика, катсцены,
 * голограммы, меню ESC, свет и т.п.).
 */
public class PacketV2 implements IMessage {

    public String type = "";
    public NBTTagCompound data = new NBTTagCompound();

    public PacketV2() {
    }

    public PacketV2(String type, NBTTagCompound data) {
        this.type = type;
        this.data = data == null ? new NBTTagCompound() : data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        type = ByteBufUtils.readUTF8String(buf);
        NBTTagCompound t = ByteBufUtils.readTag(buf);
        data = t == null ? new NBTTagCompound() : t;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, type);
        ByteBufUtils.writeTag(buf, data);
    }

    public static class Handler implements IMessageHandler<PacketV2, IMessage> {
        @Override
        public IMessage onMessage(final PacketV2 message, MessageContext ctx) {
            com.ecoserv.client.v2.ClientV2.receive(message.type, message.data);
            return null;
        }
    }
}
