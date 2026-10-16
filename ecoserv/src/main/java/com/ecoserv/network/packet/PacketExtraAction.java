package com.ecoserv.network.packet;

import com.ecoserv.extra.Bank;
import com.ecoserv.extra.ExtraSync;
import com.ecoserv.util.ChatUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Клиент → сервер: запрос состояния, положить/снять со вклада. */
public class PacketExtraAction implements IMessage {
   public static final byte REQUEST = 0;
   public static final byte DEPOSIT = 1;
   public static final byte WITHDRAW = 2;
   public byte action;
   public long amount;

   public PacketExtraAction() {
   }

   public PacketExtraAction(byte action, long amount) {
      this.action = action;
      this.amount = amount;
   }

   public void fromBytes(ByteBuf buf) {
      this.action = buf.readByte();
      this.amount = buf.readLong();
   }

   public void toBytes(ByteBuf buf) {
      buf.writeByte(this.action);
      buf.writeLong(this.amount);
   }

   public static class Handler implements IMessageHandler<PacketExtraAction, IMessage> {
      public IMessage onMessage(final PacketExtraAction message, MessageContext ctx) {
         final EntityPlayerMP player = ctx.getServerHandler().player;
         player.getServerWorld().addScheduledTask(new Runnable() {
            @Override
            public void run() {
               if (message.action == 1) {
                  ChatUtil.send(player, Bank.deposit(player, message.amount));
               } else if (message.action == 2) {
                  ChatUtil.send(player, Bank.withdraw(player, message.amount));
               }

               ExtraSync.send(player);
            }
         });
         return null;
      }
   }
}
