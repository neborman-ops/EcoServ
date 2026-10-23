package com.ecoserv.games;

import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldTeleport;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Игра на арене (трасса в мире арен): общие помощники стройки, точки старта, проверки падения. */
public abstract class ArenaGame extends Game {

    protected WorldServer world;
    protected SubworldDim dim;
    protected BlockPos origin;
    /** Границы постройки {x1, y1, z1, x2, y2, z2}. */
    protected int[] bounds;
    protected final Map<UUID, BlockPos> checkpoint = new HashMap<UUID, BlockPos>();
    protected final Map<UUID, Integer> falls = new HashMap<UUID, Integer>();

    protected ArenaGame(GameManager.Session session) {
        super(session);
    }

    @Override
    public final boolean prepare() {
        dim = GameArena.dim(true);
        world = dim == null ? null : GameArena.world(dim);
        if (world == null) {
            return false;
        }
        origin = GameArena.origin(session.slot);
        build();
        if (bounds != null) {
            GameManager.track(bounds);
        }
        return true;
    }

    /** Построить трассу от origin (и выставить bounds). */
    protected abstract void build();

    /** Где появляется игрок в начале. */
    protected abstract BlockPos start(UUID player, int index);

    @Override
    public void begin() {
        int i = 0;
        for (EntityPlayerMP p : online()) {
            BlockPos s = start(p.getUniqueID(), i++);
            checkpoint.put(p.getUniqueID(), s);
            if (!SubworldTeleport.teleportTo(p, dim, false,
                    new double[]{s.getX() + 0.5D, s.getY(), s.getZ() + 0.5D, 0.0D, 0.0D})) {
                ChatUtil.send(p, "&cНе удалось перенести на арену.");
                out(p.getUniqueID(), "не попал на арену");
                continue;
            }
            GameManager.enterMode(p);
        }
    }

    @Override
    public void cleanup() {
        if (bounds != null) {
            GameManager.untrack(bounds);
        }
    }

    // --- стройка -------------------------------------------------------------------------------------

    protected void set(int x, int y, int z, IBlockState st) {
        world.setBlockState(new BlockPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z), st, 2);
    }

    protected void setAbs(BlockPos p, IBlockState st) {
        world.setBlockState(p, st, 2);
    }

    protected void fill(int x1, int y1, int z1, int x2, int y2, int z2, IBlockState st) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, st);
                }
            }
        }
    }

    protected BlockPos rel(int x, int y, int z) {
        return new BlockPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
    }

    protected IBlockState air() {
        return Blocks.AIR.getDefaultState();
    }

    // --- игроки -------------------------------------------------------------------------------------

    /** Игрок на арене (в нужном мире). */
    @Nullable
    protected EntityPlayerMP here(UUID id) {
        EntityPlayerMP p = PlayerUtil.online(id);
        return p != null && p.dimension == dim.dim ? p : null;
    }

    /** Вернуть на точку сохранения. */
    protected void respawn(EntityPlayerMP p, String why) {
        BlockPos c = checkpoint.get(p.getUniqueID());
        if (c == null) {
            c = start(p.getUniqueID(), 0);
        }
        p.connection.setPlayerLocation(c.getX() + 0.5D, c.getY(), c.getZ() + 0.5D, 0.0F, 0.0F);
        p.motionX = 0;
        p.motionY = 0;
        p.motionZ = 0;
        p.fallDistance = 0;
        p.extinguish();
        falls.merge(p.getUniqueID(), 1, Integer::sum);
        ChatUtil.actionBar(p, "&c" + why + " &7— назад на точку");
        com.ecoserv.init.ModSounds.play(p, com.ecoserv.init.ModSounds.TRAP, 0.8F, 0.8F);
    }

    /** Еда и здоровье — чтобы ничего не отвлекало. */
    protected void keepAlive(EntityPlayerMP p) {
        if (ticks % 100 == 0) {
            p.addPotionEffect(new PotionEffect(MobEffects.SATURATION, 120, 0, true, false));
        }
        p.fallDistance = 0;
    }

    /** Блок под ногами. */
    protected IBlockState under(EntityPlayerMP p) {
        return world.getBlockState(new BlockPos(p.posX, p.posY - 0.2D, p.posZ));
    }

    protected int relZ(EntityPlayerMP p) {
        return (int) Math.floor(p.posZ) - origin.getZ();
    }

    protected int relY(EntityPlayerMP p) {
        return (int) Math.floor(p.posY) - origin.getY();
    }

    protected double relX(EntityPlayerMP p) {
        return p.posX - origin.getX() - 0.5D;
    }
}
