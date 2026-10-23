package com.ecoserv.client.v2;

import com.ecoserv.client.ClientSettings;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GoodLight (2.0): динамический свет на клиенте. Факел или другой светящийся предмет в руке, выброшенные светящиеся
 * предметы и горящие существа подсвечивают блоки вокруг. Мод временно поднимает яркость блоков в клиентском мире и
 * возвращает как было, когда источник уходит. Сервер и другие игроки ничего не замечают.
 */
@SideOnly(Side.CLIENT)
public class DynamicLight {

    private static final int MAX_SOURCES = 8;
    /** Блок → значение, которое было до подсветки. */
    private static final Map<BlockPos, Integer> ORIGINAL = new HashMap<BlockPos, Integer>();
    private static String lastKey = "";
    private static World lastWorld;
    private static int tick;

    public static void reset() {
        ORIGINAL.clear();
        lastKey = "";
        lastWorld = null;
    }

    /** Яркость предмета: блоки — их свет, остальное — по списку. */
    public static int lightOf(ItemStack s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        Item it = s.getItem();
        if (it instanceof ItemBlock) {
            Block b = ((ItemBlock) it).getBlock();
            try {
                return b.getDefaultState().getLightValue();
            } catch (Throwable t) {
                return 0;
            }
        }
        if (it == Items.LAVA_BUCKET) {
            return 15;
        }
        if (it == Items.BLAZE_ROD || it == Items.NETHER_STAR) {
            return 12;
        }
        if (it == Items.GLOWSTONE_DUST || it == Items.BLAZE_POWDER || it == Items.MAGMA_CREAM) {
            return 9;
        }
        if (it == Items.SPECTRAL_ARROW || it == Items.PRISMARINE_CRYSTALS || it == Items.FIRE_CHARGE) {
            return 8;
        }
        return 0;
    }

    private static int lightOf(Entity e) {
        int l = 0;
        if (e.isBurning()) {
            l = 14;
        }
        if (e instanceof EntityLivingBase) {
            EntityLivingBase le = (EntityLivingBase) e;
            l = Math.max(l, Math.max(lightOf(le.getHeldItemMainhand()), lightOf(le.getHeldItemOffhand())));
        } else if (e instanceof EntityItem) {
            l = Math.max(l, lightOf(((EntityItem) e).getItem()) - 1);
        }
        return l;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.world;
        if (w != lastWorld) {
            ORIGINAL.clear();
            lastKey = "";
            lastWorld = w;
        }
        if (w == null || mc.player == null) {
            return;
        }
        if (++tick % 2 != 0) {
            return;
        }
        ClientSettings s = ClientSettings.get();
        List<int[]> sources = new ArrayList<int[]>();
        if (s.dynamicLight) {
            add(sources, mc.player, lightOf(mc.player));
            if (s.dynamicLightOthers) {
                for (Entity e : w.loadedEntityList) {
                    if (e == mc.player || sources.size() >= MAX_SOURCES * 3) {
                        continue;
                    }
                    if (e.getDistanceSq(mc.player) > 48 * 48) {
                        continue;
                    }
                    if (e instanceof EntityPlayer || e instanceof EntityItem || e.isBurning()
                            || e instanceof EntityLivingBase) {
                        int l = lightOf(e);
                        if (l > 0) {
                            add(sources, e, l);
                        }
                    }
                }
            }
        }
        sources.sort((a, b) -> Integer.compare(dist(a, mc.player), dist(b, mc.player)));
        while (sources.size() > MAX_SOURCES) {
            sources.remove(sources.size() - 1);
        }
        StringBuilder key = new StringBuilder();
        for (int[] src : sources) {
            key.append(src[0]).append(',').append(src[1]).append(',').append(src[2]).append(':').append(src[3]).append(';');
        }
        key.append(s.dynamicLightRadius);
        if (key.toString().equals(lastKey)) {
            return;
        }
        lastKey = key.toString();
        // вернуть как было
        for (Map.Entry<BlockPos, Integer> e : ORIGINAL.entrySet()) {
            if (w.isBlockLoaded(e.getKey())) {
                w.setLightFor(EnumSkyBlock.BLOCK, e.getKey(), e.getValue());
            }
        }
        ORIGINAL.clear();
        // подсветить
        Map<BlockPos, Integer> target = new HashMap<BlockPos, Integer>();
        int maxR = Math.max(3, Math.min(10, s.dynamicLightRadius));
        for (int[] src : sources) {
            int level = src[3];
            int r = Math.min(maxR, level - 1);
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        int d = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        int v = level - d;
                        if (d > r || v <= 0) {
                            continue;
                        }
                        BlockPos p = new BlockPos(src[0] + dx, src[1] + dy, src[2] + dz);
                        Integer have = target.get(p);
                        if (have == null || have < v) {
                            target.put(p, v);
                        }
                    }
                }
            }
        }
        for (Map.Entry<BlockPos, Integer> e : target.entrySet()) {
            BlockPos p = e.getKey();
            if (!w.isBlockLoaded(p) || w.getBlockState(p).isOpaqueCube()) {
                continue;
            }
            int was = w.getLightFor(EnumSkyBlock.BLOCK, p);
            if (e.getValue() > was) {
                ORIGINAL.put(p, was);
                w.setLightFor(EnumSkyBlock.BLOCK, p, e.getValue());
            }
        }
    }

    private static void add(List<int[]> list, Entity e, int level) {
        if (level <= 0) {
            return;
        }
        list.add(new int[]{(int) Math.floor(e.posX), (int) Math.floor(e.posY + e.getEyeHeight() * 0.8D), (int) Math.floor(e.posZ),
                level});
    }

    private static int dist(int[] s, Entity to) {
        double dx = s[0] - to.posX;
        double dy = s[1] - to.posY;
        double dz = s[2] - to.posZ;
        return (int) (dx * dx + dy * dy + dz * dz);
    }
}
