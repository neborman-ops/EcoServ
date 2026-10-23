package com.ecoserv.client;

import com.ecoserv.EcoServ;
import com.ecoserv.init.ModSounds;
import com.ecoserv.network.packet.PacketBlackHole;
import com.ecoserv.region.HoleParams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Особые порталы на клиенте: «Чёрная дыра» (1.8.0) и «Монолит» (1.9.0).
 * <ul>
 * <li>Окно: место назначения рисуется в отдельный кадр той же камерой — настоящие блоки с текстурами и
 * светом, существа там двигаются вживую — сдвинутое так, что место выхода совпадает с порталом, и отрезанное
 * плоскостью портала. Потом окно (диск у дыры, прямоугольник у монолита) накрывается этим кадром по
 * экранным координатам (проективное текстурирование) — куда смотришь, туда и видно.</li>
 * <li>Линза вокруг дыры — по Шварцшильду (blackhole.fsh), по желанию с аккреционным диском; у монолита —
 * только лёгкая рябь в полосе ~1 блок вокруг картинки.</li>
 * <li>Экран: издалека «гиперпрыжок», совсем рядом «сжатие», после перехода «раздутие» (warp.fsh) — вместе
 * с интерфейсом, если это не выключено в настройках портала или в клиентских настройках.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class BlackHoleClient {

    static final float BC = 5.19615242F;

    static final class Hole {
        String name;
        int kind;
        double x;
        double y;
        double z;
        float r;
        int axis;
        float halfU;
        float halfV;
        int turn;
        boolean hasView;
        float[] p = new float[0];
        PortalScene scene;
        PortalScene pending;
        final PortalEntities ents = new PortalEntities();
        // сборка вида из кусков
        int partsVersion = -1;
        byte[][] parts;
        int partsGot;

        float p(HoleParams.Param param) {
            return param.index < p.length ? p[param.index] : (float) param.defHole;
        }

        boolean flag(HoleParams.Param param) {
            return p(param) != 0.0F;
        }

        boolean mono() {
            return kind == 1;
        }
    }

    /** Что передать шейдеру blackhole.fsh (ClientGlitchFx выбирает ближайшее искажение). */
    static final class Lens {
        float mode;
        float x;
        float y;
        float radius;
        float strength;
        double dist;
        float sc;
        float thetaC;
        float distM;
        float lensK = 1.0F;
        float disk;
        float[] diskNormal = {0.0F, 1.0F, 0.0F};
        float diskOuter = 22.0F;
        float diskTemp = 4500.0F;
        float diskSpin = 1.0F;
        float diskBright = 1.0F;
        float[] diskTint = {1.0F, 1.0F, 1.0F};
        float[] rim = {0.65F, 0.75F, 1.0F};
        float[] quad;
        float band;
        /** Окно портала рисуется ПОСЛЕ линзы: окно = тень дыры, его самого линза не искажает. */
        @Nullable
        Runnable after;

        void runAfter() {
            Runnable r = after;
            after = null;
            if (r != null) {
                try {
                    r.run();
                } catch (Throwable t) {
                    EcoServ.LOG.debug("Окно портала не нарисовалось: {}", t.toString());
                }
            }
        }

        void apply(Shader s, float aspect) {
            set(s, "Center", x, y);
            set(s, "Radius", radius);
            set(s, "Strength", strength);
            set(s, "Aspect", aspect);
            set(s, "Mode", mode);
            set(s, "Sc", sc);
            set(s, "ThetaC", thetaC);
            set(s, "Dist", distM);
            set(s, "LensK", lensK);
            int q = ClientSettings.quality();
            set(s, "Quality", q);
            set(s, "Steps", q >= 3 ? 220.0F : 96.0F);
            set(s, "Flash", ClientSettings.flash());
            set(s, "DiskOn", disk);
            set(s, "DiskNormal", diskNormal[0], diskNormal[1], diskNormal[2]);
            set(s, "DiskInner", 6.0F);
            set(s, "DiskOuter", diskOuter);
            set(s, "DiskTemp", diskTemp);
            set(s, "DiskSpin", diskSpin);
            set(s, "DiskBright", diskBright);
            set(s, "DiskTint", diskTint[0], diskTint[1], diskTint[2]);
            set(s, "RimColor", rim[0], rim[1], rim[2]);
            set(s, "Clock", (System.currentTimeMillis() % 1_000_000L) / 1000.0F);
            if (quad != null) {
                set(s, "Q0", quad[0], quad[1]);
                set(s, "Q1", quad[2], quad[3]);
                set(s, "Q2", quad[4], quad[5]);
                set(s, "Q3", quad[6], quad[7]);
                set(s, "Band", band);
            }
        }
    }

    private static final List<Hole> HOLES = new ArrayList<Hole>();
    private static final Map<String, Hole> BY_NAME = new HashMap<String, Hole>();
    private static int holesDim = Integer.MIN_VALUE;

    private static Framebuffer view;
    private static final FloatBuffer FOG = BufferUtils.createFloatBuffer(4);
    private static final DoubleBuffer PLANE = BufferUtils.createDoubleBuffer(4);
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer GEN = BufferUtils.createFloatBuffer(4);

    // экранный эффект
    private static float targetWarp;
    private static float targetPinch;
    private static float warp;
    private static float pinch;
    private static boolean hudWarp = true;
    private static float releasePower = 1.0F;
    private static float screenX = 0.5F;
    private static float screenY = 0.5F;
    private static long lastCloseTime;
    private static long releaseStart;
    private static int lastDim = Integer.MIN_VALUE;
    private static double lastX;
    private static double lastY;
    private static double lastZ;
    private static Framebuffer temp;
    private static Shader warpShader;
    private static Shader blitBack;
    private static boolean failed;
    private static int fbW;
    private static int fbH;
    private static float time;
    private static int humTicks;
    private static Hole nearestHole;
    private static double nearestSurface = Double.MAX_VALUE;

    // --- пакеты ------------------------------------------------------------------------------------

    static void onPacket(PacketBlackHole m) {
        if (m.op == PacketBlackHole.LIST) {
            if (m.dim != holesDim) {
                clear();
            }
            holesDim = m.dim;
            Map<String, Hole> old = new HashMap<String, Hole>(BY_NAME);
            HOLES.clear();
            BY_NAME.clear();
            for (PacketBlackHole.Hole h : m.holes) {
                Hole x = old.remove(h.name);
                if (x == null) {
                    x = new Hole();
                }
                x.name = h.name;
                x.kind = h.kind;
                x.x = h.x;
                x.y = h.y;
                x.z = h.z;
                x.r = h.radius;
                x.axis = h.axis;
                x.halfU = h.halfU;
                x.halfV = h.halfV;
                x.turn = h.turn;
                x.hasView = h.hasView;
                x.p = h.params;
                HOLES.add(x);
                BY_NAME.put(x.name, x);
            }
            for (Hole gone : old.values()) {
                drop(gone);
            }
            return;
        }
        Hole h = BY_NAME.get(m.name);
        if (h == null) {
            return;
        }
        if (m.op == PacketBlackHole.ENTITIES) {
            h.ents.onPacket(m.ents);
            return;
        }
        // PREVIEW: куски одного вида
        if (h.partsVersion != m.version || h.parts == null || h.parts.length != m.parts) {
            h.partsVersion = m.version;
            h.parts = new byte[Math.max(1, Math.min(m.parts, 64))][];
            h.partsGot = 0;
        }
        if (m.part < 0 || m.part >= h.parts.length || h.parts[m.part] != null) {
            return;
        }
        h.parts[m.part] = m.data;
        h.partsGot++;
        if (h.partsGot < h.parts.length) {
            return;
        }
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] b : h.parts) {
            all.write(b, 0, b.length);
        }
        h.parts = null;
        if (h.pending != null) {
            h.pending.delete();
        }
        h.pending = PortalScene.start(m.version, all.toByteArray());
    }

    private static void drop(Hole h) {
        if (h.scene != null) {
            h.scene.delete();
            h.scene = null;
        }
        if (h.pending != null) {
            h.pending.delete();
            h.pending = null;
        }
        h.ents.clear();
    }

    private static void clear() {
        for (Hole h : HOLES) {
            drop(h);
        }
        HOLES.clear();
        BY_NAME.clear();
    }

    public static void reset() {
        clear();
        holesDim = Integer.MIN_VALUE;
        warp = pinch = targetWarp = targetPinch = 0.0F;
    }

    /** Вид, готовый к показу (новый подменяет старый, когда соберётся). */
    @Nullable
    private static PortalScene scene(Hole h) {
        if (h.pending != null) {
            if (h.pending.failed()) {
                h.pending = null;
            } else if (h.pending.ready()) {
                if (h.scene != null) {
                    h.scene.delete();
                }
                h.scene = h.pending;
                h.pending = null;
            }
        }
        return h.scene != null && h.scene.ready() ? h.scene : null;
    }

    // --- отрисовка (из ClientGlitchFx.onRenderLast, до линзы) ------------------------------------------

    @Nullable
    static Lens renderPortals(Minecraft mc, float pt) {
        targetWarp = 0.0F;
        targetPinch = 0.0F;
        nearestHole = null;
        nearestSurface = Double.MAX_VALUE;
        if (HOLES.isEmpty() || mc.world == null || mc.world.provider.getDimension() != holesDim) {
            return null;
        }
        double vx = mc.getRenderManager().viewerPosX;
        double vy = mc.getRenderManager().viewerPosY;
        double vz = mc.getRenderManager().viewerPosZ;
        // 1) какой портал получит линзу (ближайший, где она вообще нужна) — его окно рисуется после линзы
        Hole lensHole = null;
        double lensDist = Double.MAX_VALUE;
        for (Hole h : HOLES) {
            double dx = h.x - vx;
            double dy = h.y - vy;
            double dz = h.z - vz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double surface = h.mono() ? planeDistance(h, vx, vy, vz) : d - h.r;
            if (surface < nearestSurface) {
                nearestSurface = surface;
                nearestHole = h;
            }
            double ld = Double.MAX_VALUE;
            if (!h.mono()) {
                float discR = h.r * h.p(HoleParams.SIZE) / 100.0F;
                if (d > discR + 0.3D && d < 160.0D && ClientGlitchFx.project(mc, h.x, h.y, h.z) != null) {
                    ld = d;
                }
            } else if (h.p(HoleParams.RIPPLE) > 1.0F && surface < 16.0D) {
                ld = surface;
            }
            if (ld < lensDist) {
                lensDist = ld;
                lensHole = h;
            }
        }
        // 2) остальные окна — сразу, окно портала с линзой — последним (его кадр не должен затереться)
        Lens best = null;
        for (Hole h : HOLES) {
            if (h != lensHole) {
                drawOne(mc, h, pt, vx, vy, vz, false);
            }
        }
        if (lensHole != null) {
            best = drawOne(mc, lensHole, pt, vx, vy, vz, true);
        }
        screenEffect(mc);
        return best;
    }

    @Nullable
    private static Lens drawOne(Minecraft mc, Hole h, float pt, double vx, double vy, double vz, boolean defer) {
        double dx = h.x - vx;
        double dy = h.y - vy;
        double dz = h.z - vz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > 160.0D) {
            return null;
        }
        try {
            return h.mono() ? drawMonolith(mc, h, pt, d, vx, vy, vz, defer) : drawHole(mc, h, pt, d, dx, dy, dz, defer);
        } catch (Throwable t) {
            EcoServ.LOG.debug("Портал {}: окно не нарисовалось: {}", h.name, t.toString());
            restoreState(mc);
            return null;
        }
    }

    private static void screenEffect(Minecraft mc) {
        Hole h = nearestHole;
        if (h == null) {
            return;
        }
        float scale = ClientSettings.distortion();
        float effect = Math.max(1.0F, h.p(HoleParams.EFFECT_DISTANCE));
        float approach = (float) MathHelper.clamp((effect - nearestSurface) / effect, 0.0D, 1.0D);
        targetWarp = approach * approach * (h.mono() ? 0.55F : 0.9F) * h.p(HoleParams.WARP) / 100.0F * scale;
        if (!h.mono()) {
            float close = (float) MathHelper.clamp(1.0D - nearestSurface / (h.r * 1.5D + 2.0D), 0.0D, 1.0D);
            targetPinch = close * close * 0.75F * h.p(HoleParams.PINCH) / 100.0F * scale;
            if (close > 0.25F) {
                lastCloseTime = System.currentTimeMillis();
                releasePower = h.p(HoleParams.RELEASE) / 100.0F;
            }
        } else if (nearestSurface < 1.2D) {
            lastCloseTime = System.currentTimeMillis();
            releasePower = h.p(HoleParams.RELEASE) / 100.0F;
        }
        hudWarp = h.flag(HoleParams.HUD) && ClientSettings.get().distortHud;
        float[] sc = ClientGlitchFx.project(mc, h.x, h.y, h.z);
        if (sc != null) {
            screenX = MathHelper.clamp(sc[0], 0.0F, 1.0F);
            screenY = MathHelper.clamp(sc[1], 0.0F, 1.0F);
        } else {
            screenX = 0.5F;
            screenY = 0.5F;
        }
    }

    /** Расстояние от точки до картинки монолита (прямоугольник в плоскости). */
    private static double planeDistance(Hole h, double px, double py, double pz) {
        double[] u = axisU(h.axis);
        double[] v = axisV(h.axis);
        double[] n = axisN(h.axis);
        double rx = px - h.x;
        double ry = py - h.y;
        double rz = pz - h.z;
        double du = rx * u[0] + ry * u[1] + rz * u[2];
        double dv = rx * v[0] + ry * v[1] + rz * v[2];
        double dn = rx * n[0] + ry * n[1] + rz * n[2];
        double eu = Math.max(0.0D, Math.abs(du) - h.halfU);
        double ev = Math.max(0.0D, Math.abs(dv) - h.halfV);
        return Math.sqrt(eu * eu + ev * ev + dn * dn);
    }

    static double[] axisN(int axis) {
        return axis == 0 ? new double[]{1, 0, 0} : axis == 1 ? new double[]{0, 1, 0} : new double[]{0, 0, 1};
    }

    static double[] axisU(int axis) {
        return axis == 0 ? new double[]{0, 0, 1} : new double[]{1, 0, 0};
    }

    static double[] axisV(int axis) {
        return axis == 1 ? new double[]{0, 0, 1} : new double[]{0, 1, 0};
    }

    // --- чёрная дыра ---------------------------------------------------------------------------------------

    @Nullable
    private static Lens drawHole(Minecraft mc, Hole h, float pt, double d, double dx, double dy, double dz, boolean defer) {
        final float discR = h.r * h.p(HoleParams.SIZE) / 100.0F;
        float[] sc = ClientGlitchFx.project(mc, h.x, h.y, h.z);
        Runnable window = null;
        if (d > discR + 0.3D && (sc != null || d < discR * 3.0D)) {
            final double nx = dx / d;
            final double ny = dy / d;
            final double nz = dz / d;
            // плоскость отреза: перпендикулярно взгляду через центр дыры
            double[] plane = {nx, ny, nz, -(nx * dx + ny * dy + nz * dz) + 0.05D};
            final boolean textured = renderView(mc, h, pt, d, plane, discR * 2.0F);
            window = () -> {
                // круг-окно, развёрнутый к камере
                double[] right = {-nz, 0.0D, nx};
                double rl = Math.sqrt(right[0] * right[0] + right[2] * right[2]);
                if (rl < 0.001D) {
                    right = new double[]{1.0D, 0.0D, 0.0D};
                    rl = 1.0D;
                }
                right[0] /= rl;
                right[2] /= rl;
                double[] up = {right[1] * nz - right[2] * ny, right[2] * nx - right[0] * nz, right[0] * ny - right[1] * nx};
                final int seg = 64;
                final double[][] ring = new double[seg + 1][];
                for (int i = 0; i <= seg; i++) {
                    double a = i * Math.PI * 2.0D / seg;
                    ring[i] = new double[]{dx + (right[0] * Math.cos(a) + up[0] * Math.sin(a)) * discR,
                            dy + (right[1] * Math.cos(a) + up[1] * Math.sin(a)) * discR,
                            dz + (right[2] * Math.cos(a) + up[2] * Math.sin(a)) * discR};
                }
                beginWindow(mc, h, textured);
                BufferBuilder b = Tessellator.getInstance().getBuffer();
                b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION);
                b.pos(dx, dy, dz).endVertex();
                for (double[] p : ring) {
                    b.pos(p[0], p[1], p[2]).endVertex();
                }
                Tessellator.getInstance().draw();
                endWindow(textured);
                // светящийся край (виден и без шейдеров)
                int rim = (int) h.p(HoleParams.RIM_COLOR);
                float pulse = 0.75F + 0.25F * MathHelper.sin(time * 6.0F);
                glow(mc, rim, pulse * 0.8F * ClientSettings.flash(), (bb, col, a) -> {
                    for (int i = 0; i < seg; i++) {
                        double[] a0 = ring[i];
                        double[] a1 = ring[i + 1];
                        double k = 1.18D;
                        bb.pos(a0[0], a0[1], a0[2]).color(col[0], col[1], col[2], a).endVertex();
                        bb.pos(dx + (a0[0] - dx) * k, dy + (a0[1] - dy) * k, dz + (a0[2] - dz) * k).color(col[0], col[1], col[2], 0.0F).endVertex();
                        bb.pos(dx + (a1[0] - dx) * k, dy + (a1[1] - dy) * k, dz + (a1[2] - dz) * k).color(col[0], col[1], col[2], 0.0F).endVertex();
                        bb.pos(a1[0], a1[1], a1[2]).color(col[0], col[1], col[2], a).endVertex();
                    }
                });
            };
        }
        if (!defer || sc == null || d <= discR + 0.3D) {
            if (window != null) {
                window.run();
            }
            return null;
        }
        // линза по Шварцшильду: тень = окно (b_c * M = discR)
        Lens lens = new Lens();
        lens.after = window;
        lens.mode = 2.0F;
        lens.x = sc[0];
        lens.y = sc[1];
        lens.dist = d;
        lens.sc = sc[2] * 0.5F;
        lens.thetaC = (float) Math.atan(discR / d);
        lens.distM = (float) (BC * d / discR);
        lens.radius = (float) Math.min(1.4D, lens.sc * Math.tan(Math.min(lens.thetaC * 12.0F, 1.2F)));
        lens.strength = (float) Math.min(1.0D, 0.55D + 0.45D * Math.max(0.0D, 1.0D - d / 64.0D)) * Math.min(1.0F, ClientSettings.distortion());
        lens.lensK = h.p(HoleParams.LENS) / 100.0F;
        lens.rim = rgb((int) h.p(HoleParams.RIM_COLOR));
        if (h.flag(HoleParams.DISK)) {
            lens.disk = 1.0F;
            lens.diskOuter = h.p(HoleParams.DISK_SIZE);
            lens.diskTemp = h.p(HoleParams.DISK_TEMP);
            lens.diskBright = h.p(HoleParams.DISK_BRIGHT) / 100.0F * 2.6F;
            lens.diskTint = rgb((int) h.p(HoleParams.DISK_COLOR));
            lens.diskSpin = h.flag(HoleParams.DISK_REVERSE) ? -1.0F : 1.0F;
            lens.diskNormal = diskNormal(h.p(HoleParams.DISK_TILT), h.p(HoleParams.DISK_YAW), dx, dy, dz);
        }
        return lens;
    }

    /** Нормаль диска в системе «дыра → камера» (x — вправо по экрану, y — вверх, z — на камеру). */
    static float[] diskNormal(float tiltDeg, float yawDeg, double dx, double dy, double dz) {
        double t = Math.toRadians(tiltDeg);
        double y = Math.toRadians(yawDeg);
        // нормаль в мире: вверх, наклонённая на t в сторону yaw
        double wx = Math.sin(t) * -Math.sin(y);
        double wy = Math.cos(t);
        double wz = Math.sin(t) * Math.cos(y);
        float[] mv = new float[16];
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        MATRIX.get(mv);
        // в координаты глаза (только поворот)
        double ex = mv[0] * wx + mv[4] * wy + mv[8] * wz;
        double ey = mv[1] * wx + mv[5] * wy + mv[9] * wz;
        double ez = mv[2] * wx + mv[6] * wy + mv[10] * wz;
        double hx = mv[0] * dx + mv[4] * dy + mv[8] * dz + mv[12];
        double hy = mv[1] * dx + mv[5] * dy + mv[9] * dz + mv[13];
        double hz = mv[2] * dx + mv[6] * dy + mv[10] * dz + mv[14];
        double hl = Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (hl < 1e-6) {
            return new float[]{0.0F, 1.0F, 0.0F};
        }
        // Z: от дыры к камере; X: «вправо по экрану», перпендикулярно Z
        double zx = -hx / hl;
        double zy = -hy / hl;
        double zz = -hz / hl;
        double xx = 1.0D - zx * zx;
        double xy = -zx * zy;
        double xz = -zx * zz;
        double xl = Math.sqrt(xx * xx + xy * xy + xz * xz);
        xx /= xl;
        xy /= xl;
        xz /= xl;
        double yx = zy * xz - zz * xy;
        double yy = zz * xx - zx * xz;
        double yz = zx * xy - zy * xx;
        return new float[]{(float) (ex * xx + ey * xy + ez * xz), (float) (ex * yx + ey * yy + ez * yz),
                (float) (ex * zx + ey * zy + ez * zz)};
    }

    // --- монолит -----------------------------------------------------------------------------------------

    @Nullable
    private static Lens drawMonolith(Minecraft mc, Hole h, float pt, double d, double vx, double vy, double vz, boolean defer) {
        double[] n = axisN(h.axis);
        double[] u = axisU(h.axis);
        double[] v = axisV(h.axis);
        final double cx = h.x - vx;
        final double cy = h.y - vy;
        final double cz = h.z - vz;
        // сторона, с которой смотрим: оставляем то, что ЗА плоскостью
        double side = cx * n[0] + cy * n[1] + cz * n[2] >= 0.0D ? 1.0D : -1.0D;
        if (Math.abs(cx * n[0] + cy * n[1] + cz * n[2]) < 0.02D) {
            return null;
        }
        double[] plane = {n[0] * side, n[1] * side, n[2] * side,
                -(n[0] * side * cx + n[1] * side * cy + n[2] * side * cz) + 0.02D};
        final boolean textured = renderView(mc, h, pt, d, plane, Math.max(h.halfU, h.halfV) * 2.0F);
        final double[][] corners = new double[4][];
        double[] su = {-1, 1, 1, -1};
        double[] sv = {-1, -1, 1, 1};
        for (int i = 0; i < 4; i++) {
            corners[i] = new double[]{cx + u[0] * h.halfU * su[i] + v[0] * h.halfV * sv[i],
                    cy + u[1] * h.halfU * su[i] + v[1] * h.halfV * sv[i],
                    cz + u[2] * h.halfU * su[i] + v[2] * h.halfV * sv[i]};
        }
        final int rim = (int) h.p(HoleParams.RIM_COLOR);
        Runnable window = () -> {
            beginWindow(mc, h, textured);
            BufferBuilder b = Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
            for (double[] c : corners) {
                b.pos(c[0], c[1], c[2]).endVertex();
            }
            Tessellator.getInstance().draw();
            endWindow(textured);
            // тонкое свечение рамки
            float pulse = 0.7F + 0.3F * MathHelper.sin(time * 3.0F);
            glow(mc, rim, 0.55F * pulse * ClientSettings.flash(), (bb, col, a) -> {
                double w = 0.18D;
                for (int i = 0; i < 4; i++) {
                    double[] a0 = corners[i];
                    double[] a1 = corners[(i + 1) % 4];
                    // наружу от центра
                    double[] o0 = {a0[0] - cx, a0[1] - cy, a0[2] - cz};
                    double[] o1 = {a1[0] - cx, a1[1] - cy, a1[2] - cz};
                    double l0 = Math.sqrt(o0[0] * o0[0] + o0[1] * o0[1] + o0[2] * o0[2]);
                    double l1 = Math.sqrt(o1[0] * o1[0] + o1[1] * o1[1] + o1[2] * o1[2]);
                    bb.pos(a0[0], a0[1], a0[2]).color(col[0], col[1], col[2], a).endVertex();
                    bb.pos(a0[0] + o0[0] / l0 * w, a0[1] + o0[1] / l0 * w, a0[2] + o0[2] / l0 * w).color(col[0], col[1], col[2], 0.0F).endVertex();
                    bb.pos(a1[0] + o1[0] / l1 * w, a1[1] + o1[1] / l1 * w, a1[2] + o1[2] / l1 * w).color(col[0], col[1], col[2], 0.0F).endVertex();
                    bb.pos(a1[0], a1[1], a1[2]).color(col[0], col[1], col[2], a).endVertex();
                }
            });
        };
        // рябь у краёв картинки — только вблизи
        float ripple = h.p(HoleParams.RIPPLE) / 100.0F * ClientSettings.distortion();
        double dist = planeDistance(h, vx, vy, vz);
        float[] quad = null;
        if (defer && ripple > 0.01F && dist <= 16.0D) {
            quad = new float[8];
            for (int i = 0; i < 4 && quad != null; i++) {
                float[] s = ClientGlitchFx.project(mc, corners[i][0] + vx, corners[i][1] + vy, corners[i][2] + vz);
                if (s == null) {
                    quad = null;
                } else {
                    quad[i * 2] = s[0];
                    quad[i * 2 + 1] = s[1];
                }
            }
        }
        if (quad == null) {
            window.run();
            return null;
        }
        float[] c = ClientGlitchFx.project(mc, h.x, h.y, h.z);
        Lens lens = new Lens();
        lens.after = window;
        lens.mode = 3.0F;
        lens.quad = quad;
        lens.x = c == null ? 0.5F : c[0];
        lens.y = c == null ? 0.5F : c[1];
        lens.radius = 2.0F;
        lens.dist = dist;
        // ~1 блок на расстоянии до картинки
        float scale = c == null ? 0.7F : c[2];
        lens.band = (float) Math.min(0.25D, 0.5D * scale / Math.max(1.0D, d));
        lens.strength = (float) (ripple * Math.max(0.0D, 1.0D - dist / 16.0D));
        lens.rim = rgb(rim);
        return lens;
    }

    // --- общий кадр места назначения ----------------------------------------------------------------------

    private interface GlowShape {
        void emit(BufferBuilder b, float[] col, float alpha);
    }

    private static void glow(Minecraft mc, int color, float alpha, GlowShape shape) {
        float[] col = rgb(color);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        shape.emit(b, col, alpha);
        Tessellator.getInstance().draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
    }

    /**
     * Нарисовать место назначения в отдельный кадр. plane — плоскость отреза {a, b, c, d} в координатах
     * относительно камеры. Возвращает false, если показывать нечего (тогда окно просто заливается цветом).
     */
    private static boolean renderView(Minecraft mc, Hole h, float pt, double d, double[] plane, float windowSize) {
        if ((int) h.p(HoleParams.INSIDE) != 0 || !h.hasView) {
            return false;
        }
        PortalScene scene = scene(h);
        Framebuffer main = mc.getFramebuffer();
        if (view == null || view.framebufferWidth != main.framebufferWidth || view.framebufferHeight != main.framebufferHeight) {
            if (view == null) {
                view = new Framebuffer(main.framebufferWidth, main.framebufferHeight, true);
            } else {
                view.createBindFramebuffer(main.framebufferWidth, main.framebufferHeight);
            }
        }
        int sky = scene == null ? 0x07030F : scene.sky;
        float[] skyRgb = rgb(sky);
        view.bindFramebuffer(true);
        GlStateManager.clearColor(skyRgb[0], skyRgb[1], skyRgb[2], 1.0F);
        GlStateManager.clearDepth(1.0D);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        if (scene != null) {
            double vx = mc.getRenderManager().viewerPosX;
            double vy = mc.getRenderManager().viewerPosY;
            double vz = mc.getRenderManager().viewerPosZ;
            float radius = scene.size / 2.0F;
            int clientView = mc.gameSettings.renderDistanceChunks * 16;
            float far = (float) Math.min(radius, clientView);
            GlStateManager.setFog(GlStateManager.FogMode.LINEAR);
            GlStateManager.setFogStart((float) (d + far * 0.55D));
            GlStateManager.setFogEnd((float) (d + far * 0.98D));
            FOG.clear();
            FOG.put(skyRgb[0]).put(skyRgb[1]).put(skyRgb[2]).put(1.0F).flip();
            GlStateManager.glFog(GL11.GL_FOG_COLOR, FOG);
            GlStateManager.enableFog();
            PLANE.clear();
            PLANE.put(plane[0]).put(plane[1]).put(plane[2]).put(plane[3]).flip();
            GL11.glClipPlane(GL11.GL_CLIP_PLANE0, PLANE);
            GL11.glEnable(GL11.GL_CLIP_PLANE0);

            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            mc.entityRenderer.enableLightmap();
            GlStateManager.enableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            pushSceneMatrix(h, scene, vx, vy, vz);
            scene.draw(0);
            scene.draw(1);
            scene.draw(2);
            GlStateManager.popMatrix();

            if (h.flag(HoleParams.ENTITIES) && ClientSettings.get().portalEntities) {
                drawEntities(mc, h, scene, pt, vx, vy, vz);
                mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                mc.entityRenderer.enableLightmap();
            }

            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.depthMask(false);
            pushSceneMatrix(h, scene, vx, vy, vz);
            scene.draw(3);
            GlStateManager.popMatrix();
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GL11.glDisable(GL11.GL_CLIP_PLANE0);
            GlStateManager.disableFog();
            mc.entityRenderer.disableLightmap();
        }
        main.bindFramebuffer(true);
        return true;
    }

    private static void pushSceneMatrix(Hole h, PortalScene scene, double vx, double vy, double vz) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(h.x - vx, h.y - vy, h.z - vz);
        if (h.turn != 0) {
            GlStateManager.rotate(90.0F * h.turn, 0.0F, 1.0F, 0.0F);
        }
        GlStateManager.translate(scene.offX, scene.offY, scene.offZ);
    }

    private static void drawEntities(Minecraft mc, Hole h, PortalScene scene, float pt, double vx, double vy, double vz) {
        List<PortalEntities.Doll> dolls = h.ents.all();
        if (dolls.isEmpty()) {
            return;
        }
        RenderManager rm = mc.getRenderManager();
        boolean shadows = mc.gameSettings.entityShadows;
        rm.setRenderShadow(false);
        RenderHelper.enableStandardItemLighting();
        int back = (4 - h.turn % 4) % 4;
        for (PortalEntities.Doll d : dolls) {
            try {
                double[] now = rot(d.cx, d.cy, d.cz, back);
                double[] prev = rot(d.px, d.py, d.pz, back);
                Entity e = d.entity;
                e.lastTickPosX = e.prevPosX = h.x + prev[0];
                e.lastTickPosY = e.prevPosY = h.y + prev[1];
                e.lastTickPosZ = e.prevPosZ = h.z + prev[2];
                e.posX = h.x + now[0];
                e.posY = h.y + now[1];
                e.posZ = h.z + now[2];
                float turnDeg = -90.0F * h.turn;
                e.prevRotationYaw = d.pyaw + turnDeg;
                e.rotationYaw = d.yaw + turnDeg;
                e.prevRotationPitch = e.rotationPitch = d.pitch;
                if (e instanceof EntityLivingBase) {
                    EntityLivingBase l = (EntityLivingBase) e;
                    l.prevRenderYawOffset = d.pyaw + turnDeg;
                    l.renderYawOffset = d.yaw + turnDeg;
                    l.prevRotationYawHead = d.phead + turnDeg;
                    l.rotationYawHead = d.head + turnDeg;
                }
                double ix = d.px + (d.cx - d.px) * pt;
                double iy = d.py + (d.cy - d.py) * pt;
                double iz = d.pz + (d.cz - d.pz) * pt;
                int light = scene.lightAt(ix - scene.offX, iy + 0.5D - scene.offY, iz - scene.offZ);
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) (light % 65536), (float) (light / 65536));
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                double rx = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt - vx;
                double ry = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt - vy;
                double rz = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt - vz;
                float yaw = e.prevRotationYaw + (e.rotationYaw - e.prevRotationYaw) * pt;
                rm.renderEntity(e, rx, ry, rz, yaw, pt, false);
            } catch (Throwable t) {
                // чужая модель, которой нужен мир, — пропускаем
            }
        }
        RenderHelper.disableStandardItemLighting();
        rm.setRenderShadow(shadows);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    /** Поворот вектора вокруг Y на turn четвертей (как в BlackHoles.rotate). */
    static double[] rot(double x, double y, double z, int turn) {
        for (int i = 0; i < turn; i++) {
            double nx = -z;
            z = x;
            x = nx;
        }
        return new double[]{x, y, z};
    }

    /**
     * Окно: геометрия рисуется с текстурой кадра места назначения по экранным координатам (проективное
     * текстурирование — работает при любом положении камеры, даже если углы окна за спиной). Без вида — заливка.
     */
    private static void beginWindow(Minecraft mc, Hole h, boolean textured) {
        mc.entityRenderer.disableLightmap();
        GlStateManager.disableFog();
        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        if (!textured) {
            GlStateManager.disableTexture2D();
            int inside = (int) h.p(HoleParams.INSIDE);
            float[] c = inside == 2 ? rgb((int) h.p(HoleParams.INSIDE_COLOR)) : new float[]{0.0F, 0.0F, 0.0F};
            if (inside == 0 && !h.hasView) {
                c = new float[]{0.02F, 0.01F, 0.05F};
            }
            GlStateManager.color(c[0], c[1], c[2], 1.0F);
            return;
        }
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(view.framebufferTexture);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        float tw = view.framebufferWidth / (float) view.framebufferTextureWidth;
        float th = view.framebufferHeight / (float) view.framebufferTextureHeight;
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, MATRIX);
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.scale(tw, th, 1.0F);
        GlStateManager.translate(0.5F, 0.5F, 0.5F);
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        GlStateManager.multMatrix(MATRIX);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        // плоскости texgen задаются при единичной матрице вида — тогда s,t,r,q = координаты глаза
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        gen(GlStateManager.TexGen.S, 1, 0, 0, 0);
        gen(GlStateManager.TexGen.T, 0, 1, 0, 0);
        gen(GlStateManager.TexGen.R, 0, 0, 1, 0);
        gen(GlStateManager.TexGen.Q, 0, 0, 0, 1);
        GlStateManager.popMatrix();
    }

    private static void gen(GlStateManager.TexGen coord, float a, float b, float c, float d) {
        GEN.clear();
        GEN.put(a).put(b).put(c).put(d).flip();
        GlStateManager.texGen(coord, GL11.GL_EYE_LINEAR);
        GlStateManager.texGen(coord, GL11.GL_EYE_PLANE, GEN);
        GlStateManager.enableTexGenCoord(coord);
    }

    private static void endWindow(boolean textured) {
        if (textured) {
            GlStateManager.disableTexGenCoord(GlStateManager.TexGen.S);
            GlStateManager.disableTexGenCoord(GlStateManager.TexGen.T);
            GlStateManager.disableTexGenCoord(GlStateManager.TexGen.R);
            GlStateManager.disableTexGenCoord(GlStateManager.TexGen.Q);
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        }
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void restoreState(Minecraft mc) {
        try {
            GL11.glDisable(GL11.GL_CLIP_PLANE0);
            mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.disableFog();
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
        } catch (Throwable ignored) {
            // и так плохо
        }
    }

    static float[] rgb(int c) {
        return new float[]{((c >> 16) & 255) / 255.0F, ((c >> 8) & 255) / 255.0F, (c & 255) / 255.0F};
    }

    // --- экранный эффект и звуки --------------------------------------------------------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Entity e = mc.getRenderViewEntity();
        if (mc.world == null || e == null) {
            lastDim = Integer.MIN_VALUE;
            return;
        }
        time += 0.05F;
        for (Hole h : HOLES) {
            h.ents.tick();
        }
        int dim = mc.world.provider.getDimension();
        double moved = lastDim == dim ? Math.sqrt(e.getDistanceSq(lastX, lastY, lastZ)) : 999.0D;
        // перенесло через портал (рядом был только что и прыгнул далеко) — «раздутие»
        if (System.currentTimeMillis() - lastCloseTime < 1500L && moved > 4.0D) {
            releaseStart = System.currentTimeMillis();
            lastCloseTime = 0L;
            targetPinch = 0.0F;
            pinch = 0.0F;
            playLocal(mc, ModSounds.PORTAL_RELEASE, 0.8F, 1.0F);
        }
        lastDim = dim;
        lastX = e.posX;
        lastY = e.posY;
        lastZ = e.posZ;
        if (mc.world.provider.getDimension() != holesDim) {
            targetWarp = 0.0F;
            targetPinch = 0.0F;
        }
        warp += (targetWarp - warp) * 0.25F;
        pinch += (targetPinch - pinch) * 0.3F;
        // гул рядом
        Hole h = nearestHole;
        if (h != null && h.flag(HoleParams.SOUNDS) && ++humTicks >= 40) {
            humTicks = 0;
            float effect = Math.max(2.0F, h.p(HoleParams.EFFECT_DISTANCE) * (h.mono() ? 3.0F : 1.0F));
            if (nearestSurface < effect) {
                float vol = (float) (0.15D + 0.6D * (1.0D - nearestSurface / effect));
                mc.world.playSound(h.x, h.y, h.z, h.mono() ? ModSounds.MONOLITH_HUM : ModSounds.PORTAL_HUM,
                        SoundCategory.AMBIENT, vol * volume(), h.mono() ? 1.0F : 0.8F + 0.2F * (float) Math.random(), false);
            }
        }
    }

    static float volume() {
        return ClientSettings.get().soundVolume / 100.0F;
    }

    static void playLocal(Minecraft mc, net.minecraft.util.SoundEvent sound, float vol, float pitch) {
        Entity e = mc.getRenderViewEntity();
        if (e != null && mc.world != null) {
            mc.world.playSound(e.posX, e.posY, e.posZ, sound, SoundCategory.PLAYERS, vol * volume(), pitch, false);
        }
    }

    /** Сила «раздутия» после перехода (0..1). */
    private static float release() {
        if (releaseStart == 0L) {
            return 0.0F;
        }
        float t = (System.currentTimeMillis() - releaseStart) / 1300.0F;
        if (t >= 1.0F) {
            releaseStart = 0L;
            return 0.0F;
        }
        // резко раздуло — плавно вернуло
        return (t < 0.12F ? t / 0.12F : (1.0F - t) / 0.88F) * releasePower * ClientSettings.distortion();
    }

    /** Искажение поверх интерфейса — после всего кадра. */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END && hudWarp) {
            applyWarp(event.renderTickTime);
        }
    }

    /** Если интерфейс искажать не надо — до того, как он нарисуется. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL && !hudWarp) {
            applyWarp(event.getPartialTicks());
        }
    }

    private static void applyWarp(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        float rel = release();
        float w = warp + rel * 0.5F;
        float p = pinch - rel * 0.55F;
        if (mc.world == null || failed || (w < 0.01F && Math.abs(p) < 0.01F)) {
            return;
        }
        if (!OpenGlHelper.shadersSupported || !OpenGlHelper.isFramebufferEnabled()) {
            return;
        }
        Framebuffer main = mc.getFramebuffer();
        try {
            if (temp == null || fbW != main.framebufferWidth || fbH != main.framebufferHeight) {
                fbW = main.framebufferWidth;
                fbH = main.framebufferHeight;
                if (temp == null) {
                    temp = new Framebuffer(fbW, fbH, false);
                } else {
                    temp.createBindFramebuffer(fbW, fbH);
                }
                if (warpShader != null) {
                    warpShader.deleteShader();
                    blitBack.deleteShader();
                    warpShader = null;
                }
            }
            if (warpShader == null) {
                warpShader = new Shader(mc.getResourceManager(), "ecoserv:warp", main, temp);
                blitBack = new Shader(mc.getResourceManager(), "blit", temp, main);
                warpShader.setProjectionMatrix(ortho(temp.framebufferTextureWidth, temp.framebufferTextureHeight));
                blitBack.setProjectionMatrix(ortho(main.framebufferTextureWidth, main.framebufferTextureHeight));
            }
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.disableDepth();
            GlStateManager.disableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            set(warpShader, "Center", screenX, screenY);
            set(warpShader, "Aspect", main.framebufferWidth / (float) Math.max(1, main.framebufferHeight));
            set(warpShader, "Warp", MathHelper.clamp(w, 0.0F, 1.0F));
            set(warpShader, "Pinch", MathHelper.clamp(p, -1.0F, 1.0F));
            set(warpShader, "Flash", ClientSettings.flash());
            warpShader.render(time % 1.0F);
            blitBack.render(partialTicks);
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        } catch (Throwable t) {
            failed = true;
            EcoServ.LOG.warn("Эффект порталов выключен: {}", t.toString());
        } finally {
            main.bindFramebuffer(true);
            GlStateManager.enableDepth();
            GlStateManager.enableAlpha();
        }
    }

    static void set(Shader shader, String name, float... values) {
        ShaderUniform u = shader.getShaderManager().getShaderUniform(name);
        if (u == null) {
            return;
        }
        if (values.length == 1) {
            u.set(values[0]);
        } else if (values.length == 2) {
            u.set(values[0], values[1]);
        } else if (values.length == 3) {
            u.set(values[0], values[1], values[2]);
        }
    }

    private static Matrix4f ortho(float w, float h) {
        Matrix4f m = new Matrix4f();
        m.setIdentity();
        m.m00 = 2.0F / w;
        m.m11 = 2.0F / -h;
        m.m22 = -0.0020001999F;
        m.m33 = 1.0F;
        m.m03 = -1.0F;
        m.m13 = 1.0F;
        m.m23 = -1.0001999F;
        return m;
    }
}
