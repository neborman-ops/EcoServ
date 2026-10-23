package com.ecoserv.restrict;

import com.ecoserv.EcoServ;
import com.ecoserv.util.DataStore;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Буфер (доп. инвентарь) игрока: сюда уходят предметы, которые нельзя носить в текущем мире.
 * Хранится в world/ecoserv/stash.dat вместе с NBT предметов (зачарования, содержимое и т.п.).
 */
public final class Stash {

    private static final Map<UUID, List<ItemStack>> DATA = new HashMap<UUID, List<ItemStack>>();
    private static boolean dirty;

    private Stash() {
    }

    public static List<ItemStack> get(UUID player) {
        List<ItemStack> list = DATA.get(player);
        if (list == null) {
            list = new ArrayList<ItemStack>();
            DATA.put(player, list);
        }
        return list;
    }

    @Nullable
    public static List<ItemStack> peek(UUID player) {
        return DATA.get(player);
    }

    public static boolean hasItems(UUID player) {
        List<ItemStack> list = DATA.get(player);
        return list != null && !list.isEmpty();
    }

    public static int count(UUID player) {
        List<ItemStack> list = DATA.get(player);
        int n = 0;
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                n += list.get(i).getCount();
            }
        }
        return n;
    }

    /** Положить в буфер, дозаполняя такие же стаки. */
    public static void add(UUID player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        List<ItemStack> list = get(player);
        ItemStack rest = stack.copy();
        for (int i = 0; i < list.size() && !rest.isEmpty(); i++) {
            ItemStack existing = list.get(i);
            if (ItemHandlerHelper.canItemStacksStack(existing, rest)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space > 0) {
                    int n = Math.min(space, rest.getCount());
                    existing.grow(n);
                    rest.shrink(n);
                }
            }
        }
        if (!rest.isEmpty()) {
            list.add(rest);
        }
        dirty = true;
    }

    public static void clear(UUID player) {
        if (DATA.remove(player) != null) {
            dirty = true;
        }
    }

    public static void markDirty() {
        dirty = true;
    }

    // --- хранение -----------------------------------------------------------

    private static File file() {
        return DataStore.file("stash.dat");
    }

    public static void load() {
        DATA.clear();
        dirty = false;
        File file = file();
        if (!file.exists()) {
            return;
        }
        try (InputStream in = new FileInputStream(file)) {
            NBTTagCompound root = CompressedStreamTools.readCompressed(in);
            for (String key : root.getKeySet()) {
                UUID id;
                try {
                    id = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                NBTTagList items = root.getTagList(key, Constants.NBT.TAG_COMPOUND);
                List<ItemStack> list = new ArrayList<ItemStack>();
                for (int i = 0; i < items.tagCount(); i++) {
                    ItemStack stack = new ItemStack(items.getCompoundTagAt(i));
                    if (!stack.isEmpty()) {
                        list.add(stack);
                    }
                }
                if (!list.isEmpty()) {
                    DATA.put(id, list);
                }
            }
        } catch (Exception e) {
            // не затираем повреждённый файл пустым: откладываем его в сторону
            File broken = new File(file.getParentFile(), "stash.dat.broken-" + System.currentTimeMillis());
            EcoServ.LOG.error("Не удалось прочитать буфер предметов, файл сохранён как {}", broken.getName(), e);
            if (!file.renameTo(broken)) {
                EcoServ.LOG.error("И переименовать его тоже не удалось: {}", file);
            }
        }
    }

    public static void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public static void save() {
        NBTTagCompound root = new NBTTagCompound();
        for (Map.Entry<UUID, List<ItemStack>> e : DATA.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            NBTTagList items = new NBTTagList();
            for (int i = 0; i < e.getValue().size(); i++) {
                ItemStack stack = e.getValue().get(i);
                if (!stack.isEmpty()) {
                    items.appendTag(stack.writeToNBT(new NBTTagCompound()));
                }
            }
            root.setTag(e.getKey().toString(), items);
        }
        File file = file();
        File tmp = new File(file.getParentFile(), "stash.dat.tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            CompressedStreamTools.writeCompressed(root, out);
        } catch (Exception e) {
            EcoServ.LOG.error("Не удалось сохранить буфер предметов", e);
            return;
        }
        if (file.exists() && !file.delete()) {
            EcoServ.LOG.error("Не удалось заменить {}", file);
            return;
        }
        if (!tmp.renameTo(file)) {
            EcoServ.LOG.error("Не удалось переименовать {} в {}", tmp, file);
            return;
        }
        dirty = false;
    }

    public static Map<UUID, List<ItemStack>> view() {
        return DATA;
    }
}
