package com.ecoserv.network.packet;

import com.ecoserv.network.NetUtil;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Сервер → клиент: вклад, зарплата, звания, история операций (для окна магазина). */
public class PacketExtraState implements IMessage {
   public boolean bankEnabled;
   public long bank;
   public long interestTotal;
   public double interestPercent;
   public long interestCap;
   public long interestNext;
   public boolean interestDoneToday;
   public int onlineMinutesToday;
   public int needOnlineMinutes;
   public long salaryAmount;
   public int salaryMinutes;
   public long salaryLeftMs = -1L;
   public String salaryRole = "";
   public String nextRank = "";
   public long playtimeMs;
   public final List<long[]> txTimeAmount = new ArrayList<>();
   public final List<String> txNotes = new ArrayList<>();

   public PacketExtraState() {
   }

   public void fromBytes(ByteBuf buf) {
      this.bankEnabled = buf.readBoolean();
      this.bank = buf.readLong();
      this.interestTotal = buf.readLong();
      this.interestPercent = buf.readDouble();
      this.interestCap = buf.readLong();
      this.interestNext = buf.readLong();
      this.interestDoneToday = buf.readBoolean();
      this.onlineMinutesToday = buf.readInt();
      this.needOnlineMinutes = buf.readInt();
      this.salaryAmount = buf.readLong();
      this.salaryMinutes = buf.readInt();
      this.salaryLeftMs = buf.readLong();
      this.salaryRole = NetUtil.readString(buf);
      this.nextRank = NetUtil.readString(buf);
      this.playtimeMs = buf.readLong();
      int n = Math.min(500, Math.max(0, buf.readShort()));

      for (int i = 0; i < n; i++) {
         this.txTimeAmount.add(new long[]{buf.readLong(), buf.readLong()});
         this.txNotes.add(NetUtil.readString(buf));
      }
   }

   public void toBytes(ByteBuf buf) {
      buf.writeBoolean(this.bankEnabled);
      buf.writeLong(this.bank);
      buf.writeLong(this.interestTotal);
      buf.writeDouble(this.interestPercent);
      buf.writeLong(this.interestCap);
      buf.writeLong(this.interestNext);
      buf.writeBoolean(this.interestDoneToday);
      buf.writeInt(this.onlineMinutesToday);
      buf.writeInt(this.needOnlineMinutes);
      buf.writeLong(this.salaryAmount);
      buf.writeInt(this.salaryMinutes);
      buf.writeLong(this.salaryLeftMs);
      NetUtil.writeString(buf, this.salaryRole == null ? "" : this.salaryRole);
      NetUtil.writeString(buf, this.nextRank == null ? "" : this.nextRank);
      buf.writeLong(this.playtimeMs);
      int n = Math.min(500, this.txTimeAmount.size());
      buf.writeShort(n);

      for (int i = 0; i < n; i++) {
         buf.writeLong(this.txTimeAmount.get(i)[0]);
         buf.writeLong(this.txTimeAmount.get(i)[1]);
         String note = this.txNotes.get(i);
         NetUtil.writeString(buf, note == null ? "" : note);
      }
   }

   public static class Handler implements IMessageHandler<PacketExtraState, IMessage> {
      public IMessage onMessage(PacketExtraState message, MessageContext ctx) {
         com.ecoserv.client.ClientHooksExtra.onExtraState(message);
         return null;
      }
   }
}
