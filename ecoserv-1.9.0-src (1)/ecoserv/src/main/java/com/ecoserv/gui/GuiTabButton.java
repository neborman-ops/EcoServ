package com.ecoserv.gui;

import com.ecoserv.Ref;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Круглая кнопка рядом с инвентарём в стиле кнопки-планеты (1.8.0): без серой «майнкрафтовской» подложки,
 * сам предмет со свечением по контуру. 0 — магазин (монета крутится), 1 — обмен (стрелки ездят).
 * Текстура: 2 столбца (обычная / при наведении — золотое свечение) × {@link #FRAMES} кадров по 26×26.
 */
@SideOnly(Side.CLIENT)
public class GuiTabButton extends GuiButton {

    public static final ResourceLocation COIN = new ResourceLocation(Ref.MODID, "textures/gui/coin_button.png");
    public static final ResourceLocation TRADE = new ResourceLocation(Ref.MODID, "textures/gui/trade_button.png");
    public static final int SIZE = 26;
    public static final int FRAMES = 16;
    private static final long FRAME_MS = 95L;

    private final int icon;

    public GuiTabButton(int id, int x, int y, int icon) {
        super(id, x, y, SIZE, SIZE, "");
        this.icon = icon;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        // при наведении анимация быстрее — кнопка «оживает»
        long ms = hovered ? FRAME_MS / 2 : FRAME_MS;
        int frame = (int) (System.currentTimeMillis() / ms % FRAMES);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        mc.getTextureManager().bindTexture(icon == 1 ? TRADE : COIN);
        drawModalRectWithCustomSizedTexture(x, y, hovered ? SIZE : 0, frame * SIZE, SIZE, SIZE,
                SIZE * 2.0F, (float) (SIZE * FRAMES));
        GlStateManager.disableBlend();
    }
}
