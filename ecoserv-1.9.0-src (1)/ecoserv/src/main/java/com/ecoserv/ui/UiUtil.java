package com.ecoserv.ui;

import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.ItemKey;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Мелочи для окон: иконки, короткие тексты, списки через запятую. */
public final class UiUtil {

    private UiUtil() {
    }

    public static ItemStack of(Item item) {
        return new ItemStack(item);
    }

    public static ItemStack of(Item item, int meta) {
        return new ItemStack(item, 1, meta);
    }

    public static ItemStack of(Block block) {
        return new ItemStack(block);
    }

    public static ItemStack of(Block block, int meta) {
        return new ItemStack(block, 1, meta);
    }

    /** Иконка из строки «modid:item@meta» (как в настройках подмира); пустая/битая — трава. */
    public static ItemStack icon(@Nullable String id) {
        if (id == null || id.trim().isEmpty()) {
            return new ItemStack(Blocks.GRASS);
        }
        ItemKey key = ItemKey.parse(id.trim());
        ItemStack stack = key.isValid() ? key.toStack(1) : ItemStack.EMPTY;
        return stack.isEmpty() ? new ItemStack(Blocks.GRASS) : stack;
    }

    /** Строка «modid:item@meta» для предмета (для полей с выбором предмета). */
    public static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return "";
        }
        return stack.getItem().getRegistryName() + "@" + stack.getMetadata();
    }

    /** Предмет из выбора (modid:item@meta); null — не предмет. */
    @Nullable
    public static String parseItemId(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return null;
        }
        ItemKey key = ItemKey.parse(t);
        if (!key.isValid() || key.toStack(1).isEmpty()) {
            return null;
        }
        return key.serialize();
    }

    public static String cut(@Nullable String s, int max) {
        if (s == null) {
            return "";
        }
        String plain = s;
        return plain.length() > max ? plain.substring(0, Math.max(1, max - 1)) + "…" : plain;
    }

    public static String plain(@Nullable String s) {
        return s == null ? "" : ChatUtil.strip(ChatUtil.color(s));
    }

    public static String yes(boolean on) {
        return on ? "&aда" : "&cнет";
    }

    /** Список «a, b, c» из ввода через запятую/пробелы, в нижнем регистре, без повторов. */
    public static List<String> splitList(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) {
            return out;
        }
        for (String part : text.split("[,;\\s]+")) {
            String p = part.trim().toLowerCase(Locale.ROOT);
            if (!p.isEmpty() && !out.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    public static String join(List<String> list) {
        return list == null || list.isEmpty() ? "" : String.join(", ", list);
    }
}
