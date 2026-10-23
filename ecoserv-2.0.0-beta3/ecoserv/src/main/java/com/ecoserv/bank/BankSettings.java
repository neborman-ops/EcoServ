package com.ecoserv.bank;

/** Общие настройки банков (2.0): world/ecoserv/bank_settings.json. Условия каждого банка — в самом банке. */
public class BankSettings {

    public boolean enabled = true;
    /** Комиссия обмена валют, %. */
    public double exchangeFee = 2.0D;
    /** Игроки могут открывать свои банки. */
    public boolean playerBanks = true;
    /** Цена лицензии банка игрока. */
    public long bankLicense = 50000L;
    /** Сколько банков у одного игрока. */
    public int maxBanksPerPlayer = 1;
    /** Новый банк игрока работает только после одобрения админа. */
    public boolean needApproval = false;
    /** Банк игрока не может принимать новых клиентов, пока резерв меньше. */
    public long minReserve = 10000L;
    /** Максимальный процент по вкладам и кредитам, который может поставить игрок. */
    public double maxPlayerInterest = 3.0D;
    public double maxPlayerLoanRate = 5.0D;
    /** Операции со счетами — только у банкомата. */
    public boolean needAtm = false;
    /** Игроки ставят свои банкоматы. */
    public boolean playerAtms = true;
    /** Должник (просрочил кредит) не может брать новые кредиты нигде. */
    public boolean blockDebtors = true;

    // старые поля (1 банк на сервер) — переносятся в «Центральный банк»
    public Double interestPercent;
    public Long interestCap;
    public Boolean interestOnlyOnline;
    public Double transferFee;
    public Integer maxAccounts;
    public Long accountPrice;
    public Integer maxMembers;
}
