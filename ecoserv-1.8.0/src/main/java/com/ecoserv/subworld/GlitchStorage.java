package com.ecoserv.subworld;

import com.ecoserv.util.DataStore;
import com.ecoserv.util.NbtFiles;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Хранилище ГЛИТЧ (п. 17): предметы, которые иначе пропали бы — блоки игроков, снесённые глобальной
 * правкой шаблона, предметы, упавшие в бездну, выданные админом. Достаются через ГЛИТЧ-сундук,
 * настроенный палкой GLITCH SELECT на нужные виды. Файл world/ecoserv/glitch.dat.
 */
public final class GlitchStorage {

    /** Блоки и содержимое, снесённые глобальным редактированием подмира. */
    public static final String EDIT = "edit";
    /** Предметы, упавшие в бездну в подмирах. */
    public static final String VOID = "void";
    /** Выданные админом командой. */
    public static final String ADMIN = "admin";
    /** Вещи, выпавшие при смерти в мире с пометкой GLITCH (не валяются на земле, а ждут в сундуке). */
    public static final String DEATH = "death";

    public static final List<String> CATEGORIES = Arrays.asList(EDIT, VOID, DEATH, ADMIN);

    /** Одна запись. */
    public static final class Entry {
        public final String category;
        public final ItemStack stack;
        public final long time;
        public final String note;

        Entry(String category, ItemStack stack, long time, String note) {
            this.category = category;
            this.stack = stack;
            this.time = time;
            this.note = note;
        }
    }

    /** Владелец («player:uuid» / «team:код») -> записи. */
    private static final Map<String, List<Entry>> DATA = new LinkedHashMap<String, List<Entry>>();
    private static boolean loaded;
    private static boolean dirty;

    private GlitchStorage() {
    }

    public static String title(String category) {
        switch (category) {
            case EDIT:
                return "снесено правкой шаблона";
            case VOID:
                return "упало в бездну";
            case DEATH:
                return "выпало при смерти в ГЛИТЧ-мире";
            case ADMIN:
                return "от админа";
            default:
                return category;
        }
    }

    private static File file() {
        return DataStore.file("glitch.dat");
    }

    private static void ensure() {
        if (loaded) {
            return;
        }
        loaded = true;
        DATA.clear();
        NBTTagCompound root = NbtFiles.read(file());
        if (root == null) {
            return;
        }
        for (String owner : root.getKeySet()) {
            NBTTagList list = root.getTagList(owner, 10);
            List<Entry> entries = new ArrayList<Entry>();
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound e = list.getCompoundTagAt(i);
                ItemStack stack = new ItemStack(e.getCompoundTag("item"));
                if (!stack.isEmpty()) {
                    entries.add(new Entry(e.getString("cat"), stack, e.getLong("time"), e.getString("note")));
                }
            }
            if (!entries.isEmpty()) {
                DATA.put(owner, entries);
            }
        }
    }

    /** При смене мира сервера (одиночная игра). */
    public static void reset() {
        loaded = false;
        dirty = false;
        DATA.clear();
    }

    public static void saveIfDirty() {
        if (!dirty || !loaded) {
            return;
        }
        NBTTagCompound root = new NBTTagCompound();
        for (Map.Entry<String, List<Entry>> e : DATA.entrySet()) {
            NBTTagList list = new NBTTagList();
            for (Entry entry : e.getValue()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("cat", entry.category);
                tag.setTag("item", entry.stack.writeToNBT(new NBTTagCompound()));
                tag.setLong("time", entry.time);
                tag.setString("note", entry.note);
                list.appendTag(tag);
            }
            if (list.tagCount() > 0) {
                root.setTag(e.getKey(), list);
            }
        }
        if (NbtFiles.write(file(), root)) {
            dirty = false;
        }
    }

    /** Больше записей у одного владельца не храним (самые старые уходят). */
    private static final int MAX_ENTRIES = 1000;

    /** Положить предмет владельцу (одинаковые предметы складываются в стопки). */
    public static void add(String owner, String category, ItemStack stack, String note) {
        if (stack == null || stack.isEmpty() || owner == null || owner.isEmpty()) {
            return;
        }
        ensure();
        List<Entry> list = DATA.get(owner);
        if (list == null) {
            list = new ArrayList<Entry>();
            DATA.put(owner, list);
        }
        ItemStack rest = stack.copy();
        String n = note == null ? "" : note;
        for (int i = list.size() - 1; i >= 0 && i >= list.size() - 16 && !rest.isEmpty(); i--) {
            Entry e = list.get(i);
            if (!e.category.equals(category) || !e.note.equals(n) || !ItemStack.areItemsEqual(e.stack, rest)
                    || !ItemStack.areItemStackTagsEqual(e.stack, rest)) {
                continue;
            }
            int room = e.stack.getMaxStackSize() - e.stack.getCount();
            if (room > 0) {
                int move = Math.min(room, rest.getCount());
                e.stack.grow(move);
                rest.shrink(move);
            }
        }
        while (!rest.isEmpty()) {
            ItemStack part = rest.splitStack(Math.max(1, rest.getMaxStackSize()));
            list.add(new Entry(category, part, System.currentTimeMillis(), n));
        }
        while (list.size() > MAX_ENTRIES) {
            list.remove(0);
        }
        dirty = true;
    }

    /** Ключи, чьи записи видит игрок: он сам и его команда. */
    public static List<String> ownersOf(UUID player) {
        List<String> out = new ArrayList<String>();
        out.add("player:" + player);
        String team = SubworldTeams.teamKey(player);
        if (team != null) {
            out.add(team);
        }
        return out;
    }

    /** Записи игрока нужных видов (null — всех). Пара: владелец + запись. */
    public static List<Object[]> view(EntityPlayerMP player, @Nullable Collection<String> categories) {
        ensure();
        List<Object[]> out = new ArrayList<Object[]>();
        for (String owner : ownersOf(player.getUniqueID())) {
            List<Entry> list = DATA.get(owner);
            if (list == null) {
                continue;
            }
            for (Entry e : list) {
                if (categories == null || categories.contains(e.category)) {
                    out.add(new Object[]{owner, e});
                }
            }
        }
        return out;
    }

    /** Забрать запись (true — была и удалена). */
    public static boolean take(String owner, Entry entry) {
        ensure();
        List<Entry> list = DATA.get(owner);
        if (list == null || !list.remove(entry)) {
            return false;
        }
        if (list.isEmpty()) {
            DATA.remove(owner);
        }
        dirty = true;
        return true;
    }

    public static int count(String owner) {
        ensure();
        List<Entry> list = DATA.get(owner);
        return list == null ? 0 : list.size();
    }

    /** Все владельцы с записями (для админа). */
    public static List<String> owners() {
        ensure();
        return new ArrayList<String>(DATA.keySet());
    }
}
