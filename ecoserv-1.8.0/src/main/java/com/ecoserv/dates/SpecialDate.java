package com.ecoserv.dates;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;

/** Значимая дата: праздник, день сервера и т.п. Хранится в config/ecoserv/dates.json. */
public class SpecialDate {

    public String id = "";
    public String name = "";
    public int day = 1;
    public int month = 1;
    /** Сколько дней длится (награду можно забрать в любой из них). */
    public int days = 1;
    public long money;
    /** Предметы в закодированном виде (ItemCodec). */
    public List<String> items = new ArrayList<String>();
    /** Сообщение игроку при получении награды; пусто — стандартное. */
    public String message = "";
    /** Объявить в чат в начале даты. */
    public boolean broadcast = true;
    public boolean enabled = true;

    public void normalize() {
        if (items == null) {
            items = new ArrayList<String>();
        }
        if (name == null || name.isEmpty()) {
            name = id;
        }
        if (message == null) {
            message = "";
        }
        month = Math.max(1, Math.min(12, month));
        day = Math.max(1, Math.min(31, day));
        days = Math.max(1, Math.min(366, days));
        money = Math.max(0L, money);
    }

    public String dateText() {
        return String.format("%02d.%02d", day, month);
    }

    /**
     * Если сегодня дата действует — год, к которому относится этот «выпуск» даты
     * (для даты 31.12 на 3 дня 1 января относится к прошлому году), иначе -1.
     */
    public int activeYear(LocalDate today) {
        MonthDay md;
        try {
            md = MonthDay.of(month, day);
        } catch (Exception e) {
            return -1;
        }
        for (int y = today.getYear(); y >= today.getYear() - 1; y--) {
            LocalDate start = md.atYear(y);
            LocalDate end = start.plusDays(days - 1L);
            if (!today.isBefore(start) && !today.isAfter(end)) {
                return y;
            }
        }
        return -1;
    }
}
