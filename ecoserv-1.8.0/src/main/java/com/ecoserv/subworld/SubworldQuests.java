package com.ecoserv.subworld;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Связь подмиров с FTB Quests (свои квесты подмира). Реализацию подключает интеграция с FTB Quests,
 * если мод есть; без него подмиры работают, просто без своих глав.
 */
public final class SubworldQuests {

    /** Что нужно подмирам от FTB Quests на сервере. */
    public interface Bridge {
        /** Главы: [id в hex, название]. */
        List<String[]> chapters();
    }

    @Nullable
    private static Bridge bridge;

    private SubworldQuests() {
    }

    public static void setBridge(@Nullable Bridge b) {
        bridge = b;
    }

    public static boolean available() {
        return bridge != null;
    }

    public static List<String[]> chapters() {
        if (bridge == null) {
            return Collections.emptyList();
        }
        try {
            return new ArrayList<String[]>(bridge.chapters());
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    /** Подмир со своими квестами, которому принадлежит глава (первый найденный), или null. */
    @Nullable
    public static Subworld subworldOfChapter(String chapterId) {
        for (Subworld sw : SubworldRegistry.all()) {
            if (sw.ownQuests && sw.questChapters.contains(chapterId)) {
                return sw;
            }
        }
        return null;
    }
}
