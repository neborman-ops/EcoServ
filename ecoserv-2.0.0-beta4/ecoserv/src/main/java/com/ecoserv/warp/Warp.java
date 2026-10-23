package com.ecoserv.warp;

/** Варп (1.9.0): именованная точка телепорта. Серверный (owner пустой) или игрока. */
public class Warp {

    public String name = "";
    public String title = "";
    public String description = "";
    public String icon = "minecraft:ender_pearl";
    public int dim;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    /** UUID владельца; пусто — варп сервера. */
    public String owner = "";
    public String ownerName = "";
    /** Видят все (иначе — только владелец и его команда). */
    public boolean open = true;
    /** Цена перехода (уходит владельцу варпа или серверу). */
    public long price;
    /** Нужна роль (пусто — всем). */
    public String role = "";
    public long created;
    public int visits;

    public boolean server() {
        return owner == null || owner.isEmpty();
    }

    public void normalize() {
        if (name == null) {
            name = "";
        }
        if (title == null || title.isEmpty()) {
            title = name;
        }
        if (description == null) {
            description = "";
        }
        if (icon == null || icon.isEmpty()) {
            icon = "minecraft:ender_pearl";
        }
        if (owner == null) {
            owner = "";
        }
        if (ownerName == null) {
            ownerName = "";
        }
        if (role == null) {
            role = "";
        }
        price = Math.max(0L, price);
    }
}
