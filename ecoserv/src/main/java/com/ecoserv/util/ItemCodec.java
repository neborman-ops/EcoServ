package com.ecoserv.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;

/**
 * Предметы в JSON-файлах: строка с NBT предмета (как в /give), сохраняет зачарования,
 * имена и содержимое. Нечитаемые записи пропускаются, а не роняют загрузку.
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static String encode(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return stack.writeToNBT(new NBTTagCompound()).toString();
    }

    public static ItemStack decode(String text) {
        if (text == null || text.trim().isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            NBTTagCompound tag = JsonToNBT.getTagFromJson(text);
            ItemStack stack = new ItemStack(tag);
            return stack.isEmpty() ? ItemStack.EMPTY : stack;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public static List<String> encodeAll(List<ItemStack> stacks) {
        List<String> list = new ArrayList<String>();
        if (stacks == null) {
            return list;
        }
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack != null && !stack.isEmpty()) {
                list.add(encode(stack));
            }
        }
        return list;
    }

    public static List<ItemStack> decodeAll(List<String> texts) {
        List<ItemStack> list = new ArrayList<ItemStack>();
        if (texts == null) {
            return list;
        }
        for (int i = 0; i < texts.size(); i++) {
            ItemStack stack = decode(texts.get(i));
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
        return list;
    }

    /** Список предметов в NBT (для файлов .dat и тайлов). */
    public static NBTTagList toNBT(List<ItemStack> stacks) {
        NBTTagList list = new NBTTagList();
        if (stacks == null) {
            return list;
        }
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack != null && !stack.isEmpty()) {
                list.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
        }
        return list;
    }

    public static List<ItemStack> fromNBT(NBTTagList list) {
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        if (list == null) {
            return stacks;
        }
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStack stack = new ItemStack(list.getCompoundTagAt(i));
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    /** Короткое описание для чата: «Алмаз x3, Железный слиток x16». */
    public static String describe(List<ItemStack> stacks, int max) {
        if (stacks == null || stacks.isEmpty()) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (shown >= max) {
                sb.append(" …");
                break;
            }
            if (shown > 0) {
                sb.append(", ");
            }
            sb.append(stack.getDisplayName()).append(" x").append(stack.getCount());
            shown++;
        }
        return sb.toString();
    }
}
