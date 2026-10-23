package com.ecoserv.client.ui;

import com.ecoserv.gui.GuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/** Тёмная тема окон (как у FTB Quests) и простые примитивы отрисовки. */
@SideOnly(Side.CLIENT)
public final class UiTheme {

    // 1.9.0: цвета зависят от темы (клиентские настройки) — поэтому не final
    public static int WINDOW = 0xF2171920;
    public static int TITLE = 0xFF0F1116;
    public static int SIDEBAR = 0xFF121419;
    public static int BORDER = 0xFF2C303B;
    public static int BORDER_LIGHT = 0xFF3A3F4C;
    public static int ROW = 0xFF1B1E26;
    public static int ROW_HOVER = 0xFF252935;
    public static int ROW_HL = 0xFF1E2A42;
    public static int TEXT = 0xFFE8EAF0;
    public static int DIM = 0xFF9CA3B4;
    public static int MUTED = 0xFF6A7183;
    public static int ACCENT = 0xFF4E8CFF;
    public static int INPUT = 0xFF0C0E12;
    public static int INPUT_BORDER = 0xFF383D49;
    public static int SHADE = 0xB0000000;

    /** Темы: окно, заголовок, боковая панель, рамка, светлая рамка, строка, наведение, выделение, текст,
     *  приглушённый, ещё тише, акцент, поле ввода, рамка поля. */
    private static final java.util.Map<String, int[]> THEMES = new java.util.LinkedHashMap<String, int[]>();

    static {
        THEMES.put("dark", new int[]{0xF2171920, 0xFF0F1116, 0xFF121419, 0xFF2C303B, 0xFF3A3F4C, 0xFF1B1E26,
                0xFF252935, 0xFF1E2A42, 0xFFE8EAF0, 0xFF9CA3B4, 0xFF6A7183, 0xFF4E8CFF, 0xFF0C0E12, 0xFF383D49});
        THEMES.put("midnight", new int[]{0xF20D1424, 0xFF0A0F1C, 0xFF0B1120, 0xFF1F2A44, 0xFF2C3B5E, 0xFF111A2E,
                0xFF1A2742, 0xFF1C2F56, 0xFFE6ECFF, 0xFF97A6C9, 0xFF62708F, 0xFF5B8CFF, 0xFF070B14, 0xFF2A3656});
        THEMES.put("amethyst", new int[]{0xF21A1424, 0xFF120D1A, 0xFF150F1F, 0xFF34284A, 0xFF46365F, 0xFF211932,
                0xFF2C2142, 0xFF2D1F4F, 0xFFF0EAFF, 0xFFB3A3CF, 0xFF7A6A96, 0xFFA066FF, 0xFF0E0A14, 0xFF433556});
        THEMES.put("forest", new int[]{0xF2121A15, 0xFF0C120E, 0xFF0F1611, 0xFF263A2D, 0xFF33503D, 0xFF17221B,
                0xFF1F2E24, 0xFF1C3A28, 0xFFE8F2EA, 0xFF9DB8A4, 0xFF6A8571, 0xFF3FBF73, 0xFF080D0A, 0xFF2F4637});
        THEMES.put("classic", new int[]{0xF2303030, 0xFF1E1E1E, 0xFF262626, 0xFF5A5A5A, 0xFF6E6E6E, 0xFF383838,
                0xFF454545, 0xFF3D4A5C, 0xFFFFFFFF, 0xFFBDBDBD, 0xFF8A8A8A, 0xFF7FB2FF, 0xFF141414, 0xFF6A6A6A});
    }

    public static java.util.Set<String> themes() {
        return THEMES.keySet();
    }

    /** Применить тему по названию (неизвестная — тёмная). */
    public static void apply(String name) {
        int[] t = THEMES.get(name);
        if (t == null) {
            t = THEMES.get("dark");
        }
        WINDOW = t[0];
        TITLE = t[1];
        SIDEBAR = t[2];
        BORDER = t[3];
        BORDER_LIGHT = t[4];
        ROW = t[5];
        ROW_HOVER = t[6];
        ROW_HL = t[7];
        TEXT = t[8];
        DIM = t[9];
        MUTED = t[10];
        ACCENT = t[11];
        INPUT = t[12];
        INPUT_BORDER = t[13];
    }

    /** Цвета Ui.GREY..Ui.PURPLE. */
    private static final int[] KINDS = {0xFF3B404C, 0xFF3C74E0, 0xFF2E9E62, 0xFFC9403A, 0xFFC99A22, 0xFF8A4FE0};

    private UiTheme() {
    }

    public static int kind(int kind) {
        return kind >= 0 && kind < KINDS.length ? KINDS[kind] : KINDS[0];
    }

    public static int lighter(int argb, float f) {
        int a = argb >>> 24;
        int r = Math.min(255, (int) (((argb >> 16) & 255) + (255 - ((argb >> 16) & 255)) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 255) + (255 - ((argb >> 8) & 255)) * f));
        int b = Math.min(255, (int) ((argb & 255) + (255 - (argb & 255)) * f));
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int darker(int argb, float f) {
        int a = argb >>> 24;
        int r = (int) (((argb >> 16) & 255) * (1.0F - f));
        int g = (int) (((argb >> 8) & 255) * (1.0F - f));
        int b = (int) ((argb & 255) * (1.0F - f));
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int alpha(int argb, int alpha) {
        return (alpha & 255) << 24 | (argb & 0xFFFFFF);
    }

    public static FontRenderer font() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    public static void rect(int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) {
            Gui.drawRect(x, y, x + w, y + h, color);
        }
    }

    public static void border(int x, int y, int w, int h, int color) {
        rect(x, y, w, 1, color);
        rect(x, y + h - 1, w, 1, color);
        rect(x, y + 1, 1, h - 2, color);
        rect(x + w - 1, y + 1, 1, h - 2, color);
    }

    /** Прямоугольник со скруглёнными (срезанными) углами. */
    public static void round(int x, int y, int w, int h, int color) {
        if (w < 3 || h < 3) {
            rect(x, y, w, h, color);
            return;
        }
        rect(x + 1, y, w - 2, h, color);
        rect(x, y + 1, 1, h - 2, color);
        rect(x + w - 1, y + 1, 1, h - 2, color);
    }

    public static void panel(int x, int y, int w, int h, int fill, int border) {
        rect(x, y, w, h, fill);
        border(x, y, w, h, border);
    }

    public static void text(String s, int x, int y, int color) {
        font().drawString(s, x, y, color);
    }

    public static void shadow(String s, int x, int y, int color) {
        font().drawStringWithShadow(s, x, y, color);
    }

    public static void center(String s, int cx, int y, int color) {
        font().drawString(s, cx - font().getStringWidth(s) / 2, y, color);
    }

    public static int width(String s) {
        return font().getStringWidth(s);
    }

    public static String trim(String s, int max) {
        return GuiUtil.trim(s, max);
    }

    public static void item(ItemStack stack, int x, int y) {
        // анимированная иконка: сервер шлёт предмет-заглушку с меткой ecoAnim (UiIcons)
        if (stack != null && !stack.isEmpty() && stack.hasTagCompound()
                && stack.getTagCompound().hasKey("ecoAnim")) {
            AnimSprite sprite = AnimSprite.byName(stack.getTagCompound().getString("ecoAnim"));
            if (sprite != null) {
                net.minecraft.client.renderer.GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                sprite.draw(x, y, com.ecoserv.client.ClientSettings.get().animatedIcons);
                return;
            }
        }
        GuiUtil.drawItem(stack, x, y);
    }

    /** Кнопка: заливка цветом вида, светлее при наведении, серая если недоступна. */
    public static void button(int x, int y, int w, int h, String label, int kind, boolean hover, boolean disabled) {
        int base = disabled ? 0xFF2A2D35 : kind(kind);
        int fill = hover && !disabled ? lighter(base, 0.18F) : base;
        round(x, y, w, h, darker(fill, 0.35F));
        round(x, y, w, h - 1, fill);
        String s = trim(label, w - 6);
        center(s, x + w / 2, y + (h - 8) / 2, disabled ? MUTED : 0xFFFFFFFF);
    }

    /** Переключатель-«таблетка». */
    public static void toggle(int x, int y, boolean on, boolean hover, boolean disabled) {
        int w = 26;
        int h = 12;
        int track = disabled ? 0xFF2A2D35 : on ? kind(2) : 0xFF3A3F4B;
        if (hover && !disabled) {
            track = lighter(track, 0.15F);
        }
        round(x, y, w, h, track);
        int knob = disabled ? 0xFF555A66 : 0xFFF2F3F6;
        int kx = on ? x + w - 11 : x + 1;
        round(kx, y + 1, 10, h - 2, knob);
    }

    /** Ограничить рисование прямоугольником (в координатах GUI). */
    public static void scissor(int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc);
        int scale = res.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, mc.displayHeight - (y + h) * scale, Math.max(0, w * scale), Math.max(0, h * scale));
    }

    public static void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static void resetColor() {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
