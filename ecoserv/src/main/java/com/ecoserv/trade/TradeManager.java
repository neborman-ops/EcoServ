package com.ecoserv.trade;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.gui.GuiHandler;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketTradeState;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Запросы на обмен, активные сделки и их завершение. */
public final class TradeManager {

    private static class Request {
        UUID from;
        String fromName;
        long expires;
    }

    private static final Map<Integer, TradeSession> sessions = new HashMap<Integer, TradeSession>();
    private static final Map<UUID, Integer> playerSession = new HashMap<UUID, Integer>();
    private static final Map<UUID, Request> requests = new HashMap<UUID, Request>();
    private static int nextId = 1;

    private TradeManager() {
    }

    // --- запросы -----------------------------------------------------------

    public static void sendRequest(EntityPlayerMP from, EntityPlayerMP to) {
        if (!EcoConfig.tradeEnabled) {
            ChatUtil.send(from, "&cТорговля между игроками отключена.");
            return;
        }
        if (from == to) {
            ChatUtil.send(from, "&cСам с собой не поторгуешь.");
            return;
        }
        if (isTrading(from.getUniqueID()) || isTrading(to.getUniqueID())) {
            ChatUtil.send(from, "&cКто-то из вас уже в сделке.");
            return;
        }
        if (!checkDistance(from, to, true)) {
            return;
        }

        Request request = new Request();
        request.from = from.getUniqueID();
        request.fromName = from.getName();
        request.expires = System.currentTimeMillis() + EcoConfig.tradeRequestTimeoutSeconds * 1000L;
        requests.put(to.getUniqueID(), request);

        ChatUtil.send(from, "&aЗапрос отправлен игроку &f" + to.getName() + "&a. Ожидание "
                + EcoConfig.tradeRequestTimeoutSeconds + " сек.");

        ITextComponent msg = new TextComponentString(ChatUtil.prefix() + ChatUtil.color(
                "&e" + from.getName() + " предлагает обмен. "));
        ITextComponent accept = new TextComponentString(ChatUtil.color("&a[Принять]"));
        accept.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept " + from.getName()));
        accept.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TextComponentString(ChatUtil.color("&aОткрыть окно обмена"))));
        ITextComponent space = new TextComponentString(" ");
        ITextComponent deny = new TextComponentString(ChatUtil.color("&c[Отклонить]"));
        deny.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade deny"));

        msg.appendSibling(accept).appendSibling(space).appendSibling(deny);
        to.sendMessage(msg);
    }

    public static void accept(EntityPlayerMP target, @Nullable String fromName) {
        Request request = requests.get(target.getUniqueID());
        if (request == null || System.currentTimeMillis() > request.expires) {
            requests.remove(target.getUniqueID());
            ChatUtil.send(target, "&cНет активных запросов на обмен.");
            return;
        }
        if (fromName != null && !fromName.isEmpty() && !fromName.equalsIgnoreCase(request.fromName)) {
            ChatUtil.send(target, "&cЗапрос от &f" + fromName + " &cне найден.");
            return;
        }
        EntityPlayerMP from = PlayerUtil.online(request.from);
        requests.remove(target.getUniqueID());
        if (from == null) {
            ChatUtil.send(target, "&cИгрок вышел с сервера.");
            return;
        }
        if (isTrading(from.getUniqueID()) || isTrading(target.getUniqueID())) {
            ChatUtil.send(target, "&cКто-то из вас уже в сделке.");
            return;
        }
        if (!checkDistance(from, target, true)) {
            return;
        }
        open(from, target);
    }

    public static void deny(EntityPlayerMP target) {
        Request request = requests.remove(target.getUniqueID());
        if (request == null) {
            ChatUtil.send(target, "&cНет активных запросов.");
            return;
        }
        ChatUtil.send(target, "&eЗапрос отклонён.");
        EntityPlayerMP from = PlayerUtil.online(request.from);
        if (from != null) {
            ChatUtil.send(from, "&c" + target.getName() + " отклонил обмен.");
        }
    }

    private static boolean checkDistance(EntityPlayerMP a, EntityPlayerMP b, boolean notify) {
        if (EcoConfig.tradeSameDimension && a.dimension != b.dimension) {
            if (notify) {
                ChatUtil.send(a, "&cИгрок в другом измерении.");
            }
            return false;
        }
        if (EcoConfig.tradeMaxDistance > 0.0D && a.dimension == b.dimension) {
            double dx = a.posX - b.posX;
            double dy = a.posY - b.posY;
            double dz = a.posZ - b.posZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > EcoConfig.tradeMaxDistance) {
                if (notify) {
                    ChatUtil.send(a, "&cСлишком далеко: " + (int) dist + " блоков (нужно не больше "
                            + (int) EcoConfig.tradeMaxDistance + ").");
                }
                return false;
            }
        }
        return true;
    }

    // --- сессии ------------------------------------------------------------

    public static void open(EntityPlayerMP a, EntityPlayerMP b) {
        TradeSession session = new TradeSession(nextId++, a, b);
        sessions.put(session.id, session);
        playerSession.put(a.getUniqueID(), session.id);
        playerSession.put(b.getUniqueID(), session.id);

        a.openGui(EcoServ.INSTANCE, GuiHandler.GUI_TRADE, a.world, session.id, 0, 0);
        b.openGui(EcoServ.INSTANCE, GuiHandler.GUI_TRADE, b.world, session.id, 0, 0);
        syncState(session);
        ChatUtil.send(a, "&aОбмен с &f" + b.getName() + "&a начат.");
        ChatUtil.send(b, "&aОбмен с &f" + a.getName() + "&a начат.");
    }

    @Nullable
    public static TradeSession get(int id) {
        return sessions.get(id);
    }

    @Nullable
    public static TradeSession of(@Nullable UUID player) {
        if (player == null) {
            return null;
        }
        Integer id = playerSession.get(player);
        return id == null ? null : sessions.get(id);
    }

    public static boolean isTrading(UUID player) {
        return of(player) != null;
    }

    public static void setMoney(EntityPlayerMP player, long amount) {
        TradeSession session = of(player.getUniqueID());
        if (session == null || session.finished) {
            return;
        }
        if (!EcoConfig.tradeAllowMoney) {
            return;
        }
        long max = EconomyManager.get(player.getUniqueID());
        long value = Math.max(0L, Math.min(amount, max));
        if (session.isA(player.getUniqueID())) {
            session.moneyA = value;
        } else {
            session.moneyB = value;
        }
        session.resetConfirm();
        syncState(session);
    }

    public static void setConfirm(EntityPlayerMP player, boolean confirm) {
        TradeSession session = of(player.getUniqueID());
        if (session == null || session.finished) {
            return;
        }
        if (confirm && System.currentTimeMillis() - session.lastChange < EcoConfig.tradeConfirmDelayMillis) {
            ChatUtil.send(player, "&eПредложение только что изменилось — проверь ещё раз.");
            syncState(session);
            return;
        }
        if (session.isA(player.getUniqueID())) {
            session.confirmA = confirm;
        } else {
            session.confirmB = confirm;
        }
        syncState(session);
        if (session.bothConfirmed()) {
            complete(session);
        }
    }

    public static void syncState(TradeSession session) {
        sendState(session, session.uuidA);
        sendState(session, session.uuidB);
    }

    private static void sendState(TradeSession session, @Nullable UUID uuid) {
        EntityPlayerMP player = PlayerUtil.online(uuid);
        if (player == null) {
            return;
        }
        PacketHandler.sendTo(new PacketTradeState(session.id,
                session.ownMoney(uuid), session.otherMoney(uuid),
                session.ownConfirm(uuid), session.otherConfirm(uuid),
                session.otherName(uuid)), player);
    }

    /** Завершение сделки: деньги и предметы меняются местами атомарно. */
    private static void complete(TradeSession session) {
        if (session.finished) {
            return;
        }
        EntityPlayerMP a = PlayerUtil.online(session.uuidA);
        EntityPlayerMP b = PlayerUtil.online(session.uuidB);
        if (a == null || b == null) {
            abort(session, "игрок вышел");
            return;
        }
        if (!checkDistance(a, b, false)) {
            abort(session, "игроки разошлись слишком далеко");
            return;
        }
        if (session.moneyA > 0L && !EconomyManager.has(session.uuidA, session.moneyA)) {
            abort(session, "у " + session.nameA + " не хватает денег");
            return;
        }
        if (session.moneyB > 0L && !EconomyManager.has(session.uuidB, session.moneyB)) {
            abort(session, "у " + session.nameB + " не хватает денег");
            return;
        }

        session.finished = true;

        if (session.moneyA > 0L) {
            EconomyManager.take(session.uuidA, session.moneyA);
            EconomyManager.give(session.uuidB, session.moneyA);
        }
        if (session.moneyB > 0L) {
            EconomyManager.take(session.uuidB, session.moneyB);
            EconomyManager.give(session.uuidA, session.moneyB);
        }

        List<String> itemsAtoB = moveAll(session.offerA, b);
        List<String> itemsBtoA = moveAll(session.offerB, a);

        close(session);

        ChatUtil.send(a, "&aОбмен завершён. Получено: &f" + describe(itemsBtoA, session.moneyB));
        ChatUtil.send(b, "&aОбмен завершён. Получено: &f" + describe(itemsAtoB, session.moneyA));
        log(session, itemsAtoB, itemsBtoA);
    }

    private static String describe(List<String> items, long money) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size() && i < 6; i++) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(items.get(i));
        }
        if (items.size() > 6) {
            sb.append(", ...");
        }
        if (money > 0L) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(Fmt.money(money));
        }
        return sb.length() == 0 ? "ничего" : sb.toString();
    }

    private static List<String> moveAll(net.minecraftforge.items.ItemStackHandler handler, EntityPlayerMP to) {
        List<String> names = new ArrayList<String>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            names.add(stack.getDisplayName() + " x" + stack.getCount());
            PlayerUtil.giveOrDrop(to, stack);
            handler.setStackInSlot(i, ItemStack.EMPTY);
        }
        return names;
    }

    /** Отмена сделки: всё возвращается владельцам. */
    public static void abort(@Nullable TradeSession session, String reason) {
        if (session == null || session.finished) {
            return;
        }
        session.finished = true;

        EntityPlayerMP a = PlayerUtil.online(session.uuidA);
        EntityPlayerMP b = PlayerUtil.online(session.uuidB);
        returnItems(session.offerA, session.uuidA);
        returnItems(session.offerB, session.uuidB);
        close(session);

        String msg = "&eОбмен отменён" + (reason == null || reason.isEmpty() ? "." : ": &f" + reason);
        if (a != null) {
            ChatUtil.send(a, msg);
        }
        if (b != null) {
            ChatUtil.send(b, msg);
        }
    }

    public static void abortFor(@Nullable UUID player, String reason) {
        abort(of(player), reason);
    }

    private static void returnItems(net.minecraftforge.items.ItemStackHandler handler, @Nullable UUID owner) {
        EntityPlayerMP player = PlayerUtil.online(owner);
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            handler.setStackInSlot(i, ItemStack.EMPTY);
            if (player != null) {
                PlayerUtil.giveOrDrop(player, stack);
            } else {
                // игрок офлайн — кладём в «почту» до входа
                TradeMailbox.store(owner, stack);
            }
        }
    }

    private static void close(TradeSession session) {
        sessions.remove(session.id);
        if (session.uuidA != null) {
            playerSession.remove(session.uuidA);
        }
        if (session.uuidB != null) {
            playerSession.remove(session.uuidB);
        }
        closeScreen(session.uuidA);
        closeScreen(session.uuidB);
    }

    private static void closeScreen(@Nullable UUID uuid) {
        EntityPlayerMP player = PlayerUtil.online(uuid);
        if (player != null && player.openContainer instanceof ContainerTrade) {
            player.closeScreen();
        }
    }

    /** Таймауты запросов и проверка расстояния. Вызывается раз в секунду. */
    public static void tick() {
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Request>> it = requests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Request> e = it.next();
            if (now > e.getValue().expires) {
                it.remove();
                EntityPlayerMP from = PlayerUtil.online(e.getValue().from);
                if (from != null) {
                    ChatUtil.send(from, "&eЗапрос на обмен истёк.");
                }
            }
        }

        if (sessions.isEmpty()) {
            return;
        }
        List<TradeSession> list = new ArrayList<TradeSession>(sessions.values());
        for (int i = 0; i < list.size(); i++) {
            TradeSession session = list.get(i);
            EntityPlayerMP a = PlayerUtil.online(session.uuidA);
            EntityPlayerMP b = PlayerUtil.online(session.uuidB);
            if (a == null || b == null) {
                abort(session, "игрок вышел");
                continue;
            }
            if (!checkDistance(a, b, false)) {
                abort(session, "игроки слишком далеко друг от друга");
            }
        }
    }

    public static void onLogout(UUID player) {
        abortFor(player, "игрок вышел");
        requests.remove(player);
    }

    public static void shutdown() {
        List<TradeSession> list = new ArrayList<TradeSession>(sessions.values());
        for (int i = 0; i < list.size(); i++) {
            abort(list.get(i), "сервер останавливается");
        }
        sessions.clear();
        playerSession.clear();
        requests.clear();
    }

    private static void log(TradeSession session, List<String> aToB, List<String> bToA) {
        if (!EcoConfig.tradeLog) {
            return;
        }
        Writer writer = null;
        try {
            File file = DataStore.file("trades.log");
            writer = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8);
            writer.write("[" + Fmt.timestamp(System.currentTimeMillis()) + "] "
                    + session.nameA + " -> " + session.nameB + ": " + aToB + " + " + session.moneyA + " | "
                    + session.nameB + " -> " + session.nameA + ": " + bToA + " + " + session.moneyB + "\r\n");
        } catch (IOException e) {
            EcoServ.LOG.error("Не удалось записать лог обмена", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                    // ничего
                }
            }
        }
    }
}
