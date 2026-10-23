package com.ecoserv.npc;

import com.ecoserv.EcoServ;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.PlayerUtil;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * NPC, голограммы и спутники (2.0). Это обычные сущности Minecraft без ИИ, помеченные тегами — клиенту ничего не
 * нужно. Мод следит, чтобы они стояли на месте, не получали урон и возвращались после перезапуска.
 * <ul>
 * <li>world/ecoserv/npcs.json, holograms.json, followers.json.</li>
 * </ul>
 */
public final class NpcManager {

    public static final Pattern ID = Pattern.compile("^[a-z0-9_\\-]{1,32}$");
    public static final String TAG = "ecoserv_npc";
    public static final String TAG_HOLO = "ecoserv_holo";
    public static final String TAG_FOLLOW = "ecoserv_follow";

    private static final Type NPCS = new TypeToken<ArrayList<Npc>>() {
    }.getType();
    private static final Type HOLOS = new TypeToken<ArrayList<Hologram>>() {
    }.getType();
    private static final Type FOLLOWERS = new TypeToken<LinkedHashMap<String, Follower>>() {
    }.getType();

    /** Спутник игрока. */
    public static class Follower {
        public String type = "minecraft:wolf";
        public String name = "";
        public boolean baby = true;
        /** Высота над землёй (для летающих). */
        public double hover;
    }

    private static final Map<String, Npc> NPC = new LinkedHashMap<String, Npc>();
    private static final Map<String, Hologram> HOLO = new LinkedHashMap<String, Hologram>();
    private static Map<String, Follower> followers = new LinkedHashMap<String, Follower>();
    /** id → сущность. */
    private static final Map<String, UUID> ENT = new HashMap<String, UUID>();
    /** id голограммы → сущности строк. */
    private static final Map<String, List<UUID>> HOLO_ENT = new HashMap<String, List<UUID>>();
    private static final Map<String, String> HOLO_TEXT = new HashMap<String, String>();
    private static final Map<UUID, UUID> FOLLOW_ENT = new HashMap<UUID, UUID>();
    /** Пути NPC (кэш точек) и пройденное расстояние. */
    private static final Map<String, List<double[]>> PATHS = new HashMap<String, List<double[]>>();
    private static final Map<String, Double> WALKED = new HashMap<String, Double>();
    private static int tick;

    private NpcManager() {
    }

    // --- данные ---------------------------------------------------------------------------------------

    public static void load() {
        NPC.clear();
        HOLO.clear();
        ENT.clear();
        HOLO_ENT.clear();
        HOLO_TEXT.clear();
        FOLLOW_ENT.clear();
        List<Npc> list = JsonUtil.readType(DataStore.file("npcs.json"), NPCS, null);
        if (list != null) {
            for (Npc n : list) {
                if (n != null && n.id != null && !n.id.isEmpty()) {
                    n.normalize();
                    NPC.put(n.id, n);
                }
            }
        }
        List<Hologram> holos = JsonUtil.readType(DataStore.file("holograms.json"), HOLOS, null);
        if (holos != null) {
            for (Hologram h : holos) {
                if (h != null && h.id != null && !h.id.isEmpty()) {
                    h.normalize();
                    HOLO.put(h.id, h);
                }
            }
        }
        Map<String, Follower> f = JsonUtil.readType(DataStore.file("followers.json"), FOLLOWERS, null);
        followers = f == null ? new LinkedHashMap<String, Follower>() : f;
    }

    public static void save() {
        JsonUtil.write(DataStore.file("npcs.json"), new ArrayList<Npc>(NPC.values()));
        JsonUtil.write(DataStore.file("holograms.json"), new ArrayList<Hologram>(HOLO.values()));
        JsonUtil.write(DataStore.file("followers.json"), followers);
    }

    public static List<Npc> all() {
        return new ArrayList<Npc>(NPC.values());
    }

    @Nullable
    public static Npc get(String id) {
        return id == null ? null : NPC.get(id);
    }

    public static String titleOf(String id) {
        Npc n = get(id);
        return n == null ? id : ChatUtil.strip(ChatUtil.color(n.name));
    }

    public static Npc create(EntityPlayerMP at, String id, String type) {
        id = id.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            throw new com.ecoserv.ui.UiError("id: латиница, цифры, _ и - (до 32).");
        }
        if (NPC.containsKey(id)) {
            throw new com.ecoserv.ui.UiError("NPC «" + id + "» уже есть.");
        }
        Npc n = new Npc();
        n.id = id;
        n.type = type;
        n.name = "&e" + id;
        moveTo(n, at);
        n.normalize();
        NPC.put(id, n);
        save();
        respawn(n);
        return n;
    }

    public static void moveTo(Npc n, EntityPlayerMP p) {
        n.dim = p.dimension;
        n.x = Math.floor(p.posX) + 0.5D;
        n.y = p.posY;
        n.z = Math.floor(p.posZ) + 0.5D;
        n.yaw = Math.round(p.rotationYaw / 45.0F) * 45.0F + 180.0F;
    }

    public static void delete(Npc n) {
        kill(ENT.remove(n.id), n.dim);
        NPC.remove(n.id);
        save();
    }

    /** Пересоздать сущность (после смены вида/снаряжения). */
    public static void respawn(Npc n) {
        kill(ENT.remove(n.id), n.dim);
        save();
        maintainNpc(n);
    }

    public static List<Hologram> holograms() {
        return new ArrayList<Hologram>(HOLO.values());
    }

    @Nullable
    public static Hologram hologram(String id) {
        return id == null ? null : HOLO.get(id);
    }

    public static Hologram createHologram(EntityPlayerMP at, String id) {
        id = id.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            throw new com.ecoserv.ui.UiError("id: латиница, цифры, _ и - (до 32).");
        }
        if (HOLO.containsKey(id)) {
            throw new com.ecoserv.ui.UiError("Голограмма «" + id + "» уже есть.");
        }
        Hologram h = new Hologram();
        h.id = id;
        h.dim = at.dimension;
        h.x = at.posX;
        h.y = at.posY + 1.5D;
        h.z = at.posZ;
        h.normalize();
        HOLO.put(id, h);
        save();
        respawnHologram(h);
        return h;
    }

    public static void deleteHologram(Hologram h) {
        killHolo(h);
        HOLO.remove(h.id);
        save();
    }

    public static void respawnHologram(Hologram h) {
        killHolo(h);
        save();
        maintainHolo(h);
    }

    private static void killHolo(Hologram h) {
        List<UUID> ids = HOLO_ENT.remove(h.id);
        HOLO_TEXT.remove(h.id);
        if (ids != null) {
            for (UUID u : ids) {
                kill(u, h.dim);
            }
        }
        // и все помеченные (после перезапуска)
        WorldServer w = DimensionManager.getWorld(h.dim);
        if (w != null) {
            for (Entity e : new ArrayList<Entity>(w.loadedEntityList)) {
                if (e.getTags().contains(TAG_HOLO + ":" + h.id)) {
                    e.setDead();
                }
            }
        }
    }

    // --- спутники ---------------------------------------------------------------------------------------

    public static void setFollower(EntityPlayerMP p, @Nullable Follower f) {
        removeFollowerEntity(p);
        if (f == null) {
            followers.remove(p.getUniqueID().toString());
        } else {
            followers.put(p.getUniqueID().toString(), f);
        }
        save();
    }

    @Nullable
    public static Follower followerOf(EntityPlayerMP p) {
        return followers.get(p.getUniqueID().toString());
    }

    public static void removeFollowerEntity(EntityPlayerMP p) {
        UUID e = FOLLOW_ENT.remove(p.getUniqueID());
        if (e != null) {
            for (WorldServer w : DimensionManager.getWorlds()) {
                Entity en = w.getEntityFromUuid(e);
                if (en != null) {
                    en.setDead();
                }
            }
        }
    }

    // --- сущности --------------------------------------------------------------------------------------

    private static void kill(@Nullable UUID id, int dim) {
        if (id == null) {
            return;
        }
        WorldServer w = DimensionManager.getWorld(dim);
        if (w != null) {
            Entity e = w.getEntityFromUuid(id);
            if (e != null) {
                e.setDead();
            }
        }
    }

    @Nullable
    public static Entity entityOf(Npc n) {
        UUID u = ENT.get(n.id);
        WorldServer w = DimensionManager.getWorld(n.dim);
        if (u == null || w == null) {
            return null;
        }
        Entity e = w.getEntityFromUuid(u);
        return e == null || e.isDead ? null : e;
    }

    /** Чей это NPC (по тегу сущности). */
    @Nullable
    public static Npc npcOf(Entity e) {
        for (String t : e.getTags()) {
            if (t.startsWith(TAG + ":")) {
                return NPC.get(t.substring(TAG.length() + 1));
            }
        }
        return null;
    }

    public static boolean isOurs(Entity e) {
        Set<String> tags = e.getTags();
        return tags.contains(TAG) || tags.contains(TAG_HOLO) || tags.contains(TAG_FOLLOW);
    }

    @Nullable
    private static Entity make(WorldServer w, String type) {
        try {
            return EntityList.createEntityByIDFromName(new ResourceLocation(type), w);
        } catch (Throwable t) {
            EcoServ.LOG.warn("NPC: не удалось создать «{}»: {}", type, t.toString());
            return null;
        }
    }

    private static void maintainNpc(Npc n) {
        WorldServer w = DimensionManager.getWorld(n.dim);
        if (w == null || !w.isBlockLoaded(new BlockPos(n.x, n.y, n.z))) {
            return;
        }
        Entity e = entityOf(n);
        if (e == null) {
            // сущность пережила перезапуск — подобрать по тегу
            for (Entity c : w.loadedEntityList) {
                if (!c.isDead && c.getTags().contains(TAG + ":" + n.id)) {
                    if (e == null) {
                        e = c;
                    } else {
                        c.setDead();
                    }
                }
            }
            if (e != null && !e.getClass().equals(classOf(w, n.type))) {
                e.setDead();
                e = null;
            }
            if (e == null) {
                e = make(w, n.type);
                if (e == null) {
                    return;
                }
                e.addTag(TAG);
                e.addTag(TAG + ":" + n.id);
                e.setLocationAndAngles(n.x, n.y, n.z, n.yaw, 0.0F);
                apply(n, e);
                w.spawnEntity(e);
            } else {
                apply(n, e);
            }
            ENT.put(n.id, e.getUniqueID());
        }
        // на месте? (идущих по пути не трогаем)
        if (!n.path.isEmpty() && PATHS.containsKey(n.id) && PATHS.get(n.id).size() > 1) {
            e.extinguish();
            return;
        }
        if (e.getDistanceSq(n.x, n.y, n.z) > 0.01D) {
            e.setPositionAndUpdate(n.x, n.y, n.z);
        }
        e.motionX = 0;
        e.motionY = 0;
        e.motionZ = 0;
        e.extinguish();
        if (n.baby && e instanceof EntityAgeable) {
            ((EntityAgeable) e).setGrowingAge(-24000);
        }
    }

    @Nullable
    private static Class<?> classOf(WorldServer w, String type) {
        Entity tmp = make(w, type);
        return tmp == null ? null : tmp.getClass();
    }

    /** Вид, имя, снаряжение. */
    private static void apply(Npc n, Entity e) {
        e.setCustomNameTag(ChatUtil.color(n.name));
        e.setAlwaysRenderNameTag(n.nameVisible && !n.name.trim().isEmpty());
        e.setSilent(true);
        e.setEntityInvulnerable(true);
        e.setGlowing(n.glowing);
        e.setRotationYawHead(n.yaw);
        if (e instanceof EntityLiving) {
            EntityLiving l = (EntityLiving) e;
            l.setNoAI(true);
            l.enablePersistence();
            l.renderYawOffset = n.yaw;
            for (EntityEquipmentSlot s : EntityEquipmentSlot.values()) {
                l.setDropChance(s, 0.0F);
            }
        }
        if (e instanceof EntityVillager) {
            ((EntityVillager) e).setProfession(Math.max(0, Math.min(5, n.profession)));
        }
        if (e instanceof EntityZombie) {
            ((EntityZombie) e).setChild(n.baby);
        }
        if (e instanceof EntityAgeable) {
            ((EntityAgeable) e).setGrowingAge(n.baby ? -24000 : 0);
        }
        if (e instanceof EntityArmorStand) {
            NBTTagCompound t = new NBTTagCompound();
            e.writeToNBT(t);
            t.setBoolean("ShowArms", true);
            t.setBoolean("NoBasePlate", true);
            t.setBoolean("Small", n.baby);
            t.setBoolean("Invulnerable", true);
            t.setInteger("DisabledSlots", 2039583);
            e.readFromNBT(t);
        }
        if (e instanceof EntityLivingBase) {
            EntityLivingBase l = (EntityLivingBase) e;
            for (EntityEquipmentSlot s : EntityEquipmentSlot.values()) {
                String id = n.equipment.get(s.getName());
                ItemStack st = ItemStack.EMPTY;
                if (id != null && !id.isEmpty()) {
                    ItemKey k = ItemKey.parse(id);
                    if (k.isValid()) {
                        st = k.toStack(1);
                    }
                }
                l.setItemStackToSlot(s, st);
            }
        }
    }

    private static void maintainHolo(Hologram h) {
        WorldServer w = DimensionManager.getWorld(h.dim);
        if (w == null || !w.isBlockLoaded(new BlockPos(h.x, h.y, h.z))) {
            return;
        }
        List<UUID> ids = HOLO_ENT.get(h.id);
        boolean ok = ids != null && ids.size() == h.lines.size();
        if (ok) {
            for (UUID u : ids) {
                Entity e = w.getEntityFromUuid(u);
                if (e == null || e.isDead) {
                    ok = false;
                    break;
                }
            }
        }
        if (!ok) {
            for (Entity c : new ArrayList<Entity>(w.loadedEntityList)) {
                if (c.getTags().contains(TAG_HOLO + ":" + h.id)) {
                    c.setDead();
                }
            }
            ids = new ArrayList<UUID>();
            int count = h.lines.size();
            for (int i = 0; i < count; i++) {
                EntityArmorStand s = new EntityArmorStand(w);
                double y = h.y + (count - 1 - i) * h.spacing;
                s.setLocationAndAngles(h.x, y, h.z, 0.0F, 0.0F);
                NBTTagCompound t = new NBTTagCompound();
                s.writeToNBT(t);
                t.setBoolean("Marker", true);
                t.setBoolean("Invisible", true);
                t.setBoolean("NoGravity", true);
                t.setBoolean("Invulnerable", true);
                s.readFromNBT(t);
                s.setLocationAndAngles(h.x, y, h.z, 0.0F, 0.0F);
                s.addTag(TAG_HOLO);
                s.addTag(TAG_HOLO + ":" + h.id);
                s.setSilent(true);
                w.spawnEntity(s);
                ids.add(s.getUniqueID());
            }
            HOLO_ENT.put(h.id, ids);
            HOLO_TEXT.remove(h.id);
        }
        // текст (подстановки — обновляются)
        StringBuilder all = new StringBuilder();
        List<String> texts = new ArrayList<String>();
        for (String l : h.lines) {
            String t = ChatUtil.color(holoText(l));
            texts.add(t);
            all.append(t).append('\n');
        }
        if (!all.toString().equals(HOLO_TEXT.get(h.id))) {
            HOLO_TEXT.put(h.id, all.toString());
            for (int i = 0; i < ids.size(); i++) {
                Entity e = w.getEntityFromUuid(ids.get(i));
                if (e != null) {
                    String t = texts.get(i);
                    e.setCustomNameTag(t.isEmpty() ? " " : t);
                    e.setAlwaysRenderNameTag(!ChatUtil.strip(t).trim().isEmpty());
                }
            }
        }
    }

    private static String holoText(String l) {
        String s = l.replace("%online%", Integer.toString(PlayerUtil.allOnline().size()));
        if (s.contains("%time%")) {
            s = s.replace("%time%", new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date()));
        }
        if (s.contains("%date%")) {
            s = s.replace("%date%", new java.text.SimpleDateFormat("dd.MM.yyyy").format(new java.util.Date()));
        }
        if (s.contains("%max%")) {
            s = s.replace("%max%", Integer.toString(PlayerUtil.server().getMaxPlayers()));
        }
        return s;
    }

    private static void maintainFollower(EntityPlayerMP p, Follower f) {
        WorldServer w = p.getServerWorld();
        UUID id = FOLLOW_ENT.get(p.getUniqueID());
        Entity e = id == null ? null : w.getEntityFromUuid(id);
        if (e == null || e.isDead) {
            if (id != null) {
                removeFollowerEntity(p);
            }
            e = make(w, f.type);
            if (e == null) {
                return;
            }
            e.addTag(TAG_FOLLOW);
            e.addTag(TAG_FOLLOW + ":" + p.getUniqueID());
            e.setLocationAndAngles(p.posX, p.posY, p.posZ, p.rotationYaw, 0.0F);
            e.setCustomNameTag(ChatUtil.color(f.name == null || f.name.isEmpty() ? "" : f.name));
            e.setAlwaysRenderNameTag(f.name != null && !f.name.isEmpty());
            e.setSilent(true);
            e.setEntityInvulnerable(true);
            e.setNoGravity(true);
            if (e instanceof EntityLiving) {
                ((EntityLiving) e).setNoAI(true);
            }
            if (e instanceof EntityAgeable) {
                ((EntityAgeable) e).setGrowingAge(f.baby ? -24000 : 0);
            }
            if (e instanceof EntityZombie) {
                ((EntityZombie) e).setChild(f.baby);
            }
            w.spawnEntity(e);
            FOLLOW_ENT.put(p.getUniqueID(), e.getUniqueID());
        }
        // точка за плечом
        double rad = Math.toRadians(p.rotationYaw);
        double tx = p.posX + Math.sin(rad) * 1.6D + Math.cos(rad) * 0.9D;
        double tz = p.posZ - Math.cos(rad) * 1.6D + Math.sin(rad) * 0.9D;
        double ty = p.posY + f.hover + (f.hover > 0 ? Math.sin(tick / 10.0D) * 0.15D : 0.0D);
        double dx = tx - e.posX;
        double dy = ty - e.posY;
        double dz = tz - e.posZ;
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 > 400.0D) {
            e.setPositionAndUpdate(tx, ty, tz);
        } else if (d2 > 0.02D) {
            e.setPosition(e.posX + dx * 0.25D, e.posY + dy * 0.25D, e.posZ + dz * 0.25D);
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(p.posZ - e.posZ, p.posX - e.posX)) - 90.0D);
        e.rotationYaw = yaw;
        e.setRotationYawHead(yaw);
        if (e instanceof EntityLivingBase) {
            ((EntityLivingBase) e).renderYawOffset = yaw;
        }
        e.extinguish();
    }

    /** Найти точки пути (маркеры «Точка пути» с именами путь_1, путь_2…). */
    private static void refreshPaths() {
        PATHS.clear();
        for (Npc n : NPC.values()) {
            if (n.path.isEmpty()) {
                continue;
            }
            WorldServer w = DimensionManager.getWorld(n.dim);
            if (w == null) {
                continue;
            }
            java.util.TreeMap<Integer, double[]> pts = new java.util.TreeMap<Integer, double[]>();
            for (net.minecraft.tileentity.TileEntity te : new ArrayList<net.minecraft.tileentity.TileEntity>(w.loadedTileEntityList)) {
                if (!(te instanceof com.ecoserv.dev.TileMarker) || (te.getBlockMetadata() & 15) != com.ecoserv.dev.BlockMarker.PATH) {
                    continue;
                }
                String name = ((com.ecoserv.dev.TileMarker) te).name;
                if (!name.startsWith(n.path + "_")) {
                    continue;
                }
                try {
                    int i = Integer.parseInt(name.substring(n.path.length() + 1));
                    pts.put(i, new double[]{te.getPos().getX() + 0.5D, te.getPos().getY(), te.getPos().getZ() + 0.5D});
                } catch (NumberFormatException ignored) {
                    // не номер
                }
            }
            if (pts.size() > 1) {
                PATHS.put(n.id, new ArrayList<double[]>(pts.values()));
            }
        }
    }

    /** Шаг ходьбы по пути. */
    private static void walk(Npc n) {
        List<double[]> pts = PATHS.get(n.id);
        Entity e = entityOf(n);
        if (pts == null || e == null) {
            return;
        }
        EntityPlayer near = e.world.getClosestPlayer(e.posX, e.posY, e.posZ, 3.0D, false);
        if (near != null) {
            return; // остановился поговорить
        }
        List<double[]> route = new ArrayList<double[]>(pts);
        if (n.loop) {
            route.add(pts.get(0));
        } else {
            for (int i = pts.size() - 2; i >= 0; i--) {
                route.add(pts.get(i));
            }
        }
        double total = 0;
        double[] seg = new double[route.size()];
        for (int i = 1; i < route.size(); i++) {
            double[] a = route.get(i - 1);
            double[] b = route.get(i);
            seg[i] = Math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]));
            total += seg[i];
        }
        if (total <= 0.01D) {
            return;
        }
        double d = (WALKED.containsKey(n.id) ? WALKED.get(n.id) : 0.0D) + Math.max(0.1D, Math.min(8.0D, n.speed)) / 20.0D;
        d %= total;
        WALKED.put(n.id, d);
        for (int i = 1; i < route.size(); i++) {
            if (d <= seg[i] || i == route.size() - 1) {
                double[] a = route.get(i - 1);
                double[] b = route.get(i);
                double t = seg[i] <= 0 ? 0 : Math.min(1.0D, d / seg[i]);
                double x = a[0] + (b[0] - a[0]) * t;
                double y = a[1] + (b[1] - a[1]) * t;
                double z = a[2] + (b[2] - a[2]) * t;
                float yaw = (float) (Math.toDegrees(Math.atan2(b[2] - a[2], b[0] - a[0])) - 90.0D);
                e.setPosition(x, y, z);
                e.rotationYaw = yaw;
                e.setRotationYawHead(yaw);
                if (e instanceof EntityLivingBase) {
                    ((EntityLivingBase) e).renderYawOffset = yaw;
                }
                return;
            }
            d -= seg[i];
        }
    }

    /** Повернуть голову NPC к ближайшему игроку. */
    private static void look(Npc n) {
        Entity e = entityOf(n);
        if (!(e instanceof EntityLiving)) {
            return;
        }
        EntityPlayer near = e.world.getClosestPlayer(e.posX, e.posY, e.posZ, 8.0D, false);
        EntityLiving l = (EntityLiving) e;
        if (near == null || !n.lookAtPlayers) {
            l.setRotationYawHead(n.yaw);
            l.rotationPitch = 0.0F;
            return;
        }
        double dx = near.posX - e.posX;
        double dz = near.posZ - e.posZ;
        double dy = near.posY + near.getEyeHeight() - (e.posY + e.getEyeHeight());
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        // голова не крутится на 180 — тело поворачиваем, если надо
        float diff = MathHelper.wrapDegrees(yaw - n.yaw);
        if (Math.abs(diff) > 75.0F) {
            l.renderYawOffset = yaw - Math.signum(diff) * 40.0F;
            l.rotationYaw = l.renderYawOffset;
        } else {
            l.renderYawOffset = n.yaw;
            l.rotationYaw = n.yaw;
        }
        l.setRotationYawHead(yaw);
        l.rotationPitch = pitch;
    }

    public static void tick() {
        tick++;
        if (tick % 40 == 0) {
            for (Npc n : NPC.values()) {
                try {
                    maintainNpc(n);
                } catch (Throwable t) {
                    EcoServ.LOG.warn("NPC {}: {}", n.id, t.toString());
                }
            }
            for (Hologram h : HOLO.values()) {
                try {
                    maintainHolo(h);
                } catch (Throwable t) {
                    EcoServ.LOG.warn("Голограмма {}: {}", h.id, t.toString());
                }
            }
        }
        if (tick % 200 == 100) {
            cleanup();
        }
        if (tick % 100 == 1) {
            refreshPaths();
        }
        for (Npc n : NPC.values()) {
            if (PATHS.containsKey(n.id)) {
                walk(n);
            } else if (tick % 2 == 0) {
                look(n);
            }
        }
        for (EntityPlayerMP p : PlayerUtil.allOnline()) {
            Follower f = followers.get(p.getUniqueID().toString());
            if (f != null) {
                try {
                    maintainFollower(p, f);
                } catch (Throwable t) {
                    EcoServ.LOG.warn("Спутник {}: {}", p.getName(), t.toString());
                }
            }
        }
    }

    /** Убрать «потерянные» сущности: удалённых NPC, голограмм, спутников вышедших игроков. */
    private static void cleanup() {
        Set<UUID> follow = new HashSet<UUID>(FOLLOW_ENT.values());
        for (WorldServer w : DimensionManager.getWorlds()) {
            for (Entity e : new ArrayList<Entity>(w.loadedEntityList)) {
                if (e.isDead) {
                    continue;
                }
                Set<String> tags = e.getTags();
                if (tags.contains(TAG)) {
                    Npc n = npcOf(e);
                    if (n == null || n.dim != w.provider.getDimension()
                            || (ENT.containsKey(n.id) && !ENT.get(n.id).equals(e.getUniqueID()))) {
                        e.setDead();
                    }
                } else if (tags.contains(TAG_HOLO)) {
                    boolean keep = false;
                    for (String t : tags) {
                        if (t.startsWith(TAG_HOLO + ":")) {
                            List<UUID> ids = HOLO_ENT.get(t.substring(TAG_HOLO.length() + 1));
                            keep = ids != null && ids.contains(e.getUniqueID());
                        }
                    }
                    if (!keep) {
                        e.setDead();
                    }
                } else if (tags.contains(TAG_FOLLOW) && !follow.contains(e.getUniqueID())) {
                    e.setDead();
                }
            }
        }
    }

    public static void onStop() {
        save();
        for (EntityPlayerMP p : PlayerUtil.allOnline()) {
            removeFollowerEntity(p);
        }
    }
}
