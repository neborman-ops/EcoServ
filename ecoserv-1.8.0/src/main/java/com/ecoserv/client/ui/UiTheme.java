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

    public static final int WINDOW = 0xF2171920;
    public static final int TITLE = 0xFF0F1116;
    public static final int SIDEBAR = 0xFF121419;
    public static final int BORDER = 0xFF2C303B;
    public static final int BORDER_LIGHT = 0xFF3A3F4C;
    public static final int ROW = 0xFF1B1E26;
    public static final int ROW_HOVER = 0xFF252935;
    public static final int ROW_HL = 0xFF1E2A42;
    public static final int TEXT = 0xFFE8EAF0;
    public static final int DIM = 0xFF9CA3B4;
    public static final int MUTED = 0xFF6A7183;
    public static final int ACCENT = 0xFF4E8CFF;
    public static final int INPUT = 0xFF0C0E12;
    public static final int INPUT_BORDER = 0xFF383D49;
    public static final int SHADE = 0xB0000000;

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
                sprite.draw(x, y, true);
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
