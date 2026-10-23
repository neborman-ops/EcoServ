package com.ecoserv.client.v2;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/** 2.0 beta4: блок-дисплеи в мире — блок или предмет со свободным поворотом (кватернион) и размером по осям. */
@SideOnly(Side.CLIENT)
public class DisplayRender {

    public static final class D {
        public String id;
        public double x;
        public double y;
        public double z;
        public double qx;
        public double qy;
        public double qz;
        public double qw = 1;
        public double sx = 1;
        public double sy = 1;
        public double sz = 1;
        String item;
        String mode;
        boolean glow;
        double spin;
        double vd;
        ItemStack stack;
        IBlockState state;
    }

    public static final List<D> ALL = new ArrayList<D>();

    public static void register() {
        ClientV2.on("displays", d -> {
            ALL.clear();
            NBTTagList l = d.getTagList("d", 10);
            for (int i = 0; i < l.tagCount(); i++) {
                NBTTagCompound t = l.getCompoundTagAt(i);
                D x = new D();
                x.id = t.getString("id");
                x.x = t.getDouble("x");
                x.y = t.getDouble("y");
                x.z = t.getDouble("z");
                x.qx = t.getDouble("qx");
                x.qy = t.getDouble("qy");
                x.qz = t.getDouble("qz");
                x.qw = t.getDouble("qw");
                x.sx = t.getDouble("sx");
                x.sy = t.getDouble("sy");
                x.sz = t.getDouble("sz");
                x.item = t.getString("item");
                x.mode = t.getString("mode");
                x.glow = t.getBoolean("glow");
                x.spin = t.getDouble("spin");
                x.vd = t.getDouble("vd");
                resolve(x);
                ALL.add(x);
            }
        });
    }

    public static void reset() {
        ALL.clear();
    }

    @SuppressWarnings("deprecation")
    private static void resolve(D d) {
        try {
            String[] p = d.item.split("@");
            Item it = net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(new ResourceLocation(p[0]));
            int meta = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            d.stack = it == null ? ItemStack.EMPTY : new ItemStack(it, 1, meta);
            if (it instanceof ItemBlock && !"item".equals(d.mode)) {
                Block b = ((ItemBlock) it).getBlock();
                IBlockState st = b.getStateFromMeta(it.getMetadata(meta));
                d.state = st.getRenderType() == EnumBlockRenderType.MODEL ? st : null;
            }
            if ("block".equals(d.mode) && d.state == null && it instanceof ItemBlock) {
                d.state = ((ItemBlock) it).getBlock().getDefaultState();
            }
        } catch (Throwable t) {
            d.stack = ItemStack.EMPTY;
            d.state = null;
        }
    }

    /** Поворот кватернионом через угол и ось. */
    static void rotate(double qx, double qy, double qz, double qw) {
        double w = Math.max(-1.0D, Math.min(1.0D, qw));
        double angle = 2.0D * Math.acos(w);
        double s = Math.sqrt(1.0D - w * w);
        if (s < 1e-6 || angle < 1e-6) {
            return;
        }
        GlStateManager.rotate((float) Math.toDegrees(angle), (float) (qx / s), (float) (qy / s), (float) (qz / s));
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (ALL.isEmpty() || view == null || mc.world == null) {
            return;
        }
        float pt = event.getPartialTicks();
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        double time = (mc.world.getTotalWorldTime() + pt) / 20.0D;
        mc.entityRenderer.enableLightmap();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        for (D d0 : ALL) {
            // в редакторе — живые значения, пока объект тянут мышью
            D d = editorOverride(d0);
            double dx = d.x - vx;
            double dy = d.y - vy;
            double dz = d.z - vz;
            if (dx * dx + dy * dy + dz * dz > d.vd * d.vd) {
                continue;
            }
            int light = d.glow ? 0xF000F0 : mc.world.getCombinedLight(new BlockPos(d.x, d.y, d.z), 0);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.pushMatrix();
            GlStateManager.translate(dx, dy, dz);
            if (d.spin != 0) {
                GlStateManager.rotate((float) (time * d.spin % 360.0D), 0.0F, 1.0F, 0.0F);
            }
            rotate(d.qx, d.qy, d.qz, d.qw);
            GlStateManager.scale((float) d.sx, (float) d.sy, (float) d.sz);
            try {
                if (d0.state != null) {
                    mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.translate(-0.5F, -0.5F, -0.5F);
                    // renderBlockBrightness сам поворачивает модель на 90° — компенсируем
                    GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
                    mc.getBlockRendererDispatcher().renderBlockBrightness(d0.state, 1.0F);
                } else if (!d0.stack.isEmpty()) {
                    RenderHelper.enableStandardItemLighting();
                    mc.getRenderItem().renderItem(d0.stack, ItemCameraTransforms.TransformType.FIXED);
                    RenderHelper.disableStandardItemLighting();
                }
            } catch (Throwable t) {
                d0.state = null;
                d0.stack = ItemStack.EMPTY;
            }
            GlStateManager.popMatrix();
        }
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static D editorOverride(D d) {
        if (!EditorState.open) {
            return d;
        }
        for (EditorState.Obj o : EditorState.OBJS) {
            if ("display".equals(o.type) && o.id.equals(d.id)) {
                D x = new D();
                x.id = d.id;
                x.x = o.x;
                x.y = o.y;
                x.z = o.z;
                x.qx = o.qx;
                x.qy = o.qy;
                x.qz = o.qz;
                x.qw = o.qw;
                x.sx = o.sx;
                x.sy = o.sy;
                x.sz = o.sz;
                x.glow = d.glow;
                x.spin = 0;
                x.vd = d.vd;
                return x;
            }
        }
        return d;
    }
}
