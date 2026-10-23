package com.ecoserv.network.packet;

import com.ecoserv.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Сервер -> клиент: баланс, права, статистика игрока и текущие настройки сервера. */
public class PacketPlayerState implements IMessage {

    public long balance;
    public boolean admin;
    public boolean dailyAvailable;
    public int streak;
    public long playtimeMinutes;
    public long pending;
    public long totalEarned;
    public long totalSpent;
    public double taxPercent;
    public int sellIntervalTicks;

    public PacketPlayerState() {
    }

    public PacketPlayerState(long balance, boolean admin, boolean dailyAvailable, int streak,
                             long playtimeMinutes, long pending, long totalEarned, long totalSpent,
                             double taxPercent, int sellIntervalTicks) {
        this.balance = balance;
        this.admin = admin;
        this.dailyAvailable = dailyAvailable;
        this.streak = streak;
        this.playtimeMinutes = playtimeMinutes;
        this.pending = pending;
        this.totalEarned = totalEarned;
        this.totalSpent = totalSpent;
        this.taxPercent = taxPercent;
        this.sellIntervalTicks = sellIntervalTicks;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        balance = buf.readLong();
        admin = buf.readBoolean();
        dailyAvailable = buf.readBoolean();
        streak = buf.readInt();
        playtimeMinutes = buf.readLong();
        pending = buf.readLong();
        totalEarned = buf.readLong();
        totalSpent = buf.readLong();
        taxPercent = buf.readDouble();
        sellIntervalTicks = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(balance);
        buf.writeBoolean(admin);
        buf.writeBoolean(dailyAvailable);
        buf.writeInt(streak);
        buf.writeLong(playtimeMinutes);
        buf.writeLong(pending);
        buf.writeLong(totalEarned);
        buf.writeLong(totalSpent);
        buf.writeDouble(taxPercent);
        buf.writeInt(sellIntervalTicks);
    }

    public static class Handler implements IMessageHandler<PacketPlayerState, IMessage> {
        @Override
        public IMessage onMessage(PacketPlayerState message, MessageContext ctx) {
            ClientHooks.onPlayerState(message);
            return null;
        }
    }
}
