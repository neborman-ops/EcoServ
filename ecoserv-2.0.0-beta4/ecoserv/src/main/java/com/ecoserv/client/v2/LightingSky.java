package com.ecoserv.client.v2;

import com.ecoserv.lighting.LightingProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

/**
 * Небо и облака освещения 2.0 beta3: своё небо (цвета, солнце и луна нужного размера, звёзды, широта),
 * свои облака (покрытие, плотность, цвет).
 */
@SideOnly(Side.CLIENT)
public class LightingSky {

    private static final ResourceLocation SUN = new ResourceLocation("textures/environment/sun.png");
    private static final ResourceLocation MOON = new ResourceLocation("textures/environment/moon_phases.png");
    private static LightingProfile profile = LightingProfile.off();
    private static WorldProvider skyOn;
    private static WorldProvider cloudsOn;
    private static DynamicTexture cloudTex;
    private static double cloudCoverBuilt = -1;
    private static float[] stars = new float[0];
    private static int starsBuilt = -1;

    private static final IRenderHandler NO_CLOUDS = new IRenderHandler() {
        @Override
        public void render(float partialTicks, WorldClient world, Minecraft mc) {
        }
    };

    private static final IRenderHandler SKY = new IRenderHandler() {
        @Override
        public void render(float partialTicks, WorldClient world, Minecraft mc) {
            drawSky(partialTicks, world, mc);
        }
    };

    public static void apply(LightingProfile p) {
        profile = p;
    }

    /** Наклон пути солнца по широте (поворот вокруг оси Z). */
    public static double[] tilt(double x, double y, double z) {
        if (!LightingFx.on() || !profile.customSky) {
            return new double[]{x, y, z};
        }
        double a = Math.toRadians(profile.geographicLatitude - 41.733D) * 0.5D;
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new double[]{x, y * c - z * s, y * s + z * c};
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            skyOn = null;
            cloudsOn = null;
            return;
        }
        WorldProvider pr = mc.world.provider;
        boolean sky = LightingFx.on() && profile.customSky;
        if (sky && pr.getSkyRenderer() != SKY) {
            pr.setSkyRenderer(SKY);
            skyOn = pr;
        } else if (!sky && skyOn == pr && pr.getSkyRenderer() == SKY) {
            pr.setSkyRenderer(null);
            skyOn = null;
        }
        boolean hide = LightingFx.on() && profile.clouds && profile.hideVanillaClouds;
        if (hide && pr.getCloudRenderer() == null) {
            pr.setCloudRenderer(NO_CLOUDS);
            cloudsOn = pr;
        } else if (!hide && cloudsOn == pr && pr.getCloudRenderer() == NO_CLOUDS) {
            pr.setCloudRenderer(null);
            cloudsOn = null;
        }
    }

    private static void col(int c, float k, float a) {
        GlStateManager.color((c >> 16 & 255) / 255.0F * k, (c >> 8 & 255) / 255.0F * k, (c & 255) / 255.0F * k, a);
    }

    static void drawSky(float pt, WorldClient world, Minecraft mc) {
        LightingProfile p = profile;
        float angle = world.getCelestialAngle(pt);
        float day = (float) Math.max(0.0D, Math.min(1.0D, Math.cos(angle * Math.PI * 2.0D) * 2.0D + 0.5D));
        float k = 0.05F + day * 0.95F;
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        GlStateManager.disableTexture2D();
        GlStateManager.disableFog();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        // купол: верх и горизонт
        int top = p.skyTop;
        int hor = p.skyHorizon;
        b.begin(7, DefaultVertexFormats.POSITION_COLOR);
        int seg = 16;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            // верхнее кольцо
            vtx(b, 0, 100, 0, top, k);
            vtx(b, 0, 100, 0, top, k);
            vtx(b, Math.cos(a1) * 100, 15, Math.sin(a1) * 100, hor, k);
            vtx(b, Math.cos(a0) * 100, 15, Math.sin(a0) * 100, hor, k);
            // ниже горизонта
            vtx(b, Math.cos(a0) * 100, 15, Math.sin(a0) * 100, hor, k);
            vtx(b, Math.cos(a1) * 100, 15, Math.sin(a1) * 100, hor, k);
            vtx(b, Math.cos(a1) * 100, -40, Math.sin(a1) * 100, hor, k * 0.6F);
            vtx(b, Math.cos(a0) * 100, -40, Math.sin(a0) * 100, hor, k * 0.6F);
        }
        t.draw();
        GlStateManager.pushMatrix();
        GlStateManager.rotate((float) -((p.geographicLatitude - 41.733D) * 0.5D), 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(angle * 360.0F, 1.0F, 0.0F, 0.0F);
        // звёзды
        float starA = (1.0F - day) * 0.9F;
        if (p.starCount > 0 && starA > 0.01F) {
            if (starsBuilt != p.starCount) {
                buildStars(p.starCount);
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, starA);
            b.begin(7, DefaultVertexFormats.POSITION);
            for (int i = 0; i + 2 < stars.length; i += 3) {
                star(b, stars[i], stars[i + 1], stars[i + 2]);
            }
            t.draw();
        }
        if (p.celestialBodiesShown) {
            GlStateManager.enableTexture2D();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            float s = (float) (Math.tan(Math.toRadians(p.sunAngularSize / 2.0D)) * 100.0D) * 1.4F;
            mc.getTextureManager().bindTexture(SUN);
            b.begin(7, DefaultVertexFormats.POSITION_TEX);
            b.pos(-s, 100.0D, -s).tex(0.0D, 0.0D).endVertex();
            b.pos(s, 100.0D, -s).tex(1.0D, 0.0D).endVertex();
            b.pos(s, 100.0D, s).tex(1.0D, 1.0D).endVertex();
            b.pos(-s, 100.0D, s).tex(0.0D, 1.0D).endVertex();
            t.draw();
            float m = (float) (Math.tan(Math.toRadians(p.moonAngularSize / 2.0D)) * 100.0D) * 1.4F;
            mc.getTextureManager().bindTexture(MOON);
            int phase = world.getMoonPhase();
            int px = phase % 4;
            int py = phase / 4 % 2;
            float u0 = px / 4.0F;
            float v0 = py / 2.0F;
            float u1 = (px + 1) / 4.0F;
            float v1 = (py + 1) / 2.0F;
            b.begin(7, DefaultVertexFormats.POSITION_TEX);
            b.pos(-m, -100.0D, m).tex(u1, v1).endVertex();
            b.pos(m, -100.0D, m).tex(u0, v1).endVertex();
            b.pos(m, -100.0D, -m).tex(u0, v0).endVertex();
            b.pos(-m, -100.0D, -m).tex(u1, v0).endVertex();
            t.draw();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }
        GlStateManager.popMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.enableFog();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void vtx(BufferBuilder b, double x, double y, double z, int c, float k) {
        b.pos(x, y, z).color((c >> 16 & 255) / 255.0F * k, (c >> 8 & 255) / 255.0F * k, (c & 255) / 255.0F * k, 1.0F).endVertex();
    }

    private static void buildStars(int count) {
        Random r = new Random(10842L);
        stars = new float[count * 3];
        for (int i = 0; i < count; i++) {
            double x = r.nextFloat() * 2 - 1;
            double y = r.nextFloat() * 2 - 1;
            double z = r.nextFloat() * 2 - 1;
            double l = Math.sqrt(x * x + y * y + z * z);
            if (l < 0.01) {
                l = 1;
            }
            stars[i * 3] = (float) (x / l * 100);
            stars[i * 3 + 1] = (float) (y / l * 100);
            stars[i * 3 + 2] = (float) (z / l * 100);
        }
        starsBuilt = count;
    }

    private static void star(BufferBuilder b, float x, float y, float z) {
        float s = 0.18F;
        // плоский квадратик, повёрнутый к центру
        double ax = Math.abs(y) > 90 ? 1 : 0;
        double ay = Math.abs(y) > 90 ? 0 : 1;
        double ux = ay * z;
        double uy = -ax * z;
        double uz = ax * y - ay * x;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux = ux / ul * s;
        uy = uy / ul * s;
        uz = uz / ul * s;
        double vx = (y * uz - z * uy) / 100;
        double vy = (z * ux - x * uz) / 100;
        double vz = (x * uy - y * ux) / 100;
        b.pos(x - ux - vx, y - uy - vy, z - uz - vz).endVertex();
        b.pos(x + ux - vx, y + uy - vy, z + uz - vz).endVertex();
        b.pos(x + ux + vx, y + uy + vy, z + uz + vz).endVertex();
        b.pos(x - ux + vx, y - uy + vy, z - uz + vz).endVertex();
    }

    // --- облака ---------------------------------------------------------------------------------------

    private static void buildClouds(double cover) {
        int n = 128;
        if (cloudTex == null) {
            cloudTex = new DynamicTexture(n, n);
        }
        int[] px = cloudTex.getTextureData();
        Random r = new Random(4242L);
        float[] base = new float[16 * 16];
        for (int i = 0; i < base.length; i++) {
            base[i] = r.nextFloat();
        }
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float v = 0;
                float amp = 1;
                float norm = 0;
                for (int o = 0; o < 3; o++) {
                    int cell = 16 >> o;
                    float fx = (x / (float) n * cell);
                    float fy = (y / (float) n * cell);
                    v += smooth(base, fx, fy, cell) * amp;
                    norm += amp;
                    amp *= 0.5F;
                }
                v /= norm;
                float th = 1.0F - (float) cover;
                float a = Math.max(0.0F, Math.min(1.0F, (v - th) / 0.12F));
                px[y * n + x] = ((int) (a * 255) << 24) | 0xFFFFFF;
            }
        }
        cloudTex.updateDynamicTexture();
        cloudCoverBuilt = cover;
    }

    private static float smooth(float[] base, float x, float y, int cell) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = base[(y0 % cell) * 16 + (x0 % cell)];
        float b = base[(y0 % cell) * 16 + ((x0 + 1) % cell)];
        float c = base[((y0 + 1) % cell) * 16 + (x0 % cell)];
        float d = base[((y0 + 1) % cell) * 16 + ((x0 + 1) % cell)];
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRender(RenderWorldLastEvent event) {
        LightingProfile p = profile;
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (!LightingFx.on() || !p.clouds || mc.world == null || view == null || mc.world.provider.getDimension() != 0
                && !mc.world.provider.isSurfaceWorld()) {
            return;
        }
        if (cloudTex == null || Math.abs(cloudCoverBuilt - p.cloudCover) > 0.001D) {
            buildClouds(p.cloudCover);
        }
        float pt = event.getPartialTicks();
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        double h = 160.0D - vy;
        double size = 1200.0D;
        double time = (mc.world.getTotalWorldTime() + pt) * 0.0004D;
        double scale = 1.0D / 640.0D;
        double u0 = (vx - size) * scale + time;
        double v0 = (vz - size) * scale;
        double u1 = (vx + size) * scale + time;
        double v1 = (vz + size) * scale;
        float day = (float) Math.max(0.15D, Math.min(1.0D, Math.cos(mc.world.getCelestialAngle(pt) * Math.PI * 2.0D) * 2.0D + 0.5D));
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(cloudTex.getGlTextureId());
        org.lwjgl.opengl.GL11.glTexParameteri(3553, 10242, 10497);
        org.lwjgl.opengl.GL11.glTexParameteri(3553, 10243, 10497);
        org.lwjgl.opengl.GL11.glTexParameteri(3553, 10241, 9729);
        org.lwjgl.opengl.GL11.glTexParameteri(3553, 10240, 9729);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        float alpha = (float) (0.35D + p.cloudDensity * 0.6D);
        for (int layer = 0; layer < 4; layer++) {
            double y = h + layer * 0.9D;
            float shade = (0.78F + layer * 0.07F) * day;
            col(p.cloudColor, shade, alpha * (layer == 3 ? 1.0F : 0.55F));
            b.begin(7, DefaultVertexFormats.POSITION_TEX);
            b.pos(-size, y, -size).tex(u0, v0).endVertex();
            b.pos(size, y, -size).tex(u1, v0).endVertex();
            b.pos(size, y, size).tex(u1, v1).endVertex();
            b.pos(-size, y, size).tex(u0, v1).endVertex();
            t.draw();
        }
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }
}
