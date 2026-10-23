package com.ecoserv.client;

import com.ecoserv.network.packet.PacketOpenShop;
import com.ecoserv.network.packet.PacketPlayerState;
import com.ecoserv.network.packet.PacketAuctionSync;
import com.ecoserv.network.packet.PacketShopSync;
import com.ecoserv.network.packet.PacketTradeState;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Обработка серверных пакетов на клиенте (всегда в главном потоке). */
@SideOnly(Side.CLIENT)
public final class ClientHooks {

    private ClientHooks() {
    }

    /**
     * В одиночной игре и у хоста LAN сервер работает в этом же процессе и прайс/аукцион
     * у клиента и сервера — одни и те же объекты. Перезаписывать их присланной копией нельзя:
     * это гонка потоков и откат свежих изменений (например, уже купленный лот вернулся бы).
     */
    private static boolean sharesServerData() {
        return Minecraft.getMinecraft().isIntegratedServerRunning();
    }

    public static void onShopSync(final PacketShopSync message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (sharesServerData()) {
                    return;
                }
                ClientCache.acceptChunk(message.part, message.total, message.data);
            }
        });
    }

    public static void onAuctionSync(final PacketAuctionSync message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (sharesServerData()) {
                    return;
                }
                ClientCache.acceptAuctionChunk(message.part, message.total, message.data);
            }
        });
    }

    public static void onPlayerState(final PacketPlayerState message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                ClientCache.balance = message.balance;
                ClientCache.admin = message.admin;
                com.ecoserv.role.RoleManager.clientAdmin = message.admin;
                ClientCache.dailyAvailable = message.dailyAvailable;
                ClientCache.streak = message.streak;
                ClientCache.playtimeMinutes = message.playtimeMinutes;
                ClientCache.pending = message.pending;
                ClientCache.totalEarned = message.totalEarned;
                ClientCache.totalSpent = message.totalSpent;
                ClientCache.serverTaxPercent = message.taxPercent;
                ClientCache.serverSellInterval = message.sellIntervalTicks;
            }
        });
    }

    public static void onOpenShop(final PacketOpenShop message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                // старый пакет (сервер 1.7): окно магазина теперь серверное — просим открыть его
                InventoryTabHandler.openShopTab(Integer.toString(message.tab));
            }
        });
    }

    public static void onTradeState(final PacketTradeState message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                ClientCache.tradeState.sessionId = message.sessionId;
                ClientCache.tradeState.ownMoney = message.ownMoney;
                ClientCache.tradeState.otherMoney = message.otherMoney;
                ClientCache.tradeState.ownConfirm = message.ownConfirm;
                ClientCache.tradeState.otherConfirm = message.otherConfirm;
                ClientCache.tradeState.otherName = message.otherName;
            }
        });
    }

    /**
     * Измерения подмиров. Регистрация — в главном потоке клиента, в порядке прихода пакетов:
     * пакет входа в мир (JoinGame/Respawn) приходит позже и обработается уже после неё.
     */
    public static void onSubworldSync(final com.ecoserv.network.packet.PacketSubworldSync message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                com.ecoserv.subworld.SubworldDims.applyOnClient(message.full, message.views);
            }
        });
    }

    public static void onRoles(final com.ecoserv.network.packet.PacketRoles message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                // в одиночной игре сервер в этом же процессе — роли и так известны, но набор клиента отдельный
                com.ecoserv.role.RoleManager.CLIENT_ROLES.clear();
                com.ecoserv.role.RoleManager.CLIENT_ROLES.addAll(message.ids);
                ClientCache.roleTitles.clear();
                ClientCache.roleTitles.addAll(message.titles);
            }
        });
    }

    /** Окно загрузки мира, эффекты ГЛИТЧ-бездны. */
    public static void onSubworldClient(final com.ecoserv.network.packet.PacketSubworldClient message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                switch (message.action) {
                    case com.ecoserv.network.packet.PacketSubworldClient.OPEN_UPLOAD:
                        Minecraft.getMinecraft().displayGuiScreen(new GuiUploadWorld(message.data.getLong("max")));
                        break;
                    case com.ecoserv.network.packet.PacketSubworldClient.ABYSS:
                        ClientAbyss.start(message.data);
                        break;
                    case com.ecoserv.network.packet.PacketSubworldClient.ABYSS_CLEAR:
                        ClientAbyss.stop();
                        break;
                    default:
                }
            }
        });
    }

    /** Ответ сервера на загрузку мира — сразу потоку отправки (он сам по себе, не главный). */
    public static void onUploadReply(com.ecoserv.network.packet.PacketUploadReply message) {
        UploadSender.onReply(message);
    }

    /** Последнее окно, которое игрок закрыл: запоздалые обновления не должны открыть его снова. */
    private static int closedWindow = -1;

    /** -1 — отключились от сервера (номера окон на другом сервере начинаются заново). */
    public static void windowClosed(int window) {
        closedWindow = window < 0 ? -1 : Math.max(closedWindow, window);
    }

    /** Окно мода: показать, обновить или закрыть. */
    public static void onUi(final com.ecoserv.network.packet.PacketUi message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                Minecraft mc = Minecraft.getMinecraft();
                com.ecoserv.client.ui.GuiWindow current = mc.currentScreen instanceof com.ecoserv.client.ui.GuiWindow
                        ? (com.ecoserv.client.ui.GuiWindow) mc.currentScreen : null;
                if (message.close) {
                    if (current != null && current.windowId() == message.window) {
                        current.closeByServer();
                    }
                    return;
                }
                if (current != null) {
                    if (current.windowId() == message.window) {
                        current.update(message.model);
                    } else if (message.window > current.windowId()) {
                        // переход в другое окно мода — на месте, без мигания
                        current.navigate(message.model);
                    }
                    // иначе — запоздалое обновление старого окна
                    return;
                }
                int from = message.model.hasKey("from") ? message.model.getInteger("from") : -1;
                if (message.window <= closedWindow || (from >= 0 && from <= closedWindow)) {
                    // окно уже закрыто (или это переход из окна, которое игрок только что закрыл)
                    return;
                }
                mc.displayGuiScreen(new com.ecoserv.client.ui.GuiWindow(message.model));
            }
        });
    }

    /** Глитч-всплеск в мире (ГЛИТЧ-бездна). */
    public static void onBlackHole(final com.ecoserv.network.packet.PacketBlackHole message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                BlackHoleClient.onPacket(message);
            }
        });
    }

    public static void onGlitchFx(final com.ecoserv.network.packet.PacketGlitchFx message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                ClientGlitchFx.onPacket(message);
            }
        });
    }

    /** 1.9.0: отправить серверу клиентские настройки, которые ему важны (запросы, вспышки). */
    public static void sendPrefs() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getConnection() == null || mc.player == null) {
            return;
        }
        ClientSettings s = ClientSettings.get();
        com.ecoserv.network.packet.PacketClientPrefs p = new com.ecoserv.network.packet.PacketClientPrefs();
        p.trade = s.tradeRequests;
        p.money = s.moneyRequests;
        p.invites = s.inviteRequests;
        p.computers = s.computerRequests;
        p.flashes = s.flashes;
        p.lang = "auto".equals(s.modLanguage) || s.modLanguage == null || s.modLanguage.isEmpty()
                ? mc.gameSettings.language : s.modLanguage;
        com.ecoserv.network.PacketHandler.sendToServer(p);
    }
}
