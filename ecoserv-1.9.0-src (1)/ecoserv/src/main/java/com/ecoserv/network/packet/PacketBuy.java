package com.ecoserv.network.packet;

import com.ecoserv.network.NetUtil;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.shop.ShopService;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Клиент -> сервер: покупка предмета из GUI магазина. */
public class PacketBuy implements IMessage {

    public String key = "";
    public int count = 1;

    public PacketBuy() {
    }

    public PacketBuy(String key, int count) {
        this.key = key;
        this.count = count;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        key = NetUtil.readString(buf);
        count = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetUtil.writeString(buf, key);
        buf.writeInt(count);
    }

    public static class Handler implements IMessageHandler<PacketBuy, IMessage> {
        @Override
        public IMessage onMessage(final PacketBuy message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    int count = Math.max(1, Math.min(message.count, 256));
                    ShopService.BuyResult result = ShopService.buy(player, message.key, count);
                    switch (result.status) {
                        case OK:
                            ChatUtil.send(player, "&aКуплено: &f" + result.itemName + " x" + result.count
                                    + " &7за &e" + Fmt.money(result.price));
                            break;
                        case NO_MONEY:
                            ChatUtil.send(player, "&cНе хватает денег: нужно " + Fmt.money(result.price));
                            break;
                        case NOT_FOR_SALE:
                            ChatUtil.send(player, "&cЭтот предмет не продаётся.");
                            break;
                        case QUEST_LOCKED:
                            ChatUtil.send(player, "&cСначала выполни нужный квест.");
                            break;
                        case DISABLED:
                            ChatUtil.send(player, "&cПокупка через GUI отключена на сервере.");
                            break;
                        default:
                            ChatUtil.send(player, "&cНеизвестный товар.");
                            break;
                    }
                    PacketHandler.sendPlayerState(player);
                }
            });
            return null;
        }
    }
}
