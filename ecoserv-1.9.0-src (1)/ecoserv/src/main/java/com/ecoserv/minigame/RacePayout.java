package com.ecoserv.minigame;

import java.util.ArrayList;
import java.util.List;

/**
 * Делёж банка «Квестовой гонки». До 10 игроков призовых мест три: 50% / 30% / 20%. Больше игроков —
 * больше мест (30% участников), а доли пересчитываются так, что у первого места всегда больше всех,
 * дальше — плавно меньше. Остальные теряют ставку.
 * <p>
 * Защита от «поставил минимум — забрал половину чужих ставок»: выигрыш одного игрока не больше
 * ставка × число игроков (как если бы все поставили столько же). Лишнее делится между остальными
 * призёрами, а если и им некуда — возвращается всем пропорционально ставкам.
 * Места получают только те, кто хоть что-то сделал (прошёл хотя бы шаг); если никто — ставки возвращаются.
 */
public final class RacePayout {

    private RacePayout() {
    }

    /** Сколько призовых мест при n игроках. */
    public static int places(int n) {
        if (n <= 1) {
            return 1;
        }
        if (n <= 3) {
            return n - 1;
        }
        if (n <= 10) {
            return 3;
        }
        return Math.max(3, (int) Math.round(n * 0.3D));
    }

    /** Веса мест: первое 2.5, остальные плавно от 1.5 до 1.0 (для трёх мест ровно 50/30/20). */
    static double[] weights(int places) {
        double[] w = new double[Math.max(1, places)];
        w[0] = 2.5D;
        for (int i = 1; i < w.length; i++) {
            w[i] = w.length == 2 ? 1.5D : 1.5D - 0.5D * (i - 1) / (w.length - 2);
        }
        return w;
    }

    /** Доли мест в процентах (для таблицы в окне). */
    public static double[] percents(int players) {
        double[] w = weights(places(players));
        double sum = 0;
        for (double x : w) {
            sum += x;
        }
        double[] p = new double[w.length];
        for (int i = 0; i < w.length; i++) {
            p[i] = w[i] * 100.0D / sum;
        }
        return p;
    }

    /**
     * @param stakes   ставки игроков в порядке итоговых мест (0 — первое место)
     * @param eligible может ли игрок занять призовое место (сделал хоть что-то)
     * @param cutPercent комиссия сервера, %
     * @return выплата каждому игроку (в том же порядке)
     */
    public static long[] split(long[] stakes, boolean[] eligible, double cutPercent) {
        int n = stakes.length;
        long[] out = new long[n];
        long pot = 0;
        for (long s : stakes) {
            pot += Math.max(0, s);
        }
        if (n == 0 || pot <= 0) {
            return out;
        }
        List<Integer> winners = new ArrayList<Integer>();
        int want = places(n);
        for (int i = 0; i < n && winners.size() < want; i++) {
            if (eligible[i]) {
                winners.add(i);
            }
        }
        if (winners.isEmpty()) {
            // никто ничего не сделал — всем вернуть
            System.arraycopy(stakes, 0, out, 0, n);
            return out;
        }
        long cut = (long) Math.floor(pot * Math.max(0.0D, Math.min(50.0D, cutPercent)) / 100.0D);
        long prize = pot - cut;
        double[] w = weights(winners.size());
        double[] share = new double[n];
        double[] cap = new double[n];
        boolean[] capped = new boolean[n];
        for (int k = 0; k < winners.size(); k++) {
            cap[winners.get(k)] = (double) Math.max(0, stakes[winners.get(k)]) * n;
        }
        double left = prize;
        // несколько проходов: выплаты сверх потолка уходят остальным призёрам по весам
        for (int pass = 0; pass < winners.size() + 1 && left > 0.5D; pass++) {
            double wsum = 0;
            for (int k = 0; k < winners.size(); k++) {
                if (!capped[winners.get(k)]) {
                    wsum += w[k];
                }
            }
            if (wsum <= 0) {
                break;
            }
            double spread = left;
            left = 0;
            for (int k = 0; k < winners.size(); k++) {
                int i = winners.get(k);
                if (capped[i]) {
                    continue;
                }
                double add = spread * w[k] / wsum;
                if (share[i] + add >= cap[i]) {
                    left += share[i] + add - cap[i];
                    share[i] = cap[i];
                    capped[i] = true;
                } else {
                    share[i] += add;
                }
            }
        }
        if (left > 0.5D) {
            // все призёры упёрлись в потолок — остаток всем по ставкам
            for (int i = 0; i < n; i++) {
                share[i] += left * Math.max(0, stakes[i]) / pot;
            }
        }
        long given = 0;
        for (int i = 0; i < n; i++) {
            out[i] = (long) Math.floor(share[i]);
            given += out[i];
        }
        // копейки от округления — первому призёру
        out[winners.get(0)] += prize - given;
        return out;
    }
}
