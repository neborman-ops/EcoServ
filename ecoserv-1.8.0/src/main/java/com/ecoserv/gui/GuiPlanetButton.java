package com.ecoserv.gui;

import com.ecoserv.Ref;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Кнопка-планета (рисунок Земли 32×32) рядом с инвентарём: меню миров и подмиров. */
@SideOnly(Side.CLIENT)
public class GuiPlanetButton extends GuiButton {

    public static final ResourceLocation TEXTURE = new ResourceLocation(Ref.MODID, "textures/gui/planet_button.png");
    public static final int SIZE = 32;

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
        drawModalRectWithCustomSizedTexture(x, y, hovered ? SIZE : 0, 0, SIZE, SIZE, 64.0F, 32.0F);
        GlStateManager.disableBlend();
    }
}
