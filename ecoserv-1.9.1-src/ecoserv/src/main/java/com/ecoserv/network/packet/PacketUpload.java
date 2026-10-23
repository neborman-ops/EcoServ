package com.ecoserv.network.packet;

import com.ecoserv.network.NetUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Клиент -> сервер: загрузка своего мира из игры админа (п. 3, до 2 ГБ) по частям.
 * Обработка данных — прямо в сетевом потоке (запись в файл), без нагрузки на главный поток сервера.
 */
public class PacketUpload implements IMessage {

    public static final int START = 0;
    public static final int DATA = 1;
    public static final int END = 2;
    public static final int CANCEL = 3;
    /** Сколько байт данных в одном пакете (лимит пакета клиента — 32 КБ). */
    public static final int CHUNK = 30000;

    public int type;
    public int seq;
    public String name = "";
    public long total;
    public String path = "";
    public long offset;
    public byte[] data = new byte[0];

    public PacketUpload() {
    }

    public static PacketUpload start(String name, long total, int files) {
        PacketUpload p = new PacketUpload();
        p.type = START;
        p.name = name;
        p.total = total;
        p.seq = files;
        return p;
    }

    public static PacketUpload data(int seq, String path, long offset, byte[] data) {
        PacketUpload p = new PacketUpload();
        p.type = DATA;
        p.seq = seq;
        p.path = path;
        p.offset = offset;
        p.data = data;
        return p;
    }

    public static PacketUpload end(int seq) {
        PacketUpload p = new PacketUpload();
        p.type = END;
        p.seq = seq;
        return p;
    }

    public static PacketUpload cancel() {
        PacketUpload p = new PacketUpload();
        p.type = CANCEL;
        return p;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        type = buf.readByte();
        seq = buf.readInt();
        switch (type) {
            case START:
                name = NetUtil.readString(buf);
                total = buf.readLong();
                break;
            case DATA:
                path = NetUtil.readString(buf);
                offset = buf.readLong();
                int n = buf.readUnsignedShort();
                if (n > CHUNK || n > buf.readableBytes()) {
                    throw new IllegalArgumentException("upload chunk too big");
                }
                data = new byte[n];
                buf.readBytes(data);
                break;
            default:
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(type);
        buf.writeInt(seq);
        switch (type) {
            case START:
                NetUtil.writeString(buf, name);
                buf.writeLong(total);
                break;
            case DATA:
                NetUtil.writeString(buf, path);
                buf.writeLong(offset);
                buf.writeShort(data.length);
                buf.writeBytes(data);
                break;
            default:
        }
    }

    public static class Handler implements IMessageHandler<PacketUpload, IMessage> {
        @Override
        public IMessage onMessage(PacketUpload message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            com.ecoserv.subworld.UploadReceiver.onPacket(player, message);
            return null;
        }
    }
}
