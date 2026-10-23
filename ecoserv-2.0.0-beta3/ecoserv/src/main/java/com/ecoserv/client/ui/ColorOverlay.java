package com.ecoserv.client.ui;

import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;

/** 2.0 beta3: выбор цвета — тон, насыщенность, яркость, готовые цвета и HEX. */
@SideOnly(Side.CLIENT)
final class ColorOverlay extends Overlay {

    private static final int[] PALETTE = {0xFFFFFF, 0xC0C0C0, 0x808080, 0x404040, 0x000000, 0xFF4040, 0xFF9020, 0xFFE040,
            0x80E040, 0x30C060, 0x30D0D0, 0x3080FF, 0x6050E0, 0xB050E0, 0xFF60B0, 0x8B5A2B, 0xFFD9A0, 0xFFB27A, 0xB4D2FF,
            0x5A8CFF, 0x1A2440, 0x464646, 0xC7C7C7, 0x5C3C0D};

    private final GuiWindow.WColor target;
    private float hue;
    private float sat;
    private float bri;
    private int x;
    private int y;
    private final int w = 220;
    private final int h = 150;
    private int dragBar = -1;
    private GuiTextField hex;

    ColorOverlay(GuiWindow.WColor target) {
        this.target = target;
        float[] hsb = Color.RGBtoHSB(target.val >> 16 & 255, target.val >> 8 & 255, target.val & 255, null);
        hue = hsb[0];
        sat = hsb[1];
        bri = hsb[2];
    }

    private int rgb() {
        return Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF;
    }

    private void layout(GuiWindow g) {
        x = g.left + (g.winW - w) / 2;
        y = g.top + (g.winH - h) / 2;
        if (hex == null) {
            hex = new GuiTextField(9100, UiTheme.font(), x + 112, y + 112, 60, 12);
            hex.setMaxStringLength(7);
            hex.setText(String.format("#%06X", rgb()));
        }
        hex.x = x + 112;
        hex.y = y + 112;
    }

    private void bar(int bx, int by, int bw, int which) {
        for (int i = 0; i < bw; i++) {
            float f = i / (float) (bw - 1);
            int c;
            if (which == 0) {
                c = Color.HSBtoRGB(f, 1.0F, 1.0F);
            } else if (which == 1) {
                c = Color.HSBtoRGB(hue, f, bri);
            } else {
                c = Color.HSBtoRGB(hue, sat, f);
            }
            UiTheme.rect(bx + i, by, 1, 10, 0xFF000000 | c);
        }
        float v = which == 0 ? hue : which == 1 ? sat : bri;
        int kx = bx + (int) (v * (bw - 1));
        UiTheme.rect(kx - 1, by - 2, 3, 14, 0xFFFFFFFF);
    }

    @Override
    void draw(GuiWindow g, int mx, int my, float partialTicks) {
        layout(g);
        if (dragBar >= 0 && Mouse.isButtonDown(0)) {
            set(dragBar, mx);
        } else {
            dragBar = -1;
        }
        UiTheme.rect(x - 1, y - 1, w + 2, h + 2, 0xFF07080A);
        UiTheme.panel(x, y, w, h, 0xFF191B22, UiTheme.ACCENT);
        UiTheme.shadow("Цвет: " + target.label, x + 8, y + 6, 0xFFFFD27A);
        String[] names = {"Тон", "Насыщенность", "Яркость"};
        for (int i = 0; i < 3; i++) {
            UiTheme.text(names[i], x + 8, y + 22 + i * 22, UiTheme.DIM);
            bar(x + 8, y + 31 + i * 22, 130, i);
        }
        UiTheme.rect(x + 148, y + 22, 64, 54, 0xFF000000);
        UiTheme.rect(x + 149, y + 23, 62, 52, 0xFF000000 | rgb());
        for (int i = 0; i < PALETTE.length; i++) {
            int px = x + 8 + (i % 12) * 17;
            int py = y + 90 + (i / 12) * 11;
            UiTheme.rect(px, py, 15, 9, GuiWindow.inside(mx, my, px, py, 15, 9) ? 0xFFFFFFFF : 0xFF000000);
            UiTheme.rect(px + 1, py + 1, 13, 7, 0xFF000000 | PALETTE[i]);
        }
        UiTheme.text("HEX", x + 90, y + 114, UiTheme.DIM);
        UiTheme.rect(x + 110, y + 110, 64, 16, UiTheme.INPUT);
        hex.drawTextBox();
        UiTheme.button(x + 8, y + h - 22, 70, 16, "Готово", 3, GuiWindow.inside(mx, my, x + 8, y + h - 22, 70, 16), false);
        UiTheme.button(x + 84, y + h - 22, 70, 16, "Отмена", 0, GuiWindow.inside(mx, my, x + 84, y + h - 22, 70, 16), false);
    }

    private void set(int which, int mx) {
        float f = Math.max(0, Math.min(1, (mx - (x + 8)) / 129.0F));
        if (which == 0) {
            hue = f;
        } else if (which == 1) {
            sat = f;
        } else {
            bri = f;
        }
        hex.setText(String.format("#%06X", rgb()));
    }

    private void done(GuiWindow g) {
        target.val = rgb();
        g.send(target.id, 0, false, Integer.toString(target.val));
        g.overlay = null;
    }

    @Override
    boolean click(GuiWindow g, int mx, int my, int button) {
        layout(g);
        for (int i = 0; i < 3; i++) {
            if (GuiWindow.inside(mx, my, x + 6, y + 29 + i * 22, 134, 14)) {
                dragBar = i;
                set(i, mx);
                return true;
            }
        }
        for (int i = 0; i < PALETTE.length; i++) {
            int px = x + 8 + (i % 12) * 17;
            int py = y + 90 + (i / 12) * 11;
            if (GuiWindow.inside(mx, my, px, py, 15, 9)) {
                float[] hsb = Color.RGBtoHSB(PALETTE[i] >> 16 & 255, PALETTE[i] >> 8 & 255, PALETTE[i] & 255, null);
                hue = hsb[0];
                sat = hsb[1];
                bri = hsb[2];
                hex.setText(String.format("#%06X", rgb()));
                return true;
            }
        }
        hex.mouseClicked(mx, my, button);
        if (GuiWindow.inside(mx, my, x + 8, y + h - 22, 70, 16)) {
            g.click();
            done(g);
            return true;
        }
        if (GuiWindow.inside(mx, my, x + 84, y + h - 22, 70, 16)) {
            g.click();
            return false;
        }
        return GuiWindow.inside(mx, my, x, y, w, h);
    }

    @Override
    void key(GuiWindow g, char c, int code) {
        if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
            done(g);
            return;
        }
        if (hex != null && hex.textboxKeyTyped(c, code)) {
            String t = hex.getText().replace("#", "");
            if (t.length() == 6) {
                try {
                    int v = Integer.parseInt(t, 16);
                    float[] hsb = Color.RGBtoHSB(v >> 16 & 255, v >> 8 & 255, v & 255, null);
                    hue = hsb[0];
                    sat = hsb[1];
                    bri = hsb[2];
                } catch (NumberFormatException ignored) {
                    // ввод не закончен
                }
            }
        }
    }
}
