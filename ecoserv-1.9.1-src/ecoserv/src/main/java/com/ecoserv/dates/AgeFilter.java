package com.ecoserv.dates;

import javax.annotation.Nullable;

/**
 * Фильтр возраста для «проверки возраста»: сравнивает число лет из фильтра с возрастом
 * игрока по дню рождения. Запись: «18» или «=18» (ровно), «>=16», «<=12», «>13», «<18», «14-17».
 */
public final class AgeFilter {

    public enum Op {
        EQ("="), GE(">="), LE("<="), GT(">"), LT("<"), RANGE("-");

        final String sign;

        Op(String sign) {
            this.sign = sign;
        }
    }

    public final Op op;
    public final int years;
    /** Верхняя граница для диапазона «14-17». */
    public final int to;

    private AgeFilter(Op op, int years, int to) {
        this.op = op;
        this.years = years;
        this.to = to;
    }

    /** null — запись не распознана. Пустая строка — фильтра нет (тоже null). */
    @Nullable
    public static AgeFilter parse(@Nullable String text) {
        String s = text == null ? "" : text.trim().replace(" ", "").replace("≥", ">=").replace("≤", "<=");
        if (s.isEmpty()) {
            return null;
        }
        try {
            if (s.startsWith(">=")) {
                return new AgeFilter(Op.GE, Integer.parseInt(s.substring(2)), 0);
            }
            if (s.startsWith("<=")) {
                return new AgeFilter(Op.LE, Integer.parseInt(s.substring(2)), 0);
            }
            if (s.startsWith(">")) {
                return new AgeFilter(Op.GT, Integer.parseInt(s.substring(1)), 0);
            }
            if (s.startsWith("<")) {
                return new AgeFilter(Op.LT, Integer.parseInt(s.substring(1)), 0);
            }
            if (s.startsWith("=")) {
                return new AgeFilter(Op.EQ, Integer.parseInt(s.substring(1)), 0);
            }
            int dash = s.indexOf('-', 1);
            if (dash > 0) {
                int a = Integer.parseInt(s.substring(0, dash));
                int b = Integer.parseInt(s.substring(dash + 1));
                return new AgeFilter(Op.RANGE, Math.min(a, b), Math.max(a, b));
            }
            return new AgeFilter(Op.EQ, Integer.parseInt(s), 0);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean matches(int age) {
        if (age < 0) {
            return false;
        }
        switch (op) {
            case GE:
                return age >= years;
            case LE:
                return age <= years;
            case GT:
                return age > years;
            case LT:
                return age < years;
            case RANGE:
                return age >= years && age <= to;
            default:
                return age == years;
        }
    }

    /** Для людей: «от 16 лет», «ровно 18 лет». */
    public String describe() {
        switch (op) {
            case GE:
                return "от " + years + " лет";
            case LE:
                return "до " + years + " лет включительно";
            case GT:
                return "старше " + years + " лет";
            case LT:
                return "младше " + years + " лет";
            case RANGE:
                return "от " + years + " до " + to + " лет";
            default:
                return "ровно " + years + " лет";
        }
    }

    @Override
    public String toString() {
        return op == Op.RANGE ? years + "-" + to : (op == Op.EQ ? "" : op.sign) + years;
    }
}
