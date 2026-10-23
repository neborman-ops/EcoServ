package com.ecoserv.warp;

import com.ecoserv.economy.EconomyManager;
import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.init.ModSounds;
import com.ecoserv.role.RoleManager;
import com.ecoserv.server.ServerTasks;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldNames;
import com.ecoserv.world.WorldTeleport;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayerMP;

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

/** Варпы (1.9.0): хранение (world/ecoserv/warps.json), права, телепорт с задержкой и паузой. */
public final class WarpManager {

    public static final Pattern NAME = Pattern.compile("^[\\p{L}0-9_\\-]{1,32}$");
    private static final Type LIST = new TypeToken<ArrayList<Warp>>() {
    }.getType();
    private static final Map<String, Warp> WARPS = new LinkedHashMap<String, Warp>();
    private static final Map<UUID, Long> LAST = new HashMap<UUID, Long>();
    /** Ждут телепорта: игрок -> {x, y, z} точки, где стоял. */
    private static final Map<UUID, double[]> WAITING = new HashMap<UUID, double[]>();

    private WarpManager() {
    }

    public static void load() {
        WARPS.clear();
        List<Warp> list = JsonUtil.readType(DataStore.file("warps.json"), LIST, new ArrayList<Warp>());
        if (list != null) {
            for (Warp w : list) {
                if (w != null && w.name != null && !w.name.isEmpty()) {
                    w.normalize();
                    WARPS.put(key(w.name), w);
                }
            }
        }
    }

    public static void save() {
        JsonUtil.write(DataStore.file("warps.json"), new ArrayList<Warp>(WARPS.values()));
    }

    public static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static Warp get(String name) {
        return WARPS.get(key(name));
    }

    public static List<Warp> all() {
        return new ArrayList<Warp>(WARPS.values());
    }

    /** Кто видит варп. */
    public static boolean visible(EntityPlayerMP p, Warp w) {
        if (Perms.isAdmin(p) || w.open) {
            return true;
        }
        String me = p.getUniqueID().toString();
        if (me.equals(w.owner)) {
            return true;
        }
        try {
            String ka = com.ecoserv.subworld.WorldCrews.keyOf(p.getUniqueID());
            String kb = com.ecoserv.subworld.WorldCrews.keyOf(UUID.fromString(w.owner));
            return ka != null && ka.equals(kb);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static List<Warp> visibleTo(EntityPlayerMP p) {
        List<Warp> out = new ArrayList<Warp>();
        for (Warp w : WARPS.values()) {
            if (visible(p, w)) {
                out.add(w);
            }
        }
        out.sort((a, b) -> {
            if (a.server() != b.server()) {
                return a.server() ? -1 : 1;
            }
            return a.title.compareToIgnoreCase(b.title);
        });
        return out;
    }

    public static boolean canEdit(EntityPlayerMP p, Warp w) {
        return Perms.isAdmin(p) || p.getUniqueID().toString().equals(w.owner);
    }

    public static int countOf(UUID owner) {
        int n = 0;
        for (Warp w : WARPS.values()) {
            if (owner.toString().equals(w.owner)) {
                n++;
            }
        }
        return n;
    }

    /** Поставить варп здесь. serverWarp — варп сервера (только админ). Возвращает ошибку или null. */
    @Nullable
    public static String create(EntityPlayerMP p, String name, boolean serverWarp) {
        if (!ExtraConfig.warpsEnabled && !Perms.isAdmin(p)) {
            return "&cВарпы выключены на сервере.";
        }
        String k = key(name);
        if (!NAME.matcher(k).matches()) {
            return "&cНазвание: буквы, цифры, _ и -, до 32 символов.";
        }
        if (WARPS.containsKey(k)) {
            return "&cВарп «" + k + "» уже есть.";
        }
        boolean admin = Perms.isAdmin(p);
        if (serverWarp && !admin) {
            return "&cВарпы сервера ставят только админы.";
        }
        if (!serverWarp && !admin) {
            if (ExtraConfig.warpsPerPlayer <= 0) {
                return "&cСвои варпы на сервере выключены.";
            }
            if (countOf(p.getUniqueID()) >= ExtraConfig.warpsPerPlayer) {
                return "&cУ тебя уже " + ExtraConfig.warpsPerPlayer + " варп(а) — удали лишний.";
            }
            if (ExtraConfig.warpSetPrice > 0 && !EconomyManager.take(p.getUniqueID(), ExtraConfig.warpSetPrice)) {
                return "&cНе хватает денег: варп стоит &6" + Fmt.money(ExtraConfig.warpSetPrice);
            }
        }
        Warp w = new Warp();
        w.name = k;
        w.title = name.trim();
        w.dim = p.dimension;
        w.x = p.posX;
        w.y = p.posY;
        w.z = p.posZ;
        w.yaw = p.rotationYaw;
        w.pitch = p.rotationPitch;
        w.owner = serverWarp ? "" : p.getUniqueID().toString();
        w.ownerName = serverWarp ? "" : p.getName();
        w.open = serverWarp;
        w.created = System.currentTimeMillis();
        w.normalize();
        WARPS.put(k, w);
        save();
        return null;
    }

    public static boolean remove(String name) {
        Warp w = WARPS.remove(key(name));
        if (w != null) {
            save();
            return true;
        }
        return false;
    }

    // --- телепорт -----------------------------------------------------------------------------------

    /** Почему нельзя (null — можно). */
    @Nullable
    public static String check(EntityPlayerMP p, Warp w) {
        if (Perms.isAdmin(p)) {
            return null;
        }
        if (!ExtraConfig.warpsEnabled) {
            return "&cВарпы выключены на сервере.";
        }
        if (!visible(p, w)) {
            return "&cЭто закрытый варп игрока " + w.ownerName + ".";
        }
        if (!w.role.isEmpty() && !RoleManager.hasAny(p, java.util.Collections.singletonList(w.role))) {
            return "&cНужна роль: &f" + w.role;
        }
        if (!WorldNames.exists(w.dim)) {
            return "&cМир варпа сейчас недоступен.";
        }
        Long last = LAST.get(p.getUniqueID());
        long left = last == null ? 0 : last + ExtraConfig.warpCooldownSeconds * 1000L - System.currentTimeMillis();
        if (left > 0) {
            return "&eПодожди ещё " + (left / 1000 + 1) + " с.";
        }
        return null;
    }

    public static void go(final EntityPlayerMP p, final Warp w) {
        String no = check(p, w);
        if (no != null) {
            ChatUtil.actionBar(p, no);
            return;
        }
        boolean free = Perms.isAdmin(p) || p.getUniqueID().toString().equals(w.owner);
        if (!free && w.price > 0) {
            if (!EconomyManager.take(p.getUniqueID(), w.price)) {
                ChatUtil.actionBar(p, "&cПереход стоит &6" + Fmt.money(w.price));
                return;
            }
            if (!w.server()) {
                try {
                    EconomyManager.give(UUID.fromString(w.owner), w.price);
                } catch (IllegalArgumentException ignored) {
                    // владелец потерян — деньги уходят серверу
                }
            }
        }
        int delay = Perms.isAdmin(p) ? 0 : ExtraConfig.warpDelaySeconds;
        if (delay <= 0) {
            jump(p, w);
            return;
        }
        WAITING.put(p.getUniqueID(), new double[]{p.posX, p.posY, p.posZ});
        ChatUtil.actionBar(p, "&bТелепорт на «" + w.title + "» через " + delay + " с — не двигайся");
        ModSounds.play(p, ModSounds.PORTAL_HUM, 0.6F, 1.4F);
        final UUID id = p.getUniqueID();
        ServerTasks.later(delay * 20, () -> {
            double[] at = WAITING.remove(id);
            EntityPlayerMP now = PlayerUtil.online(id);
            if (at == null || now == null) {
                return;
            }
            double dx = now.posX - at[0];
            double dz = now.posZ - at[2];
            if (dx * dx + dz * dz > 0.6D * 0.6D) {
                ChatUtil.actionBar(now, "&cТелепорт отменён — ты сдвинулся.");
                if (!free && w.price > 0) {
                    EconomyManager.give(id, w.price);
                }
                return;
            }
            jump(now, w);
        });
    }

    private static void jump(EntityPlayerMP p, Warp w) {
        LAST.put(p.getUniqueID(), System.currentTimeMillis());
        WorldTeleport.rememberPosition(p);
        com.ecoserv.region.RegionManager.suppress(p, 1500L);
        if (WorldTeleport.teleport(p, new WorldTeleport.Target(w.dim, w.x, w.y, w.z, w.yaw, w.pitch))) {
            w.visits++;
            save();
            ModSounds.play(p, ModSounds.PORTAL_RELEASE, 0.8F, 1.2F);
            ChatUtil.actionBar(p, "&aВарп: &f" + w.title);
        } else {
            ChatUtil.send(p, "&cНе удалось перенестись на варп «" + w.title + "».");
        }
    }

    public static void forget(UUID player) {
        WAITING.remove(player);
    }
}
