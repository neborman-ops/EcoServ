package com.ecoserv.economy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Источник баланса игрока. Реализации: внутренняя экономика мода,
 * мост к FTB Money, произвольный мост через рефлексию.
 */
public interface IEconomy {

    String getName();

    /** Можно ли менять баланс офлайн-игрока. Если нет — выплаты копятся и выдаются при входе. */
    boolean supportsOffline();

    long get(UUID player);

    void set(UUID player, long amount);

    /** true, если денег хватило и они списаны. */
    boolean take(UUID player, long amount);

    void give(UUID player, long amount);

    /** Топ балансов; может вернуть пустой список, если источник это не умеет. */
    List<Map.Entry<UUID, Long>> top(int count);

    void load();

    void save();
}
