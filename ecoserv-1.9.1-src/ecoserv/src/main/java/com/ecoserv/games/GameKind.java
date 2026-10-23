package com.ecoserv.games;

import java.util.ArrayList;
import java.util.List;

/** Вид мини-игры: как создать игру и какие у неё настройки (для меню и своих игр). */
public abstract class GameKind {

    public static final class Param {
        public final String key;
        public final String title;
        public final String desc;
        public final int def;
        public final int min;
        public final int max;
        /** true — переключатель (0/1). */
        public final boolean bool;

        public Param(String key, String title, String desc, int def, int min, int max, boolean bool) {
            this.key = key;
            this.title = title;
            this.desc = desc;
            this.def = def;
            this.min = min;
            this.max = max;
            this.bool = bool;
        }
    }

    public final String id;
    public final String title;
    public final String description;
    public final String icon;
    /** Нужна арена в мире (иначе — игра в окне, как «Давление»). */
    public final boolean arena;
    public final List<Param> params = new ArrayList<Param>();

    protected GameKind(String id, String title, String description, String icon, boolean arena) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.arena = arena;
    }

    protected final GameKind param(String key, String title, String desc, int def, int min, int max) {
        params.add(new Param(key, title, desc, def, min, max, false));
        return this;
    }

    protected final GameKind flag(String key, String title, String desc, boolean def) {
        params.add(new Param(key, title, desc, def ? 1 : 0, 0, 1, true));
        return this;
    }

    /** Новая игра по пресету. */
    public abstract Game create(GameManager.Session session);

    /** Пресет по умолчанию (встроенный). */
    public GamePreset defaults() {
        GamePreset p = new GamePreset();
        p.id = id;
        p.kind = id;
        p.title = title;
        p.description = description;
        p.icon = icon;
        p.builtin = true;
        for (Param prm : params) {
            p.params.put(prm.key, prm.def);
        }
        return p;
    }
}
