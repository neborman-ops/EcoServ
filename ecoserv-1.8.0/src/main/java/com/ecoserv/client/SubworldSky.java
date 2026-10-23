package com.ecoserv.client;

import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldDims;
import com.ecoserv.subworld.SubworldView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Небо подмиров (п. 25): как у типа мира, из другого измерения (обычный мир, ад, край,
 * любой номер измерения) или сплошной цвет.
 */
@SideOnly(Side.CLIENT)
public final class SubworldSky {

    private static final ResourceLocation END_SKY = new ResourceLocation("textures/environment/end_sky.png");
    private static final Map<Integer, IRenderHandler> DELEGATES = new HashMap<Integer, IRenderHandler>();
    private static final Map<Integer, WorldProvider> FOREIGN = new HashMap<Integer, WorldProvider>();
    private static final IRenderHandler NONE = new IRenderHandler() {
        @Override
        public void render(float partialTicks, WorldClient world, Minecraft mc) {
            // неба нет — как в аду, всё решает цвет тумана
        }
    };
    private static final IRenderHandler END = new BoxSky(true, 0x282828);

    private SubworldSky() {
    }

    /** Отключились от сервера: чужие измерения на другом сервере другие. */
    public static void clear() {
        DELEGATES.clear();
        FOREIGN.clear();
    }

    /** Небо «из измерения», которое само подмир, — не берём (иначе бесконечная рекурсия). */
    private static boolean ours(int id) {
        try {
            return DimensionManager.isDimensionRegistered(id) && DimensionManager.getProviderType(id) == SubworldDims.TYPE;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String sky(int dim, String kind) {
        SubworldView v = SubworldDims.clientView(dim);
        String s = v == null ? "default" : v.sky;
        if ("default".equals(s)) {
            return Subworld.NETHER.equals(kind) ? "nether" : Subworld.END.equals(kind) ? "end" : "overworld";
        }
        return s;
    }

    @Nullable
    public static IRenderHandler renderer(int dim, String kind) {
        String s = sky(dim, kind);
        if ("color".equals(s)) {
            SubworldView v = SubworldDims.clientView(dim);
            return new BoxSky(false, v == null ? 0 : v.skyColor);
        }
        if ("end".equals(s)) {
            return END;
        }
        if ("nether".equals(s)) {
            return NONE;
        }
        if (s.startsWith("dim:")) {
            return foreign(s);
        }
        // обычное небо рисует сам Minecraft (провайдер на клиенте говорит, что мир «поверхностный»)
        return null;
    }

    /** Показывать ли обычное небо (для провайдера: isSurfaceWorld на клиенте). */
    public static boolean overworldSky(int dim, String kind) {
        return "overworld".equals(sky(dim, kind));
    }

    /** Небо чужого измерения: его собственный рендер неба (если есть). */
    @Nullable
    private static IRenderHandler foreign(String s) {
        int id;
        try {
            id = Integer.parseInt(s.substring(4).trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (id == 1) {
            return END;
        }
        if (id == -1) {
            return NONE;
        }
        if (ours(id)) {
            return null;
        }
        if (DELEGATES.containsKey(id)) {
            return DELEGATES.get(id);
        }
        IRenderHandler found = null;
        try {
            WorldProvider provider = provider(id);
            if (provider != null) {
                found = provider.getSkyRenderer();
            }
        } catch (Throwable t) {
            found = null;
        }
        DELEGATES.put(id, found);
        return found;
    }

    @Nullable
    private static WorldProvider provider(int id) {
        if (FOREIGN.containsKey(id)) {
            return FOREIGN.get(id);
        }
        WorldProvider provider = null;
        try {
            if (DimensionManager.isDimensionRegistered(id)) {
                provider = DimensionManager.createProviderFor(id);
                WorldClient world = Minecraft.getMinecraft().world;
                if (world != null) {
                    provider.setWorld(world);
                }
            }
        } catch (Throwable t) {
            provider = null;
        }
        FOREIGN.put(id, provider);
        return provider;
    }

    @Nullable
    public static Vec3d fogColor(int dim, String kind) {
        SubworldView v = SubworldDims.clientView(dim);
        if (v != null && v.fogColor >= 0) {
            return rgb(v.fogColor);
        }
        String s = sky(dim, kind);
        if ("color".equals(s) && v != null) {
            return rgb(v.skyColor);
        }
        if ("nether".equals(s)) {
            return new Vec3d(0.2D, 0.03D, 0.03D);
        }
        if ("end".equals(s)) {
            return new Vec3d(0.09D, 0.075D, 0.09D);
        }
        if (s.startsWith("dim:")) {
            try {
                int id = Integer.parseInt(s.substring(4).trim());
                if (ours(id)) {
                    return null;
                }
                WorldProvider p = provider(id);
                return p == null ? null : p.getFogColor(0.5F, 0.0F);
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    public static boolean netherFog(int dim) {
        SubworldView v = SubworldDims.clientView(dim);
        return v != null && "nether".equals(v.sky);
    }

    public static boolean solid(int dim) {
        SubworldView v = SubworldDims.clientView(dim);
        return v != null && "color".equals(v.sky);
    }

    public static boolean hideClouds(int dim, String kind) {
        String s = sky(dim, kind);
        return !"overworld".equals(s) && !s.startsWith("dim:");
    }

    private static Vec3d rgb(int color) {
        return new Vec3d(((color >> 16) & 255) / 255.0D, ((color >> 8) & 255) / 255.0D, (color & 255) / 255.0D);
    }

    /** Куб неба: текстура края или сплошной цвет. */
    private static final class BoxSky extends IRenderHandler {
        private final boolean textured;
        private final int color;

        BoxSky(boolean textured, int color) {
            this.textured = textured;
            this.color = color;
        }

        @Override
        public void render(float partialTicks, WorldClient world, Minecraft mc) {
            GlStateManager.disableFog();
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.depthMask(false);
            if (textured) {
                mc.getTextureManager().bindTexture(END_SKY);
            } else {
                GlStateManager.disableTexture2D();
            }
            int r = (color >> 16) & 255;
            int g = (color >> 8) & 255;
            int b = color & 255;
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            for (int i = 0; i < 6; ++i) {
                GlStateManager.pushMatrix();
                if (i == 1) {
                    GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
                } else if (i == 2) {
                    GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
                } else if (i == 3) {
                    GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
                } else if (i == 4) {
                    GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
                } else if (i == 5) {
                    GlStateManager.rotate(-90.0F, 0.0F, 0.0F, 1.0F);
                }
                if (textured) {
                    // 7 = GL_QUADS
                    buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
                    buffer.pos(-100.0D, -100.0D, -100.0D).tex(0.0D, 0.0D).color(r, g, b, 255).endVertex();
                    buffer.pos(-100.0D, -100.0D, 100.0D).tex(0.0D, 16.0D).color(r, g, b, 255).endVertex();
                    buffer.pos(100.0D, -100.0D, 100.0D).tex(16.0D, 16.0D).color(r, g, b, 255).endVertex();
                    buffer.pos(100.0D, -100.0D, -100.0D).tex(16.0D, 0.0D).color(r, g, b, 255).endVertex();
                } else {
                    buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
                    buffer.pos(-100.0D, -100.0D, -100.0D).color(r, g, b, 255).endVertex();
                    buffer.pos(-100.0D, -100.0D, 100.0D).color(r, g, b, 255).endVertex();
                    buffer.pos(100.0D, -100.0D, 100.0D).color(r, g, b, 255).endVertex();
                    buffer.pos(100.0D, -100.0D, -100.0D).color(r, g, b, 255).endVertex();
                }
                tessellator.draw();
                GlStateManager.popMatrix();
            }
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
        }
    }
}
