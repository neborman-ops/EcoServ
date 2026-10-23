package com.ecoserv.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Ключ предмета для прайс-листа: id[@meta][#nbt].
 * meta = -1 означает «любой meta/damage».
 */
public final class ItemKey {

    public static final int ANY_META = -1;

    public final String id;
    public final int meta;
    @Nullable
    public final String nbt;

    public ItemKey(String id, int meta, @Nullable String nbt) {
        this.id = id == null ? "minecraft:air" : id;
        this.meta = meta;
        this.nbt = (nbt == null || nbt.isEmpty()) ? null : nbt;
    }

    public static ItemKey of(ItemStack stack, boolean withNbt, boolean withMeta) {
        if (stack == null || stack.isEmpty()) {
            return new ItemKey("minecraft:air", ANY_META, null);
        }
        ResourceLocation rl = stack.getItem().getRegistryName();
        String id = rl == null ? "minecraft:air" : rl.toString();
        int m = withMeta ? stack.getItemDamage() : ANY_META;
        String tag = null;
        if (withNbt && stack.hasTagCompound()) {
            tag = String.valueOf(stack.getTagCompound());
        }
        return new ItemKey(id, m, tag);
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation rl = stack.getItem().getRegistryName();
        if (rl == null || !rl.toString().equals(id)) {
            return false;
        }
        if (meta != ANY_META && stack.getItemDamage() != meta) {
            return false;
        }
        if (nbt != null) {
            return nbt.equals(String.valueOf(stack.getTagCompound()));
        }
        return true;
    }

    public ItemStack toStack(int count) {
        Item item = Item.getByNameOrId(id);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, count, meta == ANY_META ? 0 : meta);
        if (nbt != null) {
            try {
                NBTTagCompound tag = JsonToNBT.getTagFromJson(nbt);
                stack.setTagCompound(tag);
            } catch (Exception ignored) {
                // некорректный NBT в прайсе — отдаём предмет без тегов
            }
        }
        return stack;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder(id);
        sb.append('@').append(meta);
        if (nbt != null) {
            sb.append('#').append(nbt);
        }
        return sb.toString();
    }

    public static ItemKey parse(String s) {
        if (s == null || s.isEmpty()) {
            return new ItemKey("minecraft:air", ANY_META, null);
        }
        String rest = s;
        String tag = null;
        int hash = rest.indexOf('#');
        if (hash >= 0) {
            tag = rest.substring(hash + 1);
            rest = rest.substring(0, hash);
        }
        int at = rest.lastIndexOf('@');
        int m = ANY_META;
        if (at >= 0) {
            String metaPart = rest.substring(at + 1);
            rest = rest.substring(0, at);
            if ("*".equals(metaPart)) {
                m = ANY_META;
            } else {
                try {
                    m = Integer.parseInt(metaPart);
                } catch (NumberFormatException ignored) {
                    m = ANY_META;
                }
            }
        }
        if (rest.indexOf(':') < 0) {
            rest = "minecraft:" + rest;
        }
        return new ItemKey(rest, m, tag);
    }

    public boolean isValid() {
        return !"minecraft:air".equals(id) && Item.getByNameOrId(id) != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemKey)) {
            return false;
        }
        ItemKey other = (ItemKey) o;
        if (meta != other.meta || !id.equals(other.id)) {
            return false;
        }
        return nbt == null ? other.nbt == null : nbt.equals(other.nbt);
    }

    @Override
    public int hashCode() {
        int h = id.hashCode() * 31 + meta;
        return nbt == null ? h : h * 31 + nbt.hashCode();
    }

    @Override
    public String toString() {
        return serialize();
    }
}
