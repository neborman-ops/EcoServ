package com.ecoserv.role;

import java.util.ArrayList;
import java.util.List;

/**
 * Роль (тег) игрока: VIP, модератор, строитель... Хранится в config/ecoserv/roles.json.
 * Роль даёт доступ к списку команд, правам мода, телепорту по карте Xaero,
 * мирам для /world и множитель наград.
 */
public class Role {

    /** Латиница, цифры, _ (используется в командах и в барьере ролей). */
    public String id = "";
    /** Название для людей: «VIP», «Модератор». */
    public String name = "";
    /** Цвет названия (&-код). */
    public String color = "&f";
    /** Тег перед ником в чате, например «&6[VIP]» (пусто — без тега). */
    public String prefix = "";
    /** Чем больше, тем выше в списке; тег в чате берётся у роли с наибольшим приоритетом. */
    public int priority;
    /** Команды, доступные с этой ролью даже без OP (имена без «/»: tp, gamemode, time). */
    public List<String> commands = new ArrayList<String>();
    /** Права мода и Forge PermissionAPI (ecoserv.world, ecoserv.shop.edit ...). «*» — все права мода. */
    public List<String> permissions = new ArrayList<String>();
    /** Телепорт по карте Xaero (карта шлёт «/tp @p x y z»). */
    public boolean xaeroTp;
    /** Дополнительные миры для /world (имена или номера). «*» — любые. */
    public List<String> worlds = new ArrayList<String>();
    /** Множитель денежных наград за игру и ежедневки (1.0 — без изменений). */
    public double rewardMultiplier = 1.0D;
    /** Описание для списка ролей. */
    public String description = "";

    public Role() {
    }

    public Role(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String title() {
        return color + (name == null || name.isEmpty() ? id : name);
    }

    public void normalize() {
        if (commands == null) {
            commands = new ArrayList<String>();
        }
        if (permissions == null) {
            permissions = new ArrayList<String>();
        }
        if (worlds == null) {
            worlds = new ArrayList<String>();
        }
        if (name == null) {
            name = id;
        }
        if (color == null) {
            color = "&f";
        }
        if (prefix == null) {
            prefix = "";
        }
        if (description == null) {
            description = "";
        }
        if (Double.isNaN(rewardMultiplier) || rewardMultiplier < 0.0D) {
            rewardMultiplier = 1.0D;
        }
        List<String> clean = new ArrayList<String>();
        for (String c : commands) {
            String s = c == null ? "" : c.trim().toLowerCase();
            while (s.startsWith("/")) {
                s = s.substring(1);
            }
            if (!s.isEmpty() && !clean.contains(s)) {
                clean.add(s);
            }
        }
        commands = clean;
    }
}
