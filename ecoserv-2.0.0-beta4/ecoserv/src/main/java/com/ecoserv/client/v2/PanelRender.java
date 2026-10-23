package com.ecoserv.client.v2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/** 3D-панели в мире (2.0 beta3): рисует окно в мире и нажатие кнопок взглядом + ПКМ. */
@SideOnly(Side.CLIENT)
public class PanelRender {

    static final class El {
        int index;
        String kind;
        String text;
        int color;
        int bg;
        double value;
        // раскладка (пиксели панели)
        float y0;
        float y1;
        List<String> lines = new ArrayList<String>();
    }

    public static final class P {
        public String id;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public double w;
        public double h;
        public double scale;
        int bg;
        double bgAlpha;
        int border;
        boolean billboard;
        double viewDistance;
        final List<El> els = new ArrayList<El>();
        // базис
        double[] ex = new double[3];
        double[] ey = new double[3];
        double[] n = new double[3];
        float pw;
        float ph;
        int hover = -1;
    }

    public static final List<P> PANELS = new ArrayList<P>();

    public static void register() {
        ClientV2.on("panels", d -> {
            PANELS.clear();
            NBTTagList l = d.getTagList("p", 10);
            for (int i = 0; i < l.tagCount(); i++) {
                NBTTagCompound t = l.getCompoundTagAt(i);
                P p = new P();
                p.id = t.getString("id");
                p.x = t.getDouble("x");
                p.y = t.getDouble("y");
                p.z = t.getDouble("z");
                p.yaw = t.getFloat("yaw");
                p.pitch = t.getFloat("pitch");
                p.w = t.getDouble("w");
                p.h = t.getDouble("h");
                p.scale = Math.max(0.1D, t.getDouble("s"));
                p.bg = t.getInteger("bg");
                p.bgAlpha = t.getDouble("ba");
                p.border = t.getInteger("bd");
                p.billboard = t.getBoolean("bb");
                p.viewDistance = t.getDouble("vd");
                NBTTagList el = t.getTagList("el", 10);
                for (int j = 0; j < el.tagCount(); j++) {
                    NBTTagCompound c = el.getCompoundTagAt(j);
                    El e = new El();
                    e.index = c.getInteger("i");
                    e.kind = c.getString("k");
                    e.text = c.getString("t");
                    e.color = c.getInteger("c");
                    e.bg = c.getInteger("bg");
                    e.value = c.getDouble("v");
                    p.els.add(e);
                }
                PANELS.add(p);
            }
        });
    }

    public static void reset() {
        PANELS.clear();
    }

    /** Блоков на пиксель панели. */
    private static double px(P p) {
        return p.scale / 64.0D;
    }

    private static double[] rot(double x, double y, double z, double yawDeg, double pitchDeg) {
        // Rx(pitch), затем Ry(-yaw)
        double pr = Math.toRadians(pitchDeg);
        double y1 = y * Math.cos(pr) - z * Math.sin(pr);
        double z1 = y * Math.sin(pr) + z * Math.cos(pr);
        double yr = Math.toRadians(yawDeg);
        double x2 = x * Math.cos(yr) + z1 * Math.sin(yr);
        double z2 = -x * Math.sin(yr) + z1 * Math.cos(yr);
        return new double[]{x2, y1, z2};
    }

    private static float viewYaw(P p) {
        return p.billboard ? Minecraft.getMinecraft().getRenderManager().playerViewY : p.yaw - 180.0F;
    }

    private static void basis(P p) {
        double s = px(p);
        float v = viewYaw(p);
        float pitch = p.billboard ? 0 : p.pitch;
        double[] ex = rot(-1, 0, 0, -v, pitch);
        double[] ey = rot(0, -1, 0, -v, pitch);
        p.ex = new double[]{ex[0] * s, ex[1] * s, ex[2] * s};
        p.ey = new double[]{ey[0] * s, ey[1] * s, ey[2] * s};
        p.n = rot(0, 0, 1, -v, pitch);
        p.pw = (float) (p.w / s);
        p.ph = (float) (p.h / s);
    }

    private static void layout(P p, FontRenderer f) {
        float y = -p.ph / 2 + 6;
        float wrap = p.pw - 16;
        for (El e : p.els) {
            e.y0 = y;
            e.lines.clear();
            switch (e.kind) {
                case "title":
                    e.lines.addAll(f.listFormattedStringToWidth(e.text, (int) (wrap / 1.5F)));
                    y += e.lines.size() * 14 + 4;
                    break;
                case "button":
                    e.lines.add(f.trimStringToWidth(e.text, (int) wrap - 8));
                    y += 18;
                    break;
                case "bar":
                    e.lines.add(e.text);
                    y += (e.text.isEmpty() ? 0 : 10) + 10;
                    break;
                case "line":
                    y += 6;
                    break;
                default:
                    e.lines.addAll(f.listFormattedStringToWidth(e.text, (int) wrap));
                    y += e.lines.size() * 10 + 2;
            }
            e.y1 = y;
            y += 2;
        }
    }

    // --- выбор взглядом --------------------------------------------------------------------------------

    /** Точка на панели (пиксели) под лучом или null. */
    static float[] hit(P p, Vec3d o, Vec3d d) {
        double dn = d.x * p.n[0] + d.y * p.n[1] + d.z * p.n[2];
        if (Math.abs(dn) < 1e-6) {
            return null;
        }
        double t = ((p.x - o.x) * p.n[0] + (p.y - o.y) * p.n[1] + (p.z - o.z) * p.n[2]) / dn;
        if (t < 0 || t > 12) {
            return null;
        }
        double hx = o.x + d.x * t - p.x;
        double hy = o.y + d.y * t - p.y;
        double hz = o.z + d.z * t - p.z;
        double exl = p.ex[0] * p.ex[0] + p.ex[1] * p.ex[1] + p.ex[2] * p.ex[2];
        double eyl = p.ey[0] * p.ey[0] + p.ey[1] * p.ey[1] + p.ey[2] * p.ey[2];
        float u = (float) ((hx * p.ex[0] + hy * p.ex[1] + hz * p.ex[2]) / exl);
        float v = (float) ((hx * p.ey[0] + hy * p.ey[1] + hz * p.ey[2]) / eyl);
        if (Math.abs(u) > p.pw / 2 || Math.abs(v) > p.ph / 2) {
            return null;
        }
        return new float[]{u, v, (float) t};
    }

    private static void updateHover() {
        Minecraft mc = Minecraft.getMinecraft();
        for (P p : PANELS) {
            p.hover = -1;
        }
        if (mc.player == null || mc.currentScreen != null) {
            return;
        }
        Vec3d o = mc.player.getPositionEyes(1.0F);
        Vec3d d = mc.player.getLookVec();
        float best = Float.MAX_VALUE;
        P bestP = null;
        int bestI = -1;
        for (P p : PANELS) {
            float[] h = hit(p, o, d);
            if (h == null || h[2] > best) {
                continue;
            }
            for (El e : p.els) {
                if ("button".equals(e.kind) && h[1] >= e.y0 && h[1] <= e.y1 && Math.abs(h[0]) <= p.pw / 2 - 6) {
                    best = h[2];
                    bestP = p;
                    bestI = e.index;
                }
            }
        }
        if (bestP != null) {
            bestP.hover = bestI;
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.getButton() != 1 || !event.isButtonstate()) {
            return;
        }
        for (P p : PANELS) {
            if (p.hover >= 0) {
                NBTTagCompound d = new NBTTagCompound();
                d.setString("id", p.id);
                d.setInteger("i", p.hover);
                ClientV2.send("panel_click", d);
                event.setCanceled(true);
                Minecraft.getMinecraft().player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
                return;
            }
        }
    }

    // --- рисование ---------------------------------------------------------------------------------

    private static void quad(BufferBuilder b, float x0, float y0, float x1, float y1, int rgb, float a) {
        quad(b, x0, y0, x1, y1, rgb, a, 0.0F);
    }

    private static void quad(BufferBuilder b, float x0, float y0, float x1, float y1, int rgb, float a, float z) {
        float r = (rgb >> 16 & 255) / 255.0F;
        float g = (rgb >> 8 & 255) / 255.0F;
        float bl = (rgb & 255) / 255.0F;
        b.pos(x0, y0, z).color(r, g, bl, a).endVertex();
        b.pos(x0, y1, z).color(r, g, bl, a).endVertex();
        b.pos(x1, y1, z).color(r, g, bl, a).endVertex();
        b.pos(x1, y0, z).color(r, g, bl, a).endVertex();
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (mc.world == null || view == null || PANELS.isEmpty()) {
            return;
        }
        float pt = event.getPartialTicks();
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        FontRenderer f = mc.fontRenderer;
        for (P p : PANELS) {
            basis(p);
            layout(p, f);
        }
        updateHover();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.disableCull();
        for (P p : PANELS) {
            double dx = p.x - vx;
            double dy = p.y - vy;
            double dz = p.z - vz;
            if (dx * dx + dy * dy + dz * dz > p.viewDistance * p.viewDistance) {
                continue;
            }
            GlStateManager.pushMatrix();
            GlStateManager.translate(dx, dy, dz);
            GlStateManager.rotate(-viewYaw(p), 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(p.billboard ? 0 : p.pitch, 1.0F, 0.0F, 0.0F);
            float s = (float) px(p);
            GlStateManager.scale(-s, -s, s);
            float hw = p.pw / 2;
            float hh = p.ph / 2;
            GlStateManager.disableTexture2D();
            b.begin(7, DefaultVertexFormats.POSITION_COLOR);
            quad(b, -hw - 1.5F, -hh - 1.5F, hw + 1.5F, hh + 1.5F, p.border, 0.95F);
            quad(b, -hw, -hh, hw, hh, p.bg, (float) p.bgAlpha, -0.03F);
            for (El e : p.els) {
                if ("button".equals(e.kind)) {
                    int c = p.hover == e.index ? lighter(e.bg) : e.bg;
                    t(b, -hw + 6, e.y0, hw - 6, e.y1, c, 0.95F);
                } else if ("bar".equals(e.kind)) {
                    float by = e.y1 - 8;
                    t(b, -hw + 8, by, hw - 8, by + 5, 0x0C0E12, 0.9F);
                    t(b, -hw + 8, by, -hw + 8 + (float) ((p.pw - 16) * e.value), by + 5, e.bg, 1.0F);
                } else if ("line".equals(e.kind)) {
                    t(b, -hw + 8, e.y0 + 2, hw - 8, e.y0 + 3, e.color, 0.6F);
                }
            }
            tess.draw();
            GlStateManager.enableTexture2D();
            GlStateManager.translate(0.0F, 0.0F, -0.15F);
            GlStateManager.depthMask(false);
            for (El e : p.els) {
                int c = 0xFF000000 | e.color;
                switch (e.kind) {
                    case "title":
                        for (int i = 0; i < e.lines.size(); i++) {
                            GlStateManager.pushMatrix();
                            GlStateManager.translate(0, e.y0 + i * 14, 0);
                            GlStateManager.scale(1.5F, 1.5F, 1.0F);
                            String l = e.lines.get(i);
                            f.drawString(l, -f.getStringWidth(l) / 2, 0, c);
                            GlStateManager.popMatrix();
                        }
                        break;
                    case "button":
                        String bl = e.lines.isEmpty() ? "" : e.lines.get(0);
                        f.drawString(bl, -f.getStringWidth(bl) / 2, (int) e.y0 + 5, c);
                        break;
                    case "bar":
                        if (!e.text.isEmpty()) {
                            f.drawString(e.text, (int) (-hw + 8), (int) e.y0, c);
                        }
                        break;
                    case "line":
                        break;
                    default:
                        for (int i = 0; i < e.lines.size(); i++) {
                            f.drawString(e.lines.get(i), (int) (-hw + 8), (int) e.y0 + i * 10, c);
                        }
                }
            }
            GlStateManager.depthMask(true);
            GlStateManager.popMatrix();
        }
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void t(BufferBuilder b, float x0, float y0, float x1, float y1, int rgb, float a) {
        quad(b, x0, y0, x1, y1, rgb, a, -0.08F);
    }

    private static int lighter(int c) {
        int r = Math.min(255, (c >> 16 & 255) + 40);
        int g = Math.min(255, (c >> 8 & 255) + 40);
        int b = Math.min(255, (c & 255) + 40);
        return r << 16 | g << 8 | b;
    }
}
