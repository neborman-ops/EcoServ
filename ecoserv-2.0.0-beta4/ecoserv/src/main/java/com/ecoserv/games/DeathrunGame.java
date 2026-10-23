package com.ecoserv.games;

import com.ecoserv.init.ModSounds;
import com.ecoserv.util.ChatUtil;
import net.minecraft.block.BlockColored;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * «Дезран» (1.9.0): каждый раз новая трасса из случайных участков — прыжки через пропасти, осыпающийся пол,
 * полосы магмы, которые включаются волной (нужно ловить момент), прессы, узкая балка с порывами ветра, столбы.
 * Между участками — точки сохранения. По желанию один игрок — «ловушечник»: летает над трассой и запускает
 * ловушки там, где сейчас лидер.
 */
public class DeathrunGame extends ArenaGame {

    public static final class Kind extends GameKind {
        public Kind() {
            super("deathrun", "&cДезран", "Каждый раз новая трасса с ловушками: прыжки, осыпающийся пол, магма волной, "
                    + "прессы, ветер. Кто первый до финиша.", "minecraft:magma", true);
            param("difficulty", "Сложность", "1 — прогулка, 5 — ад", 3, 1, 5);
            param("segments", "Участков", "Длина трассы", 8, 3, 24);
            param("minutes", "Время, минут", "Потом места — по пройденному", 8, 2, 60);
            flag("trapper", "Ловушечник", "Один игрок (при 2+) запускает ловушки с пульта", false);
        }

        @Override
        public Game create(GameManager.Session session) {
            return new DeathrunGame(session);
        }

        @Override
        public GamePreset defaults() {
            GamePreset p = super.defaults();
            p.maxPlayers = 12;
            return p;
        }
    }

    // --- механизмы трассы ------------------------------------------------------------------------------

    private enum Type {
        GAPS, COLLAPSE, PULSE, BEAM, PILLARS, CRUSHER, STAIRS
    }

    private static final class Segment {
        Type type;
        int z1;
        int z2;
        /** Осыпающийся пол: блоки и их таймеры (тик, когда исчезнет / вернётся). */
        final List<BlockPos> tiles = new ArrayList<BlockPos>();
        final Map<BlockPos, Integer> falling = new HashMap<BlockPos, Integer>();
        final Map<BlockPos, Integer> back = new HashMap<BlockPos, Integer>();
        /** Полосы магмы / прессы: группы блоков, период, сдвиг фазы. */
        final List<List<BlockPos>> groups = new ArrayList<List<BlockPos>>();
        final List<Integer> phase = new ArrayList<Integer>();
        int period = 60;
        /** До какого тика все ловушки участка включены принудительно (пульт ловушечника). */
        int forcedUntil;
        boolean[] on = new boolean[0];
    }

    private static final IBlockState FLOOR = Blocks.STONEBRICK.getDefaultState();
    private static final IBlockState SAFE = Blocks.STONE.getDefaultState();
    private static final IBlockState MAGMA = Blocks.MAGMA.getDefaultState();
    private static final IBlockState CRUSH = Blocks.OBSIDIAN.getDefaultState();
    private static final IBlockState CHECK = Blocks.EMERALD_BLOCK.getDefaultState();
    private static final IBlockState FINISH = Blocks.GOLD_BLOCK.getDefaultState();

    private final List<Segment> segments = new ArrayList<Segment>();
    private final Random rnd = new Random();
    private int difficulty;
    private int finishZ;
    private int limitTicks;
    private UUID trapper;
    private int trapCooldown;
    private final Map<UUID, Integer> progress = new HashMap<UUID, Integer>();

    public DeathrunGame(GameManager.Session session) {
        super(session);
        difficulty = param("difficulty", 3);
        limitTicks = param("minutes", 8) * 60 * 20;
    }

    private static IBlockState wool(EnumDyeColor c) {
        return Blocks.CONCRETE.getDefaultState().withProperty(BlockColored.COLOR, c);
    }

    @Override
    protected void build() {
        // старт
        fill(-3, 0, -6, 3, 0, 6, FLOOR);
        fill(-3, 1, -6, 3, 3, -6, Blocks.GLASS.getDefaultState());
        int z = 7;
        int n = param("segments", 8);
        Type[] all = Type.values();
        Type last = null;
        for (int i = 0; i < n; i++) {
            Type t;
            do {
                t = all[rnd.nextInt(all.length)];
            } while (t == last && all.length > 1);
            last = t;
            Segment s = new Segment();
            s.type = t;
            s.z1 = z;
            z = buildSegment(s, z);
            s.z2 = z - 1;
            s.on = new boolean[s.groups.size()];
            segments.add(s);
            // точка сохранения
            fill(-3, 0, z, 3, 0, z + 2, CHECK);
            z += 3;
        }
        // финиш
        fill(-3, 0, z, 3, 0, z + 6, FINISH);
        set(0, 1, z + 3, Blocks.BEACON.getDefaultState());
        fill(-1, -1, z + 2, 1, -1, z + 4, Blocks.IRON_BLOCK.getDefaultState());
        finishZ = z;
        // ловушечник — платформа над стартом
        fill(-2, 12, -2, 2, 12, 2, Blocks.GLASS.getDefaultState());
        bounds = new int[]{origin.getX() - 10, origin.getY() - 3, origin.getZ() - 8,
                origin.getX() + 10, origin.getY() + 14, origin.getZ() + z + 10};
    }

    private int buildSegment(Segment s, int z) {
        int d = difficulty;
        switch (s.type) {
            case GAPS: {
                int end = z + 14;
                int x = 0;
                while (z < end) {
                    int len = 2 + rnd.nextInt(2);
                    fill(x - 1, 0, z, x + 1, 0, z + len - 1, FLOOR);
                    z += len;
                    int gap = 1 + rnd.nextInt(Math.min(3, 1 + d / 2));
                    z += gap;
                    if (d >= 3 && rnd.nextInt(3) == 0) {
                        x = Math.max(-2, Math.min(2, x + (rnd.nextBoolean() ? 2 : -2)));
                    }
                }
                fill(-1, 0, z, 1, 0, z, FLOOR);
                return z + 1;
            }
            case COLLAPSE: {
                int len = 8 + d;
                for (int dz = 0; dz < len; dz++) {
                    for (int dx = -3; dx <= 3; dx++) {
                        set(dx, 0, z + dz, wool(EnumDyeColor.ORANGE));
                        s.tiles.add(rel(dx, 0, z + dz));
                    }
                }
                s.period = 14 - d * 2;
                return z + len;
            }
            case PULSE: {
                int stripes = 5 + d;
                s.period = Math.max(24, 70 - d * 9);
                for (int i = 0; i < stripes; i++) {
                    List<BlockPos> g = new ArrayList<BlockPos>();
                    for (int dz = 0; dz < 2; dz++) {
                        for (int dx = -3; dx <= 3; dx++) {
                            set(dx, 0, z + i * 2 + dz, SAFE);
                            g.add(rel(dx, 0, z + i * 2 + dz));
                        }
                    }
                    s.groups.add(g);
                    s.phase.add(i * s.period / (stripes + 1));
                }
                return z + stripes * 2;
            }
            case BEAM: {
                int len = 10 + d * 2;
                fill(0, 0, z, 0, 0, z + len - 1, Blocks.QUARTZ_BLOCK.getDefaultState());
                s.period = Math.max(30, 80 - d * 10);
                return z + len;
            }
            case PILLARS: {
                int count = 5 + d;
                int x = 0;
                int h = 0;
                for (int i = 0; i < count; i++) {
                    // соседние столбы: сдвиг вбок не больше 1, перепад высоты не больше 1 — всегда допрыгнуть
                    x = Math.max(-2, Math.min(2, x + rnd.nextInt(3) - 1));
                    h = Math.max(0, Math.min(1, h + rnd.nextInt(3) - 1));
                    fill(x, -2, z, x, h, z, Blocks.PRISMARINE.getDefaultState());
                    z += 2 + (d >= 4 ? rnd.nextInt(2) : 0);
                }
                fill(-1, 0, z, 1, 0, z, FLOOR);
                return z + 1;
            }
            case CRUSHER: {
                int rows = 2 + d / 2;
                fill(-3, 0, z, 3, 0, z + rows * 4, FLOOR);
                s.period = Math.max(30, 70 - d * 8);
                for (int r = 0; r < rows; r++) {
                    int cz = z + 2 + r * 4;
                    List<BlockPos> g = new ArrayList<BlockPos>();
                    for (int dx = -3; dx <= 3; dx++) {
                        for (int dz = 0; dz < 2; dz++) {
                            set(dx, 4, cz + dz, Blocks.CONCRETE.getDefaultState());
                            for (int y = 1; y <= 3; y++) {
                                g.add(rel(dx, y, cz + dz));
                            }
                        }
                    }
                    s.groups.add(g);
                    s.phase.add(r * s.period / rows);
                }
                return z + rows * 4 + 1;
            }
            default: {
                // ступени вверх и вниз с магмой между ними
                int h = 0;
                for (int i = 0; i < 6; i++) {
                    h += i < 3 ? 1 : -1;
                    fill(-1, 0, z, 1, h, z + 1, FLOOR);
                    if (d >= 2 && i % 2 == 1) {
                        fill(-3, 0, z, -2, 0, z + 1, MAGMA);
                        fill(2, 0, z, 3, 0, z + 1, MAGMA);
                    }
                    z += 2;
                }
                return z;
            }
        }
    }

    @Override
    protected BlockPos start(UUID player, int index) {
        if (player.equals(trapper)) {
            return rel(0, 13, 0);
        }
        return rel((index % 5) - 2, 1, (index / 5) % 3 - 1);
    }

    @Override
    public void begin() {
        List<EntityPlayerMP> on = online();
        if (preset.param("trapper", 0) == 1 && on.size() >= 2) {
            trapper = on.get(rnd.nextInt(on.size())).getUniqueID();
        }
        super.begin();
        if (trapper != null) {
            EntityPlayerMP t = here(trapper);
            if (t != null) {
                t.capabilities.allowFlying = true;
                t.sendPlayerAbilities();
                ItemStack remote = new ItemStack(Items.BLAZE_ROD);
                remote.setStackDisplayName("§cПульт ловушек §7(ПКМ)");
                NBTTagCompound tag = remote.getTagCompound() == null ? new NBTTagCompound() : remote.getTagCompound();
                tag.setBoolean("ecoTrapRemote", true);
                remote.setTagCompound(tag);
                t.inventory.setInventorySlotContents(0, remote);
                t.inventoryContainer.detectAndSendChanges();
                title(t, "&cТы — ловушечник", "&7ПКМ пультом — ловушки там, где лидер");
                Game.Result r = results.get(trapper);
                if (r != null) {
                    r.note = "ловушечник";
                }
            }
        }
    }

    // --- ход -------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        ticks++;
        if (trapCooldown > 0) {
            trapCooldown--;
        }
        for (Segment s : segments) {
            tickSegment(s);
        }
        for (Game.Result r : results.values()) {
            if (r.place != 0 || r.out || r.id.equals(trapper)) {
                continue;
            }
            EntityPlayerMP p = here(r.id);
            if (p == null) {
                continue;
            }
            keepAlive(p);
            int z = relZ(p);
            progress.merge(r.id, z, Math::max);
            r.score = progress.get(r.id);
            // финиш
            if (z >= finishZ && relY(p) >= 0) {
                finish(r.id, time(ticks) + ", падений: " + falls.getOrDefault(r.id, 0));
                continue;
            }
            // точки сохранения
            if (under(p).getBlock() == Blocks.EMERALD_BLOCK) {
                BlockPos c = new BlockPos(origin.getX(), origin.getY() + 1, (int) Math.floor(p.posZ));
                BlockPos old = checkpoint.get(r.id);
                if (old == null || old.getZ() < c.getZ() - 1) {
                    checkpoint.put(r.id, c);
                    ChatUtil.actionBar(p, "&aТочка сохранения");
                    ModSounds.play(p, ModSounds.GAME_CHECKPOINT, 0.8F, 1.2F);
                }
            }
            // падение и опасные блоки
            if (p.posY < origin.getY() - 4) {
                respawn(p, "Упал");
                continue;
            }
            IBlockState below = under(p);
            if (below.getBlock() == Blocks.MAGMA) {
                respawn(p, "Обжёгся о магму");
                continue;
            }
            Segment s = segmentAt(z);
            if (s != null) {
                if (s.type == Type.CRUSHER && crushed(s, p)) {
                    respawn(p, "Раздавлен прессом");
                    continue;
                }
                if (s.type == Type.COLLAPSE) {
                    BlockPos tile = new BlockPos(p.posX, p.posY - 0.2D, p.posZ);
                    if (s.tiles.contains(tile) && !s.falling.containsKey(tile) && !s.back.containsKey(tile)) {
                        s.falling.put(tile, ticks + s.period);
                        setAbs(tile, wool(EnumDyeColor.RED));
                    }
                }
            }
        }
        if (trapper != null && ticks % 20 == 0) {
            EntityPlayerMP t = here(trapper);
            if (t != null) {
                ChatUtil.actionBar(t, trapCooldown > 0 ? "&7Пульт перезаряжается…" : "&cПульт готов &7— ПКМ");
            }
        }
        if (ticks % 20 == 0) {
            int left = limitTicks - ticks;
            for (EntityPlayerMP p : active()) {
                if (!p.getUniqueID().equals(trapper)) {
                    int prog = Math.max(0, Math.min(100, progress.getOrDefault(p.getUniqueID(), 0) * 100 / Math.max(1, finishZ)));
                    ChatUtil.actionBar(p, "&e" + time(left) + " &7· пройдено &f" + prog + "%");
                }
            }
        }
        if (ticks >= limitTicks) {
            tell("&eВремя вышло!");
            for (Game.Result r : results.values()) {
                if (r.place == 0 && !r.out && !r.id.equals(trapper)) {
                    r.note = "прошёл " + Math.max(0, Math.min(100, (int) (r.score * 100 / Math.max(1, finishZ)))) + "%";
                }
            }
            endWithTrapper();
            return;
        }
        // все бегуны закончили — ловушечник тоже
        if (trapper != null) {
            boolean anyRunner = false;
            for (Game.Result r : results.values()) {
                if (!r.id.equals(trapper) && r.place == 0 && !r.out) {
                    anyRunner = true;
                }
            }
            if (!anyRunner) {
                endWithTrapper();
            }
        }
    }

    private void endWithTrapper() {
        if (trapper != null) {
            Game.Result t = results.get(trapper);
            boolean someoneFinished = false;
            for (Game.Result r : results.values()) {
                if (!r.id.equals(trapper) && r.place > 0) {
                    someoneFinished = true;
                }
            }
            if (t != null && t.place == 0) {
                if (someoneFinished) {
                    t.out = true;
                    t.note = "ловушечник — бегуны дошли";
                } else {
                    finish(trapper, "ловушечник: никто не дошёл");
                }
            }
        }
        end();
    }

    private Segment segmentAt(int z) {
        for (Segment s : segments) {
            if (z >= s.z1 && z <= s.z2) {
                return s;
            }
        }
        return null;
    }

    private void tickSegment(Segment s) {
        boolean forced = ticks < s.forcedUntil;
        switch (s.type) {
            case COLLAPSE: {
                for (Map.Entry<BlockPos, Integer> e : new ArrayList<Map.Entry<BlockPos, Integer>>(s.falling.entrySet())) {
                    if (ticks >= e.getValue()) {
                        setAbs(e.getKey(), air());
                        world.spawnParticle(EnumParticleTypes.BLOCK_DUST, e.getKey().getX() + 0.5D, e.getKey().getY() + 0.5D,
                                e.getKey().getZ() + 0.5D, 4, 0.3D, 0.1D, 0.3D, 0.0D, net.minecraft.block.Block.getStateId(wool(EnumDyeColor.ORANGE)));
                        s.falling.remove(e.getKey());
                        s.back.put(e.getKey(), ticks + 80);
                    }
                }
                for (Map.Entry<BlockPos, Integer> e : new ArrayList<Map.Entry<BlockPos, Integer>>(s.back.entrySet())) {
                    if (ticks >= e.getValue()) {
                        setAbs(e.getKey(), wool(EnumDyeColor.ORANGE));
                        s.back.remove(e.getKey());
                    }
                }
                return;
            }
            case PULSE: {
                for (int i = 0; i < s.groups.size(); i++) {
                    boolean danger = forced || ((ticks + s.phase.get(i)) % s.period) < s.period / 2;
                    if (danger != s.on[i]) {
                        s.on[i] = danger;
                        for (BlockPos b : s.groups.get(i)) {
                            setAbs(b, danger ? MAGMA : SAFE);
                        }
                    }
                }
                return;
            }
            case CRUSHER: {
                for (int i = 0; i < s.groups.size(); i++) {
                    int t = (ticks + s.phase.get(i)) % s.period;
                    boolean down = forced || t < 12;
                    if (down != s.on[i]) {
                        s.on[i] = down;
                        for (BlockPos b : s.groups.get(i)) {
                            setAbs(b, down ? CRUSH : air());
                        }
                        if (down) {
                            BlockPos b = s.groups.get(i).get(0);
                            com.ecoserv.init.ModSounds.playAt(world, b.getX(), b.getY(), b.getZ(), ModSounds.TRAP, 0.6F, 0.6F);
                        }
                    }
                }
                return;
            }
            case BEAM: {
                int t = ticks % s.period;
                if (forced || t == 0) {
                    double push = (ticks / s.period) % 2 == 0 ? 0.55D : -0.55D;
                    push *= 0.7D + difficulty * 0.12D;
                    for (EntityPlayerMP p : active()) {
                        int z = relZ(p);
                        if (z >= s.z1 && z <= s.z2 && !p.getUniqueID().equals(trapper)) {
                            p.motionX += push;
                            p.velocityChanged = true;
                            world.spawnParticle(EnumParticleTypes.CLOUD, p.posX, p.posY + 1.0D, p.posZ, 8, 0.4D, 0.4D, 0.4D, 0.05D);
                        }
                    }
                    if (forced) {
                        s.forcedUntil = 0;
                    }
                }
                return;
            }
            default:
        }
    }

    /** Игрок внутри опустившегося пресса. */
    private boolean crushed(Segment s, EntityPlayerMP p) {
        BlockPos feet = new BlockPos(p.posX, p.posY, p.posZ);
        BlockPos head = feet.up();
        for (int i = 0; i < s.groups.size(); i++) {
            if (s.on[i] && (s.groups.get(i).contains(feet) || s.groups.get(i).contains(head))) {
                return true;
            }
        }
        return false;
    }

    /** Пульт ловушечника: ловушки участка, где сейчас лидер. */
    public void trigger(EntityPlayerMP who) {
        if (!who.getUniqueID().equals(trapper) || trapCooldown > 0) {
            return;
        }
        int lead = Integer.MIN_VALUE;
        for (EntityPlayerMP p : active()) {
            if (!p.getUniqueID().equals(trapper)) {
                lead = Math.max(lead, relZ(p));
            }
        }
        Segment s = segmentAt(lead);
        if (s == null) {
            ChatUtil.actionBar(who, "&7Лидер сейчас не на участке с ловушками");
            return;
        }
        trapCooldown = 80 + (5 - difficulty) * 10;
        switch (s.type) {
            case COLLAPSE:
                for (BlockPos b : s.tiles) {
                    if (!s.falling.containsKey(b) && !s.back.containsKey(b)) {
                        s.falling.put(b, ticks + 6);
                        setAbs(b, wool(EnumDyeColor.RED));
                    }
                }
                break;
            case PULSE:
            case CRUSHER:
                s.forcedUntil = ticks + 30;
                break;
            case BEAM:
                s.forcedUntil = ticks + 1;
                break;
            default:
                // на остальных участках — порыв ветра всем на участке
                for (EntityPlayerMP p : active()) {
                    int z = relZ(p);
                    if (z >= s.z1 && z <= s.z2 && !p.getUniqueID().equals(trapper)) {
                        p.motionX += rnd.nextBoolean() ? 0.6D : -0.6D;
                        p.motionY += 0.3D;
                        p.velocityChanged = true;
                    }
                }
        }
        ChatUtil.actionBar(who, "&cЛовушка запущена!");
        ModSounds.play(who, ModSounds.TRAP, 1.0F, 1.3F);
    }

    @Override
    public String status() {
        int done = 0;
        for (Game.Result r : results.values()) {
            if (r.place > 0) {
                done++;
            }
        }
        return "идёт " + time(ticks) + " · финишировали " + done + " из " + results.size();
    }
}
