package com.ecoserv.client.v2;

import com.ecoserv.client.ui.UiTheme;
import com.ecoserv.gui.CustomGui;
import com.ecoserv.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 2.0 beta4: рисование элементов своих окон — одинаково в игре и в дизайнере. */
@SideOnly(Side.CLIENT)
public final class GuiRender {

    private static final Map<String, ItemStack> STACKS = new HashMap<String, ItemStack>();

    private GuiRender() {
    }

    public static int argb(int rgb, double alpha) {
        return ((int) Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static ItemStack stack(String key) {
        ItemStack s = STACKS.get(key);
        if (s != null) {
            return s;
        }
        try {
            String[] p = key.split("@");
            Item it = net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(new ResourceLocation(p[0]));
            s = it == null ? ItemStack.EMPTY : new ItemStack(it, 1, p.length > 1 ? Integer.parseInt(p[1]) : 0);
        } catch (Exception e) {
            s = ItemStack.EMPTY;
        }
        STACKS.put(key, s);
        return s;
    }

    /** Строки текста: \n и перенос по ширине. */
    public static List<String> lines(String text, int width) {
        FontRenderer f = Minecraft.getMinecraft().fontRenderer;
        List<String> out = new ArrayList<String>();
        for (String part : text.replace("\\n", "\n").split("\n", -1)) {
            if (part.isEmpty()) {
                out.add("");
            } else {
                out.addAll(f.listFormattedStringToWidth(part, Math.max(8, width)));
            }
        }
        return out;
    }

    /** Окно (фон, рамка, заголовок) в точке (x, y). */
    public static void window(CustomGui g, int x, int y, boolean designer) {
        UiTheme.rect(x, y, g.width, g.height, argb(g.bg, g.bgAlpha));
        if (g.border >= 0) {
            UiTheme.border(x, y, g.width, g.height, argb(g.border, 1));
        }
        String title = designer ? ChatUtil.color(g.title) : g.title;
        if (!title.isEmpty()) {
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(title,
                    x + (g.width - Minecraft.getMinecraft().fontRenderer.getStringWidth(title)) / 2, y + 7, 0xFFFFFF);
        }
        if (g.closeButton) {
            Minecraft.getMinecraft().fontRenderer.drawString("§7✕", x + g.width - 11, y + 5, 0xFFFFFF);
        }
    }

    /**
     * Один элемент. ox, oy — левый верхний угол окна. hover — мышь над элементом. designer — показывать «&»-цвета
     * как цвета и пустые места рамками.
     */
    public static void element(CustomGui.El e, int ox, int oy, boolean hover, boolean designer) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer f = mc.fontRenderer;
        int x = ox + e.x;
        int y = oy + e.y;
        String text = designer ? ChatUtil.color(e.text) : e.text;
        switch (e.kind) {
            case "button": {
                int fill = argb(e.bg, e.bgAlpha);
                if (hover) {
                    fill = UiTheme.lighter(fill, 0.18F);
                }
                if (e.round) {
                    UiTheme.round(x, y, e.w, e.h, UiTheme.darker(fill, 0.35F));
                    UiTheme.round(x, y, e.w, e.h - 1, fill);
                } else {
                    UiTheme.rect(x, y, e.w, e.h, fill);
                }
                if (e.border >= 0) {
                    UiTheme.border(x, y, e.w, e.h, argb(e.border, 1));
                }
                drawText(text, e, x, y, true);
                break;
            }
            case "rect":
                if (e.round) {
                    UiTheme.round(x, y, e.w, e.h, argb(e.bg, e.bgAlpha));
                } else {
                    UiTheme.rect(x, y, e.w, e.h, argb(e.bg, e.bgAlpha));
                }
                if (e.border >= 0) {
                    UiTheme.border(x, y, e.w, e.h, argb(e.border, 1));
                }
                if (!text.isEmpty()) {
                    drawText(text, e, x, y, true);
                }
                break;
            case "line":
                UiTheme.rect(x, y, Math.max(1, e.w), Math.max(1, e.h), argb(e.bg, e.bgAlpha));
                break;
            case "bar": {
                UiTheme.rect(x, y, e.w, e.h, argb(UiTheme.darker(0xFF000000 | e.bg, 0.65F), Math.max(0.6, e.bgAlpha)));
                double v = designer ? 0.6 : e.value;
                UiTheme.rect(x, y, (int) Math.round(e.w * v), e.h, argb(e.bg, e.bgAlpha));
                if (e.border >= 0) {
                    UiTheme.border(x, y, e.w, e.h, argb(e.border, 1));
                }
                if (!text.isEmpty()) {
                    drawText(text, e, x, y, true);
                }
                break;
            }
            case "item": {
                if (e.bgAlpha > 0.01) {
                    UiTheme.rect(x, y, e.w, e.h, argb(e.bg, e.bgAlpha));
                }
                if (hover && !designer) {
                    UiTheme.rect(x, y, e.w, e.h, 0x40FFFFFF);
                }
                ItemStack st = stack(e.item);
                if (!st.isEmpty()) {
                    float s = Math.min(e.w, e.h) / 16.0F;
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(x + (e.w - 16 * s) / 2.0F, y + (e.h - 16 * s) / 2.0F, 0);
                    GlStateManager.scale(s, s, 1);
                    RenderHelper.enableGUIStandardItemLighting();
                    mc.getRenderItem().renderItemAndEffectIntoGUI(st, 0, 0);
                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.popMatrix();
                    GlStateManager.disableLighting();
                    GlStateManager.disableDepth();
                }
                if (!text.isEmpty()) {
                    // число в углу, как у стопки
                    f.drawStringWithShadow(text, x + e.w - f.getStringWidth(text), y + e.h - 8, 0xFFFFFF);
                }
                GlStateManager.enableDepth();
                break;
            }
            case "image": {
                if (e.image.isEmpty()) {
                    UiTheme.rect(x, y, e.w, e.h, 0x60404858);
                    UiTheme.border(x, y, e.w, e.h, 0xFF606878);
                    UiTheme.center("§7🖼", x + e.w / 2, y + e.h / 2 - 4, 0xFFFFFF);
                } else {
                    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                    GlStateManager.enableBlend();
                    mc.getTextureManager().bindTexture(new ResourceLocation("ecoserv", "textures/custom/" + e.image + ".png"));
                    Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, e.w, e.h, e.w, e.h);
                }
                if (hover && !designer && !e.steps.isEmpty()) {
                    UiTheme.rect(x, y, e.w, e.h, 0x30FFFFFF);
                }
                if (e.border >= 0) {
                    UiTheme.border(x, y, e.w, e.h, argb(e.border, 1));
                }
                break;
            }
            case "input":
                UiTheme.rect(x, y, e.w, e.h, argb(e.bg == 0x2F5BB0 ? 0x0C0E12 : e.bg, Math.max(0.5, e.bgAlpha)));
                UiTheme.border(x, y, e.w, e.h, argb(e.border >= 0 ? e.border : 0x383D49, 1));
                if (designer) {
                    String hint = text.isEmpty() ? "§8" + (e.tooltip.isEmpty() ? "поле ввода" : e.tooltip) : text;
                    f.drawString(UiTheme.trim(hint, e.w - 6), x + 4, y + (e.h - 8) / 2, argb(e.color, 1));
                }
                break;
            default:
                if (e.bgAlpha > 0.01) {
                    UiTheme.rect(x, y, e.w, e.h, argb(e.bg, e.bgAlpha));
                }
                drawText(text, e, x, y, false);
        }
    }

    private static void drawText(String text, CustomGui.El e, int x, int y, boolean centerV) {
        if (text.isEmpty()) {
            return;
        }
        FontRenderer f = Minecraft.getMinecraft().fontRenderer;
        double s = e.scale;
        int innerW = (int) ((e.w - 4) / s);
        List<String> ls = lines(text, innerW);
        int lineH = 10;
        int total = ls.size() * lineH - 2;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale((float) s, (float) s, 1.0F);
        int boxW = (int) (e.w / s);
        int boxH = (int) (e.h / s);
        int ty = centerV ? (boxH - total) / 2 : 2;
        int color = 0xFF000000 | e.color;
        for (int i = 0; i < ls.size(); i++) {
            String l = ls.get(i);
            int w = f.getStringWidth(l);
            int tx = e.align == 1 ? (boxW - w) / 2 : e.align == 2 ? boxW - w - 2 : 2;
            if (e.shadow) {
                f.drawStringWithShadow(l, tx, ty + i * lineH, color);
            } else {
                f.drawString(l, tx, ty + i * lineH, color);
            }
        }
        GlStateManager.popMatrix();
    }
}
