package com.ecoserv.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Сервер -> клиент: глитч-всплеск в мире (ГЛИТЧ-бездна) — частицы, искажение пространства и звук
 * в точке, где игрок упал или появился. Видят все игроки рядом.
 */
public class PacketGlitchFx implements IMessage {

    /** Игрок провалился в бездну: воронка и искажение. */
    public static final int FALL = 0;
    /** Игрок вернулся на спавн: вспышка. */
    public static final int ARRIVE = 1;

    public int kind;
    public double x;
    public double y;
    public double z;
    /** Рисовать ли искажение пространства («чёрная дыра»). */
    public boolean distort;
    /** Сам упавший (ему — без искажения, у него свои эффекты). */
    public boolean self;

    public PacketGlitchFx() {
    }

    public PacketGlitchFx(int kind, double x, double y, double z, boolean distort, boolean self) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.distort = distort;
        this.self = self;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        kind = buf.readByte();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        distort = buf.readBoolean();
        self = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(kind);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeBoolean(distort);
        buf.writeBoolean(self);
    }

    public static class Handler implements IMessageHandler<PacketGlitchFx, IMessage> {
        @Override
        public IMessage onMessage(PacketGlitchFx message, MessageContext ctx) {
            com.ecoserv.client.ClientHooks.onGlitchFx(message);
            return null;
        }
    }
}
