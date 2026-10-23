package com.ecoserv.util;

import com.ecoserv.config.EcoConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Форматирование чисел, денег, времени и размеров. */
public final class Fmt {

    private Fmt() {
    }

    public static String money(long amount) {
        return EcoConfig.currencySymbol + number(amount);
    }

    /** Цена с символом валюты; дробная часть показывается, только если она есть. */
    public static String money(double amount) {
        return EcoConfig.currencySymbol + price(amount);
    }

    public static String moneyFull(long amount) {
        return number(amount) + " " + EcoConfig.currencyName;
    }

    public static String number(long value) {
        String s = Long.toString(Math.abs(value));
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            sb.append(s.charAt(i));
            if (++c % 3 == 0 && i > 0) {
                sb.append(' ');
            }
        }
        if (value < 0) {
            sb.append('-');
        }
        return sb.reverse().toString();
    }

    public static String price(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return number((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static String bytes(long size) {
        if (size < 1024L) {
            return size + " Б";
        }
        double kb = size / 1024.0D;
        if (kb < 1024.0D) {
            return String.format(Locale.ROOT, "%.1f КБ", kb);
        }
        double mb = kb / 1024.0D;
        if (mb < 1024.0D) {
            return String.format(Locale.ROOT, "%.1f МБ", mb);
        }
        return String.format(Locale.ROOT, "%.2f ГБ", mb / 1024.0D);
    }

    public static String duration(long millis) {
        long sec = millis / 1000L;
        if (sec < 60L) {
            return sec + " сек";
        }
        long min = sec / 60L;
        if (min < 60L) {
            return min + " мин " + (sec % 60L) + " сек";
        }
        long hours = min / 60L;
        if (hours < 24L) {
            return hours + " ч " + (min % 60L) + " мин";
        }
        return (hours / 24L) + " д " + (hours % 24L) + " ч";
    }

    public static String minutes(int minutes) {
        if (minutes >= 60 && minutes % 60 == 0) {
            return (minutes / 60) + " ч";
        }
        return minutes + " мин";
    }

    public static String timestamp(long time) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.ROOT).format(new Date(time));
    }

    public static String fileStamp(long time) {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date(time));
    }
}
