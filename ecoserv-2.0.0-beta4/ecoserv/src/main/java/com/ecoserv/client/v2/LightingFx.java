package com.ecoserv.client.v2;

import com.ecoserv.EcoServ;
import com.ecoserv.lighting.LightingProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Освещение 2.0 beta3 на клиенте (как Lighting в Roblox): цвет света в помещениях и на улице, яркость солнца,
 * тени, время суток, туман, экспозиция и пост-эффекты — атмосфера, свечение, размытие, цветокоррекция,
 * глубина резкости, солнечные лучи, цветные источники света.
 */
@SideOnly(Side.CLIENT)
public class LightingFx {

    /** Источник света у клиента. */
    public static final class Light {
        public String kind;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public double brightness;
        public int color;
        public double range;
        public double angle;
        public boolean blockLight;
    }

    private static LightingProfile profile = LightingProfile.off();
    public static final List<Light> LIGHTS = new ArrayList<Light>();

    // lightmap
    private static Field lightmapField;
    private static Field lightmapTexField;
    private static boolean reflFailed;
    private static int[] baseLightmap = new int[256];
    private static int[] lastWritten = new int[256];

    // пост-эффекты
    private static boolean failed;
    private static Framebuffer fbA;
    private static Framebuffer fbB;
    private static Framebuffer fbC;
    private static int depthTex = -1;
    private static int fbW;
    private static int fbH;
    private static Shader light;
    private static Shader bright;
    private static Shader blurH;
    private static Shader blurV;
    private static Shader composite;
    private static final FloatBuffer PROJ = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer VIEW = BufferUtils.createFloatBuffer(16);
    private static final float[] P = new float[16];
    private static final float[] M = new float[16];
    private static boolean captured;
    private static double camX;
    private static double camY;
    private static double camZ;
    private static float sunX;
    private static float sunY;
    private static boolean sunVisible;
    private static float[] sunView = {0, 1, 0};

    public static void register() {
        ClientV2.on("lighting", d -> {
            profile = LightingProfile.read(d);
            LIGHTS.clear();
            NBTTagList l = d.getTagList("lights", 10);
            for (int i = 0; i < l.tagCount(); i++) {
                NBTTagCompound t = l.getCompoundTagAt(i);
                Light o = new Light();
                o.kind = t.getString("k");
                o.x = t.getDouble("x");
                o.y = t.getDouble("y");
                o.z = t.getDouble("z");
                o.yaw = t.getFloat("yaw");
                o.pitch = t.getFloat("pitch");
                o.brightness = t.getDouble("b");
                o.color = t.getInteger("c");
                o.range = t.getDouble("r");
                o.angle = t.getDouble("a");
                o.blockLight = t.getBoolean("bl");
                LIGHTS.add(o);
            }
            LightingSky.apply(profile);
        });
    }

    public static LightingProfile profile() {
        return profile;
    }

    public static void reset() {
        profile = LightingProfile.off();
        LIGHTS.clear();
        LightingSky.apply(profile);
    }

    private static int level() {
        int q = com.ecoserv.client.ClientSettings.get().lightingQuality;
        if (!profile.enabled || q <= 0) {
            return -1;
        }
        int l;
        switch (profile.technology) {
            case "Compatibility":
                l = 0;
                break;
            case "Voxel":
                l = 1;
                break;
            case "ShadowMap":
                l = 2;
                break;
            default:
                l = 3;
        }
        return q == 1 ? Math.min(l, 0) : l;
    }

    /** Освещение сервера включено у этого игрока (настройка «Освещение сервера» не «Выкл»). */
    public static boolean on() {
        return level() >= 0;
    }

    // --- время ---------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            lockTime();
        }
    }

    /**
     * 2.0 beta4: время ставится и перед каждым кадром — иначе пакет времени от сервера (раз в секунду) успевал
     * показать «настоящее» небо на один кадр, и небо мигало (особенно в «Хорроре»).
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            lockTime();
        }
    }

    private static void lockTime() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || !on() || !profile.lockTime) {
            return;
        }
        double h = ((profile.clockTime - 6.0D) % 24.0D + 24.0D) % 24.0D;
        long day = Math.floorDiv(mc.world.getWorldTime(), 24000L);
        long want = day * 24000L + (long) (h * 1000.0D);
        if (mc.world.getWorldTime() != want) {
            mc.world.setWorldTime(want);
        }
    }

    // --- туман ---------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onFogColor(EntityViewRenderEvent.FogColors event) {
        if (on() && profile.customFog) {
            event.setRed((profile.fogColor >> 16 & 255) / 255.0F);
            event.setGreen((profile.fogColor >> 8 & 255) / 255.0F);
            event.setBlue((profile.fogColor & 255) / 255.0F);
        }
        updateLightmap();
    }

    @SubscribeEvent
    public void onFog(EntityViewRenderEvent.RenderFogEvent event) {
        if (!on() || !profile.customFog) {
            return;
        }
        if (profile.fogEnd >= 100000.0D) {
            GlStateManager.setFogStart(100000.0F);
            GlStateManager.setFogEnd(100001.0F);
            return;
        }
        GlStateManager.setFogStart((float) profile.fogStart);
        GlStateManager.setFogEnd((float) Math.max(profile.fogStart + 1.0D, profile.fogEnd));
    }

    // --- карта освещённости ----------------------------------------------------------------------------

    private static boolean reflect(EntityRenderer er) {
        if (reflFailed) {
            return false;
        }
        if (lightmapField != null) {
            return true;
        }
        try {
            for (Field f : EntityRenderer.class.getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == DynamicTexture.class && lightmapTexField == null) {
                    lightmapTexField = f;
                } else if (f.getType() == int[].class) {
                    Object v = f.get(er);
                    if (v instanceof int[] && ((int[]) v).length == 256) {
                        lightmapField = f;
                    }
                }
            }
            if (lightmapField == null || lightmapTexField == null) {
                reflFailed = true;
                EcoServ.LOG.warn("Освещение: карта света не найдена");
                return false;
            }
            return true;
        } catch (Throwable t) {
            reflFailed = true;
            EcoServ.LOG.warn("Освещение: {}", t.toString());
            return false;
        }
    }

    private static float ch(int c, int shift) {
        return (c >> shift & 255) / 255.0F;
    }

    /** Поверх ванильной карты света: цвета Ambient/OutdoorAmbient, яркость, тени, сдвиги цвета. */
    private static void updateLightmap() {
        if (level() < 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityRenderer er = mc.entityRenderer;
        if (er == null || !reflect(er)) {
            return;
        }
        try {
            int[] map = (int[]) lightmapField.get(er);
            if (!java.util.Arrays.equals(map, lastWritten)) {
                System.arraycopy(map, 0, baseLightmap, 0, 256);
            }
            LightingProfile p = profile;
            float exposure = level() == 0 ? (float) Math.pow(2.0D, p.exposureCompensation) : 1.0F;
            for (int i = 0; i < 256; i++) {
                int c = baseLightmap[i];
                float r = ch(c, 16);
                float g = ch(c, 8);
                float b = ch(c, 0);
                float sky = (i / 16) / 15.0F;
                float in = 1.0F - sky;
                // яркость солнца (2 — как в игре)
                float sun = 1.0F + ((float) p.brightness / 2.0F - 1.0F) * sky;
                r *= sun;
                g *= sun;
                b *= sun;
                // цвет света: в помещениях и на улице (70,70,70 — как в Roblox по умолчанию)
                float ar = ch(p.ambient, 16) / 0.2745F;
                float ag = ch(p.ambient, 8) / 0.2745F;
                float ab = ch(p.ambient, 0) / 0.2745F;
                float or = ch(p.outdoorAmbient, 16) / 0.2745F;
                float og = ch(p.outdoorAmbient, 8) / 0.2745F;
                float ob = ch(p.outdoorAmbient, 0) / 0.2745F;
                r *= in * (0.6F + 0.4F * ar) + sky * (0.6F + 0.4F * or);
                g *= in * (0.6F + 0.4F * ag) + sky * (0.6F + 0.4F * og);
                b *= in * (0.6F + 0.4F * ab) + sky * (0.6F + 0.4F * ob);
                r += ch(p.ambient, 16) * 0.08F * in;
                g += ch(p.ambient, 8) * 0.08F * in;
                b += ch(p.ambient, 0) * 0.08F * in;
                // сдвиги цвета
                float lum = (r + g + b) / 3.0F;
                r += ch(p.colorShiftTop, 16) * 0.35F * sky * lum + ch(p.colorShiftBottom, 16) * 0.35F * (1.0F - lum);
                g += ch(p.colorShiftTop, 8) * 0.35F * sky * lum + ch(p.colorShiftBottom, 8) * 0.35F * (1.0F - lum);
                b += ch(p.colorShiftTop, 0) * 0.35F * sky * lum + ch(p.colorShiftBottom, 0) * 0.35F * (1.0F - lum);
                // окружение окрашивает мир цветом неба
                float env = (float) p.environmentDiffuseScale * 0.12F * sky;
                r += ch(p.skyTop, 16) * env;
                g += ch(p.skyTop, 8) * env;
                b += ch(p.skyTop, 0) * env;
                // без теней — всё освещено ровно
                if (!p.globalShadows) {
                    float m = Math.max(r, Math.max(g, b));
                    float lift = Math.max(0.0F, 0.75F - m);
                    r += lift;
                    g += lift;
                    b += lift;
                }
                // мягкость
                float soft = 1.0F - (float) p.shadowSoftness * 0.45F;
                r = (float) Math.pow(clamp(r), soft) * exposure;
                g = (float) Math.pow(clamp(g), soft) * exposure;
                b = (float) Math.pow(clamp(b), soft) * exposure;
                if (level() == 0 && p.colorCorrection) {
                    r = clamp((r + (float) p.ccBrightness) * ch(p.ccTint, 16));
                    g = clamp((g + (float) p.ccBrightness) * ch(p.ccTint, 8));
                    b = clamp((b + (float) p.ccBrightness) * ch(p.ccTint, 0));
                }
                map[i] = 0xFF000000 | (int) (clamp(r) * 255) << 16 | (int) (clamp(g) * 255) << 8 | (int) (clamp(b) * 255);
            }
            System.arraycopy(map, 0, lastWritten, 0, 256);
            ((DynamicTexture) lightmapTexField.get(er)).updateDynamicTexture();
        } catch (Throwable t) {
            reflFailed = true;
            EcoServ.LOG.warn("Освещение: карта света — {}", t.toString());
        }
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    // --- глубина и матрицы ------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onWorldLast(RenderWorldLastEvent event) {
        captured = false;
        if (level() < 1 || failed || !OpenGlHelper.shadersSupported || !OpenGlHelper.isFramebufferEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            return;
        }
        try {
            Framebuffer main = mc.getFramebuffer();
            ensure(main);
            GlStateManager.bindTexture(depthTex);
            GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, 0, 0, main.framebufferWidth,
                    main.framebufferHeight, 0);
            GlStateManager.bindTexture(0);
            PROJ.clear();
            VIEW.clear();
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJ);
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, VIEW);
            PROJ.get(P);
            VIEW.get(M);
            float pt = event.getPartialTicks();
            camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
            camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
            camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
            // солнце
            float a = mc.world.getCelestialAngle(pt) * (float) Math.PI * 2.0F;
            double[] sw = LightingSky.tilt(-Math.sin(a), Math.cos(a), 0.0D);
            float[] sv = rot(sw[0], sw[1], sw[2]);
            sunView = norm(sv);
            float w = -sv[2];
            sunVisible = w > 0.01F && sw[1] > -0.05D;
            if (w > 0.0001F) {
                sunX = P[0] * sv[0] / w * 0.5F + 0.5F;
                sunY = P[5] * sv[1] / w * 0.5F + 0.5F;
            }
            captured = true;
            // 2.0 beta4: пост-эффекты сразу после мира — до руки и интерфейса, поэтому GUI больше не ломается
            apply(pt);
        } catch (Throwable t) {
            failed = true;
            EcoServ.LOG.warn("Освещение: пост-эффекты выключены — {}", t.toString());
        }
    }

    private static float[] norm(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1e-6F ? v : new float[]{v[0] / l, v[1] / l, v[2] / l};
    }

    /** Поворот вектора матрицей вида (без сдвига). */
    private static float[] rot(double x, double y, double z) {
        return new float[]{(float) (M[0] * x + M[4] * y + M[8] * z), (float) (M[1] * x + M[5] * y + M[9] * z),
                (float) (M[2] * x + M[6] * y + M[10] * z)};
    }

    /** Точка мира → пространство вида. */
    private static float[] toView(double x, double y, double z) {
        double rx = x - camX;
        double ry = y - camY;
        double rz = z - camZ;
        return new float[]{(float) (M[0] * rx + M[4] * ry + M[8] * rz + M[12]), (float) (M[1] * rx + M[5] * ry + M[9] * rz + M[13]),
                (float) (M[2] * rx + M[6] * ry + M[10] * rz + M[14])};
    }

    private static void ensure(Framebuffer main) {
        if (fbA == null || fbW != main.framebufferWidth || fbH != main.framebufferHeight) {
            fbW = main.framebufferWidth;
            fbH = main.framebufferHeight;
            if (fbA == null) {
                fbA = new Framebuffer(fbW, fbH, false);
                fbB = new Framebuffer(fbW, fbH, false);
                fbC = new Framebuffer(fbW, fbH, false);
            } else {
                fbA.createBindFramebuffer(fbW, fbH);
                fbB.createBindFramebuffer(fbW, fbH);
                fbC.createBindFramebuffer(fbW, fbH);
            }
            fbB.setFramebufferFilter(GL11.GL_LINEAR);
            fbC.setFramebufferFilter(GL11.GL_LINEAR);
            if (depthTex < 0) {
                depthTex = GL11.glGenTextures();
            }
            GlStateManager.bindTexture(depthTex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, fbW, fbH, 0, GL11.GL_DEPTH_COMPONENT,
                    GL11.GL_FLOAT, (FloatBuffer) null);
            GlStateManager.bindTexture(0);
            if (light != null) {
                light.deleteShader();
                bright.deleteShader();
                blurH.deleteShader();
                blurV.deleteShader();
                composite.deleteShader();
                light = null;
            }
        }
        if (light == null) {
            Minecraft mc = Minecraft.getMinecraft();
            try {
                light = new Shader(mc.getResourceManager(), "ecoserv:lighting", main, fbA);
                light.addAuxFramebuffer("DepthSampler", Integer.valueOf(depthTex), fbW, fbH);
                bright = new Shader(mc.getResourceManager(), "ecoserv:bright", fbA, fbB);
                blurH = new Shader(mc.getResourceManager(), "blur", fbB, fbC);
                blurV = new Shader(mc.getResourceManager(), "blur", fbC, fbB);
                composite = new Shader(mc.getResourceManager(), "ecoserv:composite", fbA, main);
                composite.addAuxFramebuffer("BloomSampler", fbB, fbW, fbH);
                composite.addAuxFramebuffer("DepthSampler", Integer.valueOf(depthTex), fbW, fbH);
                Matrix4f o = ortho(fbW, fbH);
                light.setProjectionMatrix(o);
                bright.setProjectionMatrix(o);
                blurH.setProjectionMatrix(o);
                blurV.setProjectionMatrix(o);
                composite.setProjectionMatrix(o);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // --- пост-обработка --------------------------------------------------------------------------------

    private static void set(Shader s, String name, float... v) {
        ShaderUniform u = s.getShaderManager().getShaderUniform(name);
        if (u == null) {
            return;
        }
        switch (v.length) {
            case 1:
                u.set(v[0]);
                break;
            case 2:
                u.set(v[0], v[1]);
                break;
            case 3:
                u.set(v[0], v[1], v[2]);
                break;
            default:
                u.set(v[0], v[1], v[2], v[3]);
        }
    }

    private static void apply(float pt) {
        if (!captured || light == null || level() < 1 || failed) {
            return;
        }
        captured = false;
        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer main = mc.getFramebuffer();
        LightingProfile p = profile;
        int lvl = level();
        try {
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.disableDepth();
            GlStateManager.disableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            float far = mc.gameSettings.renderDistanceChunks * 16.0F;
            float[] proj = {P[0], P[5], P[10], P[14]};
            set(light, "ProjParams", proj);
            set(light, "FarDist", far);
            float exp = (float) Math.pow(2.0D, p.exposureCompensation);
            if (p.colorCorrection) {
                set(light, "Grade", exp, (float) p.ccBrightness * 0.5F, (float) p.ccContrast, (float) p.ccSaturation);
                set(light, "Tint", ch(p.ccTint, 16), ch(p.ccTint, 8), ch(p.ccTint, 0));
            } else {
                set(light, "Grade", exp, 0, 0, 0);
                set(light, "Tint", 1, 1, 1);
            }
            boolean atm = p.atmosphere && lvl >= 2;
            set(light, "AtmOn", atm ? 1 : 0);
            set(light, "Atm", (float) p.atmDensity, (float) p.atmOffset, (float) p.atmGlare, (float) p.atmHaze);
            set(light, "AtmColor", ch(p.atmColor, 16), ch(p.atmColor, 8), ch(p.atmColor, 0));
            set(light, "AtmDecay", ch(p.atmDecay, 16), ch(p.atmDecay, 8), ch(p.atmDecay, 0));
            set(light, "Sun", sunX, sunY, sunVisible ? 1 : 0, (float) p.sunRaysIntensity);
            set(light, "SunView", sunView[0], sunView[1], sunView[2]);
            set(light, "Rays", (float) p.sunRaysSpread, p.sunRays ? 1 : 0);
            set(light, "Spec", lvl >= 3 ? (float) p.environmentSpecularScale * 0.6F : 0, 0);
            int n = 0;
            if (lvl >= 2) {
                List<Light> near = new ArrayList<Light>(LIGHTS);
                near.sort((x, y) -> Double.compare(d2(x), d2(y)));
                for (Light l : near) {
                    if (n >= 8) {
                        break;
                    }
                    if (d2(l) > (l.range + far) * (l.range + far)) {
                        continue;
                    }
                    float[] v = toView(l.x, l.y, l.z);
                    float b = (float) l.brightness;
                    set(light, "LightPos" + n, v[0], v[1], v[2], (float) l.range);
                    set(light, "LightCol" + n, ch(l.color, 16) * b, ch(l.color, 8) * b, ch(l.color, 0) * b, "point".equals(l.kind) ? 0 : 1);
                    double yr = Math.toRadians(l.yaw);
                    double pr = Math.toRadians(l.pitch);
                    float[] dv = norm(rot(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)));
                    float cos = (float) Math.cos(Math.toRadians(Math.min(179.0D, l.angle) / 2.0D));
                    set(light, "LightDir" + n, dv[0], dv[1], dv[2], cos);
                    n++;
                }
            }
            for (int i = n; i < 8; i++) {
                set(light, "LightPos" + i, 0, 0, 0, 0);
            }
            light.render(pt);
            boolean bloom = p.bloom && p.bloomIntensity > 0.0D;
            if (bloom) {
                set(bright, "Thresh", (float) Math.min(0.98D, p.bloomThreshold / 4.0D * 0.9D));
                bright.render(pt);
                float r = (float) Math.max(1.0D, p.bloomSize / 3.0D);
                set(blurH, "BlurDir", 1, 0);
                set(blurH, "Radius", r);
                set(blurV, "BlurDir", 0, 1);
                set(blurV, "Radius", r);
                blurH.render(pt);
                blurV.render(pt);
            }
            set(composite, "ProjParams", proj);
            set(composite, "FarDist", far);
            set(composite, "BloomAmt", bloom ? (float) p.bloomIntensity * 1.5F : 0.0F);
            set(composite, "BlurPx", p.blur ? (float) p.blurSize * 0.25F : 0.0F);
            set(composite, "DofOn", p.depthOfField && lvl >= 3 ? 1 : 0);
            set(composite, "Dof", (float) p.dofFarIntensity, (float) p.dofFocusDistance, (float) p.dofInFocusRadius,
                    (float) p.dofNearIntensity);
            composite.render(pt);
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        } catch (Throwable t) {
            failed = true;
            EcoServ.LOG.warn("Освещение: пост-эффекты выключены — {}", t.toString());
        } finally {
            restoreState(main);
        }
    }

    /** Вернуть состояние GL таким, каким его ждёт Minecraft после мира (рука, интерфейс). */
    private static void restoreState(Framebuffer main) {
        main.bindFramebuffer(true);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.disableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
    }

    private static double d2(Light l) {
        double dx = l.x - camX;
        double dy = l.y - camY;
        double dz = l.z - camZ;
        return dx * dx + dy * dy + dz * dz;
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
