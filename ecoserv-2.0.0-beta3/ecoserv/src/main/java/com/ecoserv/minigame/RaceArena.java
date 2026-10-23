package com.ecoserv.minigame;

import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldManager;
import com.ecoserv.subworld.SubworldRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Арены гонки: подмир с пометкой «Мини-игра». На каждую игру — своя копия шаблона (локальный мир
 * владельца «race:&lt;номер лобби&gt;»), после игры она удаляется.
 */
public final class RaceArena {

    public static final String OWNER_PREFIX = "race:";

    private RaceArena() {
    }

    /** Измерение — копия арены для игры (не шаблон: в шаблоне админы строят со своим инвентарём). */
    public static boolean isArena(int dim) {
        SubworldDim d = SubworldRegistry.dim(dim);
        return d != null && d.isInstance() && d.owner != null && d.owner.startsWith(OWNER_PREFIX);
    }

    /** Подходящие подмиры-арены. */
    public static List<Subworld> arenas() {
        List<Subworld> out = new ArrayList<Subworld>();
        for (Subworld sw : SubworldRegistry.all()) {
            if (!sw.minigame || !sw.enabled) {
                continue;
            }
            if (!ExtraConfig.minigameArena.isEmpty() && !ExtraConfig.minigameArena.equalsIgnoreCase(sw.id)) {
                continue;
            }
            out.add(sw);
        }
        return out;
    }

    @Nullable
    public static Subworld pick() {
        List<Subworld> list = arenas();
        for (Subworld sw : list) {
            if (!SubworldManager.isBusy(sw.id)) {
                return sw;
            }
        }
        return null;
    }

    /** Создать копию арены для лобби. null — не получилось. */
    @Nullable
    public static SubworldDim create(Lobby lobby, Subworld sw) {
        String owner = OWNER_PREFIX + lobby.id;
        // на всякий случай: старая копия с тем же номером (после сбоя) — удалить
        for (SubworldDim d : SubworldRegistry.instances(sw.id, owner)) {
            SubworldManager.deleteDim(d, "");
        }
        return SubworldManager.createInstance(sw, owner, "Гонка #" + lobby.id, "Гонка #" + lobby.id, null);
    }

    /** Удалить копию арены (игроков выносит туда, откуда пришли). */
    public static void delete(int dim, String message) {
        SubworldDim d = SubworldRegistry.dim(dim);
        if (d != null && isArena(dim)) {
            SubworldManager.deleteDim(d, message);
        }
    }

    /** После перезапуска сервера: копии арен прошлых игр больше не нужны. */
    public static int cleanupStale() {
        int n = 0;
        for (SubworldDim d : new ArrayList<SubworldDim>(SubworldRegistry.dims())) {
            if (d.isInstance() && d.owner != null && d.owner.startsWith(OWNER_PREFIX)) {
                SubworldManager.deleteDim(d, "&eИгра прервана перезапуском сервера.");
                n++;
            }
        }
        return n;
    }
}
