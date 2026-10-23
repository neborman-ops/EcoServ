package com.ecoserv.client.ui;

import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Выпадающий список вариантов (с поиском, если вариантов много). */
@SideOnly(Side.CLIENT)
final class DropdownOverlay extends Overlay {

    private static final int ROW = 16;

    private final GuiWindow.WSelect select;
    private final List<GuiWindow.Opt> shown = new ArrayList<GuiWindow.Opt>();
    private GuiTextField search;
    private int scroll;
    private int x;
    private int y;
    private int w;
    private int h;
    private int listY;
    private int rows;

    /** Тот же список после обновления окна: поиск и прокрутка сохраняются. */
    DropdownOverlay(GuiWindow g, GuiWindow.WSelect select, DropdownOverlay old) {
        this(g, select);
        if (search != null && old.search != null) {
            search.setText(old.search.getText());
            filter();
        }
        scroll = old.scroll;
    }

    int selectId() {
        return select.id;
    }

    DropdownOverlay(GuiWindow g, GuiWindow.WSelect select) {
        this.select = select;
        if (select.opts.size() > 10) {
            search = new GuiTextField(9100, UiTheme.font(), 0, 0, 100, 10);
            search.setEnableBackgroundDrawing(false);
            search.setFocused(true);
            search.setMaxStringLength(48);
        }
        filter();
        // прокрутить к выбранному
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).key.equals(select.val)) {
                scroll = Math.max(0, i - 3);
            }
        }
    }

    private void filter() {
        shown.clear();
        String f = search == null ? "" : search.getText().toLowerCase(Locale.ROOT);
        for (GuiWindow.Opt o : select.opts) {
            if (f.isEmpty() || GuiWindow.W.plain(o.label + " " + o.desc + " " + o.key).contains(f)) {
                shown.add(o);
            }
        }
        scroll = 0;
    }

    private boolean hasDesc() {
        for (GuiWindow.Opt o : shown) {
            if (!o.desc.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void layout(GuiWindow g) {
        w = Math.max(select.bw, Math.min(240, g.winW - 40));
        boolean desc = hasDesc();
        int rowH = desc ? ROW + 9 : ROW;
        int maxRows = Math.max(3, (g.winH - 60) / rowH);
        rows = Math.min(maxRows, Math.max(1, shown.size()));
        int searchH = search != null ? 16 : 0;
        h = rows * rowH + 4 + searchH;
        x = Math.min(select.bx + select.bw - w, g.left + g.winW - w - 6);
        x = Math.max(g.left + 6, x);
        y = select.by + 18;
        if (y + h > g.top + g.winH - 6) {
            y = Math.max(g.top + GuiWindow.TITLE_H + 4, select.by - h - 2);
        }
        listY = y + 2 + searchH;
        if (search != null) {
            search.x = x + 14;
            search.y = y + 5;
        }
    }

    private int rowH() {
        return hasDesc() ? ROW + 9 : ROW;
    }

    @Override
    void draw(GuiWindow g, int mx, int my, float partialTicks) {
        layout(g);
        UiTheme.rect(x - 1, y - 1, w + 2, h + 2, 0xFF07080A);
        UiTheme.panel(x, y, w, h, 0xFF15171D, UiTheme.ACCENT);
        if (search != null) {
            UiTheme.panel(x + 2, y + 2, w - 4, 13, UiTheme.INPUT, UiTheme.INPUT_BORDER);
            UiTheme.text("⌕", x + 5, y + 5, UiTheme.MUTED);
            if (search.getText().isEmpty()) {
                UiTheme.text("поиск…", x + 14, y + 5, UiTheme.MUTED);
            }
            search.drawTextBox();
        }
        int rh = rowH();
        for (int i = 0; i < rows && scroll + i < shown.size(); i++) {
            GuiWindow.Opt o = shown.get(scroll + i);
            int ry = listY + i * rh;
            boolean hover = GuiWindow.inside(mx, my, x + 1, ry, w - 2, rh);
            boolean sel = o.key.equals(select.val);
            if (hover) {
                UiTheme.rect(x + 1, ry, w - 2, rh, 0xFF262D3D);
            } else if (sel) {
                UiTheme.rect(x + 1, ry, w - 2, rh, 0xFF1E2A42);
            }
            int tx = x + 5;
            if (!o.icon.isEmpty()) {
                UiTheme.item(o.icon, tx, ry + (rh - 16) / 2);
                tx += 19;
            }
            UiTheme.text(UiTheme.trim((sel ? "✔ " : "") + o.label, x + w - tx - 6), tx, ry + 4,
                    sel ? 0xFF8FB4FF : UiTheme.TEXT);
            if (!o.desc.isEmpty()) {
                UiTheme.text(UiTheme.trim(o.desc, x + w - tx - 6), tx, ry + 14, UiTheme.MUTED);
            }
        }
        if (shown.size() > rows) {
            int trackH = rows * rh;
            int thumbH = Math.max(10, trackH * rows / shown.size());
            int thumbY = listY + (trackH - thumbH) * scroll / Math.max(1, shown.size() - rows);
            UiTheme.rect(x + w - 4, thumbY, 2, thumbH, 0xFF6A7183);
        }
        if (shown.isEmpty()) {
            UiTheme.text("ничего не найдено", x + 6, listY + 4, UiTheme.MUTED);
        }
    }

    @Override
    boolean click(GuiWindow g, int mx, int my, int button) {
        layout(g);
        if (!GuiWindow.inside(mx, my, x, y, w, h)) {
            return false;
        }
        if (search != null && my < listY) {
            search.setFocused(true);
            return true;
        }
        int rh = rowH();
        int i = (my - listY) / rh;
        if (i >= 0 && i < rows && scroll + i < shown.size()) {
            g.click();
            select.choose(g, shown.get(scroll + i).key);
            g.overlay = null;
        }
        return true;
    }

    @Override
    void key(GuiWindow g, char c, int code) {
        if (search != null) {
            String before = search.getText();
            search.textboxKeyTyped(c, code);
            if (!before.equals(search.getText())) {
                filter();
            }
        }
    }

    @Override
    void wheel(int dir) {
        scroll = Math.max(0, Math.min(Math.max(0, shown.size() - rows), scroll + dir * 2));
    }

    @Override
    void tick() {
        if (search != null) {
            search.updateCursorCounter();
        }
    }
}
