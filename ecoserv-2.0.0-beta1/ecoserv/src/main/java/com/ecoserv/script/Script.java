package com.ecoserv.script;

import java.util.ArrayList;
import java.util.List;

/**
 * Сценарий (п. 14): цепочка шагов с паузами. Хранится в config/ecoserv/scripts.json.
 * Шаги (по одному в строке):
 * <pre>
 * msg &aПривет, %player%!          — сообщение в чат
 * title &6Подземелье|&7уровень 1   — заголовок|подзаголовок
 * actionbar &eНайди ключ            — текст над хотбаром
 * sound minecraft:entity.player.levelup [громкость] [высота]
 * wait 40 | wait 2s                 — пауза в тиках или секундах
 * cmd give %player% bread 3         — команда от сервера
 * pcmd spawn                        — команда от имени игрока (с его правами)
 * tp 100 64 200 [поворот наклон]    — телепорт в этом мире
 * tpworld nether [x y z]            — в другой мир (на спавн или в точку)
 * activate дверь_шахты              — «Активация» для квестов
 * require role vip | require age >=16 — иначе сценарий останавливается
 * run другой_сценарий               — запустить вложенный сценарий
 * stop                              — закончить
 * </pre>
 */
public class Script {

    public String name = "";
    public String description = "";
    public List<String> steps = new ArrayList<String>();
    /** Разрешить игроку снова запустить сценарий, пока предыдущий ещё идёт. */
    public boolean allowParallel;

    public void normalize() {
        if (steps == null) {
            steps = new ArrayList<String>();
        }
        if (description == null) {
            description = "";
        }
    }
}
