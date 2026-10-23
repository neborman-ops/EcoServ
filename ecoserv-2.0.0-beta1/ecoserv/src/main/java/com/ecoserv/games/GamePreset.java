package com.ecoserv.games;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Мини-игра в списке (1.9.0): вид (дезран, давление, погоня…) + свои название, иконка и настройки.
 * Встроенные создаются сами; админ может сделать свои («Лавовый ад», «Котельная на 6 баков»).
 */
public class GamePreset {

    public String id = "";
    public String kind = "";
    public String title = "";
    public String description = "";
    public String icon = "minecraft:clock";
    public boolean enabled = true;
    public boolean builtin;
    /** Ставка за вход (0 — без ставки). Банк делят победители. */
    public long stake;
    /** Награда сервера победителю (деньги из ниоткуда; 0 — нет). */
    public long reward;
    public int minPlayers = 1;
    public int maxPlayers = 8;
    /** Настройки вида игры (сложность, длина, время…). */
    public Map<String, Integer> params = new LinkedHashMap<String, Integer>();
    public String creator = "";
    public int played;

    public int param(String key, int def) {
        Integer v = params == null ? null : params.get(key);
        return v == null ? def : v;
    }

    public void normalize() {
        if (params == null) {
            params = new LinkedHashMap<String, Integer>();
        }
        if (title == null || title.isEmpty()) {
            title = id;
        }
        if (description == null) {
            description = "";
        }
        if (icon == null || icon.isEmpty()) {
            icon = "minecraft:clock";
        }
        stake = Math.max(0L, stake);
        reward = Math.max(0L, reward);
        minPlayers = Math.max(1, minPlayers);
        maxPlayers = Math.max(minPlayers, maxPlayers);
    }
}
