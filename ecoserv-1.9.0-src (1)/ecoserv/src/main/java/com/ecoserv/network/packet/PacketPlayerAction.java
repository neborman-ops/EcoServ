package com.ecoserv.network.packet;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.network.NetUtil;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.trade.TradeManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Клиент -> сервер: действия со вкладки «Игроки» (обмен и перевод денег). */
public class PacketPlayerAction implements IMessage {

    public static final byte TRADE_REQUEST = 0;
    public static final byte PAY = 1;

    public byte action;
    public String target = "";
    public long amount;

    public PacketPlayerAction() {
    }

    public PacketPlayerAction(byte action, String target, long amount) {
        this.action = action;
        this.target = target;
        this.amount = amount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        target = NetUtil.readString(buf);
        amount = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        NetUtil.writeString(buf, target);
        buf.writeLong(amount);
    }

    public static class Handler implements IMessageHandler<PacketPlayerAction, IMessage> {
        @Override
        public IMessage onMessage(final PacketPlayerAction message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    String name = message.target == null ? "" : message.target.trim();
                    if (name.isEmpty() || name.equalsIgnoreCase(player.getName())) {
                        return;
                    }
                    EntityPlayerMP target = PlayerUtil.online(name);

                    if (message.action == TRADE_REQUEST) {
                        if (!Perms.has(player, Perms.TRADE)) {
                            ChatUtil.send(player, "&cНет права на торговлю.");
                            return;
                        }
                        if (target == null) {
                            ChatUtil.send(player, "&cИгрок &f" + name + " &cне в сети.");
                            return;
                        }
                        TradeManager.sendRequest(player, target);
                        return;
                    }

                    if (message.action == PAY) {
                        if (!EcoConfig.allowPayCommand) {
                            ChatUtil.send(player, "&cПереводы отключены на сервере.");
                            return;
                        }
                        long amount = message.amount;
                        if (amount < EcoConfig.payMinAmount) {
                            ChatUtil.send(player, "&cМинимальная сумма перевода: &f"
                                    + Fmt.money(EcoConfig.payMinAmount));
                            return;
                        }
                        java.util.UUID uuid = PlayerUtil.uuidOf(name);
                        if (uuid == null) {
                            ChatUtil.send(player, "&cИгрок &f" + name + " &cне найден.");
                            return;
                        }
                        long received = EconomyManager.transfer(player.getUniqueID(), uuid, amount);
                        if (received < 0L) {
                            ChatUtil.send(player, "&cНе хватает денег: нужно " + Fmt.money(amount));
                            return;
                        }
                        ChatUtil.send(player, "&aПереведено &f" + name + "&a: &e" + Fmt.money(received)
                                + (received < amount ? " &7(комиссия " + Fmt.money(amount - received) + ")" : ""));
                        if (target != null) {
                            ChatUtil.send(target, "&aПолучено от &f" + player.getName() + "&a: &e"
                                    + Fmt.money(received));
                            PacketHandler.sendPlayerState(target);
                        }
                        PacketHandler.sendPlayerState(player);
                    }
                }
            });
            return null;
        }
    }
}
