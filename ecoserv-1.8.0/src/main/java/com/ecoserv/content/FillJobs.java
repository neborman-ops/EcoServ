package com.ecoserv.content;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.util.Selection;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.BlockEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Работа палочки заполнения (п. 20): заполняет пустоту в выделении блоком из второй руки,
 * забирая из инвентаря по 2 блока за каждый поставленный. Ставит понемногу за тик,
 * каждую установку проверяют моды привата и зоны (событие установки блока).
 */
public final class FillJobs {

    private static final class Job {
        final UUID player;
        final int dim;
        final BlockPos min;
        final BlockPos max;
        final ItemStack sample;
        int x;
        int y;
        int z;
        int placed;
        int skipped;

        Job(UUID player, int dim, BlockPos min, BlockPos max, ItemStack sample) {
            this.player = player;
            this.dim = dim;
            this.min = min;
            this.max = max;
            this.sample = sample;
            this.x = min.getX();
            this.y = min.getY();
            this.z = min.getZ();
        }
    }

    private static final Map<UUID, Job> JOBS = new HashMap<UUID, Job>();

    private FillJobs() {
    }

    public static boolean running(UUID player) {
        return JOBS.containsKey(player);
    }

    public static void cancel(UUID player) {
        JOBS.remove(player);
    }

    /** Начать заполнение выделения. */
    public static void start(EntityPlayerMP player, Selection.Sel sel) {
        ItemStack off = player.getHeldItemOffhand();
        if (off.isEmpty() || !(off.getItem() instanceof ItemBlock)) {
            ChatUtil.send(player, "&cВозьми во вторую руку блок, которым заполнять.");
            return;
        }
        long volume = sel.volume();
        if (volume > EcoConfig.fillMaxVolume) {
            ChatUtil.send(player, "&cСлишком большая область: " + volume + " блоков (можно до " + EcoConfig.fillMaxVolume + ").");
            return;
        }
        ItemStack sample = off.copy();
        sample.setCount(1);
        if (!player.capabilities.isCreativeMode) {
            int have = countMatching(player, sample);
            int free = countFree(player.world, sel);
            if (have < 2) {
                ChatUtil.send(player, "&cНужно хотя бы 2 блока: палочка тратит по 2 за каждый поставленный.");
                return;
            }
            if (have < free * 2) {
                ChatUtil.send(player, "&eБлоков хватит примерно на " + (have / 2) + " из " + free + " — заполню сколько получится.");
            }
        }
        JOBS.put(player.getUniqueID(), new Job(player.getUniqueID(), player.dimension, sel.min(), sel.max(), sample));
        ChatUtil.send(player, "&aЗаполняю: &f" + sel.describe() + " &7(Shift+ПКМ ещё раз — остановить)");
    }

    private static int countFree(World world, Selection.Sel sel) {
        int free = 0;
        BlockPos a = sel.min();
        BlockPos b = sel.max();
        for (BlockPos p : BlockPos.getAllInBoxMutable(a, b)) {
            if (world.isBlockLoaded(p) && world.getBlockState(p).getBlock().isReplaceable(world, p)) {
                free++;
            }
        }
        return free;
    }

    private static boolean matches(ItemStack stack, ItemStack sample) {
        return !stack.isEmpty() && stack.getItem() == sample.getItem() && stack.getMetadata() == sample.getMetadata()
                && ItemStack.areItemStackTagsEqual(stack, sample);
    }

    private static int countMatching(EntityPlayerMP player, ItemStack sample) {
        int n = 0;
        for (ItemStack s : player.inventory.mainInventory) {
            if (matches(s, sample)) {
                n += s.getCount();
            }
        }
        for (ItemStack s : player.inventory.offHandInventory) {
            if (matches(s, sample)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Забрать count блоков из инвентаря (сначала — не из второй руки). */
    private static boolean take(EntityPlayerMP player, ItemStack sample, int count) {
        if (countMatching(player, sample) < count) {
            return false;
        }
        int left = count;
        // сначала из основного инвентаря, вторая рука — в последнюю очередь
        for (ItemStack s : player.inventory.mainInventory) {
            if (left > 0 && matches(s, sample)) {
                int n = Math.min(left, s.getCount());
                s.shrink(n);
                left -= n;
            }
        }
        for (ItemStack s : player.inventory.offHandInventory) {
            if (left > 0 && matches(s, sample)) {
                int n = Math.min(left, s.getCount());
                s.shrink(n);
                left -= n;
            }
        }
        return true;
    }

    public static void tick() {
        if (JOBS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Job>> it = JOBS.entrySet().iterator();
        while (it.hasNext()) {
            Job job = it.next().getValue();
            EntityPlayerMP player = PlayerUtil.online(job.player);
            if (player == null || player.dimension != job.dim || !player.isEntityAlive()) {
                it.remove();
                continue;
            }
            if (step(player, job)) {
                it.remove();
                ChatUtil.send(player, "&aЗаполнено: &f" + job.placed + " блоков"
                        + (job.skipped > 0 ? " &7(пропущено: " + job.skipped + ")" : "")
                        + (player.capabilities.isCreativeMode ? "" : " &7потрачено: &f" + job.placed * 2));
            }
        }
    }

    /** Несколько блоков за тик. true — работа закончена. */
    private static boolean step(EntityPlayerMP player, Job job) {
        World world = player.world;
        Block block = ((ItemBlock) job.sample.getItem()).getBlock();
        int budget = Math.max(1, EcoConfig.fillBlocksPerTick);
        while (budget-- > 0) {
            if (job.y > job.max.getY()) {
                return true;
            }
            BlockPos pos = new BlockPos(job.x, job.y, job.z);
            advance(job);
            if (!world.isBlockLoaded(pos) || !world.getBlockState(pos).getBlock().isReplaceable(world, pos)) {
                continue;
            }
            boolean creative = player.capabilities.isCreativeMode;
            if (!creative && countMatching(player, job.sample) < 2) {
                ChatUtil.send(player, "&eБлоки закончились.");
                return true;
            }
            IBlockState state = block.getStateForPlacement(world, pos, EnumFacing.UP, 0.5F, 1.0F, 0.5F,
                    job.sample.getMetadata(), player, EnumHand.OFF_HAND);
            BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world, pos);
            if (!world.setBlockState(pos, state, 3)) {
                job.skipped++;
                continue;
            }
            BlockEvent.PlaceEvent event = ForgeEventFactory.onPlayerBlockPlace(player, snapshot, EnumFacing.UP, EnumHand.OFF_HAND);
            if (event.isCanceled()) {
                snapshot.restore(true, false);
                job.skipped++;
                continue;
            }
            if (!creative) {
                take(player, job.sample, 2);
            }
            job.placed++;
        }
        if (job.placed % 64 == 0) {
            ChatUtil.actionBar(player, "&7Заполнение: &f" + job.placed);
        }
        return false;
    }

    private static void advance(Job job) {
        job.x++;
        if (job.x > job.max.getX()) {
            job.x = job.min.getX();
            job.z++;
            if (job.z > job.max.getZ()) {
                job.z = job.min.getZ();
                job.y++;
            }
        }
    }
}
