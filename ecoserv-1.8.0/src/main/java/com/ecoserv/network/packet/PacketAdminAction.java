package com.ecoserv.network.packet;

import com.ecoserv.backup.BackupManager;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.integration.Integrations;
import com.ecoserv.network.NetUtil;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.server.Announcer;
import com.ecoserv.server.ItemBlacklist;
import com.ecoserv.server.LagReport;
import com.ecoserv.server.RestartScheduler;
import com.ecoserv.shop.ShopList;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.Perms;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;

/** Клиент(админ) -> сервер: кнопки серверной панели. */
public class PacketAdminAction implements IMessage {

    public static final byte BACKUP_NOW = 0;
    public static final byte BACKUP_PRUNE = 1;
    public static final byte RESTART = 2;
    public static final byte CANCEL_RESTART = 3;
    public static final byte RELOAD = 4;
    public static final byte ANNOUNCE = 5;
    public static final byte BLACKLIST_ADD_HELD = 6;
    public static final byte BLACKLIST_REMOVE = 7;
    public static final byte LAG_REPORT = 8;
    public static final byte SET_CONFIG = 9;
    public static final byte SAVE_ALL = 10;

    public byte action;
    public String arg = "";
    public int value;

    public PacketAdminAction() {
    }

    public PacketAdminAction(byte action, String arg, int value) {
        this.action = action;
        this.arg = arg;
        this.value = value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        arg = NetUtil.readString(buf);
        value = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        NetUtil.writeString(buf, arg);
        buf.writeInt(value);
    }

    public static class Handler implements IMessageHandler<PacketAdminAction, IMessage> {
        @Override
        public IMessage onMessage(final PacketAdminAction message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (!Perms.isAdmin(player)) {
                        ChatUtil.send(player, "&cНет прав администратора.");
                        return;
                    }
                    switch (message.action) {
                        case BACKUP_NOW:
                            BackupManager.scheduleWithWarning(player);
                            break;
                        case BACKUP_PRUNE:
                            ChatUtil.send(player, "&aУдалено старых бэкапов: &f" + BackupManager.prune()
                                    + " &7(осталось " + BackupManager.listBackups().size()
                                    + ", " + Fmt.bytes(BackupManager.totalSize()) + ")");
                            break;
                        case RESTART:
                            RestartScheduler.startManualCountdown(Math.max(1, message.value), player);
                            break;
                        case CANCEL_RESTART:
                            RestartScheduler.cancelCountdown(player);
                            break;
                        case RELOAD:
                            com.ecoserv.server.ServerServices.reloadAll();
                            ChatUtil.send(player, "&aКонфиг, прайс-лист, чёрный список и запреты по мирам перезагружены.");
                            break;
                        case ANNOUNCE:
                            if (message.arg != null && !message.arg.trim().isEmpty()) {
                                ChatUtil.broadcast(message.arg);
                            }
                            break;
                        case BLACKLIST_ADD_HELD: {
                            ItemStack held = player.getHeldItemMainhand();
                            if (held.isEmpty()) {
                                ChatUtil.send(player, "&cВозьми предмет в руку.");
                                return;
                            }
                            String key = ItemKey.of(held, false, true).serialize();
                            ItemBlacklist.add(key);
                            ChatUtil.send(player, "&aВ чёрный список: &f" + held.getDisplayName() + " &7(" + key + ")");
                            break;
                        }
                        case BLACKLIST_REMOVE:
                            if (ItemBlacklist.remove(message.arg)) {
                                ChatUtil.send(player, "&aУдалено из чёрного списка: &f" + message.arg);
                            } else {
                                ChatUtil.send(player, "&cНе найдено: " + message.arg);
                            }
                            break;
                        case LAG_REPORT: {
                            List<String> lines = LagReport.build(10);
                            for (int i = 0; i < lines.size(); i++) {
                                ChatUtil.sendRaw(player, lines.get(i));
                            }
                            break;
                        }
                        case SET_CONFIG: {
                            String[] parts = message.arg == null ? new String[0] : message.arg.split("\\|", 3);
                            if (parts.length < 3) {
                                ChatUtil.send(player, "&cФормат: категория|ключ|значение");
                                return;
                            }
                            if (EcoConfig.set(parts[0], parts[1], parts[2])) {
                                ChatUtil.send(player, "&aУстановлено &f" + parts[0] + "." + parts[1] + " = " + parts[2]);
                            } else {
                                ChatUtil.send(player, "&cНе удалось применить значение: " + parts[2]);
                            }
                            break;
                        }
                        case SAVE_ALL:
                            com.ecoserv.server.ServerServices.saveAll(true);
                            ChatUtil.send(player, "&aДанные сохранены.");
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
