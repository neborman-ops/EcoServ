package com.ecoserv.cc;

import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Запросы компьютеров к игрокам: «купить товар» и «оплатить». Компьютер не может сам списать чужие деньги —
 * игрок получает в чат кнопки [Подтвердить] / [Отказаться], а компьютер — событие с итогом
 * (ecoserv_purchase / ecoserv_payment). Запрос живёт cc.request_seconds секунд.
 */
public final class CcRequests {

    /** Что сделать, когда игрок нажал «Подтвердить» (в главном потоке). Возвращает аргументы события. */
    public interface Action {
        Object[] run(EntityPlayerMP player);
    }

    /** Отправить событие компьютеру. */
    public interface Notifier {
        void fire(String event, Object[] args);
    }

    public static final class Request {
        public final int id;
        public final UUID player;
        public final String playerName;
        public final String event;
        public final String text;
        final Action action;
        final Notifier notifier;
        final long expires;
        public String status = "pending";

        Request(int id, EntityPlayerMP player, String event, String text, Action action, Notifier notifier) {
            this.id = id;
            this.player = player.getUniqueID();
            this.playerName = player.getName();
            this.event = event;
            this.text = text;
            this.action = action;
            this.notifier = notifier;
            this.expires = System.currentTimeMillis() + ExtraConfig.ccRequestSeconds * 1000L;
        }
    }

    private static final Map<Integer, Request> REQUESTS = new LinkedHashMap<Integer, Request>();
    private static int nextId = 1;

    private CcRequests() {
    }

    /** Новый запрос: игроку — кнопки в чат. */
    public static synchronized Request create(EntityPlayerMP player, String event, String text, Action action, Notifier notifier) {
        if (!com.ecoserv.prefs.PlayerPrefs.computers(player.getUniqueID())) {
            throw new IllegalStateException("Игрок не принимает запросы компьютеров (клиентские настройки)");
        }
        // не больше 3 ожидающих запросов на игрока — компьютер не заспамит
        int pending = 0;
        for (Request r : REQUESTS.values()) {
            if (r.player.equals(player.getUniqueID()) && "pending".equals(r.status)) {
                pending++;
            }
        }
        if (pending >= 3) {
            throw new IllegalStateException("У игрока уже 3 неподтверждённых запроса");
        }
        Request r = new Request(nextId++, player, event, text, action, notifier);
        REQUESTS.put(r.id, r);
        ITextComponent msg = new TextComponentString(ChatUtil.prefix() + ChatUtil.color("&b[Компьютер] &f" + text + " "));
        msg.appendSibling(ChatUtil.link("&a[Подтвердить]", "/ecocc confirm " + r.id, "&aСогласиться"));
        msg.appendSibling(new TextComponentString(" "));
        msg.appendSibling(ChatUtil.link("&c[Отказаться]", "/ecocc deny " + r.id, "&cОтказаться"));
        player.sendMessage(msg);
        return r;
    }

    @Nullable
    public static synchronized Request get(int id) {
        return REQUESTS.get(id);
    }

    public static synchronized void answer(EntityPlayerMP player, int id, boolean yes) {
        Request r = REQUESTS.get(id);
        if (r == null || !r.player.equals(player.getUniqueID()) || !"pending".equals(r.status)) {
            ChatUtil.send(player, "&7Этот запрос уже не действует.");
            return;
        }
        if (!yes) {
            r.status = "denied";
            notify(r, new Object[]{"denied", r.playerName});
            ChatUtil.send(player, "&7Отказано.");
            return;
        }
        Object[] result;
        try {
            result = r.action.run(player);
        } catch (IllegalStateException e) {
            r.status = "failed";
            ChatUtil.send(player, "&c" + e.getMessage());
            notify(r, new Object[]{"failed", r.playerName, e.getMessage()});
            return;
        }
        r.status = "done";
        notify(r, result);
    }

    public static synchronized void cancel(int id) {
        Request r = REQUESTS.get(id);
        if (r != null && "pending".equals(r.status)) {
            r.status = "cancelled";
        }
    }

    private static void notify(Request r, Object[] args) {
        Object[] full = new Object[args.length + 1];
        full[0] = r.id;
        System.arraycopy(args, 0, full, 1, args.length);
        try {
            r.notifier.fire(r.event, full);
        } catch (Throwable ignored) {
            // компьютер выключен или отключён от блока
        }
    }

    /** Раз в секунду: просроченные запросы. */
    public static synchronized void tick() {
        long now = System.currentTimeMillis();
        Iterator<Request> it = REQUESTS.values().iterator();
        while (it.hasNext()) {
            Request r = it.next();
            if ("pending".equals(r.status) && now > r.expires) {
                r.status = "expired";
                notify(r, new Object[]{"expired", r.playerName});
                EntityPlayerMP p = PlayerUtil.online(r.player);
                if (p != null) {
                    ChatUtil.send(p, "&7Запрос компьютера истёк: " + r.text);
                }
            }
            if (!"pending".equals(r.status) && now > r.expires + 300_000L) {
                it.remove();
            }
        }
    }

    public static String money(long v) {
        return Fmt.money(v);
    }
}
