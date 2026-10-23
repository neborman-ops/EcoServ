package com.ecoserv.custommenu;

import com.ecoserv.content.Actions;
import com.ecoserv.script.ScriptRunner;
import com.ecoserv.script.ScriptSteps;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Perms;

import net.minecraft.entity.player.EntityPlayerMP;

/** Своё меню глазами игрока. */
public class MenuScreen extends UiScreen {

    private String menu;

    public MenuScreen(EntityPlayerMP player, String menu) {
        super(player);
        this.menu = menu;
    }

    public static int color(String c) {
        switch (c == null ? "" : c) {
            case "green":
                return Ui.GREEN;
            case "yellow":
                return Ui.YELLOW;
            case "red":
                return Ui.RED;
            case "grey":
                return Ui.GREY;
            default:
                return Ui.BLUE;
        }
    }

    @Override
    protected int refreshSeconds() {
        CustomMenu m = MenuManager.get(menu);
        return m == null ? 0 : m.refresh;
    }

    private String ph(String s, CustomMenu m) {
        return Actions.placeholders(s, player, m.id);
    }

    @Override
    protected void build(Ui ui) {
        final CustomMenu m = MenuManager.get(menu);
        if (m == null) {
            ui.title("Меню").note(Ui.GREY, "Меню удалено.");
            return;
        }
        ui.title(ph(m.title, m)).icon(UiUtil.icon(m.icon));
        if (!m.subtitle.isEmpty()) {
            ui.subtitle(ph(m.subtitle, m));
        }
        if (Perms.isAdmin(player)) {
            ui.sideButton("Изменить", com.ecoserv.ui.UiIcons.gear(), Ui.GREY, () -> new MenuEditor(player, m.id).open());
        }
        Ui.Grid grid = null;
        for (final CustomMenu.Element e : m.elements) {
            if (!e.condition.trim().isEmpty() && !ScriptSteps.condition(player, ph(e.condition, m), m.id)) {
                continue;
            }
            if (!"item".equals(e.kind)) {
                grid = null;
            }
            switch (e.kind) {
                case "header":
                    ui.header(ph(e.text, m), ph(e.desc, m));
                    break;
                case "text":
                    for (String l : ph(e.text, m).split("\\\\n|\n")) {
                        ui.text(l);
                    }
                    break;
                case "note":
                    ui.note(color(e.color), ph(e.text, m));
                    break;
                case "button": {
                    Ui.Button b = ui.buttons().add(ph(e.text, m), color(e.color), () -> click(m, e));
                    if (!e.confirm.isEmpty()) {
                        b.confirm(ph(e.confirm, m));
                    }
                    break;
                }
                case "side":
                    ui.sideButton(ph(e.text, m), UiUtil.icon(e.icon), color(e.color), () -> click(m, e));
                    break;
                case "item":
                    if (grid == null) {
                        grid = ui.grid();
                    }
                    grid.cell(UiUtil.icon(e.icon), false, x -> click(m, e), ph(e.text, m), ph(e.desc, m));
                    break;
                default: {
                    Ui.Row r = ui.row(UiUtil.icon(e.icon), ph(e.text, m));
                    for (String l : ph(e.desc, m).split("\\\\n|\n")) {
                        if (!l.isEmpty()) {
                            r.line(l);
                        }
                    }
                    if (!e.steps.isEmpty() || !e.then.isEmpty()) {
                        r.onClick(() -> click(m, e));
                    }
                }
            }
        }
    }

    private void click(CustomMenu m, CustomMenu.Element e) {
        if (!e.steps.isEmpty()) {
            ScriptRunner.startSteps(player, "menu:" + m.id, e.steps, m.id);
        }
        if ("close".equals(e.then)) {
            close();
        } else if (e.then.startsWith("menu:")) {
            CustomMenu next = MenuManager.get(e.then.substring(5));
            if (next != null && MenuManager.allowed(player, next)) {
                menu = next.id;
            }
        }
    }
}
