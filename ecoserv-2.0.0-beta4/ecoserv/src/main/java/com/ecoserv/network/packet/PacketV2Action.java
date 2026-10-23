package com.ecoserv.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** 2.0: клиент → сервер для новых систем (кнопки меню ESC, клики по 3D-панелям, конец катсцены…). */
public class PacketV2Action implements IMessage {

    public String type = "";
    public NBTTagCompound data = new NBTTagCompound();

    public PacketV2Action() {
    }

    public PacketV2Action(String type, NBTTagCompound data) {
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

    public static class Handler implements IMessageHandler<PacketV2Action, IMessage> {
        @Override
        public IMessage onMessage(final PacketV2Action message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> com.ecoserv.v2.V2Actions.handle(player, message.type, message.data));
            return null;
        }
    }
}
