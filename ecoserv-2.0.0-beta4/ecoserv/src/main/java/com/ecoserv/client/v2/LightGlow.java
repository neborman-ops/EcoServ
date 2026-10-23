package com.ecoserv.client.v2;

import com.ecoserv.client.ClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * 2.0 beta4: видимое свечение источников света — мягкий цветной ореол, повёрнутый к камере. Работает и без
 * шейдеров; со свечением (Bloom) светится ещё сильнее.
 */
@SideOnly(Side.CLIENT)
public class LightGlow {

    private static DynamicTexture tex;

    private static void build() {
        int n = 64;
        tex = new DynamicTexture(n, n);
        int[] px = tex.getTextureData();
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1;
                double dy = (y + 0.5) / n * 2 - 1;
                double r = Math.sqrt(dx * dx + dy * dy);
                double a = Math.max(0.0D, 1.0D - r);
                a = a * a * (0.35D + 0.65D * a);
                px[y * n + x] = ((int) (Math.min(1.0D, a) * 255) << 24) | 0xFFFFFF;
            }
        }
        tex.updateDynamicTexture();
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view == null || mc.world == null || LightingFx.LIGHTS.isEmpty() || !ClientSettings.get().lightGlow || !LightingFx.on()) {
            return;
        }
        if (tex == null) {
            build();
        }
        float pt = event.getPartialTicks();
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        float yaw = mc.getRenderManager().playerViewY;
        float pitch = mc.getRenderManager().playerViewX;
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableFog();
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(tex.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        double time = (System.nanoTime() / 1.0E9D);
        for (LightingFx.Light l : LightingFx.LIGHTS) {
            double dx = l.x - vx;
            double dy = l.y - vy;
            double dz = l.z - vz;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > 96 * 96) {
                continue;
            }
            float r = (l.color >> 16 & 255) / 255.0F;
            float g = (l.color >> 8 & 255) / 255.0F;
            float bl = (l.color & 255) / 255.0F;
            float k = (float) Math.min(1.5D, 0.25D + l.brightness * 0.35D);
            float flicker = 1.0F + (float) Math.sin(time * 3.1D + l.x * 1.7D + l.z) * 0.03F;
            double size = (0.6D + Math.min(4.0D, l.range * 0.08D)) * flicker;
            GlStateManager.pushMatrix();
            GlStateManager.translate(dx, dy, dz);
            GlStateManager.rotate(-yaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(pitch, 1.0F, 0.0F, 0.0F);
            // широкий мягкий ореол и яркое ядро
            for (int pass = 0; pass < 2; pass++) {
                double s = pass == 0 ? size : size * 0.28D;
                float a = pass == 0 ? 0.55F * k : 0.9F * k;
                float cr = pass == 0 ? r : Math.min(1.0F, r * 0.5F + 0.5F);
                float cg = pass == 0 ? g : Math.min(1.0F, g * 0.5F + 0.5F);
                float cb = pass == 0 ? bl : Math.min(1.0F, bl * 0.5F + 0.5F);
                b.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
                b.pos(-s, -s, 0).tex(0, 1).color(cr, cg, cb, Math.min(1.0F, a)).endVertex();
                b.pos(s, -s, 0).tex(1, 1).color(cr, cg, cb, Math.min(1.0F, a)).endVertex();
                b.pos(s, s, 0).tex(1, 0).color(cr, cg, cb, Math.min(1.0F, a)).endVertex();
                b.pos(-s, s, 0).tex(0, 0).color(cr, cg, cb, Math.min(1.0F, a)).endVertex();
                t.draw();
            }
            GlStateManager.popMatrix();
        }
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }
}
