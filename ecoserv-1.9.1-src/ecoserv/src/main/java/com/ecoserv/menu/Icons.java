package com.ecoserv.menu;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import java.util.ArrayList;
import java.util.List;

/** Иконки-кнопки для серверных меню (обычные предметы с именем и описанием). */
public final class Icons {

    private Icons() {
    }

    public static ItemStack of(Item item, int meta, String name, String... lore) {
        return build(new ItemStack(item, 1, meta), name, lore);
    }

    public static ItemStack of(Item item, String name, String... lore) {
        return build(new ItemStack(item), name, lore);
    }

    public static ItemStack of(ItemStack stack, String name, String... lore) {
        return build(stack.copy(), name, lore);
    }

    public static ItemStack build(ItemStack stack, String name, String... lore) {
        if (stack.isEmpty()) {
            return stack;
        }
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        NBTTagCompound display = tag.getCompoundTag("display");
        if (name != null) {
            display.setString("Name", color(name));
        }
        if (lore != null && lore.length > 0) {
            NBTTagList list = new NBTTagList();
            for (int i = 0; i < lore.length; i++) {
                if (lore[i] != null) {
                    list.appendTag(new NBTTagString(color(lore[i])));
                }
            }
            display.setTag("Lore", list);
        }
        tag.setTag("display", display);
        stack.setTagCompound(tag);
        return stack;
    }

    private static String color(String text) {
        return text.replace('&', '§');
    }

    /** Стекло-заполнитель фона. */
    public static ItemStack filler() {
        return of(Item.getItemFromBlock(Blocks.STAINED_GLASS_PANE), 7, "&r");
    }

    public static ItemStack green(String name, String... lore) {
        return of(Item.getItemFromBlock(Blocks.STAINED_GLASS_PANE), 5, name, lore);
    }

    public static ItemStack red(String name, String... lore) {
        return of(Item.getItemFromBlock(Blocks.STAINED_GLASS_PANE), 14, name, lore);
    }

    public static ItemStack money(String name, String... lore) {
        return of(Items.GOLD_INGOT, name, lore);
    }

    public static ItemStack arrowLeft(String name) {
        return of(Items.ARROW, name);
    }

    public static ItemStack arrowRight(String name) {
        return of(Items.ARROW, name);
    }

    public static ItemStack close() {
        return of(Item.getItemFromBlock(Blocks.BARRIER), "&cЗакрыть");
    }

    public static ItemStack back() {
        return of(Items.ARROW, "&eНазад");
    }

    /** Подсветка «выбрано» (блеск зачарования без строки зачарования). */
    public static ItemStack glint(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        stack.addEnchantment(net.minecraft.init.Enchantments.INFINITY, 1);
        if (stack.getTagCompound() != null) {
            stack.getTagCompound().setInteger("HideFlags", 1);
        }
        return stack;
    }

    /**
     * Разбивает длинный текст на строки описания по ширине (в символах),
     * каждая строка начинается с colorPrefix (например «&7»).
     */
    public static List<String> wrap(String text, int width, String colorPrefix) {
        List<String> out = new ArrayList<String>();
        if (text == null || text.trim().isEmpty()) {
            return out;
        }
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                    out.add(colorPrefix + line);
                    line.setLength(0);
                }
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(word);
            }
            if (line.length() > 0) {
                out.add(colorPrefix + line);
            }
        }
        return out;
    }

    public static List<String> lore(String... lines) {
        List<String> list = new ArrayList<String>();
        for (int i = 0; i < lines.length; i++) {
            list.add(lines[i]);
        }
        return list;
    }
}
