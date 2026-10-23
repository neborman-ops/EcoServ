package com.ecoserv.npc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** NPC (2.0): житель/моб/стойка без ИИ, который стоит на месте, смотрит на игроков и разговаривает. */
public class Npc {

    /** Реплика диалога: текст и варианты ответа. */
    public static class Node {
        public String id = "start";
        public String text = "";
        public List<Option> options = new ArrayList<Option>();
    }

    public static class Option {
        public String label = "Пока!";
        /** Следующая реплика (пусто — закрыть диалог). */
        public String next = "";
        /** Показывать, только если условие выполнено (пусто — всегда). */
        public String condition = "";
        /** Шаги сценария при выборе. */
        public List<String> steps = new ArrayList<String>();
    }

    public String id = "";
    public String name = "&eЖитель";
    /** minecraft:villager, minecraft:armor_stand, minecraft:zombie… */
    public String type = "minecraft:villager";
    /** Для жителя: 0 фермер, 1 библиотекарь, 2 священник, 3 кузнец, 4 мясник, 5 нитвит. */
    public int profession;
    public int dim;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public boolean lookAtPlayers = true;
    public boolean nameVisible = true;
    public boolean glowing;
    public boolean baby;
    /** Слот (head, chest, legs, feet, mainhand, offhand) → id предмета. */
    public Map<String, String> equipment = new LinkedHashMap<String, String>();
    /** Нажатие: dialog — открыть диалог, steps — выполнить шаги, both — оба. */
    public String onClick = "dialog";
    public List<String> clickSteps = new ArrayList<String>();
    public List<Node> dialog = new ArrayList<Node>();
    /** Ходит по маркерам «Точка пути» с именами путь_1, путь_2… (пусто — стоит). */
    public String path = "";
    /** Скорость ходьбы, блоков в секунду. */
    public double speed = 1.5D;
    /** По кругу (иначе — туда и обратно). */
    public boolean loop = true;
    /** Через сколько секунд снова можно нажать (0 — сразу). */
    public int cooldown = 1;

    public void normalize() {
        if (equipment == null) {
            equipment = new LinkedHashMap<String, String>();
        }
        if (clickSteps == null) {
            clickSteps = new ArrayList<String>();
        }
        if (dialog == null) {
            dialog = new ArrayList<Node>();
        }
        if (dialog.isEmpty()) {
            Node n = new Node();
            n.text = "Привет, %player%! Чем могу помочь?";
            Option o = new Option();
            o.label = "Пока!";
            n.options.add(o);
            dialog.add(n);
        }
        for (Node n : dialog) {
            if (n.options == null) {
                n.options = new ArrayList<Option>();
            }
            for (Option o : n.options) {
                if (o.steps == null) {
                    o.steps = new ArrayList<String>();
                }
                if (o.next == null) {
                    o.next = "";
                }
                if (o.condition == null) {
                    o.condition = "";
                }
            }
        }
        if (path == null) {
            path = "";
        }
        if (onClick == null) {
            onClick = "dialog";
        }
    }

    public Node node(String nodeId) {
        for (Node n : dialog) {
            if (n.id.equals(nodeId)) {
                return n;
            }
        }
        return dialog.isEmpty() ? null : dialog.get(0);
    }
}
