package com.ecoserv.ui;

import com.ecoserv.EcoServ;
import com.ecoserv.Ref;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketUi;
import com.ecoserv.util.ChatUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Открытые окна игроков: отправка, действия, автообновление. Всё — в главном потоке сервера. */
public final class UiManager {

    private static final Map<UUID, UiScreen> OPEN = new HashMap<UUID, UiScreen>();
    private static int nextWindow = 1;
    private static int tick;

    private UiManager() {
    }

    /** Есть ли у игрока клиентский мод (без него окна не нарисовать). */
    public static boolean clientHasMod(EntityPlayerMP player) {
        try {
            NetworkDispatcher d = NetworkDispatcher.get(player.connection.getNetworkManager());
            return d == null || d.getModList() == null || d.getModList().containsKey(Ref.MODID);
        } catch (Throwable t) {
            return true;
        }
    }

    public static void open(EntityPlayerMP player, UiScreen screen) {
        if (player.connection == null) {
            return;
        }
        if (!clientHasMod(player)) {
            ChatUtil.send(player, "&cЭто окно открывается только с модом EcoServ на клиенте.");
            return;
        }
        UiScreen old = OPEN.get(player.getUniqueID());
        if (old != null && old != screen) {
            safeClosed(old);
        }
        // ванильный контейнер (сундук, меню-сундук) закрываем: окно заменит его на клиенте
        boolean container = player.openContainer != player.inventoryContainer;
        if (container) {
            player.closeScreen();
        }
        // переход из открытого окна: клиент заменит содержимое на месте, без мигания
        screen.from = old != null && !container ? old.windowId : -1;
        screen.windowId = nextWindow++;
        screen.revision = 0;
        screen.recent.clear();
        OPEN.put(player.getUniqueID(), screen);
        send(screen);
    }

    public static void refresh(UiScreen screen) {
        if (OPEN.get(screen.player.getUniqueID()) == screen) {
            send(screen);
        }
    }

    public static void close(EntityPlayerMP player) {
        UiScreen old = OPEN.remove(player.getUniqueID());
        if (old != null) {
            safeClosed(old);
            PacketHandler.sendTo(PacketUi.close(old.windowId), player);
        }
    }

    /** Это окно сейчас открыто у своего игрока. */
    public static boolean isCurrent(UiScreen screen) {
        return OPEN.get(screen.player.getUniqueID()) == screen;
    }

    public static boolean isOpen(EntityPlayerMP player, Class<? extends UiScreen> type) {
        UiScreen s = OPEN.get(player.getUniqueID());
        return s != null && type.isInstance(s);
    }

    /** Обновить у всех окна этого вида (данные поменялись). */
    public static void refreshAll(Class<? extends UiScreen> type) {
        for (UiScreen s : new ArrayList<UiScreen>(OPEN.values())) {
            if (type.isInstance(s)) {
                send(s);
            }
        }
    }

    private static void send(UiScreen screen) {
        send(screen, true);
    }

    /** force = false — не слать, если окно не изменилось (автообновление). */
    private static void send(UiScreen screen, boolean force) {
        Ui ui = new Ui();
        try {
            screen.build(ui);
        } catch (Throwable t) {
            EcoServ.LOG.error("Ошибка при сборке окна {}", screen.getClass().getSimpleName(), t);
            ui = new Ui();
            ui.title("&cОшибка").text("&cОкно не собралось: " + t).footer("Закрыть", Ui.GREY, screen::close);
        }
        String toast = screen.takeToast();
        boolean toastError = screen.toastIsError();
        NBTTagCompound model = ui.finish(screen.windowId, screen.revision + 1, toast, toastError);
        if (screen.from >= 0) {
            model.setInteger("from", screen.from);
        }
        NBTTagCompound same = model.copy();
        same.removeTag("rev");
        if (!force && toast == null && same.equals(screen.lastSent)) {
            // ничего не поменялось — у клиента и так этот вид, его обработчики остаются в силе
            return;
        }
        screen.lastSent = same;
        screen.revision++;
        screen.recent.put(screen.revision, ui.handlers);
        // 1.9.0: перевод для игроков с другим языком
        com.ecoserv.lang.ServerLang.translate(model, com.ecoserv.lang.ServerLang.dictFor(screen.player));
        PacketHandler.sendTo(PacketUi.show(model), screen.player);
    }

    private static void safeClosed(UiScreen screen) {
        try {
            screen.onClosed();
        } catch (Throwable t) {
            EcoServ.LOG.warn("Ошибка при закрытии окна: {}", t.toString());
        }
    }

    /** Действие из окна (уже в главном потоке). */
    public static void handle(EntityPlayerMP player, int windowId, int revision, int id, int button, boolean shift,
                              String value) {
        UiScreen screen = OPEN.get(player.getUniqueID());
        if (screen == null) {
            return;
        }
        if (id == -1 && (screen.windowId == windowId || screen.from == windowId)) {
            // окно закрыто на клиенте (или закрыто то, из которого сервер только что перешёл в это:
            // клиент такой переход не покажет)
            OPEN.remove(player.getUniqueID());
            safeClosed(screen);
            return;
        }
        if (screen.windowId != windowId) {
            return;
        }
        if (screen.player != player) {
            // игрок возродился (новый объект игрока) — окно со старым игроком не годится
            OPEN.remove(player.getUniqueID());
            safeClosed(screen);
            PacketHandler.sendTo(PacketUi.close(screen.windowId), player);
            return;
        }
        if (!screen.canView()) {
            close(player);
            return;
        }
        List<Ui.ClickEx> handlers = screen.recent.get(revision);
        if (handlers == null || id < 0 || id >= handlers.size()) {
            // слишком старый вид окна — просто показать свежий
            send(screen);
            return;
        }
        try {
            handlers.get(id).run(new Ui.Event(button, shift, value));
        } catch (UiError e) {
            screen.setToast(e.getMessage(), true);
        } catch (Throwable t) {
            EcoServ.LOG.error("Ошибка действия в окне {}", screen.getClass().getSimpleName(), t);
            screen.setToast("Ошибка: " + t, true);
        }
        UiScreen now = OPEN.get(player.getUniqueID());
        if (now == screen) {
            send(screen);
        }
    }

    // --- события ---------------------------------------------------------------------------------

    public static class Events {

        @SubscribeEvent
        public void onTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || ++tick % 20 != 0 || OPEN.isEmpty()) {
                return;
            }
            int second = tick / 20;
            for (UiScreen s : new ArrayList<UiScreen>(OPEN.values())) {
                int every = s.refreshSeconds();
                if (every > 0 && second % every == 0) {
                    send(s, false);
                }
            }
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            UiScreen s = OPEN.remove(event.player.getUniqueID());
            if (s != null) {
                safeClosed(s);
            }
        }

        @SubscribeEvent
        public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
            // после смерти объект игрока новый, а экран смерти уже закрыл окно на клиенте
            UiScreen s = OPEN.remove(event.player.getUniqueID());
            if (s != null) {
                safeClosed(s);
            }
        }

        @SubscribeEvent
        public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            // окно остаётся открытым, но данные в нём могли устареть
            if (event.player instanceof EntityPlayerMP) {
                UiScreen s = OPEN.get(event.player.getUniqueID());
                if (s != null) {
                    send(s);
                }
            }
        }
    }

    /** Новый запуск сервера. */
    public static void reset() {
        OPEN.clear();
    }

    static Map<UUID, UiScreen> openWindows() {
        return new HashMap<UUID, UiScreen>(OPEN);
    }
}
