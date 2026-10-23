package com.ecoserv.electric;

import com.ecoserv.EcoServ;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Электросеть (2.0), как в Rust: источники отдают столько, сколько просят потребители по проводам; каждый блок
 * сам берёт немного; батарея копит лишнее. Считается 5 раз в секунду для загруженных блоков.
 */
public final class ElectricGrid {

    private static final int STEP = 4;
    private static int tick;

    private ElectricGrid() {
    }

    public static void tick() {
        if (++tick % STEP != 0 || TileElectric.LOADED.isEmpty()) {
            return;
        }
        Map<World, List<TileElectric>> byWorld = new HashMap<World, List<TileElectric>>();
        for (TileElectric t : new ArrayList<TileElectric>(TileElectric.LOADED)) {
            if (t.isInvalid() || t.getWorld() == null || t.getWorld().isRemote) {
                TileElectric.LOADED.remove(t);
                continue;
            }
            byWorld.computeIfAbsent(t.getWorld(), k -> new ArrayList<TileElectric>()).add(t);
        }
        for (Map.Entry<World, List<TileElectric>> e : byWorld.entrySet()) {
            try {
                run((WorldServer) e.getKey(), e.getValue());
            } catch (Throwable t) {
                EcoServ.LOG.warn("Электросеть: {}", t.toString());
            }
        }
    }

    /** Пропускает ли блок ток дальше сейчас. */
    private static boolean passes(WorldServer w, TileElectric t) {
        switch (t.type()) {
            case ElectricTypes.SWITCH:
                return t.on;
            case ElectricTypes.TIMER:
                return (w.getTotalWorldTime() / (Math.max(1, t.interval) * 20L)) % 2L == 0L;
            case ElectricTypes.SENSOR: {
                BlockPos p = t.getPos();
                EntityPlayer pl = w.getClosestPlayer(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D, t.range, false);
                return pl != null && !pl.isSpectator();
            }
            case ElectricTypes.RS_IN:
                return w.isBlockPowered(t.getPos());
            case ElectricTypes.AND:
                return t.inputs > 0 && t.poweredInputs == t.inputs;
            case ElectricTypes.OR:
                return t.poweredInputs > 0;
            case ElectricTypes.XOR:
                return t.poweredInputs == 1;
            default:
                return true;
        }
    }

    private static int maxSupply(WorldServer w, TileElectric t) {
        switch (t.type()) {
            case ElectricTypes.SOLAR: {
                if (!w.canSeeSky(t.getPos().up())) {
                    return 0;
                }
                long time = w.getWorldTime() % 24000L;
                if (time > 12500L && time < 23500L) {
                    return 0;
                }
                return w.isRaining() ? 8 : 20;
            }
            case ElectricTypes.GENERATOR:
                return t.fuel > 0 ? 50 : 0;
            case ElectricTypes.BATTERY:
                return (int) Math.min(t.rate, t.energy / STEP);
            default:
                return 0;
        }
    }

    private static void run(WorldServer w, List<TileElectric> list) {
        Map<BlockPos, TileElectric> at = new HashMap<BlockPos, TileElectric>();
        for (TileElectric t : list) {
            at.put(t.getPos(), t);
        }
        Map<TileElectric, Boolean> pass = new HashMap<TileElectric, Boolean>();
        Map<TileElectric, Integer> indeg = new HashMap<TileElectric, Integer>();
        for (TileElectric t : list) {
            pass.put(t, passes(w, t));
            indeg.put(t, 0);
        }
        for (TileElectric t : list) {
            for (BlockPos o : t.outputs) {
                TileElectric to = at.get(o);
                if (to != null && to != t) {
                    indeg.merge(to, 1, Integer::sum);
                }
            }
        }
        // спрос (с защитой от петель)
        Map<TileElectric, Integer> demand = new HashMap<TileElectric, Integer>();
        Set<TileElectric> visiting = new HashSet<TileElectric>();
        for (TileElectric t : list) {
            demand(t, at, pass, demand, visiting);
        }
        // подача по порядку
        Map<TileElectric, Integer> recv = new HashMap<TileElectric, Integer>();
        Map<TileElectric, Integer> poweredIn = new HashMap<TileElectric, Integer>();
        Deque<TileElectric> queue = new ArrayDeque<TileElectric>();
        for (TileElectric t : list) {
            if (indeg.get(t) == 0) {
                queue.add(t);
            }
        }
        Set<TileElectric> done = new HashSet<TileElectric>();
        while (!queue.isEmpty()) {
            TileElectric t = queue.poll();
            if (!done.add(t)) {
                continue;
            }
            int type = t.type();
            int in = recv.containsKey(t) ? recv.get(t) : 0;
            int use = ElectricTypes.USE[type];
            int avail;
            boolean powered;
            if (ElectricTypes.source(type)) {
                if (type == ElectricTypes.BATTERY && in > 0) {
                    t.energy = Math.min(t.capacity, t.energy + (long) in * STEP);
                }
                int outDemand = 0;
                for (BlockPos o : t.outputs) {
                    TileElectric to = at.get(o);
                    if (to != null && to != t) {
                        outDemand += demand.get(to);
                    }
                }
                avail = Math.min(maxSupply(w, t), outDemand);
                powered = avail > 0;
            } else {
                powered = in >= use && (use > 0 || in > 0);
                avail = powered && pass.get(t) ? in - use : 0;
            }
            int given = 0;
            if (avail > 0) {
                int sum = 0;
                List<TileElectric> targets = new ArrayList<TileElectric>();
                for (BlockPos o : t.outputs) {
                    TileElectric to = at.get(o);
                    if (to != null && to != t) {
                        targets.add(to);
                        sum += demand.get(to);
                    }
                }
                for (TileElectric to : targets) {
                    int d = demand.get(to);
                    int g = sum <= 0 ? avail / Math.max(1, targets.size()) : (int) ((long) avail * d / sum);
                    g = Math.min(g, sum <= 0 ? g : d);
                    if (g > 0) {
                        recv.merge(to, g, Integer::sum);
                        poweredIn.merge(to, 1, Integer::sum);
                        given += g;
                    }
                }
            }
            for (BlockPos o : t.outputs) {
                TileElectric to = at.get(o);
                if (to != null && to != t) {
                    int left = indeg.merge(to, -1, Integer::sum);
                    if (left <= 0) {
                        queue.add(to);
                    }
                }
            }
            // расход источников
            if (type == ElectricTypes.GENERATOR && given > 0) {
                t.fuel = Math.max(0, t.fuel - STEP);
            }
            if (type == ElectricTypes.BATTERY && given > 0) {
                t.energy = Math.max(0, t.energy - (long) given * STEP);
            }
            apply(w, t, in, given, demand.get(t), powered);
        }
        // петли — без тока
        for (TileElectric t : list) {
            if (!done.contains(t)) {
                apply(w, t, 0, 0, 0, false);
            }
        }
        for (TileElectric t : list) {
            int c = 0;
            for (TileElectric s : list) {
                if (s != t && s.outputs.contains(t.getPos())) {
                    c++;
                }
            }
            t.inputs = c;
            Integer p = poweredIn.get(t);
            t.poweredInputs = p == null ? 0 : p;
        }
    }

    private static int demand(TileElectric t, Map<BlockPos, TileElectric> at, Map<TileElectric, Boolean> pass,
                              Map<TileElectric, Integer> memo, Set<TileElectric> visiting) {
        Integer m = memo.get(t);
        if (m != null) {
            return m;
        }
        if (!visiting.add(t)) {
            return 0;
        }
        int type = t.type();
        int d;
        if (type == ElectricTypes.BATTERY) {
            d = (int) Math.max(0, Math.min(t.rate, (t.capacity - t.energy) / STEP));
        } else if (ElectricTypes.source(type)) {
            d = 0;
        } else {
            d = ElectricTypes.USE[type];
            if (pass.get(t)) {
                for (BlockPos o : t.outputs) {
                    TileElectric to = at.get(o);
                    if (to != null && to != t) {
                        d += demand(to, at, pass, memo, visiting);
                    }
                }
            }
        }
        visiting.remove(t);
        memo.put(t, d);
        return d;
    }

    private static void apply(WorldServer w, TileElectric t, int in, int out, int demand, boolean powered) {
        int type = t.type();
        boolean lit = ElectricTypes.source(type) ? out > 0 || (type == ElectricTypes.BATTERY && in > 0) : powered;
        boolean changed = t.in != in || t.out != out;
        t.in = in;
        t.out = out;
        t.demand = demand;
        boolean was = t.lit;
        t.setLit(lit);
        if (changed && was == lit && (type == ElectricTypes.METER || type == ElectricTypes.BATTERY || w.getTotalWorldTime() % 20 < STEP)) {
            t.changed();
        }
        if (type == ElectricTypes.DOOR && was != lit) {
            doors(w, t.getPos(), Math.max(1, Math.min(6, t.range)), lit);
        }
        if (type == ElectricTypes.ALARM && lit && w.getTotalWorldTime() % 20 < STEP) {
            BlockPos p = t.getPos();
            w.playSound(null, p, SoundEvents.BLOCK_NOTE_PLING, SoundCategory.BLOCKS, 1.5F, (w.getTotalWorldTime() / 20) % 2 == 0 ? 1.4F : 1.0F);
            w.spawnParticle(EnumParticleTypes.REDSTONE, p.getX() + 0.5D, p.getY() + 1.1D, p.getZ() + 0.5D, 6, 0.3D, 0.1D, 0.3D, 0.0D);
        }
    }

    private static void doors(World w, BlockPos c, int r, boolean open) {
        for (BlockPos p : BlockPos.getAllInBox(c.add(-r, -r, -r), c.add(r, r, r))) {
            IBlockState st = w.getBlockState(p);
            Block b = st.getBlock();
            if (b instanceof BlockDoor && st.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER) {
                ((BlockDoor) b).toggleDoor(w, p, open);
            }
        }
    }
}
