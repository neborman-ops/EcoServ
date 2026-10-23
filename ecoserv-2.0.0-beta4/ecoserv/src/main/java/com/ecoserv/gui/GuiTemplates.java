package com.ecoserv.gui;

import java.util.LinkedHashMap;
import java.util.Map;

/** Готовые окна для дизайнера — чтобы не начинать с пустого места. */
public final class GuiTemplates {

    private GuiTemplates() {
    }

    static CustomGui.El el(String kind, int x, int y, int w, int h, String text) {
        CustomGui.El e = new CustomGui.El();
        e.kind = kind;
        e.x = x;
        e.y = y;
        e.w = w;
        e.h = h;
        e.text = text;
        if ("text".equals(kind) || "item".equals(kind)) {
            e.bgAlpha = 0;
        }
        return e;
    }

    public static CustomGui blank() {
        CustomGui g = new CustomGui();
        g.title = "&fМоё окно";
        CustomGui.El t = el("text", 10, 22, 200, 30, "&7Перетащи элементы слева на окно. Двойной щелчок по тексту — изменить.");
        g.elements.add(t);
        CustomGui.El b = el("button", 70, 100, 80, 18, "Закрыть");
        b.then = "close";
        g.elements.add(b);
        return g;
    }

    public static Map<String, CustomGui> all() {
        Map<String, CustomGui> m = new LinkedHashMap<String, CustomGui>();
        m.put("Пустое", blank());

        CustomGui hi = new CustomGui();
        hi.width = 240;
        hi.height = 150;
        hi.title = "&6Добро пожаловать!";
        CustomGui.El t = el("text", 12, 26, 216, 50, "&fПривет, &e%player%&f!\\n&7Это наш сервер. Нажми кнопку, чтобы начать.");
        hi.elements.add(t);
        CustomGui.El r = el("rect", 12, 82, 216, 1, "");
        r.bg = 0x3A3F4C;
        hi.elements.add(r);
        CustomGui.El start = el("button", 20, 112, 96, 20, "&aНачать");
        start.bg = 0x2E8B57;
        start.steps.add("msg &aУдачи!");
        start.then = "close";
        hi.elements.add(start);
        CustomGui.El rules = el("button", 124, 112, 96, 20, "Правила");
        rules.bg = 0x3A3F4C;
        rules.steps.add("msg &7Правила: не гриферить, уважать других.");
        hi.elements.add(rules);
        m.put("Приветствие", hi);

        CustomGui shop = new CustomGui();
        shop.width = 230;
        shop.height = 160;
        shop.title = "&eМагазин";
        shop.refresh = 2;
        CustomGui.El money = el("text", 12, 24, 206, 12, "&7Баланс: &a%money%");
        shop.elements.add(money);
        String[][] goods = {{"minecraft:diamond@0", "Алмаз", "100"}, {"minecraft:golden_apple@0", "Золотое яблоко", "50"},
                {"minecraft:ender_pearl@0", "Жемчуг Края", "30"}};
        for (int i = 0; i < goods.length; i++) {
            int y = 44 + i * 34;
            CustomGui.El box = el("rect", 12, y, 206, 30, "");
            box.bg = 0x1F232C;
            box.border = 0x2C303B;
            shop.elements.add(box);
            CustomGui.El it = el("item", 18, y + 7, 16, 16, "");
            it.item = goods[i][0];
            it.tooltip = goods[i][1];
            shop.elements.add(it);
            CustomGui.El name = el("text", 40, y + 11, 100, 10, "&f" + goods[i][1] + " &7— " + goods[i][2]);
            shop.elements.add(name);
            CustomGui.El buy = el("button", 158, y + 6, 54, 18, "Купить");
            buy.bg = 0x2E8B57;
            String item = goods[i][0].replace("@0", "");
            buy.steps.add("if money >= " + goods[i][2] + " then give " + item + " 1");
            buy.steps.add("if money >= " + goods[i][2] + " then money -" + goods[i][2] + " else msg &cНе хватает денег");
            shop.elements.add(buy);
        }
        m.put("Магазин", shop);

        CustomGui ask = new CustomGui();
        ask.width = 200;
        ask.height = 100;
        ask.title = "&fПодтверждение";
        ask.closeButton = false;
        ask.elements.add(el("text", 10, 26, 180, 30, "&7Точно сделать это?"));
        CustomGui.El yes = el("button", 16, 68, 80, 18, "&aДа");
        yes.bg = 0x2E8B57;
        yes.then = "close";
        ask.elements.add(yes);
        CustomGui.El no = el("button", 104, 68, 80, 18, "&cНет");
        no.bg = 0x8B2E2E;
        no.then = "close";
        ask.elements.add(no);
        m.put("Да / Нет", ask);

        CustomGui form = new CustomGui();
        form.width = 220;
        form.height = 120;
        form.title = "&bАнкета";
        form.elements.add(el("text", 12, 26, 196, 10, "&7Как тебя зовут в игре?"));
        CustomGui.El in = el("input", 12, 42, 196, 16, "");
        in.var = "nickname";
        in.tooltip = "Например, Стив";
        form.elements.add(in);
        CustomGui.El ok = el("button", 70, 84, 80, 18, "Готово");
        ok.steps.add("msg &aЗапомнил: %var:nickname%");
        ok.then = "close";
        form.elements.add(ok);
        m.put("Поле ввода", form);
        for (CustomGui g : m.values()) {
            g.normalize();
        }
        return m;
    }
}
