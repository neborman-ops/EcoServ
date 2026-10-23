package com.ecoserv.bank;

/** Настройки банка (2.0): world/ecoserv/bank_settings.json, правятся в окне банка (вкладка «Админ»). */
public class BankSettings {

    public boolean enabled = true;
    /** Процент в сутки (реальные сутки, по серверному времени) на основную валюту. 0 — нет процентов. */
    public double interestPercent = 0.5D;
    /** Процент начисляется только на эту сумму (0 — без ограничения). */
    public long interestCap = 100000L;
    /** Процент только тем, кто заходил в эти сутки. */
    public boolean interestOnlyOnline = true;
    /** Комиссия обмена валют, %. */
    public double exchangeFee = 2.0D;
    /** Комиссия перевода со счёта на чужой счёт, %. */
    public double transferFee = 0.0D;
    /** Сколько счетов может открыть игрок. */
    public int maxAccounts = 3;
    /** Цена открытия счёта (первый бесплатно). */
    public long accountPrice = 500L;
    /** Сколько участников у общего счёта. */
    public int maxMembers = 10;
    /** Банкомат нужен для операций со счетом (иначе /bank работает везде). */
    public boolean needAtm = false;
    /** Игроки могут ставить свои банкоматы. */
    public boolean playerAtms = true;
}
