package com.ecoserv.network.packet;

import com.ecoserv.auction.AuctionManager;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.tile.TileVendor;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Клиент -> сервер: покупка, снятие лота, выставление товара из склада. */
public class PacketAuctionAction implements IMessage {

    public static final byte BUY = 0;
    public static final byte CANCEL = 1;
    public static final byte LIST_FROM_BLOCK = 2;
    public static final byte CANCEL_FROM_BLOCK = 3;

    public byte action;
    public long id;
    public int count;
    public long pos;
    public double price;

    public PacketAuctionAction() {
    }

    public PacketAuctionAction(byte action, long id, int count, long pos, double price) {
        this.action = action;
        this.id = id;
        this.count = count;
        this.pos = pos;
        this.price = price;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        id = buf.readLong();
        count = buf.readInt();
        pos = buf.readLong();
        price = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        buf.writeLong(id);
        buf.writeInt(count);
        buf.writeLong(pos);
        buf.writeDouble(price);
    }

    public static class Handler implements IMessageHandler<PacketAuctionAction, IMessage> {
        @Override
        public IMessage onMessage(final PacketAuctionAction message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    switch (message.action) {
                        case BUY:
                            handleBuy(player, message);
                            break;
                        case CANCEL:
                            if (AuctionManager.cancel(player, message.id, Perms.isAdmin(player))) {
                                ChatUtil.send(player, "&aЛот снят, товар вернулся на склад или в почту.");
                            } else {
                                ChatUtil.send(player, "&cЛот не найден или он не твой.");
                            }
                            break;
                        case LIST_FROM_BLOCK:
                            handleList(player, message);
                            break;
                        case CANCEL_FROM_BLOCK: {
                            TileVendor vendor = vendor(player, message.pos);
                            if (vendor == null) {
                                return;
                            }
                            int removed = AuctionManager.cancelFrom(vendor, player.getUniqueID());
                            ChatUtil.send(player, removed > 0
                                    ? "&aСнято лотов: &f" + removed
                                    : "&eНечего снимать (или склад переполнен).");
                            break;
                        }
                        default:
                            break;
                    }
                }
            });
            return null;
        }

        private static TileVendor vendor(EntityPlayerMP player, long posLong) {
            BlockPos pos = BlockPos.fromLong(posLong);
            if (player.getDistanceSq(pos) > 64.0D) {
                return null;
            }
            TileEntity te = player.world.getTileEntity(pos);
            if (!(te instanceof TileVendor)) {
                return null;
            }
            TileVendor vendor = (TileVendor) te;
            if (!vendor.canAccess(player)) {
                ChatUtil.send(player, "&cЭто не твой склад.");
                return null;
            }
            return vendor;
        }

        private static void handleList(EntityPlayerMP player, PacketAuctionAction message) {
            TileVendor vendor = vendor(player, message.pos);
            if (vendor == null) {
                return;
            }
            int listed = AuctionManager.listAll(vendor, player, message.price);
            if (listed > 0) {
                ChatUtil.send(player, "&aВыставлено на аукцион: &f" + listed
                        + " &aпо &e" + Fmt.price(message.price) + " &aза штуку"
                        + (EcoConfig.auctionTaxPercent > 0.0D
                        ? " &7(комиссия " + Fmt.price(EcoConfig.auctionTaxPercent) + "%)" : ""));
            } else {
                ChatUtil.send(player, "&eСклад пуст или цена не подходит.");
            }
        }

        private static void handleBuy(EntityPlayerMP player, PacketAuctionAction message) {
            AuctionManager.Result result = AuctionManager.buy(player, message.id, Math.max(1, message.count));
            switch (result.status) {
                case OK:
                    ChatUtil.send(player, "&aКуплено: &f" + result.itemName + " x" + result.count
                            + " &7у &f" + result.sellerName + " &7за &e" + Fmt.money(result.price));
                    break;
                case NO_MONEY:
                    ChatUtil.send(player, "&cНе хватает денег: нужно " + Fmt.money(result.price));
                    break;
                case OWN_LISTING:
                    ChatUtil.send(player, "&cЭто твой собственный лот.");
                    break;
                case DISABLED:
                    ChatUtil.send(player, "&cАукцион отключён.");
                    break;
                default:
                    ChatUtil.send(player, "&cЛот уже разобрали.");
                    break;
            }
            PacketHandler.sendPlayerState(player);
        }
    }
}
