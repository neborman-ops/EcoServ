package com.ecoserv.network.packet;

import com.ecoserv.network.NetUtil;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.tile.TileMarket;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

/** Клиент -> сервер: кнопки в GUI блока-скупщика. */
public class PacketMarketAction implements IMessage {

    public static final byte SELL_NOW = 0;
    public static final byte TOGGLE_AUTO = 1;
    public static final byte SET_OWNER = 2;
    public static final byte SET_OWNER_SELF = 3;

    public BlockPos pos = BlockPos.ORIGIN;
    public byte action;
    public String arg = "";

    public PacketMarketAction() {
    }

    public PacketMarketAction(BlockPos pos, byte action, String arg) {
        this.pos = pos;
        this.action = action;
        this.arg = arg;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        action = buf.readByte();
        arg = NetUtil.readString(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(action);
        NetUtil.writeString(buf, arg);
    }

    public static class Handler implements IMessageHandler<PacketMarketAction, IMessage> {
        @Override
        public IMessage onMessage(final PacketMarketAction message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (player.getDistanceSq(message.pos) > 64.0D) {
                        return;
                    }
                    TileEntity te = player.world.getTileEntity(message.pos);
                    if (!(te instanceof TileMarket)) {
                        return;
                    }
                    TileMarket market = (TileMarket) te;
                    if (!market.canAccess(player)) {
                        ChatUtil.send(player, "&cНет доступа к этому терминалу.");
                        return;
                    }
                    boolean isOwner = market.isOwner(player);
                    boolean admin = Perms.isAdmin(player);

                    switch (message.action) {
                        case SELL_NOW:
                            market.sell(player);
                            PacketHandler.sendPlayerState(player);
                            break;
                        case TOGGLE_AUTO:
                            if (!isOwner && !admin) {
                                ChatUtil.send(player, "&cТолько владелец может это менять.");
                                return;
                            }
                            market.setAutoSell(!market.isAutoSell());
                            ChatUtil.send(player, market.isAutoSell()
                                    ? "&aАвтопродажа включена." : "&eАвтопродажа выключена.");
                            break;
                        case SET_OWNER_SELF:
                            if (!isOwner && !admin) {
                                ChatUtil.send(player, "&cТолько владелец или админ может сменить получателя.");
                                return;
                            }
                            market.setOwner(player.getUniqueID(), player.getName());
                            ChatUtil.send(player, "&aТеперь деньги идут тебе.");
                            break;
                        case SET_OWNER:
                            if (!admin && !isOwner) {
                                ChatUtil.send(player, "&cНет прав.");
                                return;
                            }
                            String name = message.arg == null ? "" : message.arg.trim();
                            if (name.isEmpty()) {
                                market.setOwner(null, "");
                                ChatUtil.send(player, "&eВладелец сброшен.");
                                return;
                            }
                            if (!admin && !name.equalsIgnoreCase(player.getName())) {
                                ChatUtil.send(player, "&cНазначить другого игрока может только админ.");
                                return;
                            }
                            UUID uuid = PlayerUtil.uuidOf(name);
                            if (uuid == null) {
                                ChatUtil.send(player, "&cИгрок &f" + name + " &cне найден (он должен был хоть раз зайти).");
                                return;
                            }
                            market.setOwner(uuid, name);
                            ChatUtil.send(player, "&aПолучатель денег: &f" + name);
                            break;
                        default:
                            break;
                    }
                }
            });
            return null;
        }
    }
}
