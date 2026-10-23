package com.ecoserv.bank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Банковский счёт (2.0): личный или общий (команда, компания, клан). Деньги на счёте не тратятся, пока
 * их не снимут; на них может капать процент. У общего счёта есть участники с правами.
 */
public class BankAccount {

    /** Права участника общего счёта (по возрастанию). */
    public static final String VIEW = "view";
    public static final String DEPOSIT = "deposit";
    public static final String WITHDRAW = "withdraw";
    public static final String MANAGE = "manage";

    public static final class Op {
        public long time;
        public String who = "";
        /** deposit, withdraw, in, out, interest, exchange, fee. */
        public String kind = "";
        public String currency = Currency.MAIN;
        public long amount;
        public String note = "";
    }

    /** Код счёта для переводов: 6 символов. */
    public String code = "";
    public String name = "";
    public String owner = "";
    public String ownerName = "";
    public boolean shared;
    /** uuid -> право. */
    public Map<String, String> members = new LinkedHashMap<String, String>();
    public Map<String, String> memberNames = new LinkedHashMap<String, String>();
    /** валюта -> сумма. */
    public Map<String, Long> balances = new LinkedHashMap<String, Long>();
    public List<Op> history = new ArrayList<Op>();
    public long created;
    /** День (эпоха/сутки), за который уже начислен процент. */
    public long interestDay;
    public long interestTotal;
    /** Заморожен админом. */
    public boolean frozen;

    public void normalize() {
        if (code == null) {
            code = "";
        }
        if (name == null || name.isEmpty()) {
            name = code;
        }
        if (owner == null) {
            owner = "";
        }
        if (ownerName == null) {
            ownerName = "";
        }
        if (members == null) {
            members = new LinkedHashMap<String, String>();
        }
        if (memberNames == null) {
            memberNames = new LinkedHashMap<String, String>();
        }
        if (balances == null) {
            balances = new LinkedHashMap<String, Long>();
        }
        if (history == null) {
            history = new ArrayList<Op>();
        }
    }

    public long balance(String currency) {
        Long v = balances.get(currency);
        return v == null ? 0L : v;
    }

    void add(String currency, long amount) {
        long now = balance(currency) + amount;
        if (now == 0L) {
            balances.remove(currency);
        } else {
            balances.put(currency, now);
        }
    }

    /** Уровень права игрока: 0 — нет доступа, 1 view, 2 deposit, 3 withdraw, 4 manage (владелец — 4). */
    public int level(String uuid) {
        if (uuid == null) {
            return 0;
        }
        if (uuid.equals(owner)) {
            return 4;
        }
        String r = members.get(uuid);
        return levelOf(r);
    }

    public static int levelOf(String right) {
        if (right == null) {
            return 0;
        }
        switch (right) {
            case MANAGE:
                return 4;
            case WITHDRAW:
                return 3;
            case DEPOSIT:
                return 2;
            case VIEW:
                return 1;
            default:
                return 0;
        }
    }

    public static String rightTitle(String right) {
        switch (right == null ? "" : right) {
            case MANAGE:
                return "управляет";
            case WITHDRAW:
                return "снимает и кладёт";
            case DEPOSIT:
                return "только кладёт";
            case VIEW:
                return "только смотрит";
            default:
                return "нет доступа";
        }
    }

    void log(String who, String kind, String currency, long amount, String note) {
        Op op = new Op();
        op.time = System.currentTimeMillis();
        op.who = who == null ? "" : who;
        op.kind = kind;
        op.currency = currency;
        op.amount = amount;
        op.note = note == null ? "" : note;
        history.add(0, op);
        while (history.size() > 60) {
            history.remove(history.size() - 1);
        }
    }
}
