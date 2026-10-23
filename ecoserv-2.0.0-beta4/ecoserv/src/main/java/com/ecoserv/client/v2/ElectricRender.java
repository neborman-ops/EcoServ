package com.ecoserv.client.v2;

import com.ecoserv.electric.ElectricTypes;
import com.ecoserv.electric.TileElectric;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;

/** Провода электросети: провисающие линии, жёлтые — под током. Подписи — в очках/режиме /dev. */
@SideOnly(Side.CLIENT)
public class ElectricRender {

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (mc.world == null || view == null) {
            return;
        }
        float pt = event.getPartialTicks();
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.glLineWidth(3.0F);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        boolean labels = DevRender.visible();
        ArrayList<TileEntity> tiles = new ArrayList<TileEntity>(mc.world.loadedTileEntityList);
        for (TileEntity te : tiles) {
            if (!(te instanceof TileElectric)) {
                continue;
            }
            TileElectric t = (TileElectric) te;
            BlockPos a = t.getPos();
            if (a.distanceSq(vx, vy, vz) > 80.0D * 80.0D || t.outputs.isEmpty()) {
                continue;
            }
            boolean live = t.out > 0;
            float r = live ? 1.0F : 0.15F;
            float g = live ? 0.85F : 0.15F;
            float b = live ? 0.2F : 0.17F;
            for (BlockPos o : t.outputs) {
                double x1 = a.getX() + 0.5D - vx;
                double y1 = a.getY() + 0.5D - vy;
                double z1 = a.getZ() + 0.5D - vz;
                double x2 = o.getX() + 0.5D - vx;
                double y2 = o.getY() + 0.5D - vy;
                double z2 = o.getZ() + 0.5D - vz;
                double len = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1));
                double sag = Math.min(2.0D, len * 0.08D);
                buf.begin(3, DefaultVertexFormats.POSITION_COLOR);
                for (int i = 0; i <= 16; i++) {
                    double s = i / 16.0D;
                    double y = y1 + (y2 - y1) * s - Math.sin(Math.PI * s) * sag;
                    buf.pos(x1 + (x2 - x1) * s, y, z1 + (z2 - z1) * s).color(r, g, b, 1.0F).endVertex();
                }
                tess.draw();
            }
        }
        GlStateManager.enableTexture2D();
        if (labels) {
            float yaw = mc.getRenderManager().playerViewY;
            float pitch = mc.getRenderManager().playerViewX;
            boolean third = mc.gameSettings.thirdPersonView == 2;
            for (TileEntity te : tiles) {
                if (!(te instanceof TileElectric) || te.getPos().distanceSq(vx, vy, vz) > 20.0D * 20.0D) {
                    continue;
                }
                TileElectric t = (TileElectric) te;
                String label = "§e⚡ " + (t.label.isEmpty() ? ElectricTypes.TITLES[t.type()] : t.label) + " §7" + t.in + "→" + t.out;
                EntityRenderer.drawNameplate(mc.fontRenderer, label, (float) (te.getPos().getX() + 0.5D - vx),
                        (float) (te.getPos().getY() + 1.3D - vy), (float) (te.getPos().getZ() + 0.5D - vz), 0, yaw, pitch, third, false);
            }
        } else {
            // измерители видны всем
            float yaw = mc.getRenderManager().playerViewY;
            float pitch = mc.getRenderManager().playerViewX;
            boolean third = mc.gameSettings.thirdPersonView == 2;
            for (TileEntity te : tiles) {
                if (te instanceof TileElectric && ((TileElectric) te).type() == ElectricTypes.METER
                        && te.getPos().distanceSq(vx, vy, vz) < 16.0D * 16.0D) {
                    EntityRenderer.drawNameplate(mc.fontRenderer, "§e⚡ " + ((TileElectric) te).out + " ед.",
                            (float) (te.getPos().getX() + 0.5D - vx), (float) (te.getPos().getY() + 1.3D - vy),
                            (float) (te.getPos().getZ() + 0.5D - vz), 0, yaw, pitch, third, false);
                }
            }
        }
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
