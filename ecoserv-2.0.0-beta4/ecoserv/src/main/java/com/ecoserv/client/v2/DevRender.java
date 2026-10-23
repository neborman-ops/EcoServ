package com.ecoserv.client.v2;

import com.ecoserv.dev.BlockMarker;
import com.ecoserv.dev.TileLogic;
import com.ecoserv.dev.TileMarker;
import com.ecoserv.v2.ModRegistry2;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Что видит разработчик (2.0): рамки зон (красные — глобальные, синие — локальные, оранжевые — триггеры,
 * фиолетовые — порталы, зелёные — приваты), маркеры с подписями, блоки-программы. Включается режимом /dev
 * или очками разработчика на голове.
 */
@SideOnly(Side.CLIENT)
public class DevRender {

    private static boolean devMode;
    private static final List<Box> BOXES = new ArrayList<Box>();

    private static final class Box {
        String name;
        String kind;
        int color;
        AxisAlignedBB bb;
    }

    public static void register() {
        ClientV2.on("dev", d -> devMode = d.getBoolean("on"));
        ClientV2.on("dev_zones", DevRender::zones);
    }

    private static void zones(NBTTagCompound d) {
        BOXES.clear();
        NBTTagList list = d.getTagList("boxes", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int[] b = t.getIntArray("b");
            if (b.length != 6) {
                continue;
            }
            Box x = new Box();
            x.name = t.getString("n");
            x.kind = t.getString("k");
            x.color = t.getInteger("c");
            x.bb = new AxisAlignedBB(b[0], b[1], b[2], b[3], b[4], b[5]);
            BOXES.add(x);
        }
    }

    public static void reset() {
        devMode = false;
        BOXES.clear();
    }

    /** Видно ли скрытое: режим /dev или очки. */
    public static boolean visible() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return false;
        }
        return devMode || mc.player.getItemStackFromSlot(EntityEquipmentSlot.HEAD).getItem() == ModRegistry2.DEV_GLASSES;
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || !visible()) {
            return;
        }
        Entity view = mc.getRenderViewEntity();
        if (view == null) {
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
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        for (Box b : BOXES) {
            float r = (b.color >> 16 & 255) / 255.0F;
            float g = (b.color >> 8 & 255) / 255.0F;
            float bl = (b.color & 255) / 255.0F;
            AxisAlignedBB bb = b.bb.offset(-vx, -vy, -vz);
            RenderGlobal.renderFilledBox(bb, r, g, bl, 0.08F);
            GlStateManager.glLineWidth(2.0F);
            RenderGlobal.drawSelectionBoundingBox(bb, r, g, bl, 0.9F);
        }
        // маркеры и блоки-программы
        List<TileEntity> tiles = new ArrayList<TileEntity>(mc.world.loadedTileEntityList);
        for (TileEntity te : tiles) {
            if (!(te instanceof TileMarker) && !(te instanceof TileLogic)) {
                continue;
            }
            if (te.getPos().distanceSq(vx, vy, vz) > 64.0D * 64.0D) {
                continue;
            }
            int color;
            double inset;
            if (te instanceof TileMarker) {
                int type = te.getBlockMetadata() & 15;
                color = BlockMarker.COLORS[type];
                inset = 0.25D;
            } else {
                color = ((TileLogic) te).global() ? 0xFF3030 : 0x3070FF;
                inset = -0.02D;
            }
            float r = (color >> 16 & 255) / 255.0F;
            float g = (color >> 8 & 255) / 255.0F;
            float bl = (color & 255) / 255.0F;
            AxisAlignedBB bb = new AxisAlignedBB(te.getPos()).grow(-inset).offset(-vx, -vy, -vz);
            RenderGlobal.renderFilledBox(bb, r, g, bl, te instanceof TileMarker ? 0.35F : 0.12F);
            RenderGlobal.drawSelectionBoundingBox(bb, r, g, bl, 1.0F);
        }
        GlStateManager.enableTexture2D();
        GlStateManager.depthMask(true);
        // подписи
        float yaw = mc.getRenderManager().playerViewY;
        float pitch = mc.getRenderManager().playerViewX;
        boolean third = mc.gameSettings.thirdPersonView == 2;
        for (Box b : BOXES) {
            double cx = (b.bb.minX + b.bb.maxX) / 2.0D - vx;
            double cy = b.bb.maxY + 0.3D - vy;
            double cz = (b.bb.minZ + b.bb.maxZ) / 2.0D - vz;
            if (cx * cx + cy * cy + cz * cz < 96.0D * 96.0D) {
                EntityRenderer.drawNameplate(mc.fontRenderer, b.name + " §7(" + b.kind + ")", (float) cx, (float) cy, (float) cz,
                        0, yaw, pitch, third, false);
            }
        }
        for (TileEntity te : tiles) {
            if (te.getPos().distanceSq(vx, vy, vz) > 24.0D * 24.0D) {
                continue;
            }
            String label = null;
            if (te instanceof TileMarker) {
                TileMarker m = (TileMarker) te;
                int type = te.getBlockMetadata() & 15;
                label = BlockMarker.TITLES[type] + (m.name.isEmpty() ? "" : " §e" + m.name) + (m.data.isEmpty() ? "" : " §7" + m.data);
            } else if (te instanceof TileLogic) {
                TileLogic l = (TileLogic) te;
                label = (l.global() ? "§cглоб." : "§9лок.") + " §f" + (l.name.isEmpty() ? "программа" : l.name) + " §7" + l.trigger;
            }
            if (label != null) {
                EntityRenderer.drawNameplate(mc.fontRenderer, label, (float) (te.getPos().getX() + 0.5D - vx),
                        (float) (te.getPos().getY() + 1.2D - vy), (float) (te.getPos().getZ() + 0.5D - vz), 0, yaw, pitch, third, false);
            }
        }
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Надписи-маркеры видны всем (это «текст в мире»). */
    @SubscribeEvent
    public void onRenderText(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (mc.world == null || view == null) {
            return;
        }
        float pt = event.getPartialTicks();
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        float yaw = mc.getRenderManager().playerViewY;
        float pitch = mc.getRenderManager().playerViewX;
        boolean third = mc.gameSettings.thirdPersonView == 2;
        for (TileEntity te : new ArrayList<TileEntity>(mc.world.loadedTileEntityList)) {
            if (!(te instanceof TileMarker) || (te.getBlockMetadata() & 15) != BlockMarker.TEXT) {
                continue;
            }
            TileMarker m = (TileMarker) te;
            if (m.data.isEmpty() || te.getPos().distanceSq(vx, vy, vz) > 48.0D * 48.0D) {
                continue;
            }
            String[] lines = m.data.split("\\|");
            for (int i = 0; i < lines.length; i++) {
                String text = lines[i].replace('&', '§');
                EntityRenderer.drawNameplate(mc.fontRenderer, text, (float) (te.getPos().getX() + 0.5D - vx),
                        (float) (te.getPos().getY() + 0.5D + (lines.length - 1 - i) * 0.27D - vy),
                        (float) (te.getPos().getZ() + 0.5D - vz), 0, yaw, pitch, third, false);
            }
        }
    }
}
