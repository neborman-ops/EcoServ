package com.ecoserv.network.packet;

import com.ecoserv.EcoServ;
import com.ecoserv.network.NetUtil;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.shop.ShopEntry;
import com.ecoserv.shop.ShopList;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.Perms;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Клиент(админ) -> сервер: правка прайс-листа. */
public class PacketShopEdit implements IMessage {

    public static final byte OP_PUT = 0;
    public static final byte OP_REMOVE = 1;
    public static final byte OP_CLEAR = 2;
    public static final byte OP_SORT = 3;

    public byte op;
    public String key = "";
    public String json = "";

    public PacketShopEdit() {
    }

    public PacketShopEdit(byte op, String key, String json) {
        this.op = op;
        this.key = key;
        this.json = json;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        op = buf.readByte();
        key = NetUtil.readString(buf);
        json = NetUtil.readString(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(op);
        NetUtil.writeString(buf, key);
        NetUtil.writeString(buf, json);
    }

    public static class Handler implements IMessageHandler<PacketShopEdit, IMessage> {
        @Override
        public IMessage onMessage(final PacketShopEdit message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (!Perms.isAdmin(player) && !Perms.has(player, Perms.SHOP_EDIT)) {
                        ChatUtil.send(player, "&cНет прав на редактирование магазина.");
                        return;
                    }
                    // админ в подмире со своими квестами правит прайс этого подмира
                    ShopList shop = ShopList.forPlayer(player);
                    switch (message.op) {
                        case OP_PUT:
                            try {
                                ShopEntry entry = JsonUtil.GSON.fromJson(message.json, ShopEntry.class);
                                if (entry == null || entry.key == null || !entry.itemKey().isValid()) {
                                    ChatUtil.send(player, "&cНекорректная позиция.");
                                    return;
                                }
                                // клиенту не доверяем: приём 1..(18 слотов терминала), цена >= 0
                                int maxBatch = com.ecoserv.tile.TileMarket.SIZE * 64;
                                entry.batch = Math.max(1, Math.min(maxBatch, entry.batch));
                                if (Double.isNaN(entry.sellPrice) || Double.isInfinite(entry.sellPrice)
                                        || entry.sellPrice < 0.0D) {
                                    entry.sellPrice = 0.0D;
                                }
                                if (Double.isNaN(entry.buyPrice) || Double.isInfinite(entry.buyPrice)
                                        || entry.buyPrice < 0.0D) {
                                    entry.buyPrice = 0.0D;
                                }
                                if (entry.category == null) {
                                    entry.category = "";
                                }
                                if (entry.requiredQuest == null) {
                                    entry.requiredQuest = "";
                                }
                                shop.addOrReplace(entry);
                                ChatUtil.send(player, "&aСохранено: &f" + entry.displayName()
                                        + (entry.canSell()
                                        ? " &7— приём &f" + entry.batchSize() + " шт &7-> &e"
                                        + com.ecoserv.util.Fmt.money(entry.sellPrice)
                                        : " &7— &cне принимается"));
                            } catch (Exception e) {
                                EcoServ.LOG.error("Ошибка разбора позиции прайса", e);
                                ChatUtil.send(player, "&cОшибка: " + e);
                                return;
                            }
                            break;
                        case OP_REMOVE:
                            if (shop.remove(message.key)) {
                                ChatUtil.send(player, "&aПозиция удалена.");
                            } else {
                                ChatUtil.send(player, "&cПозиция не найдена.");
                                return;
                            }
                            break;
                        case OP_CLEAR:
                            shop.clear();
                            ChatUtil.send(player, "&eПрайс-лист очищен.");
                            break;
                        case OP_SORT:
                            shop.sortByName();
                            ChatUtil.send(player, "&aПрайс-лист отсортирован.");
                            break;
                        default:
                            return;
                    }
                    shop.save();
                    PacketHandler.sendShopListToScope(shop);
                }
            });
            return null;
        }
    }
}
