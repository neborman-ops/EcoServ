package com.ecoserv.cutscene;

import java.util.ArrayList;
import java.util.List;

/** Катсцена (2.0): камера летит по точкам; на точках — текст и действия. */
public class Cutscene {

    public static class Point {
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        /** За сколько тиков камера долетает до этой точки от предыдущей. */
        public int ticks = 60;
        /** Сколько тиков постоять на точке. */
        public int hold;
        /** «Заголовок|подзаголовок» при прилёте. */
        public String text = "";
        public List<String> steps = new ArrayList<String>();
    }

    public String id = "";
    public String title = "";
    public int dim;
    public List<Point> points = new ArrayList<Point>();
    /** Плавная кривая (иначе — прямые отрезки). */
    public boolean smooth = true;
    /** Плавный разгон и торможение. */
    public boolean ease = true;
    public boolean hideHud = true;
    /** Чёрные полосы сверху и снизу. */
    public boolean bars = true;
    /** Переносить игрока к камере (если точки далеко и мир там не прогружен). */
    public boolean movePlayer;
    /** Можно пропустить (Shift). */
    public boolean skippable = true;
    /** Шаги в конце. */
    public List<String> endSteps = new ArrayList<String>();

    public void normalize() {
        if (points == null) {
            points = new ArrayList<Point>();
        }
        for (Point p : points) {
            if (p.steps == null) {
                p.steps = new ArrayList<String>();
            }
            if (p.text == null) {
                p.text = "";
            }
            p.ticks = Math.max(1, p.ticks);
            p.hold = Math.max(0, p.hold);
        }
        if (endSteps == null) {
            endSteps = new ArrayList<String>();
        }
        if (title == null) {
            title = "";
        }
    }

    /** Когда (тик от начала) камера прилетает в точку i. Первая точка — в 0. */
    public int arrive(int i) {
        int t = 0;
        for (int k = 1; k <= i && k < points.size(); k++) {
            t += points.get(k - 1).hold + points.get(k).ticks;
        }
        return t;
    }

    public int length() {
        return points.isEmpty() ? 0 : arrive(points.size() - 1) + points.get(points.size() - 1).hold;
    }
}
