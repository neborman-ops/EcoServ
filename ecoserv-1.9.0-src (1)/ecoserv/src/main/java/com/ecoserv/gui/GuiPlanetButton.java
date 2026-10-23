package com.ecoserv.gui;

import com.ecoserv.Ref;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Кнопка-планета (крутящаяся Земля 32×32) рядом с инвентарём: меню миров и подмиров. */
@SideOnly(Side.CLIENT)
public class GuiPlanetButton extends GuiButton {

    public static final ResourceLocation TEXTURE = new ResourceLocation(Ref.MODID, "textures/gui/planet_button.png");
    public static final int SIZE = 32;
    public static final int FRAMES = 16;

    public GuiPlanetButton(int id, int x, int y) {
        super(id, x, y, SIZE, SIZE, "");
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        mc.getTextureManager().bindTexture(TEXTURE);
        // 1.8.0: планета крутится (16 кадров), при наведении — быстрее и с золотым свечением
        int frame = (int) (System.currentTimeMillis() / (hovered ? 70L : 140L) % FRAMES);
        drawModalRectWithCustomSizedTexture(x, y, hovered ? SIZE : 0, frame * SIZE, SIZE, SIZE, 64.0F, (float) (SIZE * FRAMES));
        GlStateManager.disableBlend();
    }
}
