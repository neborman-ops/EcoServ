package com.ecoserv.bank;

/** Кредит (2.0): банк выдал деньги, долг растёт на процент в сутки и списывается частями каждый день. */
public class Loan {
    public String id = "";
    public String bank = "";
    public String borrower = "";
    public String borrowerName = "";
    public long amount;
    /** Сколько ещё должен. */
    public long remaining;
    public double rate;
    public long created;
    /** День (эпоха), когда должен вернуть всё. */
    public long dueDay;
    public long lastDay;
    public int missed;
    public boolean defaulted;
    public long paid;
}
