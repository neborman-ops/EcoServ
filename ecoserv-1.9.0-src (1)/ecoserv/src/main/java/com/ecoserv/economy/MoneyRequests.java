package com.ecoserv.economy;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Просьбы о деньгах («можно денег?»): игрок просит у другого сумму, тот нажимает «Дать» или «Отказать».
 * Живут 5 минут, в памяти сервера.
 */
public final class MoneyRequests {

    private static final long LIFETIME = 5 * 60 * 1000L;
    /** Не чаще раза в столько миллисекунд к одному игроку. */
    private static final long COOLDOWN = 20 * 1000L;

    public static final class Request {
        public final int id;
        public final UUID from;
        public final String fromName;
        public final UUID to;
        public final String toName;
        public final long amount;
        public final String reason;
        public final long created;

        Request(int id, UUID from, String fromName, UUID to, String toName, long amount, String reason) {
            this.id = id;
            this.from = from;
            this.fromName = fromName;
            this.to = to;
            this.toName = toName;
            this.amount = amount;
            this.reason = reason;
            this.created = System.currentTimeMillis();
        }

        public long left() {
            return created + LIFETIME - System.currentTimeMillis();
        }
    }

    private static final List<Request> REQUESTS = new ArrayList<Request>();
    private static int nextId = 1;

    private MoneyRequests() {
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        REQUESTS.removeIf(r -> now - r.created > LIFETIME);
    }

    public static List<Request> incoming(UUID player) {
        prune();
        List<Request> out = new ArrayList<Request>();
        for (Request r : REQUESTS) {
            if (r.to.equals(player)) {
                out.add(r);
            }
        }
        return out;
    }

    public static List<Request> outgoing(UUID player) {
        prune();
        List<Request> out = new ArrayList<Request>();
        for (Request r : REQUESTS) {
            if (r.from.equals(player)) {
                out.add(r);
            }
        }
        return out;
    }

    /** Попросить денег. Возвращает ошибку или null. */
    @Nullable
    public static String ask(EntityPlayerMP from, UUID to, long amount, String reason) {
        prune();
        if (to.equals(from.getUniqueID())) {
            return "&cУ себя просить нельзя.";
        }
        if (!com.ecoserv.prefs.PlayerPrefs.accepts(to, from, com.ecoserv.prefs.PlayerPrefs.Kind.MONEY)) {
            return com.ecoserv.prefs.PlayerPrefs.refusal(com.ecoserv.util.PlayerUtil.nameOf(to), com.ecoserv.prefs.PlayerPrefs.Kind.MONEY);
        }
        if (amount <= 0) {
            return "&cСумма должна быть больше нуля.";
        }
        if (amount < EcoConfig.payMinAmount) {
            return "&cМинимальная сумма: &f" + Fmt.money(EcoConfig.payMinAmount);
        }
        EntityPlayerMP target = PlayerUtil.online(to);
        if (target == null) {
            return "&cИгрок не в сети — просить можно только у тех, кто в игре.";
        }
        long now = System.currentTimeMillis();
        for (Request r : REQUESTS) {
            if (r.from.equals(from.getUniqueID()) && r.to.equals(to)) {
                if (now - r.created < COOLDOWN) {
                    return "&eТы только что просил у &f" + r.toName + "&e — подожди немного.";
                }
            }
        }
        // одна просьба к одному игроку: новая заменяет старую
        REQUESTS.removeIf(r -> r.from.equals(from.getUniqueID()) && r.to.equals(to));
        String why = reason == null ? "" : reason.trim();
        if (why.length() > 80) {
            why = why.substring(0, 80);
        }
        Request req = new Request(nextId++, from.getUniqueID(), from.getName(), to, target.getName(), amount, why);
        REQUESTS.add(req);
        ChatUtil.send(target, "&e" + from.getName() + " &fпросит у тебя &6" + Fmt.money(amount)
                + (why.isEmpty() ? "" : " &7— «" + why + "»"));
        ITextComponent line = ChatUtil.component(" ");
        line.appendSibling(ChatUtil.link("&a[Дать " + Fmt.money(amount) + "]", "/eco accept " + from.getName(),
                "&aПеревести " + Fmt.money(amount) + " игроку " + from.getName()));
        line.appendSibling(ChatUtil.component(" "));
        line.appendSibling(ChatUtil.link("&c[Отказать]", "/eco deny " + from.getName(), "&cОтказать"));
        line.appendSibling(ChatUtil.component(" &8(5 минут; все просьбы: /eco requests)"));
        ChatUtil.send(target, line);
        return null;
    }

    @Nullable
    private static Request find(UUID to, @Nullable String fromName) {
        List<Request> list = incoming(to);
        if (list.isEmpty()) {
            return null;
        }
        if (fromName == null || fromName.isEmpty()) {
            return list.get(list.size() - 1);
        }
        for (Request r : list) {
            if (r.fromName.equalsIgnoreCase(fromName)) {
                return r;
            }
        }
        return null;
    }

    @Nullable
    public static Request byId(int id) {
        prune();
        for (Request r : REQUESTS) {
            if (r.id == id) {
                return r;
            }
        }
        return null;
    }

    /** Дать денег по просьбе. Возвращает ошибку или null. */
    @Nullable
    public static String accept(EntityPlayerMP player, @Nullable String fromName) {
        Request r = find(player.getUniqueID(), fromName);
        return r == null ? "&eПросьб о деньгах нет (или они истекли)." : accept(player, r);
    }

    @Nullable
    public static String accept(EntityPlayerMP player, Request r) {
        if (!REQUESTS.contains(r) || !r.to.equals(player.getUniqueID())) {
            return "&eЭта просьба уже не действует.";
        }
        if (!EcoConfig.allowPayCommand) {
            return "&cПереводы отключены на сервере.";
        }
        long received = EconomyManager.transfer(player.getUniqueID(), r.from, r.amount);
        if (received < 0L) {
            return "&cНе хватает денег: нужно &6" + Fmt.money(r.amount);
        }
        REQUESTS.remove(r);
        ChatUtil.send(player, "&aТы дал &f" + r.fromName + " &6" + Fmt.money(received)
                + (received < r.amount ? " &7(комиссия " + Fmt.money(r.amount - received) + ")" : ""));
        PacketHandler.sendPlayerState(player);
        EntityPlayerMP from = PlayerUtil.online(r.from);
        if (from != null) {
            ChatUtil.send(from, "&a" + player.getName() + " дал тебе &6" + Fmt.money(received) + "&a!");
            PacketHandler.sendPlayerState(from);
        }
        return null;
    }

    @Nullable
    public static String deny(EntityPlayerMP player, @Nullable String fromName) {
        Request r = find(player.getUniqueID(), fromName);
        return r == null ? "&eПросьб о деньгах нет." : deny(player, r);
    }

    @Nullable
    public static String deny(EntityPlayerMP player, Request r) {
        if (!REQUESTS.remove(r)) {
            return "&eЭта просьба уже не действует.";
        }
        EntityPlayerMP from = PlayerUtil.online(r.from);
        if (from != null) {
            ChatUtil.send(from, "&e" + player.getName() + " не стал давать " + Fmt.money(r.amount) + ".");
        }
        return null;
    }

    /** Отменить свою просьбу. */
    public static boolean cancel(UUID from, int id) {
        Iterator<Request> it = REQUESTS.iterator();
        while (it.hasNext()) {
            Request r = it.next();
            if (r.id == id && r.from.equals(from)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public static void forget(UUID player) {
        REQUESTS.removeIf(r -> r.from.equals(player) || r.to.equals(player));
    }

    public static void reset() {
        REQUESTS.clear();
    }
}
