package com.ecoserv.client;

import com.ecoserv.EcoServ;
import com.ecoserv.network.packet.PacketGlitchFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.entity.Entity;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * ГЛИТЧ-всплески в мире: когда игрок проваливается в ГЛИТЧ-бездну, все рядом видят воронку из частиц,
 * «битые» цветные пиксели и искажение пространства вокруг точки, как у чёрной дыры (гравитационная
 * линза с тёмным центром и светящимся кольцом). Упавшему — полноэкранный глитч-шейдер вместо фильтра
 * эндермена. Шейдеры: assets/ecoserv/shaders/program/blackhole.* и glitch.*.
 */
@SideOnly(Side.CLIENT)
public class ClientGlitchFx {

    private static final Random RANDOM = new Random();
    /** Длительность всплеска в тиках. */
    private static final int FALL_TICKS = 50;
    private static final int ARRIVE_TICKS = 24;

    private static final class Burst {
        final int kind;
        final double x;
        final double y;
        final double z;
        final boolean distort;
        final long start;
        /** 1.8.0: кто появился — центр искажения следует за ним. */
        final int entity;
        int age;

        Burst(PacketGlitchFx m) {
            kind = m.kind;
            x = m.x;
            y = m.y;
            z = m.z;
            distort = m.distort && !m.self;
            start = System.currentTimeMillis();
            entity = m.entity;
        }

        int life() {
            if (kind == PacketGlitchFx.FALL) {
                return FALL_TICKS;
            }
            // 1.8.0: появление с искажением держится дольше
            return distort ? ARRIVE_TICKS + 16 : ARRIVE_TICKS;
        }
    }

    private static final List<Burst> BURSTS = new ArrayList<Burst>();

    // --- шейдеры ---------------------------------------------------------------------------------
    private static Framebuffer temp;
    private static Shader holeShader;
    private static Shader glitchShader;
    private static Shader blitBack;
    private static boolean shadersFailed;
    private static int fbW;
    private static int fbH;
    private static float time;
    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);

    /** Сила полноэкранного глитча у упавшего (0 — выкл); выставляет ClientAbyss. */
    static float screenGlitch;

    public static void reset() {
        BlackHoleClient.reset();
        BURSTS.clear();
        screenGlitch = 0.0F;
    }

    /** Можно ли рисовать свои шейдеры (иначе — старый фильтр эндермена и только частицы). */
    static boolean shadersAvailable() {
        return !shadersFailed && OpenGlHelper.shadersSupported && OpenGlHelper.isFramebufferEnabled();
    }

    public static void onPacket(PacketGlitchFx m) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        if (world == null) {
            return;
        }
        if (BURSTS.size() >= 8) {
            BURSTS.remove(0);
        }
        Burst b = new Burst(m);
        BURSTS.add(b);
        float pitch = m.kind == PacketGlitchFx.FALL ? 0.5F : 1.4F;
        float vol = BlackHoleClient.volume();
        world.playSound(m.x, m.y, m.z, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 1.2F * vol, pitch, false);
        world.playSound(m.x, m.y, m.z, m.kind == PacketGlitchFx.FALL ? com.ecoserv.init.ModSounds.ABYSS_FALL
                : com.ecoserv.init.ModSounds.PORTAL_RELEASE, SoundCategory.PLAYERS, 1.0F * vol, m.kind == PacketGlitchFx.FALL ? 1.0F : 1.3F, false);
        // первая вспышка
        for (int i = 0; i < 40; i++) {
            spawnPixel(world, b, 1.6D);
        }
    }

    // --- частицы ---------------------------------------------------------------------------------

    /** «Битый пиксель»: пурпурный, бирюзовый или белый квадратик рядом с центром. */
    private static void spawnPixel(World world, Burst b, double spread) {
        double px = b.x + (RANDOM.nextDouble() - 0.5D) * spread * 2.0D;
        double py = b.y + RANDOM.nextDouble() * spread * 1.5D;
        double pz = b.z + (RANDOM.nextDouble() - 0.5D) * spread * 2.0D;
        int c = RANDOM.nextInt(3);
        // у REDSTONE «скорость» — это цвет (r, g, b); 0 по красному даёт обычный красный, поэтому 1e-3
        double r = c == 0 ? 1.0D : c == 1 ? 0.001D : 1.0D;
        double g = c == 0 ? 0.08D : c == 1 ? 1.0D : 1.0D;
        double bl = c == 0 ? 0.58D : c == 1 ? 1.0D : 1.0D;
        world.spawnParticle(EnumParticleTypes.REDSTONE, px, py, pz, r, g, bl);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.isGamePaused()) {
            return;
        }
        time += 0.05F;
        Iterator<Burst> it = BURSTS.iterator();
        while (it.hasNext()) {
            Burst b = it.next();
            b.age++;
            if (b.age > b.life()) {
                it.remove();
                continue;
            }
            Entity view = mc.getRenderViewEntity();
            if (view == null || view.getDistanceSq(b.x, b.y, b.z) > 96 * 96) {
                continue;
            }
            float f = 1.0F - b.age / (float) b.life();
            World w = mc.world;
            if (b.kind == PacketGlitchFx.FALL) {
                // воронка: частицы портала летят к центру по спирали
                int n = (int) (14 * f) + 4;
                for (int i = 0; i < n; i++) {
                    double ang = RANDOM.nextDouble() * Math.PI * 2.0D;
                    double rad = 1.5D + RANDOM.nextDouble() * 2.5D;
                    double ox = Math.cos(ang) * rad;
                    double oz = Math.sin(ang) * rad;
                    double oy = (RANDOM.nextDouble() - 0.3D) * 2.0D;
                    // PORTAL стартует в точке + смещение и летит в точку
                    w.spawnParticle(EnumParticleTypes.PORTAL, b.x, b.y + 1.0D, b.z, ox, oy, oz);
                }
                for (int i = 0; i < 4; i++) {
                    spawnPixel(w, b, 1.2D + 1.5D * f);
                }
                if (b.age % 3 == 0) {
                    w.spawnParticle(EnumParticleTypes.DRAGON_BREATH, b.x + (RANDOM.nextDouble() - 0.5D),
                            b.y + 0.5D + RANDOM.nextDouble(), b.z + (RANDOM.nextDouble() - 0.5D), 0.0D, 0.02D, 0.0D);
                    w.spawnParticle(EnumParticleTypes.SPELL_WITCH, b.x, b.y + 1.0D, b.z, 0.0D, 0.0D, 0.0D);
                }
                if (b.age == b.life() - 6) {
                    // схлопывание: короткая вспышка искр
                    for (int i = 0; i < 24; i++) {
                        w.spawnParticle(EnumParticleTypes.END_ROD, b.x, b.y + 1.0D, b.z,
                                (RANDOM.nextDouble() - 0.5D) * 0.4D, (RANDOM.nextDouble() - 0.2D) * 0.4D,
                                (RANDOM.nextDouble() - 0.5D) * 0.4D);
                    }
                }
            } else {
                // появление: столб битых пикселей и искр
                for (int i = 0; i < 6; i++) {
                    spawnPixel(w, b, 0.8D);
                }
                if (b.distort) {
                    // 1.8.0: пространство «выдыхает» игрока — частицы разлетаются из точки появления
                    int n = (int) (10 * f) + 2;
                    for (int i = 0; i < n; i++) {
                        double ang = RANDOM.nextDouble() * Math.PI * 2.0D;
                        double sp = 0.15D + RANDOM.nextDouble() * 0.25D;
                        w.spawnParticle(EnumParticleTypes.PORTAL, b.x, b.y + 1.0D, b.z,
                                Math.cos(ang) * sp, (RANDOM.nextDouble() - 0.4D) * 0.3D, Math.sin(ang) * sp);
                    }
                    if (b.age % 4 == 0) {
                        w.spawnParticle(EnumParticleTypes.SPELL_WITCH, b.x, b.y + 1.0D, b.z, 0.0D, 0.0D, 0.0D);
                    }
                }
                if (b.age < 6) {
                    for (int i = 0; i < 6; i++) {
                        w.spawnParticle(EnumParticleTypes.END_ROD, b.x, b.y + RANDOM.nextDouble() * 2.0D, b.z,
                                (RANDOM.nextDouble() - 0.5D) * 0.2D, 0.05D, (RANDOM.nextDouble() - 0.5D) * 0.2D);
                    }
                }
            }
        }
    }

    // --- искажение пространства и полноэкранный глитч ----------------------------------------------

    private static boolean ensureShaders(Minecraft mc) {
        if (shadersFailed || !OpenGlHelper.shadersSupported || !OpenGlHelper.isFramebufferEnabled()) {
            return false;
        }
        Framebuffer main = mc.getFramebuffer();
        try {
            if (temp == null || fbW != main.framebufferWidth || fbH != main.framebufferHeight) {
                fbW = main.framebufferWidth;
                fbH = main.framebufferHeight;
                if (temp == null) {
                    temp = new Framebuffer(fbW, fbH, false);
                    temp.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
                } else {
                    temp.createBindFramebuffer(fbW, fbH);
                }
                deleteShaders();
            }
            if (holeShader == null) {
                holeShader = new Shader(mc.getResourceManager(), "ecoserv:blackhole", main, temp);
                // 1.9.0: точные таблицы Шварцшильда (assets_src/schwarzschild_lut.py)
                holeShader.addAuxFramebuffer("DeflSampler", Integer.valueOf(lut(mc, DEFL_LUT)), 1024, 1);
                holeShader.addAuxFramebuffer("DiskSampler", Integer.valueOf(lut(mc, DISK_LUT)), 512, 256);
                glitchShader = new Shader(mc.getResourceManager(), "ecoserv:glitch", main, temp);
                blitBack = new Shader(mc.getResourceManager(), "blit", temp, main);
                Matrix4f proj = ortho(temp.framebufferTextureWidth, temp.framebufferTextureHeight);
                holeShader.setProjectionMatrix(proj);
                glitchShader.setProjectionMatrix(proj);
                blitBack.setProjectionMatrix(ortho(main.framebufferTextureWidth, main.framebufferTextureHeight));
            }
            return true;
        } catch (Throwable t) {
            shadersFailed = true;
            EcoServ.LOG.warn("Глитч-шейдеры не загрузились — останутся только частицы: {}", t.toString());
            deleteShaders();
            return false;
        }
    }

    private static final net.minecraft.util.ResourceLocation DEFL_LUT =
            new net.minecraft.util.ResourceLocation("ecoserv", "textures/effect/schwarzschild_deflection.png");
    private static final net.minecraft.util.ResourceLocation DISK_LUT =
            new net.minecraft.util.ResourceLocation("ecoserv", "textures/effect/schwarzschild_disk.png");

    private static int lut(Minecraft mc, net.minecraft.util.ResourceLocation id) {
        mc.getTextureManager().bindTexture(id);
        return mc.getTextureManager().getTexture(id).getGlTextureId();
    }

    private static void deleteShaders() {
        for (Shader s : new Shader[]{holeShader, glitchShader, blitBack}) {
            if (s != null) {
                try {
                    s.deleteShader();
                } catch (Throwable ignored) {
                    // уже удалён
                }
            }
        }
        holeShader = null;
        glitchShader = null;
        blitBack = null;
    }

    /** Ортографическая проекция, как у ванильных шейдеров постобработки. */
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

    private static void set(Shader shader, String name, float... values) {
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

    /** Сила искажения во времени: быстро раскрывается, держится, плавно гаснет. */
    private static float holeStrength(Burst b, float partialTicks) {
        float t = (b.age + partialTicks) / b.life();
        if (b.kind != PacketGlitchFx.FALL) {
            // появление: резкая вспышка искажения и плавное «затягивание» разрыва
            if (t < 0.06F) {
                return t / 0.06F;
            }
            return t < 0.3F ? 1.0F : Math.max(0.0F, 1.0F - (t - 0.3F) / 0.7F);
        }
        if (t < 0.12F) {
            return t / 0.12F;
        }
        if (t < 0.65F) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - (t - 0.65F) / 0.35F);
    }

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.anaglyph || mc.world == null) {
            return;
        }
        float pt0 = event.getPartialTicks();
        // окна особых порталов рисуются до линзы — линза искажает и их
        BlackHoleClient.Lens lens = BlackHoleClient.renderPortals(mc, pt0);
        final BlackHoleClient.Lens portal = lens;
        Entity holeEntity = null;
        Entity view = mc.getRenderViewEntity();
        double best = lens == null ? Double.MAX_VALUE : lens.dist;
        if (view != null) {
            for (Burst b : BURSTS) {
                // искажение и в точке падения, и в точке появления; берём ближайшее
                if (!b.distort) {
                    continue;
                }
                double bx = b.x;
                double by = b.y + 1.0D;
                double bz = b.z;
                Entity who = b.entity >= 0 ? mc.world.getEntityByID(b.entity) : null;
                if (who != null && b.kind != PacketGlitchFx.FALL) {
                    // появился игрок: центр — он сам (и идёт за ним)
                    bx = who.lastTickPosX + (who.posX - who.lastTickPosX) * pt0;
                    by = who.lastTickPosY + (who.posY - who.lastTickPosY) * pt0 + who.height * 0.5D;
                    bz = who.lastTickPosZ + (who.posZ - who.lastTickPosZ) * pt0;
                }
                double dist = view.getDistance(bx, by, bz);
                if (dist > 48.0D || dist >= best) {
                    continue;
                }
                float[] screen = project(mc, bx, by, bz);
                if (screen == null) {
                    continue;
                }
                best = dist;
                holeEntity = who;
                if (portal != null) {
                    // линза уйдёт к бурсту — окно портала рисуем сразу
                    portal.runAfter();
                }
                BlackHoleClient.Lens l = new BlackHoleClient.Lens();
                l.x = screen[0];
                l.y = screen[1];
                l.dist = dist;
                l.strength = holeStrength(b, pt0) * Math.min(1.0F, ClientSettings.distortion());
                l.sc = screen[2] * 0.5F;
                if (b.kind == PacketGlitchFx.FALL) {
                    // чёрная дыра бездны: тень ~1.1 блока, диск Шакуры–Сюняева, медленно поворачивается
                    double rel = Math.max(1.6D, dist);
                    double dx = bx - mc.getRenderManager().viewerPosX;
                    double dy = by - mc.getRenderManager().viewerPosY;
                    double dz = bz - mc.getRenderManager().viewerPosZ;
                    l.mode = 0.0F;
                    l.thetaC = (float) Math.atan(1.1D / rel);
                    l.distM = (float) (BlackHoleClient.BC * rel / 1.1D);
                    l.radius = (float) Math.min(1.4D, l.sc * Math.tan(Math.min(l.thetaC * 12.0F, 1.2F)));
                    l.disk = 1.0F;
                    l.diskOuter = 18.0F;
                    l.diskTemp = 5200.0F;
                    l.diskBright = 2.8F;
                    l.diskTint = new float[]{1.0F, 0.85F, 1.0F};
                    l.diskNormal = BlackHoleClient.diskNormal(14.0F, (b.age + pt0) * 2.0F, dx, dy, dz);
                    l.rim = new float[]{0.85F, 0.55F, 1.0F};
                } else {
                    l.mode = 1.0F;
                    // видимый радиус: ~3 блока на расстоянии dist
                    l.radius = (float) Math.max(0.04D, Math.min(0.6D, 0.5D * 3.0D / Math.max(1.0D, dist) * screen[2]));
                }
                lens = l;
            }
        }
        boolean distort = lens != null && lens.strength > 0.001F;
        float glitch = ClientSettings.get().screenGlitch ? screenGlitch : 0.0F;
        if (!distort && glitch <= 0.01F) {
            if (lens != null) {
                lens.runAfter();
            }
            return;
        }
        if (!ensureShaders(mc)) {
            if (lens != null) {
                lens.runAfter();
            }
            return;
        }
        float pt = event.getPartialTicks();
        Framebuffer main = mc.getFramebuffer();
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        try {
            float aspect = main.framebufferWidth / (float) Math.max(1, main.framebufferHeight);
            if (distort) {
                lens.apply(holeShader, aspect);
                holeShader.render(time % 1.0F);
                blitBack.render(pt);
                if (lens.mode == 1.0F && holeEntity != null && (holeEntity != view || mc.gameSettings.thirdPersonView != 0)) {
                    // игрок в центре не искажается: рисуем его поверх ещё раз (глубина кадра та же)
                    redraw(mc, holeEntity, pt);
                }
            }
            if (glitch > 0.01F) {
                set(glitchShader, "Strength", Math.min(1.0F, glitch) * (ClientSettings.get().flashes ? 1.0F : 0.5F));
                set(glitchShader, "Seed", RANDOM.nextFloat());
                glitchShader.render(time % 1.0F);
                blitBack.render(pt);
            }
        } catch (Throwable t) {
            shadersFailed = true;
            EcoServ.LOG.warn("Глитч-шейдер выключен: {}", t.toString());
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            main.bindFramebuffer(true);
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            if (lens != null) {
                // окно портала — поверх линзы (оно само и есть тень дыры)
                lens.runAfter();
            }
            if (distort) {
                ClientSettings.frame(Minecraft.getDebugFPS());
            }
        }
    }

    private static void redraw(Minecraft mc, Entity e, float pt) {
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        try {
            mc.getFramebuffer().bindFramebuffer(false);
            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            RenderHelper.enableStandardItemLighting();
            mc.entityRenderer.enableLightmap();
            mc.getRenderManager().renderEntityStatic(e, pt, false);
            mc.entityRenderer.disableLightmap();
            RenderHelper.disableStandardItemLighting();
        } catch (Throwable t) {
            EcoServ.LOG.debug("Перерисовка игрока не удалась: {}", t.toString());
        } finally {
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
        }
    }

    /**
     * Точка мира -> экран (0..1, начало внизу слева, как у текстуры кадра) и масштаб проекции
     * (1 / tan(половины угла обзора)). null — точка за спиной.
     */
    static float[] project(Minecraft mc, double x, double y, double z) {
        MODELVIEW.clear();
        PROJECTION.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        double rx = x - mc.getRenderManager().viewerPosX;
        double ry = y - mc.getRenderManager().viewerPosY;
        double rz = z - mc.getRenderManager().viewerPosZ;
        float[] mv = new float[16];
        float[] pr = new float[16];
        MODELVIEW.get(mv);
        PROJECTION.get(pr);
        // столбцовый порядок OpenGL: элемент (строка r, столбец c) = m[c * 4 + r]
        double ex = mv[0] * rx + mv[4] * ry + mv[8] * rz + mv[12];
        double ey = mv[1] * rx + mv[5] * ry + mv[9] * rz + mv[13];
        double ez = mv[2] * rx + mv[6] * ry + mv[10] * rz + mv[14];
        double ew = mv[3] * rx + mv[7] * ry + mv[11] * rz + mv[15];
        double cx = pr[0] * ex + pr[4] * ey + pr[8] * ez + pr[12] * ew;
        double cy = pr[1] * ex + pr[5] * ey + pr[9] * ez + pr[13] * ew;
        double cw = pr[3] * ex + pr[7] * ey + pr[11] * ez + pr[15] * ew;
        if (cw <= 0.05D) {
            return null;
        }
        float sx = (float) (cx / cw * 0.5D + 0.5D);
        float sy = (float) (cy / cw * 0.5D + 0.5D);
        if (sx < -0.3F || sx > 1.3F || sy < -0.3F || sy > 1.3F) {
            return null;
        }
        return new float[]{sx, sy, pr[5]};
    }
}
