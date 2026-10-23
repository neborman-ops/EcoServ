package com.ecoserv.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Мелкие помощники отрисовки. */
@SideOnly(Side.CLIENT)
public final class GuiUtil {

    public static final int TEX_SIZE = 256;

    private GuiUtil() {
    }

    public static void bind(ResourceLocation texture) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
    }

    public static void rect(int x, int y, int u, int v, int width, int height) {
        Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, width, height, TEX_SIZE, TEX_SIZE);
    }

    public static void fill(int x, int y, int width, int height, int color) {
        Gui.drawRect(x, y, x + width, y + height, color);
    }

    public static void drawItem(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        mc.getRenderItem().zLevel = 100.0F;
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, stack, x, y, null);
        mc.getRenderItem().zLevel = 0.0F;
        GlStateManager.disableDepth();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static String trim(String text, int maxWidth) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.fontRenderer.getStringWidth(text) <= maxWidth) {
            return text;
        }
        return mc.fontRenderer.trimStringToWidth(text, maxWidth - 6) + "...";
    }

    public static double parseDouble(String text, double fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(text.trim().replace(',', '.').replace(" ", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Сумма денег: понимает пробелы, запятую и сокращения 58k / 58к = 58 000,
     * 1.5m / 1,5м = 1 500 000. Отрицательные и нечисловые значения -> fallback.
     */
    public static double parseMoney(String text, double fallback) {
        if (text == null) {
            return fallback;
        }
        String s = text.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "").replace(" ", "")
                .replace(',', '.');
        if (s.isEmpty()) {
            return fallback;
        }
        double multiplier = 1.0D;
        char last = s.charAt(s.length() - 1);
        if (last == 'k' || last == 'к') {
            multiplier = 1_000.0D;
        } else if (last == 'm' || last == 'м') {
            multiplier = 1_000_000.0D;
        } else if (last == 'b' || last == 'б') {
            multiplier = 1_000_000_000.0D;
        }
        if (multiplier > 1.0D) {
            s = s.substring(0, s.length() - 1);
        }
        try {
            double value = Double.parseDouble(s) * multiplier;
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D) {
                return fallback;
            }
            return value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static int parseInt(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(text.trim().replace(" ", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static long parseLong(String text, long fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Long.parseLong(text.trim().replace(" ", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
