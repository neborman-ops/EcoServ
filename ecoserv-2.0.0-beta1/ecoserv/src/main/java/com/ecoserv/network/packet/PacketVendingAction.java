package com.ecoserv.network.packet;

import com.ecoserv.tile.TileVending;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Клиент(владелец) -> сервер: настройка блока продажи и вывод кассы. */
public class PacketVendingAction implements IMessage {

    public static final byte SAVE = 0;
    public static final byte WITHDRAW = 1;

    public byte action;
    public long pos;
    public long price;
    public int amount;

    public PacketVendingAction() {
    }

    public PacketVendingAction(byte action, long pos, long price, int amount) {
        this.action = action;
        this.pos = pos;
        this.price = price;
        this.amount = amount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        pos = buf.readLong();
        price = buf.readLong();
        amount = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        buf.writeLong(pos);
        buf.writeLong(price);
        buf.writeInt(amount);
    }

    public static class Handler implements IMessageHandler<PacketVendingAction, IMessage> {
        @Override
        public IMessage onMessage(final PacketVendingAction message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    BlockPos pos = BlockPos.fromLong(message.pos);
                    if (player.getDistanceSq(pos) > 64.0D) {
                        return;
                    }
                    TileEntity te = player.world.getTileEntity(pos);
                    if (!(te instanceof TileVending)) {
                        return;
                    }
                    TileVending vending = (TileVending) te;
                    if (!vending.canAccess(player)) {
                        ChatUtil.send(player, "&cЭто не твой автомат.");
                        return;
                    }

                    if (message.action == SAVE) {
                        vending.configure(message.price, message.amount);
                        ChatUtil.send(player, "&aНастройки сохранены: &e" + Fmt.money(vending.getPrice())
                                + " &7за &f" + vending.getAmount() + " шт.");
                    } else if (message.action == WITHDRAW) {
                        long taken = vending.withdraw(player);
                        if (taken > 0L) {
                            ChatUtil.send(player, "&aИз кассы забрано: &e" + Fmt.money(taken));
                            com.ecoserv.network.PacketHandler.sendPlayerState(player);
                        } else {
                            ChatUtil.send(player, "&eКасса пуста.");
                        }
                    }
                }
            });
            return null;
        }
    }
}
