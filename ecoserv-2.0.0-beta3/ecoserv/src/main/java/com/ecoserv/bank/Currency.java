package com.ecoserv.bank;

/**
 * Валюта сервера (2.0). Основная валюта мода («money») — это обычный баланс игрока; остальные валюты
 * (кристаллы, жетоны, очки ивента…) админ создаёт сам. Курс — сколько основных денег стоит 1 единица.
 */
public class Currency {

    /** id основной валюты — баланс EconomyManager. */
    public static final String MAIN = "money";

    public String id = "";
    public String name = "";
    /** Короткий знак после суммы: «💎», «кр», «т». */
    public String symbol = "";
    /** Цвет суммы, & — код. */
    public String color = "&b";
    public String icon = "minecraft:diamond";
    public String description = "";
    /** Сколько основных денег стоит 1 единица (0 — не меняется на деньги). */
    public double rate = 0.0D;
    /** Можно ли менять в банке (в обе стороны). */
    public boolean exchangeable = true;
    /** Можно ли переводить другим игрокам. */
    public boolean transferable = true;
    /** Можно ли держать на банковских счетах. */
    public boolean bankable = true;
    public boolean enabled = true;

    public boolean main() {
        return MAIN.equals(id);
    }

    public void normalize() {
        if (id == null) {
            id = "";
        }
        if (name == null || name.isEmpty()) {
            name = id;
        }
        if (symbol == null) {
            symbol = "";
        }
        if (color == null || color.isEmpty()) {
            color = "&b";
        }
        if (icon == null || icon.isEmpty()) {
            icon = "minecraft:diamond";
        }
        if (description == null) {
            description = "";
        }
        if (Double.isNaN(rate) || rate < 0.0D) {
            rate = 0.0D;
        }
    }

    /** «125 💎» с цветом. */
    public String format(long amount) {
        if (main()) {
            return "&6" + com.ecoserv.util.Fmt.money(amount);
        }
        return color + com.ecoserv.util.Fmt.number(amount) + (symbol.isEmpty() ? " " + name : " " + symbol);
    }
}
