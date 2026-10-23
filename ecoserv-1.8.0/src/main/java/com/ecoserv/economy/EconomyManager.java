package com.ecoserv.economy;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.PlayerUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Единая точка доступа к деньгам. Работает поверх выбранного провайдера
 * (внутренняя экономика / FTB Money / произвольный мост).
 * Если провайдер не умеет работать с офлайн-игроками, выплаты копятся
 * в pending.json и выдаются при следующем входе — поэтому блок-скупщик
 * честно платит владельцу, даже пока тот офлайн.
 */
public final class EconomyManager {

    private static final Type PENDING_TYPE = new TypeToken<HashMap<String, Long>>() {
    }.getType();

    private static final InternalEconomy INTERNAL = new InternalEconomy();
    private static IEconomy provider = INTERNAL;
    private static final Map<UUID, Long> pending = new ConcurrentHashMap<UUID, Long>();
    private static volatile boolean pendingDirty;

    private EconomyManager() {
    }

    public static void init() {
        INTERNAL.load();
        if (provider != INTERNAL) {
            provider.load();
        }
        loadPending();
    }

    public static IEconomy provider() {
        return provider;
    }

    public static InternalEconomy internal() {
        return INTERNAL;
    }

    public static void setProvider(IEconomy newProvider) {
        provider = newProvider == null ? INTERNAL : newProvider;
        EcoServ.LOG.info("Экономика: активен провайдер '{}'", provider.getName());
    }

    public static long get(UUID player) {
        try {
            return provider.get(player) + pendingOf(player);
        } catch (Throwable t) {
            EcoServ.LOG.error("Провайдер экономики упал при чтении баланса, откат на внутреннюю", t);
            setProvider(INTERNAL);
            return INTERNAL.get(player);
        }
    }

    public static long getBalance(EntityPlayer player) {
        return player == null ? 0L : get(player.getUniqueID());
    }

    public static boolean has(UUID player, long amount) {
        return get(player) >= amount;
    }

    /** Списывает деньги. Сначала тратит накопленные невыданные выплаты. */
    public static boolean take(UUID player, long amount) {
        if (amount <= 0L) {
            return amount == 0L;
        }
        if (get(player) < amount) {
            return false;
        }
        long left = amount;
        long p = pendingOf(player);
        if (p > 0L) {
            long used = Math.min(p, left);
            setPending(player, p - used);
            left -= used;
        }
        boolean ok;
        if (left <= 0L) {
            ok = true;
        } else {
            try {
                ok = provider.take(player, left);
            } catch (Throwable t) {
                EcoServ.LOG.error("Провайдер экономики упал при списании", t);
                ok = INTERNAL.take(player, left);
            }
        }
        if (ok) {
            com.ecoserv.extra.MoneyNote.record(player, -amount); // история операций (1.8.0)
        }
        return ok;
    }

    public static void give(UUID player, long amount) {
        if (player == null || amount <= 0L) {
            return;
        }
        com.ecoserv.extra.MoneyNote.record(player, amount); // история операций (1.8.0)
        if (!provider.supportsOffline() && PlayerUtil.online(player) == null) {
            setPending(player, pendingOf(player) + amount);
            return;
        }
        try {
            provider.give(player, amount);
        } catch (Throwable t) {
            EcoServ.LOG.error("Провайдер экономики упал при начислении, деньги отложены", t);
            setPending(player, pendingOf(player) + amount);
        }
    }

    public static void set(UUID player, long amount) {
        long before = 0L;
        try {
            before = get(player);
        } catch (Throwable ignored) {
        }
        com.ecoserv.extra.MoneyNote.record(player, amount - before); // история операций (1.8.0)
        setPending(player, 0L);
        try {
            provider.set(player, amount);
        } catch (Throwable t) {
            EcoServ.LOG.error("Провайдер экономики упал при установке баланса", t);
            INTERNAL.set(player, amount);
        }
    }

    /**
     * Перевод между игроками с учётом комиссии.
     *
     * @return сумма, дошедшая до получателя, или -1 при нехватке денег.
     */
    public static long transfer(UUID from, UUID to, long amount) {
        if (amount <= 0L || from == null || to == null || from.equals(to)) {
            return -1L;
        }
        boolean taken;
        com.ecoserv.extra.MoneyNote.set("Перевод → " + PlayerUtil.nameOf(to));
        try {
            taken = take(from, amount);
        } finally {
            com.ecoserv.extra.MoneyNote.clear();
        }
        if (!taken) {
            return -1L;
        }
        long tax = (long) Math.floor(amount * Math.max(0.0D, EcoConfig.payTaxPercent) / 100.0D);
        long net = Math.max(0L, amount - tax);
        com.ecoserv.extra.MoneyNote.set("Перевод от " + PlayerUtil.nameOf(from));
        try {
            give(to, net);
        } finally {
            com.ecoserv.extra.MoneyNote.clear();
        }
        return net;
    }

    public static List<Map.Entry<UUID, Long>> top(int count) {
        try {
            return provider.top(count);
        } catch (Throwable t) {
            return INTERNAL.top(count);
        }
    }

    public static String format(long amount) {
        return Fmt.money(amount);
    }

    // --- отложенные выплаты -------------------------------------------------

    public static long pendingOf(UUID player) {
        Long v = pending.get(player);
        return v == null ? 0L : v.longValue();
    }

    private static void setPending(UUID player, long amount) {
        if (amount <= 0L) {
            pending.remove(player);
        } else {
            pending.put(player, amount);
        }
        pendingDirty = true;
    }

    /** Вызывается при входе игрока: переносит накопленное в основной провайдер. */
    public static void flushPending(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueID();
        long amount = pendingOf(id);
        if (amount <= 0L) {
            return;
        }
        setPending(id, 0L);
        try {
            provider.give(id, amount);
            com.ecoserv.util.ChatUtil.send(player, "&aНачислено за время отсутствия: &e" + Fmt.money(amount));
        } catch (Throwable t) {
            setPending(id, amount);
            EcoServ.LOG.error("Не удалось выдать отложенные деньги игроку {}", player.getName(), t);
        }
    }

    private static void loadPending() {
        pending.clear();
        HashMap<String, Long> raw = JsonUtil.readType(DataStore.file("pending.json"), PENDING_TYPE, new HashMap<String, Long>());
        for (Map.Entry<String, Long> e : raw.entrySet()) {
            try {
                pending.put(UUID.fromString(e.getKey()), e.getValue() == null ? 0L : e.getValue());
            } catch (IllegalArgumentException ignored) {
                // мусорная запись
            }
        }
        pendingDirty = false;
    }

    public static void save() {
        INTERNAL.saveIfDirty();
        if (provider != INTERNAL) {
            try {
                provider.save();
            } catch (Throwable ignored) {
                // чужой провайдер сам управляет сохранением
            }
        }
        if (pendingDirty) {
            HashMap<String, Long> raw = new HashMap<String, Long>();
            for (Map.Entry<UUID, Long> e : pending.entrySet()) {
                raw.put(e.getKey().toString(), e.getValue());
            }
            if (JsonUtil.write(DataStore.file("pending.json"), raw)) {
                pendingDirty = false;
            }
        }
    }
}
