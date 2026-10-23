package com.ecoserv.client.ui;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.List;

/** «Точно?» — окно подтверждения перед опасным действием (удаление и т.п.). */
@SideOnly(Side.CLIENT)
final class ConfirmOverlay extends Overlay {

    private final String text;
    private final Runnable onYes;
    private int x;
    private int y;
    private int w;
    private int h;
    private List<String> lines;

    ConfirmOverlay(String text, Runnable onYes) {
        this.text = text;
        this.onYes = onYes;
    }

    private void layout(GuiWindow g) {
        w = Math.min(260, g.winW - 40);
        lines = UiTheme.font().listFormattedStringToWidth(text, w - 20);
        h = 30 + lines.size() * 10 + 28;
        x = g.left + (g.winW - w) / 2;
        y = g.top + (g.winH - h) / 2;
    }

    @Override
    void draw(GuiWindow g, int mx, int my, float partialTicks) {
        layout(g);
        UiTheme.rect(x - 1, y - 1, w + 2, h + 2, 0xFF07080A);
        UiTheme.panel(x, y, w, h, 0xFF191B22, 0xFFC9403A);
        UiTheme.rect(x + 1, y + 1, w - 2, 18, 0xFF2A1416);
        UiTheme.shadow("⚠ Подтверждение", x + 8, y + 6, 0xFFFFD27A);
        for (int i = 0; i < lines.size(); i++) {
            UiTheme.text(lines.get(i), x + 10, y + 26 + i * 10, UiTheme.TEXT);
        }
        int by = y + h - 24;
        int bw = (w - 30) / 2;
        UiTheme.button(x + 10, by, bw, 18, "Да, выполнить", 3, GuiWindow.inside(mx, my, x + 10, by, bw, 18), false);
        UiTheme.button(x + 20 + bw, by, bw, 18, "Отмена", 0, GuiWindow.inside(mx, my, x + 20 + bw, by, bw, 18), false);
    }

    @Override
    boolean click(GuiWindow g, int mx, int my, int button) {
        layout(g);
        int by = y + h - 24;
        int bw = (w - 30) / 2;
        if (GuiWindow.inside(mx, my, x + 10, by, bw, 18)) {
            g.click();
            g.overlay = null;
            onYes.run();
            return true;
        }
        if (GuiWindow.inside(mx, my, x + 20 + bw, by, bw, 18)) {
            g.click();
            return false;
        }
        return GuiWindow.inside(mx, my, x, y, w, h);
    }

    @Override
    void key(GuiWindow g, char c, int code) {
        if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
            g.overlay = null;
            onYes.run();
        }
    }
}
