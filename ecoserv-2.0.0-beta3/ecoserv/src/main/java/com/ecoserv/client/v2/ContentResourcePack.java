package com.ecoserv.client.v2;

import com.ecoserv.content.ContentPack;
import com.ecoserv.content.CustomContent;
import com.google.common.collect.ImmutableSet;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Ресурс-пак своего контента (2.0 beta3), который мод собирает сам из набора сервера: состояния и модели слотов
 * блоков и предметов, текстуры и 3D-модели. Пустые слоты получают заглушку, чтобы в логе не было ошибок.
 */
@SideOnly(Side.CLIENT)
public class ContentResourcePack implements IResourcePack {

    private static final String PLACEHOLDER = "ecoserv:blocks/logic_block";

    @Override
    public InputStream getInputStream(ResourceLocation rl) throws IOException {
        String s = text(rl.getPath());
        if (s != null) {
            return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
        }
        byte[] png = png(rl.getPath());
        if (png != null) {
            return new ByteArrayInputStream(png);
        }
        throw new FileNotFoundException(rl.toString());
    }

    @Override
    public boolean resourceExists(ResourceLocation rl) {
        if (!"ecoserv".equals(rl.getNamespace())) {
            return false;
        }
        String p = rl.getPath();
        return text(p) != null || png(p) != null;
    }

    @Override
    public Set<String> getResourceDomains() {
        return ImmutableSet.of("ecoserv");
    }

    @Override
    public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer serializer, String section) throws IOException {
        return null;
    }

    @Override
    public BufferedImage getPackImage() throws IOException {
        throw new FileNotFoundException("pack.png");
    }

    @Override
    public String getPackName() {
        return "EcoServ: контент сервера";
    }

    // --- что отдаём ----------------------------------------------------------------------------------

    private static int slot(String p, String prefix, String suffix) {
        if (!p.startsWith(prefix) || !p.endsWith(suffix)) {
            return -1;
        }
        try {
            return Integer.parseInt(p.substring(prefix.length(), p.length() - suffix.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String tex(String key) {
        ContentPack pack = CustomContent.active();
        return key != null && !key.isEmpty() && pack.textures.containsKey(key) ? "ecoserv:custom/" + key : PLACEHOLDER;
    }

    private static String text(String p) {
        ContentPack pack = CustomContent.active();
        int s = slot(p, "blockstates/cslot_", ".json");
        if (s >= 0 && s < ContentPack.BLOCKS) {
            return "{\"variants\":{\"normal\":{\"model\":\"ecoserv:cslot_" + s + "\"}}}";
        }
        s = slot(p, "models/block/cslot_", ".json");
        if (s >= 0 && s < ContentPack.BLOCKS) {
            ContentPack.BlockDef d = pack.block(s);
            if (d == null) {
                return "{\"parent\":\"block/cube_all\",\"textures\":{\"all\":\"" + PLACEHOLDER + "\"}}";
            }
            switch (d.shape) {
                case "column":
                    return "{\"parent\":\"block/cube_column\",\"textures\":{\"end\":\"" + tex(d.top.isEmpty() ? d.texture : d.top)
                            + "\",\"side\":\"" + tex(d.side.isEmpty() ? d.texture : d.side) + "\"}}";
                case "cross":
                    return "{\"parent\":\"block/cross\",\"textures\":{\"cross\":\"" + tex(d.texture) + "\"}}";
                case "model":
                    if (pack.models.containsKey(d.model)) {
                        return "{\"parent\":\"ecoserv:block/cmodel_" + d.model + "\"}";
                    }
                    return "{\"parent\":\"block/cube_all\",\"textures\":{\"all\":\"" + tex(d.texture) + "\"}}";
                default:
                    return "{\"parent\":\"block/cube_all\",\"textures\":{\"all\":\"" + tex(d.texture) + "\"}}";
            }
        }
        s = slot(p, "models/item/cslot_", ".json");
        if (s >= 0 && s < ContentPack.BLOCKS) {
            ContentPack.BlockDef d = pack.block(s);
            if (d != null && "cross".equals(d.shape)) {
                return "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"" + tex(d.texture) + "\"}}";
            }
            return "{\"parent\":\"ecoserv:block/cslot_" + s + "\"}";
        }
        s = slot(p, "models/item/citem_", ".json");
        if (s >= 0 && s < ContentPack.ITEMS) {
            ContentPack.ItemDef d = pack.item(s);
            if (d != null && !d.model.isEmpty() && pack.models.containsKey(d.model)) {
                return "{\"parent\":\"ecoserv:block/cmodel_" + d.model + "\",\"display\":{\"gui\":{\"rotation\":[30,225,0],"
                        + "\"translation\":[0,0,0],\"scale\":[0.625,0.625,0.625]},\"ground\":{\"rotation\":[0,0,0],\"translation\":[0,3,0],"
                        + "\"scale\":[0.25,0.25,0.25]},\"fixed\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[0.5,0.5,0.5]},"
                        + "\"thirdperson_righthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":[0.375,0.375,0.375]},"
                        + "\"firstperson_righthand\":{\"rotation\":[0,45,0],\"translation\":[0,0,0],\"scale\":[0.4,0.4,0.4]}}}";
            }
            return "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"" + (d == null ? PLACEHOLDER : tex(d.texture)) + "\"}}";
        }
        if (p.startsWith("models/block/cmodel_") && p.endsWith(".json")) {
            String key = p.substring("models/block/cmodel_".length(), p.length() - 5);
            return pack.models.get(key);
        }
        return null;
    }

    private static byte[] png(String p) {
        if (p.startsWith("textures/custom/") && p.endsWith(".png")) {
            return CustomContent.active().textures.get(p.substring("textures/custom/".length(), p.length() - 4));
        }
        return null;
    }
}
