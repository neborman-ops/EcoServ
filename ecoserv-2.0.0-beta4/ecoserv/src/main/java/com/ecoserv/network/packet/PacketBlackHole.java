package com.ecoserv.network.packet;

import com.ecoserv.network.NetUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Сервер -> клиент: особые порталы (1.9.0) — «Чёрная дыра» и «Монолит».
 * <ul>
 * <li>LIST — порталы в мире игрока: форма, итоговые настройки (HoleParams), поворот к месту назначения;</li>
 * <li>PREVIEW — вид места назначения: настоящие блоки (id состояний), свет и биомы вокруг точки выхода,
 * сжатые deflate и разрезанные на куски (большая дальность не влезает в один пакет);</li>
 * <li>ENTITIES — игроки и мобы в месте назначения (двигаются в окне вживую).</li>
 * </ul>
 */
public class PacketBlackHole implements IMessage {

    public static final int LIST = 0;
    public static final int PREVIEW = 1;
    public static final int ENTITIES = 2;
    /** Размер куска вида. */
    public static final int PART = 480 * 1024;

    public static class Hole {
        public String name = "";
        /** 0 — чёрная дыра, 1 — монолит. */
        public int kind;
        public double x;
        public double y;
        public double z;
        public float radius;
        /** Монолит: ось-нормаль (0 X, 1 Y, 2 Z) и полуразмеры картинки. */
        public int axis;
        public float halfU;
        public float halfV;
        /** Поворот места назначения вокруг Y (четверти оборота). */
        public int turn;
        /** Есть ли куда смотреть (место назначения известно). */
        public boolean hasView;
        public float[] params = new float[0];
    }

    public static class Ent {
        public int id;
        /** 0 — игрок, 1 — моб/существо, 2 — предмет на земле. */
        public int kind;
        public String type = "";
        public String uuid = "";
        public String name = "";
        public float x;
        public float y;
        public float z;
        public float yaw;
        public float pitch;
        public float headYaw;
        public int flags;
        public ItemStack[] gear = new ItemStack[0];
    }

    public int op;
    // LIST
    public int dim;
    public List<Hole> holes = new ArrayList<Hole>();

    // PREVIEW / ENTITIES
    public String name = "";
    public int version;
    public int part;
    public int parts;
    public byte[] data = new byte[0];
    public List<Ent> ents = new ArrayList<Ent>();

    public PacketBlackHole() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        op = buf.readByte();
        if (op == LIST) {
            dim = buf.readInt();
            int n = buf.readShort();
            for (int i = 0; i < n; i++) {
                Hole h = new Hole();
                h.name = NetUtil.readString(buf);
                h.kind = buf.readByte();
                h.x = buf.readDouble();
                h.y = buf.readDouble();
                h.z = buf.readDouble();
                h.radius = buf.readFloat();
                h.axis = buf.readByte();
                h.halfU = buf.readFloat();
                h.halfV = buf.readFloat();
                h.turn = buf.readByte();
                h.hasView = buf.readBoolean();
                int pc = buf.readShort();
                h.params = new float[Math.max(0, Math.min(pc, 256))];
                for (int k = 0; k < pc; k++) {
                    float v = buf.readFloat();
                    if (k < h.params.length) {
                        h.params[k] = v;
                    }
                }
                holes.add(h);
            }
        } else if (op == PREVIEW) {
            name = NetUtil.readString(buf);
            version = buf.readInt();
            part = buf.readShort();
            parts = buf.readShort();
            int len = buf.readInt();
            if (len < 0 || len > PART * 2) {
                len = 0;
            }
            data = new byte[len];
            buf.readBytes(data);
        } else {
            name = NetUtil.readString(buf);
            int n = buf.readShort();
            for (int i = 0; i < n; i++) {
                Ent e = new Ent();
                e.id = buf.readInt();
                e.kind = buf.readByte();
                e.type = NetUtil.readString(buf);
                if (e.kind == 0) {
                    e.uuid = NetUtil.readString(buf);
                    e.name = NetUtil.readString(buf);
                }
                e.x = buf.readFloat();
                e.y = buf.readFloat();
                e.z = buf.readFloat();
                e.yaw = buf.readFloat();
                e.pitch = buf.readFloat();
                e.headYaw = buf.readFloat();
                e.flags = buf.readByte();
                int g = buf.readByte();
                e.gear = new ItemStack[Math.max(0, g)];
                for (int k = 0; k < g; k++) {
                    e.gear[k] = ByteBufUtils.readItemStack(buf);
                }
                ents.add(e);
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(op);
        if (op == LIST) {
            buf.writeInt(dim);
            buf.writeShort(holes.size());
            for (Hole h : holes) {
                NetUtil.writeString(buf, h.name);
                buf.writeByte(h.kind);
                buf.writeDouble(h.x);
                buf.writeDouble(h.y);
                buf.writeDouble(h.z);
                buf.writeFloat(h.radius);
                buf.writeByte(h.axis);
                buf.writeFloat(h.halfU);
                buf.writeFloat(h.halfV);
                buf.writeByte(h.turn);
                buf.writeBoolean(h.hasView);
                buf.writeShort(h.params.length);
                for (float v : h.params) {
                    buf.writeFloat(v);
                }
            }
        } else if (op == PREVIEW) {
            NetUtil.writeString(buf, name);
            buf.writeInt(version);
            buf.writeShort(part);
            buf.writeShort(parts);
            buf.writeInt(data.length);
            buf.writeBytes(data);
        } else {
            NetUtil.writeString(buf, name);
            buf.writeShort(ents.size());
            for (Ent e : ents) {
                buf.writeInt(e.id);
                buf.writeByte(e.kind);
                NetUtil.writeString(buf, e.type);
                if (e.kind == 0) {
                    NetUtil.writeString(buf, e.uuid);
                    NetUtil.writeString(buf, e.name);
                }
                buf.writeFloat(e.x);
                buf.writeFloat(e.y);
                buf.writeFloat(e.z);
                buf.writeFloat(e.yaw);
                buf.writeFloat(e.pitch);
                buf.writeFloat(e.headYaw);
                buf.writeByte(e.flags);
                buf.writeByte(e.gear.length);
                for (ItemStack s : e.gear) {
                    ByteBufUtils.writeItemStack(buf, s == null ? ItemStack.EMPTY : s);
                }
            }
        }
    }

    public static class Handler implements IMessageHandler<PacketBlackHole, IMessage> {
        @Override
        public IMessage onMessage(PacketBlackHole message, MessageContext ctx) {
            com.ecoserv.client.ClientHooks.onBlackHole(message);
            return null;
        }
    }
}
