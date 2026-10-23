package com.ecoserv.claim;

import com.ecoserv.economy.EconomyManager;
import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Приваты территории по чанкам (1.9.0). Чанк принадлежит игроку; его команда (команда мода) и доверенные
 * игроки могут строить там же. Хранится в world/ecoserv/claims.json.
 */
public final class ClaimManager {

    public static final class Claim {
        public int dim;
        public int cx;
        public int cz;
        public String owner = "";
        public String ownerName = "";
        public long created;
        public long paid;
    }

    /** Настройки владельца: кому доверяет и что разрешено всем. */
    public static final class Owner {
        public String owner = "";
        public List<String> trusted = new ArrayList<String>();
        /** Всем можно открывать двери, калитки, люки, нажимать кнопки и рычаги. */
        public boolean publicDoors = false;
        /** Всем можно открывать сундуки и другие хранилища. */
        public boolean publicContainers = false;
        /** PvP на территории. */
        public boolean pvp = true;
        /** Мобы-гриферы (криперы, эндермены) ломают блоки. */
        public boolean mobGriefing = false;

        void normalize() {
            if (trusted == null) {
                trusted = new ArrayList<String>();
            }
        }
    }

    private static final class Data {
        List<Claim> claims = new ArrayList<Claim>();
        List<Owner> owners = new ArrayList<Owner>();
    }

    private static final Type DATA = new TypeToken<Data>() {
    }.getType();
    private static final Map<String, Claim> CLAIMS = new HashMap<String, Claim>();
    private static final Map<String, Owner> OWNERS = new HashMap<String, Owner>();
    private static boolean dirty;

    private ClaimManager() {
    }

    public static String key(int dim, int cx, int cz) {
        return dim + ":" + cx + ":" + cz;
    }

    public static void load() {
        CLAIMS.clear();
        OWNERS.clear();
        Data d = JsonUtil.readType(DataStore.file("claims.json"), DATA, new Data());
        if (d != null) {
            if (d.claims != null) {
                for (Claim c : d.claims) {
                    if (c != null && c.owner != null && !c.owner.isEmpty()) {
                        CLAIMS.put(key(c.dim, c.cx, c.cz), c);
                    }
                }
            }
            if (d.owners != null) {
                for (Owner o : d.owners) {
                    if (o != null && o.owner != null) {
                        o.normalize();
                        OWNERS.put(o.owner, o);
                    }
                }
            }
        }
        dirty = false;
    }

    public static void save() {
        Data d = new Data();
        d.claims.addAll(CLAIMS.values());
        d.owners.addAll(OWNERS.values());
        if (JsonUtil.write(DataStore.file("claims.json"), d)) {
            dirty = false;
        }
    }

    public static void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    @Nullable
    public static Claim at(int dim, BlockPos pos) {
        if (CLAIMS.isEmpty()) {
            return null;
        }
        return CLAIMS.get(key(dim, pos.getX() >> 4, pos.getZ() >> 4));
    }

    @Nullable
    /** 2.0: приваты вокруг игрока (для рамок в режиме разработчика). */
    public static final class ClaimView {
        public final String owner;
        public final int x;
        public final int z;

        ClaimView(String owner, int x, int z) {
            this.owner = owner;
            this.x = x;
            this.z = z;
        }
    }

    public static List<ClaimView> near(EntityPlayerMP p, int radius) {
        List<ClaimView> out = new java.util.ArrayList<ClaimView>();
        int pcx = (int) Math.floor(p.posX) >> 4;
        int pcz = (int) Math.floor(p.posZ) >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Claim c = at(p.dimension, pcx + dx, pcz + dz);
                if (c != null) {
                    out.add(new ClaimView(c.ownerName == null || c.ownerName.isEmpty() ? "приват" : c.ownerName, pcx + dx, pcz + dz));
                }
            }
        }
        return out;
    }

    public static Claim at(int dim, int cx, int cz) {
        return CLAIMS.get(key(dim, cx, cz));
    }

    public static Owner owner(String uuid) {
        Owner o = OWNERS.get(uuid);
        if (o == null) {
            o = new Owner();
            o.owner = uuid;
            OWNERS.put(uuid, o);
        }
        return o;
    }

    public static void changed() {
        dirty = true;
    }

    public static List<Claim> of(String uuid) {
        List<Claim> out = new ArrayList<Claim>();
        for (Claim c : CLAIMS.values()) {
            if (uuid.equals(c.owner)) {
                out.add(c);
            }
        }
        return out;
    }

    public static int limit(EntityPlayerMP p) {
        return Perms.isAdmin(p) ? Integer.MAX_VALUE : ExtraConfig.claimsMaxChunks;
    }

    /** Может ли игрок строить и пользоваться всем на территории владельца. */
    public static boolean trusted(@Nullable EntityPlayer p, Claim c) {
        if (p == null) {
            return false;
        }
        if (p instanceof EntityPlayerMP && Perms.isAdmin(p)) {
            return true;
        }
        String me = p.getUniqueID().toString();
        if (me.equals(c.owner)) {
            return true;
        }
        Owner o = OWNERS.get(c.owner);
        if (o != null && o.trusted.contains(me)) {
            return true;
        }
        try {
            String ka = com.ecoserv.subworld.WorldCrews.keyOf(p.getUniqueID());
            String kb = com.ecoserv.subworld.WorldCrews.keyOf(UUID.fromString(c.owner));
            return ka != null && ka.equals(kb);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // --- занять / освободить -------------------------------------------------------------------------

    /** Занять чанк. Возвращает ошибку или null. */
    @Nullable
    public static String claim(EntityPlayerMP p, int dim, int cx, int cz) {
        boolean admin = Perms.isAdmin(p);
        if (!ExtraConfig.claimsEnabled && !admin) {
            return "&cПриваты выключены на сервере.";
        }
        if (ExtraConfig.claimBlocked(dim) && !admin) {
            return "&cВ этом мире приватить нельзя.";
        }
        if (com.ecoserv.subworld.SubworldRegistry.isOurs(dim) && !admin) {
            return "&cВ подмирах территорию не приватят — там свои правила.";
        }
        Claim c = at(dim, cx, cz);
        String me = p.getUniqueID().toString();
        if (c != null) {
            return me.equals(c.owner) ? "&eЭтот чанк уже твой." : "&cЧанк уже занят: &f" + c.ownerName;
        }
        int have = of(me).size();
        if (have >= limit(p)) {
            return "&cУ тебя уже " + have + " чанков — это предел (" + ExtraConfig.claimsMaxChunks + ").";
        }
        long price = admin ? 0L : ExtraConfig.claimChunkPrice;
        if (price > 0 && !EconomyManager.take(p.getUniqueID(), price)) {
            return "&cЧанк стоит &6" + Fmt.money(price) + "&c — не хватает денег.";
        }
        c = new Claim();
        c.dim = dim;
        c.cx = cx;
        c.cz = cz;
        c.owner = me;
        c.ownerName = p.getName();
        c.created = System.currentTimeMillis();
        c.paid = price;
        CLAIMS.put(key(dim, cx, cz), c);
        owner(me);
        save();
        return null;
    }

    @Nullable
    public static String unclaim(EntityPlayerMP p, int dim, int cx, int cz) {
        Claim c = at(dim, cx, cz);
        if (c == null) {
            return "&eЧанк и так свободен.";
        }
        if (!p.getUniqueID().toString().equals(c.owner) && !Perms.isAdmin(p)) {
            return "&cЭто не твой чанк.";
        }
        CLAIMS.remove(key(dim, cx, cz));
        if (c.paid > 0) {
            try {
                EconomyManager.give(UUID.fromString(c.owner), c.paid / 2);
            } catch (IllegalArgumentException ignored) {
                // владелец потерян
            }
        }
        save();
        return null;
    }

    public static int unclaimAll(String owner) {
        int n = 0;
        for (Claim c : new ArrayList<Claim>(CLAIMS.values())) {
            if (owner.equals(c.owner)) {
                CLAIMS.remove(key(c.dim, c.cx, c.cz));
                n++;
            }
        }
        if (n > 0) {
            save();
        }
        return n;
    }

    public static String describeTrusted(String owner) {
        Owner o = OWNERS.get(owner);
        if (o == null || o.trusted.isEmpty()) {
            return "никто";
        }
        Set<String> names = new HashSet<String>();
        for (String t : o.trusted) {
            try {
                names.add(PlayerUtil.nameOf(UUID.fromString(t)));
            } catch (IllegalArgumentException ignored) {
                // мусор
            }
        }
        return String.join(", ", names);
    }
}
