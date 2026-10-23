package com.ecoserv.electric;

/** Виды электро-блоков (как в Rust): источники, батарея, логика, потребители. */
public final class ElectricTypes {

    public static final int SOLAR = 0;
    public static final int GENERATOR = 1;
    public static final int BATTERY = 2;
    public static final int SWITCH = 3;
    public static final int SPLITTER = 4;
    public static final int AND = 5;
    public static final int OR = 6;
    public static final int XOR = 7;
    public static final int TIMER = 8;
    public static final int SENSOR = 9;
    public static final int RS_IN = 10;
    public static final int RS_OUT = 11;
    public static final int LAMP = 12;
    public static final int DOOR = 13;
    public static final int ALARM = 14;
    public static final int METER = 15;

    public static final String[] IDS = {"solar", "generator", "battery", "switch", "splitter", "and", "or", "xor", "timer",
            "sensor", "rs_in", "rs_out", "lamp", "door", "alarm", "meter"};
    public static final String[] TITLES = {"Солнечная панель", "Генератор", "Батарея", "Выключатель", "Разветвитель",
            "Логика И", "Логика ИЛИ", "Логика ИСКЛ-ИЛИ", "Таймер", "Датчик игрока", "Вход редстоуна", "Выход в редстоун",
            "Лампа", "Привод двери", "Сирена", "Измеритель"};
    public static final String[] HINTS = {
            "Днём под открытым небом даёт до 20 ед.",
            "Даёт до 50 ед., пока есть топливо (уголь, дерево — ПКМ)",
            "Копит энергию и отдаёт её, когда нет другой",
            "Пропускает ток, когда включён (ПКМ)",
            "Делит ток на 3 выхода",
            "Пропускает, если ток есть на ВСЕХ входах",
            "Пропускает, если ток есть хоть на одном входе",
            "Пропускает, если ток ровно на одном входе",
            "Пропускает ток то вкл., то выкл. по времени",
            "Пропускает ток, когда рядом игрок",
            "Пропускает ток, когда на блок подан редстоун",
            "Даёт редстоун-сигнал, когда есть ток",
            "Светит, когда есть ток (2 ед.)",
            "Открывает двери рядом, когда есть ток (5 ед.)",
            "Мигает и воет, когда есть ток (3 ед.)",
            "Пропускает ток и показывает, сколько идёт"};
    /** Сколько потребляет сам блок. */
    public static final int[] USE = {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 5, 3, 0};
    /** Сколько выходов. */
    public static final int[] OUTS = {1, 1, 1, 1, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    public static final int[] COLORS = {0x3060C0, 0x606060, 0x30A050, 0xC0A030, 0xA06030, 0x5050C0, 0x5090C0, 0x9050C0,
            0xC07030, 0xC03050, 0xB02020, 0xE04040, 0xF0E080, 0x806040, 0xE02020, 0x40C0C0};

    private ElectricTypes() {
    }

    public static boolean source(int t) {
        return t == SOLAR || t == GENERATOR || t == BATTERY;
    }

    public static boolean consumer(int t) {
        return t == LAMP || t == DOOR || t == ALARM || t == RS_OUT;
    }
}
