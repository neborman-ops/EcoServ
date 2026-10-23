package com.ecoserv.region;

import com.ecoserv.EcoServ;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketBlackHole;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldNames;
import com.ecoserv.world.WorldTeleport;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Особые порталы на сервере (1.8.0 — «Чёрная дыра», 1.9.0 — «Монолит» и настройки):
 * <ul>
 * <li>рассылка клиентам: где порталы, их итоговые настройки, вид места назначения (настоящие блоки, свет, биомы)
 * и существа там (двигаются в окне вживую);</li>
 * <li>мягкое притяжение у чёрной дыры;</li>
 * <li>переход «насквозь»: у дыры выходишь с той стороны, куда шёл; у монолита — в то же место картинки
 * с другой стороны, с тем же взглядом и скоростью (с поворотом, если монолиты стоят по-разному).</li>
 * </ul>
 */
public final class BlackHoles {

    /** Формат данных вида (меняется — старые клиенты просто не покажут вид). */
    public static final int PREVIEW_MAGIC = 0xEC0B0002;

    private static final class Cached {
        int version;
        long built;
        int radius;
        List<PacketBlackHole> parts;
        /** Точка «глаз» в месте назначения: {dim, x, y, z}. */
        double[] eye;
    }

    private static final class Sent {
        int dim = Integer.MIN_VALUE;
        int listVersion = -1;
        int fxVersion = -1;
        final Map<String, Integer> previews = new HashMap<String, Integer>();
    }

    private static final class Entering {
        EntityPlayerMP player;
        Region src;
        double[] motion;
        int turn;
    }

    private static final Map<String, Cached> PREVIEWS = new HashMap<String, Cached>();
    private static final Map<UUID, Sent> SENT = new HashMap<UUID, Sent>();
    private static final ThreadLocal<Entering> ENTERING = new ThreadLocal<Entering>();
    private static final Map<UUID, Entering> AFTER = new HashMap<UUID, Entering>();
    private static int tick;
    private static int previewVersion;

    private BlackHoles() {
    }

    // --- переход ----------------------------------------------------------------------------------------

    static void entering(EntityPlayerMP player, Region r) {
        if (r.isSpecial()) {
            Entering e = new Entering();
            e.player = player;
            e.src = r;
            e.motion = new double[]{player.motionX, player.motionY, player.motionZ};
            ENTERING.set(e);
            AFTER.put(player.getUniqueID(), e);
            if (HoleParams.flag(r, HoleParams.SOUNDS)) {
                com.ecoserv.init.ModSounds.play(player, com.ecoserv.init.ModSounds.PORTAL_WHOOSH, 1.0F,
                        r.isMonolith() ? 1.3F : 0.9F);
            }
        }
    }

    static void entered(EntityPlayerMP player, Region r) {
        ENTERING.remove();
        Entering e = AFTER.remove(player.getUniqueID());
        if (e == null || !r.isSpecial()) {
            return;
        }
        double[] m = rotate(e.motion, e.turn);
        // скорость сохраняется: летел — вылетаешь
        player.motionX = m[0];
        player.motionY = r.isMonolith() ? m[1] : Math.max(m[1], 0.0D);
        player.motionZ = m[2];
        player.connection.sendPacket(new SPacketEntityVelocity(player));
    }

    /** Вектор, повёрнутый вокруг Y на turn четвертей (как поворот взгляда yaw + 90°·turn). */
    static double[] rotate(double[] v, int turn) {
        double x = v[0];
        double z = v[2];
        for (int i = 0; i < ((turn % 4) + 4) % 4; i++) {
            double nx = -z;
            double nz = x;
            x = nx;
            z = nz;
        }
        return new double[]{x, v[1], z};
    }

    /** Поворот от монолита src к монолиту dest (четверти оборота). */
    public static int turnFor(Region src, @Nullable Region dest) {
        int set = (int) Math.round(HoleParams.get(src, HoleParams.TURN));
        if (set >= 1 && set <= 4) {
            return set - 1;
        }
        if (dest == null || !src.isMonolith() || !dest.isMonolith()) {
            return 0;
        }
        int a = src.planeAxis();
        int b = dest.planeAxis();
        if (a == 1 || b == 1 || a == b) {
            return 0;
        }
        // X -> Z: +X переходит в +Z
        return a == 0 ? 1 : 3;
    }

    private static double[] normal(int axis) {
        return axis == 0 ? new double[]{1, 0, 0} : axis == 1 ? new double[]{0, 1, 0} : new double[]{0, 0, 1};
    }

    /** Точка выхода у портала назначения для того, кто сейчас входит. null — не особый или никто не входит. */
    @Nullable
    static double[] exitFor(Region dest) {
        Entering e = ENTERING.get();
        if (e == null || !dest.isSpecial()) {
            return null;
        }
        EntityPlayerMP p = e.player;
        if (dest.isBlackHole()) {
            double yaw = Math.toRadians(p.rotationYaw);
            double dx = -Math.sin(yaw);
            double dz = Math.cos(yaw);
            double out = dest.holeRadius() + 3.5D;
            return new double[]{dest.centerX() + dx * out, dest.centerY() - 1.0D, dest.centerZ() + dz * out,
                    p.rotationYaw, p.rotationPitch};
        }
        // монолит
        int turn = e.src.isMonolith() ? turnFor(e.src, dest) : 0;
        e.turn = turn;
        double cx = dest.centerX();
        double cy = dest.centerY();
        double cz = dest.centerZ();
        double[] rel;
        if (e.src.isMonolith()) {
            rel = rotate(new double[]{p.posX - e.src.centerX(), p.posY - e.src.centerY(), p.posZ - e.src.centerZ()}, turn);
        } else {
            rel = new double[]{0.0D, dest.planeAxis() == 1 ? 0.0D : dest.minY - cy, 0.0D};
        }
        int axis = dest.planeAxis();
        double[] n = normal(axis);
        // с какой стороны выходить: куда двигался (или смотрел)
        double[] dir = e.motion;
        double[] srcN = e.src.isMonolith() ? normal(e.src.planeAxis()) : null;
        if (srcN == null || Math.abs(dir[0] * srcN[0] + dir[1] * srcN[1] + dir[2] * srcN[2]) < 0.01D) {
            Vec look = Vec.look(p.rotationYaw, p.rotationPitch);
            dir = new double[]{look.x, look.y, look.z};
        }
        double[] rd = rotate(dir, turn);
        double side = rd[0] * n[0] + rd[1] * n[1] + rd[2] * n[2] >= 0.0D ? 1.0D : -1.0D;
        // убрать составляющую вдоль нормали и не вылезти за картинку
        double[] half = dest.planeHalf();
        double x = rel[0];
        double y = rel[1];
        double z = rel[2];
        if (axis == 0) {
            x = 0.0D;
            z = clamp(z, -half[0] + 0.3D, half[0] - 0.3D);
            y = clamp(y, -half[1], half[1] - 1.8D);
        } else if (axis == 2) {
            z = 0.0D;
            x = clamp(x, -half[0] + 0.3D, half[0] - 0.3D);
            y = clamp(y, -half[1], half[1] - 1.8D);
        } else {
            y = 0.0D;
            x = clamp(x, -half[0] + 0.3D, half[0] - 0.3D);
            z = clamp(z, -half[1] + 0.3D, half[1] - 0.3D);
        }
        double px = cx + x;
        double py = cy + y;
        double pz = cz + z;
        if (axis == 1) {
            py = side < 0 ? cy - 0.5D - 1.85D : cy + 0.55D;
        } else {
            px += n[0] * side * 1.0D;
            pz += n[2] * side * 1.0D;
        }
        return new double[]{px, py, pz, p.rotationYaw + 90.0F * turn, p.rotationPitch};
    }

    /** Точку выхода особого портала не «спасаем» поиском безопасного места — выход должен быть точным. */
    static boolean exactExit(Region dest) {
        return ENTERING.get() != null && dest.isSpecial();
    }

    private static double clamp(double v, double lo, double hi) {
        return hi < lo ? (lo + hi) / 2.0D : Math.max(lo, Math.min(hi, v));
    }

    /** Обычная цель (точка, спавн) — но взгляд игрока сохраняется. */
    @Nullable
    static WorldTeleport.Target keepLook(EntityPlayerMP player, Region r, @Nullable WorldTeleport.Target t) {
        if (t == null || !r.isSpecial() || "portal".equals(r.targetMode)) {
            return t;
        }
        return new WorldTeleport.Target(t.dim, t.x, t.y, t.z, player.rotationYaw, player.rotationPitch);
    }

    private static final class Vec {
        final double x;
        final double y;
        final double z;

        Vec(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static Vec look(float yaw, float pitch) {
            double f = Math.cos(-yaw * 0.017453292D - Math.PI);
            double f1 = Math.sin(-yaw * 0.017453292D - Math.PI);
            double f2 = -Math.cos(-pitch * 0.017453292D);
            double f3 = Math.sin(-pitch * 0.017453292D);
            return new Vec(f1 * f2, f3, f * f2);
        }
    }

    // --- тик: притяжение и рассылка ------------------------------------------------------------------------

    public static void tick() {
        tick++;
        try {
            for (EntityPlayerMP p : PlayerUtil.allOnline()) {
                List<Region> holes = specialsFor(p.dimension);
                if (!holes.isEmpty() && !p.isSpectator() && !PortalGuard.isPaused(p)) {
                    pull(p, holes);
                }
                if (tick % 10 == 0) {
                    sync(p, holes);
                }
                if (tick % 4 == 0 && !holes.isEmpty()) {
                    entities(p, holes);
                }
            }
        } catch (Throwable t) {
            EcoServ.LOG.error("Особые порталы: ошибка", t);
        }
    }

    private static List<Region> specialsFor(int dim) {
        List<Region> out = new ArrayList<Region>();
        int rd = RegionManager.regionDim(dim);
        for (Region r : RegionManager.inDim(rd)) {
            if (r.enabled && r.isSpecial()) {
                out.add(r);
            }
        }
        return out;
    }

    private static void pull(EntityPlayerMP p, List<Region> holes) {
        for (Region r : holes) {
            if (!r.isBlackHole() || !HoleParams.flag(r, HoleParams.PULL)) {
                continue;
            }
            double power = HoleParams.get(r, HoleParams.PULL_POWER) / 100.0D;
            double R = r.holeRadius();
            double dx = r.centerX() - p.posX;
            double dy = r.centerY() - (p.posY + 0.9D);
            double dz = r.centerZ() - p.posZ;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double reach = R + 3.0D;
            if (d < 0.3D || d > reach || power <= 0.0D) {
                continue;
            }
            double s = 0.035D * power * (1.0D - d / reach);
            p.motionX += dx / d * s;
            p.motionY += dy / d * s * 0.6D;
            p.motionZ += dz / d * s;
            p.velocityChanged = true;
        }
    }

    private static void sync(EntityPlayerMP p, List<Region> holes) {
        Sent sent = SENT.get(p.getUniqueID());
        if (sent == null) {
            sent = new Sent();
            SENT.put(p.getUniqueID(), sent);
        }
        if (sent.dim != p.dimension || sent.listVersion != RegionManager.version || sent.fxVersion != HoleParams.version) {
            sent.dim = p.dimension;
            sent.listVersion = RegionManager.version;
            sent.fxVersion = HoleParams.version;
            sent.previews.clear();
            PacketBlackHole list = new PacketBlackHole();
            list.op = PacketBlackHole.LIST;
            list.dim = p.dimension;
            for (Region r : holes) {
                list.holes.add(describe(r));
            }
            PacketHandler.sendTo(list, p);
        }
        for (Region r : holes) {
            double dx = r.centerX() - p.posX;
            double dz = r.centerZ() - p.posZ;
            if (dx * dx + dz * dz > 96.0D * 96.0D || HoleParams.get(r, HoleParams.INSIDE) != 0.0D) {
                continue;
            }
            Cached c = preview(r);
            if (c == null) {
                continue;
            }
            Integer v = sent.previews.get(r.name);
            if (v == null || v != c.version) {
                sent.previews.put(r.name, c.version);
                for (PacketBlackHole part : c.parts) {
                    PacketHandler.sendTo(part, p);
                }
            }
        }
    }

    private static PacketBlackHole.Hole describe(Region r) {
        PacketBlackHole.Hole h = new PacketBlackHole.Hole();
        h.name = r.name;
        h.kind = r.isMonolith() ? 1 : 0;
        h.x = r.centerX();
        h.y = r.centerY();
        h.z = r.centerZ();
        h.radius = (float) r.holeRadius();
        if (r.isMonolith()) {
            h.axis = Math.max(0, r.planeAxis());
            double[] half = r.planeHalf();
            h.halfU = (float) half[0];
            h.halfV = (float) half[1];
            h.turn = turnFor(r, "portal".equals(r.targetMode) ? RegionManager.get(r.targetPortal) : null);
        }
        h.hasView = destination(r) != null;
        h.params = HoleParams.resolve(r);
        return h;
    }

    public static void forget(UUID player) {
        SENT.remove(player);
        AFTER.remove(player);
    }

    /** Сбросить снятые виды (например, после правки настроек портала). */
    public static void invalidate(@Nullable String name) {
        if (name == null) {
            PREVIEWS.clear();
        } else {
            PREVIEWS.remove(name);
        }
    }

    // --- место назначения ------------------------------------------------------------------------------

    /** Точка «глаз» места назначения: {dim, x, y, z}. null — не определить. */
    @Nullable
    static double[] destination(Region r) {
        if ("portal".equals(r.targetMode)) {
            Region dest = RegionManager.get(r.targetPortal);
            if (dest == null || dest == r) {
                return null;
            }
            if (dest.isSpecial()) {
                return new double[]{dest.dim, dest.centerX(), dest.centerY(), dest.centerZ()};
            }
            double[] e = RegionManager.exitPoint(dest);
            return new double[]{dest.dim, e[0], e[1] + 1.6D, e[2]};
        }
        Integer dim = WorldNames.resolve(r.target);
        if (dim == null) {
            Subworld sw = SubworldRegistry.get(r.target);
            if (sw == null) {
                return null;
            }
            SubworldDim d = SubworldRegistry.find(sw.id, sw.perOwner() ? SubworldDim.TEMPLATE : SubworldDim.SHARED);
            if (d == null) {
                return null;
            }
            dim = d.dim;
        }
        if ("point".equals(r.targetMode)) {
            return new double[]{dim, r.tx, r.ty + 1.6D, r.tz};
        }
        WorldServer w = DimensionManager.getWorld(dim);
        if (w == null) {
            return null;
        }
        BlockPos spawn = w.getTopSolidOrLiquidBlock(w.getSpawnPoint());
        return new double[]{dim, spawn.getX() + 0.5D, spawn.getY() + 1.6D, spawn.getZ() + 0.5D};
    }

    // --- вид: блоки, свет, биомы ---------------------------------------------------------------------------

    @Nullable
    private static Cached preview(Region r) {
        Cached c = PREVIEWS.get(r.name);
        long now = System.currentTimeMillis();
        int radius = (int) HoleParams.get(r, HoleParams.VIEW_RADIUS);
        long refresh = (long) HoleParams.get(r, HoleParams.VIEW_REFRESH) * 1000L;
        if (c != null && c.radius == radius && now - c.built < refresh) {
            return c;
        }
        double[] dest = destination(r);
        if (dest == null) {
            return c;
        }
        WorldServer w = DimensionManager.getWorld((int) dest[0]);
        if (w == null) {
            // мир не загружен — грузить ради картинки не будем
            return c;
        }
        byte[] payload;
        try {
            payload = sample(w, dest, radius);
        } catch (IOException e) {
            return c;
        }
        int version = ++previewVersion;
        List<PacketBlackHole> parts = new ArrayList<PacketBlackHole>();
        int total = Math.max(1, (payload.length + PacketBlackHole.PART - 1) / PacketBlackHole.PART);
        for (int i = 0; i < total; i++) {
            PacketBlackHole p = new PacketBlackHole();
            p.op = PacketBlackHole.PREVIEW;
            p.name = r.name;
            p.version = version;
            p.part = i;
            p.parts = total;
            int from = i * PacketBlackHole.PART;
            p.data = Arrays.copyOfRange(payload, from, Math.min(payload.length, from + PacketBlackHole.PART));
            parts.add(p);
        }
        if (c == null) {
            c = new Cached();
            PREVIEWS.put(r.name, c);
        }
        c.version = version;
        c.built = now;
        c.radius = radius;
        c.parts = parts;
        c.eye = dest;
        return c;
    }

    /**
     * Снимок места назначения: куб (2R+1)^3 вокруг «глаза». Палитра id состояний блоков (0 — воздух,
     * 1 — скрытый непрозрачный блок, со всех сторон закрытый такими же), блоки, свет (небо << 4 | блоки)
     * и биомы столбцов. Незагруженные чанки не грузим — там пусто.
     */
    private static byte[] sample(WorldServer w, double[] dest, int s) throws IOException {
        int size = s * 2 + 1;
        double eyeX = dest[1];
        double eyeY = dest[2];
        double eyeZ = dest[3];
        int bx = (int) Math.floor(eyeX) - s;
        int by = (int) Math.floor(eyeY) - s;
        int bz = (int) Math.floor(eyeZ) - s;
        int n = size * size * size;
        IBlockState[] states = new IBlockState[n];
        boolean[] opaque = new boolean[n];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean[] loaded = new boolean[size * size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                m.setPos(bx + x, 64, bz + z);
                loaded[x * size + z] = w.isBlockLoaded(m);
            }
        }
        IBlockState air = Blocks.AIR.getDefaultState();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                int wy = by + y;
                for (int z = 0; z < size; z++) {
                    int i = (x * size + y) * size + z;
                    IBlockState st = air;
                    if (wy >= 0 && wy < 256 && loaded[x * size + z]) {
                        m.setPos(bx + x, wy, bz + z);
                        st = w.getBlockState(m);
                    }
                    states[i] = st;
                    opaque[i] = st.isOpaqueCube() && st.isFullCube();
                }
            }
        }
        Map<Integer, Integer> palette = new HashMap<Integer, Integer>();
        List<Integer> ids = new ArrayList<Integer>();
        ids.add(0);
        ids.add(Block.getStateId(Blocks.STONE.getDefaultState()));
        short[] blocks = new short[n];
        byte[] light = new byte[n];
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    int i = (x * size + y) * size + z;
                    IBlockState st = states[i];
                    if (st.getMaterial() == net.minecraft.block.material.Material.AIR) {
                        blocks[i] = 0;
                    } else if (opaque[i] && closed(opaque, size, x, y, z)) {
                        blocks[i] = 1;
                    } else {
                        int id = Block.getStateId(st);
                        Integer idx = palette.get(id);
                        if (idx == null) {
                            idx = ids.size();
                            ids.add(id);
                            palette.put(id, idx);
                        }
                        blocks[i] = (short) (int) idx;
                    }
                    if (!opaque[i] && loaded[x * size + z]) {
                        m.setPos(bx + x, by + y, bz + z);
                        int sky = by + y >= 256 ? 15 : by + y < 0 ? 0 : w.getLightFor(EnumSkyBlock.SKY, m);
                        int blk = by + y < 0 || by + y >= 256 ? 0 : w.getLightFor(EnumSkyBlock.BLOCK, m);
                        light[i] = (byte) ((sky & 15) << 4 | (blk & 15));
                    } else if (!loaded[x * size + z]) {
                        light[i] = (byte) 0xF0;
                    }
                }
            }
        }
        byte[] biomes = new byte[size * size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (loaded[x * size + z]) {
                    m.setPos(bx + x, 64, bz + z);
                    biomes[x * size + z] = (byte) Biome.getIdForBiome(w.getBiome(m));
                } else {
                    biomes[x * size + z] = 1;
                }
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 * 1024);
        Deflater def = new Deflater(Deflater.BEST_SPEED);
        DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes, def, 64 * 1024));
        out.writeInt(PREVIEW_MAGIC);
        out.writeInt(size);
        out.writeFloat((float) (bx - eyeX));
        out.writeFloat((float) (by - eyeY));
        out.writeFloat((float) (bz - eyeZ));
        int dim = (int) dest[0];
        out.writeInt(dim == -1 ? 0x2A0606 : dim == 1 ? 0x0D0A14 : w.isDaytime() ? 0x78A7FF : 0x0A0E22);
        out.writeBoolean(w.provider.hasSkyLight());
        out.writeInt(ids.size());
        for (int id : ids) {
            out.writeInt(id);
        }
        for (short b : blocks) {
            out.writeShort(b);
        }
        out.write(light);
        out.write(biomes);
        out.close();
        def.end();
        return bytes.toByteArray();
    }

    /** Непрозрачный блок, закрытый со всех сторон (за краем куба — тоже закрыт). */
    private static boolean closed(boolean[] opaque, int size, int x, int y, int z) {
        return op(opaque, size, x + 1, y, z) && op(opaque, size, x - 1, y, z) && op(opaque, size, x, y + 1, z)
                && op(opaque, size, x, y - 1, z) && op(opaque, size, x, y, z + 1) && op(opaque, size, x, y, z - 1);
    }

    private static boolean op(boolean[] opaque, int size, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= size || y >= size || z >= size) {
            return true;
        }
        return opaque[(x * size + y) * size + z];
    }

    // --- существа в месте назначения ---------------------------------------------------------------------

    private static void entities(EntityPlayerMP p, List<Region> holes) {
        for (Region r : holes) {
            if (!HoleParams.flag(r, HoleParams.ENTITIES) || HoleParams.get(r, HoleParams.INSIDE) != 0.0D) {
                continue;
            }
            double dx = r.centerX() - p.posX;
            double dy = r.centerY() - p.posY;
            double dz = r.centerZ() - p.posZ;
            if (dx * dx + dy * dy + dz * dz > 64.0D * 64.0D) {
                continue;
            }
            Cached c = PREVIEWS.get(r.name);
            if (c == null || c.eye == null) {
                continue;
            }
            WorldServer w = DimensionManager.getWorld((int) c.eye[0]);
            if (w == null) {
                continue;
            }
            double R = c.radius;
            AxisAlignedBB box = new AxisAlignedBB(c.eye[1] - R, c.eye[2] - R, c.eye[3] - R,
                    c.eye[1] + R, c.eye[2] + R, c.eye[3] + R);
            PacketBlackHole pkt = new PacketBlackHole();
            pkt.op = PacketBlackHole.ENTITIES;
            pkt.name = r.name;
            for (Entity e : w.getEntitiesWithinAABB(Entity.class, box)) {
                if (pkt.ents.size() >= 48) {
                    break;
                }
                if (e == p || e.isInvisible() || (e instanceof EntityPlayer && ((EntityPlayer) e).isSpectator())) {
                    continue;
                }
                PacketBlackHole.Ent out = new PacketBlackHole.Ent();
                out.id = e.getEntityId();
                if (e instanceof EntityPlayer) {
                    out.kind = 0;
                    out.type = "player";
                    out.uuid = e.getUniqueID().toString();
                    out.name = e.getName();
                } else if (e instanceof EntityItem) {
                    out.kind = 2;
                    out.type = "item";
                    out.gear = new ItemStack[]{((EntityItem) e).getItem().copy()};
                } else {
                    ResourceLocation key = EntityList.getKey(e);
                    if (key == null) {
                        continue;
                    }
                    out.kind = 1;
                    out.type = key.toString();
                }
                out.x = (float) (e.posX - c.eye[1]);
                out.y = (float) (e.posY - c.eye[2]);
                out.z = (float) (e.posZ - c.eye[3]);
                out.yaw = e.rotationYaw;
                out.pitch = e.rotationPitch;
                out.headYaw = e.getRotationYawHead();
                int flags = 0;
                if (e.isSneaking()) {
                    flags |= 1;
                }
                if (e.isBurning()) {
                    flags |= 2;
                }
                if (e instanceof EntityAgeable && ((EntityAgeable) e).isChild()) {
                    flags |= 4;
                }
                out.flags = flags;
                if (e instanceof EntityLivingBase && out.kind != 2) {
                    EntityLivingBase l = (EntityLivingBase) e;
                    EntityEquipmentSlot[] slots = EntityEquipmentSlot.values();
                    out.gear = new ItemStack[slots.length];
                    for (int i = 0; i < slots.length; i++) {
                        out.gear[i] = l.getItemStackFromSlot(slots[i]).copy();
                    }
                }
                pkt.ents.add(out);
            }
            PacketHandler.sendTo(pkt, p);
        }
    }
}
