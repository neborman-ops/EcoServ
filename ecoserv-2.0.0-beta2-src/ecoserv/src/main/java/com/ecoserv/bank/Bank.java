package com.ecoserv.bank;

/**
 * Банк (2.0). Банк сервера (owner пустой) — проценты и кредиты платит сервер. Банк игрока — проценты и
 * кредиты идут из его резерва (капитала), комиссии и проценты по кредитам — в резерв. Вклады клиентов
 * лежат на их счетах: владелец банка их тронуть не может.
 */
public class Bank {

    public String id = "";
    public String name = "";
    public String description = "";
    public String icon = "minecraft:gold_block";
    /** UUID владельца; пусто — банк сервера. */
    public String owner = "";
    public String ownerName = "";

    // --- условия для клиентов ---
    public double interestPercent = 0.5D;
    public long interestCap = 100000L;
    public boolean interestOnlyOnline = true;
    public double transferFee;
    public double withdrawFee;
    public long accountPrice;
    public int maxAccounts = 3;
    public int maxMembers = 10;
    public boolean open = true;

    // --- кредиты ---
    public boolean loans;
    /** Процент в сутки на остаток долга. */
    public double loanRate = 1.0D;
    public long loanMax = 10000L;
    public int loanDays = 7;

    // --- деньги банка (только у банков игроков) ---
    public long reserve;
    public long earned;
    public long paidInterest;

    // --- служебное ---
    public boolean approved = true;
    public boolean frozen;
    public long created;

    public boolean server() {
        return owner == null || owner.isEmpty();
    }

    public void normalize() {
        if (id == null) {
            id = "";
        }
        if (name == null || name.isEmpty()) {
            name = id;
        }
        if (description == null) {
            description = "";
        }
        if (icon == null || icon.isEmpty()) {
            icon = "minecraft:gold_block";
        }
        if (owner == null) {
            owner = "";
        }
        if (ownerName == null) {
            ownerName = "";
        }
        maxAccounts = Math.max(1, maxAccounts);
        loanDays = Math.max(1, loanDays);
    }
}
