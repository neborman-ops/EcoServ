package com.ecoserv.client.v2;

import com.ecoserv.content.ContentPack;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 3D-модель из постройки (2.0 beta3): каждый блок в выделении (до 16×16×16) становится кубиком модели со своими
 * текстурами сторон. Внутренние грани не сохраняются. Модель уходит на сервер частями.
 */
@SideOnly(Side.CLIENT)
public final class ModelCapture {

    private static final int MAX_ELEMENTS = 1200;

    private ModelCapture() {
    }

    static void capture(NBTTagCompound d) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            return;
        }
        String key = d.getString("k");
        BlockPos a = BlockPos.fromLong(d.getLong("min"));
        BlockPos b = BlockPos.fromLong(d.getLong("max"));
        int nx = b.getX() - a.getX() + 1;
        int ny = b.getY() - a.getY() + 1;
        int nz = b.getZ() - a.getZ() + 1;
        int n = Math.max(nx, Math.max(ny, nz));
        double f = 16.0D / n;
        double ox = (16 - nx * f) / 2;
        double oz = (16 - nz * f) / 2;
        Map<String, String> tex = new LinkedHashMap<String, String>();
        StringBuilder el = new StringBuilder();
        int count = 0;
        for (int y = 0; y < ny; y++) {
            for (int z = 0; z < nz; z++) {
                for (int x = 0; x < nx; x++) {
                    BlockPos p = a.add(x, y, z);
                    IBlockState st = mc.world.getBlockState(p);
                    if (st.getBlock() == Blocks.AIR) {
                        continue;
                    }
                    try {
                        st = st.getActualState(mc.world, p);
                    } catch (Throwable ignored) {
                        // как есть
                    }
                    IBakedModel m = mc.getBlockRendererDispatcher().getModelForState(st);
                    StringBuilder faces = new StringBuilder();
                    for (EnumFacing face : EnumFacing.values()) {
                        BlockPos q = p.offset(face);
                        boolean inside = q.getX() >= a.getX() && q.getX() <= b.getX() && q.getY() >= a.getY() && q.getY() <= b.getY()
                                && q.getZ() >= a.getZ() && q.getZ() <= b.getZ();
                        if (inside && mc.world.getBlockState(q).isOpaqueCube()) {
                            continue;
                        }
                        String sprite = sprite(m, st, face);
                        String var = tex.get(sprite);
                        if (var == null) {
                            var = "t" + tex.size();
                            tex.put(sprite, var);
                        }
                        if (faces.length() > 0) {
                            faces.append(',');
                        }
                        faces.append('"').append(face.getName()).append("\":{\"texture\":\"#").append(var).append("\",\"uv\":[0,0,16,16]}");
                    }
                    if (faces.length() == 0) {
                        continue;
                    }
                    if (++count > MAX_ELEMENTS) {
                        msg("§cСлишком много блоков для модели (больше " + MAX_ELEMENTS + "). Уменьши постройку.");
                        return;
                    }
                    if (el.length() > 0) {
                        el.append(',');
                    }
                    el.append("{\"from\":[").append(r(ox + x * f)).append(',').append(r(y * f)).append(',').append(r(oz + z * f))
                            .append("],\"to\":[").append(r(ox + (x + 1) * f)).append(',').append(r((y + 1) * f)).append(',')
                            .append(r(oz + (z + 1) * f)).append("],\"faces\":{").append(faces).append("}}");
                }
            }
        }
        if (count == 0) {
            msg("§cВ выделении нет блоков.");
            return;
        }
        StringBuilder json = new StringBuilder("{\"textures\":{");
        boolean first = true;
        String particle = null;
        for (Map.Entry<String, String> e : tex.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(e.getValue()).append("\":\"").append(e.getKey()).append('"');
            if (particle == null) {
                particle = e.getKey();
            }
        }
        json.append(",\"particle\":\"").append(particle).append("\"},\"elements\":[").append(el).append("]}");
        byte[] gz = ContentPack.gzip(json.toString());
        int part = 30000;
        for (int i = 0, k = 0; i < gz.length; i += part, k++) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("k", key);
            t.setInteger("i", k);
            byte[] chunk = java.util.Arrays.copyOfRange(gz, i, Math.min(gz.length, i + part));
            t.setByteArray("b", chunk);
            t.setBoolean("last", i + part >= gz.length);
            ClientV2.send("model_part", t);
        }
        msg("§aМодель «" + key + "»: кубиков " + count + ", текстур " + tex.size() + " — отправлена.");
    }

    private static String r(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    private static String sprite(IBakedModel m, IBlockState st, EnumFacing face) {
        try {
            List<BakedQuad> q = m.getQuads(st, face, 0L);
            if (q.isEmpty()) {
                for (BakedQuad x : m.getQuads(st, null, 0L)) {
                    if (x.getFace() == face) {
                        return x.getSprite().getIconName();
                    }
                }
            } else {
                return q.get(0).getSprite().getIconName();
            }
            return m.getParticleTexture().getIconName();
        } catch (Throwable t) {
            return "minecraft:blocks/stone";
        }
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(s));
        }
    }
}
