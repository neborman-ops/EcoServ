package com.ecoserv.subworld;

import com.ecoserv.EcoServ;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldTeleport;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Перенос правок шаблона в посещённые чанки локальных миров (п. 16). Для чанка сравниваются две версии
 * шаблона: та, с которой чанк сверен, и новая. Места, которые игроки не трогали, получают новые блоки
 * вместе с NBT (содержимое сундуков, таблички, спавнеры...), сущности-декор (рамки, стойки) — тоже.
 * Если там, где админы поставили новый блок, стоит блок игрока (или контейнер с его вещами), он
 * сносится и уходит в хранилище ГЛИТЧ владельца мира.
 */
public final class TemplatePatcher {

    /** Сколько изменённых блоков за тик (не меньше одного чанка). */
    private static final int BLOCK_BUDGET = 8192;
    private static final int CHUNK_BUDGET = 8;
    private static final List<String> VOLATILE_ENTITY_KEYS = Arrays.asList("Motion", "FallDistance", "Fire", "Air",
            "OnGround", "Dimension", "PortalCooldown", "UpdateBlocked", "Invulnerable");

    /** Очередь «измерение:x:z». */
    private static final Set<String> QUEUE = new LinkedHashSet<String>();

    private TemplatePatcher() {
    }

    static synchronized void queue(int dim, int x, int z) {
        QUEUE.add(TemplateEdits.key(dim, x, z));
    }

    /** После публикации — все загруженные чанки загруженных локальных миров подмира. */
    static void queueLoaded(Subworld sw, int version) {
        for (SubworldDim d : SubworldRegistry.dimsOf(sw.id)) {
            if (!d.isInstance()) {
                continue;
            }
            WorldServer w = DimensionManager.getWorld(d.dim);
            if (w == null) {
                continue;
            }
            for (Chunk c : new ArrayList<Chunk>(w.getChunkProvider().getLoadedChunks())) {
                if (TemplateEdits.versionOf(d.dim, c.x, c.z, d.templateVersion) < version) {
                    queue(d.dim, c.x, c.z);
                }
            }
        }
    }

    static synchronized void forgetDim(int dim) {
        String prefix = dim + ":";
        QUEUE.removeIf(k -> k.startsWith(prefix));
    }

    static void tick() {
        int changes = 0;
        int chunks = 0;
        while (changes < BLOCK_BUDGET && chunks < CHUNK_BUDGET) {
            String next;
            synchronized (TemplatePatcher.class) {
                Iterator<String> it = QUEUE.iterator();
                if (!it.hasNext()) {
                    return;
                }
                next = it.next();
                it.remove();
            }
            String[] p = next.split(":");
            try {
                changes += patch(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            } catch (Throwable t) {
                EcoServ.LOG.error("Не удалось перенести правку шаблона в чанк {}", next, t);
            }
            chunks++;
        }
    }

    // --- данные чанка из NBT ------------------------------------------------------------------

    /** Блоки (id<<4 | meta по индексу y<<8 | z<<4 | x), блок-сущности и сущности чанка. */
    static final class ChunkData {
        final int[] states = new int[65536];
        final Map<Integer, NBTTagCompound> tiles = new HashMap<Integer, NBTTagCompound>();
        final Map<UUID, NBTTagCompound> entities = new LinkedHashMap<UUID, NBTTagCompound>();
    }

    static ChunkData parse(@Nullable NBTTagCompound full) {
        ChunkData cd = new ChunkData();
        if (full == null) {
            return cd;
        }
        NBTTagCompound level = full.getCompoundTag("Level");
        NBTTagList sections = level.getTagList("Sections", 10);
        for (int s = 0; s < sections.tagCount(); s++) {
            NBTTagCompound sec = sections.getCompoundTagAt(s);
            int sy = sec.getByte("Y") & 15;
            byte[] blocks = sec.getByteArray("Blocks");
            byte[] data = sec.getByteArray("Data");
            if (blocks.length != 4096 || data.length != 2048) {
                continue;
            }
            byte[] add = sec.hasKey("Add", 7) ? sec.getByteArray("Add") : null;
            if (add != null && add.length != 2048) {
                add = null;
            }
            int base = sy << 12;
            for (int i = 0; i < 4096; i++) {
                int hi = add == null ? 0 : nibble(add, i);
                int id = (hi << 8) | (blocks[i] & 255);
                cd.states[base | i] = id << 4 | nibble(data, i);
            }
        }
        NBTTagList tiles = level.getTagList("TileEntities", 10);
        for (int i = 0; i < tiles.tagCount(); i++) {
            NBTTagCompound t = tiles.getCompoundTagAt(i);
            int idx = (t.getInteger("y") & 255) << 8 | (t.getInteger("z") & 15) << 4 | (t.getInteger("x") & 15);
            cd.tiles.put(idx, t);
        }
        NBTTagList entities = level.getTagList("Entities", 10);
        for (int i = 0; i < entities.tagCount(); i++) {
            NBTTagCompound e = entities.getCompoundTagAt(i);
            if (e.hasUniqueId("UUID")) {
                cd.entities.put(e.getUniqueId("UUID"), e);
            }
        }
        return cd;
    }

    private static int nibble(byte[] arr, int i) {
        return (i & 1) == 0 ? arr[i >> 1] & 15 : (arr[i >> 1] >> 4) & 15;
    }

    static int stateOf(IBlockState s) {
        Block b = s.getBlock();
        return Block.getIdFromBlock(b) << 4 | b.getMetaFromState(s);
    }

    @SuppressWarnings("deprecation")
    static IBlockState stateFrom(int v) {
        return Block.getBlockById(v >> 4).getStateFromMeta(v & 15);
    }

    private static boolean isAir(int v) {
        return (v >> 4) == 0;
    }

    private static NBTTagCompound strip(NBTTagCompound tag, String... keys) {
        NBTTagCompound c = tag.copy();
        for (String k : keys) {
            c.removeTag(k);
        }
        return c;
    }

    private static boolean sameTile(@Nullable NBTTagCompound a, @Nullable NBTTagCompound b) {
        if (a == null || b == null) {
            return a == b;
        }
        return strip(a, "x", "y", "z").equals(strip(b, "x", "y", "z"));
    }

    private static boolean sameEntity(NBTTagCompound a, NBTTagCompound b) {
        NBTTagCompound x = a.copy();
        NBTTagCompound y = b.copy();
        for (String k : VOLATILE_ENTITY_KEYS) {
            x.removeTag(k);
            y.removeTag(k);
        }
        return x.equals(y);
    }

    // --- перенос -------------------------------------------------------------------------------

    /** Обновить один чанк; вернуть число изменённых блоков. */
    private static int patch(int dim, int cx, int cz) {
        WorldServer w = DimensionManager.getWorld(dim);
        SubworldDim d = SubworldRegistry.dim(dim);
        Subworld sw = d == null ? null : SubworldRegistry.get(d.subworld);
        if (w == null || d == null || sw == null || !d.isInstance()) {
            return 0;
        }
        Chunk chunk = w.getChunkProvider().getLoadedChunk(cx, cz);
        if (chunk == null) {
            return 0;
        }
        int from = TemplateEdits.versionOf(dim, cx, cz, d.templateVersion);
        int to = sw.templateVersion;
        if (from >= to) {
            return 0;
        }
        int newest = SubworldLayers.find(sw.id, cx, cz, to);
        if (newest <= from) {
            // с той версии это место в шаблоне не менялось
            TemplateEdits.setVersion(dim, cx, cz, to);
            chunk.markDirty();
            return 0;
        }
        MinecraftServer server = PlayerUtil.server();
        if (server == null) {
            return 0;
        }
        TemplateChunkLoader loader = new TemplateChunkLoader(SubworldLayers.root(sw.id), server.getDataFixer());
        NBTTagCompound newNbt = loader.readNbt(sw.id, newest, cx, cz);
        if (newNbt == null) {
            TemplateEdits.setVersion(dim, cx, cz, to);
            return 0;
        }
        NBTTagCompound oldNbt;
        int oldVersion = SubworldLayers.find(sw.id, cx, cz, from);
        if (oldVersion >= 0) {
            oldNbt = loader.readNbt(sw.id, oldVersion, cx, cz);
        } else if (!TemplateBase.needsSnapshot(sw)) {
            oldNbt = null;
        } else {
            oldNbt = TemplateBase.read(sw.id, cx, cz);
            if (oldNbt == null) {
                // исходного вида места нет — сравнить не с чем, чанк остаётся как есть
                TemplateEdits.setVersion(dim, cx, cz, to);
                chunk.markDirty();
                return 0;
            }
        }
        ChunkData oldData = parse(oldNbt);
        ChunkData newData = parse(newNbt);
        int changes = applyBlocks(w, d, sw, chunk, oldData, newData);
        changes += applyEntities(w, oldData, newData);
        TemplateEdits.setVersion(dim, cx, cz, to);
        chunk.markDirty();
        if (changes > 0) {
            rescuePlayers(w, cx, cz);
        }
        return changes;
    }

    /**
     * Правило для контейнеров: одну и ту же добычу шаблона игрок получает только один раз. Если он уже
     * брал или клал вещи, содержимое старой версии шаблона ему повторно не выдаётся.
     */
    private static int applyBlocks(WorldServer w, SubworldDim d, Subworld sw, Chunk chunk, ChunkData o, ChunkData n) {
        int changes = 0;
        int baseX = chunk.x << 4;
        int baseZ = chunk.z << 4;
        for (int i = 0; i < 65536; i++) {
            int ov = o.states[i];
            int nv = n.states[i];
            NBTTagCompound oTile = o.tiles.get(i);
            NBTTagCompound nTile = n.tiles.get(i);
            boolean blockChanged = ov != nv;
            if (!blockChanged && sameTile(oTile, nTile)) {
                continue;
            }
            BlockPos pos = new BlockPos(baseX + (i & 15), i >> 8, baseZ + ((i >> 4) & 15));
            IBlockState cur = chunk.getBlockState(pos);
            int cv = stateOf(cur);
            NBTTagCompound curTile = tileNbt(w, pos);
            if (cv == ov) {
                // блок здесь шаблонный — игрок его не менял
                boolean touched = touched(w, pos, curTile, oTile);
                if (!blockChanged) {
                    // тот же блок, у шаблона поменялось содержимое (сундук, табличка, спавнер...)
                    if (touched) {
                        // игрок уже брал или клал вещи — контейнер остаётся как есть: иначе пришлось бы
                        // убирать его вещи, а добычу шаблона выдавать заново
                        continue;
                    }
                    applyTile(w, pos, nTile);
                } else {
                    if (touched) {
                        // вещи, которые положил игрок, — в ГЛИТЧ (добыча шаблона — не его)
                        toGlitch(w, d, sw, pos, cur, false, oTile);
                    }
                    setBlock(w, d, pos, nv, touched ? withoutOldLoot(w, pos, nTile, oTile) : nTile);
                }
                changes++;
                continue;
            }
            // игрок здесь что-то менял: блока старой версии нет, его добычу игрок уже получил
            if (!blockChanged || isAir(nv)) {
                // шаблон тут блок не менял или убрал — всё, что сделал игрок, остаётся
                continue;
            }
            NBTTagCompound fresh = withoutOldLoot(w, pos, nTile, oTile);
            if (cv == nv) {
                // у игрока здесь уже такой же блок, как в новой версии
                if (sameTile(curTile, fresh)) {
                    continue;
                }
                boolean mine = !contents(w.getTileEntity(pos)).isEmpty();
                boolean brings = hasThings(w, pos, fresh);
                if (!brings) {
                    if (mine) {
                        // шаблон вещей сюда не кладёт — контейнер игрока с его вещами не трогаем
                        continue;
                    }
                    // табличка, спавнер, пустой контейнер — данные из шаблона
                    applyTile(w, pos, fresh);
                } else {
                    // вещи игрока — в ГЛИТЧ, содержимое — из новой версии шаблона
                    toGlitch(w, d, sw, pos, cur, false, null);
                    applyTile(w, pos, fresh);
                }
                changes++;
                continue;
            }
            if (!isAir(cv) && TemplateEdits.placed(d.dim, pos)) {
                // блок игрока (с его содержимым) — в хранилище ГЛИТЧ
                toGlitch(w, d, sw, pos, cur, true, null);
            } else if (!isAir(cv)) {
                // не его блок, но вещи в контейнере — его
                toGlitch(w, d, sw, pos, cur, false, null);
            }
            setBlock(w, d, pos, nv, fresh);
            changes++;
        }
        return changes;
    }

    // --- содержимое контейнеров ------------------------------------------------------------

    private static String lootTable(@Nullable NBTTagCompound tag) {
        return tag == null ? "" : tag.getString("LootTable");
    }

    /**
     * Временная блок-сущность из NBT шаблона — только чтобы посмотреть или поправить содержимое
     * (в мир не ставится). Таблицу добычи не разворачиваем: добыча выпадает только при открытии.
     */
    @Nullable
    private static TileEntity tempTile(WorldServer w, BlockPos pos, @Nullable NBTTagCompound tag) {
        if (tag == null) {
            return null;
        }
        NBTTagCompound t = tag.copy();
        t.removeTag("LootTable");
        t.removeTag("LootTableSeed");
        t.setInteger("x", pos.getX());
        t.setInteger("y", pos.getY());
        t.setInteger("z", pos.getZ());
        try {
            return TileEntity.create(w, t);
        } catch (Throwable e) {
            return null;
        }
    }

    /** Вещи, лежащие в контейнере по NBT шаблона. */
    private static List<ItemStack> templateContents(WorldServer w, BlockPos pos, @Nullable NBTTagCompound tag) {
        return contents(tempTile(w, pos, tag));
    }

    /** Есть ли по этому NBT что выдать: вещи или таблица добычи. */
    private static boolean hasThings(WorldServer w, BlockPos pos, @Nullable NBTTagCompound tag) {
        return tag != null && (!lootTable(tag).isEmpty() || !templateContents(w, pos, tag).isEmpty());
    }

    private static Map<String, Integer> multiset(List<ItemStack> items) {
        Map<String, Integer> out = new HashMap<String, Integer>();
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                out.merge(itemKey(s), s.getCount(), Integer::sum);
            }
        }
        return out;
    }

    /**
     * Игрок трогал вещи в блоке шаблона: они не те, что лежали там по старой версии. Прочие данные
     * (таймер спавнера, топливо печи) не в счёт — они меняются сами.
     */
    private static boolean touched(WorldServer w, BlockPos pos, @Nullable NBTTagCompound curTile,
                                   @Nullable NBTTagCompound oTile) {
        if (sameTile(curTile, oTile)) {
            return false;
        }
        String oldTable = lootTable(oTile);
        if (!oldTable.isEmpty()) {
            // добыча старой версии ещё не выпала в шаблоне: трогали, если в мире она уже выпала
            return !oldTable.equals(lootTable(curTile));
        }
        if (!lootTable(curTile).isEmpty()) {
            return true;
        }
        return !multiset(contents(w.getTileEntity(pos))).equals(multiset(templateContents(w, pos, oTile)));
    }

    /**
     * Содержимое нового блока шаблона для места, где игрок уже получил добычу старой версии: без тех
     * вещей, что там уже были, и без повторной той же таблицы добычи.
     */
    @Nullable
    private static NBTTagCompound withoutOldLoot(WorldServer w, BlockPos pos, @Nullable NBTTagCompound nTile,
                                                 @Nullable NBTTagCompound oTile) {
        if (nTile == null || oTile == null) {
            return nTile;
        }
        NBTTagCompound out = nTile.copy();
        String oldTable = lootTable(oTile);
        if (!oldTable.isEmpty() && oldTable.equals(lootTable(out))) {
            out.removeTag("LootTable");
            out.removeTag("LootTableSeed");
        }
        if (!lootTable(out).isEmpty()) {
            // другая таблица добычи — новая добыча, её и выдаём
            return out;
        }
        Map<String, Integer> budget = multiset(templateContents(w, pos, oTile));
        if (budget.isEmpty()) {
            return out;
        }
        TileEntity temp = tempTile(w, pos, out);
        if (temp == null) {
            return out;
        }
        removeItems(temp, budget);
        try {
            return temp.writeToNBT(new NBTTagCompound());
        } catch (Throwable e) {
            return out;
        }
    }

    /** Убрать из контейнера вещи по списку (сколько каких). */
    private static void removeItems(TileEntity te, Map<String, Integer> budget) {
        if (te instanceof IInventory) {
            IInventory inv = (IInventory) te;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                int take = s.isEmpty() ? 0 : Math.min(s.getCount(), budget.getOrDefault(itemKey(s), 0));
                if (take > 0) {
                    budget.merge(itemKey(s), -take, Integer::sum);
                    inv.decrStackSize(i, take);
                }
            }
            return;
        }
        if (!te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
            return;
        }
        IItemHandler h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (h == null) {
            return;
        }
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            int take = s.isEmpty() ? 0 : Math.min(s.getCount(), budget.getOrDefault(itemKey(s), 0));
            if (take <= 0) {
                continue;
            }
            budget.merge(itemKey(s), -take, Integer::sum);
            if (h instanceof IItemHandlerModifiable) {
                ItemStack left = s.copy();
                left.shrink(take);
                ((IItemHandlerModifiable) h).setStackInSlot(i, left);
            } else {
                h.extractItem(i, take, false);
            }
        }
    }

    @Nullable
    private static NBTTagCompound tileNbt(WorldServer w, BlockPos pos) {
        TileEntity te = w.getTileEntity(pos);
        return te == null ? null : te.writeToNBT(new NBTTagCompound());
    }

    private static void setBlock(WorldServer w, SubworldDim d, BlockPos pos, int state, @Nullable NBTTagCompound tile) {
        // блок-сущность убираем заранее: иначе контейнер высыпал бы содержимое на землю
        if (w.getTileEntity(pos) != null) {
            w.removeTileEntity(pos);
        }
        w.setBlockState(pos, stateFrom(state), 2 | 16);
        TemplateEdits.clearPlaced(d.dim, pos);
        if (tile != null) {
            applyTile(w, pos, tile);
        }
    }

    private static void applyTile(WorldServer w, BlockPos pos, @Nullable NBTTagCompound tile) {
        if (tile == null) {
            if (w.getTileEntity(pos) != null && !w.getBlockState(pos).getBlock().hasTileEntity(w.getBlockState(pos))) {
                w.removeTileEntity(pos);
            }
            return;
        }
        NBTTagCompound tag = tile.copy();
        tag.setInteger("x", pos.getX());
        tag.setInteger("y", pos.getY());
        tag.setInteger("z", pos.getZ());
        TileEntity created = TileEntity.create(w, tag);
        if (created == null) {
            return;
        }
        TileEntity old = w.getTileEntity(pos);
        if (old != null) {
            // старое содержимое не высыпаем
            clear(old);
        }
        w.setTileEntity(pos, created);
        created.markDirty();
        IBlockState st = w.getBlockState(pos);
        w.notifyBlockUpdate(pos, st, st, 3);
    }

    /** Содержимое контейнера (копии). */
    private static List<ItemStack> contents(@Nullable TileEntity te) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (te == null) {
            return out;
        }
        if (te instanceof net.minecraft.world.storage.loot.ILootContainer
                && ((net.minecraft.world.storage.loot.ILootContainer) te).getLootTable() != null) {
            // добыча ещё не выпала (сундук не открывали): вещей там нет, и разворачивать её незачем
            return out;
        }
        if (te instanceof IInventory) {
            IInventory inv = (IInventory) te;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty()) {
                    out.add(s.copy());
                }
            }
        } else if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
            IItemHandler h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (h != null) {
                for (int i = 0; i < h.getSlots(); i++) {
                    ItemStack s = h.getStackInSlot(i);
                    if (!s.isEmpty()) {
                        out.add(s.copy());
                    }
                }
            }
        }
        return out;
    }

    private static void clear(TileEntity te) {
        if (te instanceof IInventory) {
            ((IInventory) te).clear();
            return;
        }
        if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
            IItemHandler h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (h == null) {
                return;
            }
            for (int i = 0; i < h.getSlots(); i++) {
                for (int guard = 0; guard < 64 && !h.getStackInSlot(i).isEmpty(); guard++) {
                    if (h.extractItem(i, Integer.MAX_VALUE, false).isEmpty()) {
                        break;
                    }
                }
            }
        }
    }

    private static String itemKey(ItemStack s) {
        return String.valueOf(s.getItem().getRegistryName()) + "@" + s.getMetadata() + "#"
                + (s.getTagCompound() == null ? "" : s.getTagCompound().toString());
    }

    /** Вещи, которые положил игрок: содержимое минус то, что лежало там по шаблону. */
    private static List<ItemStack> playerItems(WorldServer w, BlockPos pos, @Nullable TileEntity te,
                                               @Nullable NBTTagCompound templateTile) {
        List<ItemStack> current = contents(te);
        if (templateTile == null) {
            return current;
        }
        Map<String, Integer> budget = multiset(templateContents(w, pos, templateTile));
        if (budget.isEmpty()) {
            return current;
        }
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (ItemStack s : current) {
            String k = itemKey(s);
            int left = budget.getOrDefault(k, 0);
            if (left >= s.getCount()) {
                budget.put(k, left - s.getCount());
                continue;
            }
            s.shrink(left);
            budget.put(k, 0);
            out.add(s);
        }
        return out;
    }

    /** Верхняя половина двери/растения или изголовье кровати — предмет даёт вторая половина. */
    private static boolean secondaryPart(IBlockState state) {
        for (net.minecraft.block.properties.IProperty<?> prop : state.getPropertyKeys()) {
            String name = prop.getName();
            String value = String.valueOf(state.getValue(prop));
            if (("half".equals(name) && "upper".equals(value)) || ("part".equals(name) && "head".equals(value))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Блок игрока (и/или его вещи из контейнера) — в хранилище ГЛИТЧ владельца мира.
     * templateTile — содержимое этого места по шаблону (его не спасаем, это не вещи игрока).
     */
    private static void toGlitch(WorldServer w, SubworldDim d, Subworld sw, BlockPos pos, IBlockState state,
                                 boolean withBlock, @Nullable NBTTagCompound templateTile) {
        String note = sw.name + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        TileEntity te = w.getTileEntity(pos);
        for (ItemStack s : playerItems(w, pos, te, templateTile)) {
            GlitchStorage.add(d.owner, GlitchStorage.EDIT, s, note);
        }
        if (te != null) {
            clear(te);
        }
        if (withBlock && !secondaryPart(state)) {
            ItemStack block = ItemStack.EMPTY;
            try {
                block = state.getBlock().getPickBlock(state, null, w, pos, null);
            } catch (Throwable t) {
                block = ItemStack.EMPTY;
            }
            if (block.isEmpty()) {
                Item item = Item.getItemFromBlock(state.getBlock());
                if (item != null && item != net.minecraft.init.Items.AIR) {
                    block = new ItemStack(item, 1, state.getBlock().damageDropped(state));
                }
            }
            if (!block.isEmpty()) {
                GlitchStorage.add(d.owner, GlitchStorage.EDIT, block, note);
            }
        }
    }

    private static boolean decor(Entity e) {
        return e instanceof EntityHanging || e instanceof EntityArmorStand;
    }

    /** Сущность не менялась игроком: совпадает с тем, что было в шаблоне. */
    private static boolean untouched(Entity e, NBTTagCompound template) {
        NBTTagCompound now = e.writeToNBT(new NBTTagCompound());
        NBTTagCompound was = template.copy();
        was.removeTag("id");
        now.removeTag("id");
        return sameEntity(now, was);
    }

    /**
     * Только декор (рамки, картины, стойки для брони): добавленный админами появляется, убранный исчезает,
     * изменённый обновляется — если игрок его не трогал. Мобов и чужие вещи не трогаем вовсе.
     */
    private static int applyEntities(WorldServer w, ChunkData o, ChunkData n) {
        int changes = 0;
        for (Map.Entry<UUID, NBTTagCompound> e : n.entities.entrySet()) {
            NBTTagCompound before = o.entities.get(e.getKey());
            Entity existing = w.getEntityFromUuid(e.getKey());
            if (before == null) {
                if (existing == null) {
                    Entity created = net.minecraft.entity.EntityList.createEntityFromNBT(e.getValue().copy(), w);
                    if (created != null && decor(created) && w.spawnEntity(created)) {
                        changes++;
                    }
                }
            } else if (existing != null && decor(existing) && !sameEntity(before, e.getValue()) && untouched(existing, before)) {
                existing.setDead();
                w.removeEntityDangerously(existing);
                Entity created = net.minecraft.entity.EntityList.createEntityFromNBT(e.getValue().copy(), w);
                if (created != null && w.spawnEntity(created)) {
                    changes++;
                }
            }
        }
        for (Map.Entry<UUID, NBTTagCompound> e : o.entities.entrySet()) {
            if (n.entities.containsKey(e.getKey())) {
                continue;
            }
            Entity existing = w.getEntityFromUuid(e.getKey());
            if (existing != null && decor(existing) && untouched(existing, e.getValue())) {
                existing.setDead();
                changes++;
            }
        }
        return changes;
    }

    /** Игрокам, в которых теперь оказался блок, — наверх, на свободное место. */
    private static void rescuePlayers(WorldServer w, int cx, int cz) {
        for (EntityPlayer p : new ArrayList<EntityPlayer>(w.playerEntities)) {
            if (!(p instanceof EntityPlayerMP) || ((int) Math.floor(p.posX)) >> 4 != cx || ((int) Math.floor(p.posZ)) >> 4 != cz) {
                continue;
            }
            BlockPos feet = new BlockPos(p);
            if (WorldTeleport.isSafe(w, feet)) {
                continue;
            }
            BlockPos safe = WorldTeleport.findSafe(w, feet, 4, feet.getY());
            if (safe == null) {
                safe = SubworldTeleport.safeSpawn(w);
            }
            ((EntityPlayerMP) p).connection.setPlayerLocation(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                    p.rotationYaw, p.rotationPitch);
        }
    }
}
