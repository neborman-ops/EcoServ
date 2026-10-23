package com.ecoserv.client.v2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/** Редактор сцены в мире: рамки объектов, гизмо перемещения/поворота/размера, подписи; матрицы для луча мыши. */
@SideOnly(Side.CLIENT)
public class EditorRender {

    private static final FloatBuffer PB = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer MB = BufferUtils.createFloatBuffer(16);
    /** Наведённая ручка гизмо (для подсветки). */
    public static int hoverHandle = -1;

    /** Размер гизмо в блоках — чтобы на экране был одинаковым. */
    public static double gizmoSize(Vec3d p) {
        double dx = p.x - CameraFx.ex;
        double dy = p.y - CameraFx.ey;
        double dz = p.z - CameraFx.ez;
        return Math.max(0.6D, Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.14D);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRender(RenderWorldLastEvent event) {
        if (!EditorState.open) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            return;
        }
        float pt = event.getPartialTicks();
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        PB.clear();
        MB.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PB);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MB);
        PB.get(EditorState.P);
        MB.get(EditorState.M);
        EditorState.camX = vx;
        EditorState.camY = vy;
        EditorState.camZ = vz;
        EditorState.haveMatrices = true;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.disableCull();
        for (EditorState.Obj o : EditorState.OBJS) {
            boolean sel = o == EditorState.selected;
            int c = o.color;
            float r = (c >> 16 & 255) / 255.0F;
            float g = (c >> 8 & 255) / 255.0F;
            float b = (c & 255) / 255.0F;
            AxisAlignedBB bb = o.box().offset(-vx, -vy, -vz);
            if (sel) {
                RenderGlobal.renderFilledBox(bb, 1.0F, 0.85F, 0.2F, 0.12F);
                GlStateManager.glLineWidth(3.0F);
                RenderGlobal.drawSelectionBoundingBox(bb, 1.0F, 0.85F, 0.2F, 1.0F);
            } else {
                GlStateManager.glLineWidth(1.5F);
                RenderGlobal.drawSelectionBoundingBox(bb, r, g, b, 0.8F);
            }
            if ("light".equals(o.type) && sel) {
                drawRing(o.x - vx, o.y - vy, o.z - vz, o.sx, 1, 0.85F, 0.3F, 0.5F, 1);
            }
            if (("panel".equals(o.type) || "npc".equals(o.type) || "campoint".equals(o.type) || "light".equals(o.type)) && o.can("yaw")) {
                // куда смотрит
                double yr = Math.toRadians("panel".equals(o.type) ? o.yaw - 180 : o.yaw);
                double pr = Math.toRadians(o.pitch);
                Vec3d f = new Vec3d(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
                Vec3d p = o.pivot();
                line(p.x - vx, p.y - vy, p.z - vz, p.x - vx + f.x * 1.2, p.y - vy + f.y * 1.2, p.z - vz + f.z * 1.2, 1, 1, 1, 0.8F);
            }
        }
        // гизмо — поверх всего
        EditorState.Obj s = EditorState.selected;
        if (s != null) {
            GlStateManager.disableDepth();
            Vec3d p = s.pivot();
            double L = gizmoSize(p);
            double px = p.x - vx;
            double py = p.y - vy;
            double pz = p.z - vz;
            GlStateManager.glLineWidth(3.0F);
            if (EditorState.mode == 1) {
                if (s.can("yaw")) {
                    drawRing(px, py, pz, L, hoverHandle == 10 ? 1 : 0.2F, hoverHandle == 10 ? 1 : 0.9F, hoverHandle == 10 ? 0.4F : 0.2F, 1, 1);
                }
                if (s.can("pitch")) {
                    drawRingX(px, py, pz, L * 0.8, s.yaw, hoverHandle == 11 ? 1 : 0.9F, hoverHandle == 11 ? 1 : 0.2F, 0.2F);
                }
            } else {
                axis(px, py, pz, L, 0, hoverHandle == 0);
                axis(px, py, pz, L, 1, hoverHandle == 1);
                axis(px, py, pz, L, 2, hoverHandle == 2);
                if (EditorState.mode == 0) {
                    double q = L * 0.22;
                    AxisAlignedBB c = new AxisAlignedBB(px, py - 0.01, pz, px + q, py + 0.01, pz + q);
                    RenderGlobal.renderFilledBox(c, 1, 1, 0.3F, hoverHandle == 3 ? 0.8F : 0.4F);
                }
            }
            GlStateManager.enableDepth();
        }
        GlStateManager.enableTexture2D();
        // подписи
        float yaw = mc.getRenderManager().playerViewY;
        float pitch = mc.getRenderManager().playerViewX;
        for (EditorState.Obj o : EditorState.OBJS) {
            Vec3d p = o.pivot();
            double dx = p.x - vx;
            double dy = o.box().maxY + 0.35 - vy;
            double dz = p.z - vz;
            if (dx * dx + dy * dy + dz * dz < 28 * 28 || o == EditorState.selected) {
                EntityRenderer.drawNameplate(mc.fontRenderer, (o == EditorState.selected ? "§e" : "§f") + o.label, (float) dx, (float) dy,
                        (float) dz, 0, yaw, pitch, false, false);
            }
        }
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
    }

    /** Ручка оси: 0 — X (красная), 1 — Y (зелёная), 2 — Z (синяя). */
    public static Vec3d axisDir(int a) {
        return a == 0 ? new Vec3d(1, 0, 0) : a == 1 ? new Vec3d(0, 1, 0) : new Vec3d(0, 0, 1);
    }

    private static void axis(double px, double py, double pz, double L, int a, boolean hover) {
        Vec3d d = axisDir(a);
        float r = a == 0 ? 1 : 0.2F;
        float g = a == 1 ? 1 : 0.2F;
        float b = a == 2 ? 1 : 0.25F;
        if (hover) {
            r = 1;
            g = 1;
            b = 0.3F;
        }
        line(px, py, pz, px + d.x * L, py + d.y * L, pz + d.z * L, r, g, b, 1);
        double h = L * 0.09;
        double ex = px + d.x * L;
        double ey = py + d.y * L;
        double ez = pz + d.z * L;
        AxisAlignedBB c = new AxisAlignedBB(ex - h, ey - h, ez - h, ex + h, ey + h, ez + h);
        RenderGlobal.renderFilledBox(c, r, g, b, 1.0F);
    }

    static void line(double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder bb = t.getBuffer();
        bb.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        bb.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        bb.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        t.draw();
    }

    static void drawRing(double cx, double cy, double cz, double R, float r, float g, float b, float a, int axisY) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder bb = t.getBuffer();
        bb.begin(3, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 48; i++) {
            double an = Math.PI * 2 * i / 48;
            bb.pos(cx + Math.cos(an) * R, cy, cz + Math.sin(an) * R).color(r, g, b, a).endVertex();
        }
        t.draw();
    }

    static void drawRingX(double cx, double cy, double cz, double R, float yaw, float r, float g, float b) {
        // кольцо в вертикальной плоскости, перпендикулярной направлению взгляда объекта
        double yr = Math.toRadians(yaw);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder bb = t.getBuffer();
        bb.begin(3, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 48; i++) {
            double an = Math.PI * 2 * i / 48;
            double fwd = Math.cos(an) * R;
            double up = Math.sin(an) * R;
            // вперёд = -sin(yaw), cos(yaw)
            bb.pos(cx - Math.sin(yr) * fwd, cy + up, cz + Math.cos(yr) * fwd).color(r, g, b, 1).endVertex();
        }
        t.draw();
    }
}
