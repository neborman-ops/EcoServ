package com.ecoserv.subworld.menu;

import com.ecoserv.menu.ChatPrompt;
import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.ItemKey;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/** Общее для меню подмиров: переключатели, строки, ввод через чат с возвратом в меню. */
abstract class SubMenu extends ChestMenu {

    protected static final int PER_PAGE = 36;

    /** Что сделать с введённым текстом. */
    protected interface Input {
        void accept(EntityPlayerMP who, String text);
    }

    protected SubMenu(EntityPlayerMP player, String title, int rows) {
        super(player, cut(title), rows);
    }

    private static String cut(String title) {
        String plain = ChatUtil.strip(ChatUtil.color(title));
        return plain.length() > 30 ? plain.substring(0, 29) + "…" : plain;
    }

    /** Новое меню того же вида (для возврата после ввода в чат). */
    protected abstract ChestMenu reopen(EntityPlayerMP who);

    protected void prompt(String question, final Input input) {
        ChatPrompt.ask(player, question, new ChatPrompt.Callback() {
            @Override
            public void accept(EntityPlayerMP who, String text) {
                input.accept(who, text);
                ChestMenu next = reopen(who);
                if (next != null) {
                    next.open();
                }
            }
        });
    }

    protected static ItemStack toggle(boolean on, String title, String... hint) {
        String[] lore = new String[hint.length + 1];
        System.arraycopy(hint, 0, lore, 0, hint.length);
        lore[hint.length] = "&8ЛКМ — переключить";
        return Icons.of(Items.DYE, on ? 10 : 8, (on ? "&a" : "&c") + title + ": " + (on ? "да" : "нет"), lore);
    }

    protected static String line(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return "&8нет";
        }
        return "&f" + (text.length() > 40 ? text.substring(0, 39) + "…" : text);
    }

    /** Иконка из строки «modid:item@meta». */
    protected static ItemStack icon(String id) {
        ItemKey key = ItemKey.parse(id == null || id.isEmpty() ? "minecraft:grass@0" : id);
        ItemStack stack = key.isValid() ? key.toStack(1) : ItemStack.EMPTY;
        return stack.isEmpty() ? new ItemStack(Blocks.GRASS) : stack;
    }

    protected static int pages(int count, int perPage) {
        return Math.max(1, (count + perPage - 1) / perPage);
    }

    /** Число из текста; null + сообщение, если не число. */
    @Nullable
    protected static Long number(EntityPlayerMP who, String text, long min, long max) {
        try {
            long v = Long.parseLong(text.trim().replace(" ", "").replace("_", ""));
            if (v < min || v > max) {
                ChatUtil.send(who, "&cНужно число от " + min + " до " + max + ".");
                return null;
            }
            return v;
        } catch (NumberFormatException e) {
            ChatUtil.send(who, "&cНужно число.");
            return null;
        }
    }

    /** Цвет из «#RRGGBB» / «RRGGBB»; -1 — «нет». null — не понял. */
    @Nullable
    protected static Integer color(String text) {
        String t = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.equals("-1") || t.equals("нет") || t.equals("none")) {
            return -1;
        }
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (t.startsWith("0x")) {
            t = t.substring(2);
        }
        try {
            if (t.length() != 6) {
                return null;
            }
            return Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected static String hex(int color) {
        return color < 0 ? "нет" : String.format("#%06X", color & 0xFFFFFF);
    }

    protected static ItemStack filler() {
        return Icons.filler();
    }

    protected static Item block(net.minecraft.block.Block b) {
        return Item.getItemFromBlock(b);
    }
}
