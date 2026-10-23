package com.ecoserv.shop;

import java.util.LinkedHashMap;
import java.util.Map;

/** Итог одной продажи: сколько заработано, сколько ушло в налог, что именно продано. */
public class SaleResult {

    public long gross;
    public long net;
    public long tax;
    public int itemsSold;
    /** Сколько приёмов засчитано (приём = batch штук одной позиции). */
    public int batches;
    public int stacksSold;
    public boolean limitReached;
    public boolean questBlocked;
    public final Map<String, Integer> byItem = new LinkedHashMap<String, Integer>();
    /** Позиции, которых пока меньше одного приёма: имя -> {есть, нужно}. Они остаются в блоке. */
    public final Map<String, int[]> waiting = new LinkedHashMap<String, int[]>();

    public boolean isEmpty() {
        return itemsSold == 0;
    }

    public void addWaiting(String name, int have, int need) {
        int[] prev = waiting.get(name);
        if (prev == null) {
            waiting.put(name, new int[]{have, need});
        } else {
            prev[0] += have;
        }
    }

    /** «Висмут x128, Уголь x64» — что продано. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Integer> e : byItem.entrySet()) {
            if (i++ > 0) {
                sb.append(", ");
            }
            if (i > 4) {
                sb.append("...");
                break;
            }
            sb.append(e.getKey()).append(" x").append(e.getValue());
        }
        return sb.toString();
    }

    /** «Висмут 40/64, Уголь 10/64» — чего не хватает до полного приёма. */
    public String describeWaiting() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, int[]> e : waiting.entrySet()) {
            if (i++ > 0) {
                sb.append(", ");
            }
            if (i > 3) {
                sb.append("...");
                break;
            }
            sb.append(e.getKey()).append(' ').append(e.getValue()[0]).append('/').append(e.getValue()[1]);
        }
        return sb.toString();
    }
}
