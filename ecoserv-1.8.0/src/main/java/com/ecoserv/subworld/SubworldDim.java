package com.ecoserv.subworld;

import java.util.ArrayList;
import java.util.List;

/** Измерение подмира: общий мир, шаблон (правят админы) или локальный мир команды/игрока. */
public class SubworldDim {

    public static final String SHARED = "shared";
    public static final String TEMPLATE = "template";
    public static final String INSTANCE = "instance";

    public int dim;
    public String subworld = "";
    public String role = SHARED;
    /** Папка внутри ecoserv_worlds/&lt;подмир&gt;/. */
    public String folder = "";
    /** Для локальных миров: владелец «team:&lt;uid&gt;» или «player:&lt;uuid&gt;». */
    public String owner = "";
    public String ownerName = "";
    /** Название сохранения («Мир 1», «Хардкор»...). */
    public String saveName = "";
    /** Текущий радиус барьера (0 — без барьера). */
    public int border;
    /** Гости: uuid; build — могут строить. */
    public List<Guest> guests = new ArrayList<Guest>();
    public long created;
    public long lastUsed;
    /** Удалён (папка будет стёрта при следующем запуске сервера). */
    public boolean deleted;
    /** Точка появления уже подобрана (первый вход в мир). */
    public boolean spawnChecked;
    /** Версия шаблона, с которой локальный мир последний раз сверялся (глобальное редактирование). */
    public int templateVersion;
    /** Уже применённые награды «Расширение мира» (id наград), чтобы вся команда не расширяла по разу. */
    public List<String> appliedRewards = new ArrayList<String>();

    public static class Guest {
        public String uuid = "";
        public String name = "";
        public boolean build;
    }

    public void normalize() {
        if (guests == null) {
            guests = new ArrayList<Guest>();
        }
        if (appliedRewards == null) {
            appliedRewards = new ArrayList<String>();
        }
        if (role == null || role.isEmpty()) {
            role = SHARED;
        }
        if (owner == null) {
            owner = "";
        }
        if (ownerName == null) {
            ownerName = "";
        }
        if (saveName == null) {
            saveName = "";
        }
    }

    public boolean isTemplate() {
        return TEMPLATE.equals(role);
    }

    public boolean isInstance() {
        return INSTANCE.equals(role);
    }

    public Guest guest(String uuid) {
        for (Guest g : guests) {
            if (g.uuid.equals(uuid)) {
                return g;
            }
        }
        return null;
    }
}
