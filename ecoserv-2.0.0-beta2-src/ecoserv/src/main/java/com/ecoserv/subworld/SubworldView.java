package com.ecoserv.subworld;

import io.netty.buffer.ByteBuf;
import com.ecoserv.network.NetUtil;

/**
 * То, что клиенту нужно знать об измерении подмира: чей это мир, тип (для неба и света),
 * небо, туман. Сервер шлёт при входе игрока и при изменениях.
 */
public class SubworldView {

    public int dim;
    public String subworld = "";
    public String name = "";
    public String type = Subworld.NORMAL;
    public String sky = "default";
    public int skyColor;
    public int fogColor = -1;
    public boolean abyss;
    public int abyssY = -16;
    /** Свои квесты: id глав FTB Quests подмира (клиент прячет остальные главы). */
    public boolean ownQuests;
    public java.util.List<String> chapters = new java.util.ArrayList<String>();
    public boolean glitch;

    public static SubworldView of(Subworld sw, SubworldDim d) {
        SubworldView v = new SubworldView();
        v.dim = d.dim;
        v.subworld = sw.id;
        v.name = sw.name;
        v.type = sw.type;
        v.sky = sw.sky;
        v.skyColor = sw.skyColor;
        v.fogColor = sw.fogColor;
        v.abyss = sw.abyss.enabled;
        v.abyssY = sw.abyss.y;
        v.ownQuests = sw.ownQuests;
        v.chapters = new java.util.ArrayList<String>(sw.questChapters);
        v.glitch = sw.glitch;
        return v;
    }

    /** Обычный мир с настройками неба/своих квестов (1.8.0): subworld пустой — клиент его не регистрирует. */
    public static SubworldView ofMain(com.ecoserv.world.WorldSettings.Settings s) {
        SubworldView v = new SubworldView();
        v.dim = s.dim;
        v.subworld = "";
        v.name = com.ecoserv.world.WorldNames.displayName(s.dim);
        v.type = s.dim == -1 ? Subworld.NETHER : s.dim == 1 ? Subworld.END : Subworld.NORMAL;
        v.sky = s.sky;
        v.skyColor = s.skyColor;
        v.fogColor = s.fogColor;
        v.abyss = s.abyss.enabled;
        v.abyssY = s.abyss.y;
        v.ownQuests = s.ownQuests;
        v.chapters = new java.util.ArrayList<String>(s.questChapters);
        return v;
    }

    /** Нужно ли слать клиенту вид обычного мира. */
    public static boolean mainNeeded(com.ecoserv.world.WorldSettings.Settings s) {
        return !"default".equals(s.sky) || s.fogColor >= 0 || s.ownQuests;
    }

    public boolean isMain() {
        return subworld == null || subworld.isEmpty();
    }

    public void write(ByteBuf buf) {
        buf.writeInt(dim);
        NetUtil.writeString(buf, subworld);
        NetUtil.writeString(buf, name);
        NetUtil.writeString(buf, type);
        NetUtil.writeString(buf, sky);
        buf.writeInt(skyColor);
        buf.writeInt(fogColor);
        buf.writeBoolean(abyss);
        buf.writeInt(abyssY);
        buf.writeBoolean(ownQuests);
        buf.writeShort(chapters.size());
        for (String c : chapters) {
            NetUtil.writeString(buf, c);
        }
        buf.writeBoolean(glitch);
    }

    public static SubworldView read(ByteBuf buf) {
        SubworldView v = new SubworldView();
        v.dim = buf.readInt();
        v.subworld = NetUtil.readString(buf);
        v.name = NetUtil.readString(buf);
        v.type = NetUtil.readString(buf);
        v.sky = NetUtil.readString(buf);
        v.skyColor = buf.readInt();
        v.fogColor = buf.readInt();
        v.abyss = buf.readBoolean();
        v.abyssY = buf.readInt();
        v.ownQuests = buf.readBoolean();
        int n = buf.readShort();
        for (int i = 0; i < n; i++) {
            v.chapters.add(NetUtil.readString(buf));
        }
        v.glitch = buf.readBoolean();
        return v;
    }
}
