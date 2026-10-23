package com.ecoserv.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Сервер -> клиент: показать/обновить окно (описание в NBT) или закрыть его. */
public class PacketUi implements IMessage {

    public boolean close;
    public int window;
    public NBTTagCompound model = new NBTTagCompound();

    public PacketUi() {
    }

    public static PacketUi show(NBTTagCompound model) {
        PacketUi p = new PacketUi();
        p.model = model;
        p.window = model.getInteger("win");
        return p;
    }

    public static PacketUi close(int window) {
        PacketUi p = new PacketUi();
        p.close = true;
        p.window = window;
        return p;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        close = buf.readBoolean();
        window = buf.readInt();
        if (!close) {
            NBTTagCompound tag = ByteBufUtils.readTag(buf);
            model = tag == null ? new NBTTagCompound() : tag;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(close);
        buf.writeInt(window);
        if (!close) {
            ByteBufUtils.writeTag(buf, model);
        }
    }

    public static class Handler implements IMessageHandler<PacketUi, IMessage> {
        @Override
        public IMessage onMessage(PacketUi message, MessageContext ctx) {
            com.ecoserv.client.ClientHooks.onUi(message);
            return null;
        }
    }
}
