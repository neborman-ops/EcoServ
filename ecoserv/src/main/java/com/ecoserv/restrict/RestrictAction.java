package com.ecoserv.restrict;

import javax.annotation.Nullable;
import java.util.Locale;

/** Что именно запрещено делать с предметом. */
public enum RestrictAction {

    CRAFT("craft", "Крафт"),
    USE("use", "Использование"),
    PLACE("place", "Установка блоком"),
    CARRY("carry", "Перенос (носить с собой)");

    public final String id;
    public final String title;

    RestrictAction(String id, String title) {
        this.id = id;
        this.title = title;
    }

    @Nullable
    public static RestrictAction byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        String s = id.trim().toLowerCase(Locale.ROOT);
        for (RestrictAction a : values()) {
            if (a.id.equals(s)) {
                return a;
            }
        }
        return null;
    }
}
