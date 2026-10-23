package com.ecoserv.cutscene;

import com.ecoserv.content.Actions;
import com.ecoserv.script.ScriptRunner;
import com.ecoserv.ui.UiError;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.v2.V2Actions;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Катсцены: world/ecoserv/cutscenes.json; проигрывание — камера на клиенте, текст и шаги — сервер. */
public final class CutsceneManager {

    public static final Pattern ID = Pattern.compile("^[a-z0-9_\\-]{1,32}$");
    private static final Type LIST = new TypeToken<ArrayList<Cutscene>>() {
    }.getType();
    private static final Map<String, Cutscene> ALL = new LinkedHashMap<String, Cutscene>();
    private static final Map<UUID, Playing> PLAYING = new HashMap<UUID, Playing>();

    private static final class Playing {
        Cutscene c;
        int t;
        int next;
        int dim;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        boolean wasInvisible;
        String context;
    }

    private CutsceneManager() {
    }

    public static void load() {
        ALL.clear();
        PLAYING.clear();
        List<Cutscene> list = JsonUtil.readType(DataStore.file("cutscenes.json"), LIST, null);
        if (list != null) {
            for (Cutscene c : list) {
                if (c != null && c.id != null && !c.id.isEmpty()) {
                    c.normalize();
                    ALL.put(c.id, c);
                }
            }
        }
    }

    public static void save() {
        JsonUtil.write(DataStore.file("cutscenes.json"), new ArrayList<Cutscene>(ALL.values()));
    }

    public static List<Cutscene> all() {
        return new ArrayList<Cutscene>(ALL.values());
    }

    @Nullable
    public static Cutscene get(String id) {
        return id == null ? null : ALL.get(id.toLowerCase(Locale.ROOT));
    }

    public static Cutscene create(EntityPlayerMP p, String id) {
        id = id.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            throw new UiError("id: латиница, цифры, _ и - (до 32).");
        }
        if (ALL.containsKey(id)) {
            throw new UiError("Катсцена «" + id + "» уже есть.");
        }
        Cutscene c = new Cutscene();
        c.id = id;
        c.dim = p.dimension;
        c.normalize();
        ALL.put(id, c);
        save();
        return c;
    }

    public static void delete(Cutscene c) {
        ALL.remove(c.id);
        save();
    }

    public static Cutscene.Point here(EntityPlayerMP p) {
        Cutscene.Point pt = new Cutscene.Point();
        pt.x = p.posX;
        pt.y = p.posY + p.getEyeHeight();
        pt.z = p.posZ;
        pt.yaw = p.rotationYaw;
        pt.pitch = p.rotationPitch;
        return pt;
    }

    public static boolean isPlaying(EntityPlayerMP p) {
        return PLAYING.containsKey(p.getUniqueID());
    }

    /** Запустить катсцену игроку. */
    public static void play(EntityPlayerMP p, Cutscene c, @Nullable String context) {
        if (c.points.size() < 2) {
            com.ecoserv.util.ChatUtil.send(p, "&cВ катсцене «" + c.id + "» нужно хотя бы 2 точки.");
            return;
        }
        stop(p, false);
        Playing pl = new Playing();
        pl.c = c;
        pl.dim = p.dimension;
        pl.x = p.posX;
        pl.y = p.posY;
        pl.z = p.posZ;
        pl.yaw = p.rotationYaw;
        pl.pitch = p.rotationPitch;
        pl.wasInvisible = p.isInvisible();
        pl.context = context;
        PLAYING.put(p.getUniqueID(), pl);
        NBTTagCompound d = new NBTTagCompound();
        d.setString("id", c.id);
        d.setBoolean("smooth", c.smooth);
        d.setBoolean("ease", c.ease);
        d.setBoolean("hud", c.hideHud);
        d.setBoolean("bars", c.bars);
        d.setBoolean("skip", c.skippable);
        NBTTagList pts = new NBTTagList();
        for (Cutscene.Point pt : c.points) {
            NBTTagCompound t = new NBTTagCompound();
            t.setDouble("x", pt.x);
            t.setDouble("y", pt.y);
            t.setDouble("z", pt.z);
            t.setFloat("yaw", pt.yaw);
            t.setFloat("pitch", pt.pitch);
            t.setInteger("ticks", pt.ticks);
            t.setInteger("hold", pt.hold);
            pts.appendTag(t);
        }
        d.setTag("p", pts);
        V2Actions.send(p, "cutscene", d);
        if (c.movePlayer) {
            p.setInvisible(true);
        }
    }

    /** Остановить (skip — игрок пропустил). */
    public static void stop(EntityPlayerMP p, boolean runEnd) {
        Playing pl = PLAYING.remove(p.getUniqueID());
        if (pl == null) {
            return;
        }
        V2Actions.send(p, "cutscene_stop", new NBTTagCompound());
        if (pl.c.movePlayer) {
            p.setInvisible(pl.wasInvisible);
            if (p.dimension == pl.dim) {
                p.connection.setPlayerLocation(pl.x, pl.y, pl.z, pl.yaw, pl.pitch);
            }
        }
        if (runEnd && !pl.c.endSteps.isEmpty()) {
            ScriptRunner.startSteps(p, "cutscene:" + pl.c.id, pl.c.endSteps, pl.context);
        }
        com.ecoserv.dev.DevEvents.fire("cutscene_end", p, pl.c.id);
    }

    public static void tick() {
        if (PLAYING.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Playing> e : new ArrayList<Map.Entry<UUID, Playing>>(PLAYING.entrySet())) {
            EntityPlayerMP p = PlayerUtil.online(e.getKey());
            Playing pl = e.getValue();
            if (p == null) {
                PLAYING.remove(e.getKey());
                continue;
            }
            Cutscene c = pl.c;
            while (pl.next < c.points.size() && pl.t >= c.arrive(pl.next)) {
                Cutscene.Point pt = c.points.get(pl.next);
                if (!pt.text.trim().isEmpty()) {
                    Actions.title(p, pt.text, c.id, 10, Math.max(30, pt.hold + 20), 10);
                }
                if (!pt.steps.isEmpty()) {
                    ScriptRunner.startSteps(p, "cutscene:" + c.id, pt.steps, pl.context);
                }
                if (c.movePlayer) {
                    // игрок — рядом с камерой, чтобы мир вокруг был прогружен
                    int nx = Math.min(pl.next + 1, c.points.size() - 1);
                    Cutscene.Point to = c.points.get(nx);
                    p.connection.setPlayerLocation(to.x, to.y - 1.6D, to.z, p.rotationYaw, p.rotationPitch);
                }
                pl.next++;
            }
            pl.t++;
            if (pl.t > c.length() + 5) {
                stop(p, true);
            }
        }
    }
}
