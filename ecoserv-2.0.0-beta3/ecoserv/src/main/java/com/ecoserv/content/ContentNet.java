package com.ecoserv.content;

import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.v2.Features;
import com.ecoserv.v2.V2Actions;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Сеть своего контента: рассылка набора при входе, приём текстур из рисовалки и моделей из постройки. */
public final class ContentNet {

    private static final Map<UUID, ByteArrayOutputStream> PARTS = new HashMap<UUID, ByteArrayOutputStream>();

    private ContentNet() {
    }

    public static void init() {
        Features.JOIN.add(CustomContent::send);
        V2Actions.on("px_save", (p, d) -> {
            if (!Perms.isAdmin(p)) {
                return;
            }
            String key = clean(d.getString("k"));
            byte[] png = d.getByteArray("png");
            if (key.isEmpty() || png.length == 0 || png.length > 64 * 1024) {
                return;
            }
            CustomContent.buffer().textures.put(key, png);
            CustomContent.saveBuffer();
            ChatUtil.actionBar(p, "&aТекстура «" + key + "» сохранена в буфер");
            com.ecoserv.ui.UiManager.refreshAll(ContentScreen.class);
        });
        V2Actions.on("model_part", (p, d) -> {
            if (!Perms.isAdmin(p)) {
                return;
            }
            ByteArrayOutputStream b = PARTS.computeIfAbsent(p.getUniqueID(), k -> new ByteArrayOutputStream());
            if (d.getInteger("i") == 0) {
                b.reset();
            }
            byte[] part = d.getByteArray("b");
            b.write(part, 0, part.length);
            if (b.size() > 4 * 1024 * 1024) {
                PARTS.remove(p.getUniqueID());
                return;
            }
            if (d.getBoolean("last")) {
                PARTS.remove(p.getUniqueID());
                String key = clean(d.getString("k"));
                String json = ContentPack.gunzip(b.toByteArray());
                if (key.isEmpty() || json.isEmpty()) {
                    ChatUtil.actionBar(p, "&cМодель не получена");
                    return;
                }
                CustomContent.buffer().models.put(key, json);
                CustomContent.saveBuffer();
                ChatUtil.send(p, "&aМодель «" + key + "» сохранена в буфер. Сделай с ней блок или предмет и нажми «Применить».");
                com.ecoserv.ui.UiManager.refreshAll(ContentScreen.class);
            }
        });
    }

    public static String clean(String key) {
        return key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    /** Открыть рисовалку у админа. */
    public static void paint(EntityPlayerMP p, String key, int size) {
        NBTTagCompound d = new NBTTagCompound();
        d.setString("k", key);
        d.setInteger("size", size);
        byte[] png = CustomContent.buffer().textures.get(key);
        if (png != null) {
            d.setByteArray("png", png);
        }
        com.ecoserv.ui.UiManager.close(p);
        V2Actions.send(p, "px_edit", d);
    }

    /** Попросить клиента снять модель с постройки. */
    public static void capture(EntityPlayerMP p, String key, BlockPos min, BlockPos max) {
        NBTTagCompound d = new NBTTagCompound();
        d.setString("k", key);
        d.setLong("min", min.toLong());
        d.setLong("max", max.toLong());
        V2Actions.send(p, "model_capture", d);
    }
}
