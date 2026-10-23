package com.ecoserv.client.v2;

import com.ecoserv.EcoServ;
import com.ecoserv.content.ContentPack;
import com.ecoserv.content.CustomContent;
import com.ecoserv.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Клиент своего контента: принимает набор от сервера, хранит его в .minecraft/ecoserv/content.dat (после
 * перезапуска игры он сразу на месте) и обновляет ресурсы, если набор поменялся.
 */
@SideOnly(Side.CLIENT)
public final class ContentClient {

    private static ContentPack incoming;
    private static String incomingHash = "";
    private static String currentHash = "";

    private ContentClient() {
    }

    private static File cache() {
        File d = new File(Minecraft.getMinecraft().gameDir, "ecoserv");
        d.mkdirs();
        return new File(d, "content.dat");
    }

    /** До загрузки ресурсов игры: подключить пак и прочитать кэш. */
    @SuppressWarnings("unchecked")
    public static void preInit() {
        try (FileInputStream in = new FileInputStream(cache())) {
            ContentPack p = ContentPack.read(CompressedStreamTools.readCompressed(in));
            CustomContent.setActive(p);
            currentHash = p.hash();
        } catch (Throwable t) {
            // кэша нет — пустой набор
        }
        try {
            Minecraft mc = Minecraft.getMinecraft();
            for (Field f : Minecraft.class.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                f.setAccessible(true);
                Object v = f.get(mc);
                if (v instanceof List && !((List<?>) v).isEmpty() && ((List<?>) v).get(0) instanceof IResourcePack) {
                    ((List<IResourcePack>) v).add(new ContentResourcePack());
                    return;
                }
            }
            EcoServ.LOG.warn("Свой контент: не нашёл список ресурс-паков");
        } catch (Throwable t) {
            EcoServ.LOG.warn("Свой контент: ресурс-пак не подключён — {}", t.toString());
        }
    }

    public static void register() {
        ClientV2.on("content_begin", d -> {
            incomingHash = d.getString("hash");
            incoming = ContentPack.read(d.getCompoundTag("defs"));
        });
        ClientV2.on("content_tex", d -> {
            if (incoming != null) {
                incoming.textures.put(d.getString("k"), d.getByteArray("png"));
            }
        });
        ClientV2.on("content_model", d -> {
            if (incoming != null) {
                incoming.models.put(d.getString("k"), ContentPack.gunzip(d.getByteArray("gz")));
            }
        });
        ClientV2.on("content_end", d -> finish());
    }

    private static void finish() {
        if (incoming == null) {
            return;
        }
        ContentPack p = incoming;
        incoming = null;
        boolean changed = !incomingHash.equals(currentHash);
        CustomContent.setActive(p);
        currentHash = incomingHash;
        try (FileOutputStream out = new FileOutputStream(cache())) {
            CompressedStreamTools.writeCompressed(p.write(), out);
        } catch (Throwable t) {
            EcoServ.LOG.warn("Свой контент: кэш не записан — {}", t.toString());
        }
        if (changed) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString(ChatUtil.color("&b[EcoServ] &7Контент сервера обновился — подгружаю текстуры и модели…")));
            }
            mc.refreshResources();
        }
    }
}
