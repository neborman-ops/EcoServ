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
                circle(o.x - vx, o.y - vy, o.z - vz, new Vec3d(0, 1, 0), o.sx, 1, 0.85F, 0.3F, 0.5F);
            }
            if ("camera".equals(o.type)) {
                drawCamera(o, vx, vy, vz, sel);
            } else if ("display".equals(o.type)) {
                // оси дисплея (локальные)
                Vec3d p = o.pivot();
                for (int a = 0; a < 3; a++) {
                    Vec3d d = o.rotate(axisDir(a)).scale((a == 0 ? o.sx : a == 1 ? o.sy : o.sz) / 2 + 0.15);
                    line(p.x - vx, p.y - vy, p.z - vz, p.x - vx + d.x, p.y - vy + d.y, p.z - vz + d.z,
                            a == 0 ? 1 : 0.3F, a == 1 ? 1 : 0.3F, a == 2 ? 1 : 0.3F, sel ? 0.9F : 0.35F);
                }
            } else if (("panel".equals(o.type) || "npc".equals(o.type) || "campoint".equals(o.type) || "light".equals(o.type)) && o.can("yaw")) {
                Vec3d f = o.forward();
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
                Vec3d[] axes = rotationAxes(s);
                for (int i = 0; i < 3; i++) {
                    if (axes[i] == null) {
                        continue;
                    }
                    boolean h = hoverHandle == 20 + i;
                    circle(px, py, pz, axes[i], L, h ? 1 : i == 0 ? 1 : 0.25F, h ? 1 : i == 1 ? 1 : 0.25F, h ? 0.3F : i == 2 ? 1 : 0.25F, 1);
                }
                if (s.can("rot3") && EditorState.haveMatrices) {
                    // шар свободного вращения
                    Vec3d[] vb = EditorState.viewBasis();
                    GlStateManager.glLineWidth(1.5F);
                    circle(px, py, pz, vb[2], L * 1.12, 1, 1, 1, hoverHandle == 23 ? 0.9F : 0.35F);
                }
            } else {
                for (int a = 0; a < 3; a++) {
                    Vec3d d = EditorState.mode == 2 && s.can("rot3") ? s.rotate(axisDir(a)) : axisDir(a);
                    axis(px, py, pz, L, a, d, hoverHandle == a, EditorState.mode == 2);
                }
                if (EditorState.mode == 0) {
                    double q = L * 0.25;
                    plane(px, py, pz, q, 1, hoverHandle == 3, 0.3F, 1, 0.3F);
                    plane(px, py, pz, q, 2, hoverHandle == 4, 0.3F, 0.3F, 1);
                    plane(px, py, pz, q, 0, hoverHandle == 5, 1, 0.3F, 0.3F);
                    double c = L * 0.07;
                    RenderGlobal.renderFilledBox(new AxisAlignedBB(px - c, py - c, pz - c, px + c, py + c, pz + c), 1, 1, 1,
                            hoverHandle == 6 ? 1.0F : 0.6F);
                } else if (EditorState.mode == 2) {
                    double c = L * 0.09;
                    RenderGlobal.renderFilledBox(new AxisAlignedBB(px - c, py - c, pz - c, px + c, py + c, pz + c), 1, 1, 1,
                            hoverHandle == 7 ? 1.0F : 0.6F);
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

    /** Оси колец поворота: X, Y, Z (null — нельзя). */
    public static Vec3d[] rotationAxes(EditorState.Obj s) {
        if (s.can("rot3")) {
            return new Vec3d[]{new Vec3d(1, 0, 0), new Vec3d(0, 1, 0), new Vec3d(0, 0, 1)};
        }
        Vec3d f = s.forward();
        Vec3d right = f.crossProduct(new Vec3d(0, 1, 0));
        if (right.x * right.x + right.y * right.y + right.z * right.z < 1e-6) {
            double yr = Math.toRadians(s.yaw);
            right = new Vec3d(-Math.cos(yr), 0, -Math.sin(yr));
        }
        return new Vec3d[]{s.can("pitch") ? right.normalize() : null, s.can("yaw") ? new Vec3d(0, 1, 0) : null,
                s.can("roll") ? f.normalize() : null};
    }

    private static void axis(double px, double py, double pz, double L, int a, Vec3d d, boolean hover, boolean cubeTip) {
        float r = a == 0 ? 1 : 0.2F;
        float g = a == 1 ? 1 : 0.2F;
        float b = a == 2 ? 1 : 0.25F;
        if (hover) {
            r = 1;
            g = 1;
            b = 0.3F;
        }
        line(px, py, pz, px + d.x * L, py + d.y * L, pz + d.z * L, r, g, b, 1);
        double h = L * (cubeTip ? 0.08 : 0.06);
        double ex = px + d.x * L;
        double ey = py + d.y * L;
        double ez = pz + d.z * L;
        if (cubeTip) {
            RenderGlobal.renderFilledBox(new AxisAlignedBB(ex - h, ey - h, ez - h, ex + h, ey + h, ez + h), r, g, b, 1.0F);
        } else {
            cone(ex, ey, ez, d, L * 0.18, L * 0.06, r, g, b);
        }
    }

    /** Наконечник стрелки. */
    private static void cone(double x, double y, double z, Vec3d d, double len, double rad, float r, float g, float b) {
        Vec3d u = Math.abs(d.y) < 0.9 ? d.crossProduct(new Vec3d(0, 1, 0)).normalize() : d.crossProduct(new Vec3d(1, 0, 0)).normalize();
        Vec3d v = d.crossProduct(u).normalize();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder bb = t.getBuffer();
        bb.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        bb.pos(x + d.x * len, y + d.y * len, z + d.z * len).color(r, g, b, 1.0F).endVertex();
        for (int i = 0; i <= 12; i++) {
            double an = Math.PI * 2 * i / 12;
            double cx = Math.cos(an) * rad;
            double cy = Math.sin(an) * rad;
            bb.pos(x + u.x * cx + v.x * cy, y + u.y * cx + v.y * cy, z + u.z * cx + v.z * cy).color(r * 0.8F, g * 0.8F, b * 0.8F, 1.0F).endVertex();
        }
        t.draw();
    }

    /** Квадратик плоскости: normalAxis 0 — YZ, 1 — XZ, 2 — XY. */
    private static void plane(double px, double py, double pz, double q, int normalAxis, boolean hover, float r, float g, float b) {
        double o = q * 0.35;
        AxisAlignedBB c;
        if (normalAxis == 1) {
            c = new AxisAlignedBB(px + o, py - 0.005, pz + o, px + o + q, py + 0.005, pz + o + q);
        } else if (normalAxis == 2) {
            c = new AxisAlignedBB(px + o, py + o, pz - 0.005, px + o + q, py + o + q, pz + 0.005);
        } else {
            c = new AxisAlignedBB(px - 0.005, py + o, pz + o, px + 0.005, py + o + q, pz + o + q);
        }
        if (hover) {
            r = 1;
            g = 1;
            b = 0.3F;
        }
        RenderGlobal.renderFilledBox(c, r, g, b, hover ? 0.85F : 0.4F);
    }

    /** Смещение и размер квадратика плоскости (для попадания мышью). */
    public static double[] planeRect(double L) {
        double q = L * 0.25;
        return new double[]{q * 0.35, q * 0.35 + q};
    }

    /** Значок камеры: корпус и пирамида обзора по FOV. */
    private static void drawCamera(EditorState.Obj o, double vx, double vy, double vz, boolean sel) {
        Vec3d p = o.pivot().subtract(vx, vy, vz);
        Vec3d f = o.forward().normalize();
        Vec3d right = f.crossProduct(new Vec3d(0, 1, 0));
        if (right.x * right.x + right.y * right.y + right.z * right.z < 1e-6) {
            right = new Vec3d(1, 0, 0);
        }
        right = right.normalize();
        Vec3d up = right.crossProduct(f).normalize();
        double rr = Math.toRadians(o.roll);
        Vec3d r2 = right.scale(Math.cos(rr)).add(up.scale(Math.sin(rr)));
        Vec3d u2 = up.scale(Math.cos(rr)).subtract(right.scale(Math.sin(rr)));
        double fov = Math.toRadians(Math.max(10, Math.min(150, o.sx)));
        double depth = sel ? 3.0 : 1.4;
        double hh = Math.tan(fov / 2) * depth;
        double hw = hh * 16.0 / 9.0;
        float cr = sel ? 1 : 1;
        float cg = sel ? 0.85F : 0.55F;
        float cb = sel ? 0.2F : 0.25F;
        Vec3d c = p.add(f.scale(depth));
        Vec3d[] k = {c.add(r2.scale(hw)).add(u2.scale(hh)), c.add(r2.scale(-hw)).add(u2.scale(hh)),
                c.add(r2.scale(-hw)).add(u2.scale(-hh)), c.add(r2.scale(hw)).add(u2.scale(-hh))};
        GlStateManager.glLineWidth(sel ? 2.5F : 1.5F);
        for (int i = 0; i < 4; i++) {
            line(p.x, p.y, p.z, k[i].x, k[i].y, k[i].z, cr, cg, cb, 0.9F);
            Vec3d n = k[(i + 1) % 4];
            line(k[i].x, k[i].y, k[i].z, n.x, n.y, n.z, cr, cg, cb, 0.9F);
        }
        // «верх» кадра — треугольник
        Vec3d t1 = c.add(u2.scale(hh * 1.15)).add(r2.scale(-hw * 0.25));
        Vec3d t2 = c.add(u2.scale(hh * 1.15)).add(r2.scale(hw * 0.25));
        Vec3d t3 = c.add(u2.scale(hh * 1.45));
        line(t1.x, t1.y, t1.z, t2.x, t2.y, t2.z, cr, cg, cb, 0.9F);
        line(t2.x, t2.y, t2.z, t3.x, t3.y, t3.z, cr, cg, cb, 0.9F);
        line(t3.x, t3.y, t3.z, t1.x, t1.y, t1.z, cr, cg, cb, 0.9F);
        // корпус
        double b = 0.18;
        RenderGlobal.renderFilledBox(new AxisAlignedBB(p.x - b, p.y - b, p.z - b, p.x + b, p.y + b, p.z + b), 0.15F, 0.15F, 0.18F, 0.9F);
    }

    static void line(double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder bb = t.getBuffer();
        bb.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        bb.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        bb.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        t.draw();
    }

    /** Окружность вокруг оси n в точке (cx, cy, cz). */
    static void circle(double cx, double cy, double cz, Vec3d n, double R, float r, float g, float b, float a) {
        Vec3d nn = n.normalize();
        Vec3d u = Math.abs(nn.y) < 0.9 ? nn.crossProduct(new Vec3d(0, 1, 0)).normalize() : nn.crossProduct(new Vec3d(1, 0, 0)).normalize();
        Vec3d v = nn.crossProduct(u).normalize();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder bb = t.getBuffer();
        bb.begin(3, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 64; i++) {
            double an = Math.PI * 2 * i / 64;
            double x = Math.cos(an) * R;
            double y = Math.sin(an) * R;
            bb.pos(cx + u.x * x + v.x * y, cy + u.y * x + v.y * y, cz + u.z * x + v.z * y).color(r, g, b, a).endVertex();
        }
        t.draw();
    }
}
